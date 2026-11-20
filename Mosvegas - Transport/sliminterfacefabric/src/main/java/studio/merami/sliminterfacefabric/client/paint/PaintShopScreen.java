package studio.merami.sliminterfacefabric.client.paint;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.client.ui.BrandUi;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Box;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Icon;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Strip;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Type;
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
    private static final int TOPBAR = 58;
    private static final int CARD_Y = 60;
    private static final int LEFT_W = 146;
    private static final int RIGHT_W = 146;
    private static final int STATS_H = 120;
    private static final int STAT_STEP = 23;
    private static final int COLOUR_H = 214;
    private static final int PICKER_W = 128;
    private static final int PICKER_H = 78;
    private static final int HUE_H = 7;
    private static final int DOT = 13;
    private static final int PRESETS_IN_ROW = 7;
    private static final int PRESET_STEP_X = 19;
    private static final int PRESET_STEP_Y = 18;
    private static final int SEG_W = 154;
    private static final int DOCK_H = 50;
    private static final int PAY_W = 118;
    private static final int PAY_H = 30;
    private static final int ITEM_H = 22;
    private static final float ORBIT_SPEED = 7;
    private static final float ZOOM_MS = 220;
    private static final long DRAG_PAUSE = 3000;
    private static final long MESSAGE_TIME = 3500;
    private static final double STUDIO_FOV = 50;
    private static final int WALL_TOP = 0xFF151011;
    private static final int WALL_BOTTOM = 0xFF1D1516;
    private static final int FLOOR_TOP = 0xFF2A2122;
    private static final int FLOOR_BOTTOM = 0xFF171213;
    private static final float HORIZON = .525f;
    private static final float NEON = .333f;
    private static final int NEON_GLOW = 24;
    private static final int FLOOR_LINE_STEP = 120;
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
    private String hexText = "";
    private boolean hexFocused;
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
    private Area colourCard = new Area(0, 0, 0, 0);
    private Area picker = new Area(0, 0, 0, 0);
    private Area hueBar = new Area(0, 0, 0, 0);
    private Area seg = new Area(0, 0, 0, 0);
    private Area exitArea = new Area(0, 0, 0, 0);
    private Area hexWell = new Area(0, 0, 0, 0);
    private Area dock = new Area(0, 0, 0, 0);
    private Area payArea = new Area(0, 0, 0, 0);
    private final List<Area> itemAreas = new ArrayList<>();

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
        syncHex();
    }

    @Override
    protected void layout() {
        colourCard = new Area(width - SIDE - RIGHT_W, CARD_Y, RIGHT_W, COLOUR_H);
        picker = new Area(colourCard.x() + 9, colourCard.y() + 24, PICKER_W, PICKER_H);
        hueBar = new Area(picker.x(), picker.bottom() + 8, PICKER_W, HUE_H);
        hexWell = new Area(colourCard.x() + 9, colourCard.y() + 184, 80, 18);
        seg = new Area((width - SEG_W) / 2, 11, SEG_W, 22);
        exitArea = new Area(width - SIDE - 22, 11, 22, 22);
        layoutDock();
    }

    private void layoutDock() {
        List<CartLine> lines = lines();
        itemAreas.clear();
        int items = 0;
        for (CartLine line : lines) {
            int w = itemWidth(line);
            itemAreas.add(new Area(0, 0, w, ITEM_H));
            items += w + 5;
        }
        items = lines.isEmpty() ? Math.round(BrandUi.width(this, I18n.get("gui.slimvehicle.paint.cart_empty"), Type.SMALL)) : items - 5;
        items = Math.max(items, Math.round(BrandUi.width(this, I18n.get("gui.slimvehicle.paint.cart"), Type.CAP)));
        int total = Math.round(Math.max(BrandUi.width(this, totalText(), Type.VALUE), BrandUi.width(this, I18n.get("gui.slimvehicle.paint.total"), Type.CAP)));
        int w = 12 + items + 12 + 1 + 12 + total + 12 + PAY_W + 10;
        int x = (width - w) / 2;
        int y = height - SIDE - DOCK_H;
        dock = new Area(x, y, w, DOCK_H);
        int at = x + 12;
        for (int i = 0; i < itemAreas.size(); i++) {
            Area area = itemAreas.get(i);
            itemAreas.set(i, new Area(at, y + 20, area.w(), ITEM_H));
            at += area.w() + 5;
        }
        payArea = new Area(dock.right() - 10 - PAY_W, y + (DOCK_H - PAY_H) / 2 + 1, PAY_W, PAY_H);
    }

    private int itemWidth(CartLine line) {
        return Math.round(6 + 9 + 5 + BrandUi.width(this, line.label, Type.SMALL) + 5 + BrandUi.width(this, priceText(line.price), Type.SMALL) + 6 + 7 + 6);
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
        return screen.view(partial);
    }

    private double[] view(float partial) {
        JSONVehicle definition = vehicle.definition();
        Vec3 centre = entity.getPosition(partial).add(0, definition.size.height * .45, 0);
        double distance = (Math.max(definition.size.halfLength, definition.size.halfWidth) * 1.85 + 2.6) * zoom;
        double rise = (definition.size.height * .55 + .6) * (2.2 - 1.2 * zoom);
        float yaw = orbit;
        double x = centre.x + Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
        double z = centre.z - Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
        float pitch = (float) (Math.atan2(rise, distance) * Mth.RAD_TO_DEG);
        return new double[] {x, centre.y + rise, z, yaw, pitch};
    }

    @Override
    protected void draw(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        layoutDock();
        studio(gui, partial);
        gui.fillGradient(0, 0, width, TOPBAR, 0xF5100909, 0x00100909);
        gui.fillGradient(0, height - 90, width, height, 0x00100909, 0xB0100909);
        header(gui, mouseX, mouseY);
        stats(gui);
        hints(gui);
        colour(gui, mouseX, mouseY);
        cart(gui, mouseX, mouseY);
        status(gui);
    }

    private void studio(GuiGraphics gui, float partial) {
        int horizon = Math.round(height * HORIZON);
        gui.fillGradient(0, 0, width, horizon, WALL_TOP, WALL_BOTTOM);
        gui.fillGradient(0, horizon, width, height, FLOOR_TOP, FLOOR_BOTTOM);
        for (int x = FLOOR_LINE_STEP - 1; x < width; x += FLOOR_LINE_STEP) gui.fill(x, horizon, x + 1, height, 0x06FFFFFF);
        int neon = Math.round(height * NEON);
        gui.fillGradient(0, neon - NEON_GLOW, width, neon, 0x00D4474B, 0x38D4474B);
        gui.fillGradient(0, neon + 2, width, neon + 2 + NEON_GLOW, 0x38D4474B, 0x00D4474B);
        BrandUi.strip(gui, this, Strip.NEON, 0, neon, width, 2, false, BrandUi.WHITE);
        double[] view = view(partial);
        Vec3 camera = new Vec3(view[0], view[1], view[2]);
        Vec3 at = entity.getPosition(partial);
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees((float) view[4]));
        pose.mulPose(Axis.YP.rotationDegrees((float) view[3] + 180));
        Matrix4f projection = Minecraft.getInstance().gameRenderer.getProjectionMatrix(STUDIO_FOV);
        floorShadow(gui, projection, pose.last().pose(), at.subtract(camera));
        gui.flush();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Lighting.setupLevel(new Matrix4f(pose.last().pose()));
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, at.x - camera.x, at.y - camera.y, at.z - camera.z, entity.getYRot(), partial, pose, buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Lighting.setupFor3DItems();
    }

    private void floorShadow(GuiGraphics gui, Matrix4f projection, Matrix4f view, Vec3 at) {
        JSONVehicle definition = vehicle.definition();
        double yaw = Math.toRadians(entity.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        float x0 = Float.MAX_VALUE;
        float y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE;
        float y1 = -Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            double dx = (corner % 2 == 0 ? -1 : 1) * definition.size.halfWidth * 1.15;
            double dz = (corner < 2 ? -1 : 1) * definition.size.halfLength * 1.05;
            Vector4f point = new Vector4f((float) (at.x + dx * cos - dz * sin), (float) at.y, (float) (at.z + dx * sin + dz * cos), 1);
            view.transform(point);
            projection.transform(point);
            if (point.w <= 0) return;
            float sx = (point.x / point.w * .5f + .5f) * width;
            float sy = (.5f - point.y / point.w * .5f) * height;
            x0 = Math.min(x0, sx);
            y0 = Math.min(y0, sy);
            x1 = Math.max(x1, sx);
            y1 = Math.max(y1, sy);
        }
        float grow = (x1 - x0) * .08f;
        BrandUi.icon(gui, this, Icon.FLOOR_SHADOW, x0 - grow, y0 - grow, x1 - x0 + 2 * grow, y1 - y0 + 2 * grow, BrandUi.WHITE);
    }

    private void header(GuiGraphics gui, int mouseX, int mouseY) {
        BrandUi.logo(gui, this, 10, 5, 34);
        BrandUi.text(gui, this, I18n.get("gui.slimvehicle.paint.title").toUpperCase(Locale.ROOT), Type.TITLE, 49, 10, BrandUi.WHITE);
        String name = Convert.text(vehicle.definition().displayName()).getString().toUpperCase(Locale.ROOT);
        float after = BrandUi.text(gui, this, name, Type.SMALL, 49, 28, BrandUi.TEXT);
        BrandUi.text(gui, this, " · " + I18n.get("gui.slimvehicle.paint.station"), Type.SMALL, after, 28, BrandUi.MUTED);
        toggle(gui, mouseX, mouseY);
        BrandUi.close(gui, this, exitArea, exitArea.inside(mouseX, mouseY));
        String money = Double.isNaN(balance) ? "—" : balanceText.isEmpty() ? GarageUi.number(Math.round(balance)) + "$" : balanceText;
        BrandUi.balance(gui, this, I18n.get("gui.slimvehicle.paint.balance"), money, exitArea.x() - 10, 11, BrandUi.GOLD_LIGHT);
        BrandUi.divider(gui, this, SIDE, width - SIDE, 44, .7f);
    }

    private Area toggleArea(int index) {
        int half = (seg.w() - 4) / 2;
        return new Area(seg.x() + 2 + index * half, seg.y() + 2, half, 18);
    }

    private void toggle(GuiGraphics gui, int mouseX, int mouseY) {
        BrandUi.box(gui, this, Box.SEG, seg);
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == interiorMode;
            boolean off = i == 1 && !hasInterior;
            Area side = toggleArea(i);
            if (on) BrandUi.box(gui, this, Box.SEG_ON, side);
            int ink = on ? BrandUi.WHITE : off ? BrandUi.MUTED : side.inside(mouseX, mouseY) ? BrandUi.WHITE : BrandUi.TEXT;
            String text = I18n.get(i == 0 ? "gui.slimvehicle.paint.mode_body" : "gui.slimvehicle.paint.mode_interior");
            BrandUi.centred(gui, this, text, Type.BUTTON, side.x() + side.w() / 2f, side.y() + side.h() / 2f, ink);
        }
    }

    private void stats(GuiGraphics gui) {
        JSONVehicle definition = vehicle.definition();
        double hp = Double.isNaN(definition.engine.powerKw) ? 0 : definition.engine.powerKw * 1.341;
        double perTonne = hp / Math.max(.3, definition.mass / 1000);
        Area area = new Area(SIDE, CARD_Y, LEFT_W, STATS_H);
        BrandUi.box(gui, this, Box.GLASS, area);
        BrandUi.header(gui, this, I18n.get("gui.slimvehicle.paint.stats"), area.x() + 10, area.y() + 10);
        int row = area.y() + 28;
        row = stat(gui, row, "gui.slimvehicle.paint.power", Math.round(hp), hp / 800, "gui.slimvehicle.paint.unit_power");
        row = stat(gui, row, "gui.slimvehicle.paint.grip", Math.round(definition.physics.grip * 1000), definition.physics.grip / .5, null);
        row = stat(gui, row, "gui.slimvehicle.paint.acceleration", Math.round(perTonne), perTonne / 400, null);
        stat(gui, row, "gui.slimvehicle.paint.top_speed", Math.round(definition.physics.maxSpeedKmh), definition.physics.maxSpeedKmh / 400, "gui.slimvehicle.paint.unit_speed");
    }

    private int stat(GuiGraphics gui, int y, String key, long amount, double fraction, String unit) {
        int x = SIDE + 10;
        int barWidth = LEFT_W - 20;
        BrandUi.text(gui, this, I18n.get(key), Type.CAP, x, y, BrandUi.TEXT);
        String shown = amount <= 0 ? "—" : String.valueOf(amount);
        float right = x + barWidth;
        if (unit != null && amount > 0) {
            String text = I18n.get(unit);
            BrandUi.right(gui, this, text, Type.SMALL, right, y + 1, BrandUi.MUTED);
            right -= BrandUi.width(this, text, Type.SMALL) + 3;
        }
        BrandUi.right(gui, this, shown, Type.LABEL, right, y - 1, BrandUi.WHITE);
        gui.fill(x, y + 9, x + barWidth, y + 12, 0x12FFFFFF);
        int filled = (int) Math.round(barWidth * Mth.clamp(fraction, 0, 1));
        BrandUi.strip(gui, this, Strip.BAR, x, y + 9, filled, 3, false, BrandUi.WHITE);
        return y + STAT_STEP;
    }

    private void hints(GuiGraphics gui) {
        String[][] hints = {{I18n.get("gui.slimvehicle.paint.key_drag"), I18n.get("gui.slimvehicle.paint.rotate")}, {"Enter", I18n.get("gui.slimvehicle.paint.pay_short")},
                {"Esc", I18n.get("gui.slimvehicle.paint.back")}};
        int y = CARD_Y + STATS_H + 12;
        for (String[] hint : hints) {
            int w = BrandUi.keycap(gui, this, hint[0], SIDE + 2, y, false);
            BrandUi.text(gui, this, hint[1], Type.SMALL, SIDE + 2 + w + 6, y + 3.5f, BrandUi.TEXT);
            y += 15;
        }
    }

    private void colour(GuiGraphics gui, int mouseX, int mouseY) {
        BrandUi.box(gui, this, Box.GLASS, colourCard);
        BrandUi.header(gui, this, I18n.get(interiorMode ? "gui.slimvehicle.paint.colour_interior" : "gui.slimvehicle.paint.colour_body"), colourCard.x() + 10, colourCard.y() + 10);
        drawPicker(gui);
        BrandUi.text(gui, this, I18n.get("gui.slimvehicle.paint.palette"), Type.CAP, colourCard.x() + 10, presetY() - 10, BrandUi.MUTED);
        presets(gui, mouseX, mouseY);
        BrandUi.box(gui, this, hexFocused ? Box.INPUT_FOCUS : Box.INPUT, hexWell);
        float cy = hexWell.y() + hexWell.h() / 2f;
        float after = BrandUi.text(gui, this, "HEX", Type.CAP, hexWell.x() + 7, cy - BrandUi.capHeight(Type.CAP) / 2, BrandUi.MUTED);
        float end = BrandUi.text(gui, this, "#" + hexText, Type.LABEL, after + 6, cy - BrandUi.capHeight(Type.LABEL) / 2, BrandUi.WHITE);
        if (hexFocused && Util.getMillis() / 500 % 2 == 0) gui.fill(Math.round(end) + 1, Math.round(cy) - 4, Math.round(end) + 2, Math.round(cy) + 4, BrandUi.GOLD_LIGHT);
        int right = colourCard.right() - 9;
        int y = hexWell.y() + 1;
        int now = shownColor(currentTarget());
        int was = actualColor(currentTarget());
        tile(gui, right - 16, y, now);
        BrandUi.centred(gui, this, "→", Type.SMALL, right - 16 - 6, y + 8, BrandUi.MUTED);
        tile(gui, right - 16 - 12 - 16, y, was);
        if (new Area(right - 44, y, 44, 16).inside(mouseX, mouseY)) gui.renderTooltip(font, Component.translatable("gui.slimvehicle.paint.before_after"), mouseX, mouseY);
    }

    private void tile(GuiGraphics gui, int x, int y, int rgb) {
        BrandUi.icon(gui, this, Icon.TILE, x, y, 0xFF000000 | rgb);
        BrandUi.icon(gui, this, Icon.TILE_RIM, x, y, BrandUi.WHITE);
    }

    private void drawPicker(GuiGraphics gui) {
        int top = hsv(hue, 1, 1);
        for (int i = 0; i < picker.w(); i++) {
            float s = i / (float) (picker.w() - 1);
            gui.fillGradient(picker.x() + i, picker.y(), picker.x() + i + 1, picker.bottom(), 0xff000000 | lerp(0xffffff, top, s), 0xff000000);
        }
        rim(gui, picker);
        int cx = picker.x() + Math.round(saturation * (picker.w() - 1));
        int cy = picker.y() + Math.round((1 - value) * (picker.h() - 1));
        knob(gui, cx, cy, hsv(hue, saturation, value));
        for (int i = 0; i < 6; i++) {
            int x0 = hueBar.x() + i * hueBar.w() / 6;
            int x1 = hueBar.x() + (i + 1) * hueBar.w() / 6;
            for (int x = x0; x < x1; x++) {
                float h = (i + (x - x0) / (float) (x1 - x0)) / 6f;
                gui.fill(x, hueBar.y(), x + 1, hueBar.bottom(), 0xff000000 | hsv(h, 1, 1));
            }
        }
        rim(gui, hueBar);
        knob(gui, hueBar.x() + Math.round(hue * (hueBar.w() - 1)), hueBar.y() + hueBar.h() / 2, hsv(hue, 1, 1));
    }

    private static void rim(GuiGraphics gui, Area area) {
        int rim = 0x24FFFFFF;
        gui.fill(area.x(), area.y(), area.right(), area.y() + 1, rim);
        gui.fill(area.x(), area.bottom() - 1, area.right(), area.bottom(), rim);
        gui.fill(area.x(), area.y() + 1, area.x() + 1, area.bottom() - 1, rim);
        gui.fill(area.right() - 1, area.y() + 1, area.right(), area.bottom() - 1, rim);
    }

    private void knob(GuiGraphics gui, int cx, int cy, int rgb) {
        BrandUi.icon(gui, this, Icon.KNOB_FILL, cx - 6, cy - 6, 0xFF000000 | rgb);
        BrandUi.icon(gui, this, Icon.KNOB, cx - 6, cy - 6, BrandUi.WHITE);
    }

    private int presetY() {
        return hueBar.bottom() + 19;
    }

    private Area presetArea(int index) {
        return new Area(colourCard.x() + 10 + index % PRESETS_IN_ROW * PRESET_STEP_X, presetY() + index / PRESETS_IN_ROW * PRESET_STEP_Y, DOT, DOT);
    }

    private void presets(GuiGraphics gui, int mouseX, int mouseY) {
        int current = shownColor(currentTarget());
        for (int i = 0; i < PRESETS.length; i++) {
            Area area = presetArea(i);
            BrandUi.icon(gui, this, Icon.DOT, area.x(), area.y(), 0xFF000000 | PRESETS[i]);
            if (PRESETS[i] == current) BrandUi.icon(gui, this, Icon.DOT_RING, area.x() - 3, area.y() - 3, BrandUi.WHITE);
            else if (area.inside(mouseX, mouseY)) BrandUi.icon(gui, this, Icon.DOT_RING, area.x() - 3, area.y() - 3, 0x80FFFFFF);
        }
    }

    private void cart(GuiGraphics gui, int mouseX, int mouseY) {
        List<CartLine> lines = lines();
        BrandUi.box(gui, this, Box.GLASS, dock);
        BrandUi.text(gui, this, I18n.get("gui.slimvehicle.paint.cart"), Type.CAP, dock.x() + 12, dock.y() + 10, BrandUi.MUTED);
        if (lines.isEmpty()) BrandUi.text(gui, this, I18n.get("gui.slimvehicle.paint.cart_empty"), Type.SMALL, dock.x() + 12, dock.y() + 28, BrandUi.TEXT);
        int end = dock.x() + 12;
        for (int i = 0; i < lines.size() && i < itemAreas.size(); i++) {
            CartLine line = lines.get(i);
            Area area = itemAreas.get(i);
            boolean hover = !waiting && area.inside(mouseX, mouseY);
            BrandUi.box(gui, this, hover ? Box.CARD_HOVER : Box.CARD, area);
            float cy = area.y() + area.h() / 2f;
            BrandUi.icon(gui, this, Icon.DOT, area.x() + 6, cy - 4.5f, 9, 9, 0xFF000000 | line.color);
            float x = BrandUi.text(gui, this, line.label, Type.SMALL, area.x() + 20, cy - 2.2f, BrandUi.WHITE);
            BrandUi.text(gui, this, priceText(line.price), Type.SMALL, x + 5, cy - 2.2f, BrandUi.GOLD_LIGHT);
            BrandUi.icon(gui, this, Icon.CROSS, area.right() - 13, cy - 3.5f, 7, 7, hover ? BrandUi.RED_SOFT : BrandUi.MUTED);
            end = area.right();
        }
        if (lines.isEmpty()) end = dock.x() + 12 + Math.round(BrandUi.width(this, I18n.get("gui.slimvehicle.paint.cart_empty"), Type.SMALL));
        end = Math.max(end, dock.x() + 12 + Math.round(BrandUi.width(this, I18n.get("gui.slimvehicle.paint.cart"), Type.CAP)));
        int split = end + 12;
        gui.fill(split, dock.y() + 9, split + 1, dock.bottom() - 9, BrandUi.LINE);
        int totalRight = payArea.x() - 12;
        BrandUi.right(gui, this, I18n.get("gui.slimvehicle.paint.total"), Type.CAP, totalRight, dock.y() + 12, BrandUi.MUTED);
        BrandUi.right(gui, this, totalText(), Type.VALUE, totalRight, dock.y() + 24, BrandUi.GOLD_LIGHT);
        boolean enabled = !order.isEmpty() && !waiting;
        String text = I18n.get(waiting ? "gui.slimvehicle.paint.paying" : "gui.slimvehicle.paint.pay");
        BrandUi.primary(gui, this, payArea, text, waiting ? null : "Enter", false, enabled, enabled && payArea.inside(mouseX, mouseY));
    }

    private String totalText() {
        return free ? I18n.get("gui.slimvehicle.paint.free") : GarageUi.number(order.price(prices)) + "$";
    }

    private String priceText(long price) {
        return free ? I18n.get("gui.slimvehicle.paint.free") : GarageUi.number(price) + "$";
    }

    private void status(GuiGraphics gui) {
        if (message == null) return;
        long age = Util.getMillis() - messageAt;
        if (age > MESSAGE_TIME) {
            message = null;
            return;
        }
        float alpha = Mth.clamp((MESSAGE_TIME - age) / 600f, 0, 1);
        if (alpha < .05f) return;
        float textWidth = BrandUi.width(this, message, Type.BODY);
        int w = Math.round(textWidth) + 32;
        Area area = new Area((width - w) / 2, dock.y() - 34, w, 24);
        BrandUi.box(gui, this, Box.GLASS, area.x(), area.y(), area.w(), area.h(), alpha);
        int accent = messageOk ? BrandUi.GREEN : BrandUi.RED_SOFT;
        BrandUi.icon(gui, this, Icon.BULLET, area.x() + 10, area.y() + 9, BrandUi.alpha(accent, alpha));
        BrandUi.text(gui, this, message, Type.BODY, area.x() + 22, area.y() + 9, BrandUi.alpha(accent, alpha));
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (super.clicked(mouseX, mouseY, button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (hexWell.inside(mx, my)) {
            hexFocused = true;
            return true;
        }
        if (hexFocused) {
            hexFocused = false;
            syncHex();
        }
        if (picker.inside(mx, my)) {
            drag = Drag.PICKER;
            dragPicker(mx, my);
            return true;
        }
        if (new Area(hueBar.x(), hueBar.y() - 3, hueBar.w(), hueBar.h() + 6).inside(mx, my)) {
            drag = Drag.HUE;
            dragHue(mx);
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
        if (!colourCard.inside(mx, my) && !dock.inside(mx, my) && !seg.inside(mx, my)) {
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
            case HUE -> dragHue((int) mouseX);
            default -> {
                return super.dragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (hexFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !hexText.isEmpty()) hexText = hexText.substring(0, hexText.length() - 1);
            if (enter || key == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
                syncHex();
            }
            return true;
        }
        if (enter) {
            pay();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!hexFocused) return super.charTyped(character, modifiers);
        if (Character.digit(character, 16) < 0) return true;
        if (hexText.length() >= 6) hexText = "";
        hexText += Character.toUpperCase(character);
        if (hexText.length() == 6) typedHex(hexText);
        return true;
    }

    private void dragPicker(int mouseX, int mouseY) {
        saturation = Mth.clamp((mouseX - picker.x()) / (float) (picker.w() - 1), 0, 1);
        value = 1 - Mth.clamp((mouseY - picker.y()) / (float) (picker.h() - 1), 0, 1);
        applyPicker();
    }

    private void dragHue(int mouseX) {
        hue = Mth.clamp((mouseX - hueBar.x()) / (float) (hueBar.w() - 1), 0, .999f);
        applyPicker();
    }

    private void applyPicker() {
        setColour(hsv(hue, saturation, value));
        syncHex();
    }

    private void typedHex(String digits) {
        int rgb = Integer.parseInt(digits, 16);
        loadColour(rgb);
        setColour(rgb);
    }

    private void syncHex() {
        if (hexFocused) return;
        hexText = String.format(Locale.ROOT, "%06X", shownColor(currentTarget()));
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
        for (int i = 0; i < lines.size() && i < itemAreas.size(); i++) {
            if (itemAreas.get(i).inside(mouseX, mouseY)) {
                remove(lines.get(i).part);
                return true;
            }
        }
        if (payArea.inside(mouseX, mouseY)) {
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
