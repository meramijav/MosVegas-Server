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
        float t = RpPlayback.time(entry, anim, tickDelta);
        boolean mobile = RpPlayback.mobile(entry);
        boolean first = RpPlayback.firstPerson(entity);
        Matrix4f[] world = scene.geo().pose(anim, t);
        String prefix = entry.role() == RpServer.ROLE_TARGET ? "t_" : "";
        Matrix4f base = mobile && first ? view(entity, anim, t, tickDelta) : RpPlayback.base(entity, scene, entry, tickDelta);
        float[] offset = RpPlayback.offset(scene, entry);
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < PARTS.length; i++) {
            boolean arm = parts[i] == model.rightArm || parts[i] == model.leftArm;
            if (entry.hold() && !arm) continue;
            if (mobile && !arm && (first || parts[i] != model.head)) continue;
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
            if (first) {
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

    private static Vector3f eye(RpAnim anim, float t) {
        float[] eye = anim.sample("cam_eye", RpAnim.POSITION, t, new float[3]);
        if (eye != null) return new Vector3f(eye[0], eye[1], eye[2]);
        Vector3f standard = new Vector3f(0, 28, 0);
        float[] root = anim.sample("player", RpAnim.POSITION, t, new float[3]);
        if (root != null) standard.add(root[0], root[1], root[2]);
        return standard;
    }

    private static float scenePitch(RpAnim anim, float t) {
        float[] cam = anim.sample("cam", RpAnim.ROTATION, t, new float[3]);
        if (cam != null) return cam[0];
        float[] head = anim.sample("head", RpAnim.ROTATION, t, new float[3]);
        return LOOK_DOWN + (head != null ? head[0] : 0);
    }

    static Matrix4f view(LivingEntity entity, RpAnim anim, float t, float tickDelta) {
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        float turn = MathHelper.wrapDegrees(entity.getYaw(tickDelta) - bodyYaw);
        float pitch = scenePitch(anim, t) - entity.getPitch(tickDelta);
        Vector3f eye = eye(anim, t);
        return new Matrix4f().rotateY((float) Math.toRadians(turn))
                .translate(eye).rotateX((float) Math.toRadians(pitch)).translate(-eye.x, -eye.y, -eye.z);
    }

    public static Shot camera(Entity entity, float tickDelta) {
        if (!(entity instanceof LivingEntity living) || !RpPlayback.firstPerson(entity)) return null;
        RpPlayback.Entry entry = RpPlayback.of(entity);
        RpScenes.Scene scene = RpScenes.get(entry.item());
        RpAnim anim = RpPlayback.animation(entity, entry, scene);
        if (anim == null) return null;
        float t = RpPlayback.time(entry, anim, tickDelta);
        Vector3f eye = eye(anim, t), look = null;
        if (RpPlayback.mobile(entry)) {
            float yaw = living.getYaw(tickDelta);
            Vector3f at = rawToWorld(living, yaw).transformPosition(eye);
            return new Shot(living.getLerpedPos(tickDelta).add(at.x, at.y, at.z), yaw, living.getPitch(tickDelta));
        }
        float pitch = scenePitch(anim, t);
        float[] lookBone = anim.sample("cam_look", RpAnim.POSITION, t, new float[3]);
        float[] cam = anim.sample("cam", RpAnim.ROTATION, t, new float[3]);
        boolean eyeBone = anim.sample("cam_eye", RpAnim.POSITION, t, new float[3]) != null;
        if (eyeBone && lookBone != null) {
            look = new Vector3f(lookBone[0], lookBone[1], lookBone[2]);
        } else if (eyeBone && cam != null) {
            double p = Math.toRadians(cam[0]), y = Math.toRadians(cam[1]);
            look = new Vector3f(eye).add((float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (-Math.cos(y) * Math.cos(p)));
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
