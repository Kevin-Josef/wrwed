package de.mapartassist.cam;

import de.mapartassist.config.ModConfig;
import de.mapartassist.esp.BlockEsp;
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
    /** Verfolger-Modus: Kamera folgt dem Spieler, der normal weiterspielt. */
    private static boolean follow = false;
    private static ArmorStandEntity cam;
    private static ClientWorld world;
    private static double x, y, z, eyeOff;
    private static float yaw, pitch, savedYaw, savedPitch;
    private static long lastNs;

    public static boolean active() { return active; }

    public static boolean following() { return active && follow; }

    public static void set(MinecraftClient mc, boolean on) {
        if (on) {
            if (active && follow) stop(mc); // Modus wechseln
            start(mc, false);
        } else if (active && !follow) stop(mc);
    }

    /** Verfolger-Kamera an/aus (Spieler spielt normal weiter). */
    public static void setFollow(MinecraftClient mc, boolean on) {
        if (on) {
            if (active && !follow) stop(mc);
            start(mc, true);
        } else if (active && follow) stop(mc);
    }

    public static void toggle(MinecraftClient mc) {
        if (active) stop(mc);
        else start(mc, false);
    }

    /** Nur die freie Freecam (nicht der Verfolger-Modus). */
    public static boolean freeFlying() { return active && !follow; }

    private static void start(MinecraftClient mc, boolean followMode) {
        if (active || mc.player == null || mc.world == null) return;
        // Eine laufende Wiedergabe (Recorder) bleibt absichtlich aktiv.
        if (MovementRecorder.state == MovementRecorder.State.RECORDING) MovementRecorder.stopAll(mc);
        ClientPlayerEntity p = mc.player;
        cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
        cam.setInvisible(true);
        cam.noClip = true;
        eyeOff = cam.getEyeY() - cam.getY();
        world = mc.world;
        yaw = savedYaw = p.getYaw();
        pitch = savedPitch = p.getPitch();
        Vec3d eye = p.getEyePos();
        double yr0 = Math.toRadians(yaw);
        x = eye.x + Math.sin(yr0) * 3.0;
        y = eye.y + 0.6;
        z = eye.z - Math.cos(yr0) * 3.0;
        place(true);
        mc.setCameraEntity(cam);
        follow = followMode;
        active = true;
        lastNs = System.nanoTime();
        p.sendMessage(Text.literal(followMode ? "Verfolger-Kamera: AN" : "Freecam: AN").formatted(Formatting.GREEN), true);
    }

    public static void stop(MinecraftClient mc) {
        if (!active) return;
        active = false;
        cam = null;
        world = null;
        boolean wasFollow = follow;
        follow = false;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            if (!wasFollow) { // im Verfolger-Modus behaelt der Spieler seine Blickrichtung
                mc.player.setYaw(savedYaw);
                mc.player.setPitch(savedPitch);
            }
            mc.player.sendMessage(Text.literal(wasFollow ? "Verfolger-Kamera: AUS" : "Freecam: AUS").formatted(Formatting.RED), true);
        }
    }

    private static void place(boolean snap) {
        // setzt Position, Blickrichtung und die "vorherigen" Werte gleichzeitig (kein Interpolations-Ruckeln)
        cam.refreshPositionAndAngles(x, y - eyeOff, z, yaw, pitch);
        cam.setHeadYaw(yaw);
        cam.setBodyYaw(yaw);
    }

    private static boolean down(long w, int key) {
        return GLFW.glfwGetKey(w, key) == GLFW.GLFW_PRESS;
    }

    /** Von der Wiedergabe (Recorder) gesetzte Blickrichtung uebernehmen, damit sie nicht als Mausbewegung zaehlt. */
    public static void syncSaved(float newYaw, float newPitch) {
        if (!active) return;
        savedYaw = newYaw;
        savedPitch = newPitch;
    }

    /**
     * Am Anfang jedes Ticks: Waehrend der Spiel-Logik (Zielerfassung, Abbauen, Benutzen) ist der echte Spieler die
     * Kamera. Dadurch zielt und baut der Spieler weiter normal ab; nur die Darstellung zeigt die Freecam.
     */
    public static void tickStart(MinecraftClient mc) {
        if (!active) return;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        boolean replay = MovementRecorder.state == MovementRecorder.State.PLAYING;
        if (!replay && !follow) {
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
        }
        update(mc);
    }

    /** Am Ende jedes Ticks: wieder die Freecam-Kamera zeigen. */
    public static void tickEnd(MinecraftClient mc) {
        if (active && cam != null && mc.player != null && mc.world == world) mc.setCameraEntity(cam);
    }

    /** Kamera haengt hinter/ueber dem Spieler; Steuerung und Blickrichtung bleiben beim Spieler. */
    private static void updateFollow(MinecraftClient mc, ClientPlayerEntity p) {
        ModConfig cfg = ModConfig.get();
        double d = MathHelper.clamp(cfg.followDistance, 2, 30);
        double h = MathHelper.clamp(cfg.followHeight, 0, 20);
        Vec3d pos = p.getLerpedPos(BlockEsp.partial());
        double eyeY = pos.y + (p.getEyeY() - p.getY());
        yaw = p.getYaw();
        double yr = Math.toRadians(yaw);
        x = pos.x + Math.sin(yr) * d;
        y = eyeY + h;
        z = pos.z - Math.cos(yr) * d;
        pitch = MathHelper.clamp(p.getPitch() + (float) Math.toDegrees(Math.atan2(h, d)), -90f, 90f);
        place(false);
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
        if (follow) {
            updateFollow(mc, p);
            return;
        }
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
