package studio.merami.sliminterfacefabric.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.physics.CraftPhysics;
import studio.merami.sliminterfacefabric.client.ClientConfig;
import studio.merami.sliminterfacefabric.client.VehicleControls;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HeliHud {
    private static final int WHITE = 0xebffffff;
    private static final int FAINT = 0x78ffffff;
    private static final int SHADOW = 0x5a000000;
    private static final int GREEN = 0xff78e68c;
    private static final int AMBER = 0xffffbe46;
    private static final int RED = 0xffff5050;
    private static final int HELP_BACK = 0x96000000;
    private static final int ALT_RANGE = 150;
    private static final int ALT_STEP = 10;
    private static final int ALT_MAJOR = 50;
    private static final int ALT_MARGIN = 22;
    private static final int CORNER_MARGIN = 10;
    private static final int CORNER_BOTTOM = 26;
    private static final int LINE = 11;
    private static final int HELP_ROW = 13;
    private static final int SPOOL_BAR = 40;
    private static final double SINK_SHOWN = .3;
    private static final double PULL_UP_SPEED = -6;
    private static final double PULL_UP_HEIGHT = 14;
    private static final double ON_GROUND = 1;
    private static final float LOW_FUEL = .25f;
    private static final float EMPTY_FUEL = .1f;
    private static final float DAMAGED = .6f;
    private static final int WARNING_TICKS = 30;

    private HeliHud() {}

    static int render(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float speed, float fuel, double agl, double vertical, boolean blink) {
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        altimeter(gui, font, agl, vertical, width, height);
        int top = corner(gui, font, vehicle, speed, fuel, width, height);
        if (ClientConfig.hints && agl < ON_GROUND) help(gui, font, vehicle);
        warning(gui, font, vehicle, agl, vertical, width, height, blink);
        VehicleHud.prompt(gui, font, width / 2, height - CORNER_BOTTOM);
        return top;
    }

    private static void altimeter(GuiGraphics gui, Font font, double agl, double vertical, int width, int height) {
        int x = width - ALT_MARGIN;
        int top = height * 4 / 15;
        int bottom = height * 11 / 15;
        gui.fill(x + 1, top + 1, x + 3, bottom + 1, SHADOW);
        gui.fill(x, top, x + 2, bottom, FAINT);
        for (int value = 0; value <= ALT_RANGE; value += ALT_STEP) {
            int y = bottom - (bottom - top) * value / ALT_RANGE;
            gui.fill(x + 2, y, x + 2 + (value % ALT_MAJOR == 0 ? 6 : 3), y + 1, FAINT);
        }
        double shown = Mth.clamp(agl, 0, ALT_RANGE);
        int y = (int) Math.round(bottom - (bottom - top) * shown / ALT_RANGE);
        for (int i = 0; i < 4; i++) gui.fill(x - 2 - i, y - i, x - 1 - i, y + i + 1, WHITE);
        String label = I18n.get("hud.slimvehicle.heli.metres", Math.round(shown));
        gui.drawString(font, label, x - 8 - font.width(label), y - 4, WHITE, true);
        if (Math.abs(vertical) < SINK_SHOWN) return;
        String arrow = (vertical > 0 ? "▲ " : "▼ ") + String.format(Locale.ROOT, "%.1f", Math.abs(vertical));
        int color = vertical > 0 ? GREEN : vertical < PULL_UP_SPEED ? RED : AMBER;
        gui.drawString(font, arrow, x - 8 - font.width(arrow), y + 6, color, true);
    }

    private static int corner(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float speed, float fuel, int width, int height) {
        int right = width - CORNER_MARGIN;
        int y = height - CORNER_BOTTOM;
        String unit = I18n.get("hud.slimvehicle.kmh");
        int unitX = right - font.width(unit);
        gui.drawString(font, unit, unitX, y + 7, FAINT, true);
        String digits = Integer.toString(Math.round(speed));
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(unitX - 4 - font.width(digits) * 2, y, 0);
        pose.scale(2, 2, 1);
        gui.drawString(font, digits, 0, 0, WHITE, true);
        pose.popPose();
        int at = y - 4;
        float rotor = vehicle.rotor();
        if (vehicle.flag(EntityVehicleF_Physics.ENGINE) && rotor < CraftPhysics.HOVER_ROTOR) {
            at -= LINE;
            String text = I18n.get("hud.slimvehicle.heli.rotor", Math.round(rotor * 100));
            gui.drawString(font, text, right - font.width(text), at, AMBER, true);
            gui.fill(right - SPOOL_BAR, at + 9, right, at + 10, SHADOW);
            gui.fill(right - SPOOL_BAR, at + 9, right - SPOOL_BAR + Math.round(SPOOL_BAR * Mth.clamp(rotor, 0, 1)), at + 10, AMBER);
            at -= 3;
        }
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

    private static void help(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle) {
        List<VehicleHud.Hint> rows = new ArrayList<>();
        if (!vehicle.flag(EntityVehicleF_Physics.ENGINE)) rows.add(new VehicleHud.Hint(VehicleControls.keyName(VehicleAction.ENGINE), I18n.get("hud.slimvehicle.heli.help.engine"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.liftName(), I18n.get("hud.slimvehicle.heli.help.up"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.sinkName(), I18n.get("hud.slimvehicle.heli.help.down"), false));
        rows.add(new VehicleHud.Hint(VehicleControls.forwardName(), I18n.get("hud.slimvehicle.heli.help.forward"), false));
        rows.add(new VehicleHud.Hint(List.of(VehicleControls.leftName(), VehicleControls.rightName()), I18n.get("hud.slimvehicle.heli.help.turn"), false));
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

    private static void warning(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, double agl, double vertical, int width, int height, boolean blink) {
        List<String> list = new ArrayList<>();
        if (vehicle.wrecked()) list.add("hud.slimvehicle.hint.wrecked");
        if (agl > ON_GROUND && agl < PULL_UP_HEIGHT && vertical < PULL_UP_SPEED) list.add("hud.slimvehicle.craft.warn.pull_up");
        if (agl > 1.5 && vehicle.rotor() < CraftPhysics.HOVER_ROTOR) list.add("hud.slimvehicle.craft.warn.rotor");
        if (vehicle.engineHealth() <= 0) list.add("hud.slimvehicle.hint.engine_dead");
        if (vehicle.fuel() <= 0) list.add("hud.slimvehicle.hint.tank_empty");
        if (!VehicleControls.holdsKey(vehicle)) list.add("hud.slimvehicle.hint.no_key");
        if (list.isEmpty()) return;
        String text = I18n.get(list.get((int) ((vehicle.world().gameTime() / WARNING_TICKS) % list.size())));
        gui.drawString(font, text, (width - font.width(text)) / 2, height / 2 - 40, blink ? RED : 0xffc03c3c, true);
    }
}
