package de.mapartassist.cam;

import de.mapartassist.config.ModConfig;
import de.mapartassist.macro.MovementRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam: die Kamera loest sich vom Spieler und fliegt frei. Der Spieler bleibt stehen.
 * Umgesetzt ohne Mixins ueber eine unsichtbare Kamera-Entity (nicht in der Welt) und
 * direkte Tastenabfrage per GLFW.
 */
public final class Freecam {
    private Freecam() {}

    private static boolean active = false;
    private static ArmorStandEntity cam;
    private static ClientWorld world;
    private static double x, y, z, eyeOff;
    private static float yaw, pitch, savedYaw, savedPitch;
    private static long lastNs;

    public static boolean active() { return active; }

    public static void set(MinecraftClient mc, boolean on) {
        if (on) start(mc);
        else stop(mc);
    }

    public static void toggle(MinecraftClient mc) { set(mc, !active); }

    private static void start(MinecraftClient mc) {
        if (active || mc.player == null || mc.world == null) return;
        MovementRecorder.stopAll(mc);
        ClientPlayerEntity p = mc.player;
        cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
        cam.setInvisible(true);
        cam.noClip = true;
        eyeOff = cam.getEyeY() - cam.getY();
        world = mc.world;
        yaw = savedYaw = p.getYaw();
        pitch = savedPitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        x = eye.x; y = eye.y; z = eye.z;
        place(true);
        mc.setCameraEntity(cam);
        active = true;
        lastNs = System.nanoTime();
        p.sendMessage(Text.literal("Freecam: AN").formatted(Formatting.GREEN), true);
    }

    public static void stop(MinecraftClient mc) {
        if (!active) return;
        active = false;
        cam = null;
        world = null;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
            mc.player.sendMessage(Text.literal("Freecam: AUS").formatted(Formatting.RED), true);
        }
    }

    private static void place(boolean snap) {
        double ny = y - eyeOff;
        cam.setPosition(x, ny, z);
        cam.setYaw(yaw);
        cam.setPitch(pitch);
        cam.setHeadYaw(yaw);
        cam.setBodyYaw(yaw);
        if (snap) {
            cam.prevX = x; cam.prevY = ny; cam.prevZ = z;
            cam.lastRenderX = x; cam.lastRenderY = ny; cam.lastRenderZ = z;
        } else {
            cam.prevX = x; cam.prevY = ny; cam.prevZ = z;
            cam.lastRenderX = x; cam.lastRenderY = ny; cam.lastRenderZ = z;
        }
        cam.prevYaw = yaw;
        cam.prevPitch = pitch;
    }

    private static boolean down(long w, int key) {
        return GLFW.glfwGetKey(w, key) == GLFW.GLFW_PRESS;
    }

    /** Am Anfang jedes Ticks: Spielersteuerung sperren, solange die Kamera frei fliegt. */
    public static void tickStart(MinecraftClient mc) {
        if (!active) return;
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
        o.attackKey.setPressed(false);
        o.useKey.setPressed(false);
        while (o.attackKey.wasPressed()) { /* verwerfen */ }
        while (o.useKey.wasPressed()) { /* verwerfen */ }
        update(mc);
    }

    /** Pro Frame und pro Tick aufrufbar (arbeitet mit echter Zeitdifferenz). */
    public static void update(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null || mc.world != world || cam == null) {
            stop(mc);
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        ClientPlayerEntity p = mc.player;
        // Mausbewegung geht an die Kamera, der Spieler behaelt seine Blickrichtung.
        yaw += p.getYaw() - savedYaw;
        pitch = MathHelper.clamp(pitch + (p.getPitch() - savedPitch), -90f, 90f);
        p.setYaw(savedYaw);
        p.setPitch(savedPitch);

        if (mc.currentScreen == null) {
            long w = mc.getWindow().getHandle();
            double sp = ModConfig.get().freecamSpeed * (down(w, GLFW.GLFW_KEY_LEFT_CONTROL) ? 3.0 : 1.0) * dt;
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            Vec3d fwd = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
            Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
            double dx = 0, dy = 0, dz = 0;
            if (down(w, GLFW.GLFW_KEY_W)) { dx += fwd.x; dy += fwd.y; dz += fwd.z; }
            if (down(w, GLFW.GLFW_KEY_S)) { dx -= fwd.x; dy -= fwd.y; dz -= fwd.z; }
            if (down(w, GLFW.GLFW_KEY_D)) { dx += right.x; dz += right.z; }
            if (down(w, GLFW.GLFW_KEY_A)) { dx -= right.x; dz -= right.z; }
            if (down(w, GLFW.GLFW_KEY_SPACE)) dy += 1;
            if (down(w, GLFW.GLFW_KEY_LEFT_SHIFT)) dy -= 1;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-6) {
                x += dx / len * sp;
                y += dy / len * sp;
                z += dz / len * sp;
            }
        }
        place(false);
    }
}
