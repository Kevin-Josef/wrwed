package de.mapartassist.gui;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;

public final class Theme {
    private Theme() {}

    public static int accent() { return 0xFF000000 | ModConfig.get().accentColor; }
    public static int bg() { return 0xFF000000 | ModConfig.get().bgColor; }

    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, bg());
        ctx.fill(x, y, x + w, y + 3, accent());
        ctx.fill(x, y + h - 1, x + w, y + h, accent());
        ctx.fill(x, y, x + 1, y + h, accent());
        ctx.fill(x + w - 1, y, x + w, y + h, accent());
    }
}
