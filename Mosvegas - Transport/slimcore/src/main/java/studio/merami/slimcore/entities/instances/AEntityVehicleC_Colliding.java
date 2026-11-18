package studio.merami.slimcore.entities.instances;

import org.joml.Matrix4d;
import studio.merami.slimcore.baseclasses.BlockPosition;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.HullCollision;
import studio.merami.slimcore.baseclasses.ModelSkeleton;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.sliminterface.BlockHit;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IInterfaceCore;
import studio.merami.slimcore.sliminterface.IWrapperDamage;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.sliminterface.SyncedValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AEntityVehicleC_Colliding extends AEntityF_Multipart<JSONVehicle> {
    public static final int HEADLIGHT_LEFT = 1;
    public static final int HEADLIGHT_RIGHT = 2;
    public static final int TAILLIGHT_LEFT = 4;
    public static final int TAILLIGHT_RIGHT = 8;
    private static final SyncedValue<Integer> LAMPS = SyncedValue.integer(AEntityVehicleC_Colliding.class, 0);
    private static final Map<String, ModelSkeleton.Cube> PANELS = new ConcurrentHashMap<>();
    private static final double WINDOW_FRAME = .12;
    private static final String SCRAPE_SOUND = "minecraft:block.grindstone.use";
    private static final double TURN_PUSH = .3;
    private static final double WALL_BOUNCE = .15;
    private static final double CAR_BOUNCE = .25;
    private static final double WALL_FRICTION = .3;
    private static final double CAR_FRICTION = .2;
    private static final double BOUNCE_SPEED = .08;
    private static final double MAX_SPIN = .075;
    private static final double SMASH_SPEED = .25;
    private static final double SHOVE = .08;
    private static final double SMASH_WEIGHT = -.5;
    private static final double HIT_WEIGHT = .5;
    private static final double THROW_WEIGHT = .3;
    private static final double LANDING_WEIGHT = .5;
    private static final double SPAWN_STEP = .25;
    private static final double SPAWN_REACH = 6;
    private static final double SPAWN_GAP = .2;

    protected double forward;
    protected double lateral;
    protected DrivePhysics.Drift drift = new DrivePhysics.Drift(0, 0);
    private final Map<Integer, Integer> struck = new HashMap<>();
    private int crashSoundTick = -100;
    private int scrapeSoundTick = -100;

    protected AEntityVehicleC_Colliding(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    @Override
    protected JSONVehicle lookupDefinition(String id) {
        return PackParser.vehicle(id);
    }

    public boolean lampBroken(int lamp) {
        return (data.get(LAMPS) & lamp) != 0;
    }

    public float windowProgress(int seat) {
        return seat >= 0 && seat < seats().size() ? seats().get(seat).windowProgress : 0;
    }

    public ChassisGeometry.Pose puncturePose() {
        JSONVehicle definition = definition();
        double[] offsets = new double[definition.wheels.size()];
        boolean any = false;
        for (int i = 0; i < offsets.length; i++) {
            if (punctured(i)) {
                offsets[i] = -ChassisGeometry.PUNCTURE_DROP;
                any = true;
            }
        }
        return any ? ChassisGeometry.wheelPlane(definition, offsets) : new ChassisGeometry.Pose(0, 0, 0);
    }

    @Override
    public Point3D restingBodyToWorld(Point3D local) {
        ChassisGeometry.Pose pose = puncturePose();
        Point3D pivot = new Point3D(0, definition().bodyPivot, 0);
        Point3D v = local.subtract(pivot).xRot((float) -pose.pitch());
        double c = Math.cos(pose.roll());
        double s = Math.sin(pose.roll());
        v = new Point3D(v.x * c - v.y * s, v.x * s + v.y * c, v.z).add(pivot).add(0, pose.heave(), 0);
        return localToWorld(v);
    }

    @Override
    public Hull buildHull(float yaw) {
        JSONVehicle definition = definition();
        List<Hull.Box> boxes = new ArrayList<>();
        List<Hull.Box> controls = new ArrayList<>();
        Matrix4d root = root(yaw);
        Matrix4d body = body(root);
        Hull.Frame frame = new Hull.Frame(new Point3D(x(), y(), z()), Hull.direction(root, new Point3D(1, 0, 0)), Hull.direction(root, new Point3D(0, 0, 1)),
                definition.bodyHalfWidth * definition.scale, definition.bodyHalfLength * definition.scale);
        for (AJSONInteractable.Box box : definition.hull.boxes) boxes.add(Hull.chassisBox(body, box.center, box.half, null, -1));
        for (PartGroundDevice wheel : wheels()) {
            if (!wheel.definition.solid) continue;
            boolean flat = wheel.isPunctured();
            double r = wheel.definition.radius;
            Point3D half = flat ? new Point3D(wheel.definition.halfWidth, r * .7, r * .82) : new Point3D(wheel.definition.halfWidth, r, r);
            boxes.add(Hull.chassisBox(root, wheel.center().add(0, flat ? -ChassisGeometry.PUNCTURE_DROP : 0, 0), half, wheel, -1));
        }
        if (definition.modelLocation == null) return new Hull(boxes, controls, frame);
        List<ModelSkeleton.Bone> bones = ModelSkeleton.bones(definition.modelLocation);
        Map<String, ModelSkeleton.Bone> byName = ModelSkeleton.byName(bones);
        Map<String, Matrix4d> matrices = new HashMap<>();
        for (ModelSkeleton.Bone bone : bones) {
            PartOpening opening = openingByBone(bone.name());
            PartGlass pane = glassByBone(bone.name());
            boolean extra = definition.hull.bones.containsKey(bone.name());
            if (opening == null && pane == null && !extra) continue;
            if (pane != null && (pane.isBroken() || (pane.window() >= 0 && windowProgress(pane.window()) > .95f))) continue;
            APart part = partOf(bone.name(), byName);
            Matrix4d matrix = boneMatrix(bone, byName, matrices, body);
            if (opening != null && pane == null) {
                ModelSkeleton.Cube panel = panel(definition, bone, opening, part);
                Hull.Box box = Hull.bake(matrix, panel, part, -1);
                controls.add(box);
                if (opening.definition.solid) boxes.add(box);
            } else {
                List<Integer> only = definition.hull.bones.get(bone.name());
                for (int i = 0; i < bone.cubes().size(); i++) {
                    if (only != null && !only.isEmpty() && !only.contains(i)) continue;
                    boxes.add(Hull.bake(matrix, bone.cubes().get(i), part, pane == null ? -1 : pane.index));
                }
            }
        }
        return new Hull(boxes, controls, frame);
    }

    public Map<String, List<Hull.Box>> boneBoxes(Collection<String> names) {
        JSONVehicle definition = definition();
        Map<String, List<Hull.Box>> found = new HashMap<>();
        if (definition.modelLocation == null || names.isEmpty()) return found;
        Map<String, ModelSkeleton.Bone> byName = ModelSkeleton.byName(ModelSkeleton.bones(definition.modelLocation));
        Map<String, Matrix4d> matrices = new HashMap<>();
        Matrix4d body = body(root(yaw()));
        for (String name : names) {
            ModelSkeleton.Bone bone = byName.get(name);
            if (bone == null) continue;
            Matrix4d matrix = boneMatrix(bone, byName, matrices, body);
            APart part = partOf(name, byName);
            List<Hull.Box> boxes = new ArrayList<>();
            for (ModelSkeleton.Cube cube : bone.cubes()) boxes.add(Hull.bake(matrix, cube, part, -1));
            found.put(name, boxes);
        }
        return found;
    }

    private Matrix4d root(float yaw) {
        return new Matrix4d().translation(x(), y(), z()).rotateY(Math.toRadians(180 - yaw)).scale(definition().scale);
    }

    private Matrix4d body(Matrix4d root) {
        ChassisGeometry.Pose lean = puncturePose();
        double pivot = definition().bodyPivot;
        return new Matrix4d(root).translate(0, lean.heave() + pivot, 0).rotateZ(-lean.roll()).rotateX(-lean.pitch()).translate(0, -pivot, 0);
    }

    private List<Hull.Box> windowOpenings() {
        JSONVehicle definition = definition();
        List<Hull.Box> openings = new ArrayList<>();
        if (definition.modelLocation == null) return openings;
        List<ModelSkeleton.Bone> bones = ModelSkeleton.bones(definition.modelLocation);
        Map<String, ModelSkeleton.Bone> byName = ModelSkeleton.byName(bones);
        Map<String, Matrix4d> matrices = new HashMap<>();
        Matrix4d body = body(root(yaw()));
        for (ModelSkeleton.Bone bone : bones) {
            PartGlass pane = glassByBone(bone.name());
            if (pane == null) continue;
            Matrix4d matrix = new Matrix4d(bone.parent() == null ? body : boneMatrix(byName.get(bone.parent()), byName, matrices, body));
            Hull.rotateAt(matrix, bone.pivot(), bone.rotation());
            APart part = partOf(bone.name(), byName);
            for (ModelSkeleton.Cube cube : bone.cubes()) openings.add(Hull.bake(matrix, cube, part, pane.index).grow(WINDOW_FRAME));
        }
        return openings;
    }

    public Optional<Hull.Hit> cabinHit(Point3D start, Point3D end) {
        List<Hull.Box> windows = null;
        Hull.Hit nearest = null;
        for (Hull.Box box : hull().boxes()) {
            Optional<Point3D> point = box.clip(start, end);
            if (point.isEmpty()) continue;
            double distance = start.distanceTo(point.get());
            if (nearest != null && distance >= nearest.distance()) continue;
            if (box.glass < 0 && box.part instanceof PartOpening) {
                if (windows == null) windows = windowOpenings();
                boolean throughWindow = false;
                for (Hull.Box window : windows) throughWindow |= window.part == box.part && window.clip(start, end).isPresent();
                if (throughWindow) continue;
            }
            nearest = new Hull.Hit(box, point.get(), distance);
        }
        return Optional.ofNullable(nearest);
    }

    public void cabinShot(Point3D start, Point3D end, float damage) {
        cabinHit(start, end).ifPresent(hit -> {
            if (hit.box().glass >= 0) breakGlass(hit.box().glass);
            else damage(worldToLocal(hit.point()), null, hit.box().part, damage * .3f);
        });
    }

    private static ModelSkeleton.Cube panel(JSONVehicle definition, ModelSkeleton.Bone bone, PartOpening opening, APart part) {
        double max = opening.definition.maxHalfWidth;
        return PANELS.computeIfAbsent(definition.modelLocation + ":" + bone.name() + ":" + max, key -> {
            BoundingBox merged = null;
            for (ModelSkeleton.Cube cube : bone.cubes()) {
                Hull.Box box = Hull.bake(new Matrix4d(), cube, part, -1);
                if (max > 0 && Math.abs(box.center.x) > max) continue;
                merged = merged == null ? box.bounds : merged.minmax(box.bounds);
            }
            if (merged == null) throw new IllegalStateException("Missing panel " + key);
            if (max > 0) merged = new BoundingBox(Math.max(-max, merged.minX), merged.minY, merged.minZ, Math.min(max, merged.maxX), merged.maxY, merged.maxZ);
            return new ModelSkeleton.Cube(merged.center(), new Point3D(merged.sizeX() / 2, merged.sizeY() / 2, merged.sizeZ() / 2), Point3D.ZERO, Point3D.ZERO);
        });
    }

    private Matrix4d boneMatrix(ModelSkeleton.Bone bone, Map<String, ModelSkeleton.Bone> bones, Map<String, Matrix4d> done, Matrix4d root) {
        Matrix4d cached = done.get(bone.name());
        if (cached != null) return cached;
        Matrix4d m = new Matrix4d(bone.parent() == null ? root : boneMatrix(bones.get(bone.parent()), bones, done, root));
        Point3D rotation = bone.rotation();
        PartOpening opening = openingByBone(bone.name());
        if (bone.name().equals(definition().render.chassis)) {
            rotation = Point3D.ZERO;
        } else if (opening != null) {
            rotation = opening.definition.rotate.scale(opening.progress);
            if (!dents().isEmpty()) {
                Point3D push = DamageModel.displacement(dents(), new Point3D(-bone.pivot().x, bone.pivot().y, -bone.pivot().z));
                m.translate(-push.x, push.y, -push.z);
            }
        }
        PartGlass pane = glassByBone(bone.name());
        if (pane != null && pane.window() >= 0) m.translate(0, -windowProgress(pane.window()) * definition().windowDrop, 0);
        Hull.rotateAt(m, bone.pivot(), rotation);
        done.put(bone.name(), m);
        return m;
    }

    private APart partOf(String name, Map<String, ModelSkeleton.Bone> bones) {
        PartOpening opening = openingByBone(name);
        if (opening != null) return opening;
        ModelSkeleton.Bone bone = bones.get(name);
        return bone != null && bone.parent() != null ? partOf(bone.parent(), bones) : null;
    }

    public double inertia() {
        JSONVehicle definition = definition();
        double length = 2 * definition.size.halfLength * definition.scale;
        double width = 2 * definition.size.halfWidth * definition.scale;
        return definition.massRatio * (length * length + width * width) / 12;
    }

    public Point3D velocity() {
        return new Point3D(lateral, 0, forward).yRot((float) -Math.toRadians(yaw()));
    }

    protected void setVelocity(Point3D velocity) {
        double yaw = Math.toRadians(yaw());
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        forward = velocity.z * c - velocity.x * s;
        lateral = velocity.x * c + velocity.z * s;
    }

    public void push(Point3D change, double spin) {
        setVelocity(velocity().add(change));
        drift = new DrivePhysics.Drift(SlimMath.clamp(drift.yawRate() + spin, -MAX_SPIN, MAX_SPIN), drift.loss());
    }

    private List<AEntityVehicleC_Colliding> vehiclesIn(BoundingBox box) {
        List<AEntityVehicleC_Colliding> vehicles = new ArrayList<>();
        for (AEntityA_Base entity : world().coreEntities(box)) {
            if (entity instanceof AEntityVehicleC_Colliding vehicle && vehicle != this) vehicles.add(vehicle);
        }
        return vehicles;
    }

    protected HullCollision.World surroundings(float yaw) {
        Point3D motion = new Point3D(lateral, 0, forward).yRot((float) -Math.toRadians(yaw));
        BoundingBox reach = hull().bounds().inflate(TURN_PUSH + .3, 0, TURN_PUSH + .3).expandTowards(motion)
                .expandTowards(0, Math.max(.625, definition().maxStep), 0).inflate(.001);
        List<BoundingBox> blocks = new ArrayList<>(world().blockCollisions(host, reach));
        List<HullCollision.Other> vehicles = new ArrayList<>();
        for (AEntityVehicleC_Colliding vehicle : vehiclesIn(reach)) vehicles.add(new HullCollision.Other(vehicle, vehicle.hull()));
        return new HullCollision.World(blocks, vehicles);
    }

    protected boolean turn(float yaw, HullCollision.World world) {
        Hull before = hull();
        Hull turned = buildHull(yaw);
        Point3D shift = Point3D.ZERO;
        if (HullCollision.blocked(before, Point3D.ZERO, turned, Point3D.ZERO, world)) {
            shift = tryStep(before, turned, Point3D.ZERO, Point3D.ZERO, world);
            if (shift == null) shift = HullCollision.separate(before, turned, world, TURN_PUSH);
            if (shift == null) return false;
        }
        setYaw(yaw);
        adoptHull(turned, yaw);
        if (shift.lengthSqr() > 0) setPosition(position().add(shift));
        return true;
    }

    protected Point3D slide(Point3D velocity, HullCollision.World world) {
        Hull hull = hull();
        Point3D travel = Point3D.ZERO;
        double time = 1;
        moves:
        for (int round = 0; round < 6 && time > 1e-4; round++) {
            Point3D move = velocity.scale(time);
            if (move.lengthSqr() < 1e-10) break;
            int steps = Math.max(1, (int) Math.ceil(move.length() / .20));
            Point3D step = move.scale(1.0 / steps);
            HullCollision.Contact hit = null;
            int done = 0;
            for (; done < steps; done++) {
                Point3D candidate = travel.add(step);
                if (HullCollision.blocked(hull, travel, hull, candidate, world)) {
                    double kept = smash(hull, travel, candidate, world, velocity);
                    if (kept < 1) {
                        velocity = velocity.scale(kept);
                        time *= 1 - (double) done / steps;
                        continue moves;
                    }
                    Point3D climbed = tryStep(hull, hull, travel, candidate, world);
                    if (climbed == null) {
                        hit = HullCollision.contact(hull, travel, candidate, world);
                        break;
                    }
                    candidate = climbed;
                }
                travel = candidate;
            }
            if (done == steps) break;
            if (hit == null) {
                velocity = Point3D.ZERO;
                break;
            }
            Point3D touch = travel.add(step.scale(hit.time())).add(hit.normal().scale(.002));
            if (!HullCollision.blocked(hull, travel, hull, touch, world)) travel = touch;
            time *= 1 - (done + hit.time()) / steps;
            velocity = collide(velocity, hit, position().add(travel));
        }
        if (travel.lengthSqr() > 0) setPosition(position().add(travel));
        return velocity;
    }

    private Point3D collide(Point3D velocity, HullCollision.Contact hit, Point3D center) {
        Point3D n = hit.normal();
        AEntityVehicleC_Colliding other = hit.other() == null ? null : hit.other().vehicle();
        Point3D relative = other == null ? velocity : velocity.subtract(other.velocity());
        double approach = -relative.dot(n);
        if (approach <= 1e-6) return velocity;
        double mass = definition().massRatio;
        double arm = lever(hit.point().subtract(center), n);
        double otherArm = 0;
        double resistance = 1 / mass + arm * arm / inertia();
        if (other != null) {
            otherArm = lever(hit.point().subtract(other.position()), n);
            resistance += 1 / other.definition().massRatio + otherArm * otherArm / other.inertia();
        }
        double impulse = (1 + (approach < BOUNCE_SPEED ? 0 : other == null ? WALL_BOUNCE : CAR_BOUNCE)) * approach / resistance;
        Point3D along = relative.add(n.scale(approach));
        double slideSpeed = along.length();
        double friction = 0;
        if (slideSpeed > 1e-6) {
            friction = Math.min((other == null ? WALL_FRICTION : CAR_FRICTION) * impulse,
                    slideSpeed / (1 / mass + (other == null ? 0 : 1 / other.definition().massRatio)));
        }
        Point3D change = slideSpeed > 1e-6 ? n.scale(impulse).subtract(along.scale(friction / slideSpeed)) : n.scale(impulse);
        velocity = velocity.add(change.scale(1 / mass));
        drift = new DrivePhysics.Drift(SlimMath.clamp(drift.yawRate() - arm * impulse / inertia(), -MAX_SPIN, MAX_SPIN),
                Math.max(drift.loss(), Math.min(.35, impulse / mass * 1.2)));
        impact(hit, change.length() / mass, slideSpeed);
        if (other != null) {
            other.push(change.scale(-1 / other.definition().massRatio), otherArm * impulse / other.inertia());
            other.crash(hit.point(), hit.obstacle().part, change.length() / other.definition().massRatio, n.reverse());
        }
        return velocity;
    }

    private static double lever(Point3D r, Point3D n) {
        return r.z * n.x - r.x * n.z;
    }

    private void impact(HullCollision.Contact hit, double deltaV, double slide) {
        if (world().isClient()) return;
        Point3D point = hit.point();
        if (deltaV > .08) {
            if (ticks() - crashSoundTick > 4) {
                crashSoundTick = ticks();
                emitSoundAt(point, definition().sounds.collision, (float) SlimMath.clamp(deltaV * 1.6, .25, 1.6), (float) SlimMath.clamp(1.15 - deltaV * .3, .75, 1.15));
            }
            if (hit.block() != null && deltaV > .25) {
                world().spawnBlockParticles(BlockPosition.containing(hit.block().center()), point, (int) Math.min(30, deltaV * 25), .15, .1);
            }
        } else if (slide > .12) {
            world().spawnParticles(IWrapperWorld.Particle.SPARK, point, 2, .05, .05, .05, .15);
            if (ticks() - scrapeSoundTick > 9) {
                scrapeSoundTick = ticks();
                world().playSound(point, SCRAPE_SOUND, .45f, 1.35f);
            }
            wear(DamageModel.scrape(slide), 0);
        }
        crash(point, hit.box().part, deltaV, hit.normal());
    }

    private double smash(Hull hull, Point3D from, Point3D to, HullCollision.World world, Point3D velocity) {
        double mass = definition().massRatio;
        if (velocity.horizontalDistance() < SMASH_SPEED * DrivePhysics.weight(mass, SMASH_WEIGHT) || !ruleEnabled(IInterfaceCore.Rule.VEHICLE_BREAKS_BLOCKS)) return 1;
        double kept = 1;
        for (HullCollision.Touch touch : HullCollision.touches(hull, from, hull, to, world)) {
            if (touch.block() == null) continue;
            BlockPosition pos = BlockPosition.containing(touch.block().center());
            float hardness = world().breakableHardness(pos);
            if (hardness < 0) continue;
            world().destroyBlock(pos, host);
            world.blocks().removeIf(box -> BlockPosition.containing(box.center()).equals(pos));
            kept *= 1 - (1 - Math.max(.5, .96 - .05 * hardness)) / Math.max(.5, mass);
            wear(.5f + hardness, .1f);
        }
        return kept;
    }

    protected void strike(Point3D velocity) {
        Hull hull = hull();
        double mass = definition().massRatio;
        boolean hurt = ruleEnabled(IInterfaceCore.Rule.VEHICLE_HURTS_PEOPLE);
        for (IWrapperEntity entity : world().entities(host, bounds().inflate(.15))) {
            if (!entity.isLiving() || hasPassenger(entity) || entity.isSpectator() || entity.vehicle() instanceof AEntityVehicleC_Colliding) continue;
            if (!hull.intersects(entity.bounds().inflate(.08))) continue;
            Point3D away = entity.position().subtract(position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-6 ? velocity.normalize() : away.normalize();
            double closing = velocity.dot(away);
            float amount = DamageModel.runOver(closing) * (float) DrivePhysics.weight(mass, HIT_WEIGHT);
            if (hurt && amount > 0 && ticks() - struck.getOrDefault(entity.id(), -100) > 10) {
                struck.put(entity.id(), ticks());
                entity.hurt(world().damage(IWrapperWorld.DamageKind.RUN_OVER, host, controllingPassenger()), amount);
                entity.setVelocity(entity.velocity().add(velocity.scale(.9 * DrivePhysics.weight(mass, THROW_WEIGHT))).add(away.scale(.2)).add(0, .2 + closing * .3, 0));
                entity.markHurt();
                double kept = Math.max(.7, 1 - .03 / mass);
                forward *= kept;
                lateral *= kept;
                emitSoundAt(entity.position(), definition().sounds.collision, .6f, 1.3f);
            } else {
                Point3D v = entity.velocity();
                double along = v.x * away.x + v.z * away.z;
                double need = Math.max(0, closing) + SHOVE;
                if (along < need) {
                    entity.setVelocity(v.add(away.x * (need - along), 0, away.z * (need - along)));
                    entity.markImpulse();
                    if (entity.asPlayer() != null) entity.markHurt();
                }
            }
        }
    }

    protected void forgetStruck() {
        struck.values().removeIf(tick -> ticks() - tick > 40);
    }

    public void crash(Point3D point, APart part, double deltaV, Point3D push) {
        if (world().isClient()) return;
        float occupants = DamageModel.occupant(deltaV);
        if (occupants > 0 && ruleEnabled(IInterfaceCore.Rule.VEHICLE_HURTS_PEOPLE)) {
            for (IWrapperEntity passenger : passengers()) passenger.hurt(world().damage(IWrapperWorld.DamageKind.CRASH, null, null), occupants);
        }
        damage(worldToLocal(point), directionToLocal(push), part, DamageModel.impact(deltaV));
    }

    protected void land(double speed) {
        float amount = DamageModel.landing(speed) * (float) DrivePhysics.weight(definition().massRatio, LANDING_WEIGHT);
        if (amount <= 0) return;
        emitSound(definition().sounds.bump, .8f);
        if (speed > .7 && ruleEnabled(IInterfaceCore.Rule.VEHICLE_HURTS_PEOPLE)) {
            for (IWrapperEntity passenger : passengers()) passenger.hurt(world().damage(IWrapperWorld.DamageKind.FALL, null, null), (float) ((speed - .7) * 20));
        }
        wear(amount, .3f);
    }

    @Override
    protected void breakInZone(AJSONInteractable.Zone zone, Point3D local, float amount) {
        super.breakInZone(zone, local, amount);
        if (amount < DamageModel.LAMP || definition().lamps == null) return;
        boolean left = local.x > .25;
        boolean right = local.x < -.25;
        boolean middle = !left && !right && amount >= 2 * DamageModel.LAMP;
        int bits = data.get(LAMPS);
        if (zone.sideValue == DamageModel.Side.FRONT) {
            if (left || middle) bits |= HEADLIGHT_LEFT;
            if (right || middle) bits |= HEADLIGHT_RIGHT;
        }
        if (zone.sideValue == DamageModel.Side.REAR) {
            if (left || middle) bits |= TAILLIGHT_LEFT;
            if (right || middle) bits |= TAILLIGHT_RIGHT;
        }
        data.set(LAMPS, bits);
    }

    @Override
    public boolean damaged() {
        return super.damaged() || data.get(LAMPS) != 0;
    }

    @Override
    public void repair() {
        super.repair();
        data.set(LAMPS, 0);
    }

    private Point3D[] blast(Point3D from) {
        JSONVehicle definition = definition();
        Point3D surface = new Point3D(SlimMath.clamp(from.x, -definition.damage.halfWidth, definition.damage.halfWidth),
                SlimMath.clamp(from.y, .3, definition.size.height * .85), SlimMath.clamp(from.z, -definition.damage.halfLength, definition.damage.halfLength));
        Point3D push = new Point3D(0, definition.size.height * .4, 0).subtract(from);
        return new Point3D[]{surface, push.lengthSqr() < 1e-6 ? new Point3D(0, -1, 0) : push.normalize()};
    }

    public boolean projectileHit(Point3D start, Point3D end, IWrapperDamage source, float damage) {
        int pane = hull().glassRay(start, end);
        if (pane >= 0) breakGlass(pane);
        Optional<Hull.Hit> part = hull().ray(start, end).filter(hit -> hit.box().part != null);
        if (part.isPresent() && part.get().box().part instanceof PartGroundDevice wheel) {
            puncture(wheel.index);
            return true;
        }
        IWrapperEntity victim = null;
        double nearest = Double.MAX_VALUE;
        for (IWrapperEntity passenger : passengers()) {
            Optional<Point3D> point = passenger.bounds().clip(start, end);
            if (point.isPresent() && point.get().distanceTo(start) < nearest) {
                nearest = point.get().distanceTo(start);
                victim = passenger;
            }
        }
        if (victim != null) {
            Point3D cabin = worldToLocal(start.lerp(end, Math.min(1, nearest / Math.max(.001, start.distanceTo(end)))));
            if (cabin.y > .88) {
                victim.hurt(source, damage);
                return true;
            }
        }
        hull().ray(start, end).ifPresent(body -> damage(worldToLocal(body.point()), null, body.box().part, damage * .3f));
        return true;
    }

    public boolean attack(IWrapperDamage source, float amount) {
        if (world().isClient()) return true;
        IWrapperPlayer attacker = source.attacker() == null ? null : source.attacker().asPlayer();
        if (attacker != null && !attacker.isCreative() && attacker.isSneaking() && shove(attacker)) return true;
        if (!world().allows(attacker, IWrapperWorld.Access.DAMAGE, this)) return false;
        IWrapperEntity projectile = source.projectile();
        if (projectile != null) {
            Point3D start = projectile.position().subtract(projectile.velocity());
            return projectileHit(start, projectile.position().add(projectile.velocity().scale(2)), source, amount);
        }
        if (source.isExplosion()) {
            Point3D from = source.sourcePosition();
            Point3D local = from == null ? Point3D.ZERO : worldToLocal(from);
            Point3D[] blast = blast(local);
            damage(local, null, null, amount);
            if (ruleEnabled(IInterfaceCore.Rule.VEHICLE_DAMAGE)) addDent(blast[0], blast[1], amount);
            return true;
        }
        if (attacker != null && attacker.isCreative() && attacker.isSneaking() && isOwner(attacker)) {
            remove();
            return true;
        }
        return false;
    }

    protected boolean shove(IWrapperPlayer player) {
        return false;
    }

    protected double sampleWheelGround(int wheel) {
        PartGroundDevice device = wheel(wheel);
        return sampleGround(device.center().x, device.center().z, device.definition.contact);
    }

    protected double sampleGround(double x, double z, double halfLength) {
        Point3D center = localToWorld(new Point3D(x, 0, z));
        double highest = Double.NEGATIVE_INFINITY;
        double low = y() - 1.1;
        double high = y() + .64;
        BoundingBox query = ChassisGeometry.bounds(new Point3D(center.x, low, center.z), yaw(), .15, halfLength, high - low);
        for (BoundingBox box : world().blockCollisions(host, query)) {
            if (box.maxY > high || box.maxY < low) continue;
            if (ChassisGeometry.overlaps(new Point3D(center.x, 0, center.z), yaw(), .15, halfLength, low, high, box)) highest = Math.max(highest, box.maxY);
        }
        return Double.isFinite(highest) ? highest : y() - 1.1;
    }

    protected Point3D tryStep(Hull before, Hull hull, Point3D travel, Point3D candidate, HullCollision.World world) {
        double maxStep = definition().maxStep;
        if (maxStep <= 0) return null;
        if (!onGround() && definition().craft == null) {
            boolean supported = false;
            for (int i = 0; i < wheels().size(); i++) {
                double gap = y() - sampleWheelGround(i);
                supported |= gap >= -.025 && gap < .12;
            }
            if (!supported || motion().y < -.2) return null;
        }
        double lift = candidate.y;
        for (int pass = 0; pass < 4; pass++) {
            double required = lift;
            for (HullCollision.Touch touch : HullCollision.touches(before, travel, hull, new Point3D(candidate.x, lift, candidate.z), world)) {
                if (touch.block() == null || touch.block().maxY - y() > maxStep) return null;
                required = Math.max(required, touch.block().maxY - hull.boxes().get(touch.index()).bounds.minY + .002);
            }
            if (required == lift) break;
            lift = required;
        }
        if (lift <= travel.y || lift > maxStep) return null;
        for (double y = travel.y + .08; y < lift + .08; y += .08) {
            if (HullCollision.blocked(before, travel, before, new Point3D(travel.x, Math.min(y, lift), travel.z), world)) return null;
        }
        Point3D raised = new Point3D(candidate.x, lift, candidate.z);
        return HullCollision.blocked(before, travel, hull, raised, world) ? null : raised;
    }

    protected void moveVertically(double velocity) {
        double allowed = velocity;
        Hull hull = hull();
        BoundingBox swept = hull.bounds().expandTowards(0, velocity, 0);
        List<BoundingBox> obstacles = world().blockCollisions(host, swept);
        for (Hull.Box box : hull.boxes()) {
            BoundingBox sweep = box.bounds.expandTowards(0, velocity, 0);
            if (obstacles.stream().noneMatch(b -> b.intersects(sweep))) continue;
            BoundingBox slice = box.bounds;
            for (BoundingBox block : obstacles) {
                if (slice.maxX <= block.minX + 1e-6 || slice.minX >= block.maxX - 1e-6 || slice.maxZ <= block.minZ + 1e-6 || slice.minZ >= block.maxZ - 1e-6) continue;
                if (!box.intersects(new BoundingBox(block.minX, slice.minY - .01, block.minZ, block.maxX, slice.maxY + .01, block.maxZ))) continue;
                if (velocity < 0 && slice.minY >= block.maxY - 1e-5) allowed = Math.max(allowed, block.maxY - slice.minY);
                if (velocity > 0 && slice.maxY <= block.minY + 1e-5) allowed = Math.min(allowed, block.minY - slice.maxY);
            }
        }
        setPosition(position().add(0, allowed, 0));
        setOnGround(velocity < 0 && allowed > velocity + 1e-6);
    }

    private boolean collides(Point3D at, float yaw) {
        Hull hull = yaw == yaw() ? hull() : buildHull(yaw);
        Point3D offset = at.subtract(position());
        BoundingBox broad = hull.bounds().move(offset);
        for (BoundingBox box : world().blockCollisions(host, broad)) {
            if (hull.intersects(box.move(offset.reverse()))) return true;
        }
        for (AEntityVehicleC_Colliding other : vehiclesIn(broad)) {
            if (hull.intersects(other.hull(), offset)) return true;
        }
        if (yaw != yaw()) adoptHull(hull, yaw);
        return false;
    }

    public boolean canSpawnAtCurrentPosition() {
        return !collides(position(), yaw());
    }

    public void keepClearOf(IWrapperPlayer player) {
        Point3D look = player.lookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-6) return;
        Point3D start = position();
        Point3D step = look.normalize().scale(SPAWN_STEP);
        for (int i = 0; i < SPAWN_REACH / SPAWN_STEP && bounds().inflate(SPAWN_GAP).intersects(player.bounds()); i++) setPosition(position().add(step));
        if (bounds().inflate(SPAWN_GAP).intersects(player.bounds()) || !canSpawnAtCurrentPosition()) setPosition(start);
    }

    public void settleOnGround(double maxDrop) {
        moveVertically(-maxDrop);
        setMotion(Point3D.ZERO);
        host.refreshBounds();
    }

    public Optional<Point3D> spawnClearance(IWrapperPlayer player) {
        if (!bounds().intersects(player.bounds())) return Optional.of(player.position());
        Point3D local = worldToLocal(player.position());
        JSONVehicle definition = definition();
        List<Point3D> candidates = new ArrayList<>();
        for (double extra = 0; extra <= 3; extra += .5) {
            for (int side : new int[]{1, -1}) {
                candidates.add(localToWorld(new Point3D(side * (definition.size.halfWidth + .6 + extra), 0,
                        SlimMath.clamp(local.z, -definition.size.halfLength, definition.size.halfLength))));
                candidates.add(localToWorld(new Point3D(SlimMath.clamp(local.x, -definition.size.halfWidth, definition.size.halfWidth), 0,
                        side * (definition.size.halfLength + .6 + extra))));
            }
        }
        candidates.sort(Comparator.comparingDouble(player.position()::distanceToSqr));
        for (Point3D at : candidates) {
            BlockHit floor = world().raycastBlocks(at.add(0, 2, 0), at.add(0, -3, 0), player);
            if (!floor.hit() || !floor.top()) continue;
            Point3D feet = floor.location().add(0, .02, 0);
            BoundingBox body = player.standingBounds(feet);
            if (!bounds().inflate(.1).intersects(body) && world().noCollision(player, body)) return Optional.of(feet);
        }
        return Optional.empty();
    }

    @Override
    public void load(IWrapperNBT tag) {
        super.load(tag);
        data.set(LAMPS, tag.getInt("Lamps"));
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        tag.setInt("Lamps", data.get(LAMPS));
    }
}
