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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
    public static KeyBinding guiKey;
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

        if (pendingPos != null) {
            BlockState confirmed = client.world.getBlockState(pendingPos);
            if (pendingBlock != null && confirmed.getBlock() == pendingBlock) {
                pendingPos = null;
                pendingBlock = null;
                pendingTicks = 0;
            } else if (++pendingTicks < 8) {
                return;
            } else {
                // Server did not confirm the placement; don't immediately spam retries.
                pendingPos = null;
                pendingBlock = null;
                pendingTicks = 0;
                cooldown = 5;
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

        BlockState actual = world.getBlockState(target);
        if (actual.getBlock() == wanted.getBlock()) return;
        if (!actual.isAir() && !actual.isReplaceable()) return;

        // Keep placement clear of the player, including the feet/edge case:
        // a carpet placed exactly in the block space beneath the player's feet can
        // cause client/server collision corrections even when the full block boxes
        // only touch at their boundary. Expand the target slightly upward and sideways.
        Box targetSafety = new Box(target).expand(0.08, 0.16, 0.08);

        // Also reserve the player's immediate forward movement corridor. A carpet
        // placed just outside the current bounding box can still be hit on the next
        // movement tick, causing step-up / server position correction on multiplayer.
        // Project in the current horizontal movement direction; if nearly stationary,
        // use the view direction so the player can still aim ahead while starting to walk.
        Box playerSafety = player.getBoundingBox().expand(0.08, 0.12, 0.08);
        double vx = player.getVelocity().x;
        double vz = player.getVelocity().z;
        double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
        double dx;
        double dz;
        if (horizontalSpeed > 0.03) {
            dx = vx / horizontalSpeed;
            dz = vz / horizontalSpeed;
        } else {
            double yaw = Math.toRadians(player.getYaw());
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
        }
        // Keep roughly one short movement step clear in front of the player.
        Box movementCorridor = playerSafety.stretch(dx * 0.85, 0.0, dz * 0.85);
        if (playerSafety.intersects(targetSafety) || movementCorridor.intersects(targetSafety)) return;

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

        // Reuse the actual vanilla hit result. Do not fabricate a face/position:
        // servers validate that the clicked block and face are plausible.
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        // Avoid extra swing animations and repeated attempts when the interaction was
        // rejected locally. A slightly longer minimum interval reduces packet bursts
        // and client/server correction jitter during rapid carpet placement.
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            pendingPos = target.toImmutable();
            pendingBlock = wantedBlock;
            pendingTicks = 0;
            cooldown = Math.max(1, cfg.delayTicks);
        } else {
            cooldown = 4;
        }
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
