package ru.mosvegas.items.rp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.mosvegas.items.ConsumptionPose;

public final class RpPoser {
    private static final String[] PARTS = {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    private static Entity posedEntity;
    private static RpScenes.Scene posedScene;
    private static RpPlayback.Entry posedEntry;
    private static Matrix4f[] posedWorld;
    private static Matrix4f posedBase;

    private RpPoser() {}

    public static void pose(LivingEntity entity, BipedEntityModel<?> model, float tickDelta) {
        posedEntity = null;
        RpPlayback.Entry entry = RpPlayback.of(entity);
        if (entry == null) return;
        RpScenes.Scene scene = RpScenes.get(entry.item());
        if (scene == null) return;
        RpAnim anim = RpPlayback.animation(entity, entry, scene);
        if (anim == null) return;
        Matrix4f[] world = scene.geo().pose(anim, RpPlayback.time(entry, anim, tickDelta));
        String prefix = entry.role() == RpServer.ROLE_TARGET ? "t_" : "";
        Matrix4f base = RpPlayback.base(entity, scene, entry, tickDelta);
        float[] offset = RpPlayback.offset(scene, entry);
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < PARTS.length; i++) {
            if (entry.hold() && parts[i] != model.rightArm && parts[i] != model.leftArm) continue;
            RpGeo.Bone bone = scene.geo().byName.get(prefix + PARTS[i]);
            if (bone == null) continue;
            Matrix4f raw = new Matrix4f(base).mul(world[bone.index]).translate(offset[0], offset[1], offset[2]);
            apply(parts[i], raw);
        }
        model.hat.copyTransform(model.head);
        if (model instanceof PlayerEntityModel<?> player) {
            player.jacket.copyTransform(model.body);
            player.leftSleeve.copyTransform(model.leftArm);
            player.rightSleeve.copyTransform(model.rightArm);
            player.leftPants.copyTransform(model.leftLeg);
            player.rightPants.copyTransform(model.rightLeg);
            if (RpPlayback.firstPerson(entity)) {
                model.head.visible = model.hat.visible = model.body.visible = false;
                model.rightLeg.visible = model.leftLeg.visible = false;
                player.jacket.visible = player.leftPants.visible = player.rightPants.visible = false;
            }
        }
        posedEntity = entity;
        posedScene = scene;
        posedEntry = entry;
        posedWorld = world;
        posedBase = base;
    }

    static void apply(ModelPart part, Matrix4f raw) {
        Matrix4f java = new Matrix4f(RpGeo.RAW_TO_MODEL).mul(raw).mul(RpGeo.MODEL_TO_RAW);
        ModelTransform rest = part.getDefaultTransform();
        Vector3f pivot = java.transformPosition(new Vector3f(rest.pivotX, rest.pivotY, rest.pivotZ));
        Quaternionf rotation = java.getNormalizedRotation(new Quaternionf());
        Vector3f angles = ConsumptionPose.modelAngles(rotation);
        part.pivotX = pivot.x;
        part.pivotY = pivot.y;
        part.pivotZ = pivot.z;
        part.pitch = angles.x;
        part.yaw = angles.y;
        part.roll = angles.z;
    }

    public static void drawProps(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        if (posedEntity != entity || posedScene == null) return;
        boolean target = posedEntry.role() == RpServer.ROLE_TARGET;
        Matrix4f base = posedBase;
        var buffer = vertices.getBuffer(RenderLayer.getEntityTranslucent(posedScene.texture()));
        posedScene.geo().draw(matrices, buffer, posedWorld, base, bone -> bone.isTarget() == target, light,
                OverlayTexture.DEFAULT_UV, 1);
    }

    private static final float LOOK_DOWN = 30;

    public record Shot(Vec3d position, float yaw, float pitch) {}

    public static Shot camera(Entity entity, float tickDelta) {
        if (!(entity instanceof LivingEntity living) || !RpPlayback.firstPerson(entity)) return null;
        RpPlayback.Entry entry = RpPlayback.of(entity);
        RpScenes.Scene scene = RpScenes.get(entry.item());
        RpAnim anim = RpPlayback.animation(entity, entry, scene);
        if (anim == null) return null;
        float t = RpPlayback.time(entry, anim, tickDelta);
        float[] tmp = new float[3];
        Vector3f eye, look = null;
        float pitch = 0;
        float[] eyeBone = anim.sample("cam_eye", RpAnim.POSITION, t, new float[3]);
        float[] lookBone = anim.sample("cam_look", RpAnim.POSITION, t, new float[3]);
        if (eyeBone != null && lookBone != null) {
            eye = new Vector3f(eyeBone[0], eyeBone[1], eyeBone[2]);
            look = new Vector3f(lookBone[0], lookBone[1], lookBone[2]);
        } else {
            eye = new Vector3f(0, 28, 0);
            float[] root = anim.sample("player", RpAnim.POSITION, t, tmp);
            if (root != null) eye.add(root[0], root[1], root[2]);
            float[] cam = anim.sample("cam", RpAnim.ROTATION, t, new float[3]);
            if (cam != null) {
                pitch = cam[0];
            } else {
                float[] head = anim.sample("head", RpAnim.ROTATION, t, new float[3]);
                pitch = LOOK_DOWN + (head != null ? head[0] : 0);
            }
        }
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw);
        Matrix4f toWorld = rawToWorld(living, bodyYaw);
        Vec3d origin = living.getLerpedPos(tickDelta);
        Vector3f eyeWorld = toWorld.transformPosition(new Vector3f(eye));
        Vec3d position = origin.add(eyeWorld.x, eyeWorld.y, eyeWorld.z);
        if (look == null) return new Shot(position, bodyYaw, pitch);
        Vector3f lookWorld = toWorld.transformPosition(new Vector3f(look)).sub(eyeWorld);
        float yaw = (float) Math.toDegrees(Math.atan2(-lookWorld.x, lookWorld.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookWorld.y, Math.hypot(lookWorld.x, lookWorld.z)));
        return new Shot(position, yaw, lookPitch);
    }

    static Matrix4f rawToWorld(LivingEntity entity, float bodyYaw) {
        return new Matrix4f()
                .rotateY((float) Math.toRadians(180 - bodyYaw))
                .scale(-1, -1, 1)
                .scale(renderScale(entity))
                .translate(0, -1.501f, 0)
                .scale(1 / 16f)
                .mul(RpGeo.RAW_TO_MODEL);
    }

    static float renderScale(LivingEntity entity) {
        float scale = entity instanceof net.minecraft.entity.player.PlayerEntity ? 0.9375f
                : entity instanceof net.minecraft.entity.mob.HuskEntity ? 1.0625f : 1f;
        return entity.isBaby() ? scale * 0.5f : scale;
    }

    public static boolean cameraActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && RpPlayback.firstPerson(client.player);
    }
}
