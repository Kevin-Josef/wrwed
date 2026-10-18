package de.mapartassist.cape;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Kleine Farbraster (Aussen-/Innenseite) fuer die Vorschau im Menue. Kein Textur-API noetig. */
public final class CapePreview {
    private CapePreview() {}

    public static final int GW = 20, GH = 32;

    /** ARGB-Raster GW x GH. */
    public record Data(int[] outer, int[] inner) {}

    private static final Map<String, Data> cache = new HashMap<>();
    private static final Set<String> failed = new HashSet<>();

    public static void clear() {
        cache.clear();
        failed.clear();
    }

    public static Data get(String name) {
        if (name == null || name.isEmpty()) return null;
        Data d = cache.get(name);
        if (d != null) return d;
        if (failed.contains(name)) return null;
        try {
            CapeManager.Entry e = CapeManager.find(name);
            if (e == null) throw new IllegalStateException("fehlt");
            BufferedImage img = CapeManager.readNormalized(e.cape());
            int k = img.getWidth() / 64;
            if (k < 1) throw new IllegalStateException("zu klein");
            d = new Data(sample(img, 1 * k, 1 * k, 10 * k, 16 * k), sample(img, 12 * k, 1 * k, 10 * k, 16 * k));
            cache.put(name, d);
            return d;
        } catch (Exception ex) {
            failed.add(name);
            return null;
        }
    }

    private static int[] sample(BufferedImage img, int ox, int oy, int rw, int rh) {
        int[] out = new int[GW * GH];
        for (int gy = 0; gy < GH; gy++) {
            for (int gx = 0; gx < GW; gx++) {
                int x0 = ox + gx * rw / GW, x1 = Math.max(x0 + 1, ox + (gx + 1) * rw / GW);
                int y0 = oy + gy * rh / GH, y1 = Math.max(y0 + 1, oy + (gy + 1) * rh / GH);
                long sa = 0, sr = 0, sg = 0, sb = 0, n = 0;
                for (int y = y0; y < y1 && y < img.getHeight(); y++) {
                    for (int x = x0; x < x1 && x < img.getWidth(); x++) {
                        int c = img.getRGB(x, y);
                        int a = c >>> 24;
                        sa += a;
                        sr += (long) ((c >> 16) & 0xFF) * a;
                        sg += (long) ((c >> 8) & 0xFF) * a;
                        sb += (long) (c & 0xFF) * a;
                        n++;
                    }
                }
                if (n == 0 || sa == 0) { out[gy * GW + gx] = 0; continue; }
                int a = (int) (sa / n);
                int r = (int) (sr / sa), g = (int) (sg / sa), b = (int) (sb / sa);
                out[gy * GW + gx] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }
}
