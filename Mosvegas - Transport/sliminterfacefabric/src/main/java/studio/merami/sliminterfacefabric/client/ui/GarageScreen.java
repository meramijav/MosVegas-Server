package studio.merami.sliminterfacefabric.client.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public abstract class GarageScreen extends Screen {
    private static final int MIN_WIDTH = 640;
    private static final int MIN_HEIGHT = 360;

    protected GarageScreen(Component title) {
        super(title);
    }

    public final int pixelScale() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, virtualScale(window.getWidth(), window.getHeight()));
    }

    protected int virtualScale(int windowWidth, int windowHeight) {
        return Math.min(windowWidth / MIN_WIDTH, windowHeight / MIN_HEIGHT);
    }

    protected final double factor() {
        return Minecraft.getInstance().getWindow().getGuiScale() / pixelScale();
    }

    @Override
    protected void init() {
        Window window = Minecraft.getInstance().getWindow();
        width = window.getWidth() / pixelScale();
        height = window.getHeight() / pixelScale();
        layout();
    }

    protected abstract void layout();

    protected abstract void draw(GuiGraphics gui, int mouseX, int mouseY, float partial);

    @Override
    public final void render(GuiGraphics gui, int realX, int realY, float partial) {
        double factor = factor();
        gui.pose().pushPose();
        gui.pose().scale((float) (1 / factor), (float) (1 / factor), 1);
        draw(gui, (int) (realX * factor), (int) (realY * factor), partial);
        gui.pose().popPose();
    }

    protected final void drawWidgets(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        super.render(gui, mouseX, mouseY, partial);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {}

    public final void scissor(GuiGraphics gui, double x0, double y0, double x1, double y1) {
        double factor = factor();
        gui.enableScissor(Mth.floor(x0 / factor), Mth.floor(y0 / factor), Mth.ceil(x1 / factor), Mth.ceil(y1 / factor));
    }

    @Override
    public final boolean mouseClicked(double realX, double realY, int button) {
        double factor = factor();
        return clicked(realX * factor, realY * factor, button);
    }

    protected boolean clicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseReleased(double realX, double realY, int button) {
        double factor = factor();
        return released(realX * factor, realY * factor, button);
    }

    protected boolean released(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseDragged(double realX, double realY, int button, double dragX, double dragY) {
        double factor = factor();
        return dragged(realX * factor, realY * factor, button, dragX * factor, dragY * factor);
    }

    protected boolean dragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
