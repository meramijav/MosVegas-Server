package studio.merami.sliminterfacefabric.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.ui.GarageUi.Area;

import java.util.Locale;

public final class BrandUi {
    public static final int RED = 0xFFD4474B;
    public static final int RED_SOFT = 0xFFF0797C;
    public static final int GOLD = 0xFFECAA38;
    public static final int GOLD_LIGHT = 0xFFFBD098;
    public static final int GOLD_PALE = 0xFFFFF0CD;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int SOFT = 0xFFC6C3C3;
    public static final int TEXT = 0xFFB4AFAF;
    public static final int MUTED = 0xFF7B7777;
    public static final int GREEN = 0xFF86D9A2;
    public static final int LINE = 0x16FFFFFF;
    private static final ResourceLocation LOGO = SlimVehicle.id("textures/gui/brand/logo.png");
    private static final int LOGO_SIZE = 128;
    private static final int STRIP_WIDTH = 128;
    private static final float CAP_TOP = .2f;
    private static final float CAP_HEIGHT = .6f;
    private static final float GLYPH_SHIFT = 3;
    private static final int LIP = 2;

    public enum Type {
        CAP("cap", 7.5f, .9f, true),
        SMALL("small", 7.5f, 0, false),
        BODY("body", 8.5f, 0, false),
        LABEL("label", 9.5f, 0, false),
        BUTTON("button", 10.5f, .45f, true),
        VALUE("value", 13.5f, 0, false),
        TITLE("title", 17.5f, .25f, false);

        private final ResourceLocation[] fonts = new ResourceLocation[4];
        private final float size;
        private final float tracking;
        private final boolean upper;

        Type(String id, float size, float tracking, boolean upper) {
            for (int i = 0; i < fonts.length; i++) fonts[i] = SlimVehicle.id("brand_" + id + "_" + (i + 1));
            this.size = size;
            this.tracking = tracking;
            this.upper = upper;
        }

        public float size() {
            return size;
        }

        private String shape(String text) {
            return upper ? text.toUpperCase(Locale.ROOT) : text;
        }

        private Component component(String text, int scale) {
            ResourceLocation font = fonts[Math.max(1, Math.min(4, scale)) - 1];
            return Component.literal(text).withStyle(style -> style.withFont(font));
        }
    }

    public enum Box {
        PANEL("panel", 50, 50, 23, 12),
        GLASS("glass", 46, 46, 21, 10),
        CARD("card", 22, 22, 9, 0),
        CARD_HOVER("card_hover", 22, 22, 9, 0),
        CARD_ON("card_on", 130, 70, 15, 6),
        CHIP("chip", 18, 20, 7, 0),
        CHIP_HOVER("chip_hover", 18, 20, 7, 0),
        CHIP_ON("chip_on", 28, 30, 12, 5),
        PRIMARY("primary", 40, 48, 18, 9),
        PRIMARY_HOVER("primary_hover", 40, 48, 18, 9),
        GHOST("ghost", 20, 20, 8, 0),
        GHOST_HOVER("ghost_hover", 20, 20, 8, 0),
        KEY("key", 12, 11, 4, 0),
        KEY_GOLD("key_gold", 12, 11, 4, 0),
        SEG("seg", 24, 22, 10, 0),
        SEG_ON("seg_on", 28, 26, 12, 4),
        INPUT("input", 18, 18, 7, 0),
        INPUT_FOCUS("input_focus", 18, 18, 7, 0),
        WELL("well", 374, 100, 12, 0);

        private final ResourceLocation[] ids;
        private final int w;
        private final int h;
        private final int slice;
        private final int margin;

        Box(String name, int w, int h, int slice, int margin) {
            ids = textures(name);
            this.w = w;
            this.h = h;
            this.slice = slice;
            this.margin = margin;
        }
    }

