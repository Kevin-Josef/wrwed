package de.mapartassist.cape;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Baut aus einem beliebigen Bild (PNG/JPG/...) ein Cape im 64x32-Layout.
 * Aufloesung bleibt erhalten: die Datei wird so gross, dass das Motiv nicht verkleinert wird.
 */
public final class CapeCreator {
    private CapeCreator() {}

    public enum Fit {
        CROP("Zentriert zuschneiden"), FIT("Einpassen (Rand transparent)"), STRETCH("Strecken");
        public final String label;
        Fit(String l) { label = l; }
    }

    public enum Inner {
        MIRROR("Spiegelbild"), EDGE("Randfarbe"), DARK("Abgedunkelt");
        public final String label;
        Inner(String l) { label = l; }
    }

    private static final int MAX_K = 32; // max. 2048x1024

    /** Erstellt das Cape und legt es in der Bibliothek ab. Liefert Fehlertext oder null. */
    public static String create(Path src, Fit fit, Inner inner) {
        String file = src.getFileName().toString();
        String base = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        try {
            BufferedImage img = ImageIO.read(src.toFile());
            if (img == null) return "Bild nicht lesbar (bitte PNG oder JPG): " + file;
            BufferedImage canvas = build(img, fit, inner);
            CapeManager.saveCreated(base, canvas);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return "Cape-Creator-Fehler: " + e.getMessage();
        }
    }

    private static BufferedImage build(BufferedImage img, Fit fit, Inner inner) {
        int sw = img.getWidth(), sh = img.getHeight();
        double ratio = 10.0 / 16.0;

        // Quell-Ausschnitt und Aufloesung (k = Pixel pro Cape-Einheit)
        int sx = 0, sy = 0, cw = sw, ch = sh;
        double basis;
        switch (fit) {
            case CROP -> {
                if (sw / (double) sh > ratio) { cw = (int) Math.round(sh * ratio); ch = sh; }
                else { cw = sw; ch = (int) Math.round(sw / ratio); }
                sx = (sw - cw) / 2;
                sy = (sh - ch) / 2;
                basis = cw / 10.0;
            }
            case FIT -> basis = (sw / (double) sh < ratio) ? sh / 16.0 : sw / 10.0;
            default -> basis = Math.max(sw / 10.0, sh / 16.0);
        }
        int k = (int) Math.max(1, Math.min(MAX_K, Math.round(basis)));

        // Aussenflaeche (10x16 Einheiten)
        int fw = 10 * k, fh = 16 * k;
        BufferedImage face = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fg = quality(face.createGraphics());
        switch (fit) {
            case CROP -> fg.drawImage(img, 0, 0, fw, fh, sx, sy, sx + cw, sy + ch, null);
            case STRETCH -> fg.drawImage(img, 0, 0, fw, fh, 0, 0, sw, sh, null);
            case FIT -> {
                double sc = Math.min(fw / (double) sw, fh / (double) sh);
                int dw = (int) Math.round(sw * sc), dh = (int) Math.round(sh * sc);
                int dx = (fw - dw) / 2, dy = (fh - dh) / 2;
                fg.drawImage(img, dx, dy, dx + dw, dy + dh, 0, 0, sw, sh, null);
            }
        }
        fg.dispose();

        BufferedImage out = new BufferedImage(64 * k, 32 * k, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(out.createGraphics());

        // Aussen (1,1)
        g.drawImage(face, 1 * k, 1 * k, null);

        // Innen (12,1)
        int ix = 12 * k, iy = 1 * k;
        switch (inner) {
            case MIRROR -> g.drawImage(face, ix + fw, iy, ix, iy + fh, 0, 0, fw, fh, null);
            case DARK -> {
                g.drawImage(face, ix, iy, null);
                g.setColor(new Color(0, 0, 0, 130));
                g.fillRect(ix, iy, fw, fh);
            }
            default -> {
                g.setColor(new Color(edgeColor(face)));
                g.fillRect(ix, iy, fw, fh);
            }
        }

        // Kanten: jeweils die Randpixel der Aussenflaeche in die schmalen Streifen ziehen
        g.drawImage(face.getSubimage(0, 0, fw, 1), 1 * k, 0, 1 * k + fw, k, 0, 0, fw, 1, null);              // oben
        g.drawImage(face.getSubimage(0, fh - 1, fw, 1), 11 * k, 0, 11 * k + fw, k, 0, 0, fw, 1, null);      // unten
        g.drawImage(face.getSubimage(0, 0, 1, fh), 0, 1 * k, k, 1 * k + fh, 0, 0, 1, fh, null);              // links
        g.drawImage(face.getSubimage(fw - 1, 0, 1, fh), 11 * k, 1 * k, 12 * k, 1 * k + fh, 0, 0, 1, fh, null); // rechts
        g.dispose();
        return out;
    }

    private static Graphics2D quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** Durchschnittsfarbe (opak) der Randpixel. */
    private static int edgeColor(BufferedImage f) {
        long r = 0, gr = 0, b = 0, n = 0;
        int w = f.getWidth(), h = f.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y : new int[]{0, h - 1}) { int[] c = add(f.getRGB(x, y)); if (c != null) { r += c[0]; gr += c[1]; b += c[2]; n++; } }
        }
        for (int y = 1; y < h - 1; y++) {
            for (int x : new int[]{0, w - 1}) { int[] c = add(f.getRGB(x, y)); if (c != null) { r += c[0]; gr += c[1]; b += c[2]; n++; } }
        }
        if (n == 0) return 0xFF303030;
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (gr / n) << 8) | (int) (b / n);
    }

    private static int[] add(int argb) {
        if ((argb >>> 24) < 16) return null;
        return new int[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF};
    }
}
