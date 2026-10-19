package de.mapartassist.gui;

import de.mapartassist.cape.CapeCreator;
import de.mapartassist.cape.CapeManager;
import de.mapartassist.cape.CapePreview;
import de.mapartassist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Hauptmenue (v2 Design): eigene, animierte Bedienelemente. Mausbedienung wird direkt ueber GLFW
 * abgefragt, daher keine Abhaengigkeit von den Maus-Event-Signaturen.
 * Neue Features = neuer Eintrag im {@link Tab}-Enum + ein Block in build().
 */
public class MenuScreen extends Screen {
    private enum Tab {
        MAPART("Map Art"),
        LOOK("Aussehen"),
        CAPES("Capes"),
        CREATOR("Creator"),
        MACROS("Makros");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int W = 500, H = 292, TAB_W = 66;
    private static final int S = 100, HUE_W = 14;
    private static final int ROW_H = 30, CAPE_ROWS = 5, LIST_W = 230;

    // bleiben beim erneuten Oeffnen erhalten
    private static Tab activeTab = Tab.MAPART;
    private static boolean editAccent = true;
    private static int capeScroll = 0;
    private static boolean dropAsElytra = false;
    private static int macroScroll = 0;
    private static String macroStatus = "";
    private static CapeCreator.Fit creatorFit = CapeCreator.Fit.CROP;
    private static CapeCreator.Inner creatorInner = CapeCreator.Inner.MIRROR;

    private final Screen parent;
    private final long t0 = System.nanoTime();
    private int left, top, cx, cy, cw;

    private final List<Control> controls = new ArrayList<>();
    private Control active = null;
    private boolean wasDown = false;
    private float tabIndX = -1, tabIndW = TAB_W;
    private float tabChangeAt = -10f;
    private String hoverCape = null;

    // Farbwaehler
    private float hue, sat, val;
    private TextFieldWidget hexField;
    private boolean suppressHex = false;

    public MenuScreen(Screen parent) {
        super(Text.literal("Mod-Menü"));
        this.parent = parent;
        CapeManager.scan();
        CapePreview.clear();
    }

    // ================================================================== Hilfsfunktionen

    private float time() { return (System.nanoTime() - t0) / 1_000_000_000f; }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int alpha(int c, float a) {
        return ((int) (255 * Math.max(0, Math.min(1, a))) << 24) | (c & 0xFFFFFF);
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, s, x, y, color);
    }

