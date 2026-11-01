package ru.mosvegas.casino.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Flat casino-style button drawn with plain fills (no vanilla widget textures). */
public class UiButton {
    public final int x, y, w, h;
    public final Text label;
    public final int color;
    public final boolean enabled;
    public final Runnable action;
    public Text sub;
    public boolean selected;

    public UiButton(int x, int y, int w, int h, Text label, int color, boolean enabled, Runnable action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.color = color;
        this.enabled = enabled;
        this.action = action;
    }

    public UiButton sub(Text sub) {
        this.sub = sub;
        return this;
    }

    public boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public void draw(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        boolean hover = enabled && hit(mouseX, mouseY);
        int base = enabled ? color : 0xFF3A3A3A;
        int fill = hover ? brighten(base, 30) : base;
        ctx.fill(x, y, x + w, y + h, 0xC0000000);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        int border = selected ? 0xFFFFD84A : hover ? 0xFFFFFFFF : 0xFF000000 | brighten(base, 60);
        ctx.drawBorder(x, y, w, h, border);
        if (selected) ctx.drawBorder(x - 1, y - 1, w + 2, h + 2, 0xFFFFD84A);
        int textColor = enabled ? 0xFFFFFF : 0x8A8A8A;
        if (sub == null) {
            ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2, textColor);
        } else {
            ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + h / 2 - 9, textColor);
            ctx.drawCenteredTextWithShadow(tr, sub, x + w / 2, y + h / 2 + 1, enabled ? 0xFFE9A8 : 0x8A8A8A);
        }
    }

    static int brighten(int argb, int amount) {
        int a = argb >>> 24, r = Math.min(255, ((argb >> 16) & 0xFF) + amount), g = Math.min(255, ((argb >> 8) & 0xFF) + amount), b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
