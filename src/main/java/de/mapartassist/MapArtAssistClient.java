package de.mapartassist;

import de.mapartassist.cam.Freecam;
import de.mapartassist.combat.CombatLock;
import de.mapartassist.litematica.SchematicProgress;
import de.mapartassist.macro.AfkGuard;
import de.mapartassist.macro.AutoRepair;
import de.mapartassist.macro.MovementRecorder;
import de.mapartassist.macro.SellMacro;
import de.mapartassist.config.ModConfig;
import de.mapartassist.gui.MenuScreen;
import de.mapartassist.guard.SpawnerGuard;
import de.mapartassist.macro.AuctionMacro;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Hand;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

public class MapArtAssistClient implements ClientModInitializer {
    private static final double REACH = 4.5;

    public static KeyBinding toggleKey;
    public static KeyBinding combatKey;
    public static KeyBinding guiKey;
    public static KeyBinding spawnerKey;
    public static KeyBinding freecamKey;
    public static KeyBinding recordKey;
    public static KeyBinding playKey;
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("mapartassist", "main"));

    private int cooldown = 0;
    private int messageCooldown = 0;
    private boolean warnedMissing = false;

    // One placement at a time: wait for the server-authoritative world state
    // before issuing another interaction. This avoids rapid retries during rollback.
    private BlockPos pendingPos = null;
    private Block pendingBlock = null;
    private int pendingTicks = 0;

    // Defer the synthetic click until the next tick, then require the exact same
    // crosshair block and face. This prevents a stale click after the player looks away.
    private BlockPos queuedHitPos = null;
    private Direction queuedHitSide = null;
    private BlockPos queuedTarget = null;
    private Block queuedBlock = null;

    // Per-frame placement (runs every rendered frame, not only every 20th of a second).
    private long tickCounter = 0;
    private long lastPlaceTick = -1000;
    private int placedThisTick = 0;
    private final Map<BlockPos, Long> recentAttempts = new HashMap<>();


    @Override
    public void onInitializeClient() {
        ModConfig.load();
        AuctionMacro.init();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        combatKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.combat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        spawnerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.spawner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));
        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY));
        recordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_1, CATEGORY));
        playKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.play", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_2, CATEGORY));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        // The guard runs BEFORE vanilla processes input for the tick, i.e. right before
        // our synthetic right-click would actually be used.
        ClientTickEvents.START_CLIENT_TICK.register(this::guardClick);
        ClientTickEvents.START_CLIENT_TICK.register(MovementRecorder::tickStart);
        ClientTickEvents.START_CLIENT_TICK.register(Freecam::tickStart);
        ClientTickEvents.END_CLIENT_TICK.register(MovementRecorder::tickEnd);
        ClientTickEvents.END_CLIENT_TICK.register(Freecam::tickEnd);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> SchematicProgress.tick(mc, tickCounter));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientTickEvents.END_CLIENT_TICK.register(AuctionMacro::tick);
        ClientTickEvents.END_CLIENT_TICK.register(SellMacro::tick);
        ClientTickEvents.END_CLIENT_TICK.register(AfkGuard::tick);
        ClientTickEvents.END_CLIENT_TICK.register(AutoRepair::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CombatLock::check);
        ClientTickEvents.END_CLIENT_TICK.register(SpawnerGuard::tick);
        // /sellmacro = /sell-Makro, /repairmacro = Auto-Reparatur (Test). Die Server-Befehle /sell und /shop bleiben frei.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("sellmacro")
                    .executes(c -> { SellMacro.start(false); return 1; })
                    .then(ClientCommandManager.literal("stop").executes(c -> { SellMacro.stop(); return 1; })));
            dispatcher.register(ClientCommandManager.literal("repairmacro")
                    .executes(c -> { AutoRepair.start(true); return 1; })
                    .then(ClientCommandManager.literal("stop").executes(c -> { AutoRepair.stop(); return 1; })));
        });
        // /ahmacro = Auktions-Makro (bewusst NICHT /ah, damit /ah und /ah sell des Servers normal funktionieren)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ahmacro")
                        .executes(c -> { AuctionMacro.start(); return 1; })
                        .then(ClientCommandManager.literal("stop")
                                .executes(c -> { AuctionMacro.stop(); return 1; }))
                        // /ahmacro reset = gemerkte Auto-Preise vergessen
                        .then(ClientCommandManager.literal("reset")
                                .executes(c -> {
                                    ModConfig.get().lastAutoPrice.clear();
                                    ModConfig.save();
                                    AuctionMacro.message("Gemerkte Auto-Preise gelöscht.", Formatting.GREEN);
                                    return 1;
                                }))
                        // /ahmacro preis tnt 200k   oder   /ahmacro preis tnt auto
                        .then(ClientCommandManager.literal("preis")
                                .then(ClientCommandManager.argument("item", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("betrag", StringArgumentType.word())
                                                .executes(c -> {
                                                    String word = StringArgumentType.getString(c, "item");
                                                    String id = word.contains(":") ? word : "minecraft:" + word;
                                                    String val = StringArgumentType.getString(c, "betrag");
                                                    ModConfig cfg = ModConfig.get();
                                                    if (!cfg.sellItems.contains(id)) {
                                                        AuctionMacro.message("\"" + id + "\" ist nicht in der Verkaufsliste.",
                                                                Formatting.YELLOW);
                                                        return 0;
                                                    }
                                                    if (val.equalsIgnoreCase("auto")) {
                                                        cfg.sellUseManual.put(id, false);
                                                        ModConfig.save();
                                                        AuctionMacro.message("Preis für " + id + ": automatisch.", Formatting.GREEN);
                                                        return 1;
                                                    }
                                                    long v = AuctionMacro.parseMoney(val);
                                                    if (v < 1) {
                                                        AuctionMacro.message("Ungültiger Betrag: " + val, Formatting.RED);
                                                        return 0;
                                                    }
                                                    cfg.sellPrices.put(id, v);
                                                    cfg.sellUseManual.put(id, true);
                                                    ModConfig.save();
                                                    AuctionMacro.message("Manueller Preis für " + id + ": " + v, Formatting.GREEN);
                                                    return 1;
                                                }))))));
        // A HUD element is drawn once per frame, after the crosshair target was updated.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of("mapartassist", "frame_placer"),
                (ctx, tickCounter) -> {
                    onFrame(MinecraftClient.getInstance());
                    CombatLock.check(MinecraftClient.getInstance());
                    Freecam.update(MinecraftClient.getInstance());
                });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of("mapartassist", "progress_hud"),
                (ctx, tickCounter) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (!ModConfig.get().progressHud || mc.options == null || mc.options.hudHidden) return;
                    String t = SchematicProgress.hudText();
                    if (t != null) ctx.drawTextWithShadow(mc.textRenderer, t, 6, 6, 0xFFFFFFFF);
                });
    }

    // ------------------------------------------------------------------ Auto-Schleichen
    private boolean autoSneakForced = false;
    private int sneakHeldTicks = 0;

    /** Bloecke, bei denen ein Rechtsklick ein Menue oeffnet und deshalb geschlichen werden muss. */
    private static boolean isSneakBlock(BlockState st) {
        return st.isOf(Blocks.DISPENSER);
    }

    /**
     * true, wenn gerade ein Dispenser anvisiert ist, an dessen Seite der Schematic-Block
     * gesetzt werden soll, und genau dieser Block in der Hand liegt.
     */
    private boolean wantsDispenserSneak(MinecraftClient client, ModConfig cfg) {
        if (!cfg.autoPlace || client.player == null || client.world == null || client.currentScreen != null) return false;
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        if (!isSneakBlock(client.world.getBlockState(hit.getBlockPos()))) return false;
        if (client.player.getEyePos().squaredDistanceTo(hit.getPos()) > REACH * REACH) return false;
        World schematic = LitematicaBridge.getSchematicWorld();
        if (schematic == null) return false;
        BlockPos target = hit.getBlockPos().offset(hit.getSide());
        BlockState wanted = schematic.getBlockState(target);
        if (wanted.isAir()) return false;
        BlockState actual = client.world.getBlockState(target);
        if (actual.getBlock() == wanted.getBlock()) return false;
        if (!actual.isAir() && !actual.isReplaceable()) return false;
        return isBlock(client.player.getMainHandStack(), wanted.getBlock());
    }

    /** Schleichen nur solange der Dispenser anvisiert wird; danach wird nur eigenes Schleichen wieder beendet. */
    private void updateAutoSneak(MinecraftClient client, ModConfig cfg) {
        if (client.player == null || client.options == null) return;
        sneakHeldTicks = client.player.isSneaking() ? sneakHeldTicks + 1 : 0;
        boolean want = wantsDispenserSneak(client, cfg);
        if (want && !autoSneakForced && !client.options.sneakKey.isPressed()) {
            client.options.sneakKey.setPressed(true);
            autoSneakForced = true;
        } else if (!want && autoSneakForced) {
            client.options.sneakKey.setPressed(false);
            autoSneakForced = false;
        }
    }

    /** Platzieren erst, wenn bei einem Dispenser das Schleichen schon beim Server angekommen ist. */
    private boolean sneakReadyFor(MinecraftClient client, BlockHitResult hit) {
        if (!isSneakBlock(client.world.getBlockState(hit.getBlockPos()))) return true;
        return client.player.isSneaking() && sneakHeldTicks >= 2;
    }

    /** Called every rendered frame. Places directly with the exact, freshly validated crosshair hit. */
    private void onFrame(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.frameMode || !cfg.autoPlace) return;
        if (client.player == null || client.world == null || client.currentScreen != null
                || client.interactionManager == null) return;
        if (cooldown > 0) return; // set by inventory switching, counted down in onTick

        // Interval in ticks between placements; 0 = every single frame.
        if (cfg.delayTicks > 0 && tickCounter - lastPlaceTick < cfg.delayTicks) return;
        // Limit packets per tick: a flood of placements makes the server correct the player (stutter).
        if (placedThisTick >= Math.max(1, cfg.maxPerTick)) return;

        ClientPlayerEntity player = client.player;
        World schematic = LitematicaBridge.getSchematicWorld();
        if (schematic == null) return;
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        if (player.getEyePos().squaredDistanceTo(hit.getPos()) > REACH * REACH) return;

        BlockPos target = hit.getBlockPos().offset(hit.getSide());
        BlockState wanted = schematic.getBlockState(target);
        if (wanted.isAir()) return;
        if (cfg.progressSkipPlacing && SchematicProgress.isExcluded(wanted.getBlock())) return;
        BlockState actual = client.world.getBlockState(target);
        if (actual.getBlock() == wanted.getBlock()) return;
        if (!actual.isAir() && !actual.isReplaceable()) return;
        if (player.getBoundingBox().intersects(new Box(target))) return;
        if (!rotationOk(client, cfg, hit, wanted)) {
            hintRotation(client, wanted);
            return;
        }

        // Do not hammer the same position every frame if the server keeps rejecting it.
        Long last = recentAttempts.get(target);
        if (last != null && tickCounter - last < 4) return;

        Block wantedBlock = wanted.getBlock();
        if (!isBlock(player.getMainHandStack(), wantedBlock)) {
            if (!cfg.takeFromInventory) return;
            if (!switchTo(client, wantedBlock)) {
                if (messageCooldown <= 0) {
                    player.sendMessage(Text.literal("Fehlt im Inventar: ")
                            .append(wantedBlock.getName()).formatted(Formatting.YELLOW), true);
                    messageCooldown = 40;
                }
                return;
            }
            cooldown = 2;
            return;
        }

        // Dispenser anvisiert: erst schleichen, sonst oeffnet der Rechtsklick dessen Inventar.
        if (!sneakReadyFor(client, hit)) return;

        // Exact held item + exact hit, verified in this very frame: nothing can go stale.
        recentAttempts.put(target.toImmutable(), tickCounter);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        lastPlaceTick = tickCounter;
        placedThisTick++;
    }

    private void onTick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        tickCounter++;
        placedThisTick = 0;
        if (tickCounter % 40 == 0) recentAttempts.values().removeIf(t -> tickCounter - t > 40);
        if (messageCooldown > 0) messageCooldown--;

        while (freecamKey.wasPressed()) Freecam.toggle(client);
        while (recordKey.wasPressed()) MovementRecorder.toggleRecord(client);
        while (playKey.wasPressed()) MovementRecorder.togglePlay(client);
        while (guiKey.wasPressed()) client.setScreen(new MenuScreen(null));
        while (combatKey.wasPressed()) {
            cfg.combatLock = !cfg.combatLock;
            ModConfig.save();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("HT1 Combat Lock: " + (cfg.combatLock ? "AN" : "AUS"))
                        .formatted(cfg.combatLock ? Formatting.GREEN : Formatting.RED), true);
            }
        }
        while (spawnerKey.wasPressed()) {
            cfg.spawnerGuard = !cfg.spawnerGuard;
            ModConfig.save();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Spawner-Schutz: " + (cfg.spawnerGuard ? "AN" : "AUS"))
                        .formatted(cfg.spawnerGuard ? Formatting.GREEN : Formatting.RED), true);
            }
        }
        while (toggleKey.wasPressed()) {
            cfg.autoPlace = !cfg.autoPlace;
            ModConfig.save();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Auto-Platzieren: " + (cfg.autoPlace ? "AN" : "AUS"))
                        .formatted(cfg.autoPlace ? Formatting.GREEN : Formatting.RED), true);
            }
        }

        updateAutoSneak(client, cfg);

        if (!cfg.autoPlace || client.player == null || client.world == null
                || client.currentScreen != null || client.interactionManager == null) return;

        if (cfg.frameMode) {
            if (cooldown > 0) cooldown--;
            return;
        }

        if (pendingPos != null) {
            pendingTicks++;
            BlockState observed = client.world.getBlockState(pendingPos);

            // The client predicts a successful placement before the server replies.
            // Do not treat that immediate prediction as server confirmation; give the
            // normal interaction/server round trip a few ticks to settle first.
            if (pendingTicks >= 1 && pendingBlock != null
                    && observed.getBlock() == pendingBlock) {
                pendingPos = null;
                pendingBlock = null;
                pendingTicks = 0;
            } else if (pendingTicks < 2) {
                return;
            } else {
                // No matching block appeared; avoid an immediate retry burst.
                pendingPos = null;
                pendingBlock = null;
                pendingTicks = 0;
                cooldown = 1;
                return;
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        tryPlace(client, cfg);
        if (LitematicaBridge.isMissing() && !warnedMissing) {
            warnedMissing = true;
            client.player.sendMessage(Text.literal("Litematica nicht gefunden!").formatted(Formatting.RED), false);
        }
    }

    /**
     * Uses Minecraft's real crosshair hit result. The previous broad search corridor
     * could choose a block the player was not actually targeting, causing false
     * placements, movement obstruction and server-side rollback.
     */
    private void tryPlace(MinecraftClient client, ModConfig cfg) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        World schematic = LitematicaBridge.getSchematicWorld();
        if (schematic == null) return;

        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        if (player.getEyePos().squaredDistanceTo(hit.getPos()) > REACH * REACH) return;

        // Place into the adjacent position on the face actually hit.
        BlockPos target = hit.getBlockPos().offset(hit.getSide());

        BlockState wanted = schematic.getBlockState(target);
        if (wanted.isAir()) return;
        if (cfg.progressSkipPlacing && SchematicProgress.isExcluded(wanted.getBlock())) return;

        BlockState actual = world.getBlockState(target);
        if (actual.getBlock() == wanted.getBlock()) return;
        if (!actual.isAir() && !actual.isReplaceable()) return;

        // Do not place into the player's current collision box. Avoid speculative
        // forward-corridor blocking: it prevented legitimate placements while moving
        // and did not reliably address server-side movement corrections.
        if (player.getBoundingBox().intersects(new Box(target))) return;
        if (!rotationOk(client, cfg, hit, wanted)) {
            hintRotation(client, wanted);
            return;
        }

        Block wantedBlock = wanted.getBlock();
        if (!isBlock(player.getMainHandStack(), wantedBlock)) {
            if (!cfg.takeFromInventory) return;
            if (!switchTo(client, wantedBlock)) {
                if (messageCooldown <= 0) {
                    player.sendMessage(Text.literal("Fehlt im Inventar: ")
                            .append(wantedBlock.getName()).formatted(Formatting.YELLOW), true);
                    messageCooldown = 40;
                }
                return;
            }
            // Inventory selection changes must settle before the next placement attempt.
            cooldown = 2;
            return;
        }

        // Dispenser anvisiert: erst schleichen, sonst oeffnet der Rechtsklick dessen Inventar.
        if (!sneakReadyFor(client, hit)) return;

        // Click through vanilla's normal input handler (smooth movement). The click is
        // consumed at the start of the next tick; guardClick() re-checks everything
        // right before that and cancels the click if the situation changed.
        queuedHitPos = hit.getBlockPos().toImmutable();
        queuedHitSide = hit.getSide();
        queuedTarget = target.toImmutable();
        queuedBlock = wantedBlock;
        KeyBinding.onKeyPressed(client.options.useKey.getDefaultKey());
        pendingPos = target.toImmutable();
        pendingBlock = wantedBlock;
        pendingTicks = 0;
        // delayTicks = ticks between two placements. 0 and 1 both mean "every tick"
        // (Minecraft processes input once per tick, so that is the maximum speed).
        cooldown = Math.max(0, cfg.delayTicks - 1);
    }

    /**
     * Runs at the very start of a tick, before vanilla handles the use key. If the
     * crosshair moved to another block/face, the held item changed, or the schematic
     * wants a different block at the target, the queued click is thrown away.
     */
    private void guardClick(MinecraftClient client) {
        if (queuedHitPos == null) return;

        boolean valid = client.player != null && client.world != null
                && client.crosshairTarget instanceof BlockHitResult h
                && h.getType() == HitResult.Type.BLOCK
                && h.getBlockPos().equals(queuedHitPos)
                && h.getSide() == queuedHitSide
                && queuedBlock != null
                && isBlock(client.player.getMainHandStack(), queuedBlock);

        if (valid) {
            World schematic = LitematicaBridge.getSchematicWorld();
            valid = schematic != null
                    && schematic.getBlockState(queuedTarget).getBlock() == queuedBlock;
            if (valid && client.crosshairTarget instanceof BlockHitResult hh) {
                valid = rotationOk(client, ModConfig.get(), hh, schematic.getBlockState(queuedTarget));
            }
        }

        if (!valid) {
            // Consume the click we queued so vanilla never sees it.
            while (client.options.useKey.wasPressed()) { /* drain */ }
            pendingPos = null;
            pendingBlock = null;
            pendingTicks = 0;
            cooldown = 0;
        }
        clearQueuedPlacement();
    }

    private void clearQueuedPlacement() {
        queuedHitPos = null;
        queuedHitSide = null;
        queuedTarget = null;
        queuedBlock = null;
    }

    /** Eigenschaften, die die Ausrichtung eines Blocks bestimmen. */
    private static final Set<String> ORIENTATION_PROPS =
            Set.of("facing", "axis", "rotation", "half", "face", "orientation", "hinge");

    /**
     * Rotations-Pruefung: Wuerde der Block mit dem aktuellen Blick/Klick genau in der Ausrichtung landen, die die
     * Schematic verlangt (facing, axis, rotation, half ...)? Bloecke ohne Ausrichtung sind immer ok.
     */
    private boolean rotationOk(MinecraftClient client, ModConfig cfg, BlockHitResult hit, BlockState wanted) {
        if (!cfg.rotationCheck || client.player == null) return true;
        List<Property<?>> props = new ArrayList<>();
        for (Property<?> pr : wanted.getProperties()) {
            if (ORIENTATION_PROPS.contains(pr.getName())) props.add(pr);
        }
        if (props.isEmpty()) return true;
        BlockState predicted;
        try {
            ItemPlacementContext ctx = new ItemPlacementContext(client.player, Hand.MAIN_HAND, new ItemStack(wanted.getBlock()), hit);
            predicted = wanted.getBlock().getPlacementState(ctx);
        } catch (Throwable t) {
            return true; // Vorhersage nicht moeglich: nicht blockieren
        }
        if (predicted == null) return false;
        for (Property<?> pr : props) {
            if (!predicted.contains(pr)) continue;
            if (!wanted.get(pr).equals(predicted.get(pr))) return false;
        }
        return true;
    }

    private void hintRotation(MinecraftClient client, BlockState wanted) {
        if (messageCooldown > 0 || client.player == null) return;
        client.player.sendMessage(Text.literal("Falsche Ausrichtung: ")
                .append(wanted.getBlock().getName()).formatted(Formatting.YELLOW), true);
        messageCooldown = 40;
    }

    private static boolean isBlock(ItemStack stack, Block block) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() == block;
    }

    /** Searches the hotbar first, then swaps an inventory stack into a hotbar slot. */
    private boolean switchTo(MinecraftClient client, Block block) {
        ClientPlayerEntity player = client.player;
        for (int i = 0; i < 9; i++) {
            if (isBlock(player.getInventory().getStack(i), block)) {
                player.getInventory().setSelectedSlot(i);
                return true;
            }
        }

        for (int s = 9; s < 36; s++) {
            if (!isBlock(player.getInventory().getStack(s), block)) continue;
            int target = -1;
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).isEmpty()) {
                    target = i;
                    break;
                }
            }
            if (target < 0) target = 8;

            client.interactionManager.clickSlot(
                    player.playerScreenHandler.syncId, s, target, SlotActionType.SWAP, player);
            player.getInventory().setSelectedSlot(target);
            return true;
        }
        return false;
    }
}
