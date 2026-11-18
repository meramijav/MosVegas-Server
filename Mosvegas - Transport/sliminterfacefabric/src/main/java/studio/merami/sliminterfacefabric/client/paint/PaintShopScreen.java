package studio.merami.sliminterfacefabric.client.paint;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.client.ui.GarageScreen;
import studio.merami.sliminterfacefabric.client.ui.GarageUi;
import studio.merami.sliminterfacefabric.client.ui.GarageUi.Area;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.PacketPaintResult;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PaintShopScreen extends GarageScreen {
    private static final String ALL = "";
    private static final int[] PRESETS = {0x0d0e10, 0xf4f5f2, 0x9aa0a8, 0x5b6068, 0xc4161c, 0x7a0f14, 0xff7a1a, 0xf3e03b, 0x2f9e44, 0x0b5d3b, 0x1c3f94, 0x00a3d9, 0x5b2a86, 0xe0509a};
    private static final int SIDE = 12;
    private static final int TOP = 44;
    private static final int LEFT_WIDTH = 150;
    private static final int RIGHT_WIDTH = 190;
    private static final int TITLE = 20;
    private static final int ROW = 13;
    private static final int STAT = 24;
    private static final int STAT_SEGMENTS = 27;
    private static final int TOGGLE = 18;
    private static final int HUE_WIDTH = 14;
    private static final int PRESETS_IN_ROW = 7;
    private static final int PRESET_W = 21;
    private static final int PRESET_H = 12;
    private static final int PRESET_STEP_X = 24;
    private static final int PRESET_STEP_Y = 15;
    private static final int PAY_H = 20;
    private static final int ROW_HOVER = 0x1effffff;
    private static final float ORBIT_SPEED = 7;
    private static final float ZOOM_MS = 220;
    private static final long DRAG_PAUSE = 3000;
    private static final long MESSAGE_TIME = 3500;
    private static PaintShopScreen active;

    private final BuilderVehicle entity;
    private final EntityVehicleF_Physics vehicle;
    private final PaintShop.Prices prices;
    private final boolean free;
    private final boolean hasInterior;
    private double balance;
    private String balanceText;
    private PaintShop.Order order = PaintShop.Order.EMPTY;
    private boolean interiorMode;
    private Set<String> broken = Set.of();
    private float hue;
    private float saturation;
    private float value;
    private Drag drag = Drag.NONE;
    private EditBox hex;
    private boolean syncingHex;
    private boolean waiting;
    private String message;
    private boolean messageOk;
    private long messageAt;
    private float orbit;
    private float zoom = 1;
    private long lastFrame;
    private long draggedAt;
    private CameraType camera;
    private boolean hideGui;
    private int rightX;
    private int panelBottom;
    private int pickerWidth;
    private int pickerHeight;
    private int pickerX;
    private int pickerY;
    private int hueX;
    private int presetY;
    private int hexY;
    private Area exitArea = new Area(0, 0, 0, 0);
    private Area hexWell = new Area(0, 0, 0, 0);

    private enum Drag { NONE, ORBIT, PICKER, HUE }

    private record CartLine(String part, String label, int color, long price) {}

    public PaintShopScreen(BuilderVehicle entity, PaintShop.Prices prices, double balance, String balanceText, boolean free) {
        super(Component.translatable("gui.slimvehicle.paint.title"));
        this.entity = entity;
        this.vehicle = entity.vehicle();
        this.prices = prices;
        this.balance = balance;
        this.balanceText = balanceText;
        this.free = free;
        hasInterior = !vehicle.definition().interiorPaintBones.isEmpty();
        orbit = entity.getYRot() + 140;
        loadColour(shownColor(ALL));
    }

    public int vehicleId() {
        return vehicle.id();
    }

    @Override
    protected int virtualScale(int windowWidth, int windowHeight) {
        return Math.max(Math.min(2, Math.min(windowWidth / 640, windowHeight / 360)), Math.min(windowWidth / 960, windowHeight / 540));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (active != this) {
            camera = mc.options.getCameraType();
            hideGui = mc.options.hideGui;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.options.hideGui = true;
            active = this;
            lastFrame = Util.getMillis();
        }
        super.init();
        hex = new EditBox(font, hexWell.x() + 4, hexWell.y() + 3, hexWell.w() - 8, 10, Component.translatable("gui.slimvehicle.paint.hex"));
        hex.setBordered(false);
        hex.setMaxLength(7);
        hex.setTextColor(GarageUi.WHITE);
        hex.setResponder(this::typedHex);
        addRenderableWidget(hex);
        syncHex();
    }

    @Override
    protected void layout() {
        rightX = width - SIDE - RIGHT_WIDTH;
        pickerX = rightX + 10;
        pickerY = TOP + TITLE + TOGGLE + 8 + 11;
        pickerWidth = RIGHT_WIDTH - 20 - HUE_WIDTH - 6;
        panelBottom = orderTop() + orderHeight(0);
        int presetRows = (PRESETS.length + PRESETS_IN_ROW - 1) / PRESETS_IN_ROW;
        int presetsHeight = presetRows * PRESET_STEP_Y - PRESET_STEP_Y + PRESET_H;
        pickerHeight = panelBottom - 8 - 14 - 6 - presetsHeight - 6 - pickerY;
        hueX = pickerX + pickerWidth + 6;
        presetY = pickerY + pickerHeight + 6;
        hexY = presetY + presetsHeight + 6;
        hexWell = new Area(pickerX + 26, hexY, 62, 14);
        Component exit = Component.translatable("gui.slimvehicle.paint.exit").withStyle(ChatFormatting.BOLD);
        int exitWidth = font.width(exit) + 2 * font.width("Esc") + 32;
        exitArea = new Area(width - SIDE - exitWidth, 8, exitWidth, 22);
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        vehicle.paintPreview = null;
        if (active == this) {
            active = null;
            mc.options.setCameraType(camera);
            mc.options.hideGui = hideGui;
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (entity.isRemoved() || mc.player == null || BuilderVehicle.ridden(mc.player) != vehicle) onClose();
        broken = PaintShop.brokenParts(vehicle);
    }

    public static double[] camera(float partial) {
        PaintShopScreen screen = active;
        if (screen == null || Minecraft.getInstance().screen != screen) return null;
        long now = Util.getMillis();
        long elapsed = Math.min(100, now - screen.lastFrame);
        if (screen.drag != Drag.ORBIT && now - screen.draggedAt > DRAG_PAUSE) screen.orbit += ORBIT_SPEED * elapsed / 1000f;
        float target = screen.interiorMode ? .55f : 1;
        screen.zoom += (target - screen.zoom) * (1 - (float) Math.exp(-elapsed / ZOOM_MS));
        screen.lastFrame = now;
        JSONVehicle definition = screen.vehicle.definition();
        Vec3 centre = screen.entity.getPosition(partial).add(0, definition.size.height * .45, 0);
        double distance = (Math.max(definition.size.halfLength, definition.size.halfWidth) * 1.85 + 2.6) * screen.zoom;
        double rise = (definition.size.height * .55 + .6) * (2.2 - 1.2 * screen.zoom);
        float yaw = screen.orbit;
        double x = centre.x + Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
        double z = centre.z - Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
        float pitch = (float) (Math.atan2(rise, distance) * Mth.RAD_TO_DEG);
        return new double[] {x, centre.y + rise, z, yaw, pitch};
    }

    @Override
    protected void draw(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        gui.fill(0, 0, width, 37, GarageUi.PANEL);
        gui.fillGradient(0, height - 44, width, height, 0x00000000, 0x90000000);
        header(gui, mouseX, mouseY);
        stats(gui);
        order(gui, mouseX, mouseY);
        paint(gui, mouseX, mouseY);
        drawWidgets(gui, mouseX, mouseY, partial);
        hints(gui);
        status(gui);
    }

    private void header(GuiGraphics gui, int mouseX, int mouseY) {
        String name = Convert.text(vehicle.definition().displayName()).getString().toUpperCase(Locale.ROOT);
        GarageUi.bigText(gui, font, Component.literal(name).withStyle(ChatFormatting.BOLD), SIDE, 8, 2, GarageUi.WHITE);
        gui.drawString(font, Component.translatable("gui.slimvehicle.paint.station"), SIDE, 27, GarageUi.MUTED, false);
        GarageUi.hazard(gui, this, new Area(0, 37, width, 3), GarageUi.AMBER_DIM, 0);
        GarageUi.button(gui, font, exitArea, Component.translatable("gui.slimvehicle.paint.exit").withStyle(ChatFormatting.BOLD), "Esc", false, true, exitArea.inside(mouseX, mouseY));
        Component label = Component.translatable("gui.slimvehicle.paint.balance");
        String money = Double.isNaN(balance) ? "—" : balanceText.isEmpty() ? GarageUi.number(Math.round(balance)) + "$" : balanceText;
        Component cash = Component.literal(money).withStyle(ChatFormatting.BOLD);
        int right = exitArea.x() - 10;
        gui.drawString(font, label, right - font.width(label), 8, GarageUi.MUTED, false);
        gui.drawString(font, cash, right - font.width(cash), 20, GarageUi.GREEN, false);
    }

    private static int statsBottom() {
        return TOP + TITLE + 4 * STAT + 4;
    }

    private static int orderTop() {
        return statsBottom() + 8;
    }

    private static int orderHeight(int lines) {
        return TITLE + Math.max(1, lines) * ROW + 6 + 12 + PAY_H + 8;
    }

    private void stats(GuiGraphics gui) {
        JSONVehicle definition = vehicle.definition();
        double hp = Double.isNaN(definition.engine.powerKw) ? 0 : definition.engine.powerKw * 1.341;
        double perTonne = hp / Math.max(.3, definition.mass / 1000);
        Area area = new Area(SIDE, TOP, LEFT_WIDTH, statsBottom() - TOP);
        GarageUi.panel(gui, area);
        GarageUi.title(gui, font, "gui.slimvehicle.paint.stats", area.x() + 8, area.y() + 7, area.w() - 16);
        int row = area.y() + TITLE + 2;
        row = stat(gui, row, "gui.slimvehicle.paint.power", Math.round(hp), hp / 800);
        row = stat(gui, row, "gui.slimvehicle.paint.grip", Math.round(definition.physics.grip * 1000), definition.physics.grip / .5);
        row = stat(gui, row, "gui.slimvehicle.paint.acceleration", Math.round(perTonne), perTonne / 400);
        stat(gui, row, "gui.slimvehicle.paint.top_speed", Math.round(definition.physics.maxSpeedKmh), definition.physics.maxSpeedKmh / 400);
    }

    private int stat(GuiGraphics gui, int y, String key, long amount, double fraction) {
        int x = SIDE + 8;
        int barWidth = LEFT_WIDTH - 16;
        gui.drawString(font, Component.translatable(key), x, y, GarageUi.SOFT, false);
        String shown = amount <= 0 ? "—" : String.valueOf(amount);
        Component number = Component.literal(shown).withStyle(ChatFormatting.BOLD);
        gui.drawString(font, number, x + barWidth - font.width(number), y, GarageUi.WHITE, false);
        int filled = (int) Math.round(STAT_SEGMENTS * Mth.clamp(fraction, 0, 1));
        int segment = (barWidth + 1) / STAT_SEGMENTS;
        for (int i = 0; i < STAT_SEGMENTS; i++) {
            gui.fill(x + i * segment, y + 11, x + (i + 1) * segment - 1, y + 16, i < filled ? GarageUi.AMBER : GarageUi.STEEL_HOVER);
        }
        return y + STAT;
    }

    private void order(GuiGraphics gui, int mouseX, int mouseY) {
        List<CartLine> lines = lines();
        int top = orderTop();
        Area area = new Area(SIDE, top, LEFT_WIDTH, orderHeight(lines.size()));
        GarageUi.panel(gui, area);
        GarageUi.title(gui, font, "gui.slimvehicle.paint.cart", area.x() + 8, area.y() + 7, area.w() - 16);
        int row = top + TITLE;
        if (lines.isEmpty()) {
            gui.drawString(font, Component.translatable("gui.slimvehicle.paint.cart_empty"), area.x() + 8, row + 3, GarageUi.MUTED, false);
            row += ROW;
        }
        for (CartLine line : lines) {
            boolean hover = new Area(area.x() + 4, row, area.w() - 8, ROW).inside(mouseX, mouseY);
            if (hover) gui.fill(area.x() + 4, row, area.right() - 4, row + ROW, ROW_HOVER);
            swatch(gui, area.x() + 9, row + 3, 7, 7, line.color);
            gui.drawString(font, font.plainSubstrByWidth(line.label, LEFT_WIDTH - 80), area.x() + 20, row + 3, GarageUi.WHITE, false);
            String price = free ? I18n.get("gui.slimvehicle.paint.free") : GarageUi.number(line.price) + "$";
            gui.drawString(font, price, area.right() - 18 - font.width(price), row + 3, GarageUi.MUTED, false);
            gui.drawString(font, "×", area.right() - 12, row + 3, hover ? GarageUi.RED : GarageUi.FAINT, false);
            row += ROW;
        }
        gui.fill(area.x() + 8, row + 2, area.right() - 8, row + 3, GarageUi.RIM);
        row += 6;
        gui.drawString(font, Component.translatable("gui.slimvehicle.paint.total").withStyle(ChatFormatting.BOLD), area.x() + 8, row + 2, GarageUi.WHITE, false);
        String sum = free ? I18n.get("gui.slimvehicle.paint.free") : GarageUi.number(order.price(prices)) + "$";
        Component amount = Component.literal(sum).withStyle(ChatFormatting.BOLD);
        gui.drawString(font, amount, area.right() - 8 - font.width(amount), row + 2, GarageUi.GREEN, false);
        boolean enabled = !order.isEmpty() && !waiting;
        Area pay = payArea(lines.size());
        Component text = Component.translatable(waiting ? "gui.slimvehicle.paint.paying" : "gui.slimvehicle.paint.pay").withStyle(ChatFormatting.BOLD);
        GarageUi.button(gui, font, pay, text, null, true, enabled, enabled && pay.inside(mouseX, mouseY));
    }

    private static Area payArea(int lines) {
        return new Area(SIDE + 8, orderTop() + TITLE + Math.max(1, lines) * ROW + 6 + 12, LEFT_WIDTH - 16, PAY_H);
    }

    private void paint(GuiGraphics gui, int mouseX, int mouseY) {
        Area area = new Area(rightX, TOP, RIGHT_WIDTH, panelBottom - TOP);
        GarageUi.panel(gui, area);
        GarageUi.title(gui, font, "gui.slimvehicle.paint.tab", area.x() + 8, area.y() + 7, area.w() - 16);
        toggle(gui, mouseX, mouseY);
        gui.drawString(font, Component.translatable("gui.slimvehicle.paint.colour"), pickerX, pickerY - 10, GarageUi.MUTED, false);
        picker(gui);
        presets(gui, mouseX, mouseY);
        gui.drawString(font, "HEX", pickerX, hexY + 3, GarageUi.MUTED, false);
        GarageUi.framed(gui, hexWell, GarageUi.WELL, hex != null && hex.isFocused() ? GarageUi.AMBER : GarageUi.RIM);
        int chipX = hueX + HUE_WIDTH - 62;
        swatch(gui, chipX, hexY, 25, 14, actualColor(currentTarget()));
        gui.drawString(font, "→", chipX + 28, hexY + 3, GarageUi.MUTED, false);
        swatch(gui, chipX + 37, hexY, 25, 14, shownColor(currentTarget()));
        if (new Area(chipX, hexY, 62, 14).inside(mouseX, mouseY)) gui.renderTooltip(font, Component.translatable("gui.slimvehicle.paint.before_after"), mouseX, mouseY);
    }

    private Area toggleArea(int index) {
        int half = (RIGHT_WIDTH - 20) / 2;
        return new Area(rightX + 10 + index * half, TOP + TITLE, half, TOGGLE);
    }

    private void toggle(GuiGraphics gui, int mouseX, int mouseY) {
        Area whole = new Area(rightX + 10, TOP + TITLE, (RIGHT_WIDTH - 20) / 2 * 2, TOGGLE);
        GarageUi.framed(gui, whole, GarageUi.WELL, GarageUi.RIM);
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == interiorMode;
            boolean off = i == 1 && !hasInterior;
            Area side = toggleArea(i);
            if (on) GarageUi.framed(gui, side, GarageUi.AMBER, GarageUi.AMBER);
            else if (!off && side.inside(mouseX, mouseY)) gui.fill(side.x() + 1, side.y() + 1, side.right() - 1, side.bottom() - 1, ROW_HOVER);
            Component text = Component.translatable(i == 0 ? "gui.slimvehicle.paint.mode_body" : "gui.slimvehicle.paint.mode_interior").withStyle(ChatFormatting.BOLD);
            gui.drawString(font, text, side.x() + (side.w() - font.width(text)) / 2, side.y() + 5, on ? GarageUi.ON_AMBER : off ? GarageUi.FAINT : GarageUi.SOFT, false);
        }
    }

    private void picker(GuiGraphics gui) {
        gui.fill(pickerX - 1, pickerY - 1, pickerX + pickerWidth + 1, pickerY + pickerHeight + 1, GarageUi.RIM);
        int top = hsv(hue, 1, 1);
        for (int i = 0; i < pickerWidth; i++) {
            float s = i / (float) (pickerWidth - 1);
            gui.fillGradient(pickerX + i, pickerY, pickerX + i + 1, pickerY + pickerHeight, 0xff000000 | lerp(0xffffff, top, s), 0xff000000);
        }
        int cx = pickerX + Math.round(saturation * (pickerWidth - 1));
        int cy = pickerY + Math.round((1 - value) * (pickerHeight - 1));
        ring(gui, cx, cy, value > .5f && saturation < .5f ? 0xff000000 : GarageUi.WHITE);
        gui.fill(hueX - 1, pickerY - 1, hueX + HUE_WIDTH + 1, pickerY + pickerHeight + 1, GarageUi.RIM);
        for (int i = 0; i < 6; i++) {
            int y0 = pickerY + i * pickerHeight / 6;
            int y1 = pickerY + (i + 1) * pickerHeight / 6;
            gui.fillGradient(hueX, y0, hueX + HUE_WIDTH, y1, 0xff000000 | hsv(i / 6f, 1, 1), 0xff000000 | hsv((i + 1) / 6f, 1, 1));
        }
        int hy = pickerY + Math.round(hue * (pickerHeight - 1));
        gui.fill(hueX - 2, hy - 1, hueX + HUE_WIDTH + 2, hy + 2, 0xff000000);
        gui.fill(hueX - 1, hy, hueX + HUE_WIDTH + 1, hy + 1, GarageUi.WHITE);
    }

    private Area presetArea(int index) {
        return new Area(pickerX + index % PRESETS_IN_ROW * PRESET_STEP_X, presetY + index / PRESETS_IN_ROW * PRESET_STEP_Y, PRESET_W, PRESET_H);
    }

    private void presets(GuiGraphics gui, int mouseX, int mouseY) {
        int current = shownColor(currentTarget());
        for (int i = 0; i < PRESETS.length; i++) {
            Area area = presetArea(i);
            int rim = PRESETS[i] == current ? GarageUi.AMBER : area.inside(mouseX, mouseY) ? GarageUi.RIM_LIGHT : GarageUi.RIM;
            gui.fill(area.x() - 1, area.y() - 1, area.right() + 1, area.bottom() + 1, rim);
            gui.fill(area.x(), area.y(), area.right(), area.bottom(), 0xff000000 | PRESETS[i]);
        }
    }

    private void hints(GuiGraphics gui) {
        List<String[]> hints = List.of(new String[] {I18n.get("gui.slimvehicle.paint.key_drag"), I18n.get("gui.slimvehicle.paint.rotate")},
                new String[] {"Enter", I18n.get("gui.slimvehicle.paint.pay_short")}, new String[] {"Esc", I18n.get("gui.slimvehicle.paint.back")});
        int x = width - SIDE;
        int y = height - 18;
        for (int i = hints.size() - 1; i >= 0; i--) {
            String key = hints.get(i)[0];
            String label = hints.get(i)[1];
            x -= font.width(label);
            gui.drawString(font, label, x, y + 2, GarageUi.SOFT, false);
            x -= Math.max(11, font.width(key) + 6) + 4;
            GarageUi.keycap(gui, font, key, x, y);
            x -= 14;
        }
    }

    private void status(GuiGraphics gui) {
        if (message == null) return;
        long age = Util.getMillis() - messageAt;
        if (age > MESSAGE_TIME) {
            message = null;
            return;
        }
        int alpha = (int) (255 * Mth.clamp((MESSAGE_TIME - age) / 600f, 0, 1));
        if (alpha < 8) return;
        Component text = Component.literal(message).withStyle(ChatFormatting.BOLD);
        int textWidth = font.width(text);
        int x = (width - textWidth) / 2;
        int y = height - 54;
        int accent = alpha << 24 | (messageOk ? GarageUi.GREEN : GarageUi.RED) & 0xffffff;
        gui.fill(x - 10, y - 6, x + textWidth + 10, y + 14, alpha * 15 / 16 << 24 | GarageUi.PANEL & 0xffffff);
        gui.fill(x - 10, y - 6, x - 8, y + 14, accent);
        gui.drawString(font, text, x, y, accent, false);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (super.clicked(mouseX, mouseY, button)) return true;
        if (hex != null && hex.isFocused()) hex.setFocused(false);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (new Area(pickerX, pickerY, pickerWidth, pickerHeight).inside(mx, my)) {
            drag = Drag.PICKER;
            dragPicker(mx, my);
            return true;
        }
        if (new Area(hueX - 2, pickerY, HUE_WIDTH + 4, pickerHeight).inside(mx, my)) {
            drag = Drag.HUE;
            dragHue(my);
            return true;
        }
        if (exitArea.inside(mx, my)) {
            play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.2f, .3f);
            onClose();
            return true;
        }
        if (clickPaint(mx, my) || clickOrder(mx, my)) {
            play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.6f, .25f);
            return true;
        }
        if (!new Area(rightX, TOP, RIGHT_WIDTH, panelBottom - TOP).inside(mx, my)) {
            drag = Drag.ORBIT;
            draggedAt = Util.getMillis();
        }
        return true;
    }

    @Override
    protected boolean released(double mouseX, double mouseY, int button) {
        if (drag != Drag.NONE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (drag == Drag.ORBIT) draggedAt = Util.getMillis();
            drag = Drag.NONE;
            return true;
        }
        return super.released(mouseX, mouseY, button);
    }

    @Override
    protected boolean dragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        switch (drag) {
            case ORBIT -> {
                orbit += (float) dragX * .8f;
                draggedAt = Util.getMillis();
            }
            case PICKER -> dragPicker((int) mouseX, (int) mouseY);
            case HUE -> dragHue((int) mouseY);
            default -> {
                return super.dragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && (hex == null || !hex.isFocused())) {
            pay();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && hex != null) {
            hex.setFocused(false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void dragPicker(int mouseX, int mouseY) {
        saturation = Mth.clamp((mouseX - pickerX) / (float) (pickerWidth - 1), 0, 1);
        value = 1 - Mth.clamp((mouseY - pickerY) / (float) (pickerHeight - 1), 0, 1);
        applyPicker();
    }

    private void dragHue(int mouseY) {
        hue = Mth.clamp((mouseY - pickerY) / (float) (pickerHeight - 1), 0, .999f);
        applyPicker();
    }

    private void applyPicker() {
        setColour(hsv(hue, saturation, value));
        syncHex();
    }

    private void typedHex(String text) {
        if (syncingHex) return;
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() != 6) return;
        try {
            int rgb = Integer.parseInt(digits, 16);
            loadColour(rgb);
            setColour(rgb);
        } catch (NumberFormatException ignored) {
        }
    }

    private void syncHex() {
        if (hex == null) return;
        syncingHex = true;
        hex.setValue(String.format(Locale.ROOT, "#%06X", shownColor(currentTarget())));
        syncingHex = false;
    }

    private boolean clickPaint(int mouseX, int mouseY) {
        if (toggleArea(0).inside(mouseX, mouseY)) {
            setMode(false);
            return true;
        }
        if (toggleArea(1).inside(mouseX, mouseY)) {
            if (hasInterior) setMode(true);
            return true;
        }
        for (int i = 0; i < PRESETS.length; i++) {
            if (presetArea(i).inside(mouseX, mouseY)) {
                loadColour(PRESETS[i]);
                setColour(PRESETS[i]);
                syncHex();
                return true;
            }
        }
        return false;
    }

    private boolean clickOrder(int mouseX, int mouseY) {
        List<CartLine> lines = lines();
        int row = orderTop() + TITLE;
        for (CartLine line : lines) {
            if (new Area(SIDE + 4, row, LEFT_WIDTH - 8, ROW).inside(mouseX, mouseY)) {
                remove(line.part);
                return true;
            }
            row += ROW;
        }
        if (payArea(lines.size()).inside(mouseX, mouseY)) {
            pay();
            return true;
        }
        return false;
    }

    private void setMode(boolean interior) {
        if (interiorMode == interior) return;
        interiorMode = interior;
        loadColour(shownColor(currentTarget()));
        syncHex();
    }

    private String currentTarget() {
        return interiorMode ? PaintShop.INTERIOR : ALL;
    }

    private void setColour(int rgb) {
        String target = currentTarget();
        if (waiting) return;
        if (!interiorMode && unavailable(target)) {
            show(I18n.get("gui.slimvehicle.paint.needs_repair"), false);
            return;
        }
        if (PaintShop.INTERIOR.equals(target)) order = order.withInterior(rgb == vehicle.interiorColor() && order.interior() == PaintShop.NONE ? PaintShop.NONE : rgb);
        else if (ALL.equals(target)) order = order.withBody(rgb);
        else order = order.withPart(target, rgb);
        preview();
    }

    private void loadColour(int rgb) {
        float r = (rgb >> 16 & 255) / 255f;
        float g = (rgb >> 8 & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max <= 0 ? 0 : delta / max;
        if (delta > 0) {
            float h;
            if (max == r) h = (g - b) / delta;
            else if (max == g) h = 2 + (b - r) / delta;
            else h = 4 + (r - g) / delta;
            hue = ((h / 6) % 1 + 1) % 1;
        }
    }

    private void remove(String part) {
        if (waiting) return;
        if (ALL.equals(part)) order = order.withBody(PaintShop.NONE);
        else if (PaintShop.INTERIOR.equals(part)) order = order.withInterior(PaintShop.NONE);
        else order = order.withPart(part, PaintShop.NONE);
        preview();
        loadColour(shownColor(currentTarget()));
        syncHex();
    }

    private void preview() {
        vehicle.paintPreview = order.isEmpty() ? null : order;
    }

    private void pay() {
        if (order.isEmpty() || waiting) return;
        if (!free && !Double.isNaN(balance) && balance < order.price(prices)) {
            show(I18n.get("message.slimvehicle.paint.no_money"), false);
            return;
        }
        waiting = true;
        PaintShopClient.order(vehicle, order);
    }

    void result(PacketPaintResult packet) {
        waiting = false;
        balance = packet.balance();
        balanceText = packet.balanceText();
        show(I18n.get(packet.key()), packet.ok());
        if (packet.ok()) {
            play(SoundEvents.BOTTLE_FILL, .8f, .5f);
            order = PaintShop.Order.EMPTY;
            vehicle.paintPreview = null;
            onClose();
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void show(String text, boolean ok) {
        message = text;
        messageOk = ok;
        messageAt = Util.getMillis();
    }

    private boolean unavailable(String part) {
        if (ALL.equals(part)) return !broken.isEmpty();
        return broken.contains(part);
    }

    private int actualColor(String part) {
        if (ALL.equals(part)) return vehicle.color();
        if (PaintShop.INTERIOR.equals(part)) return vehicle.interiorColor();
        return vehicle.partColors().getOrDefault(part, vehicle.color()) & 0xffffff;
    }

    private int shownColor(String part) {
        if (ALL.equals(part)) return order.body() != PaintShop.NONE ? order.body() : vehicle.color();
        if (PaintShop.INTERIOR.equals(part)) return order.interior() != PaintShop.NONE ? order.interior() : vehicle.interiorColor();
        return vehicle.partColor(part) & 0xffffff;
    }

    private List<CartLine> lines() {
        List<CartLine> lines = new ArrayList<>();
        if (order.body() != PaintShop.NONE) lines.add(new CartLine(ALL, partName(ALL), order.body(), prices.body()));
        order.parts().forEach((bone, rgb) -> lines.add(new CartLine(bone, partName(bone), rgb, prices.part())));
        if (order.interior() != PaintShop.NONE) lines.add(new CartLine(PaintShop.INTERIOR, partName(PaintShop.INTERIOR), order.interior(), prices.interior()));
        return lines;
    }

    private String partName(String part) {
        if (ALL.equals(part)) return I18n.get("gui.slimvehicle.paint.part.all");
        String key = "gui.slimvehicle.paint.part." + part;
        return I18n.exists(key) ? I18n.get(key) : part;
    }

    private static void swatch(GuiGraphics gui, int x, int y, int w, int h, int rgb) {
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, GarageUi.RIM);
        gui.fill(x, y, x + w, y + h, 0xff000000 | rgb);
    }

    private static void ring(GuiGraphics gui, int x, int y, int color) {
        gui.fill(x - 3, y - 3, x + 4, y - 2, color);
        gui.fill(x - 3, y + 3, x + 4, y + 4, color);
        gui.fill(x - 3, y - 2, x - 2, y + 3, color);
        gui.fill(x + 3, y - 2, x + 4, y + 3, color);
    }

    private static int hsv(float h, float s, float v) {
        return Mth.hsvToRgb(((h % 1) + 1) % 1, Mth.clamp(s, 0, 1), Mth.clamp(v, 0, 1)) & 0xffffff;
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, from >> 16 & 255, to >> 16 & 255));
        int g = Math.round(Mth.lerp(t, from >> 8 & 255, to >> 8 & 255));
        int b = Math.round(Mth.lerp(t, from & 255, to & 255));
        return r << 16 | g << 8 | b;
    }
}
