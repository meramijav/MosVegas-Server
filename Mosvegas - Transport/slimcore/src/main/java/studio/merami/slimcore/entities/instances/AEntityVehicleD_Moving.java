package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.HullCollision;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONCraft;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.physics.CraftPhysics;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.physics.Suspension;
import studio.merami.slimcore.sliminterface.BlockHit;
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
    public static final int AIRBORNE = 4096;
    private static final SyncedValue<Float> STEER = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final SyncedValue<Float> CYCLIC = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
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

    private static final SyncedValue<Float> ROTOR = SyncedValue.decimal(AEntityVehicleD_Moving.class, 0f);
    private static final float CRAFT_GROUND_PROBE = .04f;
    private static final float CRAFT_BOB = .018f;
    private static final int HOP_PAUSE = 16;
    private static final int RIDER_JUMP_TICKS = 4;
    private static final float RIDER_LANDING = .82f;
    private static final double RIDER_IDLE_SPEED = .03;
    private static final double EXIT_DROP = 3.5;
    private static final double SWAY_PITCH = .012;
    private static final double SWAY_ROLL = .016;
    private static final double SHOVE = .18;
    private static final double SHOVE_MAX = .32;

    protected DrivePhysics.Input input = DrivePhysics.IDLE;
    protected float liftInput;
    protected int inputTick;
    private int hopTick = -100;
    private boolean hopHeld;
    protected boolean craftAirborne;
    private double craftTurn;
    public float rotorAngle;
    public float oldRotorAngle;
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
    public float visualCyclic;
    public float oldVisualCyclic;
    public float riderAir;
    public float oldRiderAir;
    public float riderLand;
    public float oldRiderLand;
    public float riderIdle;
    public float oldRiderIdle;
    private int riderAirTicks;
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

    public void acceptInput(IWrapperPlayer player, float throttle, float brake, float steer, boolean handbrake, float lift) {
        acceptInput(player, throttle, brake, steer, handbrake);
        if (driver(player) && Float.isFinite(lift)) liftInput = SlimMath.clamp(lift, -1, 1);
    }

    public boolean craft() {
        return definition().craft != null;
    }

    public float rotor() {
        return data.get(ROTOR);
    }

    @Override
    protected boolean exitBlocked() {
        JSONCraft craft = definition().craft;
        if (craft == null || !craft.helicopter() || onGround()) return false;
        Point3D at = position();
        if (!Double.isNaN(world().waterSurface(at.x, at.z, at.y - EXIT_DROP - 1, at.y))) return false;
        return !world().raycastBlocks(at, at.add(0, -EXIT_DROP, 0), host).hit();
    }

    protected CraftPhysics.Input craftControls() {
        if (controllingPassenger() == null || exiting() || ticks() - inputTick > 10) {
            return new CraftPhysics.Input(0, 0, 0, definition().craft.helicopter() ? -.5 : 0);
        }
        if (!driveAllowed()) {
            if (input.throttle() > .05 || input.steer() != 0 || liftInput != 0) zoneNotice();
            return CraftPhysics.Input.IDLE;
        }
        return new CraftPhysics.Input(input.throttle(), input.brake(), input.steer(), liftInput);
    }

    protected CraftPhysics.State craftState() {
        return new CraftPhysics.State(forward, lateral, motion().y, rotor(), craftTurn);
    }

    protected double craftClearance() {
        Point3D at = position();
        double probe = CraftPhysics.CUSHION_HEIGHT + 1;
        BlockHit hit = world().raycastBlocks(at, at.add(0, -probe, 0), host);
        double clearance = hit.hit() ? at.y - hit.location().y : Double.NaN;
        double water = world().waterSurface(at.x, at.z, at.y - probe, at.y);
        if (!Double.isNaN(water) && (Double.isNaN(clearance) || at.y - water < clearance)) clearance = at.y - water;
        return clearance;
    }

    public double waterDepth() {
        JSONCraft craft = definition().craft;
        double surface = world().waterSurface(x(), z(), y() - 1, y() + craft.draft + 1);
        return Double.isNaN(surface) ? Double.NaN : surface - craft.draft - y();
    }

    protected void tickCraftMotion(CraftPhysics.Result result) {
        forward = result.forward();
        lateral = result.lateral();
        craftTurn = -result.yawDelta();
        data.set(ROTOR, (float) result.rotor());
        double sway = definition().craft.helicopter() && !onGround() ? result.rotor() : 0;
        data.set(PITCH, (float) (result.pitch() + Math.sin(ticks() * .11) * SWAY_PITCH * sway));
        data.set(ROLL, (float) (result.roll() + Math.sin(ticks() * .07 + 1.3) * SWAY_ROLL * sway));
        float driven = controllingPassenger() == null ? 0 : 1;
        data.set(STEER, SlimMath.approach(data.get(STEER), (float) (input.steer() * definition().physics.steerLock) * driven, .08f));
        data.set(CYCLIC, SlimMath.approach(data.get(CYCLIC), (float) (input.throttle() - input.brake()) * driven, .12f));
        setFlag(TURN_LEFT, false);
        setFlag(TURN_RIGHT, false);
        setFlag(BRAKING, false);
        float oldYaw = yaw();
        Point3D start = position();
        boolean allowedBefore = driveAllowed();
        float nextYaw = oldYaw + (float) result.yawDelta();
        boolean turning = Math.abs(nextYaw - oldYaw) > 1e-5;
        HullCollision.World world = turning || Math.abs(forward) + Math.abs(lateral) > 1e-6 ? surroundings(nextYaw) : null;
        if (turning && !turn(nextYaw, world)) lateral = 0;
        syncHeading();
        Point3D horizontal = velocity();
        double climbStart = y();
        Point3D moved = world == null ? horizontal : slide(horizontal, world);
        if (moved != horizontal) {
            setVelocity(moved);
            horizontal = moved;
        }
        double climbed = y() - climbStart;
        double vy = result.vertical();
        if (onGround() && vy <= 0) vy = -CRAFT_GROUND_PROBE;
        boolean airborne = !onGround();
        moveVertically(vy);
        if (airborne && onGround() && vy < -.3) land(-vy);
        double carried = climbed > 1e-3 ? Math.max(onGround() ? 0 : vy, climbed) : onGround() ? 0 : vy;
        setMotion(horizontal.x, carried, horizontal.z);
        data.set(SPEED, (float) forward);
        host.refreshBounds();
        if (allowedBefore && !driveAllowed()) {
            stopAtZone(start, oldYaw);
            horizontal = Point3D.ZERO;
        }
        boolean moving = Math.abs(forward) + Math.abs(lateral) > .02;
        if (moving) strike(horizontal);
        if (moving || ticks() % 5 == 0) {
            for (IWrapperEntity entity : world().entities(host, bounds())) {
                if (entity.isLiving() && entity.asPlayer() == null) pushOut(entity);
            }
        }
        if (ticks() % 100 == 0) forgetStruck();
        stampPose();
        oldBodyPitch = bodyPitch;
        oldBodyRoll = bodyRoll;
        bodyPitch = (float) result.pitch();
        bodyRoll = (float) result.roll();
    }

    @Override
    protected boolean shove(IWrapperPlayer player) {
        JSONCraft craft = definition().craft;
        if (craft == null || !craft.watercraft() || controllingPassenger() != null) return false;
        Point3D away = position().subtract(player.position());
        double length = Math.hypot(away.x, away.z);
        if (length < 1e-3) return false;
        Point3D next = velocity().add(new Point3D(away.x / length * SHOVE, 0, away.z / length * SHOVE));
        double speed = Math.hypot(next.x, next.z);
        if (speed > SHOVE_MAX) next = next.scale(SHOVE_MAX / speed);
        setVelocity(next);
        world().playSound(position(), "minecraft:entity.player.attack.knockback", .7f, .8f);
        return true;
    }

    protected boolean craftHop(CraftPhysics.Input controls) {
        boolean pressed = controls.lift() > .5;
        boolean fresh = pressed && !hopHeld && ticks() - hopTick > HOP_PAUSE;
        hopHeld = pressed;
        if (fresh) hopTick = ticks();
        return fresh;
    }

    protected void splash(double strength) {
        Point3D at = position().add(0, definition().craft.draft, 0);
        world().spawnParticles(IWrapperWorld.Particle.SPLASH, at, (int) (20 + 60 * Math.min(1, strength)), .9, .15, .9, .25);
        world().playSound(at, "minecraft:entity.generic.splash", (float) Math.min(1.5, .4 + strength), .85f + random.nextFloat() * .2f);
    }

    protected void tickCraftClient() {
        replayServerPose();
        oldBodyPitch = bodyPitch;
        oldBodyRoll = bodyRoll;
        oldBodyHeave = bodyHeave;
        oldLeanPitch = leanPitch;
        bodyPitch = SlimMath.lerp(.2f, bodyPitch, data.get(PITCH));
        bodyRoll = SlimMath.lerp(.2f, bodyRoll, data.get(ROLL));
        boolean bobbing = definition().craft.watercraft() && !Double.isNaN(waterDepth());
        bodyHeave = bobbing ? (float) Math.sin(ticks() * .13) * CRAFT_BOB / (1 + Math.abs(speed()) * 4) : SlimMath.lerp(.3f, bodyHeave, 0);
        if (definition().craft.riding != null) tickRider();
        host.refreshBounds();
    }

    private void tickRider() {
        oldRiderAir = riderAir;
        oldRiderLand = riderLand;
        oldRiderIdle = riderIdle;
        riderLand *= RIDER_LANDING;
        if (flag(AIRBORNE)) {
            riderAirTicks++;
        } else {
            if (riderAirTicks >= RIDER_JUMP_TICKS) riderLand = 1;
            riderAirTicks = 0;
        }
        riderAir = SlimMath.lerp(.3f, riderAir, riderAirTicks >= RIDER_JUMP_TICKS ? 1 : 0);
        riderIdle = SlimMath.lerp(.06f, riderIdle, Math.abs(speed()) < RIDER_IDLE_SPEED ? 1 : 0);
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
        oldVisualCyclic = visualCyclic;
        visualCyclic = SlimMath.lerp(.30f, visualCyclic, data.get(CYCLIC));
        oldWheelRoll = wheelRoll;
        wheelRoll += speed() / (float) (wheel(0).definition.rollRadius * scale());
        oldRotorAngle = rotorAngle;
        rotorAngle += rotor();
        if (rotorAngle > 1000) {
            rotorAngle -= 1000;
            oldRotorAngle -= 1000;
        }
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
