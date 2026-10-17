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

        // Never attempt to place inside the player's own collision box.
        if (player.getBoundingBox().intersects(new Box(target))) return;

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
            cooldown = Math.max(3, cfg.delayTicks);
        } else {
            cooldown = 3;
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
