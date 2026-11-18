package ru.mosvegas.character.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.MosVegasCharacter;

public final class Ui {
    public static final float DW = 960f, DH = 540f;

    public static final int BG = 0xFF1D1415;
    public static final int BG2 = 0xFF181011;
    public static final int PANEL = 0xFF241A1B;
    public static final int PANEL2 = 0xFF2E2122;
    public static final int PANEL3 = 0xFF382829;
    public static final int WELL = 0xFF1C1415;
    public static final int LINE = 0xFF423031;
    public static final int RED = 0xFFD4474B;
    public static final int RED_TOP = 0xFFE0565A;
    public static final int RED_HOVER = 0xFFE05155;
    public static final int RED2 = 0xFF9B3234;
    public static final int RED3 = 0xFF6C0D17;
    public static final int GOLD = 0xFFECAA38;
    public static final int GOLD2 = 0xFFFBD098;
    public static final int GOLD3 = 0xFFFFF0CD;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int SOFT = 0xFFC6C3C3;
    public static final int TEXT = 0xFFB4AFAF;
    public static final int MUTED = 0xFF7B7777;
    public static final int FLOOR = 0xFF110B0C;

    private static final Identifier RR = tex("rr");
    private static final Identifier RING = tex("ring");
    private static final Identifier RING_BOLD = tex("ring_bold");
    private static final Identifier DISC = tex("disc");
    private static final Identifier FADE = tex("fade");
    public static final Identifier CHECK = tex("check");
    public static final Identifier CHEVRON_L = tex("chevron_l");
    public static final Identifier CHEVRON_R = tex("chevron_r");
    public static final Identifier DIAMOND = tex("diamond");
    public static final Identifier DIAMOND_RING = tex("diamond_ring");
    public static final Identifier LOGO = tex("logo");
    private static final int NINE = 72, CORNER = 32, Q = 4;

    public enum F {
        B28(true, 28), B13(true, 13), B12(true, 12), B9_5(true, 9.5f), B8_5(true, 8.5f), B8(true, 8), B7_5(true, 7.5f), B7(true, 7),
        B6_8(true, 6.8f), B6_5(true, 6.5f),
        M8(false, 8), M7_5(false, 7.5f), M7(false, 7), M6_8(false, 6.8f), M6_5(false, 6.5f), M5_8(false, 5.8f);

        public final boolean bold;
        public final float size;

        F(boolean bold, float size) {
            this.bold = bold;
            this.size = size;
        }
    }

    public final DrawContext ctx;
    public final float k;
    public final float w, h;
    private final float px;

    private Ui(DrawContext ctx, float k, float w, float h, float px) {
        this.ctx = ctx;
        this.k = k;
        this.w = w;
        this.h = h;
        this.px = px;
    }

    private static Identifier tex(String name) {
        return MosVegasCharacter.id("textures/gui/" + name + ".png");
    }

    private static float pixels(int screenW, int screenH) {
        double gui = MinecraftClient.getInstance().getWindow().getScaleFactor();
        double raw = Math.min(screenW / DW, screenH / DH) * gui;
        return (float) Math.max(0.75, raw);
    }

    public static float scaleFor(int screenW, int screenH) {
        return pixels(screenW, screenH) / (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    public static Ui begin(DrawContext ctx, int screenW, int screenH) {
        float px = pixels(screenW, screenH);
        float k = scaleFor(screenW, screenH);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(k, k, 1f);
        return new Ui(ctx, k, screenW / k, screenH / k, px);
    }

    public void end() {
        ctx.getMatrices().pop();
    }

    public static float toDesign(double gui, float k) {
        return (float) (gui / k);
    }

    public int gui(float design) {
        return Math.round(design * k);
    }

    private void tint(int argb) {
        ctx.setShaderColor((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private void quad(Identifier id, float x, float y, float w, float h, float u, float v, int rw, int rh, int tw, int th) {
        int x0 = Math.round(x * Q), y0 = Math.round(y * Q), x1 = Math.round((x + w) * Q), y1 = Math.round((y + h) * Q);
        if (x1 <= x0 || y1 <= y0) return;
        ctx.drawTexture(id, x0, y0, x1 - x0, y1 - y0, u, v, rw, rh, tw, th);
    }

    private void nine(Identifier id, float x, float y, float w, float h, float r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2f);
        RenderSystem.enableBlend();
        tint(argb);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1f / Q, 1f / Q, 1f);
        int mid = NINE - 2 * CORNER;
        float[] xs = {x, x + r, x + w - r};
        float[] ws = {r, w - 2 * r, r};
        float[] ys = {y, y + r, y + h - r};
        float[] hs = {r, h - 2 * r, r};
        int[] us = {0, CORNER, CORNER + mid};
        int[] uw = {CORNER, mid, CORNER};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (ws[i] <= 0 || hs[j] <= 0) continue;
                quad(id, xs[i], ys[j], ws[i], hs[j], us[i], us[j], uw[i], uw[j], NINE, NINE);
            }
        }
        ctx.getMatrices().pop();
        ctx.setShaderColor(1, 1, 1, 1);
    }

    public void rr(float x, float y, float w, float h, float r, int argb) {
        nine(RR, x, y, w, h, r, argb);
    }

    public void ring(float x, float y, float w, float h, float r, int argb, boolean bold) {
        nine(bold ? RING_BOLD : RING, x, y, w, h, r, argb);
    }

    public void image(Identifier id, float x, float y, float w, float h, int argb) {
        RenderSystem.enableBlend();
        tint(argb);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1f / Q, 1f / Q, 1f);
        quad(id, x, y, w, h, 0, 0, 1, 1, 1, 1);
        ctx.getMatrices().pop();
        ctx.setShaderColor(1, 1, 1, 1);
    }

