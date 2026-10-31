package de.mapartassist.esp;

import de.mapartassist.config.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Block-ESP: zeigt ausgewaehlte Bloecke als Umrisse durch Waende.
 *
 * Gezeichnet wird in 2D ueber das HUD: Die Block-Ecken werden mit der Kamera (Position, Blickrichtung, FOV)
 * auf den Bildschirm projiziert. Das braucht keine eigene Render-Pipeline und keine Mixins, deshalb ist es
 * unempfindlich gegen Aenderungen im Minecraft-Rendering.
 *
 * Der Scan laeuft in kleinen Happen ueber mehrere Ticks (kein Ruckeln).
 */
public final class BlockEsp {
    private BlockEsp() {}

    /** Ein gefundener Block. */
    private static final class Hit {
        final int x, y, z, color, d2;
        final Block block;

        Hit(int x, int y, int z, Block block, int color, int d2) {
            this.x = x; this.y = y; this.z = z; this.block = block; this.color = color; this.d2 = d2;
        }
    }

    private static final int BUDGET = 40_000;   // Rechenbudget pro Tick (leerer Abschnitt ~1-3, voller Scan 4096)
    private static final int HARD_CAP = 4_000;  // maximal gemerkte Treffer
    private static final double NEAR = 0.05;

    private static Set<Block> selected = new HashSet<>();
    private static int version = 1, seenVersion = 0;

    private static List<Hit> result = new ArrayList<>();
    private static List<Hit> building = null;
    private static boolean scanning = false;
    private static int cooldown = 0;
    private static int sOx, sOy, sOz, sR, sMinY, sMaxY;
    private static int minCx, maxCx, minCz, maxCz, curCx, curCz, curSec;
    private static WorldChunk curChunk = null;
    /** Schneller Vorab-Test pro Chunk-Abschnitt (prueft nur die Palette, nicht jeden Block). */
    private static final Predicate<BlockState> IS_SELECTED = st -> selected.contains(st.getBlock());
    private static int scanRange = -1;
    private static ClientWorld lastWorld = null;

    // Tick-Zeitpunkt fuer die Zwischenschritte zwischen zwei Ticks
    private static long tickNs = System.nanoTime();
    // FOV-Aufweitung beim Sprinten (wie vanilla: pro Tick halber Weg zum Ziel)
    private static float fovMul = 1f, lastFovMul = 1f;

    // wiederverwendete Hilfsfelder (keine Allokation pro Frame)
    private static final double[] PX = new double[8], PY = new double[8], PZ = new double[8];
    private static final BlockPos.Mutable MPOS = new BlockPos.Mutable();

    // ------------------------------------------------------------------ Auswahl

    /** Muss aufgerufen werden, wenn sich die Auswahl oder die Reichweite aendert. */
    public static void invalidate() { version++; }

    public static int found() { return result.size(); }

    public static boolean isSelected(Block b) { return ModConfig.get().espBlocks.contains(idOf(b)); }

    public static void toggle(Block b) {
        List<String> l = ModConfig.get().espBlocks;
        String id = idOf(b);
        if (!l.remove(id)) l.add(id);
        ModConfig.save();
        invalidate();
    }

    public static void clear() {
        ModConfig.get().espBlocks.clear();
        ModConfig.save();
        invalidate();
    }

    /** Alle Erze (…_ore) und Antiker Schutt. Gibt die Anzahl neu hinzugefuegter Bloecke zurueck. */
    public static int addOres() {
        int n = 0;
        for (Block b : Registries.BLOCK) {
            String p = Registries.BLOCK.getId(b).getPath();
            if (p.endsWith("_ore") || p.equals("ancient_debris")) n += add(b) ? 1 : 0;
        }
        if (n > 0) { ModConfig.save(); invalidate(); }
        return n;
    }

    /** Kisten, Fass, Endertruhe und Spawner. */
    public static int addStorage() {
        Set<String> names = Set.of("chest", "trapped_chest", "ender_chest", "barrel", "spawner", "trial_spawner");
        int n = 0;
        for (Block b : Registries.BLOCK) {
            if (names.contains(Registries.BLOCK.getId(b).getPath())) n += add(b) ? 1 : 0;
        }
        if (n > 0) { ModConfig.save(); invalidate(); }
        return n;
    }

    private static boolean add(Block b) {
        List<String> l = ModConfig.get().espBlocks;
        String id = idOf(b);
        if (l.contains(id)) return false;
        l.add(id);
        return true;
    }

    private static String idOf(Block b) { return Registries.BLOCK.getId(b).toString(); }