    public enum Icon {
        STAR("star", 9, 9),
        STAR_WHITE("star_white", 9, 9),
        CROSS("cross", 9, 9),
        DIAMOND("diamond", 11, 11),
        BULLET("bullet", 6, 6),
        RADIO("radio", 9, 9),
        RADIO_ON("radio_on", 9, 9),
        DOT("dot", 13, 13),
        DOT_RING("dot_ring", 19, 19),
        TILE("tile", 16, 16),
        TILE_RIM("tile_rim", 16, 16),
        KNOB("knob", 12, 12),
        KNOB_FILL("knob_fill", 12, 12),
        FLOOR_SHADOW("floor_shadow", 64, 24);

        private final ResourceLocation[] ids;
        public final int w;
        public final int h;

        Icon(String name, int w, int h) {
            ids = textures(name);
            this.w = w;
            this.h = h;
        }
    }

    public enum Strip {
        HAIR("hair"),
        FADE_RED("fade_red"),
        BAR("bar"),
        NEON("neon");

        private final ResourceLocation[] ids;

        Strip(String name) {
            ids = textures(name);
        }
    }

    private BrandUi() {}

    private static ResourceLocation[] textures(String name) {
        return new ResourceLocation[] {SlimVehicle.id("textures/gui/brand/" + name + "_x2.png"), SlimVehicle.id("textures/gui/brand/" + name + "_x3.png")};
    }