    public void region(Identifier id, float x, float y, float w, float h, int u, int v, int rw, int rh, int tw, int th) {
        RenderSystem.enableBlend();
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1f / Q, 1f / Q, 1f);
        quad(id, x, y, w, h, u, v, rw, rh, tw, th);
        ctx.getMatrices().pop();
    }

    public void ellipse(float cx, float cy, float rx, float ry, int argb) {
        image(DISC, cx - rx, cy - ry, rx * 2, ry * 2, argb);
    }

    public void rect(float x, float y, float w, float h, int argb) {
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1f / Q, 1f / Q, 1f);
        int x0 = Math.round(x * Q), y0 = Math.round(y * Q);
        ctx.fill(x0, y0, Math.max(x0 + 1, Math.round((x + w) * Q)), Math.max(y0 + 1, Math.round((y + h) * Q)), argb);
        ctx.getMatrices().pop();
    }

    public void vgradient(float x, float y, float w, float h, int top, int bottom) {
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1f / Q, 1f / Q, 1f);
        ctx.fillGradient(Math.round(x * Q), Math.round(y * Q), Math.round((x + w) * Q), Math.round((y + h) * Q), top, bottom);
        ctx.getMatrices().pop();
    }

    public void divider(float x0, float x1, float y) {
        image(FADE, x0, y, x1 - x0, 1f, RED2);
        float cx = (x0 + x1) / 2f;
        image(DIAMOND, cx - 4.5f, y + 0.5f - 4.5f, 9, 9, RED3);
        image(DIAMOND_RING, cx - 4.5f, y + 0.5f - 4.5f, 9, 9, RED);
        image(DIAMOND, cx - 1.6f, y + 0.5f - 1.6f, 3.2f, 3.2f, RED);
    }

    public void check(float cx, float cy, float size, int argb) {
        image(CHECK, cx - size / 2, cy - size / 2, size, size, argb);
    }

    public float width(String s, F f, float tracking) {
        return TextCache.advance(s, f.bold, f.size * px, tracking * px) / px;
    }

    public float width(String s, F f) {
        return width(s, f, 0);
    }

    public float text(String s, F f, float x, float cy, int argb, int align, float tracking) {
        if (s.isEmpty()) return x;
        TextCache.Entry e = TextCache.get(s, f.bold, f.size * px, tracking * px);
        float tw = e.advance() / px;
        float left = align == 0 ? x : align == 1 ? x - tw / 2 : x - tw;
        var lm = TextCache.metrics(f.bold, f.size * px);
        float baselinePx = cy * px + (lm.getAscent() - lm.getDescent()) / 2f;
        int x0 = Math.round(left * px) - 2;
        int y0 = Math.round(baselinePx - e.baseline());
        RenderSystem.enableBlend();
        tint(argb);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1f / px, 1f / px, 1f);
        ctx.drawTexture(e.id(), x0, y0, e.w(), e.h(), 0, 0, e.w(), e.h(), e.w(), e.h());
        ctx.getMatrices().pop();
        ctx.setShaderColor(1, 1, 1, 1);
        return left + tw;
    }

    public float text(String s, F f, float x, float cy, int argb, int align) {
        return text(s, f, x, cy, argb, align, 0);
    }

    public String fit(String s, F f, float room) {
        if (width(s, f) <= room) return s;
        String cut = s;
        while (cut.length() > 1 && width(cut + "…", f) > room) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }

    public void primary(float x, float y, float w, float h, String label, F f, boolean hover, boolean enabled) {
        if (!enabled) {
            rr(x, y, w, h - 3, 7, PANEL);
            text(label, f, x + w / 2, y + (h - 3) / 2, MUTED, 1);
            return;
        }
        int body = hover ? RED_HOVER : RED;
        rr(x, y, w, h, 7, RED3);
        rr(x, y, w, h - 3, 7, body);
        rr(x + 1, y + 1, w - 2, 4, 3, hover ? 0xFFEE6A6E : RED_TOP);
        rr(x + 1, y + 3, w - 2, h - 7, 6, body);
        text(label, f, x + w / 2, y + (h - 3) / 2, WHITE, 1);
    }

    public void ghost(float x, float y, float w, float h, String label, F f, boolean hover) {
        if (hover) rr(x, y, w, h, 7, PANEL2);
        ring(x, y, w, h, 7, hover ? 0xFF5A4344 : LINE, false);
        text(label, f, x + w / 2, y + h / 2, hover ? WHITE : SOFT, 1);
    }

    public float keycap(float x, float y, String key) {
        float kw = width(key, F.B6_5, 0.5f) + 12;
        rr(x, y, kw, 15, 4, 0xFF140E0F);
        rr(x, y, kw, 13, 4, PANEL2);
        text(key, F.B6_5, x + kw / 2, y + 6.5f, GOLD2, 1, 0.5f);
        return kw;
    }

    public static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
