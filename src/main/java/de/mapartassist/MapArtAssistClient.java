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

        while (guiKey.wasPressed()) {
            client.setScreen(new MainScreen(null));
        }
        while (toggleKey.wasPressed()) {
            cfg.autoPlace = !cfg.autoPlace;
            ModConfig.save();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Auto-Platzieren: " + (cfg.autoPlace ? "AN" : "AUS"))
                        .formatted(cfg.autoPlace ? Formatting.GREEN : Formatting.RED), true);
            }
        }

        if (!cfg.autoPlace) return;
        if (client.player == null || client.world == null || client.currentScreen != null) return;
        if (client.interactionManager == null) return;

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
     * Schaut entlang deiner Blickrichtung. Der erste Schematic-Block, der noch nicht gebaut ist,
     * ist der Block, den du "anschaust". Passt er zur Hand, wird er platziert. Sonst wird er
     * (falls eingeschaltet) aus Hotbar/Inventar geholt.
     */
    private void tryPlace(MinecraftClient client, ModConfig cfg) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        Block heldBlock = player.getMainHandStack().getItem() instanceof BlockItem held ? held.getBlock() : null;
        if (heldBlock == null && !cfg.takeFromInventory) return;

        World schem = LitematicaBridge.getSchematicWorld();
        if (schem == null) return;

        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);

        BlockPos last = null;
        for (double d = 0; d <= REACH; d += 0.1) {
            BlockPos p = BlockPos.ofFloored(eye.add(dir.multiply(d)));
            if (p.equals(last)) continue;
            last = p;

            BlockState real = world.getBlockState(p);
            // Ein echter Block ist im Weg -> du schaust nicht auf eine freie Schematic-Stelle
            if (!real.isAir() && !real.isReplaceable()) return;

            BlockState want = schem.getBlockState(p);
            if (want.isAir()) continue;

            // Das ist der Schematic-Block, den du ansiehst
            Block wantBlock = want.getBlock();
            if (wantBlock == heldBlock) {
                placeAt(client, cfg, p, eye);
            } else if (cfg.takeFromInventory && !switchTo(client, wantBlock)) {
                if (messageCooldown <= 0) {
                    player.sendMessage(Text.literal("Fehlt im Inventar: ")
                            .append(wantBlock.getName()).formatted(Formatting.YELLOW), true);
                    messageCooldown = 40;
                }
            }
            return;
        }
    }

    private static boolean isBlock(ItemStack stack, Block block) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() == block;
    }

    /** Holt den Block in die Hand: erst Hotbar absuchen, dann das Inventar. */
    private boolean switchTo(MinecraftClient client, Block block) {
        ClientPlayerEntity player = client.player;

        // 1. Hotbar
        for (int i = 0; i < 9; i++) {
            if (isBlock(player.getInventory().getStack(i), block)) {
                pressHotbarKey(client, i);
                cooldown = 1;
                return true;
            }
        }

        // 2. Inventar -> in einen Hotbar-Platz tauschen
        for (int s = 9; s < 36; s++) {
            if (isBlock(player.getInventory().getStack(s), block)) {
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
                pressHotbarKey(client, target);
                cooldown = 2;
                return true;
            }
        }
        return false;
    }

    /** Waehlt einen Hotbar-Platz, indem die Zifferntaste virtuell gedrueckt wird. */
    private void pressHotbarKey(MinecraftClient client, int slot) {
        KeyBinding key = client.options.hotbarKeys[slot];
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()));
    }

    private void placeAt(MinecraftClient client, ModConfig cfg, BlockPos p, Vec3d eye) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        // Nicht in dich selbst hinein platzieren
        if (player.getBoundingBox().intersects(new Box(p))) return;

        // Einen angrenzenden echten Block als Auflage suchen
        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            BlockPos support = p.offset(side);
            BlockState s = world.getBlockState(support);
            if (s.isAir() || s.isReplaceable()) continue;

            Direction face = side.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(support).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.distanceTo(hitVec);
            if (dist > REACH || dist >= bestDist) continue;

            bestDist = dist;
            best = new BlockHitResult(hitVec, face, support, false);
        }
        if (best == null) return;

        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, best);
        player.swingHand(Hand.MAIN_HAND);
        cooldown = cfg.delayTicks;
    }
}
