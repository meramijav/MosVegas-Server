package ru.mosvegas.character.client.render;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import ru.mosvegas.character.data.Appearance;

public final class BodyShape {
    private static final float[][] BUILDS = {
            {0.90f, 0.90f, 0.86f, 0.90f, 0.92f, 0.92f},
            {1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f},
            {1.10f, 1.06f, 1.16f, 1.12f, 1.05f, 1.05f},
            {1.18f, 1.26f, 1.16f, 1.16f, 1.12f, 1.14f},
    };

    private BodyShape() {}

    public static void apply(PlayerEntityModel<?> m, Appearance a) {
        float[] b = a == null ? BUILDS[1] : BUILDS[Math.floorMod(a.build, BUILDS.length)];
        float bx = b[0], bz = b[1], ax = b[2], az = b[3], lx = b[4], lz = b[5];
        if (a != null && a.gender == Appearance.FEMALE) {
            bx *= 0.94f;
            lx *= 1.02f;
        }
        m.body.xScale = bx;
        m.body.zScale = bz;
        m.rightArm.xScale = ax;
        m.rightArm.zScale = az;
        m.leftArm.xScale = ax;
        m.leftArm.zScale = az;
        if (bx != 1f || ax != 1f) {
            float k = (4f * bx + ax) / 5f;
            m.rightArm.pivotX *= k;
            m.rightArm.pivotZ *= k;
            m.leftArm.pivotX *= k;
            m.leftArm.pivotZ *= k;
        }
        m.rightLeg.xScale = lx;
        m.rightLeg.zScale = lz;
        m.leftLeg.xScale = lx;
        m.leftLeg.zScale = lz;
        float legPivot = 2f * lx - 0.1f;
        m.rightLeg.pivotX = -legPivot;
        m.leftLeg.pivotX = legPivot;
        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }

    public static float bust(Appearance a) {
        if (a == null || a.gender != Appearance.FEMALE) return 0f;
        return switch (Math.floorMod(a.build, BUILDS.length)) {
            case 0 -> 0.75f;
            case 3 -> 1.25f;
            default -> 1f;
        };
    }

    public static float bodyDepth(Appearance a) {
        return a == null ? 1f : BUILDS[Math.floorMod(a.build, BUILDS.length)][1];
    }
}
