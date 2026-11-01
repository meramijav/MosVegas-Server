package studio.merami.sliminterfacefabric.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class DamageDiagram {
    private static final int LENGTH = 60;
    private static final int MARGIN = 2;
    private static final byte NONE = 0;
    private static final byte BODY = 1;
    private static final byte GLASS = 2;
    private static final byte SEAM = 3;
    private static final byte OUTLINE = 4;
    private static final byte TYRE = 5;
    private static final byte MIRROR = 6;
    private static final byte HEAD = 7;
    private static final byte TAIL = 8;
    private static final byte GLINT = 9;
    private static final int STEEL = 0xff9aa5af;
    private static final int SEAM_COLOR = 0xff1a1f25;
    private static final int OUTLINE_COLOR = 0xff0b0e11;
    private static final int GLASS_COLOR = 0xff2b3946;
    private static final int SHATTER = 0xff8796a4;
    private static final int GLINT_COLOR = 0xff4a6074;
    private static final int TYRE_COLOR = 0xff121518;
    private static final int MIRROR_COLOR = 0xff5d6771;
    private static final int YELLOW = 0xffe9c46a;
    private static final int AMBER = 0xffff8c2a;
    private static final int RED = 0xffe23b3b;
    private static final int DARK_RED = 0xff7a1d1d;
    private static final int HEAD_ON = 0xfffff1c2;
    private static final int HEAD_OFF = 0xffc3cad1;
    private static final int TAIL_ON = 0xffff4a3d;
    private static final int TAIL_OFF = 0xff8c2a26;
    private static final int LAMP_BROKEN = 0xff3a4047;
    private static final Map<String, Layout> LAYOUTS = new HashMap<>();

    private record Layout(JSONVehicle definition, int width, int height, byte[] kinds, int[] refs, byte[] shades) {}

    private DamageDiagram() {}

    static boolean shown(EntityVehicleF_Physics vehicle) {
        JSONVehicle definition = vehicle.definition();
        for (AJSONInteractable.Zone zone : definition.damage.zones) {
            if (vehicle.zoneDamage(zone.index) > 0) return true;
        }
        for (int i = 0; i < definition.wheels.size(); i++) {
            if (vehicle.punctured(i)) return true;
        }
        for (AJSONInteractable.Zone zone : definition.damage.zones) {
            if (zone.glassIndex >= 0 && vehicle.brokenGlass(zone.glassIndex)) return true;
        }
        return false;
    }

    static int width(JSONVehicle definition) {
        return layout(definition).width;
    }

    static int height(JSONVehicle definition) {
        return layout(definition).height;
    }

    static void clear() {
        LAYOUTS.clear();
    }

    static void draw(GuiGraphics gui, EntityVehicleF_Physics vehicle, int x, int y, boolean blink) {
        Layout layout = layout(vehicle.definition());
        boolean lights = vehicle.flag(EntityVehicleF_Physics.LIGHTS);
        for (int j = 0; j < layout.height; j++) {
            int start = 0;
            int run = 0;
            for (int i = 0; i <= layout.width; i++) {
                int color = i < layout.width ? color(layout, vehicle, i, j, blink, lights) : 0;
                if (i < layout.width && color == run) continue;
                if (run != 0) gui.fill(x + start, y + j, x + i, y + j + 1, run);
                start = i;
                run = color;
            }
        }
    }

    private static int color(Layout layout, EntityVehicleF_Physics vehicle, int i, int j, boolean blink, boolean lights) {
        int at = j * layout.width + i;
        int ref = layout.refs[at];
        return switch (layout.kinds[at]) {
            case BODY -> shade(ref < 0 ? STEEL : damage(vehicle.zoneDamage(ref) / DamageModel.FULL, blink), layout.shades[at]);
            case GLASS, GLINT -> ref >= 0 && vehicle.brokenGlass(ref) && ((i + j) & 1) == 0 ? SHATTER : layout.kinds[at] == GLINT ? GLINT_COLOR : GLASS_COLOR;
            case SEAM -> SEAM_COLOR;
            case OUTLINE -> OUTLINE_COLOR;
            case TYRE -> vehicle.punctured(ref) ? (blink ? RED : DARK_RED) : TYRE_COLOR;
            case MIRROR -> MIRROR_COLOR;
            case HEAD -> vehicle.lampBroken(ref == 0 ? EntityVehicleF_Physics.HEADLIGHT_LEFT : EntityVehicleF_Physics.HEADLIGHT_RIGHT) ? LAMP_BROKEN : lights ? HEAD_ON : HEAD_OFF;
            case TAIL -> vehicle.lampBroken(ref == 0 ? EntityVehicleF_Physics.TAILLIGHT_LEFT : EntityVehicleF_Physics.TAILLIGHT_RIGHT) ? LAMP_BROKEN : lights ? TAIL_ON : TAIL_OFF;
            default -> 0;
        };
    }

    private static int damage(float level, boolean blink) {
        if (level <= 0) return STEEL;
        if (level >= 1) return blink ? RED : DARK_RED;
        return level < .5f ? mix(YELLOW, AMBER, level * 2) : mix(AMBER, RED, (level - .5f) * 2);
    }

    private static int shade(int color, int percent) {
        int r = Math.min(255, ((color >> 16) & 255) * percent / 100);
        int g = Math.min(255, ((color >> 8) & 255) * percent / 100);
        int b = Math.min(255, (color & 255) * percent / 100);
        return color & 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int mix(int from, int to, float k) {
        int r = (int) Mth.lerp(k, (from >> 16) & 255, (to >> 16) & 255);
        int g = (int) Mth.lerp(k, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(k, from & 255, to & 255);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static Layout layout(JSONVehicle definition) {
        Layout layout = LAYOUTS.get(definition.id);
        if (layout == null || layout.definition != definition) LAYOUTS.put(definition.id, layout = build(definition));
        return layout;
    }

    private static Layout build(JSONVehicle definition) {
        AJSONInteractable.Damage damage = definition.damage;
        int body = Mth.clamp(2 * (int) Math.round(LENGTH * damage.halfWidth / damage.halfLength / 2), 16, 30);
        int width = body + 2 * MARGIN;
        int height = LENGTH + 2 * MARGIN;
        byte[] kinds = new byte[width * height];
        int[] refs = new int[width * height];
        byte[] shades = new byte[width * height];
        Arrays.fill(refs, -1);
        double scale = LENGTH / 2.0 / damage.halfLength;
        int strip = Math.max(3, Math.round(body * .17f));
        int hood = Math.round(LENGTH * .3f);
        int screen = hood + Math.max(3, Math.round(LENGTH * .08f));
        int rearGlass = Math.round(LENGTH * .8f);
        int trunk = rearGlass + Math.max(2, Math.round(LENGTH * .05f));
        AJSONInteractable.Zone front = first(damage, DamageModel.Side.FRONT);
        AJSONInteractable.Zone rear = first(damage, DamageModel.Side.REAR);
        AJSONInteractable.Zone roof = first(damage, DamageModel.Side.TOP);
        for (int j = 0; j < LENGTH; j++) {
            double z = (LENGTH / 2.0 - j - .5) / scale;
            boolean taper = j == hood || j == trunk - 1;
            for (int i = 0; i < body; i++) {
                if (!inside(i, j, body)) continue;
                int at = (j + MARGIN) * width + i + MARGIN;
                boolean side = i < strip || i >= body - strip || taper && (i == strip || i == body - 1 - strip);
                AJSONInteractable.Zone zone;
                if (j < 2) zone = front;
                else if (j >= LENGTH - 2) zone = rear;
                else if (side) zone = along(damage, i < body / 2 ? DamageModel.Side.LEFT : DamageModel.Side.RIGHT, z, z >= 0 ? front : rear);
                else if (j < hood) zone = front;
                else if (j < screen || j >= rearGlass && j < trunk) {
                    AJSONInteractable.Zone owner = j < screen ? front : rear;
                    kinds[at] = j > hood && j < screen - 1 && i - strip == j - hood + 1 ? GLINT : GLASS;
                    refs[at] = owner == null ? -1 : owner.glassIndex;
                    continue;
                } else zone = j < rearGlass ? roof : rear;
                kinds[at] = BODY;
                refs[at] = zone == null ? -1 : zone.index;
                shades[at] = (byte) (j < 2 || j >= LENGTH - 2 ? 90 : side ? 86 : j >= screen && j < rearGlass ? 108 : 100);
            }
        }
        byte[] bare = kinds.clone();
        for (int j = 0; j < LENGTH; j++) {
            for (int i = 0; i < body; i++) {
                int at = (j + MARGIN) * width + i + MARGIN;
                if (bare[at] != BODY) continue;
                int up = at - width;
                int outer = at + (i < body / 2 ? -1 : 1);
                if (bare[up] == BODY && refs[up] != refs[at] || bare[outer] == BODY && refs[outer] != refs[at]) kinds[at] = SEAM;
            }
        }
        int lamp = Math.max(2, Math.round(body * .14f));
        for (int i = 1; i < body - 1; i++) {
            int side = i < body / 2 ? 0 : 1;
            if (i > lamp && i < body - 1 - lamp) continue;
            if (inside(i, 0, body)) set(kinds, refs, width, i + MARGIN, MARGIN, HEAD, side);
            if (inside(i, LENGTH - 1, body)) set(kinds, refs, width, i + MARGIN, MARGIN + LENGTH - 1, TAIL, side);
        }
        for (int at = 0; at < kinds.length; at++) {
            if (kinds[at] != NONE) continue;
            int i = at % width;
            int j = at / width;
            if (solid(kinds, width, height, i - 1, j) || solid(kinds, width, height, i + 1, j)
                    || solid(kinds, width, height, i, j - 1) || solid(kinds, width, height, i, j + 1)) kinds[at] = OUTLINE;
        }
        for (int side = 0; side < 2; side++) {
            int column = side == 0 ? MARGIN - 1 : MARGIN + body;
            int beyond = side == 0 ? column - 1 : column + 1;
            for (int j = screen - 3; j < screen - 1; j++) {
                set(kinds, refs, width, column, j + MARGIN, MIRROR, -1);
                set(kinds, refs, width, beyond, j + MARGIN, OUTLINE, -1);
            }
            set(kinds, refs, width, column, screen - 4 + MARGIN, OUTLINE, -1);
            set(kinds, refs, width, column, screen - 1 + MARGIN, OUTLINE, -1);
        }
        for (AJSONMultipart.Wheel wheel : definition.wheels) {
            int half = Math.max(2, (int) Math.round(wheel.radius * scale * .85));
            int centre = (int) Math.round(LENGTH / 2.0 - wheel.pos.z * scale);
            boolean left = wheel.pos.x >= 0;
            int outside = left ? MARGIN - 1 : MARGIN + body;
            int inner = left ? MARGIN : MARGIN + body - 1;
            int beyond = left ? outside - 1 : outside + 1;
            for (int j = centre - half; j < centre + half; j++) {
                if (j < 0 || j >= LENGTH) continue;
                set(kinds, refs, width, outside, j + MARGIN, TYRE, wheel.index);
                set(kinds, refs, width, inner, j + MARGIN, TYRE, wheel.index);
                set(kinds, refs, width, beyond, j + MARGIN, OUTLINE, -1);
            }
            for (int j : new int[]{centre - half - 1, centre + half}) {
                if (j >= 0 && j < LENGTH) set(kinds, refs, width, outside, j + MARGIN, OUTLINE, -1);
            }
        }
        return new Layout(definition, width, height, kinds, refs, shades);
    }

    private static void set(byte[] kinds, int[] refs, int width, int i, int j, byte kind, int ref) {
        if (i < 0 || i >= width || j < 0 || j * width + i >= kinds.length) return;
        kinds[j * width + i] = kind;
        refs[j * width + i] = ref;
    }

    private static boolean solid(byte[] kinds, int width, int height, int i, int j) {
        if (i < 0 || j < 0 || i >= width || j >= height) return false;
        byte kind = kinds[j * width + i];
        return kind == BODY || kind == GLASS || kind == GLINT || kind == SEAM || kind == HEAD || kind == TAIL;
    }

    private static boolean inside(int i, int j, int body) {
        int radius = j < LENGTH / 2 ? 4 : 3;
        int dx = i < radius ? radius - i : i >= body - radius ? i - (body - radius - 1) : 0;
        int dy = j < radius ? radius - j : j >= LENGTH - radius ? j - (LENGTH - radius - 1) : 0;
        if (dx == 0 || dy == 0) return true;
        double fx = dx - .5;
        double fy = dy - .5;
        return fx * fx + fy * fy <= radius * radius;
    }

    private static AJSONInteractable.Zone first(AJSONInteractable.Damage damage, DamageModel.Side side) {
        for (AJSONInteractable.Zone zone : damage.zones) {
            if (zone.sideValue == side) return zone;
        }
        return null;
    }

    private static AJSONInteractable.Zone along(AJSONInteractable.Damage damage, DamageModel.Side side, double z, AJSONInteractable.Zone fallback) {
        AJSONInteractable.Zone best = null;
        double gap = Double.MAX_VALUE;
        for (AJSONInteractable.Zone zone : damage.zones) {
            if (zone.sideValue != side) continue;
            double distance = z < zone.from ? zone.from - z : z > zone.to ? z - zone.to : 0;
            if (distance < gap) {
                gap = distance;
                best = zone;
            }
        }
        return best == null ? fallback : best;
    }
}
