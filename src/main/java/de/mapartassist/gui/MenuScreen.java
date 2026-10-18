package de.mapartassist.gui;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Hauptmenue der Mod: links die Reiter (Features), rechts der Inhalt.
 * Neue Features = neuen Eintrag in {@link Tab} + einen Block in init()/render().
 */
public class MenuScreen extends Screen {
    private enum Tab {
        MAPART("Map Art"),
        LOOK("Aussehen"),
        CAPES("Capes");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int W = 380, H = 250, SIDE = 96;
    private static final int S = 100;        // Groesse des Farbfelds
    private static final int HUE_W = 14;     // Breite der Farbton-Leiste

    // bleiben beim erneuten Oeffnen erhalten
    private static Tab activeTab = Tab.MAPART;
    private static boolean editAccent = true;

    private final Screen parent;
    private int left, top, cx, cw;

    // Farbwaehler-Zustand (HSV, jeweils 0..1)
    private float hue, sat, val;
    private int dragMode = 0; // 0 nichts, 1 Farbfeld, 2 Farbton-Leiste
    private boolean wasDown = false;
    private TextFieldWidget hexField;
    private boolean suppressHex = false;

    public MenuScreen(Screen parent) {
        super(Text.literal("MapArt Assist"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        cx = left + SIDE + 8;
        cw = W - SIDE - 16;

        // Reiter links
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(t.label), btn -> {
                activeTab = t;
                clearAndInit();
            }).dimensions(left + 8, top + 30 + i * 24, SIDE - 12, 20).build();
            b.active = t != activeTab;
            addDrawableChild(b);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(left + 8, top + H - 28, SIDE - 12, 20).build());

        switch (activeTab) {
            case MAPART -> initMapArt();
            case LOOK -> initLook();
            case CAPES -> { }
        }
    }

    private void initMapArt() {
        ModConfig cfg = ModConfig.get();

        addDrawableChild(ButtonWidget.builder(onOff("Auto-Platzieren", cfg.autoPlace), b -> {
            cfg.autoPlace = !cfg.autoPlace;
            b.setMessage(onOff("Auto-Platzieren", cfg.autoPlace));
        }).dimensions(cx, top + 30, cw, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Block aus Inventar holen", cfg.takeFromInventory), b -> {
            cfg.takeFromInventory = !cfg.takeFromInventory;
            b.setMessage(onOff("Block aus Inventar holen", cfg.takeFromInventory));
        }).dimensions(cx, top + 56, cw, 20).build());

        addDrawableChild(new DelaySlider(cx, top + 82, cw, 20));

        addDrawableChild(ButtonWidget.builder(onOff("Frame-Modus (experimentell)", cfg.frameMode), b -> {
            cfg.frameMode = !cfg.frameMode;
            b.setMessage(onOff("Frame-Modus (experimentell)", cfg.frameMode));
        }).dimensions(cx, top + 118, cw, 20).build());

        addDrawableChild(new MaxPerTickSlider(cx, top + 144, cw, 20));
    }

    private void initLook() {
        int half = (cw - 4) / 2;
        ButtonWidget accentBtn = ButtonWidget.builder(Text.literal("Akzentfarbe"), b -> {
            editAccent = true;
            clearAndInit();
        }).dimensions(cx, top + 30, half, 20).build();
        accentBtn.active = !editAccent;
        addDrawableChild(accentBtn);

        ButtonWidget bgBtn = ButtonWidget.builder(Text.literal("Hintergrund"), b -> {
            editAccent = false;
            clearAndInit();
        }).dimensions(cx + half + 4, top + 30, half, 20).build();
        bgBtn.active = editAccent;
        addDrawableChild(bgBtn);

        int rgb = editedColor();
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];

        int rx = cx + S + HUE_W + 24;      // rechte Spalte neben dem Picker
        int rw = cx + cw - rx;

