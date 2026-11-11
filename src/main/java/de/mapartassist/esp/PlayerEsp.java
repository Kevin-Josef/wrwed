package de.mapartassist.esp;

import de.mapartassist.config.ModConfig;
import de.mapartassist.mini.Minimi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Spieler-ESP: Umrissbox, optional Linie (Tracer) und Name + Entfernung fuer andere Spieler.
 * Gleiche HUD-Projektion wie die Block-ESP, nur in der Ego-Ansicht.
 */
public final class PlayerEsp {
    private PlayerEsp() {}

    private static final int COLOR = 0xFFFF5555;
    private static final int MAX_DRAW = 60;
    private static final double[] PX = new double[8], PY = new double[8], PZ = new double[8];

    public static void render(DrawContext g, MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.playerEspEnabled || mc.world == null || mc.player == null || mc.options == null) return;
        if (mc.options.hudHidden) return;

        float pt = BlockEsp.partial();
        if (!CamPose.compute(mc, pt)) return;
        double ex = CamPose.x, ey = CamPose.y, ez = CamPose.z;
        double yaw = Math.toRadians(CamPose.yaw);
        double pitch = Math.toRadians(CamPose.pitch);
        double sy = Math.sin(yaw), cyw = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double fx = -sy * cp, fy = -sp, fz = cyw * cp;
        double rx = -cyw, ry = 0, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = cyw * sp;
        float fovMulNow = BlockEsp.lastFovMul + (BlockEsp.fovMul - BlockEsp.lastFovMul) * pt;
        double fov = mc.options.getFov().getValue() * fovMulNow;
        double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double kx = (sw / 2.0) / (tanHalf * aspect);
        double ky = (sh / 2.0) / tanHalf;
        double midX = sw / 2.0, midY = sh / 2.0;
        double range2 = (double) cfg.playerEspRange * cfg.playerEspRange;
        int drawn = 0;

        for (PlayerEntity o : mc.world.getPlayers()) {
            if (drawn >= MAX_DRAW) break;
            if (o == mc.player || o.isSpectator() || Minimi.isMini(o)) continue;
            Vec3d pos = o.getLerpedPos(pt);
            double dx = pos.x - ex, dy = pos.y - ey, dz = pos.z - ez;
            if (dx * dx + dy * dy + dz * dz > range2) continue;
            double hw = o.getWidth() / 2.0, h = o.getHeight();
            double zc = (dx * fx + (dy + h / 2) * fy + dz * fz);
            if (zc < -2.0) continue; // komplett hinter der Kamera

            for (int c = 0; c < 8; c++) {
                double ax = pos.x + ((c & 1) == 0 ? -hw : hw) - ex;
                double ay = pos.y + (((c >> 1) & 1) == 0 ? 0 : h) - ey;
                double az = pos.z + (((c >> 2) & 1) == 0 ? -hw : hw) - ez;
                PX[c] = ax * rx + ay * ry + az * rz;
                PY[c] = ax * ux + ay * uy + az * uz;
                PZ[c] = ax * fx + ay * fy + az * fz;
            }
            // Mitte der Box
            double mxr = dx, myr = dy + h / 2, mzr = dz;
            double mz = mxr * fx + myr * fy + mzr * fz;
            double mx = mxr * rx + myr * ry + mzr * rz;
            double my = mxr * ux + myr * uy + mzr * uz;
            boolean centerVisible = mz > BlockEsp.NEAR;
            double sizePx = centerVisible ? ky * h / mz : 0; // Hoehe der Box in Pixeln

            boolean any = false;
            if (centerVisible && sizePx < 6.0) {
                int px = (int) Math.round(midX + mx * kx / mz);
                int py = (int) Math.round(midY - my * ky / mz);
                if (px >= -4 && px <= sw + 4 && py >= -4 && py <= sh + 4) {
                    g.fill(px - 1, py - 1, px + 2, py + 2, COLOR);
                    any = true;
                }
            } else {
                for (int c = 0; c < 8; c++) {
                    for (int bit = 0; bit < 3; bit++) {
                        if ((c & (1 << bit)) != 0) continue;
                        int d = c | (1 << bit);
                        any |= BlockEsp.edge(g, PX[c], PY[c], PZ[c], PX[d], PY[d], PZ[d],
                                midX, midY, kx, ky, sw, sh, COLOR);
                    }
                }
            }
            if (cfg.playerEspTracers && centerVisible) {
                double tx = midX + mx * kx / mz, ty = midY - my * ky / mz;
                BlockEsp.clippedLine(g, midX, midY, tx, ty, sw, sh, (COLOR & 0xFFFFFF) | 0x99000000);
                any = true;
            }
            if (cfg.playerEspLabels && centerVisible) {
                // Label unter den Fuessen (kollidiert nicht mit der Item-Anzeige ueber dem Kopf)
                double fx0 = dx * rx + dy * ry + dz * rz;
                double fy0 = dx * ux + dy * uy + dz * uz;
                double fz0 = dx * fx + dy * fy + dz * fz;
                if (fz0 > BlockEsp.NEAR) {
                    int lx = (int) Math.round(midX + fx0 * kx / fz0);
                    int ly = (int) Math.round(midY - fy0 * ky / fz0) + 3;
                    if (lx > -100 && lx < sw + 100 && ly > -20 && ly < sh + 20) {
                        String t = o.getName().getString() + " " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
                        g.drawTextWithShadow(mc.textRenderer, t, lx - mc.textRenderer.getWidth(t) / 2, ly, 0xFFFFFFFF);
                        any = true;
                    }
                }
            }
            if (any) drawn++;
        }
    }
}
