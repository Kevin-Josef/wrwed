package de.mapartassist.esp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Echte Kameralage fuer die ESPs. Richtung und Position kommen von der tatsaechlich gerenderten Kamera
 * (inkl. Free-Look-Mods, F5, Freecam), das FOV vom echten Render-FOV (inkl. Zoomify & Co.).
 * Ergebnis in x/y/z (Welt) und yaw/pitch (Grad).
 */
public final class CamPose {
    private CamPose() {}

    static double x, y, z;
    static double yaw, pitch;

    // ---- echtes FOV (vom GameRenderer-Mixin gemeldet)
    private static int frame = 0, fovFrame = -1;
    private static double capturedFov = Double.NaN;

    /** Einmal pro HUD-Frame aufrufen. */
    public static void nextFrame() { frame++; }

    /** Vom Mixin aufgerufen: nur der erste getFov-Wert pro Frame zaehlt (die Welt, nicht die Hand). */
    public static void captureFov(double v) {
        if (fovFrame != frame && v > 1.0 && v < 179.0) {
            fovFrame = frame;
            capturedFov = v;
        }
    }

    /** tan(FOV/2) des echten Bildes; ohne Mixin-Wert wie bisher aus den Optionen + Sprint-Aufweitung. */
    static double tanHalf(MinecraftClient mc, float pt) {
        double fov;
        if (!Double.isNaN(capturedFov) && frame - fovFrame <= 1) {
            fov = capturedFov;
        } else {
            float mul = BlockEsp.lastFovMul + (BlockEsp.fovMul - BlockEsp.lastFovMul) * pt;
            fov = mc.options.getFov().getValue() * mul;
        }
        return Math.tan(Math.toRadians(fov) / 2.0);
    }

    // ---- Kameraposition per Reflection (unabhaengig vom Methodennamen der Mappings)
    private static Method posMethod;
    private static boolean posTried;

    private static Vec3d cameraPos(Camera c) {
        if (!posTried) {
            posTried = true;
            for (Method m : Camera.class.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == Vec3d.class
                        && !Modifier.isStatic(m.getModifiers())) {
                    posMethod = m;
                    break;
                }
            }
        }
        if (posMethod == null) return null;
        try {
            Object o = posMethod.invoke(c);
            if (o instanceof Vec3d v && !Double.isNaN(v.x) && !Double.isNaN(v.y) && !Double.isNaN(v.z)) return v;
        } catch (Throwable t) {
            posMethod = null;
        }
        return null;
    }

    static boolean compute(MinecraftClient mc, float pt) {
        if (mc.world == null || mc.player == null || mc.options == null) return false;
        Entity cam = mc.getCameraEntity();
        if (cam == null) cam = mc.player;
        Perspective persp = mc.options.getPerspective();

        Camera c = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        boolean ready = c != null && c.isReady();

        double yw, pi;
        if (ready) {
            // Richtung der echten Kamera: enthaelt Free-Look, F5-Spiegelung usw. schon
            yw = c.getYaw();
            pi = c.getPitch();
            Vec3d p = cameraPos(c);
            if (p != null) {
                x = p.x; y = p.y; z = p.z; yaw = yw; pitch = pi;
                return true;
            }
        } else {
            yw = cam.getYaw(pt);
            pi = cam.getPitch(pt);
            if (persp.isFrontView()) { yw += 180.0; pi = -pi; }
        }

        // Fallback: Position aus Entity + (bei F5) Rueckwaertsstrecke mit Wand-Kollision
        Vec3d lp = cam.getLerpedPos(pt);
        double ex = lp.x, ey = lp.y + (cam.getEyeY() - cam.getY()), ez = lp.z;
        if (persp.isFirstPerson()) {
            x = ex; y = ey; z = ez; yaw = yw; pitch = pi;
            return true;
        }
        double yr = Math.toRadians(yw), pr = Math.toRadians(pi);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double dist = 4.0, d = dist;
        for (int i = 0; i < 8; i++) {
            double ox = ((i & 1) * 2 - 1) * 0.1, oy = (((i >> 1) & 1) * 2 - 1) * 0.1, oz = (((i >> 2) & 1) * 2 - 1) * 0.1;
            Vec3d s = new Vec3d(ex + ox, ey + oy, ez + oz);
            Vec3d e = new Vec3d(ex + ox - fx * dist, ey + oy - fy * dist, ez + oz - fz * dist);
            try {
                HitResult h = mc.world.raycast(new RaycastContext(s, e,
                        RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, cam));
                if (h.getType() != HitResult.Type.MISS) {
                    double hd = h.getPos().distanceTo(s);
                    if (hd < d) d = hd;
                }
            } catch (Exception ignored) {
            }
        }
        x = ex - fx * d; y = ey - fy * d; z = ez - fz * d;
        yaw = yw; pitch = pi;
        return true;
    }
}
