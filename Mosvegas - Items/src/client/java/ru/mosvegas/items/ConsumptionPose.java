package ru.mosvegas.items;

import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

/** Model-part angles and the burning cigarette shared by every renderer. */
public final class ConsumptionPose {
    private ConsumptionPose() {}

    public static Vector3f modelAngles(Quaternionf q) {
        // Minecraft 1.20.4 bundles JOML 1.10.5, whose getEulerAnglesZYX has an
        // incorrect sign in the pitch denominator. Use the normalized formula.
        q.normalize();
        return new Vector3f(
                (float) Math.atan2(2 * (q.w * q.x + q.y * q.z), 1 - 2 * (q.x * q.x + q.y * q.y)),
                (float) Math.asin(MathHelper.clamp(2 * (q.w * q.y - q.z * q.x), -1, 1)),
                (float) Math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.y * q.y + q.z * q.z)));
    }

    public static float remainingFraction(int remainingUses, int totalUses, float usedPortion) {
        return MathHelper.clamp((remainingUses - MathHelper.clamp(usedPortion, 0, 1)) / Math.max(1, totalUses), 0, 1);
    }

    /** Top of the ember in model pixels for the given remaining paper. */
    public static float emberTop(float fraction) {
        return 7 - 5.5f * (1 - fraction);
    }

    public static void applyCigaretteBurn(GeoBone bone, float fraction) {
        if (bone.getName().equals("paper")) bone.setScaleY(bone.getScaleY() * Math.max(0.001f, fraction));
        else if (bone.getName().equals("ember")) bone.setPosY(bone.getPosY() - 5.5f * (1 - fraction));
    }
}