    private static void rebuildSelected() {
        Set<Block> s = new HashSet<>();
        for (String id : ModConfig.get().espBlocks) {
            try {
                Block b = Registries.BLOCK.get(Identifier.of(id));
                if (b != Blocks.AIR) s.add(b);
            } catch (Exception ignored) {
            }
        }
        selected = s;
    }

    // ------------------------------------------------------------------ Farben

    /** Feste Farben fuer bekannte Bloecke, sonst eine stabile Farbe aus dem Namen. */
    public static int colorFor(Block b) {
        String p = Registries.BLOCK.getId(b).getPath();
        if (p.contains("diamond")) return 0xFF33E6FF;
        if (p.contains("emerald")) return 0xFF2EE06A;
        if (p.contains("gold")) return 0xFFFFD43B;
        if (p.contains("iron")) return 0xFFE0B089;
        if (p.contains("coal")) return 0xFF9A9AA6;
        if (p.contains("redstone")) return 0xFFFF3B30;
        if (p.contains("lapis")) return 0xFF3B6BFF;
        if (p.contains("copper")) return 0xFFFF8A3D;
        if (p.contains("debris") || p.contains("netherite")) return 0xFFC77A3A;
        if (p.contains("quartz")) return 0xFFFFFFFF;
        if (p.contains("spawner")) return 0xFFB04BFF;
        if (p.contains("shulker")) return 0xFFE070E0;
        if (p.equals("chest") || p.equals("trapped_chest") || p.equals("barrel")) return 0xFFFFA500;
        if (p.equals("ender_chest")) return 0xFFB04BFF;
        float hue = ((p.hashCode() & 0x7FFFFFFF) % 360) / 360f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.8f, 1f) & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ Scan (jeden Tick)

    public static void tick(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();

        // FOV-Aufweitung (wie vanilla: Sprint = +15 %, pro Tick halber Weg zum Ziel)
        lastFovMul = fovMul;
        float target = mc.player != null && mc.player.isSprinting() ? 1.15f : 1f;
        fovMul += (target - fovMul) * 0.5f;
        tickNs = System.nanoTime();

        if (!cfg.espEnabled || mc.world == null || mc.player == null) {
            if (!result.isEmpty()) result = new ArrayList<>();
            building = null;
            scanning = false;
            return;
        }

        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            result = new ArrayList<>();
            building = null;
            scanning = false;
            cooldown = 0;
            seenVersion = 0;
        }
        if (seenVersion != version || scanRange != cfg.espRange) {
            seenVersion = version;
            scanRange = cfg.espRange;
            rebuildSelected();
            building = null;
            scanning = false;
            cooldown = 0;
            if (selected.isEmpty()) {
                result = new ArrayList<>();
                return;
            }
        }
        if (selected.isEmpty()) return;

        ClientWorld world = mc.world;
        if (!scanning) {
            if (cooldown > 0) { cooldown--; return; }
            BlockPos pp = mc.player.getBlockPos();
            sOx = pp.getX(); sOy = pp.getY(); sOz = pp.getZ();
            sR = Math.max(8, Math.min(128, cfg.espRange));
            int bottom = world.getBottomY();
            int top = bottom + world.getHeight() - 1;
            sMinY = Math.max(bottom, sOy - sR);
            sMaxY = Math.min(top, sOy + sR);
            minCx = (sOx - sR) >> 4; maxCx = (sOx + sR) >> 4;
            minCz = (sOz - sR) >> 4; maxCz = (sOz + sR) >> 4;
            curCx = minCx; curCz = minCz; curSec = 0; curChunk = null;
            building = new ArrayList<>();
            scanning = true;
        }

