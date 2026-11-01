package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.HullCollision;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.physics.Suspension;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.sliminterface.SyncedValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AEntityVehicleD_Moving extends AEntityVehicleC_Colliding {
    public static final int BRAKING = 8;
    public static final int TURN_LEFT = 64;
    public static final int TURN_RIGHT = 128;
    private static final SyncedValue<Float> STEER = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final SyncedValue<Float> SPEED = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final SyncedValue<Float> SLIP = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final SyncedValue<Float> PITCH = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final SyncedValue<Float> ROLL = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final List<SyncedValue<Float>> WHEEL_HEIGHTS = new ArrayList<>();
    private static final float WHEEL_DROOP = .04f;
    private static final float WHEEL_BUMP = .28f;
    private static final int ZONE_RECHECK = 20;
    private static final int ZONE_NOTICE = 40;

    static {
        for (int i = 0; i < AJSONMultipart.MAX_WHEELS; i++) WHEEL_HEIGHTS.add(SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f));
    }

    protected DrivePhysics.Input input = DrivePhysics.IDLE;
    protected int inputTick;
    protected float brakePressure;
    private boolean wasHandbrake;
    private final Suspension suspension = new Suspension();
    private final double[] supportCache = new double[AJSONMultipart.MAX_WHEELS];
    private boolean supportCacheReady;
    private int impactTick = -100;
    private int zoneDriver = -1;
    private int[] zoneCells;
    private int zoneTick;
    private int zoneNoticeTick = -ZONE_NOTICE;
    private boolean zoneAllowed = true;
    public float bodyPitch;
    public float oldBodyPitch;
    public float bodyRoll;
    public float oldBodyRoll;
    public float bodyHeave;
    public float oldBodyHeave;
    public float leanPitch;
    public float oldLeanPitch;
    public float wheelRoll;
    public float oldWheelRoll;
    public float visualSteer;
    public float oldVisualSteer;
    private boolean visualsReady;
    private int restingTicks;
    private double restX;
    private double restY;
    private double restZ;
    private float restYaw;

    protected AEntityVehicleD_Moving(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    @Override
    public float speed() {
        return data.get(SPEED);
    }

    public float slip() {
        return data.get(SLIP);
    }

    protected void setSlip(float slip) {
        data.set(SLIP, slip);
    }

    public void acceptInput(IWrapperPlayer player, float throttle, float brake, float steer, boolean handbrake) {
        if (!driver(player) || !Float.isFinite(throttle) || !Float.isFinite(brake) || !Float.isFinite(steer)) return;
        input = new DrivePhysics.Input(SlimMath.clamp(throttle, 0, 1), SlimMath.clamp(brake, 0, 1), SlimMath.clamp(steer, -1, 1), handbrake);
        inputTick = ticks();
    }

    public double steeringPull() {
        double pull = 0;
        for (AJSONInteractable.Zone zone : definition().damage.zones) {
            if (zone.pull != 0) pull += zone.pull * zoneDamage(zone.index) / DamageModel.FULL;
        }
        return pull * DamageModel.PULL;
    }

    @Override
    public Point3D chassisToWorld(Point3D local) {
        Point3D pivot = new Point3D(0, definition().bodyPivot, 0);
        Point3D v = local.subtract(pivot).xRot(-bodyPitch);
        double c = Math.cos(bodyRoll);
        double s = Math.sin(bodyRoll);
        v = new Point3D(v.x * c - v.y * s, v.x * s + v.y * c, v.z).add(pivot).add(0, bodyHeave, 0);
        return localToWorld(v);
    }

    public Point3D chassisToWorld(Point3D local, float partial) {
        float pitch = SlimMath.lerp(partial, oldBodyPitch, bodyPitch);
        float roll = SlimMath.lerp(partial, oldBodyRoll, bodyRoll);
        Point3D pivot = new Point3D(0, definition().bodyPivot, 0);
        Point3D v = local.subtract(pivot).xRot(-pitch);
        double c = Math.cos(roll);
        double s = Math.sin(roll);
        v = new Point3D(v.x * c - v.y * s, v.x * s + v.y * c, v.z).add(pivot).add(0, SlimMath.lerp(partial, oldBodyHeave, bodyHeave), 0);
        return v.scale(scale()).yRot((float) -Math.toRadians(SlimMath.rotLerp(partial, prevYaw(), yaw()))).add(position(partial));
    }

    public Point3D levelledDirection(Point3D local, float partial) {
        return ChassisGeometry.levelled(local, SlimMath.lerp(partial, oldBodyPitch, bodyPitch), SlimMath.lerp(partial, oldLeanPitch, leanPitch),
                SlimMath.lerp(partial, oldBodyRoll, bodyRoll), SlimMath.rotLerp(partial, prevYaw(), yaw()));
    }

    protected void tickVisualMotion() {
        oldVisualSteer = visualSteer;
        visualSteer = SlimMath.lerp(.30f, visualSteer, data.get(STEER));
        oldWheelRoll = wheelRoll;
        wheelRoll += speed() / (float) (wheel(0).definition.rollRadius * scale());
    }

    protected void tickClientMotion() {
        double beforeY = y();
        replayServerPose();
        updateSuspensionVisuals(beforeY, lerpTargetY());
        host.refreshBounds();
    }

    protected boolean driveAllowed() {
        IWrapperEntity driver = controllingPassenger();
        IWrapperPlayer player = driver == null ? null : driver.asPlayer();
        if (player == null) return true;
        BoundingBox box = bounds();
        int[] cells = {SlimMath.floor(box.minX), SlimMath.floor(box.minY), SlimMath.floor(box.minZ),
                SlimMath.floor(box.maxX), SlimMath.floor(box.maxY), SlimMath.floor(box.maxZ)};
        if (player.id() != zoneDriver || !Arrays.equals(cells, zoneCells) || ticks() - zoneTick >= ZONE_RECHECK) {
            zoneDriver = player.id();
            zoneCells = cells;
            zoneTick = ticks();
            zoneAllowed = world().allows(player, IWrapperWorld.Access.USE, this);
        }
        return zoneAllowed;
    }

    protected void zoneNotice() {
        IWrapperEntity driver = controllingPassenger();
        IWrapperPlayer player = driver == null ? null : driver.asPlayer();
        if (player == null || ticks() - zoneNoticeTick < ZONE_NOTICE) return;
        zoneNoticeTick = ticks();
        message(player, "message.slimvehicle.zone.use");
    }

    protected DrivePhysics.Input tickControls() {
        if (controllingPassenger() == null || ticks() - inputTick > 10) input = DrivePhysics.IDLE;
        if (exiting()) input = new DrivePhysics.Input(0, 1, 0, false);
        if (!driveAllowed()) {
            if (input.throttle() > .05 || input.steer() != 0) zoneNotice();
            input = new DrivePhysics.Input(0, 1, 0, false);
        }
        brakePressure = DrivePhysics.brakePressure(brakePressure, (float) input.brake());
        return new DrivePhysics.Input(input.brake() > .05 ? 0 : input.throttle(),
                controllingPassenger() == null ? 1 : brakePressure, input.steer(), input.handbrake());
    }

    protected float tickSteering() {
        JSONVehicle definition = definition();
        double pull = Math.abs(forward) > .02 ? steeringPull() : 0;
        float target = (float) (input.steer() * (definition.physics.steerLock / (1 + Math.abs(forward) * definition.physics.steerFalloff)) + pull);
        float steer = SlimMath.approach(data.get(STEER), target, input.steer() == 0 ? .042f : .05f);
        if (wasHandbrake != input.handbrake()) {
            emitSound(input.handbrake() ? definition.sounds.handbrakeOn : definition.sounds.handbrakeOff, 1);
            wasHandbrake = input.handbrake();
        }
        data.set(STEER, steer);
        return steer;
    }

    protected void tickMotion(double yawDelta, double lastForward, double lastLateral, double lastY) {
        setFlag(TURN_LEFT, controllingPassenger() != null && input.steer() > .2);
        setFlag(TURN_RIGHT, controllingPassenger() != null && input.steer() < -.2);
        float oldYaw = yaw();
        Point3D start = position();
        boolean allowedBefore = driveAllowed();
        float nextYaw = oldYaw + (float) yawDelta;
        boolean turning = Math.abs(nextYaw - oldYaw) > 1e-5;
        HullCollision.World world = turning || Math.abs(forward) + Math.abs(lateral) > 1e-6 ? surroundings(nextYaw) : null;
        if (turning && !turn(nextYaw, world)) {
            lateral = 0;
            drift = new DrivePhysics.Drift(0, drift.loss());
        }
        syncHeading();
        Point3D horizontal = velocity();
        Point3D moved = world == null ? horizontal : slide(horizontal, world);
        if (moved != horizontal) {
            setVelocity(moved);
            horizontal = moved;
        }
        double previousSpeed = Math.hypot(lastForward, lastLateral);
        double currentSpeed = Math.hypot(forward, lateral);
        setFlag(BRAKING, input.brake() > .05 || brakePressure > .025 || input.handbrake()
                || previousSpeed > .035 && currentSpeed < previousSpeed - .0003);
        double vy = Math.max(-1.2, motion().y - .08);
        boolean airborne = !onGround();
        moveVertically(vy);
        if (airborne && onGround()) land(-vy);
        setMotion(horizontal.x, onGround() ? 0 : vy, horizontal.z);
        data.set(SPEED, (float) forward);
        host.refreshBounds();
        if (allowedBefore && !driveAllowed()) {
            stopAtZone(start, oldYaw);
            horizontal = Point3D.ZERO;
        }
        tickWheels(oldYaw, lastForward, lastLateral, lastY);
        boolean moving = Math.abs(forward) + Math.abs(lateral) > .02;
        if (moving) strike(horizontal);
        if (moving || ticks() % 5 == 0) {
            for (IWrapperEntity entity : world().entities(host, bounds())) {
                if (entity.isLiving() && entity.asPlayer() == null) pushOut(entity);
            }
        }
        if (ticks() % 100 == 0) forgetStruck();
        stampPose();
    }

    private void stopAtZone(Point3D start, float yaw) {
        setPosition(start);
        setYaw(yaw);
        syncHeading();
        forward = 0;
        lateral = 0;
        drift = new DrivePhysics.Drift(0, drift.loss());
        setMotion(0, 0, 0);
        data.set(SPEED, 0f);
        host.refreshBounds();
        zoneNotice();
    }

    private void tickWheels(float oldYaw, double lastForward, double lastLateral, double lastY) {
        JSONVehicle definition = definition();
        int count = wheels().size();
        double impact = 0;
        for (int i = 0; i < count; i++) {
            double height = sampleWheelGround(i);
            if (supportCacheReady) impact = Math.max(impact, Math.abs(height - supportCache[i]));
            supportCache[i] = height;
        }
        supportCacheReady = true;
        double scale = definition.scale;
        double[] sorted = Arrays.copyOf(supportCache, count);
        Arrays.sort(sorted);
        double road = count % 2 == 0 ? (sorted[count / 2 - 1] + sorted[count / 2]) * .5 : sorted[count / 2];
        double[] heights = new double[count];
        boolean grounded = false;
        for (int i = 0; i < count; i++) {
            double relative = (Math.max(supportCache[i], road - .20 * scale) - y()) / scale - .01;
            grounded |= relative > -.06;
            heights[i] = SlimMath.clamp(relative, -.62, .45);
        }
        for (int i = 0; i < count; i++) {
            if (!grounded) heights[i] = -.03;
            if (punctured(i)) heights[i] -= ChassisGeometry.PUNCTURE_DROP;
            data.set(WHEEL_HEIGHTS.get(i), (float) heights[i]);
        }
        double lateralAcceleration = -Math.toRadians(SlimMath.wrapDegrees(yaw() - oldYaw)) * forward + (lateral - lastLateral);
        suspension.carry(definition.massRatio, definition.size.height);
        suspension.step(forward - lastForward, lateralAcceleration, grounded);
        data.set(PITCH, (float) suspension.pitch);
        data.set(ROLL, (float) suspension.roll);
        updateSuspensionVisuals(lastY, y());
        if (impact > .10 && Math.abs(speed()) > .025 && ticks() - impactTick > 7) {
            emitSound(definition.sounds.bump, 1);
            impactTick = ticks();
        }
    }

    private void updateSuspensionVisuals(double previousY, double authoritativeY) {
        if (visualsReady && x() == restX && y() == restY && z() == restZ && yaw() == restYaw && previousY == y() && authoritativeY == y()) {
            if (++restingTicks > 20) {
                keepVisuals();
                return;
            }
        } else {
            restingTicks = 0;
            restX = x();
            restY = y();
            restZ = z();
            restYaw = yaw();
        }
        settleSuspension(previousY, authoritativeY);
    }

    private void keepVisuals() {
        oldBodyPitch = bodyPitch;
        oldBodyRoll = bodyRoll;
        oldBodyHeave = bodyHeave;
        oldLeanPitch = leanPitch;
        for (PartGroundDevice wheel : wheels()) wheel.oldTravel = wheel.travel;
    }

    private void settleSuspension(double previousY, double authoritativeY) {
        JSONVehicle definition = definition();
        List<PartGroundDevice> wheels = wheels();
        int count = wheels.size();
        float moved = (float) ((y() - previousY) / definition.scale);
        float correction = (float) ((authoritativeY - y()) / definition.scale);
        keepVisuals();
        double[] offsets = new double[count];
        for (int i = 0; i < count; i++) {
            PartGroundDevice wheel = wheels.get(i);
            float target = data.get(WHEEL_HEIGHTS.get(i)) + correction;
            float current = wheel.travel - moved;
            if (!visualsReady) current = target;
            else if (current < target) current = Math.max(SlimMath.lerp(.6f, current, target), target - .15f);
            else current = SlimMath.lerp(.45f, current, target);
            offsets[i] = wheel.travel = current;
        }
        ChassisGeometry.Pose plane = ChassisGeometry.wheelPlane(definition, offsets);
        float pitch = (float) plane.pitch() + data.get(PITCH);
        float roll = (float) plane.roll() + data.get(ROLL);
        float heave = (float) plane.heave();
        leanPitch = visualsReady ? SlimMath.lerp(.5f, leanPitch, data.get(PITCH)) : data.get(PITCH);
        if (!visualsReady) {
            bodyPitch = pitch;
            bodyRoll = roll;
            bodyHeave = heave;
        } else {
            bodyPitch = SlimMath.clamp(SlimMath.lerp(.5f, bodyPitch, pitch), pitch - .012f, pitch + .012f);
            bodyRoll = SlimMath.clamp(SlimMath.lerp(.5f, bodyRoll, roll), roll - .015f, roll + .015f);
            bodyHeave = SlimMath.clamp(SlimMath.lerp(.5f, bodyHeave - moved, heave), heave - .025f, heave + .025f);
        }
        for (int end = 0; end < 2 && !definition.underbody.isEmpty(); end++) {
            double deficit = 0;
            double tip = 0;
            for (Point3D point : definition.underbody) {
                if ((point.z > 0) != (end == 0)) continue;
                tip = end == 0 ? Math.max(tip, point.z) : Math.min(tip, point.z);
                double floor = (sampleGround(point.x, point.z, .12) - y()) / definition.scale - point.y;
                double at = bodyHeave - point.z * Math.sin(bodyPitch) + point.x * Math.sin(bodyRoll);
                deficit = Math.max(deficit, floor - at);
            }
            if (deficit <= 0) continue;
            double axle = end == 0 ? definition.rearAxle : definition.frontAxle;
            double sinStep = -deficit / (tip - axle);
            bodyPitch = (float) Math.asin(SlimMath.clamp(Math.sin(bodyPitch) + sinStep, -.5, .5));
            bodyHeave += (float) (axle * sinStep);
        }
        ChassisGeometry.Pose body = new ChassisGeometry.Pose(bodyPitch, bodyRoll, bodyHeave);
        for (int i = 0; i < count; i++) {
            float arch = (float) ChassisGeometry.bodyAtWheel(definition, body, i);
            wheels.get(i).travel = SlimMath.clamp(wheels.get(i).travel, arch - WHEEL_DROOP, arch + WHEEL_BUMP);
        }
        if (!visualsReady) {
            keepVisuals();
            visualsReady = true;
        }
    }

    @Override
    protected void onTyresSynced() {
        super.onTyresSynced();
        restingTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(SyncedValue<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == PITCH || key == ROLL || WHEEL_HEIGHTS.contains(key)) restingTicks = 0;
    }
}
