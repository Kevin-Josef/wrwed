package de.mapartassist.litematica;

import de.mapartassist.LitematicaBridge;
import de.mapartassist.config.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fortschritt der geladenen Schematic: vergleicht Block fuer Block die Schematic-Welt mit der echten Welt.
 * Der Scan laeuft in kleinen Happen ueber mehrere Ticks. Hinweis: Der Client kennt nur geladene Chunks,
 * alles weiter weg zaehlt als "noch offen".
 */
public final class SchematicProgress {
    private SchematicProgress() {}

    public static final class Stat {
        public final Block block;
        public int total, done;

        Stat(Block block) { this.block = block; }
    }

    public static String status = "Noch nicht geprüft.";

    private static Map<Block, Stat> stats = new HashMap<>();
    private static Map<Block, Stat> building;
    private static List<int[]> boxes;
    private static int boxIdx, cx, cy, cz;
    private static boolean scanning = false;
    private static long scannedBlocks = 0, totalVolume = 0;
    private static int version = 0;
    private static long finishedAtTick = -1000;

    private static Set<Block> excluded = null;

    public static boolean scanning() { return scanning; }

    /** Aendert sich bei jedem fertigen Scan / jeder Aenderung der Ausschluesse. */
    public static int version() { return version; }

    public static boolean hasData() { return !stats.isEmpty(); }

    // ------------------------------------------------------------------ Ausschluesse

    private static Set<Block> excludedSet() {
        if (excluded == null) {
            excluded = new HashSet<>();
            for (String id : ModConfig.get().progressExcluded) {
                try {
                    Block b = Registries.BLOCK.get(Identifier.of(id));
                    excluded.add(b);
                } catch (Exception ignored) {
                }
            }
        }
        return excluded;
    }

    public static boolean isExcluded(Block b) { return excludedSet().contains(b); }

    public static void toggleExcluded(Block b) {
        String id = Registries.BLOCK.getId(b).toString();
        List<String> l = ModConfig.get().progressExcluded;
        if (!l.remove(id)) l.add(id);
        excluded = null;
        ModConfig.save();
        version++;
    }

    public static void clearExcluded() {
        ModConfig.get().progressExcluded.clear();
        excluded = null;
        ModConfig.save();
        version++;
    }

    // ------------------------------------------------------------------ Scan

    public static void start(MinecraftClient mc) {
        if (scanning) return;
        if (mc.world == null) return;
        if (LitematicaBridge.getSchematicWorld() == null) {
            status = "Keine Schematic geladen (oder Litematica fehlt).";
            return;
        }
        List<int[]> b = LitematicaBridge.getPlacementBoxes();
        if (b == null || b.isEmpty()) {
            status = "Platzierung nicht lesbar. Ist eine Schematic platziert und aktiv?";
            return;
        }
        long vol = 0;
        for (int[] x : b) vol += (long) (x[3] - x[0] + 1) * (x[4] - x[1] + 1) * (x[5] - x[2] + 1);
        if (vol <= 0 || vol > 120_000_000L) {
            status = "Bereich zu groß oder ungültig.";
            return;
        }
        boxes = b;
        totalVolume = vol;
        boxIdx = 0;
        cx = boxes.get(0)[0];
        cy = boxes.get(0)[1];
        cz = boxes.get(0)[2];
        building = new HashMap<>();
        scannedBlocks = 0;
        scanning = true;
        status = "Prüfe …";
    }

    /** Jeden Tick aufrufen. */
    public static void tick(MinecraftClient mc, long tickCounter) {
        ModConfig cfg = ModConfig.get();
        if (!scanning) {
            if (cfg.progressHud && mc.world != null && tickCounter - finishedAtTick > 200) start(mc);
            return;
        }
        World schem = LitematicaBridge.getSchematicWorld();
        if (mc.world == null || schem == null) {
            scanning = false;
            status = "Abgebrochen (Welt/Schematic nicht mehr da).";
            return;
        }
        long end = System.nanoTime() + 4_000_000L; // max. ca. 4 ms pro Tick
        BlockPos.Mutable m = new BlockPos.Mutable();
        int counter = 0;
        while (true) {
            if ((++counter & 255) == 0 && System.nanoTime() > end) return;
            int[] b = boxes.get(boxIdx);
            m.set(cx, cy, cz);
            BlockState sb = schem.getBlockState(m);
            if (!sb.isAir()) {
                Block block = sb.getBlock();
                Stat st = building.get(block);
                if (st == null) {
                    st = new Stat(block);
                    building.put(block, st);
                }
                st.total++;
                if (mc.world.getBlockState(m).getBlock() == block) st.done++;
            }
            scannedBlocks++;

            // Cursor weiter: x, dann z, dann y
            if (++cx > b[3]) {
                cx = b[0];
                if (++cz > b[5]) {
                    cz = b[2];
                    if (++cy > b[4]) {
                        if (++boxIdx >= boxes.size()) {
                            finish(tickCounter);
                            return;
                        }
                        int[] nb = boxes.get(boxIdx);
                        cx = nb[0]; cy = nb[1]; cz = nb[2];
                    }
                }
            }
        }
    }

    private static void finish(long tickCounter) {
        stats = building;
        building = null;
        scanning = false;
        finishedAtTick = tickCounter;
        version++;
        status = "Geprüft: " + scannedBlocks + " Positionen. Nicht geladene Chunks zählen als offen.";
    }

    // ------------------------------------------------------------------ Auswertung

    /** Alle Block-Arten der Schematic, die meisten zuerst. */
    public static List<Stat> sorted() {
        List<Stat> l = new ArrayList<>(stats.values());
        l.sort((a, b) -> Integer.compare(b.total, a.total));
        return l;
    }

    /** {fertig, gesamt} unter Beachtung der Ausschluesse. */
    public static long[] totals() {
        boolean exclDone = ModConfig.get().progressExcludedDone;
        long done = 0, total = 0;
        for (Stat s : stats.values()) {
            if (isExcluded(s.block)) {
                if (exclDone) {
                    done += s.total;
                    total += s.total;
                }
            } else {
                done += s.done;
                total += s.total;
            }
        }
        return new long[]{done, total};
    }

    public static double percent() {
        long[] t = totals();
        return t[1] == 0 ? 0.0 : 100.0 * t[0] / t[1];
    }

    public static String hudText() {
        if (stats.isEmpty()) return scanning ? "Schematic: prüfe …" : null;
        long[] t = totals();
        return String.format(Locale.ROOT, "Schematic: %.1f %%  (%d / %d)%s", percent(), t[0], t[1], scanning ? "  …" : "");
    }
}
