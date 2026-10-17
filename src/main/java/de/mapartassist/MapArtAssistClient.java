package de.mapartassist;

import de.mapartassist.config.ModConfig;
import de.mapartassist.gui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
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
     * ist der Block, den du "anschaust". Passt er zu dem Block in deiner Hand, wird er platziert.
     */
    private void tryPlace(MinecraftClient client, ModConfig cfg) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (!(player.getMainHandStack().getItem() instanceof BlockItem held)) return;

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

            // Das ist der Schematic-Block, den du ansiehst: nur platzieren, wenn er zur Hand passt
            if (want.getBlock() != held.getBlock()) return;

            placeAt(client, cfg, p, eye);
            return;
        }
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
