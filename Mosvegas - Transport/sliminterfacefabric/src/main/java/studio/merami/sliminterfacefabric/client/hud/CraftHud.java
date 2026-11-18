package studio.merami.sliminterfacefabric.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;

final class CraftHud {
    private static final double AIRBORNE_GAP = .45;
    private static final int MIN_JUMP_TICKS = 6;
    private static final long JUMP_SHOWN_TICKS = 100;

    private static double shownVertical;
    private static double lastY = Double.NaN;
    private static long lastTick;
    private static long jumpStart = -1;
    private static double jumpPeak;
    private static double jumpBase;
    private static long landedAt = -1000;
    private static double lastJumpHeight;
    private static double bestJump;

    private CraftHud() {}

    static void dashboard(GuiGraphics gui, Font font, EntityVehicleF_Physics vehicle, float speed, float rpm, float fuel) {
        long tick = vehicle.world().gameTime();
        boolean blink = tick % 16 < 8;
        double agl = heightAboveGround(vehicle);
        track(vehicle, tick, agl);
        if (vehicle.definition().craft.helicopter()) {
            HeliHud.render(gui, font, vehicle, speed, fuel, agl, shownVertical, blink);
            return;
        }
        JetHud.render(gui, font, vehicle, speed, rpm, fuel, jump(tick), blink);
    }

    private static JetHud.Jump jump(long tick) {
        if (jumpStart >= 0) return new JetHud.Jump(true, false, jumpPeak, bestJump);
        if (tick - landedAt < JUMP_SHOWN_TICKS) return new JetHud.Jump(false, lastJumpHeight >= bestJump - 1e-6, lastJumpHeight, bestJump);
        return null;
    }

    private static double heightAboveGround(EntityVehicleF_Physics vehicle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        Point3D at = vehicle.position();
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
        return at.y - ground;
    }

    private static void track(EntityVehicleF_Physics vehicle, long tick, double height) {
        double y = vehicle.position().y;
        if (tick != lastTick) {
            if (!Double.isNaN(lastY) && tick - lastTick < 10) {
                double rate = (y - lastY) / (tick - lastTick) * 20;
                shownVertical = Mth.lerp(.3, shownVertical, rate);
            }
            lastY = y;
            lastTick = tick;
            if (vehicle.definition().craft.watercraft()) trackJump(tick, height, y);
        }
    }

    private static void trackJump(long tick, double height, double y) {
        boolean airborne = height > AIRBORNE_GAP;
        if (airborne && jumpStart < 0) {
            jumpStart = tick;
            jumpBase = y - height;
            jumpPeak = height;
        } else if (airborne) {
            jumpPeak = Math.max(jumpPeak, y - jumpBase);
        } else if (jumpStart >= 0) {
            if (tick - jumpStart >= MIN_JUMP_TICKS) {
                lastJumpHeight = jumpPeak;
                bestJump = Math.max(bestJump, jumpPeak);
                landedAt = tick;
            }
            jumpStart = -1;
        }
    }
}
