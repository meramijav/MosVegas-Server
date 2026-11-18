package studio.merami.sliminterfacefabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONCraft;
import studio.merami.slimcore.jsondefs.JSONVehicle;

public final class CraftRiderPose {
    private static final double ARM = 11;
    private static final double LEG = 12;
    private static final float LEAN_TURN = .32f;
    private static final float TURN_PACE = .4f;
    private static final float LEAN_FORWARD = .22f;
    private static final float LEAN_BACK = .16f;
    private static final float AIR_PITCH = .12f;
    private static final float LAND_PITCH = .22f;
    private static final float IDLE_BACK = .14f;
    private static final float IDLE_SWAY = .03f;
    private static final double STAND = .2;
    private static final double CROUCH = .13;
    private static final double LAND_FORWARD = .04;

    private record Lean(Point3D shift, float roll, float pitch) {}

    private CraftRiderPose() {}

    private static JSONCraft.Riding riding(EntityVehicleF_Physics vehicle) {
        JSONCraft craft = vehicle.definition().craft;
        return craft == null ? null : craft.riding;
    }

    private static float share(EntityVehicleF_Physics vehicle, float partial) {
        double lock = vehicle.definition().physics.steerLock;
        float steer = Mth.lerp(partial, vehicle.oldVisualSteer, vehicle.visualSteer);
        return lock > 0 ? Mth.clamp(steer / (float) lock, -1, 1) : 0;
    }

    private static Lean lean(EntityVehicleF_Physics vehicle, JSONCraft.Riding riding, float partial) {
        JSONCraft craft = vehicle.definition().craft;
        float pace = (float) Mth.clamp(Math.abs(vehicle.speed()) / (craft.maxSpeed() * TURN_PACE), 0, 1);
        float cyclic = Mth.lerp(partial, vehicle.oldVisualCyclic, vehicle.visualCyclic);
        float air = Mth.lerp(partial, vehicle.oldRiderAir, vehicle.riderAir);
        float land = Mth.lerp(partial, vehicle.oldRiderLand, vehicle.riderLand);
        float idle = smooth(Mth.lerp(partial, vehicle.oldRiderIdle, vehicle.riderIdle));
        float age = vehicle.ticks() + partial;
        float roll = -share(vehicle, partial) * pace * LEAN_TURN * (1 - air * .5f) + idle * Mth.sin(age * .05f) * IDLE_SWAY;
        float pitch = (float) riding.lean + Math.max(0, cyclic) * LEAN_FORWARD - Math.max(0, -cyclic) * LEAN_BACK
                + air * AIR_PITCH + land * LAND_PITCH - idle * IDLE_BACK;
        Point3D shift = new Point3D(0, air * STAND - land * CROUCH, land * LAND_FORWARD);
        return new Lean(shift, roll, pitch);
    }

    public static void lean(PoseStack pose, EntityVehicleF_Physics vehicle, float partial) {
        JSONCraft.Riding riding = riding(vehicle);
        if (riding == null) return;
        Lean lean = lean(vehicle, riding, partial);
        JSONVehicle definition = vehicle.definition();
        Point3D shift = lean.shift().scale(definition.scale);
        double hip = ChassisGeometry.riderHipHeight(definition);
        pose.translate(-shift.x, shift.y, -shift.z);
        pose.translate(0, hip, 0);
        pose.mulPose(Axis.ZP.rotation(-lean.roll()));
        pose.mulPose(Axis.XP.rotation(-lean.pitch()));
        pose.translate(0, -hip, 0);
    }

    public static boolean arms(ModelPart left, ModelPart right, EntityVehicleF_Physics vehicle, float partial) {
        JSONCraft.Riding riding = riding(vehicle);
        if (riding == null) return false;
        Lean lean = lean(vehicle, riding, partial);
        float share = share(vehicle, partial);
        float idle = smooth(Mth.lerp(partial, vehicle.oldRiderIdle, vehicle.riderIdle));
        aim(vehicle, left, unlean(vehicle, lean, riding.grip(true, share)), ARM, .65, 1.4);
        aim(vehicle, right, unlean(vehicle, lean, riding.grip(false, share).lerp(riding.rest, idle)), ARM, .65, 1.4);
        return true;
    }

    public static boolean legs(ModelPart left, ModelPart right, EntityVehicleF_Physics vehicle, float partial) {
        JSONCraft.Riding riding = riding(vehicle);
        if (riding == null) return false;
        Lean lean = lean(vehicle, riding, partial);
        aim(vehicle, left, unlean(vehicle, lean, riding.foot(true)), LEG, .7, 1.4);
        aim(vehicle, right, unlean(vehicle, lean, riding.foot(false)), LEG, .7, 1.4);
        return true;
    }

    private static Point3D unlean(EntityVehicleF_Physics vehicle, Lean lean, Point3D target) {
        JSONVehicle definition = vehicle.definition();
        double size = definition.scale;
        Point3D hip = vehicle.seat(0).pos().add(0, ChassisGeometry.riderThighRadius(definition), 0);
        Point3D shift = lean.shift();
        double x = -(target.x - hip.x - shift.x) * size;
        double y = (target.y - hip.y - shift.y) * size;
        double z = -(target.z - hip.z - shift.z) * size;
        double c = Math.cos(lean.roll());
        double s = Math.sin(lean.roll());
        double rx = x * c - y * s;
        double ry = x * s + y * c;
        c = Math.cos(lean.pitch());
        s = Math.sin(lean.pitch());
        double py = ry * c - z * s;
        double pz = ry * s + z * c;
        return new Point3D(hip.x - rx / size, hip.y + py / size, hip.z - pz / size);
    }

    private static void aim(EntityVehicleF_Physics vehicle, ModelPart part, Point3D target, double length, double shortest, double longest) {
        JSONVehicle definition = vehicle.definition();
        Point3D seat = vehicle.seat(0).pos();
        double scale = ChassisGeometry.riderModelScale(definition);
        double size = definition.scale;
        double x = (target.x - seat.x) * size * 16 / scale - part.x;
        double y = 24.016 - ((target.y - seat.y - ChassisGeometry.riderThighRadius(definition)) * size + ChassisGeometry.riderHipHeight(definition)) * 16 / scale - part.y;
        double z = -(target.z - seat.z) * size * 16 / scale - part.z;
        part.xRot = (float) Math.atan2(z, Math.sqrt(x * x + y * y));
        part.yRot = 0;
        part.zRot = (float) -Math.atan2(x, y);
        part.yScale = (float) Mth.clamp(Math.sqrt(x * x + y * y + z * z) / length, shortest, longest);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
