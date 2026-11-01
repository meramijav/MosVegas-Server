package studio.merami.slimcore.entities.components;

import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.PoseReplay;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.SyncedValue;

import java.util.List;

public abstract class AEntityB_Existing extends AEntityA_Base {
    private static final SyncedValue<Float> HEADING = SyncedValue.decimal(AEntityB_Existing.class, 0f);
    private static final SyncedValue<Integer> POSE_TICK = SyncedValue.integer(AEntityB_Existing.class, 0);
    private static final double TELEPORT = 64;

    private final PoseReplay replay = new PoseReplay();
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private Point3D stampedPosition;
    private float stampedYaw;

    protected AEntityB_Existing(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    public int poseTick() {
        return data.get(POSE_TICK);
    }

    public double scale() {
        return 1;
    }

    public Point3D position() {
        return host.position();
    }

    public Point3D position(float partial) {
        return host.position(partial);
    }

    public double x() {
        return host.x();
    }

    public double y() {
        return host.y();
    }

    public double z() {
        return host.z();
    }

    public void setPosition(Point3D position) {
        host.setPosition(position.x, position.y, position.z);
    }

    public void setPosition(double x, double y, double z) {
        host.setPosition(x, y, z);
    }

    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        host.moveTo(x, y, z, yaw, pitch);
    }

    public float yaw() {
        return host.yaw();
    }

    public float prevYaw() {
        return host.prevYaw();
    }

    public void setYaw(float yaw) {
        host.setYaw(yaw);
    }

    public BoundingBox bounds() {
        return host.bounds();
    }

    public Point3D motion() {
        return host.velocity();
    }

    public void setMotion(Point3D motion) {
        host.setVelocity(motion);
    }

    public void setMotion(double x, double y, double z) {
        host.setVelocity(new Point3D(x, y, z));
    }

    public boolean onGround() {
        return host.onGround();
    }

    public void setOnGround(boolean onGround) {
        host.setOnGround(onGround);
    }

    public double distanceToSqr(Point3D point) {
        return host.distanceToSqr(point);
    }

    public Point3D localToWorld(Point3D local) {
        return local.scale(scale()).yRot((float) -Math.toRadians(yaw())).add(position());
    }

    public Point3D worldToLocal(Point3D world) {
        return ChassisGeometry.toLocal(world, position(), yaw(), scale());
    }

    public Point3D worldToLocal(Point3D world, float partial) {
        return ChassisGeometry.toLocal(world, position(partial), SlimMath.rotLerp(partial, prevYaw(), yaw()), scale());
    }

    public Point3D directionToLocal(Point3D world) {
        return world.yRot((float) Math.toRadians(yaw()));
    }

    public Point3D directionToWorld(Point3D local) {
        return local.yRot((float) -Math.toRadians(yaw()));
    }

    protected void replayServerPose() {
        PoseReplay.Pose pose = replay.next();
        if (pose == null) return;
        boolean teleport = distanceToSqr(new Point3D(pose.x(), pose.y(), pose.z())) > TELEPORT * TELEPORT;
        setPosition(pose.x(), pose.y(), pose.z());
        setYaw(pose.yaw());
        if (teleport) host.setOldPosAndRot();
    }

    protected void syncHeading() {
        data.set(HEADING, yaw());
    }

    protected void stampPose() {
        if (stampedPosition == null || stampedPosition.distanceToSqr(position()) > 1e-8 || Math.abs(SlimMath.wrapDegrees(yaw() - stampedYaw)) > 1e-3) {
            stampedPosition = position();
            stampedYaw = yaw();
            data.set(POSE_TICK, ticks());
        }
    }

    public void onMovedTo(double x, double y, double z) {
        if (world().isClient()) {
            lerpX = x;
            lerpY = y;
            lerpZ = z;
            replay.clear();
        }
    }

    public void lerpTo(double x, double y, double z) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
    }

    public double lerpTargetX() {
        return lerpX;
    }

    public double lerpTargetY() {
        return lerpY;
    }

    public double lerpTargetZ() {
        return lerpZ;
    }

    @Override
    public void onSyncedDataUpdated(List<SyncedValue<?>> keys) {
        super.onSyncedDataUpdated(keys);
        if (!world().isClient()) return;
        for (SyncedValue<?> key : keys) {
            if (key == POSE_TICK) {
                replay.add(data.get(POSE_TICK), new PoseReplay.Pose(lerpX, lerpY, lerpZ, data.get(HEADING)),
                        new PoseReplay.Pose(x(), y(), z(), yaw()));
                break;
            }
        }
    }
}