        int budget = BUDGET;
        int maxD2 = (sR + 2) * (sR + 2);
        while (budget > 0) {
            if (curChunk == null) {
                curChunk = world.getChunkManager().getWorldChunk(curCx, curCz);
                curSec = 0;
                budget -= 8;
            }
            if (curChunk != null) {
                ChunkSection[] secs = curChunk.getSectionArray();
                if (curSec < secs.length) {
                    ChunkSection sec = secs[curSec];
                    int baseY = (curChunk.getBottomSectionCoord() + curSec) << 4;
                    curSec++;
                    if (sec == null || sec.isEmpty() || baseY + 15 < sMinY || baseY > sMaxY) { budget -= 1; continue; }
                    if (!sec.hasAny(IS_SELECTED)) { budget -= 3; continue; }
                    budget -= 4096;
                    scanSection(sec, curCx << 4, baseY, curCz << 4, maxD2);
                    continue;
                }
            }
            // naechste Chunk-Spalte
            curChunk = null;
            if (++curCz > maxCz) {
                curCz = minCz;
                if (++curCx > maxCx) {
                    finishScan();
                    break;
                }
            }
        }
    }

    private static void scanSection(ChunkSection sec, int baseX, int baseY, int baseZ, int maxD2) {
        for (int ly = 0; ly < 16; ly++) {
            int y = baseY + ly;
            if (y < sMinY || y > sMaxY) continue;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState st = sec.getBlockState(lx, ly, lz);
                    if (st.isAir()) continue;
                    Block b = st.getBlock();
                    if (!selected.contains(b)) continue;
                    int x = baseX + lx, z = baseZ + lz;
                    int dx = x - sOx, dy = y - sOy, dz = z - sOz;
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > maxD2) continue;
                    building.add(new Hit(x, y, z, b, colorFor(b), d2));
                }
            }
        }
    }

    private static void finishScan() {
        List<Hit> done = building;
        building = null;
        scanning = false;
        cooldown = 10;
        if (done == null) return;
        done.sort((a, b) -> Integer.compare(a.d2, b.d2));
        if (done.size() > HARD_CAP) done = new ArrayList<>(done.subList(0, HARD_CAP));
        result = done;
    }

    // ------------------------------------------------------------------ Zeichnen (jeden Frame)

    private static float partial() {
        float p = (System.nanoTime() - tickNs) / 50_000_000f;
        return Math.max(0f, Math.min(1f, p));
    }

    public static void render(DrawContext g, MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.espEnabled || mc.world == null || mc.player == null || mc.options == null) return;
        if (mc.options.hudHidden) return;
        if (!mc.options.getPerspective().isFirstPerson()) return; // Projektion gilt nur fuer die Ego-Ansicht
        List<Hit> hits = result;
        if (hits.isEmpty()) return;

        Entity cam = mc.getCameraEntity();
        if (cam == null) cam = mc.player;

        float pt = partial();
        Vec3d lp = cam.getLerpedPos(pt);
        double ex = lp.x, ey = lp.y + (cam.getEyeY() - cam.getY()), ez = lp.z;
        double yaw = Math.toRadians(cam.getYaw(pt));
        double pitch = Math.toRadians(cam.getPitch(pt));
        double sy = Math.sin(yaw), cyw = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);

        // Blickrichtung (f), rechts (r), oben (u)
        double fx = -sy * cp, fy = -sp, fz = cyw * cp;
        double rx = -cyw, ry = 0, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = cyw * sp;

        float fovMulNow = lastFovMul + (fovMul - lastFovMul) * pt;
        double fov = mc.options.getFov().getValue() * fovMulNow;
        double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double kx = (sw / 2.0) / (tanHalf * aspect); // Pixel pro Einheit (x / z)
        double ky = (sh / 2.0) / tanHalf;            // Pixel pro Einheit (y / z)
        double midX = sw / 2.0, midY = sh / 2.0;

        int range = cfg.espRange;
        double range2 = (range + 1.5) * (range + 1.5);
        int maxDraw = Math.max(10, cfg.espMaxBlocks);
        int drawn = 0;

        for (int i = 0; i < hits.size() && drawn < maxDraw; i++) {
            Hit h = hits.get(i);
            double cxr = h.x + 0.5 - ex, cyr = h.y + 0.5 - ey, czr = h.z + 0.5 - ez;
            if (cxr * cxr + cyr * cyr + czr * czr > range2) continue;
            double zc = cxr * fx + cyr * fy + czr * fz;
            if (zc < -1.0) continue; // komplett hinter der Kamera

            // Block noch da? (Abgebaute Bloecke verschwinden sofort, der Scan holt neue erst spaeter)
            MPOS.set(h.x, h.y, h.z);
            if (mc.world.getBlockState(MPOS).getBlock() != h.block) continue;

            // Ecken in Kamera-Koordinaten
            for (int c = 0; c < 8; c++) {
                double ax = h.x + (c & 1) - ex, ay = h.y + ((c >> 1) & 1) - ey, az = h.z + ((c >> 2) & 1) - ez;
                PX[c] = ax * rx + ay * ry + az * rz;
                PY[c] = ax * ux + ay * uy + az * uz;
                PZ[c] = ax * fx + ay * fy + az * fz;
            }

            // Mitte (fuer Punkt und Tracer)
            double mxc = cxr * rx + cyr * ry + czr * rz;
            double myc = cxr * ux + cyr * uy + czr * uz;
            boolean centerVisible = zc > NEAR;
            double size = centerVisible ? ky / zc : 0; // Pixel pro Blockkante

            boolean any = false;
            if (centerVisible && size < 5.0) {
                // weit weg: kleiner Punkt statt Umriss
                int px = (int) Math.round(midX + mxc * kx / zc);
                int py = (int) Math.round(midY - myc * ky / zc);
                if (px >= -4 && px <= sw + 4 && py >= -4 && py <= sh + 4) {
                    g.fill(px - 1, py - 1, px + 2, py + 2, h.color);
                    any = true;
                }
            } else {
                for (int c = 0; c < 8; c++) {
                    for (int bit = 0; bit < 3; bit++) {
                        if ((c & (1 << bit)) != 0) continue;
                        int d = c | (1 << bit);
                        any |= edge(g, PX[c], PY[c], PZ[c], PX[d], PY[d], PZ[d], midX, midY, kx, ky, sw, sh, h.color);
                    }
                }
            }

            if (cfg.espTracers && centerVisible && drawn < 120) {
                double tx = midX + mxc * kx / zc, ty = midY - myc * ky / zc;
                clippedLine(g, midX, midY, tx, ty, sw, sh, (h.color & 0xFFFFFF) | 0x99000000);
            }
            if (any || (cfg.espTracers && centerVisible)) drawn++;
        }
    }

    /** Kante mit Near-Plane-Clipping projizieren und zeichnen. true, wenn etwas sichtbar war. */
    private static boolean edge(DrawContext g, double x0, double y0, double z0, double x1, double y1, double z1,
                                double midX, double midY, double kx, double ky, int sw, int sh, int color) {
        if (z0 < NEAR && z1 < NEAR) return false;
        if (z0 < NEAR) {
            double t = (NEAR - z0) / (z1 - z0);
            x0 += (x1 - x0) * t; y0 += (y1 - y0) * t; z0 = NEAR;
        } else if (z1 < NEAR) {
            double t = (NEAR - z1) / (z0 - z1);
            x1 += (x0 - x1) * t; y1 += (y0 - y1) * t; z1 = NEAR;
        }
        double ax = midX + x0 * kx / z0, ay = midY - y0 * ky / z0;
        double bx = midX + x1 * kx / z1, by = midY - y1 * ky / z1;
        return clippedLine(g, ax, ay, bx, by, sw, sh, color);
    }

    /** Linie am Bildschirmrand abschneiden (Liang-Barsky) und zeichnen. */
    private static boolean clippedLine(DrawContext g, double x0, double y0, double x1, double y1, int sw, int sh, int color) {
        double dx = x1 - x0, dy = y1 - y0;
        double t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - 0, sw - x0, y0 - 0, sh - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return false;
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0) { if (r > t1) return false; if (r > t0) t0 = r; }
                else { if (r < t0) return false; if (r < t1) t1 = r; }
            }
        }
        int ax = (int) Math.round(x0 + dx * t0), ay = (int) Math.round(y0 + dy * t0);
        int bx = (int) Math.round(x0 + dx * t1), by = (int) Math.round(y0 + dy * t1);
        line(g, ax, ay, bx, by, color);
        return true;
    }

    /** 1-Pixel-Linie aus waagerechten bzw. senkrechten Laeufen (wenig fill-Aufrufe). */
    private static void line(DrawContext g, int x0, int y0, int x1, int y1, int c) {
        int dx = x1 - x0, dy = y1 - y0;
        if (dx == 0 && dy == 0) { g.fill(x0, y0, x0 + 1, y0 + 1, c); return; }
        if (Math.abs(dx) >= Math.abs(dy)) {
            if (x0 > x1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
            double m = (double) (y1 - y0) / (x1 - x0);
            int runStart = x0, runY = y0;
            for (int x = x0 + 1; x <= x1; x++) {
                int y = (int) Math.round(y0 + (x - x0) * m);
                if (y != runY) {
                    g.fill(runStart, runY, x, runY + 1, c);
                    runStart = x; runY = y;
                }
            }
            g.fill(runStart, runY, x1 + 1, runY + 1, c);
        } else {
            if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
            double m = (double) (x1 - x0) / (y1 - y0);
            int runStart = y0, runX = x0;
            for (int y = y0 + 1; y <= y1; y++) {
                int x = (int) Math.round(x0 + (y - y0) * m);
                if (x != runX) {
                    g.fill(runX, runStart, runX + 1, y, c);
                    runStart = y; runX = x;
                }
            }
            g.fill(runX, runStart, runX + 1, y1 + 1, c);
        }
    }
}
