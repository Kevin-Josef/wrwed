package de.mapartassist.esp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Glatte Linien fuer die ESPs. Statt Pixeltreppen aus GUI-Pixeln (bei GUI-Skalierung 2-4 sehr grob) wird jede
 * Linie als gedrehter, weich auslaufender Streifen in voller Bildschirmaufloesung gezeichnet.
 * Koordinaten kommen weiterhin in GUI-Einheiten (double) an, die Umrechnung passiert hier.
 */
final class EspDraw {
    private EspDraw() {}

    /** Teilpixel pro echtem Bildschirmpixel (fillGradient nimmt nur ganze Zahlen). */
    private static final int Q = 4;
    /** Halbe Linienbreite in Teilpixeln: 6 = 1,5 echte Pixel pro Seite, weich auslaufend. Groesser = dicker. */
    private static final int HALF = 6;

    static void line(DrawContext g, double x0, double y0, double x1, double y1, int color) {
        if ((color >>> 24) == 0) return;
        double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
        double dx = x1 - x0, dy = y1 - y0;
        double lenPx = Math.sqrt(dx * dx + dy * dy) * s;
        if (lenPx < 0.05) { dot(g, x0, y0, color); return; }
        int len = (int) Math.round(lenPx * Q);
        int clear = color & 0xFFFFFF;
        float k = (float) (1.0 / (s * Q));
        var m = g.getMatrices();
        m.pushMatrix();
        m.translate((float) x0, (float) y0);
        m.rotate((float) Math.atan2(dy, dx));
        m.scale(k, k);
        // obere Haelfte: transparent -> Farbe, untere Haelfte: Farbe -> transparent (weiche Kanten)
        g.fillGradient(-2, -HALF, len + 2, 0, clear, color);
        g.fillGradient(-2, 0, len + 2, HALF, color, clear);
        m.popMatrix();
    }

    /** Kleiner weicher Punkt fuer weit entfernte Ziele. */
    static void dot(DrawContext g, double x, double y, int color) {
        if ((color >>> 24) == 0) return;
        double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float k = (float) (1.0 / (s * Q));
        int soft = (color & 0xFFFFFF) | ((((color >>> 24) * 2) / 5) << 24);
        var m = g.getMatrices();
        m.pushMatrix();
        m.translate((float) x, (float) y);
        m.scale(k, k);
        g.fill(-8, -8, 8, 8, soft);
        g.fill(-5, -5, 5, 5, color);
        m.popMatrix();
    }
}
