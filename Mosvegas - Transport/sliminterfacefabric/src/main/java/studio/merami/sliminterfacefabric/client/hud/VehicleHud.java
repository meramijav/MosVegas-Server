package studio.merami.sliminterfacefabric.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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
import studio.merami.sliminterfacefabric.client.paint.PaintShopClient;
import studio.merami.sliminterfacefabric.client.sound.RadioStations;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.util.ArrayList;
import java.util.List;

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
    private static final int STRIP_WIDTH = 210;
    private static final int STRIP_HEIGHT = 30;
    private static final int FIT_WIDTH = 330;
    private static final int STRIP_RIM = 0xff59636e;
    private static final int SPEED_X = 17;
    private static final int SPEED_FIELD = 34;
    private static final int GEAR_X = 76;
    private static final int GEAR_WIDTH = 22;
    private static final int RPM_X = 103;
    private static final int RPM_SEGMENTS = 18;
    private static final int RPM_SEGMENT = 2;
    private static final float REV_WARNING = .85f;
    private static final int GAUGE_X = 161;
    private static final int GAUGE_SEGMENTS = 5;
    private static final int GAUGE_SEGMENT = 3;
    private static final int GAUGE_BAR = 4;
    private static final int LAMP_GAP = 2;
    private static final int PANEL = 0xe0181c21;
    private static final int RIM = 0xffe8ecef;
    private static final int INSET = 0xff262c33;
    private static final int INSET_RIM = 0xff4f5964;
    private static final int TROUGH = 0xff0c0f12;
    private static final int WHITE = 0xfff2f5f7;
    private static final int DIM = 0xff8a949c;
    private static final int UNLIT = 0xff3c444d;
    private static final int RED = 0xffe23b3b;
    private static final int DARK_RED = 0xff7a1d1d;
    private static final int AMBER = 0xffffb12a;
    private static final int GREEN = 0xff52d36a;
    private static final int BLUE = 0xff4aa3ff;
    private static final int STACK_GAP = 4;
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
                DamageDiagram.clear();
            }
        });
    }

    public static int freeBottom(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        return gui.guiHeight() - (mc.gameMode != null && mc.gameMode.canHurtPlayer() ? ABOVE_ACTION_BAR : ABOVE_HOTBAR) - STACK_GAP;
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
        list.add(new Hint(VehicleControls.keyName(VehicleAction.HORN), I18n.get(PaintShopClient.parkedVehicle(Minecraft.getInstance()) != null ? "hud.slimvehicle.keys.paint" : "hud.slimvehicle.keys.horn"), VehicleControls.hornHeld()));
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
        float scale = Mth.clamp(gui.guiWidth() / (float) FIT_WIDTH, .6f, 1f);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(Math.round(gui.guiWidth() / 2f - STRIP_WIDTH * scale / 2), Math.round(gui.guiHeight() - 3 - STRIP_HEIGHT * scale), 0);
        pose.scale(scale, scale, 1);
        boolean blink = (vehicle.world().gameTime() % 16) < 8;
        strip(gui, font, vehicle, blink);
        int above = -lamps(gui, vehicle, blink) - KEY_HEIGHT - 4;
        List<Segment> hint = hint(vehicle);
        if (!hint.isEmpty()) {
            segments(gui, font, hint, STRIP_WIDTH / 2, above);
            above -= KEY_HEIGHT + 6;
        }
        prompt(gui, font, STRIP_WIDTH / 2, above + KEY_HEIGHT);
        int shift = 0;
        if (ClientConfig.hints) {
            if (VehicleControls.hintsHeld()) {
                List<Hint> left = driverLeft(vehicle);
                List<Hint> right = driverRight(vehicle);
                shift = hints(gui, font, left, -10, STRIP_HEIGHT - left.size() * HINT_ROW, true) + 10;
                hints(gui, font, right, STRIP_WIDTH + 10, STRIP_HEIGHT - right.size() * HINT_ROW, false);
            } else {
                hints(gui, font, List.of(new Hint(VehicleControls.hintsName(), I18n.get("hud.slimvehicle.keys.hints"), false)), STRIP_WIDTH + 8,
                        (STRIP_HEIGHT - KEY_HEIGHT) / 2, false);
            }
        }
        if (DamageDiagram.shown(vehicle)) {
            int width = DamageDiagram.width(definition) + 2 * DAMAGE_PAD;
            int height = DamageDiagram.height(definition) + 2 * DAMAGE_PAD;
            int x = -shift - 10 - width;
            int y = STRIP_HEIGHT - height;
            framed(gui, x, y, x + width, y + height, PANEL, RIM);
            DamageDiagram.draw(gui, vehicle, x + DAMAGE_PAD, y + DAMAGE_PAD, blink);
        }
        gui.setColor(1, 1, 1, 1);
        pose.popPose();
    }

    private static void strip(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, boolean blink) {
        JSONVehicle definition = vehicle.definition();
        framed(gui, 0, 0, STRIP_WIDTH, STRIP_HEIGHT, PANEL, STRIP_RIM);
        int middle = (STRIP_HEIGHT - ICON) / 2;
        icon(gui, TURN_LEFT, 4, middle, vehicle.flag(EntityVehicleF_Physics.TURN_LEFT) && blink ? GREEN : UNLIT);
        icon(gui, TURN_RIGHT, STRIP_WIDTH - 4 - ICON, middle, vehicle.flag(EntityVehicleF_Physics.TURN_RIGHT) && blink ? GREEN : UNLIT);
        gui.setColor(1, 1, 1, 1);
        String speed = Integer.toString(Math.min(999, Math.round(shownSpeed)));
        enlarged(gui, font, speed, SPEED_X + SPEED_FIELD - (font.width(speed) * 2 - 2), 7, 2, WHITE);
        gui.drawString(font, I18n.get("hud.slimvehicle.kmh"), SPEED_X + SPEED_FIELD + 3, 15, DIM, false);
        int gear = vehicle.gear();
        String gearText = gear < 0 ? "R" : gear == 0 ? "N" : definition.transmission.automatic ? "D" + gear : Integer.toString(gear);
        framed(gui, GEAR_X, 5, GEAR_X + GEAR_WIDTH, STRIP_HEIGHT - 5, INSET, INSET_RIM);
        centered(gui, font, gearText, GEAR_X + GEAR_WIDTH / 2, 11, gear < 0 ? AMBER : gear == 0 ? GREEN : WHITE);
        boolean running = vehicle.flag(EntityVehicleF_Physics.ENGINE);
        float maxRpm = (float) definition.engine.maxRpm;
        float redline = (float) definition.engine.redlineRpm;
        int lit = running ? Math.round(Mth.clamp(shownRpm / maxRpm, 0, 1) * RPM_SEGMENTS) : 0;
        for (int i = 0; i < RPM_SEGMENTS; i++) {
            float value = (i + 1) * maxRpm / RPM_SEGMENTS;
            int color = i >= lit ? TROUGH : value > redline ? RED : value > redline * REV_WARNING ? AMBER : GREEN;
            int left = RPM_X + i * (RPM_SEGMENT + 1);
            gui.fill(left, 6, left + RPM_SEGMENT, 12, color);
        }
        int rpm = Math.min(9999, Math.round(shownRpm / RPM_STEP) * RPM_STEP);
        gui.drawString(font, Integer.toString(rpm), RPM_X, 15, running ? DIM : UNLIT, false);
        float fuel = Mth.clamp(shownFuel, 0, 1);
        int fuelColor = fuel < .1f ? (blink ? RED : DARK_RED) : fuel < .25f ? AMBER : GREEN;
        icon(gui, FUEL, GAUGE_X, 4, fuel < .1f ? fuelColor : DIM);
        float health = Mth.clamp(vehicle.health() / DamageModel.FULL, 0, 1);
        icon(gui, BODY, GAUGE_X, STRIP_HEIGHT - 4 - ICON, vehicle.wrecked() ? (blink ? RED : DARK_RED) : DIM);
        gui.setColor(1, 1, 1, 1);
        bar(gui, GAUGE_X + ICON + 2, 6, fuel, fuelColor);
        bar(gui, GAUGE_X + ICON + 2, STRIP_HEIGHT - 6 - GAUGE_BAR, health, health > .6f ? GREEN : health > .3f ? AMBER : RED);
    }

    private static void enlarged(GuiGraphics gui, Font font, String text, int x, int y, int scale, int color) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        gui.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static void bar(GuiGraphics gui, int x, int y, float level, int color) {
        int lit = level <= 0 ? 0 : Math.max(1, Math.round(level * GAUGE_SEGMENTS));
        for (int i = 0; i < GAUGE_SEGMENTS; i++) {
            int left = x + i * (GAUGE_SEGMENT + 1);
            gui.fill(left, y, left + GAUGE_SEGMENT, y + GAUGE_BAR, i < lit ? color : TROUGH);
        }
    }

    private static int lamps(GuiGraphics gui, EntityVehicleF_Physics vehicle, boolean blink) {
        JSONVehicle definition = vehicle.definition();
        List<int[]> lamps = new ArrayList<>();
        if (vehicle.flag(EntityVehicleF_Physics.LIGHTS)) lamps.add(new int[]{HEADLIGHT, BLUE});
        if (VehicleControls.handbrakeHeld()) lamps.add(new int[]{HANDBRAKE, RED});
        if (vehicle.flag(EntityVehicleF_Physics.LOCKED)) lamps.add(new int[]{LOCK, AMBER});
        if (definition.emergency && vehicle.flag(EntityVehicleF_Physics.SIREN)) lamps.add(new int[]{SIREN, blink ? RED : BLUE});
        if (vehicle.flag(EntityVehicleF_Physics.RADIO)) lamps.add(new int[]{RADIO, BLUE});
        if (definition.wipers != null && vehicle.flag(EntityVehicleF_Physics.WIPERS)) lamps.add(new int[]{WIPERS, GREEN});
        float engine = vehicle.engineHealth();
        if (engine < 50) lamps.add(new int[]{ENGINE, engine < 20 ? (blink ? RED : DARK_RED) : AMBER});
        if (!VehicleControls.holdsKey(vehicle)) lamps.add(new int[]{KEY, blink ? AMBER : UNLIT});
        if (lamps.isEmpty()) return 0;
        int width = lamps.size() * (ICON + LAMP_GAP) - LAMP_GAP;
        int x = (STRIP_WIDTH - width) / 2;
        for (int[] lamp : lamps) {
            icon(gui, lamp[0], x, -ICON - LAMP_GAP, lamp[1]);
            x += ICON + LAMP_GAP;
        }
        gui.setColor(1, 1, 1, 1);
        return ICON + LAMP_GAP;
    }

    private static void icon(GuiGraphics gui, int index, int x, int y, int color) {
        gui.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
        gui.blit(ICONS, x, y, index * ICON, 0, ICON, ICON, ICON * ICON_COUNT, ICON);
    }

    private static List<Segment> hint(EntityVehicleF_Physics vehicle) {
        if (vehicle.wrecked()) return text("hud.slimvehicle.hint.wrecked");
        if (vehicle.engineHealth() <= 0) return text("hud.slimvehicle.hint.engine_dead");
        if (vehicle.fuel() <= 0) return text("hud.slimvehicle.hint.tank_empty");
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
