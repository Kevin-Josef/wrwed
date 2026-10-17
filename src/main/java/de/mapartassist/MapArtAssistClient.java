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
import net.minecraft.world.RaycastContext;
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

        // Probe a narrow corridor around the look ray, rather than a single voxel line.
        // This helps with thin collision shapes (fences/walls) and small aiming changes.
        java.util.HashSet<BlockPos> checked = new java.util.HashSet<>();
        for (double d = 0.25; d <= REACH; d += 0.15) {
            BlockPos center = BlockPos.ofFloored(eye.add(dir.multiply(d)));
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        BlockPos p = center.add(ox, oy, oz);
                        if (!checked.add(p)) continue;

                        Vec3d targetCenter = Vec3d.ofCenter(p);
                        Vec3d toTarget = targetCenter.subtract(eye);
                        double distance = toTarget.length();
                        if (distance > REACH || distance < 0.2) continue;
                        // Only consider blocks roughly in front of the player.
                        if (toTarget.normalize().dotProduct(dir) < 0.82) continue;

                        BlockState want = schem.getBlockState(p);
                        if (want.isAir()) continue;

                        BlockState actual = world.getBlockState(p);
                        if (matches(actual, want)) continue;
                        if (!actual.isAir() && !actual.isReplaceable()) continue;
                        if (player.getBoundingBox().intersects(new Box(p))) continue;

                        Block wantBlock = want.getBlock();
                        if (!isBlock(player.getMainHandStack(), wantBlock)) {
                            if (!cfg.takeFromInventory) continue;
                            if (!switchTo(client, wantBlock)) {
                                if (messageCooldown <= 0) {
                                    player.sendMessage(Text.literal("Fehlt im Inventar: ")
                                            .append(wantBlock.getName()).formatted(Formatting.YELLOW), true);
                                    messageCooldown = 40;
                                }
                                cooldown = 2;
                                return;
                            }
                            // Wait for the server/client inventory state to settle.
                            cooldown = 2;
                            return;
                        }

                        BlockHitResult hit = findVisibleSupport(world, player, eye, p);
                        if (hit == null) continue;

                        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                        player.swingHand(Hand.MAIN_HAND);
                        cooldown = Math.max(1, cfg.delayTicks);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Finds a real, unobstructed support-face hit. The previous implementation fabricated
     * hit results without checking visibility, which can be rejected by multiplayer servers.
     */
    private BlockHitResult findVisibleSupport(ClientWorld world, ClientPlayerEntity player,
                                              Vec3d eye, BlockPos target) {
        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            BlockPos support = target.offset(side);
            BlockState supportState = world.getBlockState(support);
            if (supportState.isAir() || supportState.isReplaceable()) continue;

            Direction face = side.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(support).add(
                    face.getOffsetX() * 0.499, face.getOffsetY() * 0.499, face.getOffsetZ() * 0.499);
            double dist = eye.distanceTo(hitVec);
            if (dist > REACH || dist >= bestDist) continue;

            // Confirm the line to the face is actually clear and ends on this face.
            BlockHitResult visible = world.raycast(new RaycastContext(
                    eye, hitVec, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, player));
            if (visible.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) continue;
            if (!visible.getBlockPos().equals(support) || visible.getSide() != face) continue;

            bestDist = dist;
            best = new BlockHitResult(hitVec, face, support, false);
        }
        return best;
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


}