    private static int unit(GarageScreen screen) {
        return screen.pixelScale() >= 3 ? 3 : 2;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static float snap(GarageScreen screen, float value) {
        int scale = screen.pixelScale();
        return Math.round(value * scale) / (float) scale;
    }

    public static void tint(GuiGraphics gui, int argb) {
        gui.setColor((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    public static int alpha(int argb, float alpha) {
        int value = Math.round((argb >>> 24) * Math.max(0, Math.min(1, alpha)));
        return Math.max(5, value) << 24 | argb & 0xFFFFFF;
    }

    public static void box(GuiGraphics gui, GarageScreen screen, Box box, Area area) {
        box(gui, screen, box, area.x(), area.y(), area.w(), area.h(), 1);
    }

    public static void box(GuiGraphics gui, GarageScreen screen, Box box, int x, int y, int w, int h, float alpha) {
        int u = unit(screen);
        ResourceLocation id = box.ids[u - 2];
        int tw = box.w * u;
        int th = box.h * u;
        int x0 = (x - box.margin) * u;
        int y0 = (y - box.margin) * u;
        int dw = (w + 2 * box.margin) * u;
        int dh = (h + 2 * box.margin) * u;
        int sx = Math.min(box.slice * u, dw / 2);
        int sy = Math.min(box.slice * u, dh / 2);
        int[] xs = {x0, x0 + sx, x0 + dw - sx, x0 + dw};
        int[] ys = {y0, y0 + sy, y0 + dh - sy, y0 + dh};
        int[] us = {0, sx, tw - sx, tw};
        int[] vs = {0, sy, th - sy, th};
        RenderSystem.enableBlend();
        gui.setColor(1, 1, 1, alpha);
        gui.pose().pushPose();
        gui.pose().scale(1f / u, 1f / u, 1);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int width = xs[i + 1] - xs[i];
                int height = ys[j + 1] - ys[j];
                if (width <= 0 || height <= 0) continue;
                gui.blit(id, xs[i], ys[j], width, height, us[i], vs[j], us[i + 1] - us[i], vs[j + 1] - vs[j], tw, th);
            }
        }
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    public static void icon(GuiGraphics gui, GarageScreen screen, Icon icon, float x, float y, float w, float h, int argb) {
        int u = unit(screen);
        RenderSystem.enableBlend();
        tint(gui, argb);
        gui.pose().pushPose();
        gui.pose().translate(snap(screen, x), snap(screen, y), 0);
        gui.pose().scale(w / (icon.w * u), h / (icon.h * u), 1);
        gui.blit(icon.ids[u - 2], 0, 0, 0, 0, icon.w * u, icon.h * u, icon.w * u, icon.h * u);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    public static void icon(GuiGraphics gui, GarageScreen screen, Icon icon, float x, float y, int argb) {
        icon(gui, screen, icon, x, y, icon.w, icon.h, argb);
    }

    public static void strip(GuiGraphics gui, GarageScreen screen, Strip strip, float x, float y, float w, float h, boolean mirror, int argb) {
        if (w <= 0 || h <= 0) return;
        int u = unit(screen);
        int tw = STRIP_WIDTH * u;
        RenderSystem.enableBlend();
        tint(gui, argb);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(w / tw, h, 1);
        gui.blit(strip.ids[u - 2], 0, 0, tw, 1, mirror ? tw : 0, 0, mirror ? -tw : tw, 1, tw, 1);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    public static void logo(GuiGraphics gui, GarageScreen screen, float x, float y, float size) {
        RenderSystem.enableBlend();
        gui.pose().pushPose();
        gui.pose().translate(snap(screen, x), snap(screen, y), 0);
        gui.pose().scale(size / LOGO_SIZE, size / LOGO_SIZE, 1);
        gui.blit(LOGO, 0, 0, 0, 0, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
        gui.pose().popPose();
    }

    public static float width(GarageScreen screen, String text, Type type) {
        String shaped = type.shape(text);
        if (shaped.isEmpty()) return 0;
        int scale = screen.pixelScale();
        if (type.tracking == 0) return font().getSplitter().stringWidth(type.component(shaped, scale));
        float total = 0;
        for (int i = 0; i < shaped.length(); i++) total += font().getSplitter().stringWidth(type.component(String.valueOf(shaped.charAt(i)), scale));
        return total + type.tracking * (shaped.length() - 1);
    }

    public static float text(GuiGraphics gui, GarageScreen screen, String text, Type type, float x, float capTop, int argb) {
        String shaped = type.shape(text);
        int scale = screen.pixelScale();
        float y = snap(screen, capTop - type.size * CAP_TOP + GLYPH_SHIFT);
        float at = snap(screen, x);
        if (type.tracking == 0) {
            draw(gui, type.component(shaped, scale), at, y, argb);
            return at + font().getSplitter().stringWidth(type.component(shaped, scale));
        }
        for (int i = 0; i < shaped.length(); i++) {
            Component glyph = type.component(String.valueOf(shaped.charAt(i)), scale);
            draw(gui, glyph, snap(screen, at), y, argb);
            at += font().getSplitter().stringWidth(glyph) + type.tracking;
        }
        return at - type.tracking;
    }

    public static void right(GuiGraphics gui, GarageScreen screen, String text, Type type, float right, float capTop, int argb) {
        text(gui, screen, text, type, right - width(screen, text, type), capTop, argb);
    }

    public static void centred(GuiGraphics gui, GarageScreen screen, String text, Type type, float cx, float cy, int argb) {
        text(gui, screen, text, type, cx - width(screen, text, type) / 2, cy - capHeight(type) / 2, argb);
    }

    public static float capHeight(Type type) {
        return type.size * CAP_HEIGHT;
    }

    private static void draw(GuiGraphics gui, Component text, float x, float y, int argb) {
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.drawString(font(), text, 0, 0, argb, false);
        gui.pose().popPose();
    }

    public static String fit(GarageScreen screen, String text, Type type, float room) {
        if (width(screen, text, type) <= room) return text;
        String cut = text;
        while (cut.length() > 1 && width(screen, cut + "…", type) > room) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }

    public static int keycap(GuiGraphics gui, GarageScreen screen, String key, float x, float y, boolean gold) {
        return keycap(gui, screen, key, x, y, gold, 1);
    }

    public static int keycap(GuiGraphics gui, GarageScreen screen, String key, float x, float y, boolean gold, float alpha) {
        int w = keycapWidth(screen, key);
        int left = Math.round(x);
        int top = Math.round(y);
        box(gui, screen, gold ? Box.KEY_GOLD : Box.KEY, left, top, w, 11, alpha);
        centred(gui, screen, key, Type.CAP, left + w / 2f, top + 5f, alpha(gold ? GOLD_PALE : TEXT, alpha));
        return w;
    }

    public static int keycapWidth(GarageScreen screen, String key) {
        return Math.max(14, Math.round(width(screen, key, Type.CAP) + 7));
    }

    public static void primary(GuiGraphics gui, GarageScreen screen, Area area, String label, String key, boolean star, boolean enabled, boolean hover) {
        float alpha = enabled ? 1 : .45f;
        box(gui, screen, hover ? Box.PRIMARY_HOVER : Box.PRIMARY, area.x(), area.y(), area.w(), area.h(), alpha);
        float labelWidth = width(screen, label, Type.BUTTON);
        float keyWidth = key == null ? 0 : keycapWidth(screen, key) + 7;
        float starWidth = star ? 13 : 0;
        float x = area.x() + (area.w() - labelWidth - keyWidth - starWidth) / 2;
        float cy = area.y() + (area.h() - LIP) / 2f;
        int ink = alpha(WHITE, enabled ? 1 : .55f);
        if (star) {
            icon(gui, screen, Icon.STAR_WHITE, x, cy - 4.5f, alpha(GOLD_PALE, enabled ? 1 : .55f));
            x += starWidth;
        }
        text(gui, screen, label, Type.BUTTON, x, cy - capHeight(Type.BUTTON) / 2, ink);
        if (key != null) keycap(gui, screen, key, x + labelWidth + 7, cy - 5.5f, true, enabled ? 1 : .55f);
    }

    public static void ghost(GuiGraphics gui, GarageScreen screen, Area area, String label, String key, boolean hover) {
        box(gui, screen, hover ? Box.GHOST_HOVER : Box.GHOST, area);
        float labelWidth = width(screen, label, Type.BUTTON);
        float keyWidth = key == null ? 0 : keycapWidth(screen, key) + 6;
        float x = area.x() + (area.w() - labelWidth - keyWidth) / 2;
        float cy = area.y() + area.h() / 2f;
        text(gui, screen, label, Type.BUTTON, x, cy - capHeight(Type.BUTTON) / 2, hover ? WHITE : SOFT);
        if (key != null) keycap(gui, screen, key, x + labelWidth + 6, cy - 5.5f, false);
    }

    public static void close(GuiGraphics gui, GarageScreen screen, Area area, boolean hover) {
        box(gui, screen, hover ? Box.GHOST_HOVER : Box.GHOST, area);
        icon(gui, screen, Icon.CROSS, area.x() + (area.w() - 9) / 2f, area.y() + (area.h() - 9) / 2f, hover ? WHITE : TEXT);
    }

    public static void balance(GuiGraphics gui, GarageScreen screen, String label, String value, float right, float top, int argb) {
        right(gui, screen, label, Type.CAP, right, top, MUTED);
        right(gui, screen, value, Type.VALUE, right, top + 10, argb);
    }

    public static void divider(GuiGraphics gui, GarageScreen screen, int x0, int x1, int y, float alpha) {
        float centre = (x0 + x1) / 2f;
        strip(gui, screen, Strip.FADE_RED, x0, y, centre - 9 - x0, 1, false, alpha(WHITE, alpha));
        strip(gui, screen, Strip.FADE_RED, centre + 9, y, x1 - centre - 9, 1, true, alpha(WHITE, alpha));
        icon(gui, screen, Icon.DIAMOND, centre - 5.5f, y + .5f - 5.5f, alpha(WHITE, alpha));
    }

    public static void hair(GuiGraphics gui, GarageScreen screen, Area panel) {
        strip(gui, screen, Strip.HAIR, panel.x() + 18, panel.y(), panel.w() - 36, 1, false, WHITE);
    }

    public static void header(GuiGraphics gui, GarageScreen screen, String title, int x, int y) {
        icon(gui, screen, Icon.BULLET, x, y - .5f, WHITE);
        text(gui, screen, title, Type.CAP, x + 10, y + 1, WHITE);
    }
}
