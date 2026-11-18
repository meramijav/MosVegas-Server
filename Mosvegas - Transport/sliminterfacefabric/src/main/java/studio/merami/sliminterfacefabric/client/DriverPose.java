package studio.merami.sliminterfacefabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

public final class DriverPose {
    static final float LOOK_YAW = 150;
    static final float LOOK_UP = -60;
    static final float LOOK_DOWN = 80;
    private static final double EYE_UP = 4.5;
    private static final double EYE_FORWARD = 3;
    private static final double EYE_BEHIND = 1;
    private static final double LEAN_OUT = .15;

    private DriverPose() {}

    public static boolean firstPersonDriver(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || !mc.options.getCameraType().isFirstPerson()) return false;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(entity);
        return vehicle != null && vehicle.driver(WrapperPlayer.of(mc.player)) && vehicle.definition().driver != null && !vehicle.seat(0).standing();
    }

    public static void clampLook(Entity entity) {
        if (!firstPersonDriver(entity)) return;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(entity);
        float relative = Mth.wrapDegrees(entity.getYRot() - vehicle.yaw());
        float yaw = Mth.clamp(relative, -LOOK_YAW, LOOK_YAW) - relative;
        float pitch = Mth.clamp(entity.getXRot(), LOOK_UP, LOOK_DOWN) - entity.getXRot();
        if (yaw != 0) {
            entity.setYRot(entity.getYRot() + yaw);
            entity.yRotO += yaw;
            entity.setYHeadRot(entity.getYRot());
        }
        if (pitch != 0) {
            entity.setXRot(entity.getXRot() + pitch);
            entity.xRotO += pitch;
        }
    }

    private static Vec3 modelOrigin(EntityVehicleF_Physics vehicle, float partial) {
        JSONVehicle definition = vehicle.definition();
        Vec3 hip = Convert.vec(vehicle.chassisToWorld(vehicle.seat(0).pos().add(0, ChassisGeometry.riderThighRadius(definition), 0), partial));
        return hip.add(0, -ChassisGeometry.riderHipHeight(definition), 0);
    }

    private static void place(PoseStack pose, EntityVehicleF_Physics vehicle, float partial) {
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, vehicle.prevYaw(), vehicle.yaw())));
        lean(pose, vehicle, partial, true);
    }

    public static Vec3 eye(Entity player, float partial) {
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        float scale = ChassisGeometry.riderModelScale(vehicle.definition());
        PoseStack pose = new PoseStack();
        place(pose, vehicle, partial);
        Vector3f neck = pose.last().pose().transformPosition(new Vector3f(0, 1.501f * scale, 0));
        Vec3 origin = modelOrigin(vehicle, partial);
        float yaw = player.getViewYRot(partial);
        float pitch = player.getViewXRot(partial);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
        double pixel = scale / 16;
        Vec3 offset = up.scale(EYE_UP * pixel).add(look.scale(EYE_FORWARD * pixel));
        float vehicleYaw = Mth.rotLerp(partial, vehicle.prevYaw(), vehicle.yaw());
        Vec3 ahead = Vec3.directionFromRotation(0, vehicleYaw);
        Vec3 side = Vec3.directionFromRotation(0, vehicleYaw - 90);
        double along = offset.dot(ahead);
        if (along < -EYE_BEHIND * pixel) offset = offset.add(ahead.scale(-EYE_BEHIND * pixel - along));
        float relative = Math.abs(Mth.wrapDegrees(yaw - vehicleYaw));
        double turn = Mth.clamp((relative - 80) / 70, 0, 1);
        turn = turn * turn * (3 - 2 * turn);
        if (turn > 0) {
            double toward = Math.signum(look.dot(side));
            offset = offset.add(side.scale(toward * LEAN_OUT * turn)).add(ahead.scale(.04 * turn));
        }
        return origin.add(neck.x(), neck.y(), neck.z()).add(offset);
    }

    public static void faceForward(ModelPart head, ModelPart hat) {
        head.xRot = head.yRot = head.zRot = 0;
        hat.copyFrom(head);
    }

    public static void arms(ModelPart left, ModelPart right, EntityVehicleF_Physics vehicle, float partial) {
        if (CraftRiderPose.arms(left, right, vehicle, partial)) return;
        JSONVehicle.Driver driver = vehicle.definition().driver;
        if (driver == null) return;
        float steer = Mth.lerp(partial, vehicle.oldVisualSteer, vehicle.visualSteer);
        Point3D leftGrip = driver.steering.grip(true, steer);
        Point3D rightGrip = driver.steering.grip(false, steer);
        float age = vehicle.ticks() + partial - vehicle.shiftAnimationStart;
        float reach = age < 0 || age >= 14 || driver.lever() == null ? 0 : age < 5 ? smooth(age / 5) : age < 8 ? 1 : 1 - smooth((age - 8) / 6);
        Point3D lever = driver.lever() == null ? rightGrip : driver.lever().add(0, 0, (vehicle.gear() % 2 == 0 ? .04 : -.04) * Math.sin(Math.PI * age / 14));
        aim(vehicle, left, leftGrip);
        aim(vehicle, right, rightGrip.lerp(lever, reach));
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static void aim(EntityVehicleF_Physics vehicle, ModelPart arm, Point3D target) {
        JSONVehicle definition = vehicle.definition();
        Point3D seat = vehicle.seat(0).pos();
        double scale = ChassisGeometry.riderModelScale(definition);
        double size = definition.scale;
        double x = (target.x - seat.x) * size * 16 / scale - arm.x;
        double y = 24.016 - ((target.y - seat.y - ChassisGeometry.riderThighRadius(definition)) * size + ChassisGeometry.riderHipHeight(definition)) * 16 / scale - arm.y;
        double z = -(target.z - seat.z) * size * 16 / scale - arm.z;
        arm.xRot = (float) Math.atan2(z, Math.sqrt(x * x + y * y));
        arm.yRot = 0;
        arm.zRot = (float) -Math.atan2(x, y);
        arm.yScale = (float) Mth.clamp(Math.sqrt(x * x + y * y + z * z) / 11, .65, 1.4);
    }

    public static void lean(PoseStack pose, EntityVehicleF_Physics vehicle, float partial, boolean driver) {
        double hip = ChassisGeometry.riderHipHeight(vehicle.definition());
        pose.translate(0, hip, 0);
        pose.mulPose(Axis.ZP.rotation(-Mth.lerp(partial, vehicle.oldBodyRoll, vehicle.bodyRoll)));
        pose.mulPose(Axis.XP.rotation(-Mth.lerp(partial, vehicle.oldBodyPitch, vehicle.bodyPitch)));
        pose.translate(0, -hip, 0);
        if (driver) CraftRiderPose.lean(pose, vehicle, partial);
    }

    public static void renderFirstPerson(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator() || !firstPersonDriver(player)) return;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        float partial = context.tickDelta();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        model.riding = true;
        model.crouching = false;
        model.attackTime = 0;
        model.young = false;
        model.swimAmount = 0;
        model.leftArmPose = model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.setAllVisible(true);
        model.head.visible = model.hat.visible = false;
        model.jacket.visible = player.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.setupAnim(player, 0, 0, player.tickCount + partial, 0, 0);
        arms(model.leftArm, model.rightArm, vehicle, partial);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        Vec3 at = modelOrigin(vehicle, partial).subtract(context.camera().getPosition());
        PoseStack pose = context.matrixStack();
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        place(pose, vehicle, partial);
        float scale = ChassisGeometry.riderModelScale(vehicle.definition());
        pose.scale(-scale, -scale, scale);
        pose.translate(0, -1.501, 0);
        var buffer = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(player.getSkin().texture());
        VertexConsumer consumer = buffer.getBuffer(type);
        int light = renderer.getPackedLightCoords(player, partial);
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        buffer.endBatch(type);
        pose.popPose();
        model.head.visible = model.hat.visible = true;
    }
}
