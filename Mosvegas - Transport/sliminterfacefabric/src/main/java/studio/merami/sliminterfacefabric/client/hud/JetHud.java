package studio.merami.sliminterfacefabric.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.sliminterfacefabric.client.ClientConfig;
import studio.merami.sliminterfacefabric.client.VehicleControls;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class JetHud {
    private static final int WHITE = 0xebffffff;
    private static final int FAINT = 0x78ffffff;
    private static final int OFF = 0x26ffffff;
    private static final int BACK = 0x78000000;
    private static final int GREEN = 0xff78e68c;
    private static final int AMBER = 0xffffbe46;
    private static final int RED = 0xffff5050;
    private static final int RED_OFF = 0x46ff5050;
    private static final int GOLD = 0xffffd250;
    private static final int INK = 0xff0a0c0e;
    private static final int WINDOW = 0xa0000000;
    private static final int HELP_BACK = 0x96000000;
    private static final int WIDTH = 130;
    private static final int HEIGHT = 60;
    private static final int MARGIN = 10;
    private static final int BOTTOM = 8;
    private static final int PROMPT_BOTTOM = 26;
    private static final int SEGMENTS = 24;
    private static final int SEGMENT_STEP = 5;
    private static final double AMBER_SHARE = .8;
    private static final int FUEL_CELLS = 5;
    private static final int LINE = 11;
    private static final int HELP_ROW = 13;
    private static final int JUMP_TOP = 46;
    private static final float MOVING = .02f;
    private static final float LOW_FUEL = .25f;
    private static final float EMPTY_FUEL = .1f;
    private static final float DAMAGED = .6f;
    private static final int WARNING_TICKS = 30;
    private static final String GEARS = "FNR";

    record Jump(boolean live, boolean record, double height, double best) {}

    private JetHud() {}

    static int render(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float speed, float rpm, float fuel, Jump jump, boolean blink) {
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        int x = width - MARGIN - WIDTH;
        int y = height - BOTTOM - HEIGHT;
        cluster(gui, font, vehicle, speed, rpm, fuel, x, y);
        int top = lines(gui, font, vehicle, fuel, width - MARGIN, y);
        if (jump != null) jump(gui, font, jump, width / 2);
        if (ClientConfig.hints && Math.abs(vehicle.speed()) < MOVING) help(gui, font, vehicle);
        warning(gui, font, vehicle, jump, width, height, blink);
        VehicleHud.prompt(gui, font, width / 2, height - PROMPT_BOTTOM);
        return top;
    }

    private static void cluster(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float speed, float rpm, float fuel, int x, int y) {
        gui.fill(x + 1, y, x + WIDTH - 1, y + HEIGHT, BACK);
        gui.fill(x, y + 1, x + 1, y + HEIGHT - 1, BACK);
        gui.fill(x + WIDTH - 1, y + 1, x + WIDTH, y + HEIGHT - 1, BACK);
        int left = x + 5;
        tachometer(gui, font, vehicle, rpm, left, y + 14);
        int row = y + 29;
        gear(gui, font, vehicle, left, row);
        String digits = Integer.toString(Math.min(999, Math.round(speed)));
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x + 88 - font.width(digits) * 3, row + 2, 0);
        pose.scale(3, 3, 1);
        gui.drawString(font, digits, 0, 0, vehicle.flag(EntityVehicleF_Physics.ENGINE) ? WHITE : FAINT, true);
        pose.popPose();
        gui.drawString(font, I18n.get("hud.slimvehicle.kmh"), x + 95, row + 2, FAINT, true);
        float tank = Mth.clamp(fuel, 0, 1);
        int color = tank < EMPTY_FUEL ? RED : tank < LOW_FUEL ? AMBER : WHITE;
        pump(gui, x + 96, row + 16, color);
        int lit = tank <= 0 ? 0 : Math.max(1, Math.round(tank * FUEL_CELLS));
        for (int i = 0; i < FUEL_CELLS; i++) {
            int at = x + 105 + i * 4;
            gui.fill(at, row + 17, at + 3, row + 24, i < lit ? color : OFF);
        }
    }

    private static void tachometer(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float rpm, int left, int base) {
        double max = vehicle.definition().engine.maxRpm;
        double redline = vehicle.definition().engine.redlineRpm;
        double amber = redline * AMBER_SHARE;
        int lit = (int) Math.round(Mth.clamp(rpm / max, 0, 1) * SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            int tall = 3 + Math.round(7f * i / (SEGMENTS - 1));
            int at = left + i * SEGMENT_STEP;
            double value = (i + 1) * max / SEGMENTS;
            boolean red = value > redline;
            int color = i < lit ? (red ? RED : value > amber ? AMBER : WHITE) : red ? RED_OFF : OFF;
            gui.fill(at, base - tall, at + SEGMENT_STEP - 1, base, color);
        }
        for (int thousands = 0; thousands * 1000 <= max; thousands += 2) {
            int at = left + (int) Math.round(thousands * 1000 / max * SEGMENTS) * SEGMENT_STEP;
            gui.drawString(font, Integer.toString(thousands), at, base + 2, FAINT, true);
        }
    }

    private static void gear(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, int left, int row) {
        float speed = vehicle.speed();
        char gear = speed < -MOVING ? 'R' : speed > MOVING ? 'F' : 'N';
        for (int i = 0; i < GEARS.length(); i++) {
            char letter = GEARS.charAt(i);
            String text = String.valueOf(letter);
            int y = row + i * 10;
            if (letter == gear) {
                gui.fill(left - 1, y - 1, left + 7, y + 8, letter == 'N' ? GREEN : letter == 'R' ? AMBER : WHITE);
                gui.drawString(font, text, left + 1, y, INK, false);
            } else {
                gui.drawString(font, text, left + 1, y, OFF, true);
            }
        }
    }

    private static void pump(GuiGraphics gui, int x, int y, int color) {
        gui.fill(x, y, x + 4, y + 7, color);
        gui.fill(x + 1, y + 1, x + 3, y + 3, WINDOW);
        gui.fill(x + 4, y + 1, x + 5, y + 2, color);
        gui.fill(x + 5, y + 2, x + 6, y + 6, color);
        gui.fill(x - 1, y + 7, x + 5, y + 8, color);
    }

    private static int lines(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float fuel, int right, int top) {
        int at = top - 4;
        if (fuel < LOW_FUEL) {
            at -= LINE;
            String text = I18n.get("hud.slimvehicle.heli.fuel", String.format(Locale.ROOT, "%.0f", vehicle.fuel()));
            gui.drawString(font, text, right - font.width(text), at, fuel < EMPTY_FUEL ? RED : AMBER, true);
        }
        float health = vehicle.wrecked() ? 0 : Mth.clamp(vehicle.health() / DamageModel.FULL, 0, 1);
        if (health < DAMAGED) {
            at -= LINE;
            String text = I18n.get("hud.slimvehicle.heli.hull", Math.round(health * 100));
            gui.drawString(font, text, right - font.width(text), at, health < .3f ? RED : AMBER, true);
        }
        return at;
    }

    private static void jump(GuiGraphics gui, Font font, Jump jump, int centre) {
        boolean record = jump.record() && !jump.live();
        String title = I18n.get(record ? "hud.slimvehicle.craft.record" : "hud.slimvehicle.craft.jump");
        gui.drawString(font, title, centre - font.width(title) / 2, JUMP_TOP, record ? GOLD : FAINT, true);
        String value = I18n.get("hud.slimvehicle.heli.metres", String.format(Locale.ROOT, "%.1f", jump.height()));
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(centre - font.width(value) * 3 / 2f, JUMP_TOP + 12, 0);
        pose.scale(3, 3, 1);
        gui.drawString(font, value, 0, 0, record ? GOLD : WHITE, true);
        pose.popPose();
        String best = I18n.get("hud.slimvehicle.craft.best", String.format(Locale.ROOT, "%.1f", jump.best()));
        gui.drawString(font, best, centre - font.width(best) / 2, JUMP_TOP + 40, FAINT, true);
    }

    private static void help(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle) {
        List<VehicleHud.Hint> rows = new ArrayList<>();
        if (!vehicle.flag(EntityVehicleF_Physics.ENGINE)) rows.add(new VehicleHud.Hint(VehicleControls.keyName(VehicleAction.ENGINE), I18n.get("hud.slimvehicle.heli.help.engine"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.forwardName(), I18n.get("hud.slimvehicle.jet.help.gas"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.backName(), I18n.get("hud.slimvehicle.jet.help.back"), false));
        rows.add(new VehicleHud.Hint(List.of(VehicleControls.leftName(), VehicleControls.rightName()), I18n.get("hud.slimvehicle.heli.help.turn"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.liftName(), I18n.get("hud.slimvehicle.jet.help.hop"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.keyName(VehicleAction.LIGHTS), I18n.get("hud.slimvehicle.heli.help.lights"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.keyName(VehicleAction.EXIT), I18n.get("hud.slimvehicle.heli.help.exit"), false));
        int width = 0;
        for (VehicleHud.Hint row : rows) width = Math.max(width, VehicleHud.capsWidth(font, row.keys()) + 4 + font.width(row.label()));
        gui.fill(6, 6, 6 + width + 12, 6 + rows.size() * HELP_ROW + 8, HELP_BACK);
        for (int i = 0; i < rows.size(); i++) {
            VehicleHud.Hint row = rows.get(i);
            int y = 10 + i * HELP_ROW;
            VehicleHud.caps(gui, font, row.keys(), 12, y);
            gui.drawString(font, row.label(), 12 + VehicleHud.capsWidth(font, row.keys()) + 4, y + 2, WHITE, true);
        }
    }

    private static void warning(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, Jump jump, int width, int height, boolean blink) {
        List<String> list = new ArrayList<>();
        boolean airborne = jump != null && jump.live();
        if (vehicle.wrecked()) list.add("hud.slimvehicle.hint.wrecked");
        if (vehicle.flag(EntityVehicleF_Physics.ENGINE) && !airborne && Double.isNaN(vehicle.waterDepth())) list.add("hud.slimvehicle.hint.beached");
        if (vehicle.engineHealth() <= 0) list.add("hud.slimvehicle.hint.engine_dead");
        if (vehicle.fuel() <= 0) list.add("hud.slimvehicle.hint.tank_empty");
        if (!VehicleControls.holdsKey(vehicle)) list.add("hud.slimvehicle.hint.no_key");
        if (list.isEmpty()) return;
        String text = I18n.get(list.get((int) ((vehicle.world().gameTime() / WARNING_TICKS) % list.size())));
        gui.drawString(font, text, (width - font.width(text)) / 2, height / 2 - 40, blink ? RED : 0xffc03c3c, true);
    }
}
