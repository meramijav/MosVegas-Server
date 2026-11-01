package ru.mosvegas.items;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rigid placement in block units. Rigid poses keep vanilla's normal matrix correct when applied. */
public record Pose(Vector3f position, Quaternionf rotation) {
    public static final Pose IDENTITY = new Pose(new Vector3f(), new Quaternionf());

    /** Same frame as ModelPart.rotate, which renders the part's cuboids. */
    public static Pose of(ModelPart part) {
        return new Pose(new Vector3f(part.pivotX, part.pivotY, part.pivotZ).div(16),
                new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch));
    }

    /** Rotation whose local +Y and +Z axes point along the given directions (Z is made perpendicular). */
    public static Quaternionf basis(Vector3f y, Vector3f z) {
        Vector3f up = new Vector3f(y).normalize();
        Vector3f back = new Vector3f(z).sub(new Vector3f(up).mul(up.dot(z))).normalize();
        Vector3f side = new Vector3f(up).cross(back);
        return new Matrix3f(side, up, back).getNormalizedRotation(new Quaternionf());
    }

    /** Places local point {@code local} at {@code point} with the given rotation. */
    public static Pose holding(Vector3f point, Quaternionf rotation, Vector3f local) {
        return new Pose(new Vector3f(point).sub(rotation.transform(new Vector3f(local))), rotation);
    }

    public Vector3f transform(Vector3f local) {
        return rotation.transform(new Vector3f(local)).add(position);
    }

    public Pose then(Pose child) {
        return new Pose(transform(child.position), new Quaternionf(rotation).mul(child.rotation));
    }

    /** Moves the held point in a straight line and turns the item around it. */
    public static Pose blend(Pose from, Pose to, float progress, Vector3f heldPoint) {
        if (progress <= 0) return from;
        if (progress >= 1) return to;
        Vector3f point = from.transform(heldPoint).lerp(to.transform(heldPoint), progress);
        Quaternionf rotation = new Quaternionf(from.rotation).slerp(to.rotation, progress);
        return holding(point, rotation, heldPoint);
    }

    public void apply(MatrixStack matrices) {
        matrices.translate(position.x, position.y, position.z);
        matrices.multiply(rotation);
    }

    public static Quaternionf degrees(float x, float y, float z) {
        return new Quaternionf().rotationXYZ(x * MathHelper.RADIANS_PER_DEGREE,
                y * MathHelper.RADIANS_PER_DEGREE, z * MathHelper.RADIANS_PER_DEGREE);
    }
}
