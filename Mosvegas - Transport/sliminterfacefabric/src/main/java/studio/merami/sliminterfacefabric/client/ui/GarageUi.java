package studio.merami.sliminterfacefabric.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class GarageUi {
    public static final int PANEL = 0xFF101319;
    public static final int STEEL = 0xFF1B2027;
    public static final int STEEL_HOVER = 0xFF262D37;
    public static final int WELL = 0xFF0B0D11;
    public static final int RIM = 0xFF3B4451;
    public static final int RIM_LIGHT = 0xFF5A6675;
    public static final int AMBER = 0xFFF5B335;
    public static final int AMBER_BRIGHT = 0xFFFFCB62;
    public static final int AMBER_DIM = 0xFF5C4622;
    public static final int WHITE = 0xFFF2F4F7;
    public static final int SOFT = 0xFFC3CAD3;
    public static final int MUTED = 0xFF8F99A7;
    public static final int FAINT = 0xFF565F6C;
    public static final int ON_AMBER = 0xFF15171B;
    public static final int GREEN = 0xFF5AD07A;
    public static final int RED = 0xFFF0585D;
    private static final int CUT = 4;
    private static final int STRIPE = 7;
    private static final int CHASE_PERIOD = 18;
    private static final int CHASE_DASH = 10;

    public record Area(int x, int y, int w, int h) {
        public boolean inside(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    private GarageUi() {}

    public static void plate(GuiGraphics gui, Area area, int color) {
        int x0 = area.x;
        int y0 = area.y;
        int x1 = area.right();
        int y1 = area.bottom();
        gui.fill(x0 + CUT, y0, x1 - CUT, y1, color);
        gui.fill(x0, y0 + CUT, x0 + CUT, y1 - CUT, color);
        gui.fill(x1 - CUT, y0 + CUT, x1, y1 - CUT, color);
        for (int step = 1; step < CUT; step++) {
            gui.fill(x0 + CUT - step, y0 + step, x0 + CUT, y0 + step + 1, color);
            gui.fill(x1 - CUT, y0 + step, x1 - CUT + step, y0 + step + 1, color);
            gui.fill(x0 + CUT - step, y1 - step - 1, x0 + CUT, y1 - step, color);
            gui.fill(x1 - CUT, y1 - step - 1, x1 - CUT + step, y1 - step, color);
        }
    }

    public static void framed(GuiGraphics gui, Area area, int fill, int rim) {
        plate(gui, area, rim);
        plate(gui, new Area(area.x + 1, area.y + 1, area.w - 2, area.h - 2), fill);
    }

    public static void panel(GuiGraphics gui, Area area) {
        framed(gui, area, PANEL, RIM);
        gui.fill(area.x + CUT, area.y + 1, area.right() - CUT, area.y + 2, 0x18FFFFFF);
    }

    public static void scanlines(GuiGraphics gui, Area area) {
        gui.drawManaged(() -> {
            for (int y = area.y + 2; y < area.bottom() - 2; y += 2) gui.fill(area.x + 2, y, area.right() - 2, y + 1, 0x20000000);
        });
    }

    public static void bolts(GuiGraphics gui, Area area) {
        int x0 = area.x + 5;
        int y0 = area.y + 5;
        int x1 = area.right() - 8;
        int y1 = area.bottom() - 8;
        for (int[] at : new int[][] {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}}) {
            gui.fill(at[0], at[1], at[0] + 3, at[1] + 3, RIM);
            gui.fill(at[0] + 1, at[1] + 1, at[0] + 2, at[1] + 2, 0xFF12151A);
        }
    }

    public static void hazard(GuiGraphics gui, GarageScreen screen, Area area, int color, double shift) {
        gui.fill(area.x, area.y, area.right(), area.bottom(), 0xFF14171C);
        screen.scissor(gui, area.x, area.y, area.right(), area.bottom());
        int offset = Mth.floor(Mth.positiveModulo(shift, STRIPE * 2));
        for (int row = 0; row < area.h; row++) {
            for (int x = area.x - STRIPE * 2 - area.h + offset; x < area.right(); x += STRIPE * 2) {
                gui.fill(x + row, area.y + row, x + row + STRIPE, area.y + row + 1, color);
            }
        }
        gui.disableScissor();
    }

    public static void title(GuiGraphics gui, Font font, String key, int x, int y, int width) {
        Component text = Component.translatable(key).withStyle(ChatFormatting.BOLD);
        gui.fill(x, y + 1, x + 2, y + 8, AMBER);
        gui.drawString(font, text, x + 6, y, WHITE, false);
        int line = x + 6 + font.width(text) + 6;
        if (line < x + width) gui.fill(line, y + 4, x + width, y + 5, RIM);
    }

    public static int keycap(GuiGraphics gui, Font font, String key, int x, int y) {
        int width = Math.max(11, font.width(key) + 6);
        Area area = new Area(x, y, width, 11);
        gui.fill(x + 1, y, x + width - 1, y + 11, 0xFF0C0E12);
        gui.fill(x, y + 1, x + width, y + 10, 0xFF0C0E12);
        gui.fill(x + 1, y + 1, x + width - 1, y + 9, STEEL_HOVER);
        gui.drawString(font, key, area.x + (width - font.width(key) + 1) / 2, y + 1, SOFT, false);
        return width;
    }

    public static void button(GuiGraphics gui, Font font, Area area, Component text, String key, boolean accent, boolean enabled, boolean hover) {
        int fill = !enabled ? STEEL : accent ? (hover ? AMBER_BRIGHT : AMBER) : (hover ? STEEL_HOVER : STEEL);
        int rim = !enabled ? RIM : accent ? fill : (hover ? RIM_LIGHT : RIM);
        framed(gui, area, fill, rim);
        int color = !enabled ? FAINT : accent ? ON_AMBER : WHITE;
        gui.drawString(font, text, area.x + (area.w - font.width(text)) / 2, area.y + (area.h - 8) / 2, color, false);
        if (key != null && area.w - font.width(text) > 2 * font.width(key) + 28) {
            gui.drawString(font, key, area.right() - font.width(key) - 7, area.y + (area.h - 8) / 2, accent && enabled ? 0xA015171B : MUTED, false);
        }
    }

    public static void chip(GuiGraphics gui, Font font, Area area, String text, boolean selected, boolean hover, boolean enabled) {
        int fill = selected ? AMBER : hover && enabled ? STEEL_HOVER : STEEL;
        int rim = selected ? AMBER : hover && enabled ? RIM_LIGHT : RIM;
        framed(gui, area, fill, rim);
        int color = selected ? ON_AMBER : enabled ? WHITE : FAINT;
        gui.drawString(font, text, area.x + (area.w - font.width(text)) / 2, area.y + (area.h - 8) / 2, color, false);
    }

    public static void chase(GuiGraphics gui, GarageScreen screen, Area area, double phase) {
        int y = area.bottom() - 3;
        screen.scissor(gui, area.x + CUT, y, area.right() - CUT, y + 2);
        int shift = Mth.floor(phase * CHASE_PERIOD);
        for (int x = area.x - CHASE_PERIOD + shift; x < area.right(); x += CHASE_PERIOD) gui.fill(x, y, x + CHASE_DASH, y + 2, AMBER);
        gui.disableScissor();
    }

    public static void bigText(GuiGraphics gui, Font font, Component text, int x, int y, float scale, int color) {
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, text, 0, 0, color, false);
        gui.pose().popPose();
    }

    public static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int from = a >>> shift & 0xFF;
            int to = b >>> shift & 0xFF;
            result |= Math.round(from + (to - from) * t) << shift;
        }
        return result;
    }

    public static String number(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder text = new StringBuilder(value < 0 ? "-" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) text.append(' ');
            text.append(digits.charAt(i));
        }
        return text.toString();
    }
}