    private void textCentered(DrawContext ctx, String s, int cxp, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, s, cxp - textRenderer.getWidth(s) / 2, y, color);
    }

    // ================================================================== Steuerelemente

    private abstract class Control {
        int x, y, w, h;
        float hover = 0;

        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

        void animateHover(int mx, int my, boolean enabled) {
            float target = enabled && contains(mx, my) ? 1f : 0f;
            hover += (target - hover) * 0.3f;
        }

        abstract void draw(DrawContext ctx, int mx, int my);
        void press(int mx, int my) { }
        void drag(int mx, int my) { }
    }

    private class Btn extends Control {
        final Supplier<String> label;
        final Runnable run;
        BooleanSupplier enabled = () -> true;
        BooleanSupplier selected = () -> false;

        Btn(int x, int y, int w, int h, Supplier<String> label, Runnable run) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.label = label; this.run = run;
        }

        Btn enabledIf(BooleanSupplier e) { enabled = e; return this; }
        Btn selectedIf(BooleanSupplier s) { selected = s; return this; }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            boolean en = enabled.getAsBoolean();
            animateHover(mx, my, en);
            int acc = Theme.accent();
            int base = selected.getAsBoolean() ? alpha(acc, 0.35f) : 0xFF2A2A36;
            int bg = mix(base, alpha(acc, 0.45f), hover * 0.7f);
            ctx.fill(x, y, x + w, y + h, en ? bg : 0xFF22222A);
            if (en && (hover > 0.05f || selected.getAsBoolean())) {
                ctx.fill(x, y + h - 2, x + w, y + h, alpha(acc, selected.getAsBoolean() ? 1f : hover));
            }
            textCentered(ctx, label.get(), x + w / 2, y + (h - 8) / 2, en ? 0xFFFFFFFF : 0xFF666672);
        }

        @Override
        void press(int mx, int my) {
            if (enabled.getAsBoolean()) run.run();
        }
    }

    private class TabBtn extends Control {
        final Tab tab;

        TabBtn(int x, int y, int w, int h, Tab tab) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.tab = tab;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            boolean on = tab == activeTab;
            animateHover(mx, my, true);
            if (on) ctx.fill(x, y, x + w, y + h, alpha(Theme.accent(), 0.16f));
            else if (hover > 0.02f) ctx.fill(x, y, x + w, y + h, alpha(0xFFFFFF, 0.08f * hover));
            int col = on ? 0xFFFFFFFF : mix(0xFF9A9AA8, 0xFFFFFFFF, hover);
            textCentered(ctx, tab.label, x + w / 2, y + (h - 8) / 2 - (int) (hover * 1), col);
        }

        @Override
        void press(int mx, int my) {
            if (activeTab != tab) {
                activeTab = tab;
                tabChangeAt = time();
                clearAndInit();
            }
        }
    }

    private class Tog extends Control {
        final String name;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        float anim;

        Tog(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
            this.x = x; this.y = y; this.w = w; this.h = 22;
            this.name = name; this.get = get; this.set = set;
            this.anim = get.getAsBoolean() ? 1f : 0f;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            anim += ((get.getAsBoolean() ? 1f : 0f) - anim) * 0.35f;
            if (hover > 0.02f) ctx.fill(x, y, x + w, y + h, alpha(0xFFFFFF, 0.07f * hover));
            text(ctx, name, x + 6, y + 7, 0xFFFFFFFF);
            int tx = x + w - 34, ty = y + 5;
            ctx.fill(tx, ty, tx + 28, ty + 12, mix(0xFF3A3A48, Theme.accent() | 0xFF000000, anim));
            int kx = tx + 2 + Math.round(anim * 16);
            ctx.fill(kx, ty + 2, kx + 8, ty + 10, 0xFFFFFFFF);
        }

        @Override
        void press(int mx, int my) { set.accept(!get.getAsBoolean()); }
    }

    private class Sld extends Control {
        final String name, unit;
        final int min, max;
        final IntSupplier get;
        final IntConsumer set;

        Sld(int x, int y, int w, String name, String unit, int min, int max, IntSupplier get, IntConsumer set) {
            this.x = x; this.y = y; this.w = w; this.h = 26;
            this.name = name; this.unit = unit; this.min = min; this.max = max; this.get = get; this.set = set;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            int v = get.getAsInt();
            text(ctx, name + ": " + v + unit, x + 2, y, 0xFFFFFFFF);
            int ty = y + 14, th = 6;
            ctx.fill(x, ty, x + w, ty + th, 0xFF2A2A36);
            float f = (v - min) / (float) (max - min);
            int fw = Math.round(f * (w - 1));
            ctx.fill(x, ty, x + fw, ty + th, Theme.accent());
            int kx = x + fw - 2;
            ctx.fill(kx, ty - 3, kx + 5, ty + th + 3, mix(0xFFCCCCCC, 0xFFFFFFFF, hover));
        }

        private void setFrom(int mx) {
            float f = clamp01((mx - x) / (double) (w - 1));
            set.accept(min + Math.round(f * (max - min)));
        }

        @Override
        void press(int mx, int my) { setFrom(mx); }

        @Override
        void drag(int mx, int my) { setFrom(mx); }
    }

    private class CapeRow extends Control {
        final CapeManager.Entry e;

        CapeRow(int x, int y, int w, CapeManager.Entry e) {
            this.x = x; this.y = y; this.w = w; this.h = ROW_H - 2; this.e = e;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            boolean sel = e.name().equals(ModConfig.get().selectedCape);
            int acc = Theme.accent();
            int bg = sel ? alpha(acc, 0.20f + 0.06f * (float) Math.sin(time() * 3)) : 0xFF262631;
            bg = mix(bg, alpha(acc, 0.30f), hover * 0.6f);
            ctx.fill(x, y, x + w, y + h, bg);
            if (sel) ctx.fill(x, y, x + 3, y + h, acc);
            if (hover > 0.5f) hoverCape = e.name();

            // Mini-Vorschau (echte Textur)
            CapePreview.Data d = CapePreview.get(e.name());
            int tx = x + 9, ty = y + 2, tw = 15, th = h - 4;
            ctx.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFF111118);
            if (d != null) drawFace(ctx, d, false, tx, ty, tw, th);
            else text(ctx, "?", tx + 4, ty + 7, 0xFF888888);

            text(ctx, e.name(), x + 32, y + 6, sel ? 0xFFFFFFFF : 0xFFDDDDDD);
            if (e.hasElytra()) text(ctx, "+ Elytra", x + 32, y + 16, 0xFF7C8A99);
        }

        @Override
        void press(int mx, int my) {
            CapeManager.select(e.name());
        }
    }

    private class MacroRow extends Control {
        final String id;

        MacroRow(int x, int y, int w, String id) {
            this.x = x; this.y = y; this.w = w; this.h = 26; this.id = id;
        }

        private Item item() {
            try {
                return Registries.ITEM.get(Identifier.of(id));
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            ctx.fill(x, y, x + w, y + h, mix(0xFF262631, alpha(Theme.accent(), 0.25f), hover * 0.6f));
            Item it = item();
            if (it != null) {
                ctx.drawItem(new ItemStack(it), x + 6, y + 5);
                text(ctx, it.getName().getString(), x + 30, y + 9, 0xFFFFFFFF);
            } else {
                text(ctx, id, x + 30, y + 9, 0xFFAA6666);
            }
            boolean overX = mx >= x + w - 24 && mx < x + w && my >= y && my < y + h;
            text(ctx, "✕", x + w - 16, y + 9, overX ? 0xFFFF6666 : 0xFF888890);
        }

        @Override
        void press(int mx, int my) {
            if (mx >= x + w - 24) {
                ModConfig.get().sellItems.remove(id);
                ModConfig.save();
                clearAndInit();
            }
        }
    }

    private class PickerControl extends Control {
        int mode = 0; // 1 Farbfeld, 2 Farbton

        PickerControl(int x, int y) {
            this.x = x; this.y = y; this.w = S + 10 + HUE_W; this.h = S;
        }

        @Override
        boolean contains(int mx, int my) {
            return my >= y && my < y + S && ((mx >= x && mx < x + S) || (mx >= x + S + 10 && mx < x + S + 10 + HUE_W));
        }

        @Override
        void press(int mx, int my) {
            mode = mx < x + S + 5 ? 1 : 2;
            drag(mx, my);
        }

        @Override
        void drag(int mx, int my) {
            if (mode == 1) {
                sat = clamp01((mx - x) / (double) (S - 1));
                val = 1f - clamp01((my - y) / (double) (S - 1));
            } else if (mode == 2) {
                hue = clamp01((my - y) / (double) (S - 1));
            }
            applyHsv();
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            int hx = x + S + 10;
            for (int i = 0; i < S; i++) {
                int topColor = 0xFF000000 | hsvToRgb(hue, i / (float) (S - 1), 1f);
                ctx.fillGradient(x + i, y, x + i + 1, y + S, topColor, 0xFF000000);
            }
            outline(ctx, x - 1, y - 1, S + 2, S + 2, 0xFF000000);
            int px = x + Math.round(sat * (S - 1)), py = y + Math.round((1f - val) * (S - 1));
            outline(ctx, px - 3, py - 3, 7, 7, 0xFFFFFFFF);
            outline(ctx, px - 4, py - 4, 9, 9, 0xFF000000);

            for (int i = 0; i < S; i++) {
                ctx.fill(hx, y + i, hx + HUE_W, y + i + 1, 0xFF000000 | hsvToRgb(i / (float) (S - 1), 1f, 1f));
            }
            outline(ctx, hx - 1, y - 1, HUE_W + 2, S + 2, 0xFF000000);
            int hy = y + Math.round(hue * (S - 1));
            ctx.fill(hx - 2, hy - 1, hx + HUE_W + 2, hy + 2, 0xFFFFFFFF);
            outline(ctx, hx - 2, hy - 1, HUE_W + 4, 3, 0xFF000000);
        }
    }

    // ================================================================== Aufbau

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        cx = left + 16;
        cy = top + 54;
        cw = W - 32;
        controls.clear();
        active = null;
        hexField = null;

        Tab[] tabs = Tab.values();
        int tx0 = left + W - 12 - tabs.length * TAB_W;
        for (int i = 0; i < tabs.length; i++) {
            controls.add(new TabBtn(tx0 + i * TAB_W, top + 10, TAB_W, 22, tabs[i]));
        }
        controls.add(new Btn(left + W - 96, top + H - 30, 80, 20, () -> "Fertig", this::close));

        switch (activeTab) {
            case MAPART -> buildMapArt();
            case LOOK -> buildLook();
            case CAPES -> buildCapes();
            case CREATOR -> buildCreator();
            case MACROS -> buildMacros();
        }
    }

    private static final int COL = 214, COL2 = 234;

    private void buildMapArt() {
        ModConfig cfg = ModConfig.get();
        controls.add(new Tog(cx + 6, cy + 26, COL - 12, "Auto-Platzieren", () -> cfg.autoPlace, v -> cfg.autoPlace = v));
        controls.add(new Tog(cx + 6, cy + 52, COL - 12, "Block aus Inventar holen",
                () -> cfg.takeFromInventory, v -> cfg.takeFromInventory = v));
        controls.add(new Sld(cx + 12, cy + 90, COL - 24, "Verzögerung", " Ticks", 0, 10,
                () -> cfg.delayTicks, v -> cfg.delayTicks = v));

        controls.add(new Tog(cx + COL2 + 6, cy + 26, COL - 12, "Frame-Modus", () -> cfg.frameMode, v -> cfg.frameMode = v));
        controls.add(new Sld(cx + COL2 + 12, cy + 64, COL - 24, "Max. pro Tick", "", 1, 4,
                () -> Math.max(1, Math.min(4, cfg.maxPerTick)), v -> cfg.maxPerTick = v));
    }

    private void buildLook() {
        controls.add(new Btn(cx, cy, 150, 20, () -> "Akzentfarbe", () -> { editAccent = true; clearAndInit(); })
                .selectedIf(() -> editAccent));
        controls.add(new Btn(cx + 154, cy, 150, 20, () -> "Hintergrund", () -> { editAccent = false; clearAndInit(); })
                .selectedIf(() -> !editAccent));

        int rgb = editedColor();
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];

        controls.add(new PickerControl(cx + 4, cy + 34));

        int rx = cx + S + HUE_W + 36;
        hexField = new TextFieldWidget(textRenderer, rx, cy + 66, 74, 18, Text.literal("Hex"));
        hexField.setMaxLength(6);
        hexField.setText(String.format("%06X", rgb));
        hexField.setChangedListener(t -> {
            if (suppressHex || t.length() != 6) return;
            try {
                int c = Integer.parseInt(t, 16);
                setEditedColor(c);
                float[] h = rgbToHsv(c);
                hue = h[0]; sat = h[1]; val = h[2];
            } catch (NumberFormatException ignored) {
            }
        });
        addDrawableChild(hexField);

        controls.add(new Btn(rx, cy + 94, 150, 20, () -> "Zurücksetzen", () -> {
            setEditedColor(editAccent ? ModConfig.DEFAULT_ACCENT : ModConfig.DEFAULT_BG);
            clearAndInit();
        }));
    }

    private void buildCapes() {
        List<CapeManager.Entry> list = CapeManager.list();
        int maxScroll = Math.max(0, list.size() - CAPE_ROWS);
        capeScroll = Math.max(0, Math.min(capeScroll, maxScroll));
        for (int i = 0; i < CAPE_ROWS; i++) {
            int idx = capeScroll + i;
            if (idx >= list.size()) break;
            controls.add(new CapeRow(cx, cy + i * ROW_H, LIST_W, list.get(idx)));
        }
        int ay = cy + CAPE_ROWS * ROW_H + 4;
        controls.add(new Btn(cx, ay, 28, 18, () -> "▲", () -> { capeScroll--; clearAndInit(); })
                .enabledIf(() -> capeScroll > 0));
        controls.add(new Btn(cx + 32, ay, 28, 18, () -> "▼", () -> { capeScroll++; clearAndInit(); })
                .enabledIf(() -> capeScroll < maxScroll));

        int rx = cx + LIST_W + 10, rw = cx + cw - rx;
        int by = cy + 132, bw = (rw - 8) / 3;
        controls.add(new Btn(rx, by, bw, 20, () -> "Keins", () -> CapeManager.select(""))
                .enabledIf(() -> !ModConfig.get().selectedCape.isEmpty()));
        controls.add(new Btn(rx + bw + 4, by, bw, 20, () -> "Löschen", () -> {
            CapeManager.delete(ModConfig.get().selectedCape);
            clearAndInit();
        }).enabledIf(() -> CapeManager.find(ModConfig.get().selectedCape) != null));
        controls.add(new Btn(rx + (bw + 4) * 2, by, bw, 20, () -> "Ordner", () -> {
            try {
                Files.createDirectories(CapeManager.dir());
                Util.getOperatingSystem().open(CapeManager.dir().toUri());
            } catch (Exception ex) {
                CapeManager.status = "Ordner konnte nicht geöffnet werden.";
            }
        }));
        controls.add(new Btn(rx, by + 24, rw, 20,
                () -> "PNG hineinziehen als: " + (dropAsElytra ? "ELYTRA" : "CAPE"),
                () -> dropAsElytra = !dropAsElytra));
    }

    private static final int MACRO_ROWS = 5;

    private void buildMacros() {
        ModConfig cfg = ModConfig.get();
        List<String> items = cfg.sellItems;
        int maxScroll = Math.max(0, items.size() - MACRO_ROWS);
        macroScroll = Math.max(0, Math.min(macroScroll, maxScroll));
        for (int i = 0; i < MACRO_ROWS; i++) {
            int idx = macroScroll + i;
            if (idx >= items.size()) break;
            controls.add(new MacroRow(cx + 6, cy + 26 + i * 28, LIST_W - 12, items.get(idx)));
        }
        int ay = cy + 26 + MACRO_ROWS * 28 + 2;
        controls.add(new Btn(cx + 6, ay, 28, 18, () -> "▲", () -> { macroScroll--; clearAndInit(); })
                .enabledIf(() -> macroScroll > 0));
        controls.add(new Btn(cx + 38, ay, 28, 18, () -> "▼", () -> { macroScroll++; clearAndInit(); })
                .enabledIf(() -> macroScroll < maxScroll));

        controls.add(new Btn(cx + LIST_W + 16, cy + 26, cx + cw - (cx + LIST_W + 16) - 6, 20,
                () -> "Item aus der Hand hinzufügen", () -> {
            if (client == null || client.player == null || client.player.getMainHandStack().isEmpty()) {
                macroStatus = "Nimm erst ein Item in die Hand.";
                return;
            }
            String id = Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString();
            if (cfg.sellItems.contains(id)) {
                macroStatus = "Ist schon in der Liste.";
                return;
            }
            cfg.sellItems.add(id);
            ModConfig.save();
            macroStatus = "Hinzugefügt: " + client.player.getMainHandStack().getName().getString();
            clearAndInit();
        }));
        controls.add(new Tog(cx + LIST_W + 16, cy + 56, cx + cw - (cx + LIST_W + 16) - 6,
                "Testlauf (nur anzeigen)", () -> cfg.macroDryRun, v -> cfg.macroDryRun = v));
    }

    private void buildCreator() {
        controls.add(new Btn(cx, cy, LIST_W, 20, () -> "Zuschnitt: " + creatorFit.label, () -> {
            CapeCreator.Fit[] v = CapeCreator.Fit.values();
            creatorFit = v[(creatorFit.ordinal() + 1) % v.length];
        }));
        controls.add(new Btn(cx, cy + 24, LIST_W, 20, () -> "Innenseite: " + creatorInner.label, () -> {
            CapeCreator.Inner[] v = CapeCreator.Inner.values();
            creatorInner = v[(creatorInner.ordinal() + 1) % v.length];
        }));
        controls.add(new Btn(cx, cy + 148, LIST_W, 20, () -> "Zur Bibliothek", () -> {
            activeTab = Tab.CAPES;
            tabChangeAt = time();
            clearAndInit();
        }));
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (activeTab == Tab.CREATOR) {
            String err = null;
            for (Path p : paths) {
                String e = CapeCreator.create(p, creatorFit, creatorInner);
                if (e != null) err = e;
            }
            if (err != null) CapeManager.status = err;
            return;
        }
        if (activeTab != Tab.CAPES) return;
        String error = null;
        for (Path p : paths) {
            String err = CapeManager.importPng(p, dropAsElytra);
            if (err != null) error = err;
        }
        if (error != null) CapeManager.status = error;
        clearAndInit();
    }

    // ================================================================== Farben

    private int editedColor() { return editAccent ? ModConfig.get().accentColor : ModConfig.get().bgColor; }

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

    // ================================================================== Zeichnen

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        float t = Math.min(1f, time() / 0.3f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int acc = Theme.accent();

        ctx.fill(0, 0, width, height, alpha(0x000000, 0.40f * ease));

        int bg = Theme.bg();
        ctx.fillGradient(left, top, left + W, top + H, alpha(mix(bg, 0xFFFFFFFF, 0.07f), 0.97f), alpha(bg, 0.97f));
        drawHexBackground(ctx, time());
        ctx.fill(left, top, left + W, top + 42, 0x40000000);
        ctx.fill(left, top + 42, left + W, top + 43, alpha(acc, 0.35f));
        ctx.fill(left, top + H - 38, left + W, top + H, 0x30000000);
        outline(ctx, left, top, W, H, alpha(acc, 0.55f));
        ctx.fill(left, top, left + Math.round(W * ease), top + 2, acc);

        // Titel
        String title = ModConfig.get().menuTitle == null || ModConfig.get().menuTitle.isBlank()
                ? "Mod-Menü" : ModConfig.get().menuTitle;
        int sp = title.indexOf(' ');
        String first = sp < 0 ? title : title.substring(0, sp);
        String rest = sp < 0 ? "" : title.substring(sp);
        text(ctx, first, left + 14, top + 15, acc);
        text(ctx, rest, left + 14 + textRenderer.getWidth(first), top + 15, 0xFFFFFFFF);
    }

    /** Dezent animiertes Sechseck-Muster: driftet langsam, pulsiert, ein Lichtfleck wandert darueber. */
    private void drawHexBackground(DrawContext ctx, float t) {
        int acc = Theme.accent();
        final float R = 26f, h = R * 0.8660254f;
        float ox = (t * 3f) % (3 * R), oy = (t * 2f) % (2 * h);
        float gx = left + W * (0.5f + 0.45f * (float) Math.sin(t * 0.35f));
        float gy = top + H * (0.5f + 0.40f * (float) Math.sin(t * 0.27f + 1.3f));

        ctx.enableScissor(left, top, left + W, top + H);
        int cols = (int) (W / (1.5f * R)) + 5, rows = (int) (H / (2 * h)) + 4;
        for (int c = -3; c < cols; c++) {
            for (int r = -3; r < rows; r++) {
                float x = left + c * 1.5f * R + ox;
                float y = top + r * 2 * h + ((c & 1) != 0 ? h : 0) + oy;
                float a = 0.045f + 0.05f * (0.5f + 0.5f * (float) Math.sin(t * 0.7f + x * 0.021f + y * 0.027f));
                float dx = x - gx, dy = y - gy;
                a += 0.22f * Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy) / 130f);
                int col = alpha(acc, a);

                // oben
                ctx.fill(Math.round(x - R / 2), Math.round(y - h), Math.round(x + R / 2), Math.round(y - h) + 1, col);
                // links oben / rechts oben (schraege Kanten)
                float slope = h / (R / 2);
                int steps = Math.round(R / 2);
                for (int i = 0; i < steps; i++) {
                    int ya = Math.round(y - (i + 1) * slope), yb = Math.round(y - i * slope);
                    int xl = Math.round(x - R + i), xr = Math.round(x + R - i - 1);
                    ctx.fill(xl, ya, xl + 1, yb + 1, col);
                    ctx.fill(xr, ya, xr + 1, yb + 1, col);
                }
            }
        }
        ctx.disableScissor();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Maus direkt abfragen
        boolean down = client != null && GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean edge = down && !wasDown;
        wasDown = down;
        if (edge) {
            active = null;
            for (int i = controls.size() - 1; i >= 0; i--) {
                Control c = controls.get(i);
                if (c.contains(mouseX, mouseY)) {
                    active = c;
                    c.press(mouseX, mouseY);
                    break;
                }
            }
        } else if (down && active != null) {
            active.drag(mouseX, mouseY);
        }
        if (!down) active = null;

        super.render(ctx, mouseX, mouseY, delta); // Hintergrund + Textfeld

        hoverCape = null;
        renderBackdrop(ctx);
        List<Control> snapshot = new ArrayList<>(controls);
        for (Control c : snapshot) c.draw(ctx, mouseX, mouseY);
        renderOverlay(ctx);

        // Reiter-Indikator gleitet unter den aktiven Reiter
        for (Control c : snapshot) {
            if (c instanceof TabBtn tb && tb.tab == activeTab) {
                if (tabIndX < 0) { tabIndX = tb.x; tabIndW = tb.w; }
                tabIndX += (tb.x - tabIndX) * 0.28f;
                tabIndW += (tb.w - tabIndW) * 0.28f;
            }
        }
        if (tabIndX >= 0) {
            ctx.fill(Math.round(tabIndX), top + 40, Math.round(tabIndX + tabIndW), top + 43, Theme.accent());
        }

        // Ueberblenden beim Reiterwechsel
        float p = Math.min(1f, (time() - tabChangeAt) / 0.22f);
        if (p < 1f) {
            ctx.fill(left + 1, top + 44, left + W - 1, top + H - 39, alpha(Theme.bg(), 0.85f * (1f - p)));
        }
    }

    private void card(DrawContext ctx, int x, int y, int w, int h, String title) {
        ctx.fill(x, y, x + w, y + h, 0x38000000);
        outline(ctx, x, y, w, h, alpha(Theme.accent(), 0.25f));
        if (title != null) {
            text(ctx, title, x + 10, y + 9, alpha(Theme.accent(), 1f));
            ctx.fill(x + 10, y + 20, x + w - 10, y + 21, alpha(0xFFFFFF, 0.10f));
        }
    }

    /** Karten usw. HINTER den Bedienelementen. */
    private void renderBackdrop(DrawContext ctx) {
        switch (activeTab) {
            case MAPART -> {
                card(ctx, cx, cy - 4, COL, 150, "Platzieren");
                card(ctx, cx + COL2, cy - 4, COL, 150, "Experimentell");
            }
            case LOOK -> { }
            case CAPES -> {
                int rx = cx + LIST_W + 10, rw = cx + cw - rx;
                card(ctx, rx, cy, rw, 126, null);
            }
            case CREATOR -> {
                int rx = cx + LIST_W + 10, rw = cx + cw - rx;
                card(ctx, rx, cy, rw, 126, null);
            }
            case MACROS -> {
                card(ctx, cx, cy - 4, LIST_W, 194, "Auktion: Verkaufsliste");
                card(ctx, cx + LIST_W + 10, cy - 4, cw - LIST_W - 10, 194, "Einstellungen");
            }
        }
    }

    /** Texte und Vorschau ueber den Bedienelementen. */
    private void renderOverlay(DrawContext ctx) {
        switch (activeTab) {
            case MAPART -> {
                text(ctx, "Frame-Modus platziert pro Bild statt pro Tick.", cx + COL2 + 12, cy + 100, 0xFF777783);
                text(ctx, "Bei Rucklern: ausgeschaltet lassen.", cx + COL2 + 12, cy + 113, 0xFF777783);
                drawStatus(ctx);
            }
            case LOOK -> renderLook(ctx);
            case CAPES -> renderCapes(ctx);
            case CREATOR -> renderCreator(ctx);
            case MACROS -> renderMacros(ctx);
        }
    }

    private void renderMacros(DrawContext ctx) {
        ModConfig cfg = ModConfig.get();
        if (cfg.sellItems.isEmpty()) {
            text(ctx, "Noch keine Items.", cx + 12, cy + 32, 0xFF999999);
            text(ctx, "Item in die Hand nehmen und", cx + 12, cy + 46, 0xFF777783);
            text(ctx, "rechts auf Hinzufügen klicken.", cx + 12, cy + 58, 0xFF777783);
        }
        int rx = cx + LIST_W + 26;
        text(ctx, "Befehl: /ahsell  (folgt)", rx, cy + 90, 0xFFCCCCCC);
        text(ctx, "Verkauft nur Items aus der Liste,", rx, cy + 106, 0xFF777783);
        text(ctx, "die du gerade im Inventar hast.", rx, cy + 118, 0xFF777783);
        text(ctx, "Preis-Erkennung: wird noch auf", rx, cy + 138, 0xFFFFD866);
        text(ctx, "HugoSMP eingestellt.", rx, cy + 150, 0xFFFFD866);
        text(ctx, macroStatus, cx, top + H - 24, 0xFFFFD866);
    }

    private void renderLook(DrawContext ctx) {
        int rx = cx + S + HUE_W + 36;
        text(ctx, "Vorschau", rx, cy + 34, 0xFFCCCCCC);
        ctx.fill(rx - 1, cy + 45, rx + 151, cy + 63, 0xFF000000);
        ctx.fill(rx, cy + 46, rx + 150, cy + 62, 0xFF000000 | editedColor());
        text(ctx, "#", rx - 9, cy + 71, 0xFFCCCCCC);

        // Mini-Beispiel im aktuellen Farbschema
        int mx = rx + 170, my = cy + 34, mw = cx + cw - mx, mh = 80;
        ctx.fill(mx, my, mx + mw, my + mh, 0xFF000000 | ModConfig.get().bgColor);
        outline(ctx, mx, my, mw, mh, alpha(Theme.accent(), 0.7f));
        ctx.fill(mx, my, mx + mw, my + 2, Theme.accent());
        text(ctx, "Beispiel", mx + 8, my + 10, 0xFFFFFFFF);
        ctx.fill(mx + 8, my + 28, mx + mw - 8, my + 46, alpha(Theme.accent(), 0.35f));
        ctx.fill(mx + 8, my + 44, mx + mw - 8, my + 46, Theme.accent());
        textCentered(ctx, "Knopf", mx + mw / 2, my + 33, 0xFFFFFFFF);
        ctx.fill(mx + 8, my + 56, mx + 36, my + 68, mix(0xFF3A3A48, Theme.accent() | 0xFF000000, 1f));
        ctx.fill(mx + 22, my + 58, mx + 34, my + 66, 0xFFFFFFFF);

        text(ctx, "Farbe im Feld ziehen, rechts den Farbton wählen.", cx, cy + 142, 0xFF777783);
    }

    private void renderCapes(DrawContext ctx) {
        List<CapeManager.Entry> list = CapeManager.list();
        if (list.isEmpty()) {
            text(ctx, "Noch keine Capes.", cx + 8, cy + 8, 0xFF999999);
            text(ctx, "PNG ins Fenster ziehen.", cx + 8, cy + 22, 0xFF777783);
        }
        text(ctx, list.size() + (list.size() == 1 ? " Cape" : " Capes"), cx + 68, cy + CAPE_ROWS * ROW_H + 8, 0xFF777783);

        String showing = hoverCape != null ? hoverCape : ModConfig.get().selectedCape;
        int rx = cx + LIST_W + 10, rw = cx + cw - rx;
        drawPreviewCard(ctx, showing, rx, cy, rw, 126);
        drawStatus(ctx);
    }

    private void renderCreator(DrawContext ctx) {
        int zx = cx, zy = cy + 52, zw = LIST_W, zh = 88;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time() * 2.5);
        ctx.fill(zx, zy, zx + zw, zy + zh, alpha(Theme.accent(), 0.06f + 0.05f * pulse));
        int bc = alpha(Theme.accent(), 0.45f + 0.4f * pulse);
        for (int i = 0; i < zw; i += 8) {
            ctx.fill(zx + i, zy, zx + Math.min(i + 4, zw), zy + 1, bc);
            ctx.fill(zx + i, zy + zh - 1, zx + Math.min(i + 4, zw), zy + zh, bc);
        }
        for (int i = 0; i < zh; i += 8) {
            ctx.fill(zx, zy + i, zx + 1, zy + Math.min(i + 4, zh), bc);
            ctx.fill(zx + zw - 1, zy + i, zx + zw, zy + Math.min(i + 4, zh), bc);
        }
        textCentered(ctx, "Bild hier hineinziehen", zx + zw / 2, zy + 32, 0xFFFFFFFF);
        textCentered(ctx, "PNG oder JPG, am besten hochkant", zx + zw / 2, zy + 48, 0xFF8A8A96);

        int rx = cx + LIST_W + 10, rw = cx + cw - rx;
        drawPreviewCard(ctx, ModConfig.get().selectedCape, rx, cy, rw, 126);
        drawStatus(ctx);
    }

    private void drawStatus(DrawContext ctx) {
        String st = CapeManager.status;
        boolean has = st != null && !st.isEmpty() && (activeTab == Tab.CAPES || activeTab == Tab.CREATOR);
        text(ctx, has ? st : "Capes sind nur für dich sichtbar.", cx, top + H - 24, has ? 0xFFFFD866 : 0xFF777783);
    }

    /** Echte Textur-Darstellung einer Cape-Seite. */
    private void drawFace(DrawContext ctx, CapePreview.Data d, boolean inner, int x, int y, int w, int h) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, d.id(), x, y,
                inner ? d.tw() : 0, 0, w, h, d.tw(), d.th(), d.texW(), d.texH());
    }

    /** Karte mit langsam drehendem Cape (Aussen- und Innenseite), in voller Texturqualitaet. */
    private void drawPreviewCard(DrawContext ctx, String name, int bx, int by, int bw, int bh) {
        int acc = Theme.accent();
        ctx.fillGradient(bx + 1, by + 1, bx + bw - 1, by + bh - 1, alpha(acc, 0.12f), 0x20000000);

        CapePreview.Data d = CapePreview.get(name);
        if (name == null || name.isEmpty() || d == null) {
            textCentered(ctx, name == null || name.isEmpty() ? "Kein Cape" : "Keine Vorschau", bx + bw / 2, by + bh / 2 - 4, 0xFF777783);
            return;
        }

        float t = time();
        float c = (float) Math.cos(t * 1.0f);
        boolean front = c >= 0;
        int fullH = 88, fullW = fullH * 10 / 16;
        int w = Math.max(2, Math.round(fullW * Math.abs(c)));
        int x0 = bx + bw / 2 - w / 2;
        int y0 = by + 18 + Math.round((float) Math.sin(t * 2f) * 2f);

        // Stange + Schatten
        ctx.fill(bx + bw / 2 - fullW / 2 - 6, y0 - 5, bx + bw / 2 + fullW / 2 + 6, y0 - 3, 0xFF8A8A96);
        ctx.fill(bx + bw / 2 - w / 2 - 2, y0 + fullH + 5, bx + bw / 2 + w / 2 + 2, y0 + fullH + 7, 0x30000000);

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, d.id(), x0, y0,
                front ? 0 : d.tw(), 0, w, fullH, d.tw(), d.th(), d.texW(), d.texH());

        textCentered(ctx, name, bx + bw / 2, by + bh - 14, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        ModConfig.save();
    }
}
