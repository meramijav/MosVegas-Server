package studio.merami.sliminterfacefabric.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartGroundDevice;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.ClientConfig;
import studio.merami.sliminterfacefabric.client.VehicleControls;
import studio.merami.sliminterfacefabric.client.sound.RadioStations;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VehicleHud {
    private static final ResourceLocation ICONS = SlimVehicle.id("textures/gui/hud_icons.png");
    private static final int ICON = 9;
    private static final int ICON_COUNT = 12;
    private static final int HEADLIGHT = 0;
    private static final int FUEL = 1;
    private static final int BODY = 2;
    private static final int HANDBRAKE = 3;
    private static final int LOCK = 4;
    private static final int RADIO = 5;
    private static final int WIPERS = 6;
    private static final int ENGINE = 7;
    private static final int SIREN = 8;
    private static final int TURN_LEFT = 9;
    private static final int TURN_RIGHT = 10;
    private static final int KEY = 11;
    private static final int DIAL = DialFace.SIZE;
    private static final int MODULE_WIDTH = 84;
    private static final int MODULE_HEIGHT = 104;
    private static final int GAP = 4;
    private static final int WIDTH = DIAL + GAP + MODULE_WIDTH + GAP + DIAL;
    private static final int HEIGHT = DIAL + 4;
    private static final int PANEL = 0xe0181c21;
    private static final int RIM = 0xffe8ecef;
    private static final int INSET = 0xff262c33;
    private static final int INSET_RIM = 0xff4f5964;
    private static final int TROUGH = 0xff0c0f12;
    private static final int WHITE = 0xfff2f5f7;
    private static final int DIM = 0xff8a949c;
    private static final int UNLIT = 0xff3c444d;
    private static final int NEEDLE = 0xffff5a2a;
    private static final int NEEDLE_SHADOW = 0xff7a2410;
    private static final int HUB = 0xff1b1f24;
    private static final int RED = 0xffe23b3b;
    private static final int DARK_RED = 0xff7a1d1d;
    private static final int AMBER = 0xffffb12a;
    private static final int GREEN = 0xff52d36a;
    private static final int BLUE = 0xff4aa3ff;
    private static final int NEEDLE_LENGTH = 52;
    private static final int NEEDLE_TAIL = 8;
    private static final int SEGMENTS = 7;
    private static final int SEGMENT = 4;
    private static final int ABOVE_HOTBAR = 30;
    private static final int ABOVE_ACTION_BAR = 78;
    private static final int KEY_FACE = 0xff913432;
    private static final int KEY_LIGHT = 0xffb5554c;
    private static final int KEY_DARK = 0xff561a18;
    private static final int KEY_HEIGHT = 11;
    private static final int KEY_GAP = 2;
    private static final int LABEL_GAP = 4;
    private static final int SEGMENT_GAP = 10;
    private static final int HINT_ROW = 14;
    private static final int HINT_ON = 0xfff2f5f7;
    private static final int HINT_OFF = 0xffb4bcc3;
    private static final int[] SPEED_STEPS = {5, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100};
    private static final int READOUT = 91;
    private static final int UNIT = 108;
    private static final int RPM_STEP = 50;
    private static final int DAMAGE_PAD = 6;
    private static float shownSpeed;
    private static float shownRpm;
    private static float shownFuel;
    private static long lastFrame;
    private static EntityVehicleF_Physics lastVehicle;

    private VehicleHud() {}

    private record Hint(List<String> keys, String label, boolean on) {
        Hint(String key, String label, boolean on) {
            this(List.of(key), label, on);
        }
    }

    private record Segment(List<String> keys, String text) {}

    public static void registerReload() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SlimVehicle.id("hud");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                DialFace.clear();
                DamageDiagram.clear();
            }
        });
    }

    public static void render(GuiGraphics gui, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(mc.player);
        if (vehicle == null) {
            if (InteractionOverlay.hit == null) return;
            boolean survival = mc.gameMode != null && mc.gameMode.canHurtPlayer();
            RenderSystem.enableBlend();
            gui.drawManaged(() -> prompt(gui, mc.font, gui.guiWidth() / 2, gui.guiHeight() - (survival ? ABOVE_ACTION_BAR : ABOVE_HOTBAR)));
            RenderSystem.disableBlend();
            return;
        }
        float kmh = Math.abs(vehicle.speed()) * 72;
        float rpm = vehicle.rpm();
        float fuel = vehicle.fuel() / (float) vehicle.definition().fuel.tank;
        long now = Util.getMillis();
        float blend = vehicle == lastVehicle ? 1 - (float) Math.exp(-(now - lastFrame) / 70.0) : 1;
        lastFrame = now;
        lastVehicle = vehicle;
        shownSpeed = Mth.lerp(blend, shownSpeed, kmh);
        shownRpm = Mth.lerp(blend, shownRpm, rpm);
        shownFuel = Mth.lerp(blend, shownFuel, fuel);
        RenderSystem.enableBlend();
        gui.drawManaged(() -> {
            if (vehicle.driver(WrapperPlayer.of(mc.player))) dashboard(gui, mc.font, vehicle);
            else passenger(gui, mc.font);
        });
        RenderSystem.disableBlend();
    }

    private static void prompt(GuiGraphics gui, Font font, int centre, int bottom) {
        var hit = InteractionOverlay.hit;
        if (hit == null || hit.zone().part() instanceof PartGroundDevice) return;
        segments(gui, font, List.of(new Segment(List.of(VehicleControls.useKeyName()), Convert.text(hit.zone().text()).getString())), centre, bottom - KEY_HEIGHT);
    }

    private static void passenger(GuiGraphics gui, Font font) {
        String text = I18n.get("hud.slimvehicle.speed", Math.round(shownSpeed));
        int width = font.width(text) + 11;
        int x = gui.guiWidth() / 2 - width / 2;
        int y = gui.guiHeight() - 62;
        panel(gui, x, y, x + width, y + 15);
        gui.drawString(font, text, x + 6, y + 4, WHITE, true);
        prompt(gui, font, gui.guiWidth() / 2, y - 4);
        if (!ClientConfig.hints) return;
        List<Hint> right = List.of(
                new Hint(VehicleControls.keyName(VehicleAction.WINDOW), I18n.get("hud.slimvehicle.keys.window"), false),
                new Hint(VehicleControls.keyName(VehicleAction.EXIT), I18n.get("hud.slimvehicle.keys.exit"), false));
        hints(gui, font, right, gui.guiWidth() / 2 + 100, gui.guiHeight() - 2 - right.size() * HINT_ROW, false);
    }

    private static List<Hint> driverLeft(EntityVehicleF_Physics vehicle) {
        List<Hint> list = new ArrayList<>();
        list.add(new Hint(VehicleControls.keyName(VehicleAction.ENGINE), I18n.get("hud.slimvehicle.keys.engine"), vehicle.flag(EntityVehicleF_Physics.ENGINE)));
        list.add(new Hint(VehicleControls.keyName(VehicleAction.LIGHTS), I18n.get("hud.slimvehicle.keys.lights"), vehicle.flag(EntityVehicleF_Physics.LIGHTS)));
        list.add(new Hint(List.of(VehicleControls.keyName(VehicleAction.GEAR_DOWN), VehicleControls.keyName(VehicleAction.GEAR_UP)),
                I18n.get(vehicle.definition().transmission.automatic ? "hud.slimvehicle.keys.selector" : "hud.slimvehicle.keys.gears"), false));
        list.add(new Hint(VehicleControls.handbrakeName(), I18n.get("hud.slimvehicle.keys.handbrake"), VehicleControls.handbrakeHeld()));
        list.add(new Hint(VehicleControls.keyName(VehicleAction.HORN), I18n.get("hud.slimvehicle.keys.horn"), VehicleControls.hornHeld()));
        list.add(new Hint(VehicleControls.keyName(VehicleAction.EXIT), I18n.get("hud.slimvehicle.keys.exit"), false));
        return list;
    }

    private static List<Hint> driverRight(EntityVehicleF_Physics vehicle) {
        JSONVehicle definition = vehicle.definition();
        List<Hint> list = new ArrayList<>();
        list.add(new Hint(VehicleControls.keyName(VehicleAction.LOCK), I18n.get("hud.slimvehicle.keys.lock"), vehicle.flag(EntityVehicleF_Physics.LOCKED)));
        list.add(new Hint(VehicleControls.keyName(VehicleAction.WINDOW), I18n.get("hud.slimvehicle.keys.window"), false));
        if (definition.wipers != null) list.add(new Hint(VehicleControls.keyName(VehicleAction.WIPERS), I18n.get("hud.slimvehicle.keys.wipers"), vehicle.flag(EntityVehicleF_Physics.WIPERS)));
        list.add(new Hint(VehicleControls.keyName(VehicleAction.RADIO), I18n.get("hud.slimvehicle.keys.radio"), vehicle.flag(EntityVehicleF_Physics.RADIO)));
        if (vehicle.flag(EntityVehicleF_Physics.RADIO) && RadioStations.any()) {
            list.add(new Hint(List.of(VehicleControls.keyName(VehicleAction.RADIO_PREVIOUS), VehicleControls.keyName(VehicleAction.RADIO_NEXT)), I18n.get("hud.slimvehicle.keys.station"), false));
        }
        if (definition.emergency) list.add(new Hint(VehicleControls.keyName(VehicleAction.SIREN), I18n.get("hud.slimvehicle.keys.siren"), vehicle.flag(EntityVehicleF_Physics.SIREN)));
        list.add(new Hint(VehicleControls.vibrationsName(), I18n.get("hud.slimvehicle.keys.vibrations"), ClientConfig.vibrations));
        return list;
    }

    private static int keyWidth(Font font, String key) {
        return Math.max(KEY_HEIGHT, font.width(key) + 6);
    }

    private static int capsWidth(Font font, List<String> keys) {
        int width = -KEY_GAP;
        for (String key : keys) width += keyWidth(font, key) + KEY_GAP;
        return width;
    }

    private static void caps(GuiGraphics gui, Font font, List<String> keys, int x, int y) {
        for (String key : keys) {
            int width = keyWidth(font, key);
            keycap(gui, font, key, x, y, width);
            x += width + KEY_GAP;
        }
    }

    private static int hints(GuiGraphics gui, Font font, List<Hint> hints, int edge, int top, boolean leftSide) {
        int caps = 0;
        int labels = 0;
        for (Hint hint : hints) {
            caps = Math.max(caps, capsWidth(font, hint.keys));
            labels = Math.max(labels, font.width(hint.label));
        }
        int capsX = leftSide ? edge - caps : edge;
        int y = top;
        for (Hint hint : hints) {
            caps(gui, font, hint.keys, capsX, y);
            int labelX = leftSide ? capsX - LABEL_GAP - font.width(hint.label) : capsX + caps + LABEL_GAP;
            gui.drawString(font, hint.label, labelX, y + 2, hint.on ? HINT_ON : HINT_OFF, true);
            y += HINT_ROW;
        }
        return caps + LABEL_GAP + labels;
    }

    private static void segments(GuiGraphics gui, Font font, List<Segment> segments, int centre, int top) {
        int width = -SEGMENT_GAP;
        for (Segment segment : segments) width += segmentWidth(font, segment) + SEGMENT_GAP;
        int x = centre - width / 2;
        for (Segment segment : segments) {
            if (!segment.keys.isEmpty()) {
                caps(gui, font, segment.keys, x, top);
                x += capsWidth(font, segment.keys) + LABEL_GAP;
            }
            gui.drawString(font, segment.text, x, top + 2, HINT_ON, true);
            x += font.width(segment.text) + SEGMENT_GAP;
        }
    }

    private static int segmentWidth(Font font, Segment segment) {
        return (segment.keys.isEmpty() ? 0 : capsWidth(font, segment.keys) + LABEL_GAP) + font.width(segment.text);
    }

    private static void keycap(GuiGraphics gui, Font font, String key, int x, int y, int width) {
        gui.fill(x, y, x + width, y + KEY_HEIGHT, KEY_DARK);
        gui.fill(x, y, x + width - 1, y + KEY_HEIGHT - 1, KEY_LIGHT);
        gui.fill(x + 1, y + 1, x + width - 1, y + KEY_HEIGHT - 1, KEY_FACE);
        gui.drawString(font, key, x + (width - font.width(key) + 1) / 2, y + 2, HINT_ON, false);
    }

    private static void dashboard(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle) {
        JSONVehicle definition = vehicle.definition();
        float scale = Mth.clamp(Math.min(gui.guiWidth() / 600f, gui.guiHeight() / 330f), .45f, 1f);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(Math.round(gui.guiWidth() / 2f - WIDTH * scale / 2), Math.round(gui.guiHeight() - 3 - HEIGHT * scale), 0);
        pose.scale(scale, scale, 1);
        int top = HEIGHT - DIAL;
        int speedStep = speedStep(definition.render.speedometer);
        int rpmStep = Math.max(1, (int) Math.ceil(Math.ceil(definition.engine.maxRpm / 1000) / (DialFace.MAJOR.length - 1)));
        float redline = (float) (definition.engine.redlineRpm / 1000);
        ResourceLocation speedFace = DialFace.face(speedStep, -1);
        ResourceLocation rpmFace = DialFace.face(rpmStep, redline);
        if (speedFace != null) gui.blit(speedFace, 0, top, 0, 0, DIAL, DIAL, DIAL, DIAL);
        if (rpmFace != null) gui.blit(rpmFace, WIDTH - DIAL, top, 0, 0, DIAL, DIAL, DIAL, DIAL);
        boolean blink = (vehicle.world().gameTime() % 16) < 8;
        speedometer(gui, font, top, speedStep);
        tachometer(gui, font, vehicle, WIDTH - DIAL, top, rpmStep);
        int mx = DIAL + GAP;
        int my = HEIGHT - MODULE_HEIGHT;
        module(gui, font, vehicle, mx, my, blink);
        List<Segment> hint = hint(vehicle);
        int above = -KEY_HEIGHT - 4;
        if (!hint.isEmpty()) {
            segments(gui, font, hint, WIDTH / 2, above);
            above -= KEY_HEIGHT + 6;
        }
        prompt(gui, font, WIDTH / 2, above + KEY_HEIGHT);
        int shift = 0;
        if (ClientConfig.hints) {
            List<Hint> left = driverLeft(vehicle);
            List<Hint> right = driverRight(vehicle);
            shift = hints(gui, font, left, -10, top + (DIAL - left.size() * HINT_ROW) / 2, true) + 10;
            hints(gui, font, right, WIDTH + 10, top + (DIAL - right.size() * HINT_ROW) / 2, false);
        }
        if (DamageDiagram.shown(vehicle)) {
            int width = DamageDiagram.width(definition) + 2 * DAMAGE_PAD;
            int height = DamageDiagram.height(definition) + 2 * DAMAGE_PAD;
            int x = -shift - 10 - width;
            int y = top + (DIAL - height) / 2;
            framed(gui, x, y, x + width, y + height, PANEL, RIM);
            DamageDiagram.draw(gui, vehicle, x + DAMAGE_PAD, y + DAMAGE_PAD, blink);
        }
        icons(gui, vehicle, mx, my, blink);
        pose.popPose();
    }

    private static int speedStep(double speedometer) {
        for (int step : SPEED_STEPS) {
            if (step * (DialFace.MAJOR.length - 1) >= speedometer - 1e-6) return step;
        }
        return SPEED_STEPS[SPEED_STEPS.length - 1];
    }

    private static void speedometer(GuiGraphics gui, Font font, int top, int step) {
        readout(gui, font, Math.min(999, Math.round(shownSpeed)), 3, true, DIAL / 2, top + READOUT);
        centered(gui, font, I18n.get("hud.slimvehicle.kmh"), DIAL / 2, top + UNIT, DIM);
        needle(gui, DIAL / 2, top + DIAL / 2, DialFace.angle(shownSpeed, step));
    }

    private static void tachometer(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, int left, int top, int step) {
        int rpm = Math.min(9999, Math.round(shownRpm / RPM_STEP) * RPM_STEP);
        readout(gui, font, rpm, 4, vehicle.flag(EntityVehicleF_Physics.ENGINE), left + DIAL / 2, top + READOUT);
        centered(gui, font, I18n.get("hud.slimvehicle.rpm"), left + DIAL / 2, top + UNIT, DIM);
        needle(gui, left + DIAL / 2, top + DIAL / 2, DialFace.angle(shownRpm / 1000, step));
    }

    private static void readout(GuiGraphics gui, Font font, int value, int count, boolean lit, int centre, int y) {
        String digits = String.format(Locale.ROOT, "%0" + count + "d", value);
        int lead = 0;
        while (lead < count - 1 && digits.charAt(lead) == '0') lead++;
        int width = font.width(digits) * 2 - 2;
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(centre - width / 2, y, 0);
        pose.scale(2, 2, 1);
        int x = 0;
        for (int i = 0; i < digits.length(); i++) {
            String digit = digits.substring(i, i + 1);
            gui.drawString(font, digit, x, 0, lit && i >= lead ? WHITE : UNLIT, false);
            x += font.width(digit);
        }
        pose.popPose();
    }

    private static void needle(GuiGraphics gui, int cx, int cy, float angle) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        gui.fill(-1, -NEEDLE_LENGTH, 2, NEEDLE_TAIL, NEEDLE_SHADOW);
        gui.fill(-1, -NEEDLE_LENGTH, 1, NEEDLE_TAIL, NEEDLE);
        pose.popPose();
        disc(gui, cx, cy, 5, HUB);
        disc(gui, cx, cy, 2, NEEDLE);
    }

    private static void disc(GuiGraphics gui, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - (dy + .5) * (dy + .5)));
            gui.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static void module(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, int x, int y, boolean blink) {
        JSONVehicle definition = vehicle.definition();
        framed(gui, x, y, x + MODULE_WIDTH, y + MODULE_HEIGHT, PANEL, RIM);
        int gear = vehicle.gear();
        String gearText = gear < 0 ? "R" : gear == 0 ? "N" : !definition.transmission.automatic ? Integer.toString(gear) : definition.gears() == 1 ? "D" : "D" + gear;
        int boxX = x + (MODULE_WIDTH - 44) / 2;
        int boxY = y + 19;
        framed(gui, boxX, boxY, boxX + 44, boxY + 34, INSET, INSET_RIM);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(boxX + 22 - (font.width(gearText) * 3 - 3) / 2, boxY + 7, 0);
        pose.scale(3, 3, 1);
        gui.drawString(font, gearText, 0, 0, gear < 0 ? AMBER : gear == 0 ? GREEN : WHITE, false);
        pose.popPose();
        float fuel = Mth.clamp(shownFuel, 0, 1);
        int fuelColor = fuel < .1f ? (blink ? RED : DARK_RED) : fuel < .25f ? AMBER : GREEN;
        bar(gui, x + 16, y + 62, fuel, fuelColor);
        String energy = definition.engine.electric ? Math.round(vehicle.fuel() / definition.fuel.tank * 100) + "%"
                : I18n.get("hud.slimvehicle.litres", String.format(Locale.ROOT, "%.0f", vehicle.fuel()));
        gui.drawString(font, energy, x + 16 + SEGMENTS * (SEGMENT + 1) + 3, y + 61, DIM, false);
        float health = Mth.clamp(vehicle.health() / DamageModel.FULL, 0, 1);
        bar(gui, x + 16, y + 76, health, health > .6f ? GREEN : health > .3f ? AMBER : RED);
        String condition = vehicle.wrecked() ? "0%" : Math.round(health * 100) + "%";
        gui.drawString(font, condition, x + 16 + SEGMENTS * (SEGMENT + 1) + 3, y + 75, vehicle.wrecked() ? (blink ? RED : DARK_RED) : DIM, false);
    }

    private static void bar(GuiGraphics gui, int x, int y, float level, int color) {
        int lit = level <= 0 ? 0 : Math.max(1, Math.round(level * SEGMENTS));
        for (int i = 0; i < SEGMENTS; i++) {
            int left = x + i * (SEGMENT + 1);
            gui.fill(left, y, left + SEGMENT, y + 5, i < lit ? color : TROUGH);
        }
    }

    private static void icons(GuiGraphics gui, EntityVehicleF_Physics vehicle, int x, int y, boolean blink) {
        JSONVehicle definition = vehicle.definition();
        icon(gui, TURN_LEFT, x + 6, y + 6, vehicle.flag(EntityVehicleF_Physics.TURN_LEFT) && blink ? GREEN : UNLIT);
        icon(gui, TURN_RIGHT, x + MODULE_WIDTH - 6 - ICON, y + 6, vehicle.flag(EntityVehicleF_Physics.TURN_RIGHT) && blink ? GREEN : UNLIT);
        icon(gui, HEADLIGHT, x + (MODULE_WIDTH - ICON) / 2, y + 6, vehicle.flag(EntityVehicleF_Physics.LIGHTS) ? BLUE : UNLIT);
        float fuel = Mth.clamp(shownFuel, 0, 1);
        icon(gui, FUEL, x + 5, y + 60, fuel < .1f ? (blink ? RED : DARK_RED) : DIM);
        icon(gui, BODY, x + 5, y + 74, vehicle.wrecked() ? (blink ? RED : DARK_RED) : DIM);
        List<int[]> lamps = new ArrayList<>();
        lamps.add(new int[]{HANDBRAKE, VehicleControls.handbrakeHeld() ? RED : UNLIT});
        lamps.add(new int[]{LOCK, vehicle.flag(EntityVehicleF_Physics.LOCKED) ? AMBER : UNLIT});
        if (definition.emergency) lamps.add(new int[]{SIREN, vehicle.flag(EntityVehicleF_Physics.SIREN) ? (blink ? RED : BLUE) : UNLIT});
        lamps.add(new int[]{RADIO, vehicle.flag(EntityVehicleF_Physics.RADIO) ? BLUE : UNLIT});
        if (definition.wipers != null) lamps.add(new int[]{WIPERS, vehicle.flag(EntityVehicleF_Physics.WIPERS) ? GREEN : UNLIT});
        float engine = vehicle.engineHealth();
        lamps.add(new int[]{ENGINE, engine < 20 ? (blink ? RED : DARK_RED) : engine < 50 ? AMBER : UNLIT});
        if (!VehicleControls.holdsKey(vehicle)) lamps.add(new int[]{KEY, blink ? AMBER : UNLIT});
        int width = lamps.size() * (ICON + 2) - 2;
        int lx = x + (MODULE_WIDTH - width) / 2;
        for (int[] lamp : lamps) {
            icon(gui, lamp[0], lx, y + 89, lamp[1]);
            lx += ICON + 2;
        }
        gui.setColor(1, 1, 1, 1);
    }

    private static void icon(GuiGraphics gui, int index, int x, int y, int color) {
        gui.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
        gui.blit(ICONS, x, y, index * ICON, 0, ICON, ICON, ICON * ICON_COUNT, ICON);
    }

    private static List<Segment> hint(EntityVehicleF_Physics vehicle) {
        if (vehicle.wrecked()) return text("hud.slimvehicle.hint.wrecked");
        if (vehicle.engineHealth() <= 0) return text("hud.slimvehicle.hint.engine_dead");
        if (vehicle.fuel() <= 0) return text(vehicle.definition().engine.electric ? "hud.slimvehicle.hint.tank_empty.electric" : "hud.slimvehicle.hint.tank_empty");
        if (!vehicle.flag(EntityVehicleF_Physics.ENGINE)) {
            if (!VehicleControls.holdsKey(vehicle)) return text("hud.slimvehicle.hint.no_key");
            return List.of(new Segment(List.of(VehicleControls.keyName(VehicleAction.ENGINE)), I18n.get("hud.slimvehicle.hint.start")));
        }
        if (vehicle.gear() == 0 && Math.abs(vehicle.speed()) < .02) {
            String reverse = VehicleControls.keyName(VehicleAction.GEAR_DOWN);
            if (vehicle.definition().transmission.automatic) {
                return List.of(new Segment(List.of(VehicleControls.forwardName()), I18n.get("hud.slimvehicle.hint.forward")),
                        new Segment(List.of(reverse), I18n.get("hud.slimvehicle.hint.reverse")));
            }
            return List.of(new Segment(List.of(VehicleControls.keyName(VehicleAction.GEAR_UP)), I18n.get("hud.slimvehicle.hint.first")),
                    new Segment(List.of(reverse), I18n.get("hud.slimvehicle.hint.reverse_gear")));
        }
        return List.of();
    }

    private static List<Segment> text(String key) {
        return List.of(new Segment(List.of(), I18n.get(key)));
    }

    private static void centered(GuiGraphics gui, Font font, String text, int x, int y, int color) {
        gui.drawString(font, text, x - (font.width(text) - 1) / 2, y, color, false);
    }

    private static void panel(GuiGraphics gui, int x0, int y0, int x1, int y1) {
        framed(gui, x0, y0, x1, y1, PANEL, RIM);
    }

    private static void framed(GuiGraphics gui, int x0, int y0, int x1, int y1, int fill, int rim) {
        gui.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        gui.fill(x0 + 1, y0, x1 - 1, y0 + 1, rim);
        gui.fill(x0 + 1, y1 - 1, x1 - 1, y1, rim);
        gui.fill(x0, y0 + 1, x0 + 1, y1 - 1, rim);
        gui.fill(x1 - 1, y0 + 1, x1, y1 - 1, rim);
    }
}
