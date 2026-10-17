package de.mapartassist.gui;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MainScreen extends Screen {
    private static final int W = 230, H = 200;
    private final Screen parent;
    private int left, top;

    public MainScreen(Screen parent) {
        super(Text.literal("MapArt Assist"));
        this.parent = parent;
    }

    private Text toggleText() {
        boolean on = ModConfig.get().autoPlace;
        return Text.literal("Auto-Platzieren: " + (on ? "AN" : "AUS"))
                .formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    private Text invText() {
        boolean on = ModConfig.get().takeFromInventory;
        return Text.literal("Block aus Inventar holen: " + (on ? "AN" : "AUS"))
                .formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        ModConfig cfg = ModConfig.get();

        addDrawableChild(ButtonWidget.builder(toggleText(), b -> {
            cfg.autoPlace = !cfg.autoPlace;
            b.setMessage(toggleText());
        }).dimensions(left + 10, top + 28, W - 20, 20).build());

        addDrawableChild(ButtonWidget.builder(invText(), b -> {
            cfg.takeFromInventory = !cfg.takeFromInventory;
            b.setMessage(invText());
        }).dimensions(left + 10, top + 54, W - 20, 20).build());

        addDrawableChild(new DelaySlider(left + 10, top + 80, W - 20, 20));

        addDrawableChild(ButtonWidget.builder(Text.literal("Farbe ändern"),
                b -> client.setScreen(new ColorScreen(this)))
                .dimensions(left + 10, top + 106, W - 20, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(left + 10, top + H - 30, W - 20, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        Theme.panel(ctx, left, top, W, H);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(textRenderer, "MapArt Assist", left + 10, top + 9, Theme.accent());
        ctx.drawTextWithShadow(textRenderer, "Weitere Features folgen...", left + 10, top + 138, 0xFF888888);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        ModConfig.save();
    }

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
}
