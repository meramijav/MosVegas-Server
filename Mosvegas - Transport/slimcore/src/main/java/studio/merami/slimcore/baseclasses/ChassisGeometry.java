package studio.merami.slimcore.baseclasses;

import studio.merami.slimcore.jsondefs.AJSONMultipart;

import java.util.List;

public final class ChassisGeometry {
    public static final float RIDER_SCALE = .70f;
    public static final double PUNCTURE_DROP = .105;

    public record Pose(double pitch, double roll, double heave) {}

    private ChassisGeometry() {}

    public static float riderModelScale(AJSONMultipart definition) {
        return .9375f * (float) definition.rider.scale;
    }

    public static double riderHipHeight(AJSONMultipart definition) {
        return .751 * riderModelScale(definition);
    }

    public static double riderThighRadius(AJSONMultipart definition) {
        return .125 * riderModelScale(definition);
    }

    public static float seatedEyes(AJSONMultipart definition) {
        return 1.82f * riderModelScale(definition);
    }

    public static Point3D toLocal(Point3D world, Point3D position, float yaw, double scale) {
        return world.subtract(position).yRot((float) Math.toRadians(yaw)).scale(1 / scale);
    }

    public static Point3D levelled(Point3D local, double bodyPitch, double leanPitch, double roll, float yaw) {
        Point3D v = local.xRot((float) -(bodyPitch - leanPitch));
        double c = Math.cos(roll);
        double s = Math.sin(roll);
        v = new Point3D(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
        return v.yRot((float) -Math.toRadians(yaw)).normalize();
    }

    public static Pose wheelPlane(AJSONMultipart definition, double[] offsets) {
        List<AJSONMultipart.Wheel> wheels = definition.wheels;
        double front = 0;
        double rear = 0;
        double left = 0;
        double right = 0;
        double sum = 0;
        int frontCount = 0;
        int rearCount = 0;
        int leftCount = 0;
        int rightCount = 0;
        for (int i = 0; i < wheels.size(); i++) {
            Point3D center = wheels.get(i).pos;
            sum += offsets[i];
            if (center.z > definition.meanWheelZ) {
                front += offsets[i];
                frontCount++;
            } else {
                rear += offsets[i];
                rearCount++;
            }
            if (center.x > 1e-6) {
                left += offsets[i];
                leftCount++;
            } else if (center.x < -1e-6) {
                right += offsets[i];
                rightCount++;
            }
        }
        double pitch = frontCount > 0 && rearCount > 0
                ? Math.asin(Math.max(-.35, Math.min(.35, (rear / rearCount - front / frontCount) / definition.axleSpan))) : 0;
        double roll = leftCount > 0 && rightCount > 0
                ? Math.asin(Math.max(-.45, Math.min(.45, (left / leftCount - right / rightCount) / definition.track))) : 0;
        return new Pose(pitch, roll, sum / wheels.size() + definition.meanWheelZ * Math.sin(pitch));
    }

    public static double bodyAtWheel(AJSONMultipart definition, Pose pose, int wheel) {
        Point3D center = definition.wheels.get(wheel).pos;
        return pose.heave() - center.z * Math.sin(pose.pitch()) + center.x * Math.sin(pose.roll());
    }

    public static boolean overlaps(Point3D center, float yaw, double halfWidth, double halfLength, double low, double high, BoundingBox box) {
        if (center.y + high <= box.minY + 1e-5 || center.y + low >= box.maxY - 1e-5) return false;
        double c = Math.cos(Math.toRadians(yaw));
        double s = Math.sin(Math.toRadians(yaw));
        double dx = box.center().x - center.x;
        double dz = box.center().z - center.z;
        double bx = box.sizeX() / 2;
        double bz = box.sizeZ() / 2;
        return Math.abs(dx) < Math.abs(c) * halfWidth + Math.abs(s) * halfLength + bx - .001
                && Math.abs(dz) < Math.abs(s) * halfWidth + Math.abs(c) * halfLength + bz - .001
                && Math.abs(dx * c + dz * s) < halfWidth + bx * Math.abs(c) + bz * Math.abs(s) - .001
                && Math.abs(-dx * s + dz * c) < halfLength + bx * Math.abs(s) + bz * Math.abs(c) - .001;
    }

    public static BoundingBox bounds(Point3D position, float yaw, double halfWidth, double halfLength, double height) {
        double c = Math.abs(Math.cos(Math.toRadians(yaw)));
        double s = Math.abs(Math.sin(Math.toRadians(yaw)));
        double x = c * halfWidth + s * halfLength;
        double z = s * halfWidth + c * halfLength;
        return new BoundingBox(position.x - x, position.y, position.z - z, position.x + x, position.y + height, position.z + z);
    }
}