        hexField = new TextFieldWidget(textRenderer, rx, top + 100, 70, 18, Text.literal("Hex"));
        hexField.setMaxLength(6);
        hexField.setText(String.format("%06X", rgb));
        hexField.setChangedListener(text -> {
            if (suppressHex || text.length() != 6) return;
            try {
                int c = Integer.parseInt(text, 16);
                setEditedColor(c);
                float[] h = rgbToHsv(c);
                hue = h[0];
                sat = h[1];
                val = h[2];
            } catch (NumberFormatException ignored) {
            }
        });
        addDrawableChild(hexField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zurücksetzen"), b -> {
            setEditedColor(editAccent ? ModConfig.DEFAULT_ACCENT : ModConfig.DEFAULT_BG);
            clearAndInit();
        }).dimensions(rx, top + 128, rw, 20).build());
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "AN" : "AUS")).formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    // ------------------------------------------------------------------ Farben

    private int editedColor() {
        return editAccent ? ModConfig.get().accentColor : ModConfig.get().bgColor;
    }

    private void setEditedColor(int rgb) {
        rgb &= 0xFFFFFF;
        if (editAccent) ModConfig.get().accentColor = rgb;
        else ModConfig.get().bgColor = rgb;
    }

    private void applyHsv() {
        int rgb = hsvToRgb(hue, sat, val);
        setEditedColor(rgb);
        if (hexField != null) {
            suppressHex = true;
            hexField.setText(String.format("%06X", rgb));
            suppressHex = false;
        }
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = (h - (float) Math.floor(h)) * 6f;
        int i = (int) Math.floor(h);
        float f = h - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static float clamp01(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        Theme.panel(ctx, left, top, W, H);
        // Trennlinie zwischen Reitern und Inhalt
        ctx.fill(left + SIDE, top + 26, left + SIDE + 1, top + H - 6, Theme.accent());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (activeTab == Tab.LOOK) handlePicker(mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawTextWithShadow(textRenderer, "MapArt Assist", left + 10, top + 9, Theme.accent());
        ctx.drawTextWithShadow(textRenderer, activeTab.label, cx, top + 9, 0xFFFFFFFF);

        switch (activeTab) {
            case MAPART -> {
                ctx.drawTextWithShadow(textRenderer, "Frame-Modus: ruckelt es, bitte AUS lassen.",
                        cx, top + 172, 0xFF888888);
                ctx.drawTextWithShadow(textRenderer, "Weitere Features folgen...",
                        cx, top + 186, 0xFF888888);
            }
            case LOOK -> renderPicker(ctx);
            case CAPES -> {
                ctx.drawTextWithShadow(textRenderer, "Eigene Capes (mit Elytra)", cx, top + 34, 0xFFCCCCCC);
                ctx.drawTextWithShadow(textRenderer, "Kommt bald.", cx, top + 50, 0xFF888888);
            }
        }
    }

    private void handlePicker(int mouseX, int mouseY) {
        int px = cx, py = top + 62;
        int hx = px + S + 10;

        // Mausknopf direkt abfragen, damit Ziehen ohne Maus-Event-Signaturen funktioniert
        boolean down = client != null && GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !wasDown) {
            if (mouseX >= px && mouseX < px + S && mouseY >= py && mouseY < py + S) dragMode = 1;
            else if (mouseX >= hx && mouseX < hx + HUE_W && mouseY >= py && mouseY < py + S) dragMode = 2;
        }
        if (!down) dragMode = 0;
        wasDown = down;

        if (dragMode == 1) {
            sat = clamp01((mouseX - px) / (double) (S - 1));
            val = 1f - clamp01((mouseY - py) / (double) (S - 1));
            applyHsv();
        } else if (dragMode == 2) {
            hue = clamp01((mouseY - py) / (double) (S - 1));
            applyHsv();
        }
    }

    private void renderPicker(DrawContext ctx) {
        int px = cx, py = top + 62;
        int hx = px + S + 10;
        int rx = hx + HUE_W + 10;
        int rw = cx + cw - rx;

        // Farbfeld: links nach rechts Saettigung, oben nach unten Helligkeit
        for (int x = 0; x < S; x++) {
            int topColor = 0xFF000000 | hsvToRgb(hue, x / (float) (S - 1), 1f);
            ctx.fillGradient(px + x, py, px + x + 1, py + S, topColor, 0xFF000000);
        }
        outline(ctx, px - 1, py - 1, S + 2, S + 2, 0xFF000000);
        int mx = px + Math.round(sat * (S - 1));
        int my = py + Math.round((1f - val) * (S - 1));
        outline(ctx, mx - 3, my - 3, 7, 7, 0xFFFFFFFF);
        outline(ctx, mx - 4, my - 4, 9, 9, 0xFF000000);

        // Farbton-Leiste
        for (int y = 0; y < S; y++) {
            ctx.fill(hx, py + y, hx + HUE_W, py + y + 1, 0xFF000000 | hsvToRgb(y / (float) (S - 1), 1f, 1f));
        }
        outline(ctx, hx - 1, py - 1, HUE_W + 2, S + 2, 0xFF000000);
        int hy = py + Math.round(hue * (S - 1));
        ctx.fill(hx - 2, hy - 1, hx + HUE_W + 2, hy + 2, 0xFFFFFFFF);
        outline(ctx, hx - 2, hy - 1, HUE_W + 4, 3, 0xFF000000);

        // Vorschau + Beschriftung rechts
        ctx.drawTextWithShadow(textRenderer, "Vorschau", rx, top + 62, 0xFFCCCCCC);
        ctx.fill(rx, top + 74, rx + rw, top + 94, 0xFF000000 | editedColor());
        outline(ctx, rx - 1, top + 73, rw + 2, 22, 0xFF000000);
        ctx.drawTextWithShadow(textRenderer, "#", rx - 8, top + 105, 0xFFCCCCCC);

        ctx.drawTextWithShadow(textRenderer, "Farbe im Feld ziehen,", cx, top + 172, 0xFF888888);
        ctx.drawTextWithShadow(textRenderer, "rechts daneben den Farbton wählen.", cx, top + 184, 0xFF888888);
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        ModConfig.save();
    }

    // ------------------------------------------------------------------ Regler

    private static class DelaySlider extends SliderWidget {
        DelaySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), ModConfig.get().delayTicks / 10.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Verzögerung: " + (int) Math.round(value * 10) + " Ticks"));
        }

        @Override
        protected void applyValue() {
            ModConfig.get().delayTicks = (int) Math.round(value * 10);
        }
    }

    private static class MaxPerTickSlider extends SliderWidget {
        MaxPerTickSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), (Math.max(1, Math.min(4, ModConfig.get().maxPerTick)) - 1) / 3.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Max. pro Tick (Frame-Modus): " + (1 + (int) Math.round(value * 3))));
        }

        @Override
        protected void applyValue() {
            ModConfig.get().maxPerTick = 1 + (int) Math.round(value * 3);
        }
    }
}
