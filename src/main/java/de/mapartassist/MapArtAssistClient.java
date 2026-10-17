package de.mapartassist;

import de.mapartassist.config.ModConfig;
import de.mapartassist.gui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

public class MapArtAssistClient implements ClientModInitializer {
    private static final double REACH = 4.5;
    private static final double RAY_STEP = 0.05;

    public static KeyBinding toggleKey;
    public static KeyBinding guiKey;
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("mapartassist", "main"));

    private int cooldown = 0;
    private int messageCooldown = 0;
    private boolean warnedMissing = false;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mapartassist.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (messageCooldown > 0) messageCooldown--;

        while (guiKey.wasPressed()) client.setScreen(new MainScreen(null));
        while (toggleKey.wasPressed()) {
            cfg.autoPlace = !cfg.autoPlace;
            ModConfig.save();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Auto-Platzieren: " + (cfg.autoPlace ? "AN" : "AUS"))
                        .formatted(cfg.autoPlace ? Formatting.GREEN : Formatting.RED), true);
            }
        }

        if (!cfg.autoPlace || client.player == null || client.world == null
                || client.currentScreen != null || client.interactionManager == null) return;

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
     * Sucht entlang des Sichtstrahls nach dem ersten Schematic-Block, der in der Welt
     * tatsächlich noch fehlt. Bereits korrekt platzierte Blöcke werden übersprungen.
     */
    private void tryPlace(MinecraftClient client, ModConfig cfg) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        World schem = LitematicaBridge.getSchematicWorld();
        if (schem == null) return;

        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        BlockPos last = null;

        for (double d = 0; d <= REACH; d += RAY_STEP) {
            BlockPos p = BlockPos.ofFloored(eye.add(dir.multiply(d)));
            if (p.equals(last)) continue;
            last = p;

            BlockState want = schem.getBlockState(p);
            if (want.isAir()) {
                // Ein fester Block in der Welt verdeckt alles dahinter.
                BlockState actual = world.getBlockState(p);
                if (!actual.isAir() && !actual.isReplaceable()) return;
                continue;
            }

            BlockState actual = world.getBlockState(p);
            if (matches(actual, want)) continue;

            // Ein anderer, nicht ersetzbarer Block steht im Ziel oder versperrt die Sicht.
            if (!actual.isAir() && !actual.isReplaceable()) return;

            if (player.getBoundingBox().intersects(new Box(p))) return;

            Block wantBlock = want.getBlock();
            if (!isBlock(player.getMainHandStack(), wantBlock)) {
                if (!cfg.takeFromInventory) return;
                if (!switchTo(client, wantBlock)) {
                    if (messageCooldown <= 0) {
                        player.sendMessage(Text.literal("Fehlt im Inventar: ")
                                .append(wantBlock.getName()).formatted(Formatting.YELLOW), true);
                        messageCooldown = 40;
                    }
                    return;
                }
                // Give the selected slot / inventory swap a tick to settle before placing.
                cooldown = 1;
                return;
            }

            placeAt(client, cfg, p, eye);
            return;
        }
    }

    private static boolean matches(BlockState actual, BlockState wanted) {
        // Block identity is the important criterion for map art. Exact state matching
        // would reject blocks whose orientation is set by the placement context.
        return actual.getBlock() == wanted.getBlock();
    }

    private static boolean isBlock(ItemStack stack, Block block) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() == block;
    }

    /** Searches the hotbar first, then moves an inventory stack into a hotbar slot. */
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

    private void placeAt(MinecraftClient client, ModConfig cfg, BlockPos p, Vec3d eye) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player.getBoundingBox().intersects(new Box(p))) return;

        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            BlockPos support = p.offset(side);
            BlockState supportState = world.getBlockState(support);
            if (supportState.isAir() || supportState.isReplaceable()) continue;

            Direction face = side.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(support).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.distanceTo(hitVec);
            if (dist > REACH || dist >= bestDist) continue;

            bestDist = dist;
            best = new BlockHitResult(hitVec, face, support, false);
        }
        if (best == null) return;

        // Don't apply the delay if no valid support face was found.
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, best);
        player.swingHand(Hand.MAIN_HAND);
        cooldown = Math.max(1, cfg.delayTicks);
    }
}
