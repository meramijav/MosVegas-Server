package studio.merami.sliminterfacefabric.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class HudText {
    private static final float LIFT = 1;

    private HudText() {}

    public static void drawString(GuiGraphics gui, Font font, String text, int x, int y, int color, boolean shadow) {
        gui.pose().pushPose();
        gui.pose().translate(0, 0, LIFT);
        gui.drawString(font, text, x, y, color, shadow);
        gui.pose().popPose();
    }

    public static void drawString(GuiGraphics gui, Font font, Component text, int x, int y, int color, boolean shadow) {
        gui.pose().pushPose();
        gui.pose().translate(0, 0, LIFT);
        gui.drawString(font, text, x, y, color, shadow);
        gui.pose().popPose();
    }

    public static void drawString(GuiGraphics gui, Font font, String text, int x, int y, int color) {
        drawString(gui, font, text, x, y, color, true);
    }

    public static void drawString(GuiGraphics gui, Font font, Component text, int x, int y, int color) {
        drawString(gui, font, text, x, y, color, true);
    }

    public static void drawCenteredString(GuiGraphics gui, Font font, String text, int x, int y, int color) {
        drawString(gui, font, text, x - font.width(text) / 2, y, color, true);
    }

    public static void drawCenteredString(GuiGraphics gui, Font font, Component text, int x, int y, int color) {
        drawString(gui, font, text, x - font.width(text) / 2, y, color, true);
    }
}
