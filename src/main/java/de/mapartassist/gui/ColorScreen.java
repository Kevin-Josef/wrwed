package de.mapartassist.gui;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class ColorScreen extends Screen {
    private static final int W = 230, H = 210;
    private final Screen parent;
    private int left, top;

    public ColorScreen(Screen parent) {
        super(Text.literal("Farbe ändern"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        String[] names = {"R", "G", "B"};
        for (int i = 0; i < 6; i++) {
            boolean accent = i < 3;
            int shift = 16 - 8 * (i % 3);
            int y = top + 34 + i * 20 + (accent ? 0 : 14);
            addDrawableChild(new ChannelSlider(left + 10, y, W - 20, accent, shift,
                    (accent ? "Akzent " : "Hintergrund ") + names[i % 3]));
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Zurücksetzen"), b -> {
            ModConfig.get().accentColor = ModConfig.DEFAULT_ACCENT;
            ModConfig.get().bgColor = ModConfig.DEFAULT_BG;
            clearAndInit();
        }).dimensions(left + 10, top + H - 30, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(left + W - 110, top + H - 30, 100, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        Theme.panel(ctx, left, top, W, H);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(textRenderer, "Farbe ändern", left + 10, top + 9, Theme.accent());
        ctx.drawTextWithShadow(textRenderer, "Akzentfarbe", left + 10, top + 24, 0xFFCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Hintergrundfarbe", left + 10, top + 98, 0xFFCCCCCC);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        ModConfig.save();
    }

    private static class ChannelSlider extends SliderWidget {
        private final boolean accent;
        private final int shift;
        private final String label;

        ChannelSlider(int x, int y, int w, boolean accent, int shift, String label) {
            super(x, y, w, 18, Text.empty(), 0);
            this.accent = accent;
            this.shift = shift;
            this.label = label;
            this.value = current() / 255.0;
            updateMessage();
        }

        private int color() {
            return accent ? ModConfig.get().accentColor : ModConfig.get().bgColor;
        }

        private int current() {
            return (color() >> shift) & 0xFF;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + (int) Math.round(value * 255)));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(value * 255);
            int c = (color() & ~(0xFF << shift)) | (v << shift);
            if (accent) ModConfig.get().accentColor = c;
            else ModConfig.get().bgColor = c;
        }
    }
}
