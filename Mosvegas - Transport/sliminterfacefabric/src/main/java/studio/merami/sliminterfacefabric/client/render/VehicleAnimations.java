package studio.merami.sliminterfacefabric.client.render;

import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartGlass;
import studio.merami.slimcore.entities.instances.PartGroundDevice;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.PartSeat;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.rendering.Animator;
import studio.merami.sliminterfacefabric.client.ClientConfig;

import static studio.merami.slimcore.rendering.Animator.POSITION;
import static studio.merami.slimcore.rendering.Animator.ROTATION;
import static studio.merami.slimcore.rendering.Animator.SCALE;

final class VehicleAnimations {
    private VehicleAnimations() {}

    static void apply(EntityVehicleF_Physics vehicle, JSONVehicle definition, float partial, Animator.Pose pose) {
        float steer = Mth.lerp(partial, vehicle.oldVisualSteer, vehicle.visualSteer);
        float roll = Mth.lerp(partial, vehicle.oldWheelRoll, vehicle.wheelRoll);
        boolean vibrate = ClientConfig.vibrations && definition.vibration;
        float age = vehicle.ticks() + partial;
        float engineShake = vibrate && vehicle.flag(EntityVehicleF_Physics.ENGINE) ? (float) Math.sin(age * (1.6 + vehicle.rpm() / 5000)) * .018f : 0;
        float roadShake = vibrate ? (float) (Math.sin(age * 2.1) * Math.min(.025, Math.abs(vehicle.speed()) * .024)) : 0;
        int chassis = pose.bone(definition.render.chassis);
        pose.set(chassis, ROTATION, -Mth.lerp(partial, vehicle.oldBodyPitch, vehicle.bodyPitch));
        pose.set(chassis, ROTATION + 2, -Mth.lerp(partial, vehicle.oldBodyRoll, vehicle.bodyRoll) + roadShake * .08f);
        pose.set(chassis, POSITION + 1, Mth.lerp(partial, vehicle.oldBodyHeave, vehicle.bodyHeave) * 16 + roadShake + engineShake * .18f);
        if (definition.engine.bone != null) {
            int engine = pose.bone(definition.engine.bone);
            pose.set(engine, ROTATION + 2, engineShake * .24f);
            pose.set(engine, POSITION + 1, engineShake);
            pose.set(engine, POSITION, engineShake * .4f);
        }
        wipers(vehicle, definition, partial, pose);
        for (PartGroundDevice wheel : vehicle.wheels()) {
            String name = wheel.definition.bone;
            int steering = pose.bone("steer_" + name);
            pose.set(steering, ROTATION + 1, wheel.definition.steer ? steer : 0);
            pose.set(steering, POSITION + 1, Mth.lerp(partial, wheel.oldTravel, wheel.travel) * 16);
            pose.set(pose.bone("roll_" + name), ROTATION, -roll);
            int tyre = pose.bone("tyre_" + name);
            pose.set(tyre, SCALE + 1, wheel.isPunctured() ? .7f : 1);
            pose.set(tyre, SCALE + 2, wheel.isPunctured() ? .82f : 1);
        }
        for (PartOpening opening : vehicle.openings()) {
            float open = Mth.lerp(partial, opening.previousProgress, opening.progress);
            double length = opening.definition.rotate.length();
            float buckle = opening.isJammed() && length > 0 ? (float) (opening.definition.buckle / length) : 0;
            float swing = open + buckle;
            int bone = pose.bone(opening.definition.bone);
            Point3D rotate = opening.definition.rotate;
            if (rotate.x != 0) pose.set(bone, ROTATION, (float) rotate.x * swing);
            if (rotate.y != 0) pose.set(bone, ROTATION + 1, (float) rotate.y * swing);
            if (rotate.z != 0) pose.set(bone, ROTATION + 2, (float) rotate.z * swing);
        }
        for (PartGlass pane : vehicle.glassPanes()) {
            PartSeat seat = pane.window() >= 0 && pane.window() < vehicle.seats().size() ? vehicle.seat(pane.window()) : null;
            float window = seat == null ? 0 : Mth.lerp(partial, seat.previousWindowProgress, seat.windowProgress);
            int bone = pose.bone(pane.definition.bone);
            if (pane.window() >= 0) pose.set(bone, POSITION + 1, -window * (float) definition.windowDrop * 16);
            pose.setHidden(bone, window > .95f || pane.isBroken());
        }
        driver(vehicle, definition, steer, pose);
    }

    private static void wipers(EntityVehicleF_Physics vehicle, JSONVehicle definition, float partial, Animator.Pose pose) {
        JSONVehicle.Wipers wipers = definition.wipers;
        if (wipers == null) return;
        float phase = Mth.lerp(partial, vehicle.oldWiperPhase, vehicle.wiperPhase);
        float sweep = -(1 - Mth.cos(phase * Mth.TWO_PI)) * .5f * (float) wipers.sweep;
        double angle = Math.toRadians(wipers.glassAngle);
        double c = Math.cos(sweep);
        double s = Math.sin(sweep);
        double ny = -Math.sin(angle);
        double nz = Math.cos(angle);
        for (String name : wipers.bones) {
            int bone = pose.bone(name);
            pose.set(bone, ROTATION, (float) Math.atan2(ny * nz * (1 - c), c + nz * nz * (1 - c)));
            pose.set(bone, ROTATION + 1, (float) Math.asin(ny * s));
            pose.set(bone, ROTATION + 2, (float) Math.atan2(nz * s, c));
        }
    }

    private static void driver(EntityVehicleF_Physics vehicle, JSONVehicle definition, float steer, Animator.Pose pose) {
        JSONVehicle.Driver driver = definition.driver;
        if (driver == null) return;
        if (driver.steering.bone != null) {
            int bone = pose.bone(driver.steering.bone);
            float angle = driver.steering.angle(steer);
            Point3D axis = driver.steering.axis;
            if (axis.x != 0) pose.set(bone, ROTATION, (float) axis.x * angle);
            if (axis.y != 0) pose.set(bone, ROTATION + 1, (float) axis.y * angle);
            if (axis.z != 0) pose.set(bone, ROTATION + 2, (float) axis.z * angle);
        }
        if (driver.leverBone() == null) return;
        int gear = vehicle.gear();
        int gates = (definition.gears() + 1) / 2;
        int bone = pose.bone(driver.leverBone());
        if (definition.transmission.automatic) {
            pose.set(bone, ROTATION, gear < 0 ? -.15f : gear == 0 ? 0 : .15f);
            pose.set(bone, ROTATION + 2, 0);
        } else if (gear == 0) {
            pose.set(bone, ROTATION, 0);
            pose.set(bone, ROTATION + 2, gates <= 1 ? 0 : -.25f);
        } else if (gear < 0) {
            pose.set(bone, ROTATION, .2f);
            pose.set(bone, ROTATION + 2, .25f);
        } else {
            int gate = (gear - 1) / 2;
            pose.set(bone, ROTATION, gear % 2 == 1 ? -.2f : .2f);
            pose.set(bone, ROTATION + 2, gates <= 1 ? 0 : -.25f + .5f * gate / (gates - 1));
        }
    }
}
