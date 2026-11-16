package ru.mosvegas.items.rp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RpPlayback {
    public record Entry(String item, String animation, int role, double start, boolean hold, float yaw, int partner) {}

    private static final Map<Integer, Entry> ACTIVE = new ConcurrentHashMap<>();

    private RpPlayback() {}

    static void play(int entityId, String item, String animation, int role, int elapsed, boolean hold, float yaw, int partner) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return;
        ACTIVE.put(entityId, new Entry(item, animation, role, world.getTime() - elapsed, hold, yaw, partner));
    }

    static void clear(int entityId) {
        ACTIVE.remove(entityId);
    }

    public static Entry of(Entity entity) {
        return entity == null ? null : ACTIVE.get(entity.getId());
    }

    public static boolean acting(Entity entity) {
        Entry entry = of(entity);
        return entry != null && entry.role() == RpServer.ROLE_ACTOR;
    }

    public static boolean firstPerson(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == null || entity != client.player || client.options.getPerspective() != Perspective.FIRST_PERSON) return false;
        Entry entry = of(entity);
        if (entry == null || entry.role() != RpServer.ROLE_ACTOR || entry.hold()) return false;
        RpScenes.Scene scene = RpScenes.get(entry.item());
        if (scene == null) return false;

        return scene.animation(entry.animation()) != null;
    }

    public static RpAnim animation(Entity entity, Entry entry, RpScenes.Scene scene) {
        String name = entry.animation();
        if (firstPerson(entity) && name.endsWith("_3p")) {
            RpAnim first = scene.animation(name.substring(0, name.length() - 3) + "_1p");
            if (first != null) return first;
        }
        return scene.animation(name);
    }

    public static float time(Entry entry, RpAnim anim, float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        float t = world == null ? 0 : (float) ((world.getTime() - entry.start() + (double) tickDelta) / 20.0);
        return Math.max(0, Math.min(anim.length, t));
    }

    public static Matrix4f base(Entity entity, RpScenes.Scene scene, Entry entry, float tickDelta) {
        if (entry.role() != RpServer.ROLE_TARGET) return new Matrix4f();
        var world = MinecraftClient.getInstance().world;
        Entity partner = world == null || entry.partner() < 0 ? null : world.getEntityById(entry.partner());
        if (!entry.hold() && partner instanceof LivingEntity actor && entity instanceof LivingEntity target) {
            Matrix4f actorSpace = place(actor, tickDelta);
            return place(target, tickDelta).invert().mul(actorSpace);
        }
        RpGeo.Bone root = scene.geo().byName.get("t_player");
        if (root == null) return new Matrix4f();
        float[] pivot = root.pivot;
        Matrix4f placement = new Matrix4f().translate(pivot[0], pivot[1], pivot[2]);
        if (Math.abs(root.rotation[0]) < 1e-3f && Math.abs(root.rotation[2]) < 1e-3f) {
            RpGeo.rotateBedrock(placement, 0, root.rotation[1], 0);
        }
        return placement.invert();
    }

    static Matrix4f place(LivingEntity entity, float tickDelta) {
        var pos = entity.getLerpedPos(tickDelta);
        float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        return new Matrix4f().translate((float) pos.x, (float) pos.y, (float) pos.z).mul(RpPoser.rawToWorld(entity, yaw));
    }

    public static float[] offset(RpScenes.Scene scene, Entry entry) {
        if (entry.role() != RpServer.ROLE_TARGET) return new float[]{0, 0, 0};
        RpGeo.Bone root = scene.geo().byName.get("t_player");
        return root == null ? new float[]{0, 0, 0} : root.pivot;
    }

    static void tick(MinecraftClient client) {
        if (client.world == null) {
            ACTIVE.clear();
            return;
        }
        for (var it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity entity = client.world.getEntityById(e.getKey());
            Entry entry = e.getValue();
            RpScenes.Scene scene = RpScenes.get(entry.item());
            RpAnim anim = scene == null ? null : scene.animation(entry.animation());
            double age = (client.world.getTime() - entry.start()) / 20.0;
            if (entity == null && age > 30 || anim == null || !entry.hold() && age > anim.length + 0.1) {
                it.remove();
                continue;
            }
            if (entity instanceof LivingEntity living && !entry.hold()) {
                living.bodyYaw = entry.yaw();
                living.prevBodyYaw = entry.yaw();
                if (living != client.player) {
                    living.headYaw = entry.yaw();
                    living.prevHeadYaw = entry.yaw();
                }
            }
        }
    }
}
