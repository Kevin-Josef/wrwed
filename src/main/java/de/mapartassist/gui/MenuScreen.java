package de.mapartassist.gui;

import de.mapartassist.cape.CapeCreator;
import de.mapartassist.combat.CombatLock;
import de.mapartassist.cape.CapeManager;
import de.mapartassist.cape.CapePreview;
import de.mapartassist.cam.Freecam;
import de.mapartassist.config.ModConfig;
import de.mapartassist.litematica.SchematicProgress;
import de.mapartassist.macro.AfkGuard;
import de.mapartassist.macro.AutoRepair;
import de.mapartassist.macro.MovementRecorder;
import de.mapartassist.macro.SellMacro;
import de.mapartassist.guard.SpawnerGuard;
import de.mapartassist.macro.AuctionMacro;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
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
        HOME(null, "Startseite"),
        AUCTION("Makros", "Auktions-Makro"),
        SELL("Makros", "Verkaufs-Makro"),
        HT1("Makros", "HT1 Combat Lock"),
        SPAWNER("Makros", "Spawner-Schutz"),
        RECORDER("Makros", "Bewegungs-Recorder"),
        REPAIR("Makros", "Auto-Reparatur"),
        AFK("Makros", "AFK-Wächter"),
        CAPES("Kosmetik", "Capes"),
        CREATOR("Kosmetik", "Cape Creator"),
        CALC(null, "Taschenrechner"),
        MAPART("Litematica", "Map Art"),
        PROGRESS("Litematica", "Fortschritt"),
        SETTINGS(null, "Design"),
        FREECAM("Extras", "Freecam");

        final String group, label;
        Tab(String group, String label) { this.group = group; this.label = label; }
        String title() { return group == null ? label : group + " > " + label; }
    }

    /** Fenstergroesse (ohne Seitenleiste: Startseite mit Kacheln). */
    private static final int W = 636, H = 292;
    private static final int S = 100, HUE_W = 14;
    private static final int ROW_H = 30, CAPE_ROWS = 5, LIST_W = 230;

    // bleiben beim erneuten Oeffnen erhalten
    private static Tab activeTab = Tab.HOME;
    private static boolean editAccent = true;
    private static int capeScroll = 0;
    private static boolean dropAsElytra = false;
    private static int macroScroll = 0;
    private static int recScroll = 0;
    private static int sellScroll = 0;
    private static String cellStatus = "";
    private static int progScroll = 0;
    private int progSeen = -1;
    private static String macroStatus = "";
    private static CapeCreator.Fit creatorFit = CapeCreator.Fit.CROP;
    private static CapeCreator.Inner creatorInner = CapeCreator.Inner.MIRROR;

    private final Screen parent;
    private final long t0 = System.nanoTime();
    private int left, top, cx, cy, cw;

    private final List<Control> controls = new ArrayList<>();
    private Control active = null;
    private boolean wasDown = false;
    private float tabChangeAt = -10f;
    private String hoverCape = null;

    // Farbwaehler
    private float hue, sat, val;
    private TextFieldWidget hexField, cmdField, titleField;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private boolean suppressHex = false;

    public MenuScreen(Screen parent) {
        super(Text.literal("Mod-Menü"));
        this.parent = parent;
        activeTab = Tab.HOME; // Menue startet immer auf der Startseite
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

    /** Kachel auf der Startseite (kompakt, ein Bereich pro Gruppe). */
    private class Tile extends Control {
        final Tab tab;

        Tile(int x, int y, int w, int h, Tab tab) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.tab = tab;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            int acc = Theme.accent();
            int yy = y - Math.round(hover * 2);
            ctx.fill(x, yy, x + w, yy + h, mix(0xFF262631, alpha(acc, 0.30f), hover * 0.85f));
            outline(ctx, x, yy, w, h, alpha(acc, 0.18f + 0.55f * hover));
            ctx.fill(x, yy, x + Math.round(w * (0.25f + 0.75f * hover)), yy + 2, acc);

            ctx.fill(x + 8, yy + 8, x + 38, yy + 38, alpha(acc, 0.16f + 0.14f * hover));
            outline(ctx, x + 8, yy + 8, 30, 30, alpha(acc, 0.35f));
            Item it = tileItem(tab);
            ctx.drawItem(new ItemStack(it), x + 15, yy + 15);

            text(ctx, tileName(tab), x + 45, yy + 10, 0xFFFFFFFF);
            String st = tileStatus(tab);
            if (st != null) {
                boolean good = !st.equals("AUS");
                int cw0 = textRenderer.getWidth(st) + 8;
                ctx.fill(x + 45, yy + 23, x + 45 + cw0, yy + 35, good ? alpha(0x34D399, 0.30f) : alpha(0xFFFFFF, 0.08f));
                text(ctx, st, x + 49, yy + 25, good ? 0xFF66DD99 : 0xFF8A8A96);
            } else {
                text(ctx, tileSub(tab), x + 45, yy + 24, 0xFF8A8A96);
            }
        }

        @Override
        void press(int mx, int my) { openTab(tab); }
    }

    /** Kleiner Icon-Knopf oben rechts (Design). */
    private class IconBtn extends Control {
        final int kind; // 0 = Design
        final String tip;
        final BooleanSupplier on;
        final Runnable run;

        IconBtn(int x, int y, int kind, String tip, BooleanSupplier on, Runnable run) {
            this.x = x; this.y = y; this.w = 18; this.h = 18;
            this.kind = kind; this.tip = tip; this.on = on; this.run = run;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            boolean sel = on.getAsBoolean();
            int acc = Theme.accent();
            ctx.fill(x, y, x + w, y + h, sel ? alpha(acc, 0.35f) : alpha(0xFFFFFF, 0.06f + 0.10f * hover));
            if (sel) ctx.fill(x, y + h - 2, x + w, y + h, acc);
            int lc = sel || hover > 0.4f ? 0xFFFFFFFF : 0xFFAAAAB6;
            if (kind == 0) {
                int[] ky = {y + 4, y + 8, y + 12};
                int[] kx = {x + 11, x + 5, x + 9};
                for (int i = 0; i < 3; i++) {
                    ctx.fill(x + 3, ky[i], x + w - 3, ky[i] + 1, lc);
                    ctx.fill(kx[i], ky[i] - 1, kx[i] + 3, ky[i] + 2, sel ? 0xFFFFFFFF : acc);
                }
            }
            if (hover > 0.5f) text(ctx, tip, x + w - textRenderer.getWidth(tip), y + 22, 0xFF9A9AA8);
        }

        @Override
        void press(int mx, int my) { run.run(); }
    }

    /** Farb-Vorgabe zum Anklicken. */
    private class Swatch extends Control {
        final int rgb;

        Swatch(int x, int y, int w, int h, int rgb) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.rgb = rgb;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            ctx.fill(x, y, x + w, y + h, 0xFF000000 | rgb);
            boolean cur = (editedColor() & 0xFFFFFF) == rgb;
            if (cur) outline(ctx, x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
            else if (hover > 0.3f) outline(ctx, x - 1, y - 1, w + 2, h + 2, alpha(0xFFFFFF, 0.6f));
        }

        @Override
        void press(int mx, int my) {
            setEditedColor(rgb);
            clearAndInit();
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
                ModConfig c = ModConfig.get();
                long mp = c.sellPrices.getOrDefault(id, 0L);
                boolean man = AuctionMacro.useManual(c, id);
                text(ctx, it.getName().getString() + (mp > 0 ? "  §7(" + mp + ")" : ""), x + 30, y + 9, 0xFFFFFFFF);
                int cx0 = x + w - 88;
                boolean overChip = mx >= cx0 && mx < cx0 + 58 && my >= y + 4 && my < y + h - 4;
                ctx.fill(cx0, y + 4, cx0 + 58, y + h - 4, man ? alpha(0xE0A030, overChip ? 0.9f : 0.7f) : alpha(Theme.accent(), overChip ? 0.9f : 0.7f));
                textCentered(ctx, man ? "Manuell" : "Auto", cx0 + 29, y + 9, 0xFFFFFFFF);
            } else {
                text(ctx, id, x + 30, y + 9, 0xFFAA6666);
            }
            boolean overX = mx >= x + w - 24 && mx < x + w && my >= y && my < y + h;
            text(ctx, "✕", x + w - 16, y + 9, overX ? 0xFFFF6666 : 0xFF888890);

        }

        @Override
        void press(int mx, int my) {
            if (mx >= x + w - 88 && mx < x + w - 30) {
                ModConfig c = ModConfig.get();
                long mp = c.sellPrices.getOrDefault(id, 0L);
                boolean man = AuctionMacro.useManual(c, id);
                if (!man && mp <= 0) {
                    macroStatus = "Erst Preis setzen: /ahmacro preis " + id.replace("minecraft:", "") + " 200k";
                    return;
                }
                c.sellUseManual.put(id, !man);
                ModConfig.save();
                macroStatus = !man ? "Manueller Preis: " + mp : "Automatisch: günstigster Auktionspreis.";
                return;
            }
            if (mx >= x + w - 24) {
                ModConfig.get().sellItems.remove(id);
                ModConfig.get().sellAmounts.remove(id);
                ModConfig.save();
                clearAndInit();
            }
        }
    }

    private class CellRow extends Control {
        final String id;

        CellRow(int x, int y, int w, String id) {
            this.x = x; this.y = y; this.w = w; this.h = 26; this.id = id;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            ctx.fill(x, y, x + w, y + h, mix(0xFF262631, alpha(Theme.accent(), 0.25f), hover * 0.6f));
            Item it = null;
            try {
                it = Registries.ITEM.get(Identifier.of(id));
            } catch (Exception ignored) {
            }
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
                ModConfig.get().cellItems.remove(id);
                ModConfig.save();
                clearAndInit();
            }
        }
    }

    private class RecRow extends Control {
        final MovementRecorder.Rec r;

        RecRow(int x, int y, int w, MovementRecorder.Rec r) {
            this.x = x; this.y = y; this.w = w; this.h = 26; this.r = r;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            boolean sel = r.name.equals(ModConfig.get().selectedRecording);
            int acc = Theme.accent();
            ctx.fill(x, y, x + w, y + h, mix(sel ? alpha(acc, 0.25f) : 0xFF262631, alpha(acc, 0.30f), hover * 0.6f));
            if (sel) ctx.fill(x, y, x + 3, y + h, acc);
            text(ctx, r.name, x + 10, y + 9, sel ? 0xFFFFFFFF : 0xFFDDDDDD);
            String len = String.format(java.util.Locale.ROOT, "%.1f s", r.size() / 20f);
            text(ctx, len, x + w - 8 - textRenderer.getWidth(len), y + 9, 0xFF8A8A96);
        }

        @Override
        void press(int mx, int my) {
            ModConfig.get().selectedRecording = r.name;
            ModConfig.save();
        }
    }

    private class ProgRow extends Control {
        final SchematicProgress.Stat st;

        ProgRow(int x, int y, int w, SchematicProgress.Stat st) {
            this.x = x; this.y = y; this.w = w; this.h = 24; this.st = st;
        }

        @Override
        void draw(DrawContext ctx, int mx, int my) {
            animateHover(mx, my, true);
            boolean ex = SchematicProgress.isExcluded(st.block);
            int acc = Theme.accent();
            ctx.fill(x, y, x + w, y + h, mix(ex ? 0xFF2E2A22 : 0xFF262631, alpha(acc, 0.28f), hover * 0.6f));
            Item it = st.block.asItem();
            if (it != Items.AIR) ctx.drawItem(new ItemStack(it), x + 4, y + 4);
            String name = shorten(st.block.getName().getString(), 15);
            text(ctx, name, x + 24, y + 4, ex ? 0xFF8A8A96 : 0xFFFFFFFF);
            String right = ex ? "ausgeschlossen" : st.done + "/" + st.total;
            text(ctx, right, x + w - 6 - textRenderer.getWidth(right), y + 4, ex ? 0xFFE0A030 : 0xFF9A9AA8);
            float f = st.total == 0 ? 0f : st.done / (float) st.total;
            ctx.fill(x + 24, y + h - 5, x + w - 6, y + h - 3, 0xFF14141B);
            ctx.fill(x + 24, y + h - 5, x + 24 + Math.round((w - 30) * f), y + h - 3, ex ? 0xFFE0A030 : acc);
        }

        @Override
        void press(int mx, int my) { SchematicProgress.toggleExcluded(st.block); }
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
        // Inhaltsbereich der Unterseiten (mittig; frueher neben der Seitenleiste)
        cw = 484;
        cx = left + (W - cw) / 2;
        cy = top + 54;
        controls.clear();
        active = null;
        hexField = null;
        cmdField = null;
        titleField = null;
        fields.clear();

        controls.add(new IconBtn(left + W - 28, top + 12, 0, "Design", () -> activeTab == Tab.SETTINGS, () ->
                openTab(activeTab == Tab.SETTINGS ? Tab.HOME : Tab.SETTINGS)));
        controls.add(new Btn(left + W - 96, top + H - 30, 80, 20, () -> "Fertig", this::close));
        if (activeTab != Tab.HOME) {
            controls.add(new Btn(left + W - 190, top + H - 30, 88, 20, () -> "‹ Startseite", () -> openTab(Tab.HOME)));
        }

        switch (activeTab) {
            case HOME -> buildHome();
            case MAPART -> buildMapArt();
            case SETTINGS -> buildSettings();
            case CAPES -> buildCapes();
            case CREATOR -> buildCreator();
            case AUCTION -> buildMacros();
            case HT1 -> buildHt1();
            case SPAWNER -> buildSpawner();
            case RECORDER -> buildRecorder();
            case SELL -> buildSell();
            case REPAIR -> buildRepair();
            case AFK -> buildAfk();
            case PROGRESS -> buildProgress();
            case CALC -> buildCalc();
            case FREECAM -> buildFreecam();
        }
    }

    private void openTab(Tab t) {
        if (activeTab != t) {
            activeTab = t;
            tabChangeAt = time();
            clearAndInit();
        }
    }

    // ---- Startseite

    private record Group(String title, int row, int side, Tab[] tabs) {}

    private static final Group[] GROUPS = {
            new Group("Verkauf", 0, 0, new Tab[]{Tab.AUCTION, Tab.SELL}),
            new Group("Litematica", 0, 1, new Tab[]{Tab.MAPART, Tab.PROGRESS}),
            new Group("Automatik", 1, -1, new Tab[]{Tab.HT1, Tab.SPAWNER, Tab.RECORDER, Tab.REPAIR}),
            new Group("Kosmetik", 2, 0, new Tab[]{Tab.CAPES}),
            new Group("Extras", 2, 1, new Tab[]{Tab.CALC, Tab.AFK, Tab.FREECAM}),
    };
    private static final int TILE_W = 144, TILE_H = 46, TILE_GAP = 10;

    private int groupX(Group g) {
        if (g.side() != 1) return left + 14;
        int w0 = 0;
        for (Group o : GROUPS) {
            if (o.row() == g.row() && o.side() != 1) w0 = o.tabs().length * TILE_W + (o.tabs().length - 1) * TILE_GAP;
        }
        return left + 14 + w0 + 12;
    }
    private int groupY(Group g) { return top + 48 + g.row() * 68; }

    private static Item tileItem(Tab t) {
        return switch (t) {
            case AUCTION -> Items.GOLD_INGOT;
            case SELL -> Items.EMERALD;
            case REPAIR -> Items.DIAMOND_PICKAXE;
            case AFK -> Items.RED_BED;
            case FREECAM -> Items.ENDER_EYE;
            case HT1 -> Items.IRON_SWORD;
            case SPAWNER -> Items.SPAWNER;
            case RECORDER -> Items.CLOCK;
            case MAPART -> Items.FILLED_MAP;
            case PROGRESS -> Items.WRITABLE_BOOK;
            case CAPES -> Items.ELYTRA;
            case CREATOR -> Items.PAINTING;
            case CALC -> Items.COMPARATOR;
            default -> Items.PINK_DYE;
        };
    }

    private static String tileName(Tab t) {
        return switch (t) {
            case RECORDER -> "Recorder";
            case SELL -> "Verkaufs-Makro";
            case REPAIR -> "Auto-Reparatur";
            case AFK -> "AFK-Wächter";
            case FREECAM -> "Freecam";
            case PROGRESS -> "Fortschritt";
            case CREATOR -> "Creator";
            case CALC -> "Rechner";
            default -> t.label;
        };
    }

    private static String tileSub(Tab t) {
        return switch (t) {
            case RECORDER -> "Aufnehmen & Loop";
            case PROGRESS -> "Prozent & Blöcke";
            case CAPES -> "Bibliothek";
            case CREATOR -> "Bild zu Cape";
            case CALC -> "Mit Verlauf";
            case FREECAM -> "Frei fliegen";
            case SETTINGS -> "Farben & Effekte";
            default -> "";
        };
    }

    private static String tileStatus(Tab t) {
        ModConfig c = ModConfig.get();
        return switch (t) {
            case AUCTION -> AuctionMacro.running() ? "LÄUFT" : "AUS";
            case SELL -> SellMacro.running() ? "LÄUFT" : c.cellAuto ? "AUTO" : "AUS";
            case REPAIR -> AutoRepair.running() ? "LÄUFT" : c.repairEnabled ? "AN" : "AUS";
            case AFK -> AfkGuard.active() ? "AN" : "AUS";
            case FREECAM -> Freecam.active() ? "AN" : "AUS";
            case HT1 -> c.combatLock ? "AN" : "AUS";
            case SPAWNER -> c.spawnerGuard ? "AN" : "AUS";
            case MAPART -> c.autoPlace ? "AN" : "AUS";
            case RECORDER -> switch (MovementRecorder.state) {
                case RECORDING -> "REC";
                case PLAYING -> "LÄUFT";
                default -> "AUS";
            };
            default -> null;
        };
    }

    private void buildHome() {
        for (Group g : GROUPS) {
            for (int i = 0; i < g.tabs().length; i++) {
                controls.add(new Tile(groupX(g) + i * (TILE_W + TILE_GAP), groupY(g) + 14, TILE_W, TILE_H, g.tabs()[i]));
            }
        }
    }

    // ---- Freecam

    private void buildFreecam() {
        ModConfig cfg = ModConfig.get();
        controls.add(new Tog(cx + 6, cy + 28, COL - 12, "Freecam",
                () -> Freecam.active(), v -> Freecam.set(client, v)));
        controls.add(new Sld(cx + 12, cy + 58, COL - 24, "Geschwindigkeit", " Blöcke/s", 1, 40,
                () -> cfg.freecamSpeed, v -> cfg.freecamSpeed = v));
    }

    private TextFieldWidget addField(TextFieldWidget f) {
        fields.add(f);
        addSelectableChild(f);
        return f;
    }

    private static final int COL = 214, COL2 = 234;

    private void buildMapArt() {
        ModConfig cfg = ModConfig.get();
        controls.add(new Tog(cx + 6, cy + 26, COL - 12, "Auto-Platzieren", () -> cfg.autoPlace, v -> cfg.autoPlace = v));
        controls.add(new Tog(cx + 6, cy + 52, COL - 12, "Block aus Inventar holen",
                () -> cfg.takeFromInventory, v -> cfg.takeFromInventory = v));
        controls.add(new Sld(cx + 12, cy + 90, COL - 24, "Verzögerung", " Ticks", 0, 10,
                () -> cfg.delayTicks, v -> cfg.delayTicks = v));
        controls.add(new Tog(cx + 6, cy + 120, COL - 12, "Nur richtige Rotation",
                () -> cfg.rotationCheck, v -> { cfg.rotationCheck = v; ModConfig.save(); }));

        controls.add(new Tog(cx + COL2 + 6, cy + 26, COL - 12, "Frame-Modus", () -> cfg.frameMode, v -> cfg.frameMode = v));
        controls.add(new Sld(cx + COL2 + 12, cy + 64, COL - 24, "Max. pro Tick", "", 1, 4,
                () -> Math.max(1, Math.min(4, cfg.maxPerTick)), v -> cfg.maxPerTick = v));
    }

    private static final int[] ACCENT_PRESETS = {
            0x3FA7FF, 0x34D399, 0xF87171, 0xA78BFA, 0xFBBF24, 0xF472B6, 0x22D3EE, 0xFB923C};
    private static final int[] BG_PRESETS = {
            0x1E1E28, 0x14141B, 0x0F1A24, 0x1A1426, 0x14231B, 0x2A1616, 0x262626, 0x0B0B0B};
    private static final String[] BG_STYLES = {"Hex", "Sterne", "Regen", "Verlauf", "Raster", "Aurora", "Bild", "Aus"};
    private static final String[] BORDER_STYLES = {"Schlicht", "Glühend", "Doppelt"};
    private static final int SET_A = 236, SET_B = 170, SET_GAP = 10;

    private int designX() { return left + 14; }

    private void buildSettings() {
        ModConfig cfg = ModConfig.get();
        int lx = designX();

        // ---- Karte 1: Farben
        controls.add(new Btn(lx + 10, cy + 22, 106, 18, () -> "Akzentfarbe", () -> { editAccent = true; clearAndInit(); })
                .selectedIf(() -> editAccent));
        controls.add(new Btn(lx + 122, cy + 22, 106, 18, () -> "Hintergrund", () -> { editAccent = false; clearAndInit(); })
                .selectedIf(() -> !editAccent));

        int rgb = editedColor();
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];

        controls.add(new PickerControl(lx + 14, cy + 48));

        int rx = lx + 14 + S + 10 + HUE_W + 16;
        hexField = addField(new TextFieldWidget(textRenderer, rx, cy + 50, 74, 18, Text.literal("Hex")));
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
        controls.add(new Btn(rx, cy + 76, lx + SET_A - 10 - rx, 18, () -> "Zurücksetzen", () -> {
            setEditedColor(editAccent ? ModConfig.DEFAULT_ACCENT : ModConfig.DEFAULT_BG);
            clearAndInit();
        }));

        int[] presets = editAccent ? ACCENT_PRESETS : BG_PRESETS;
        for (int i = 0; i < presets.length; i++) {
            controls.add(new Swatch(lx + 14 + i * 26, cy + 158, 22, 14, presets[i]));
        }

        // ---- Karte 2: Hintergrund-Effekt
        int bx = lx + SET_A + SET_GAP;
        int bw = (SET_B - 24) / 2;
        for (int i = 0; i < BG_STYLES.length; i++) {
            final int idx = i;
            controls.add(new Btn(bx + 10 + (i % 2) * (bw + 4), cy + 26 + (i / 2) * 22, bw, 18,
                    () -> BG_STYLES[idx], () -> cfg.bgStyle = idx).selectedIf(() -> cfg.bgStyle == idx));
        }
        controls.add(new Btn(bx + 10, cy + 118, SET_B - 20, 18, () -> {
            String n = cfg.customBackground;
            return "Bild: " + (n == null || n.isEmpty() ? "keins" : shorten(n, 14));
        }, () -> {
            List<String> files = BackgroundImage.list();
            if (files.isEmpty()) {
                BackgroundImage.status = "Noch kein Bild. PNG/JPG ins Fenster ziehen.";
                return;
            }
            int i = files.indexOf(cfg.customBackground);
            String next = i + 1 >= files.size() ? "" : files.get(i + 1);
            BackgroundImage.select(next);
            if (!next.isEmpty()) cfg.bgStyle = 6;
        }));
        controls.add(new Btn(bx + 10, cy + 140, SET_B - 20, 18, () -> "Bilder-Ordner öffnen", () -> {
            try {
                Files.createDirectories(BackgroundImage.dir());
                Util.getOperatingSystem().open(BackgroundImage.dir().toUri());
            } catch (Exception ex) {
                BackgroundImage.status = "Ordner konnte nicht geöffnet werden.";
            }
        }));
        controls.add(new Sld(bx + 12, cy + 164, SET_B - 24, "Bild abdunkeln", " %", 0, 90,
                () -> cfg.customBgDim, v -> cfg.customBgDim = v));

        // ---- Karte 3: Menue
        int px = bx + SET_B + SET_GAP, pw = left + W - 14 - px;
        controls.add(new Sld(px + 12, cy + 62, pw - 24, "Deckkraft", " %", 40, 100,
                () -> cfg.bgOpacity, v -> cfg.bgOpacity = v));
        controls.add(new Tog(px + 6, cy + 94, pw - 12, "Übergänge", () -> cfg.menuAnimations, v -> cfg.menuAnimations = v));
        controls.add(new Tog(px + 6, cy + 118, pw - 12, "Regenbogen", () -> cfg.rainbowAccent, v -> cfg.rainbowAccent = v));
        controls.add(new Btn(px + 10, cy + 144, pw - 20, 18, () -> "Rahmen: " + BORDER_STYLES[cfg.borderStyle % 3],
                () -> cfg.borderStyle = (cfg.borderStyle + 1) % 3));
        controls.add(new Btn(px + 10, cy + 168, pw - 20, 18, () -> "Alles zurücksetzen", () -> {
            cfg.accentColor = ModConfig.DEFAULT_ACCENT;
            cfg.bgColor = ModConfig.DEFAULT_BG;
            cfg.bgOpacity = 97;
            cfg.bgStyle = 0;
            cfg.borderStyle = 0;
            cfg.rainbowAccent = false;
            cfg.menuAnimations = true;
            cfg.customBgDim = 55;
            clearAndInit();
        }));
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static final int SELL_ROWS = 5;

    private void buildSell() {
        ModConfig cfg = ModConfig.get();
        List<String> items = cfg.cellItems;
        int maxScroll = Math.max(0, items.size() - SELL_ROWS);
        sellScroll = Math.max(0, Math.min(sellScroll, maxScroll));
        for (int i = 0; i < SELL_ROWS; i++) {
            int idx = sellScroll + i;
            if (idx >= items.size()) break;
            controls.add(new CellRow(cx + 6, cy + 26 + i * 28, LIST_W - 12, items.get(idx)));
        }
        int ay = cy + 26 + SELL_ROWS * 28 + 2;
        controls.add(new Btn(cx + 6, ay, 28, 18, () -> "▲", () -> { sellScroll--; clearAndInit(); })
                .enabledIf(() -> sellScroll > 0));
        controls.add(new Btn(cx + 38, ay, 28, 18, () -> "▼", () -> { sellScroll++; clearAndInit(); })
                .enabledIf(() -> sellScroll < maxScroll));

        int rx = cx + LIST_W + 20, rw = cx + cw - rx - 10;
        controls.add(new Btn(rx, cy + 26, rw, 20, () -> "Item aus der Hand hinzufügen", () -> {
            if (client == null || client.player == null || client.player.getMainHandStack().isEmpty()) {
                cellStatus = "Nimm erst ein Item in die Hand.";
                return;
            }
            String id = Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString();
            if (cfg.cellItems.contains(id)) {
                cellStatus = "Ist schon in der Liste.";
                return;
            }
            cfg.cellItems.add(id);
            ModConfig.save();
            cellStatus = "Hinzugefügt: " + client.player.getMainHandStack().getName().getString();
            clearAndInit();
        }));
        controls.add(new Tog(rx - 6, cy + 52, rw + 12, "Wiederholen, bis leer", () -> cfg.cellRepeat, v -> { cfg.cellRepeat = v; ModConfig.save(); }));
        controls.add(new Tog(rx - 6, cy + 74, rw + 12, "Dauerbetrieb (immer)", () -> cfg.cellAuto, v -> { cfg.cellAuto = v; ModConfig.save(); }));
        int hw = (rw - 4) / 2;
        controls.add(new Btn(rx, cy + 104, hw, 20, () -> "Jetzt verkaufen", () -> {
            close();
            SellMacro.start(false);
        }).enabledIf(() -> !SellMacro.running() && !ModConfig.get().cellItems.isEmpty()));
        controls.add(new Btn(rx + hw + 4, cy + 104, hw, 20, () -> "Stopp", SellMacro::stop)
                .enabledIf(SellMacro::running));
    }

    private void buildAfk() {
        ModConfig cfg = ModConfig.get();
        controls.add(new Tog(cx + 6, cy + 26, COL - 12, "Wächter aktiv", () -> AfkGuard.active(), v -> AfkGuard.set(client, v)));
        controls.add(new Sld(cx + 12, cy + 54, COL - 24, "Als Sprung zählt ab", " Blöcke", 10, 300,
                () -> cfg.afkRadius, v -> cfg.afkRadius = v));
        controls.add(new Sld(cx + 12, cy + 84, COL - 24, "/afk wiederholen", " s", 1, 15,
                () -> cfg.afkRetrySec, v -> cfg.afkRetrySec = v));
        controls.add(new Btn(cx + 10, cy + 120, COL - 20, 20, () -> "Jetzt /afk senden (bin am Spawn)", () -> {
            AfkGuard.forceAway();
        }).enabledIf(AfkGuard::active));
    }

    private void buildRepair() {
        ModConfig cfg = ModConfig.get();
        int lw = 250;
        controls.add(new Tog(cx + 6, cy + 26, lw - 12, "Auto-Reparatur", () -> cfg.repairEnabled, v -> { cfg.repairEnabled = v; ModConfig.save(); }));
        controls.add(new Sld(cx + 12, cy + 54, lw - 24, "Reparieren ab", " Haltbarkeit", 5, 300,
                () -> cfg.repairThreshold, v -> cfg.repairThreshold = v));
        controls.add(new Sld(cx + 12, cy + 84, lw - 24, "XP-Flaschen", " Stack(s)", 1, 4,
                () -> cfg.repairStacks, v -> cfg.repairStacks = v));
        controls.add(new Tog(cx + 6, cy + 116, lw - 12, "Aufnahme danach fortsetzen", () -> cfg.repairResume, v -> { cfg.repairResume = v; ModConfig.save(); }));
        int hw = (lw - 24) / 2;
        controls.add(new Btn(cx + 12, cy + 146, hw, 20, () -> "Jetzt testen", () -> {
            close();
            AutoRepair.start(true);
        }).enabledIf(() -> !AutoRepair.running()));
        controls.add(new Btn(cx + 12 + hw + 4, cy + 146, hw, 20, () -> "Stopp", AutoRepair::stop)
                .enabledIf(AutoRepair::running));

        int rx = cx + lw + 20, rw = cx + cw - rx - 10;
        titleField = addField(new TextFieldWidget(textRenderer, rx, cy + 36, rw, 18, Text.literal("Kategorie")));
        titleField.setMaxLength(32);
        titleField.setText(cfg.repairCategory);
        titleField.setChangedListener(t -> {
            cfg.repairCategory = t;
            ModConfig.save();
        });
    }

    private static final int REC_ROWS = 5;

    private void buildRecorder() {
        ModConfig cfg = ModConfig.get();
        List<MovementRecorder.Rec> list = MovementRecorder.list();
        int maxScroll = Math.max(0, list.size() - REC_ROWS);
        recScroll = Math.max(0, Math.min(recScroll, maxScroll));
        for (int i = 0; i < REC_ROWS; i++) {
            int idx = recScroll + i;
            if (idx >= list.size()) break;
            controls.add(new RecRow(cx + 6, cy + 26 + i * 28, LIST_W - 12, list.get(idx)));
        }
        int ay = cy + 26 + REC_ROWS * 28 + 2;
        controls.add(new Btn(cx + 6, ay, 28, 18, () -> "▲", () -> { recScroll--; clearAndInit(); })
                .enabledIf(() -> recScroll > 0));
        controls.add(new Btn(cx + 38, ay, 28, 18, () -> "▼", () -> { recScroll++; clearAndInit(); })
                .enabledIf(() -> recScroll < maxScroll));

        int rx = cx + LIST_W + 20, rw = cx + cw - rx - 10;
        controls.add(new Btn(rx, cy + 26, rw, 20,
                () -> MovementRecorder.state == MovementRecorder.State.RECORDING ? "Aufnahme beenden" : "Aufnahme starten", () -> {
            net.minecraft.client.MinecraftClient mc = client;
            if (MovementRecorder.state == MovementRecorder.State.RECORDING) {
                MovementRecorder.stopRecording(mc);
                clearAndInit();
            } else {
                close();
                MovementRecorder.startRecording(mc);
            }
        }).enabledIf(() -> MovementRecorder.state != MovementRecorder.State.PLAYING)
                .selectedIf(() -> MovementRecorder.state == MovementRecorder.State.RECORDING));
        int hw = (rw - 4) / 2;
        controls.add(new Btn(rx, cy + 50, hw, 20, () -> "Abspielen", () -> {
            net.minecraft.client.MinecraftClient mc = client;
            close();
            MovementRecorder.startPlaying(mc);
        }).enabledIf(() -> MovementRecorder.state == MovementRecorder.State.IDLE
                && MovementRecorder.find(cfg.selectedRecording) != null));
        controls.add(new Btn(rx + hw + 4, cy + 50, hw, 20, () -> "Stopp", () -> MovementRecorder.stopAll(client))
                .enabledIf(() -> MovementRecorder.state != MovementRecorder.State.IDLE));
        controls.add(new Tog(rx - 6, cy + 78, rw + 12, "Endlos wiederholen", () -> cfg.recEndless, v -> cfg.recEndless = v));
        controls.add(new Sld(rx + 2, cy + 106, rw - 4, "Wiederholungen", "x", 1, 99,
                () -> Math.max(1, Math.min(99, cfg.recRepeats)), v -> cfg.recRepeats = v));
        controls.add(new Tog(rx - 6, cy + 136, rw + 12, "Blickrichtung anpassen", () -> cfg.recRelativeYaw, v -> cfg.recRelativeYaw = v));
        controls.add(new Btn(rx, cy + 162, rw, 18, () -> "Ausgewählte löschen", () -> {
            MovementRecorder.delete(cfg.selectedRecording);
            clearAndInit();
        }).enabledIf(() -> MovementRecorder.find(cfg.selectedRecording) != null
                && MovementRecorder.state == MovementRecorder.State.IDLE));
    }

    private static final int PROG_ROWS = 5;

    private void buildProgress() {
        ModConfig cfg = ModConfig.get();
        if (!SchematicProgress.hasData() && !SchematicProgress.scanning()) SchematicProgress.start(client);
        progSeen = SchematicProgress.version();

        List<SchematicProgress.Stat> list = SchematicProgress.sorted();
        int maxScroll = Math.max(0, list.size() - PROG_ROWS);
        progScroll = Math.max(0, Math.min(progScroll, maxScroll));
        for (int i = 0; i < PROG_ROWS; i++) {
            int idx = progScroll + i;
            if (idx >= list.size()) break;
            controls.add(new ProgRow(cx + 6, cy + 42 + i * 26, LIST_W - 12, list.get(idx)));
        }
        int ay = cy + 42 + PROG_ROWS * 26 + 2;
        controls.add(new Btn(cx + 6, ay, 28, 16, () -> "▲", () -> { progScroll--; clearAndInit(); })
                .enabledIf(() -> progScroll > 0));
        controls.add(new Btn(cx + 38, ay, 28, 16, () -> "▼", () -> { progScroll++; clearAndInit(); })
                .enabledIf(() -> progScroll < maxScroll));

        int rx = cx + LIST_W + 20, rw = cx + cw - rx - 10;
        controls.add(new Btn(rx, cy + 42, rw, 20, () -> SchematicProgress.scanning() ? "Prüfe …" : "Jetzt prüfen",
                () -> SchematicProgress.start(client)).enabledIf(() -> !SchematicProgress.scanning()));
        controls.add(new Tog(rx - 6, cy + 68, rw + 12, "Ausgeschlossene = fertig",
                () -> cfg.progressExcludedDone, v -> { cfg.progressExcludedDone = v; ModConfig.save(); }));
        controls.add(new Tog(rx - 6, cy + 92, rw + 12, "Ausgeschlossene nicht setzen",
                () -> cfg.progressSkipPlacing, v -> { cfg.progressSkipPlacing = v; ModConfig.save(); }));
        controls.add(new Tog(rx - 6, cy + 116, rw + 12, "Prozent im Spiel zeigen",
                () -> cfg.progressHud, v -> { cfg.progressHud = v; ModConfig.save(); }));
        controls.add(new Btn(rx, cy + 144, rw, 18, () -> "Block aus Hand ausschließen", () -> {
            if (client == null || client.player == null || !(client.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem bi)) {
                SchematicProgress.status = "Nimm erst einen Block in die Hand.";
                return;
            }
            if (!SchematicProgress.isExcluded(bi.getBlock())) SchematicProgress.toggleExcluded(bi.getBlock());
        }));
        controls.add(new Btn(rx, cy + 166, rw, 18, () -> "Alle Ausschlüsse löschen", SchematicProgress::clearExcluded)
                .enabledIf(() -> !cfg.progressExcluded.isEmpty()));
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
        int by = cy + 132, bw = (rw - 12) / 4;
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
        controls.add(new Btn(rx + (bw + 4) * 3, by, bw, 20, () -> "Creator", () -> openTab(Tab.CREATOR)));
        controls.add(new Btn(rx, by + 24, rw, 20,
                () -> "PNG hineinziehen als: " + (dropAsElytra ? "ELYTRA" : "CAPE"),
                () -> dropAsElytra = !dropAsElytra));
    }

    private void buildHt1() {
        ModConfig cfg = ModConfig.get();
        controls.add(new Tog(cx + 6, cy + 26, COL - 12, "Combat Lock aktiv",
                () -> cfg.combatLock, v -> { cfg.combatLock = v; ModConfig.save(); }));
        controls.add(new Tog(cx + 6, cy + 54, COL - 12, "Nur bei Spieler in der Nähe",
                () -> cfg.combatLockNeedsPlayer, v -> { cfg.combatLockNeedsPlayer = v; ModConfig.save(); }));
        controls.add(new Sld(cx + 12, cy + 90, COL - 24, "Verzögerung", " s", 0, 19,
                () -> Math.max(0, Math.min(19, cfg.combatLockDelaySec)), v -> cfg.combatLockDelaySec = v));
    }

    private void buildSpawner() {
        ModConfig cfg = ModConfig.get();
        controls.add(new Tog(cx + 6, cy + 22, COL - 12, "Spawner-Schutz aktiv",
                () -> cfg.spawnerGuard, v -> { cfg.spawnerGuard = v; ModConfig.save(); }));
        controls.add(new Tog(cx + 6, cy + 46, COL - 12, "Spawner abbauen",
                () -> cfg.spawnerGuardMine, v -> { cfg.spawnerGuardMine = v; ModConfig.save(); }));
        controls.add(new Sld(cx + 12, cy + 70, COL - 24, "Erkennungs-Radius", " Blöcke", 10, 100,
                () -> Math.max(10, Math.min(100, cfg.spawnerGuardRadius)), v -> cfg.spawnerGuardRadius = v));
        controls.add(new Tog(cx + 6, cy + 100, COL - 12, "Nur an meiner Base",
                () -> cfg.spawnerGuardHomeOnly, v -> { cfg.spawnerGuardHomeOnly = v; ModConfig.save(); }));
        controls.add(new Btn(cx + 10, cy + 126, COL - 20, 18, () -> "Base hier speichern", () -> SpawnerGuard.saveBase(client)));

        int rx = cx + COL2;
        controls.add(new Tog(rx + 6, cy + 26, COL - 12, "Befehl ausführen",
                () -> cfg.spawnerGuardCommand, v -> { cfg.spawnerGuardCommand = v; ModConfig.save(); }));
        cmdField = addField(new TextFieldWidget(textRenderer, rx + 10, cy + 68, COL - 20, 18, Text.literal("Befehl")));
        cmdField.setMaxLength(100);
        cmdField.setText(cfg.spawnerGuardCommandText == null ? "" : cfg.spawnerGuardCommandText);
        cmdField.setChangedListener(t -> cfg.spawnerGuardCommandText = t);
        controls.add(new Tog(rx + 6, cy + 94, COL - 12, "Danach Server verlassen",
                () -> cfg.spawnerGuardLeaveAfter, v -> { cfg.spawnerGuardLeaveAfter = v; ModConfig.save(); }));
        controls.add(new Sld(rx + 12, cy + 124, COL - 24, "Verlassen nach", " s", 1, 15,
                () -> Math.max(1, Math.min(15, cfg.spawnerGuardLeaveDelaySec)), v -> cfg.spawnerGuardLeaveDelaySec = v));
    }

    private static final String[][] CALC_KEYS = {
            {"C", "DEL", "÷", "×"},
            {"7", "8", "9", "−"},
            {"4", "5", "6", "+"},
    };

    private void buildCalc() {
        int bw = 46, bh = 24, g = 4, x0 = cx + 13, y0 = cy + 38;
        for (int r = 0; r < CALC_KEYS.length; r++) {
            for (int c = 0; c < 4; c++) {
                String k = CALC_KEYS[r][c];
                controls.add(new Btn(x0 + c * (bw + g), y0 + r * (bh + g), bw, bh, () -> k, () -> TrollCalc.press(k))
                        .selectedIf(() -> k.length() == 1 && "÷×−+".contains(k)));
            }
        }
        for (int c = 0; c < 3; c++) {
            String k = String.valueOf(c + 1);
            controls.add(new Btn(x0 + c * (bw + g), y0 + 3 * (bh + g), bw, bh, () -> k, () -> TrollCalc.press(k)));
        }
        controls.add(new Btn(x0 + 3 * (bw + g), y0 + 3 * (bh + g), bw, bh * 2 + g, () -> "=", () -> TrollCalc.press("="))
                .selectedIf(() -> true));
        controls.add(new Btn(x0, y0 + 4 * (bh + g), bw * 2 + g, bh, () -> "0", () -> TrollCalc.press("0")));
        controls.add(new Btn(x0 + 2 * (bw + g), y0 + 4 * (bh + g), bw, bh, () -> ",", () -> TrollCalc.press(",")));
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

        int rbx = cx + LIST_W + 16, rbw = cx + cw - rbx - 6;
        controls.add(new Btn(rbx, cy + 24, rbw, 18,
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
        int th = (rbw - 4) / 2;
        controls.add(new Tog(rbx, cy + 46, th, "Testlauf", () -> cfg.macroDryRun, v -> cfg.macroDryRun = v));
        controls.add(new Tog(rbx + th + 4, cy + 46, th, "Wiederholen", () -> cfg.macroRepeat, v -> cfg.macroRepeat = v));
        controls.add(new Sld(rbx + 6, cy + 72, rbw - 12, "Preis senken ab", " Angeboten", 1, 10,
                () -> cfg.undercutMin, v -> { cfg.undercutMin = v; }));
        controls.add(new Sld(rbx + 6, cy + 100, rbw - 12, "Tempo", " von 3", 1, 3,
                () -> cfg.macroSpeed, v -> { cfg.macroSpeed = v; ModConfig.save(); }));
        controls.add(new Btn(rbx, cy + 130, (rbw - 4) / 2, 20, () -> cfg.macroDryRun ? "Starten (Test)" : "Jetzt starten", () -> {
            close();
            AuctionMacro.start();
        }).enabledIf(() -> !AuctionMacro.running() && !ModConfig.get().sellItems.isEmpty()));
        controls.add(new Btn(rbx + (rbw - 4) / 2 + 4, cy + 130, (rbw - 4) / 2, 20, () -> "Stopp",
                AuctionMacro::stop).enabledIf(AuctionMacro::running));
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
        if (activeTab == Tab.SETTINGS) {
            for (Path p : paths) {
                String err = BackgroundImage.importFile(p);
                if (err == null) ModConfig.get().bgStyle = 6;
                else BackgroundImage.status = err;
            }
            return;
        }
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
        ModConfig cfg = ModConfig.get();
        float t = cfg.menuAnimations ? Math.min(1f, time() / 0.3f) : 1f;
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int acc = Theme.accent();

        ctx.fill(0, 0, width, height, alpha(0x000000, 0.40f * ease));

        int bg = Theme.bg();
        float op = cfg.bgOpacity / 100f;
        ctx.fillGradient(left, top, left + W, top + H, alpha(mix(bg, 0xFFFFFFFF, 0.07f), op), alpha(bg, op));
        drawBackgroundEffect(ctx, time());

        // Kopf- und Fussleiste
        ctx.fill(left + 1, top, left + W, top + 42, 0x40000000);
        ctx.fill(left + 1, top + 42, left + W, top + 43, alpha(acc, 0.35f));
        ctx.fill(left + 1, top + H - 38, left + W, top + H, 0x30000000);

        float pulse = 0.5f + 0.5f * (float) Math.sin(time() * 2.2f);
        switch (cfg.borderStyle) {
            case 1 -> {
                outline(ctx, left, top, W, H, alpha(acc, 0.35f + 0.5f * pulse));
                outline(ctx, left - 1, top - 1, W + 2, H + 2, alpha(acc, 0.10f + 0.25f * pulse));
                outline(ctx, left - 2, top - 2, W + 4, H + 4, alpha(acc, 0.04f + 0.12f * pulse));
            }
            case 2 -> {
                outline(ctx, left, top, W, H, alpha(acc, 0.7f));
                outline(ctx, left + 3, top + 3, W - 6, H - 6, alpha(acc, 0.25f));
            }
            default -> outline(ctx, left, top, W, H, alpha(acc, 0.55f));
        }
        ctx.fill(left, top, left + Math.round(W * ease), top + 2, acc);

        // Fester Titel (erstes Wort in Akzentfarbe, Rest darunter)
        String title = ModConfig.DEFAULT_TITLE;
        int sp = title.indexOf(' ');
        String first = sp < 0 ? title : title.substring(0, sp);
        String rest = sp < 0 ? "" : title.substring(sp + 1);
        text(ctx, first, left + 14, top + 10, acc);
        if (!rest.isEmpty()) text(ctx, rest, left + 14, top + 22, 0xFFFFFFFF);

        // Aktueller Bereich
        if (activeTab != Tab.HOME) {
            text(ctx, "›", left + 120, top + 17, alpha(0xFFFFFF, 0.35f));
            text(ctx, activeTab.title(), left + 132, top + 17, 0xFFFFFFFF);
        }
    }

    private void clipPanel(DrawContext ctx) { ctx.enableScissor(left, top, left + W, top + H); }

    private void drawBackgroundEffect(DrawContext ctx, float t) {
        ModConfig cfg = ModConfig.get();
        switch (cfg.bgStyle) {
            case 0 -> drawHexBackground(ctx, t);
            case 1 -> drawStars(ctx, t);
            case 2 -> drawRain(ctx, t);
            case 3 -> drawSheen(ctx, t);
            case 4 -> drawGrid(ctx, t);
            case 5 -> drawAurora(ctx, t);
            case 6 -> drawImageBackground(ctx, t);
            default -> { }
        }
    }

    private static int hash(int v) {
        v ^= v >>> 16; v *= 0x7feb352d; v ^= v >>> 15; v *= 0x846ca68b; v ^= v >>> 16;
        return v & 0x7fffffff;
    }

    private void drawStars(DrawContext ctx, float t) {
        int acc = Theme.accent();
        clipPanel(ctx);
        for (int i = 0; i < 90; i++) {
            int h1 = hash(i * 7919 + 1), h2 = hash(i * 104729 + 13);
            float speed = 2f + (h1 & 7) * 0.8f;
            float x = (h1 % W) - t * speed;
            x -= (float) Math.floor(x / W) * W;
            int y = h2 % H;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1f + (h2 & 3) * 0.6f) + i);
            int size = (h1 % 11 == 0) ? 2 : 1;
            int col = alpha(i % 5 == 0 ? acc : 0xFFFFFF, 0.20f + 0.65f * tw);
            int sx = left + (int) x, sy = top + y;
            ctx.fill(sx, sy, sx + size, sy + size, col);
        }
        ctx.disableScissor();
    }

    private void drawRain(DrawContext ctx, float t) {
        int acc = Theme.accent();
        clipPanel(ctx);
        for (int i = 0; i < 70; i++) {
            int h1 = hash(i * 31337 + 5), h2 = hash(i * 9973 + 77);
            float speed = 60f + (h1 % 90);
            int len = 6 + (h2 % 9);
            int x = left + (h1 % W);
            float y = ((h2 % (H + 40)) + t * speed) % (H + 40) - 20;
            int sy = top + (int) y;
            ctx.fill(x, sy, x + 1, sy + len, alpha(acc, 0.12f + (h1 % 25) / 100f));
        }
        ctx.disableScissor();
    }

    private void drawSheen(DrawContext ctx, float t) {
        int acc = Theme.accent();
        float p = 0.5f + 0.5f * (float) Math.sin(t * 0.5f);
        clipPanel(ctx);
        ctx.fillGradient(left, top, left + W, top + H, alpha(acc, 0.10f + 0.14f * p), alpha(acc, 0.0f));
        int sx = left + (int) ((t * 45f) % (W + 200)) - 100;
        for (int k = 0; k < 90; k++) {
            float f = 1f - Math.abs(k - 45) / 45f;
            ctx.fill(sx + k, top, sx + k + 1, top + H, alpha(0xFFFFFF, 0.06f * f));
        }
        ctx.disableScissor();
    }

    private void drawGrid(DrawContext ctx, float t) {
        int acc = Theme.accent();
        final int step = 24;
        float ox = (t * 14f) % step, oy = (t * 8f) % step;
        clipPanel(ctx);
        for (int x = -step; x < W + step; x += step) {
            int px = left + Math.round(x + ox);
            ctx.fill(px, top, px + 1, top + H, alpha(acc, 0.07f));
        }
        for (int y = -step; y < H + step; y += step) {
            int py = top + Math.round(y + oy);
            ctx.fill(left, py, left + W, py + 1, alpha(acc, 0.07f));
        }
        int band = top + (int) ((t * 50f) % (H + 24)) - 12;
        ctx.fillGradient(left, band - 12, left + W, band, alpha(acc, 0f), alpha(acc, 0.16f));
        ctx.fillGradient(left, band, left + W, band + 12, alpha(acc, 0.16f), alpha(acc, 0f));
        ctx.disableScissor();
    }

    private void drawAurora(DrawContext ctx, float t) {
        int acc = Theme.accent();
        int[] cols = {acc, mix(acc, 0xFF34D399, 0.6f), mix(acc, 0xFFA78BFA, 0.6f)};
        clipPanel(ctx);
        for (int b = 0; b < 3; b++) {
            for (int x = 0; x < W; x += 3) {
                float yc = top + H * (0.30f + 0.12f * b) + 26f * (float) Math.sin(t * (0.35f + 0.1f * b) + x * 0.012f + b * 2f);
                int y0 = Math.round(yc);
                ctx.fillGradient(left + x, y0 - 34, left + x + 3, y0, alpha(cols[b], 0f), alpha(cols[b], 0.17f));
                ctx.fillGradient(left + x, y0, left + x + 3, y0 + 34, alpha(cols[b], 0.17f), alpha(cols[b], 0f));
            }
        }
        ctx.disableScissor();
    }

    private void drawImageBackground(DrawContext ctx, float t) {
        BackgroundImage.Data d = BackgroundImage.get();
        if (d == null) {
            drawSheen(ctx, t);
            return;
        }
        float ar = W / (float) H;
        int sw, sh;
        if (d.w() / (float) d.h() > ar) {
            sh = d.h();
            sw = Math.round(sh * ar);
        } else {
            sw = d.w();
            sh = Math.round(sw / ar);
        }
        int u = (d.w() - sw) / 2, v = (d.h() - sh) / 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, d.id(), left, top, u, v, W, H, sw, sh, d.w(), d.h());
        ctx.fill(left, top, left + W, top + H, alpha(0x000000, ModConfig.get().customBgDim / 100f));
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
    public boolean charTyped(CharInput input) {
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (activeTab == Tab.PROGRESS && SchematicProgress.version() != progSeen) {
            progSeen = SchematicProgress.version();
            clearAndInit();
        }
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
        for (TextFieldWidget f : fields) f.render(ctx, mouseX, mouseY, delta); // ueber den Karten
        for (Control c : snapshot) c.draw(ctx, mouseX, mouseY);
        renderOverlay(ctx);

        // Ueberblenden beim Reiterwechsel
        float p = ModConfig.get().menuAnimations ? Math.min(1f, (time() - tabChangeAt) / 0.22f) : 1f;
        if (p < 1f) {
            ctx.fill(left + 2, top + 44, left + W - 1, top + H - 39, alpha(Theme.bg(), 0.85f * (1f - p)));
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
            case SETTINGS -> {
                int lx = designX(), bx = lx + SET_A + SET_GAP, px = bx + SET_B + SET_GAP;
                card(ctx, lx, cy - 4, SET_A, 196, "Farben");
                card(ctx, bx, cy - 4, SET_B, 196, "Hintergrund");
                card(ctx, px, cy - 4, left + W - 14 - px, 196, "Menü");
            }
            case FREECAM -> card(ctx, cx, cy - 4, COL, 158, "Freecam");
            case AFK -> {
                card(ctx, cx, cy - 4, COL, 158, "AFK-Wächter");
                card(ctx, cx + COL2, cy - 4, COL, 158, "So funktioniert es");
            }
            case SELL -> {
                card(ctx, cx, cy - 4, LIST_W, 194, "Verkaufsliste (/sell)");
                card(ctx, cx + LIST_W + 10, cy - 4, cw - LIST_W - 10, 194, "Einstellungen");
            }
            case REPAIR -> {
                card(ctx, cx, cy - 4, 250, 194, "Einstellungen");
                card(ctx, cx + 260, cy - 4, cw - 260, 194, "Shop-Kategorie");
            }
            case RECORDER -> {
                card(ctx, cx, cy - 4, LIST_W, 194, "Aufnahmen");
                card(ctx, cx + LIST_W + 10, cy - 4, cw - LIST_W - 10, 194, "Steuerung");
            }
            case PROGRESS -> {
                card(ctx, cx, cy + 16, LIST_W, 174, "Blöcke (Klick = ausschließen)");
                card(ctx, cx + LIST_W + 10, cy + 16, cw - LIST_W - 10, 174, "Einstellungen");
            }
            case HOME -> { }
            case CAPES -> {
                int rx = cx + LIST_W + 10, rw = cx + cw - rx;
                card(ctx, rx, cy, rw, 126, null);
            }
            case CREATOR -> {
                int rx = cx + LIST_W + 10, rw = cx + cw - rx;
                card(ctx, rx, cy, rw, 126, null);
            }
            case HT1 -> {
                card(ctx, cx, cy - 4, COL, 150, "HT1 Combat Lock");
                card(ctx, cx + COL2, cy - 4, COL, 150, "So funktioniert es");
            }
            case SPAWNER -> {
                card(ctx, cx, cy - 4, COL, 184, "Spawner-AFK-Schutz");
                card(ctx, cx + COL2, cy - 4, COL, 184, "Danach");
            }
            case CALC -> {
                card(ctx, cx, cy - 4, 222, 190, null);
                card(ctx, cx + 232, cy - 4, cw - 232, 190, "Verlauf");
            }
            case AUCTION -> {
                card(ctx, cx, cy - 4, LIST_W, 194, "Auktion: Verkaufsliste");
                card(ctx, cx + LIST_W + 10, cy - 4, cw - LIST_W - 10, 194, "Einstellungen");
            }
        }
    }

    /** Texte und Vorschau ueber den Bedienelementen. */
    private void renderOverlay(DrawContext ctx) {
        switch (activeTab) {
            case MAPART -> {
                text(ctx, "Rotation: Piston, Dropper, Treppen usw. werden", cx + COL2 + 12, cy + 126, 0xFF777783);
                text(ctx, "nur gesetzt, wenn sie richtig stehen würden.", cx + COL2 + 12, cy + 137, 0xFF777783);
                text(ctx, "Frame-Modus platziert pro Bild statt pro Tick.", cx + COL2 + 12, cy + 100, 0xFF777783);
                text(ctx, "Bei Rucklern: ausgeschaltet lassen.", cx + COL2 + 12, cy + 113, 0xFF777783);
                drawStatus(ctx);
            }
            case SETTINGS -> renderSettings(ctx);
            case CAPES -> renderCapes(ctx);
            case CREATOR -> renderCreator(ctx);
            case AUCTION -> renderMacros(ctx);
            case HT1 -> renderHt1(ctx);
            case SPAWNER -> renderSpawner(ctx);
            case CALC -> renderCalc(ctx);
            case RECORDER -> renderRecorder(ctx);
            case SELL -> renderSell(ctx);
            case AFK -> renderAfk(ctx);
            case REPAIR -> renderRepair(ctx);
            case PROGRESS -> renderProgress(ctx);
            case HOME -> renderHome(ctx);
            case FREECAM -> renderFreecam(ctx);
        }
    }

    private void renderHome(DrawContext ctx) {
        int acc = Theme.accent();
        for (Group g : GROUPS) {
            int gx = groupX(g), gy = groupY(g);
            int gw = g.tabs().length * TILE_W + (g.tabs().length - 1) * TILE_GAP;
            String l = g.title().toUpperCase(java.util.Locale.ROOT);
            text(ctx, l, gx + 2, gy + 2, alpha(acc, 0.9f));
            int lx = gx + 8 + textRenderer.getWidth(l);
            ctx.fill(lx, gy + 6, gx + gw, gy + 7, alpha(0xFFFFFF, 0.12f));
        }
        ModConfig c = ModConfig.get();
        List<String> on = new ArrayList<>();
        if (c.autoPlace) on.add("Auto-Platzieren");
        if (c.combatLock) on.add("Combat Lock");
        if (c.spawnerGuard) on.add("Spawner-Schutz");
        if (AuctionMacro.running()) on.add("Auktions-Makro");
        if (MovementRecorder.state == MovementRecorder.State.PLAYING) on.add("Recorder");
        text(ctx, on.isEmpty() ? "Nichts aktiv." : "Aktiv: " + String.join(", ", on),
                left + 14, top + H - 24, on.isEmpty() ? 0xFF777783 : 0xFF66DD99);
    }

    private void renderAfk(DrawContext ctx) {
        text(ctx, AfkGuard.modeText(), cx + 12, cy + 144, AfkGuard.active() ? 0xFF66DD99 : 0xFF999999);
        int rx = cx + COL2 + 12;
        drawWrapped(ctx, "Einschalten, während du in einer AFK-Area stehst. Welche Area es ist, ist egal.",
                rx, cy + 26, COL - 24, 0xFFCCCCCC);
        drawWrapped(ctx, "Wirst du weggesetzt (großer Sprung oder neue Welt, z. B. Spawn), sendet die Mod /afk, bis der nächste Teleport kommt.",
                rx, cy + 62, COL - 24, 0xFFCCCCCC);
        drawWrapped(ctx, "Aus wird nur von Hand geschaltet.", rx, cy + 118, COL - 24, 0xFF777783);
        String st = AfkGuard.status;
        text(ctx, st == null ? "" : shorten(st, 78), cx, top + H - 24, 0xFFFFD866);
    }

    private void renderSell(DrawContext ctx) {
        ModConfig cfg = ModConfig.get();
        if (cfg.cellItems.isEmpty()) {
            text(ctx, "Noch keine Items.", cx + 12, cy + 32, 0xFF999999);
            text(ctx, "Item in die Hand nehmen und", cx + 12, cy + 46, 0xFF777783);
            text(ctx, "rechts auf Hinzufügen klicken.", cx + 12, cy + 58, 0xFF777783);
        }
        int rx = cx + LIST_W + 20, rw = cx + cw - rx - 10;
        drawWrapped(ctx, "Dauerbetrieb: Sobald ein Item aus der Liste im Inventar ist, wird sofort alles ins /sell-Menü gelegt und verkauft, ohne Pause und ohne Timer. Ausschalten: Schalter oder Stopp.",
                rx, cy + 132, rw, 0xFF777783);
        text(ctx, "Befehl: /sellmacro  (Stopp: /sellmacro stop)", rx, cy + 179, 0xFFCCCCCC);
        String ms = SellMacro.running() || cellStatus.isEmpty() ? SellMacro.status : cellStatus;
        text(ctx, ms == null ? "" : shorten(ms, 78), cx, top + H - 24, 0xFFFFD866);
    }

    private void renderRepair(DrawContext ctx) {
        int rx = cx + 270, rw = cx + cw - rx - 10;
        text(ctx, "Kategorie-Name (sonst Totem):", rx, cy + 24, 0xFF999999);
        drawWrapped(ctx, "Ist die Spitzhacke in deiner Hand fast kaputt: /shop, Totem (Ausrüstung), XP-Flasche, \"=64\" und Kaufen. Dann werden die Flaschen nach unten geworfen, Mending repariert.",
                rx, cy + 62, rw, 0xFFCCCCCC);
        drawWrapped(ctx, "Klappt ein Shop-Schritt nicht, steht der Menü-Inhalt in config/mapartassist/shop-dump.txt.",
                rx, cy + 128, rw, 0xFF777783);
        text(ctx, "Befehl: /repairmacro", rx, cy + 168, 0xFFCCCCCC);
        String st = AutoRepair.status;
        text(ctx, st == null ? "" : shorten(st, 78), cx, top + H - 24, 0xFFFFD866);
    }

    private void renderRecorder(DrawContext ctx) {
        if (MovementRecorder.list().isEmpty()) {
            text(ctx, "Noch keine Aufnahmen.", cx + 12, cy + 32, 0xFF999999);
            text(ctx, "Aufnahme starten, dann normal", cx + 12, cy + 46, 0xFF777783);
            text(ctx, "spielen (graben, laufen ...) und", cx + 12, cy + 58, 0xFF777783);
            text(ctx, "mit Num 1 beenden.", cx + 12, cy + 70, 0xFF777783);
        }
        String st = switch (MovementRecorder.state) {
            case RECORDING -> "Status: AUFNAHME";
            case PLAYING -> "Status: SPIELT AB";
            default -> "";
        };
        String s = !st.isEmpty() ? st : MovementRecorder.status;
        boolean hasStatus = s != null && !s.isEmpty();
        text(ctx, hasStatus ? s : "Tasten: Num 1 = Aufnahme, Num 2 = Abspielen / Stopp",
                cx, top + H - 24, hasStatus ? 0xFFFFD866 : 0xFF777783);
    }

    private void renderProgress(DrawContext ctx) {
        int acc = Theme.accent();
        int bx = cx, by = cy - 4, bw = cw, bh = 16;
        ctx.fill(bx, by, bx + bw, by + bh, 0xFF14141B);
        double pct = SchematicProgress.percent();
        ctx.fill(bx, by, bx + (int) Math.round(bw * pct / 100.0), by + bh, alpha(acc, 0.55f));
        outline(ctx, bx, by, bw, bh, alpha(acc, 0.5f));
        long[] t = SchematicProgress.totals();
        String label = SchematicProgress.hasData()
                ? String.format(java.util.Locale.ROOT, "%.1f %%   (%d / %d Blöcke)", pct, t[0], t[1])
                : "Noch keine Daten";
        textCentered(ctx, label, bx + bw / 2, by + 4, 0xFFFFFFFF);
        if (SchematicProgress.sorted().isEmpty()) {
            text(ctx, "Keine Daten.", cx + 12, cy + 48, 0xFF999999);
            text(ctx, "Schematic laden und platzieren,", cx + 12, cy + 62, 0xFF777783);
            text(ctx, "dann \"Jetzt prüfen\" klicken.", cx + 12, cy + 74, 0xFF777783);
        }
        String st = SchematicProgress.status;
        text(ctx, st == null ? "" : shorten(st, 78), cx, top + H - 24, 0xFFFFD866);
    }

    private void drawWrapped(DrawContext ctx, String s, int x, int y, int maxW, int color) {
        for (OrderedText l : textRenderer.wrapLines(Text.literal(s), maxW)) {
            ctx.drawTextWithShadow(textRenderer, l, x, y, color);
            y += 11;
        }
    }

    private void renderFreecam(DrawContext ctx) {
        int rx = cx + 12;
        drawWrapped(ctx, "Kamera fliegt frei, dein Spieler bleibt stehen. Taste F7 schaltet um.",
                rx, cy + 92, COL - 24, 0xFFCCCCCC);
        drawWrapped(ctx, "W/A/S/D bewegen, Leertaste hoch, Shift runter, Strg schneller.",
                rx, cy + 116, COL - 24, 0xFF777783);
    }

    private void renderSpawner(DrawContext ctx) {
        ModConfig cfg = ModConfig.get();
        text(ctx, cfg.spawnerGuard ? "Status: AN" : "Status: AUS", cx + 12, cy + 150,
                cfg.spawnerGuard ? 0xFF66DD77 : 0xFFDD6666);
        boolean pick = client != null && client.player != null && SpawnerGuard.hasSilkPickaxe(client.player);
        text(ctx, pick ? "Silk-Touch-Spitzhacke: gefunden" : "Silk-Touch-Spitzhacke: FEHLT",
                cx + 12, cy + 160, pick ? 0xFF66DD77 : 0xFFFFD866);
        String bt = SpawnerGuard.baseText();
        if (cfg.spawnerHomeSet && cfg.spawnerGuardHomeOnly && client != null) {
            bt += SpawnerGuard.atBase(client) ? "  (hier: aktiv)" : "  (weit weg: ruht)";
        }
        text(ctx, bt, cx + 12, cy + 170, cfg.spawnerHomeSet ? 0xFF999999 : 0xFFFFD866);

        int rx = cx + COL2 + 12;
        text(ctx, "Befehl oder Text (z. B. /spawn):", rx, cy + 56, 0xFFCCCCCC);
        if (!cfg.spawnerGuardCommand) {
            text(ctx, "Befehl aus: es wird immer verlassen.", rx, cy + 152, 0xFFFFD866);
        } else if (!cfg.spawnerGuardLeaveAfter) {
            text(ctx, "Nach dem Befehl bleibst du online.", rx, cy + 152, 0xFF777783);
            text(ctx, "Der Schutz schaltet sich dann aus.", rx, cy + 163, 0xFF777783);
        } else {
            text(ctx, "Nach dem Befehl wartest du kurz,", rx, cy + 152, 0xFF777783);
            text(ctx, "dann wird der Server verlassen.", rx, cy + 163, 0xFF777783);
        }
        text(ctx, SpawnerGuard.status, cx, top + H - 24, SpawnerGuard.active() ? 0xFFFF6666 : 0xFFFFD866);
    }

    private void renderCalc(DrawContext ctx) {
        int dx = cx + 13, dy = cy + 2, dw = 196;
        ctx.fill(dx, dy, dx + dw, dy + 30, 0xFF14141B);
        outline(ctx, dx, dy, dw, 30, alpha(Theme.accent(), 0.5f));
        String pend = TrollCalc.pending();
        text(ctx, pend, dx + dw - 6 - textRenderer.getWidth(pend), dy + 4, 0xFF777783);
        String disp = TrollCalc.display();
        text(ctx, disp, dx + dw - 6 - textRenderer.getWidth(disp), dy + 16, 0xFFFFFFFF);

        int rx = cx + 232 + 10;
        int y = cy + 28;
        if (TrollCalc.history.isEmpty()) text(ctx, "Noch nichts gerechnet.", rx, y, 0xFF777783);
        for (String h : TrollCalc.history) {
            text(ctx, h, rx, y, 0xFFCCCCCC);
            y += 13;
        }
    }

    private void renderHt1(DrawContext ctx) {
        ModConfig cfg = ModConfig.get();
        text(ctx, cfg.combatLock ? "Status: AN" : "Status: AUS", cx + 12, cy + 122,
                cfg.combatLock ? 0xFF66DD77 : 0xFFDD6666);
        long rem = CombatLock.remainingMs();
        if (rem >= 0) {
            text(ctx, "Verlasse Server in " + (int) Math.ceil(rem / 1000.0) + " s", cx + 12, cy + 136, 0xFFFF6666);
        } else if (!CombatLock.lastTrigger.isEmpty()) {
            text(ctx, CombatLock.lastTrigger, cx + 12, cy + 136, 0xFFFFD866);
        }
        int rx = cx + COL2 + 12;
        text(ctx, "Sobald du Schaden nimmst, trennt die", rx, cy + 28, 0xFFCCCCCC);
        text(ctx, "Mod die Verbindung (0 s = sofort).", rx, cy + 41, 0xFFCCCCCC);
        text(ctx, "Geprüft wird jeden Frame, die Reaktion", rx, cy + 62, 0xFF777783);
        text(ctx, "kommt direkt nach dem Treffer-Paket.", rx, cy + 75, 0xFF777783);
        text(ctx, "Der Server hat den Treffer da schon", rx, cy + 96, 0xFF777783);
        text(ctx, "gezählt. Taste K schaltet ein/aus.", rx, cy + 109, 0xFF777783);
    }

    private void renderMacros(DrawContext ctx) {
        ModConfig cfg = ModConfig.get();
        if (cfg.sellItems.isEmpty()) {
            text(ctx, "Noch keine Items.", cx + 12, cy + 32, 0xFF999999);
            text(ctx, "Item in die Hand nehmen und", cx + 12, cy + 46, 0xFF777783);
            text(ctx, "rechts auf Hinzufügen klicken.", cx + 12, cy + 58, 0xFF777783);
        }
        int rx = cx + LIST_W + 26;
        text(ctx, "Befehl: /ahmacro   (Stopp: /ahmacro stop)", rx, cy + 156, 0xFFCCCCCC);
        text(ctx, "Preis: /ahmacro preis tnt 200k | reset", rx, cy + 167, 0xFF777783);
        if (cfg.macroDryRun) {
            text(ctx, "TESTLAUF AN: es wird nichts eingestellt!", rx, cy + 178,
                    alpha(0xFF5555, 0.7f + 0.3f * (float) Math.sin(time() * 4)));
        } else {
            text(ctx, cfg.macroSpeed >= 3 ? "Tempo 3: Kick-Gefahr durch Spamschutz!" : "F8 = Diagnose", rx, cy + 178,
                    cfg.macroSpeed >= 3 ? 0xFFFFD866 : 0xFF777783);
        }
        String ms = AuctionMacro.running() || macroStatus.isEmpty() ? AuctionMacro.status : macroStatus;
        text(ctx, ms == null ? "" : ms, cx, top + H - 24, 0xFFFFD866);
    }

    private void renderSettings(DrawContext ctx) {
        int lx = designX();
        int rx = lx + 14 + S + 10 + HUE_W + 16;
        text(ctx, "#", rx - 9, cy + 55, 0xFFCCCCCC);
        text(ctx, "Vorschau", rx, cy + 100, 0xFFCCCCCC);
        int sw = lx + SET_A - 10 - rx;
        ctx.fill(rx - 1, cy + 111, rx + sw + 1, cy + 129, 0xFF000000);
        ctx.fill(rx, cy + 112, rx + sw, cy + 128, 0xFF000000 | editedColor());
        text(ctx, "Voreinstellungen", lx + 14, cy + 150, 0xFF777783);

        int bx = lx + SET_A + SET_GAP, px = bx + SET_B + SET_GAP;
        if (ModConfig.get().bgStyle == 6 && BackgroundImage.get() == null) {
            text(ctx, "PNG/JPG ins Fenster ziehen", bx + 10, cy + 108, 0xFFFFD866);
        }
        text(ctx, "Menü-Titel (fest)", px + 10, cy + 22, 0xFF999999);
        int pw = left + W - 14 - px;
        ctx.fill(px + 10, cy + 33, px + pw - 10, cy + 51, 0x40000000);
        outline(ctx, px + 10, cy + 33, pw - 20, 18, alpha(Theme.accent(), 0.3f));
        text(ctx, shorten(ModConfig.DEFAULT_TITLE, 20), px + 15, cy + 38, 0xFFAAAAB6);
        String bs = BackgroundImage.status;
        text(ctx, bs == null ? "" : bs, left + 14, top + H - 24, 0xFFFFD866);
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
