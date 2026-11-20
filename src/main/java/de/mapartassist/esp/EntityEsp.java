package de.mapartassist.esp;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity-ESP (Teil der ESP): Umrisse fuer gewaehlte Entity-Typen, z. B. Enderperlen (Pearl-Stasis),
 * Item Frames, Armor Stands, Endkristalle. Gleiche HUD-Projektion wie die Block-ESP.
 */
public final class EntityEsp {
    private EntityEsp() {}

    private static final int MAX_DRAW = 200;
    private static final double[] PX = new double[8], PY = new double[8], PZ = new double[8];
    private static Set<String> cache = new HashSet<>();
    private static int cacheHash = 0;

    public static String idOf(EntityType<?> t) { return Registries.ENTITY_TYPE.getId(t).toString(); }

    public static boolean isSelected(EntityType<?> t) { return ModConfig.get().entityEsp.contains(idOf(t)); }

    public static void toggle(EntityType<?> t) {
        var l = ModConfig.get().entityEsp;
        String id = idOf(t);
        if (!l.remove(id)) l.add(id);
        ModConfig.save();
    }

    public static void clear() {
        ModConfig.get().entityEsp.clear();
        ModConfig.save();
    }

    /** Fuegt Typen per ID hinzu; gibt die Anzahl neu hinzugefuegter zurueck. */
    public static int addAll(String... ids) {
        var l = ModConfig.get().entityEsp;
        int n = 0;
        for (String id : ids) {
            if (!l.contains(id)) {
                l.add(id);
                n++;
            }
        }
        if (n > 0) ModConfig.save();
        return n;
    }

    public static int colorFor(String path) {
        switch (path) {
            case "ender_pearl": return 0xFFB04BFF;
            case "end_crystal": return 0xFFFF6EC7;
            case "item_frame": case "glow_item_frame": return 0xFFFFA500;
            case "armor_stand": return 0xFFFFD43B;
            case "tnt": case "tnt_minecart": return 0xFFFF3B30;
            case "item": return 0xFF33E6FF;
            default:
                float hue = ((path.hashCode() & 0x7FFFFFFF) % 360) / 360f;
                return 0xFF000000 | (Color.HSBtoRGB(hue, 0.8f, 1f) & 0xFFFFFF);
        }
    }

    private static Set<String> selected(ModConfig cfg) {
        int h = cfg.entityEsp.hashCode();
        if (h != cacheHash || cache.size() != cfg.entityEsp.size()) {
            cache = new HashSet<>(cfg.entityEsp);
            cacheHash = h;
        }
        return cache;
    }

    public static void render(DrawContext g, MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.entityEspEnabled || mc.world == null || mc.player == null || mc.options == null) return;
        if (mc.options.hudHidden || cfg.entityEsp.isEmpty()) return;
        Set<String> sel = selected(cfg);

        float pt = BlockEsp.partial();
        if (!CamPose.compute(mc, pt)) return;
        double ex = CamPose.x, ey = CamPose.y, ez = CamPose.z;
        double yaw = Math.toRadians(CamPose.yaw);
        double pitch = Math.toRadians(CamPose.pitch);
        double sy = Math.sin(yaw), cyw = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double fx = -sy * cp, fy = -sp, fz = cyw * cp;
        double rx = -cyw, ry = 0, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = cyw * sp;
        double tanHalf = CamPose.tanHalf(mc, pt);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double kx = (sw / 2.0) / (tanHalf * aspect);
        double ky = (sh / 2.0) / tanHalf;
        double midX = sw / 2.0, midY = sh / 2.0;
        double range = Math.max(8, cfg.entityEspRange);
        EspDraw.style(cfg.entityEspStyle);
        double range2 = range * range;
        int drawn = 0;

        for (Entity o : mc.world.getEntities()) {
            if (drawn >= MAX_DRAW) break;
            if (o == mc.player || o.isRemoved()) continue;
            String id = idOf(o.getType());
            if (!sel.contains(id)) continue;
            Vec3d pos = o.getLerpedPos(pt);
            double dx = pos.x - ex, dy = pos.y - ey, dz = pos.z - ez;
            double dist2 = dx * dx + dy * dy + dz * dz;
            if (dist2 > range2) continue;
            double hw = Math.max(0.15, o.getWidth() / 2.0), h = Math.max(0.2, o.getHeight());
            double zc = dx * fx + (dy + h / 2) * fy + dz * fz;
            if (zc < -2.0) continue;
            int color = cfg.entityEspStyle.box(colorFor(Registries.ENTITY_TYPE.getId(o.getType()).getPath()));

            for (int c = 0; c < 8; c++) {
                double ax = pos.x + ((c & 1) == 0 ? -hw : hw) - ex;
                double ay = pos.y + (((c >> 1) & 1) == 0 ? 0 : h) - ey;
                double az = pos.z + (((c >> 2) & 1) == 0 ? -hw : hw) - ez;
                PX[c] = ax * rx + ay * ry + az * rz;
                PY[c] = ax * ux + ay * uy + az * uz;
                PZ[c] = ax * fx + ay * fy + az * fz;
            }
            double mxr = dx, myr = dy + h / 2, mzr = dz;
            double mz = mxr * fx + myr * fy + mzr * fz;
            double mx = mxr * rx + myr * ry + mzr * rz;
            double my = mxr * ux + myr * uy + mzr * uz;
            boolean centerVisible = mz > BlockEsp.NEAR;
            double sizePx = centerVisible ? ky * Math.max(h, hw * 2) / mz : 0;

            boolean any = false;
            if (centerVisible && sizePx < 6.0) {
                double px = midX + mx * kx / mz;
                double py = midY - my * ky / mz;
                if (px >= -4 && px <= sw + 4 && py >= -4 && py <= sh + 4) {
                    EspDraw.dot(g, px, py, color);
                    any = true;
                }
            } else {
                for (int c = 0; c < 8; c++) {
                    for (int bit = 0; bit < 3; bit++) {
                        if ((c & (1 << bit)) != 0) continue;
                        int d = c | (1 << bit);
                        any |= BlockEsp.edge(g, PX[c], PY[c], PZ[c], PX[d], PY[d], PZ[d],
                                midX, midY, kx, ky, sw, sh, color);
                    }
                }
            }
            if (cfg.entityEspTracers && centerVisible && drawn < 120) {
                double tx = midX + mx * kx / mz, ty = midY - my * ky / mz;
                BlockEsp.clippedLine(g, midX, midY, tx, ty, sw, sh, cfg.entityEspStyle.tracer(color));
                any = true;
            }
            if (cfg.entityEspLabels && centerVisible && sizePx >= 6.0) {
                int lx = (int) Math.round(midX + mx * kx / mz);
                int ly = (int) Math.round(midY - my * ky / mz) + (int) (sizePx / 2) + 2;
                if (lx > -100 && lx < sw + 100 && ly > -20 && ly < sh + 20) {
                    String t = o.getType().getName().getString() + " " + Math.round(Math.sqrt(dist2)) + "m";
                    g.drawTextWithShadow(mc.textRenderer, t, lx - mc.textRenderer.getWidth(t) / 2, ly, 0xFFFFFFFF);
                    any = true;
                }
            }
            if (any) drawn++;
        }
    }
}
