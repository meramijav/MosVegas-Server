package ru.mosvegas.casino.client.dealer;

import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * World positions of the dealers' palms from the last rendered frame (GeckoLib bones {@code leftHand} /
 * {@code rightHand}). Entities are rendered before block entities, so the table overlay reads the pose of the
 * same frame and can put cards and chips right into the hands.
 */
public final class DealerHands {
    public record Pose(Vec3d left, Vec3d right, float time) {
        public Vec3d hand(boolean leftHand) { return leftHand ? left : right; }
    }

    private static final Map<Integer, Pose> POSES = new HashMap<>();

    private DealerHands() {}

    public static void update(int entityId, Vec3d left, Vec3d right, float time) {
        POSES.put(entityId, new Pose(left, right, time));
    }

    /** Pose of a dealer rendered within the last couple of ticks, or null (culled, unloaded, other dimension). */
    public static Pose get(int entityId, float now) {
        Pose p = POSES.get(entityId);
        return p != null && now - p.time() <= 2f && now >= p.time() - 1f ? p : null;
    }

    public static void clear() { POSES.clear(); }
}
