package de.mapartassist.esp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Echte Kameralage fuer die ESPs: Ego-Ansicht (Augenposition) oder F5 (hinter/vor dem Spieler, mit derselben
 * Wand-Kollision wie Vanilla). Ergebnis in x/y/z (Welt) und yaw/pitch (Grad).
 */
final class CamPose {
    private CamPose() {}

    static double x, y, z;
    static double yaw, pitch;

    private static double kPt = Double.NaN, kX, kY, kZ, kYaw, kPitch;
    private static Perspective kPersp;
    private static boolean kOk;

    static boolean compute(MinecraftClient mc, float pt) {
        if (mc.world == null || mc.player == null || mc.options == null) return false;
        Entity cam = mc.getCameraEntity();
        if (cam == null) cam = mc.player;
        Vec3d lp = cam.getLerpedPos(pt);
        double ex = lp.x, ey = lp.y + (cam.getEyeY() - cam.getY()), ez = lp.z;
        double cy = cam.getYaw(pt), cpi = cam.getPitch(pt);
        Perspective persp = mc.options.getPerspective();

        // gleiche Eingabe wie im letzten Aufruf (selber Frame) -> Ergebnis wiederverwenden
        if (pt == kPt && ex == kX && ey == kY && ez == kZ && cy == kYaw && cpi == kPitch && persp == kPersp) {
            return kOk;
        }
        kPt = pt; kX = ex; kY = ey; kZ = ez; kYaw = cy; kPitch = cpi; kPersp = persp;

        if (persp.isFirstPerson()) {
            x = ex; y = ey; z = ez; yaw = cy; pitch = cpi;
            return kOk = true;
        }

        double yw = cy, pi = cpi;
        if (persp.isFrontView()) { // Blick von vorn auf den Spieler
            yw += 180.0;
            pi = -pi;
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
        x = ex - fx * d;
        y = ey - fy * d;
        z = ez - fz * d;
        yaw = yw;
        pitch = pi;
        return kOk = true;
    }
}
