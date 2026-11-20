package de.mapartassist.config;

/**
 * Aussehen einer ESP (Block, Entity oder Spieler). Standard: die bisherigen Farben, kein Glow.
 * Alle Farben sind RGB ohne Alpha.
 */
public class EspStyle {
    /** Eigene Farbe fuer Umriss/Punkt statt der Standardfarbe. */
    public boolean customColor = false;
    public int color = 0xFF5555;
    /** Eigene Farbe fuer die Tracer-Linie (sonst Farbe des Umrisses). */
    public boolean customTracer = false;
    public int tracerColor = 0xFFFFFF;
    /** Leuchten um die Linien, Breite in echten Bildschirmpixeln. */
    public boolean glow = false;
    public int glowWidth = 4;

    /** Farbe fuer Umriss/Punkt; def ist die Standardfarbe (ARGB). */
    public int box(int def) {
        return customColor ? (0xFF000000 | (color & 0xFFFFFF)) : def;
    }

    /** Tracer-Farbe (mit leichter Transparenz); boxColor ist die schon aufgeloeste Umrissfarbe. */
    public int tracer(int boxColor) {
        int rgb = customTracer ? (tracerColor & 0xFFFFFF) : (boxColor & 0xFFFFFF);
        return rgb | 0x99000000;
    }

    public void clamp() {
        glowWidth = Math.max(1, Math.min(12, glowWidth <= 0 ? 4 : glowWidth));
        color &= 0xFFFFFF;
        tracerColor &= 0xFFFFFF;
    }
}
