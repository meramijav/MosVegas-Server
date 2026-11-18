package studio.merami.sliminterfacefabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartGroundDevice;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.sliminterfacefabric.wrapper.WrapperWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class CollisionGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void modelContours(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        spawn(h, car);
        Hull hull = car.hull();
        h.assertTrue(hull.boxes().size() <= 30, "Exterior hull must have a bounded, small collision budget");
        Point3D air = car.localToWorld(new Point3D(0, 1.5, 2.55));
        BoundingBox query = new BoundingBox(air.add(-.08, -.08, -.08), air.add(.08, .08, .08));
        h.assertTrue(car.bounds().intersects(query), "Probe must be within the broad phase");
        h.assertFalse(hull.intersects(query), "Air above bonnet must not collide");
        h.assertTrue(h.getLevel().getEntityCollisions(null, aabb(query)).isEmpty(), "Vanilla player collision must use contour shapes");
        Point3D from = car.localToWorld(new Point3D(0, 1.5, 4));
        Point3D to = car.localToWorld(new Point3D(0, 1.5, 1.65));
        h.assertTrue(hull.ray(from, to).isEmpty(), "Ray above bonnet must pass through air");
        Point3D cabin = car.localToWorld(new Point3D(.43, .90, .3));
        h.assertFalse(hull.intersects(new BoundingBox(cabin.add(-.1, -.1, -.1), cabin.add(.1, .1, .1))), "Seats must not add interior collision");
        car.bodyPitch = .14f;
        car.bodyRoll = .10f;
        car.bodyHeave = .09f;
        h.assertTrue(car.buildHull(0).bounds().equals(hull.bounds()), "Visual suspension must never change physical ground contact");
        PartOpening door = car.opening("door_fl");
        Hull.Box closed = hull.boxes().stream().filter(b -> b.part == door).findFirst().orElseThrow();
        door.progress = 1;
        Hull.Box opened = car.buildHull(0).boxes().stream().filter(b -> b.part == door).findFirst().orElseThrow();
        h.assertTrue(closed.center.distanceTo(opened.center) > .2, "Door hitbox must follow hinge animation");
        car.setYaw(45);
        Hull turned = car.hull();
        h.assertTrue(turned.boxes().get(1).center.distanceTo(hull.boxes().get(1).center) > .1, "Hull must rotate with car");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tyreCollidersMatchContactCenters(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        for (float yaw : new float[]{0, 30, 90, 155}) {
            car.setYaw(yaw);
            for (int i = 0; i < 4; i++) {
                PartGroundDevice wheel = car.wheel(i);
                Hull.Box box = car.hull().boxes().stream().filter(b -> b.part == wheel).findFirst().orElseThrow();
                Point3D contact = car.localToWorld(wheel.center());
                h.assertTrue(box.center.distanceTo(contact) < .001, "Tyre collision and terrain contact must coincide at yaw " + yaw + " delta=" + box.center.subtract(contact));
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnMovesPlayerOutsideWholeCar(GameTestHelper h) {
        for (int x = 1; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        car.setYaw(32);
        spawn(h, car);
        Player player = h.makeMockPlayer();
        place(player, car.position());
        Point3D safe = car.spawnClearance(w(player)).orElseThrow();
        AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(v(safe));
        h.assertFalse(aabb(car.bounds()).intersects(body), "Spawn clearance must leave even the hollow cabin and broad hull");
        h.assertTrue(h.getLevel().noCollision(player, body), "Spawn clearance must not put the player in blocks");
        h.assertTrue(Math.abs(safe.y - car.y()) < .1, "Spawn clearance should move sideways onto ground");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void punctureLeansHullWithModel(GameTestHelper h) {
        for (int x = 1; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                h.setBlock(x, 1, z, Blocks.AIR);
            }
        }
        EntityVehicleF_Physics car = createAt(h, new Vec3(4.5, 1.03, 4.5));
        car.setYaw(25);
        spawn(h, car);
        for (int tick = 0; tick < 20; tick++) tick(car);
        Hull intact = car.hull();
        double groundY = car.y();
        car.puncture(0);
        for (int tick = 0; tick < 30; tick++) tick(car);
        h.assertTrue(Math.abs(car.y() - groundY) < .02, "A flat tyre must not lift or drop the physical car");
        Hull flat = car.hull();
        Hull.Box tyre = flat.boxes().stream().filter(b -> b.part == car.wheel(0)).findFirst().orElseThrow();
        Hull.Box other = flat.boxes().stream().filter(b -> b.part == car.wheel(1)).findFirst().orElseThrow();
        h.assertTrue(Math.abs(tyre.bounds.minY - other.bounds.minY) < .01, "Flat tyre tread stays on the road");
        h.assertTrue(tyre.bounds.maxY < other.bounds.maxY - .1, "Flat tyre contour is squashed like the model");
        ChassisGeometry.Pose lean = car.puncturePose();
        h.assertTrue(Math.abs(car.bodyPitch - lean.pitch()) < .01 && Math.abs(car.bodyRoll - lean.roll()) < .01 && Math.abs(car.bodyHeave - lean.heave()) < .02,
                "Rendered chassis must settle to the hit-box lean: pitch=" + car.bodyPitch + "/" + lean.pitch() + " roll=" + car.bodyRoll + "/" + lean.roll()
                        + " heave=" + car.bodyHeave + "/" + lean.heave());
        Point3D corner = car.chassisToWorld(new Point3D(.9, 1.70, 1.2));
        Point3D intactCorner = car.localToWorld(new Point3D(.9, 1.70, 1.2));
        h.assertTrue(corner.y < intactCorner.y - .03, "Body must lean toward the flat tyre");
        h.assertTrue(flat.bounds().minY <= intact.bounds().minY + .001, "Hull bottom unchanged");
        assertWheelsAttached(h, car, "punctured");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void playerCaughtInsideCarIsReleasedAndPushedOut(GameTestHelper h) {
        floor(h, 1, 10, 0, 12);
        EntityVehicleF_Physics car = car(h, 5, 6, 0);
        spawn(h, car);
        Player player = h.makeMockPlayer();
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        Point3D inside = car.localToWorld(new Point3D(.3, 0, .3));
        player.setPos(inside.x, ground, inside.z);
        h.assertTrue(car.embeds(w(player)), "A player standing inside the body is caught in it");
        h.assertTrue(h.getLevel().getEntityCollisions(player, player.getBoundingBox().inflate(1.5)).isEmpty(), "The car must not hold a player who is already inside it");
        for (int tick = 0; tick < 30 && car.hull().intersects(box(player.getBoundingBox().deflate(.02))); tick++) {
            car.pushOut(w(player));
            player.move(MoverType.SELF, player.getDeltaMovement());
            player.setDeltaMovement(player.getDeltaMovement().multiply(.5, 0, .5));
        }
        Point3D local = car.worldToLocal(p(player.position()));
        h.assertFalse(car.embeds(w(player)), "The player is pushed out of the car: " + local);
        h.assertTrue(local.x > .9 && Math.abs(local.z - .3) < .2, "The player leaves sideways through the nearest (left) side: " + local);
        h.assertFalse(car.hull().intersects(box(player.getBoundingBox().deflate(.02))), "The player ends up outside the hull");
        Vec3 before = player.position();
        player.move(MoverType.SELF, new Vec3(-.5, 0, 0));
        h.assertTrue(player.position().x > before.x - .3, "Once out, the car body blocks the player again: moved " + (player.position().x - before.x));

        openDoor(car, 0);
        Hull.Box door = control(car, "door_fl");
        Player walker = h.makeMockPlayer();
        walker.setPos(door.center.x, ground, door.center.z);
        h.assertTrue(car.worldToLocal(p(walker.position())).x > 1, "Test setup: standing in the open door, beside the car");
        h.assertFalse(car.embeds(w(walker)), "Standing in an open door is not being caught inside the car");
        for (int tick = 0; tick < 20 && car.hull().intersects(box(walker.getBoundingBox().deflate(.02))); tick++) walk(car, walker, Vec3.ZERO);
        h.assertFalse(car.hull().intersects(box(walker.getBoundingBox().deflate(.02))), "The door that swung into the player pushes them out of it");
        h.assertTrue(car.worldToLocal(p(walker.position())).x > 1, "...away from the car, not into it");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "slide_side", timeoutTicks = 100)
    public void walksAlongTheSideOfACar(GameTestHelper h) {
        floor(h, 1, 24, 1, 24);
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        for (int run = 0; run < 8; run++) {
            int yaw = new int[]{0, 90, 7, 30}[run % 4];
            String model = run < 4 ? "volga" : "g63";
            EntityVehicleF_Physics car = car(h, 12, 12, yaw, model);
            spawn(h, car);
            for (int into : new int[]{0, 8}) {
                Player player = h.makeMockPlayer();
                Point3D start = car.localToWorld(new Point3D(1.3, 0, -2.6));
                player.setPos(start.x, ground, start.z);
                Vec3 side = v(car.directionToWorld(new Point3D(1, 0, 0)));
                for (int i = 0; i < 40 && car.hull().intersectsEntity(box(player.getBoundingBox())); i++) player.setPos(player.getX() + side.x * .05, ground, player.getZ() + side.z * .05);
                Vec3 along = v(car.directionToWorld(new Point3D(0, 0, 1)));
                Vec3 input = along.scale(Math.cos(Math.toRadians(into))).add(side.scale(-Math.sin(Math.toRadians(into)))).scale(.1);
                Vec3 from = player.position();
                for (int tick = 0; tick < 40; tick++) walk(car, player, input);
                double travelled = player.position().subtract(from).dot(along);
                h.assertTrue(travelled > 6.5, "Walking along the side of a " + model + " turned " + yaw + " degrees, " + into + " degrees into it, must not catch on wheels or door edges: travelled " + travelled);
            }
            car.remove();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doorPanelsAreEvenAndFlush(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        BoundingBox front = control(car, "door_fl").bounds;
        BoundingBox rear = control(car, "door_rl").bounds;
        h.assertTrue(Math.abs(front.minY - rear.minY) < .01 && Math.abs(front.maxY - rear.maxY) < .01, "Front and rear doors are the same height: " + front + " " + rear);
        h.assertTrue(Math.abs(front.maxX - rear.maxX) < .03, "Front and rear doors lie in one plane (the mirror is not part of the door): " + front.maxX + " vs " + rear.maxX);
        double side = car.definition().bodyHalfWidth * car.scale() + 1e-3;
        for (Hull.Box box : car.hull().entityBoxes()) {
            boolean door = box.part instanceof PartOpening opening && opening.isDoor();
            if (!door && !(box.part instanceof PartGroundDevice)) continue;
            for (int i = 0; i < 8; i++) h.assertTrue(Math.abs(box.corner(i).x - car.x()) <= side, "Closed doors and tyres do not stick out of the body side for people: " + box.part.label());
        }
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnedCarStandsClearOfThePlayer(GameTestHelper h) {
        floor(h, 1, 10, 0, 16);
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        Player player = h.makeMockPlayer();
        Vec3 at = h.absoluteVec(new Vec3(5, 1, 3));
        player.setPos(at.x, ground, at.z);
        player.setYRot(0);
        player.setXRot(30);
        EntityVehicleF_Physics car = create(h);
        car.moveTo(at.x, ground, at.z + 1.5, player.getYRot(), 0);
        h.assertTrue(aabb(car.bounds()).intersects(player.getBoundingBox()), "Test setup: a car centred on the block in front covers the player");
        Vec3 stood = player.position();
        car.keepClearOf(w(player));
        h.assertFalse(aabb(car.bounds()).inflate(.1).intersects(player.getBoundingBox()), "The new car moves forward off the player");
        h.assertTrue(car.canSpawnAtCurrentPosition() && player.position().equals(stood), "...onto free ground, and the player stays where they stood");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "stuck_search", timeoutTicks = 400)
    public void playerNeverGetsStuckAroundCars(GameTestHelper h) {
        floor(h, 1, 24, 1, 24);
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        List<String> stuck = new ArrayList<>();
        List<String> inside = new ArrayList<>();
        Random random = new Random(7);
        for (int scene = 0; scene < 6; scene++) {
            String model = scene < 3 ? "volga" : "g63";
            List<EntityVehicleF_Physics> cars = new ArrayList<>();
            cars.add(car(h, 12, 12, 30, model));
            if (scene % 3 == 1) for (int seat = 0; seat < 4; seat++) openDoor(cars.get(0), seat);
            if (scene % 3 == 2) {
                EntityVehicleF_Physics second = car(h, 12, 12, 36, model);
                Point3D side = cars.get(0).directionToWorld(new Point3D(1, 0, 0));
                second.setPosition(cars.get(0).position().add(side.scale(2.75)));
                second.refreshHull();
                cars.add(second);
            }
            for (EntityVehicleF_Physics car : cars) spawn(h, car);
            String name = model + (scene % 3 == 0 ? " doors closed" : scene % 3 == 1 ? " doors open" : " two cars side by side");
            for (double x = 4; x <= 20; x += .4) {
                for (double z = 4; z <= 20; z += .4) {
                    Vec3 at = h.absoluteVec(new Vec3(x, 0, z));
                    Player player = h.makeMockPlayer();
                    player.setPos(at.x, ground, at.z);
                    AABB box = player.getBoundingBox();
                    if (near(cars, box.deflate(.02)) || !near(cars, box.inflate(1))) continue;
                    boolean entered = false;
                    Vec3 dir = Vec3.ZERO;
                    for (int tick = 0; tick < 25; tick++) {
                        if (tick % 5 == 0) {
                            double a = random.nextDouble() * Math.PI * 2;
                            dir = new Vec3(Math.sin(a), 0, Math.cos(a)).scale(.1);
                        }
                        stroll(cars, player, dir);
                        for (EntityVehicleF_Physics car : cars) {
                            Point3D local = car.worldToLocal(p(player.position()));
                            entered |= Math.abs(local.x) < .9 && Math.abs(local.z) < 2.9;
                        }
                    }
                    String where = name + " from " + String.format(Locale.ROOT, "(%.1f %.1f)", x, z) + " to "
                            + String.format(Locale.ROOT, "(%.2f %.2f)", player.getX() - h.absoluteVec(Vec3.ZERO).x, player.getZ() - h.absoluteVec(Vec3.ZERO).z);
                    if (entered) inside.add(where);
                    boolean free = false;
                    for (int turn = 0; turn < 8 && !free; turn++) {
                        Vec3 way = new Vec3(Math.sin(turn * Math.PI / 4), 0, Math.cos(turn * Math.PI / 4)).scale(.1);
                        Player walker = h.makeMockPlayer();
                        walker.setPos(player.getX(), player.getY(), player.getZ());
                        for (int tick = 0; tick < 30; tick++) stroll(cars, walker, way);
                        free = !near(cars, walker.getBoundingBox().inflate(.4));
                    }
                    if (!free) stuck.add(where);
                }
            }
            for (EntityVehicleF_Physics car : cars) car.remove();
        }
        h.assertTrue(inside.isEmpty(), "Walking around never carries the player inside a car (" + inside.size() + "): " + inside.subList(0, Math.min(5, inside.size())));
        h.assertTrue(stuck.isEmpty(), "From anywhere beside the cars the player can walk away (" + stuck.size() + "): " + stuck.subList(0, Math.min(5, stuck.size())));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "stuck_ground", timeoutTicks = 400)
    public void playerWalkingOnTheGroundNeverGetsStuck(GameTestHelper h) {
        floor(h, 1, 24, 1, 24);
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        List<String> stuck = new ArrayList<>();
        List<String> inside = new ArrayList<>();
        Random random = new Random(11);
        int tried = 0;
        int climbed = 0;
        for (int scene = 0; scene < 6; scene++) {
            String model = scene < 2 || scene >= 4 ? "g63" : "volga";
            boolean tilted = scene >= 4;
            floor(h, 1, 24, 1, 24);
            EntityVehicleF_Physics car = car(h, 12, 12, tilted ? 0 : 30, model);
            if (tilted) {
                for (int x = 1; x < 24; x++) {
                    for (int z = 1; z < 24; z++) {
                        Point3D local = car.worldToLocal(p(h.absoluteVec(new Vec3(x + .5, 1, z + .5))));
                        if (local.x > .4 && Math.abs(local.z) < 4) h.setBlock(x, 1, z, Blocks.STONE_SLAB);
                    }
                }
                car.setPosition(car.position().add(0, .6, 0));
            }
            if (scene % 2 == 1) for (int seat = 0; seat < car.seats().size(); seat++) openDoor(car, seat);
            spawn(h, car);
            if (tilted) {
                for (int tick = 0; tick < 60; tick++) {
                    if (tick % 3 == 0) car.setOnGround(false);
                    step(car);
                }
                car.refreshHull();
            }
            String name = model + (scene % 2 == 0 ? " doors closed" : " doors open") + (tilted ? " on a kerb" : "");
            for (double x = 5; x <= 19; x += .5) {
                for (double z = 5; z <= 19; z += .5) {
                    Vec3 at = h.absoluteVec(new Vec3(x, 0, z));
                    Player player = h.makeMockPlayer();
                    player.setPos(at.x, ground + .55, at.z);
                    if (car.hull().intersects(box(player.getBoundingBox().deflate(.02))) || !car.hull().intersects(box(player.getBoundingBox().inflate(1)))) continue;
                    tried++;
                    Vec3 dir = Vec3.ZERO;
                    boolean entered = false;
                    for (int tick = 0; tick < 40; tick++) {
                        if (tick % 8 == 0) {
                            double a = random.nextDouble() * Math.PI * 2;
                            dir = new Vec3(Math.sin(a), 0, Math.cos(a)).scale(random.nextBoolean() ? .1 : .14);
                        }
                        fall(car, player, dir, random.nextInt(10) == 0);
                        entered |= car.embeds(w(player));
                        if (player.onGround() && player.getY() > ground + .55 && car.hull().intersects(box(player.getBoundingBox().move(0, -.05, 0)))) climbed++;
                    }
                    String where = name + " from " + String.format(Locale.ROOT, "(%.1f %.1f)", x, z) + " to " + car.worldToLocal(p(player.position()));
                    if (entered) inside.add(where);
                    boolean free = false;
                    for (int turn = 0; turn < 8 && !free; turn++) {
                        Vec3 way = new Vec3(Math.sin(turn * Math.PI / 4), 0, Math.cos(turn * Math.PI / 4)).scale(.1);
                        Player walker = h.makeMockPlayer();
                        walker.setPos(player.getX(), player.getY(), player.getZ());
                        walker.setDeltaMovement(player.getDeltaMovement());
                        walker.setOnGround(player.onGround());
                        for (int tick = 0; tick < 50; tick++) fall(car, walker, way, tick % 12 == 0);
                        free = !car.hull().intersects(box(walker.getBoundingBox().inflate(.4)));
                    }
                    if (!free) stuck.add(where);
                }
            }
            car.remove();
        }
        h.assertTrue(tried > 500 && climbed > 50, "Test setup: walks start from many spots and often climb onto the car: " + tried + " starts, " + climbed + " ticks on the car");
        h.assertTrue(inside.isEmpty(), "Walking, running and jumping around never carries the player into a car (" + inside.size() + "): " + inside.subList(0, Math.min(5, inside.size())));
        h.assertTrue(stuck.isEmpty(), "From anywhere around the car the player can walk or jump away (" + stuck.size() + "): " + stuck.subList(0, Math.min(5, stuck.size())));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "driven_into", timeoutTicks = 100)
    public void carDrivenIntoPlayerNeverSwallowsThem(GameTestHelper h) {
        floor(h, 1, 24, 1, 24);
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        for (String model : new String[]{"g63", "volga"}) {
            for (int yaw : new int[]{0, 30}) {
                EntityVehicleF_Physics car = car(h, 12, 4, yaw, model);
                spawn(h, car);
                Player driver = h.makeMockPlayer();
                Player standing = h.makeMockPlayer();
                Point3D ahead = car.localToWorld(new Point3D(0, 0, 4.2));
                standing.setPos(ahead.x, ground, ahead.z);
                own(car, driver);
                place(driver, car.position());
                ride(driver, car);
                car.action(w(driver), VehicleAction.ENGINE);
                if (!car.definition().transmission.automatic) car.shift(1, w(driver));
                int inside = 0;
                double fastest = 0;
                for (int tick = 0; tick < 30; tick++) {
                    car.acceptInput(w(driver), tick < 18 ? 1 : 0, tick < 18 ? 0 : 1, 0, false);
                    step(car);
                    place(driver, car.position());
                    fall(car, standing, Vec3.ZERO, false);
                    fastest = Math.max(fastest, car.speed());
                    if (car.embeds(w(standing))) inside++;
                }
                h.assertTrue(fastest > .3 || !model.equals("g63"), "Test setup: the " + model + " reaches more than the escape speed: " + fastest * 72 + " km/h");
                h.assertTrue(inside == 0, "A " + model + " turned " + yaw + " that drives into a standing player pushes them ahead, never through its body: " + inside + " ticks inside");
                driver.stopRiding();
                car.remove();
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "roof", timeoutTicks = 100)
    public void playerOnTopCanAlwaysGetDown(GameTestHelper h) {
        floor(h, 1, 24, 1, 24);
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        List<String> trapped = new ArrayList<>();
        for (String model : new String[]{"g63", "volga"}) {
            EntityVehicleF_Physics car = car(h, 12, 12, 30, model);
            spawn(h, car);
            int tried = 0;
            for (double x = -1.3; x <= 1.3; x += .2) {
                for (double z = -3.1; z <= 3.1; z += .3) {
                    Player player = h.makeMockPlayer();
                    Point3D top = car.localToWorld(new Point3D(x, 3.2, z));
                    player.setPos(top.x, top.y, top.z);
                    for (int tick = 0; tick < 40; tick++) fall(car, player, Vec3.ZERO, false);
                    if (player.getY() < ground + .5) continue;
                    tried++;
                    Vec3 start = player.position();
                    boolean down = false;
                    for (int turn = 0; turn < 8 && !down; turn++) {
                        Player walker = h.makeMockPlayer();
                        walker.setPos(start.x, start.y, start.z);
                        walker.setOnGround(true);
                        Vec3 way = new Vec3(Math.sin(turn * Math.PI / 4), 0, Math.cos(turn * Math.PI / 4)).scale(.1);
                        for (int tick = 0; tick < 80; tick++) fall(car, walker, way, tick % 10 == 0);
                        down = walker.getY() < ground + .05;
                    }
                    if (!down) trapped.add(model + " " + car.worldToLocal(p(start)));
                }
            }
            h.assertTrue(tried > 100, "Test setup: the player lands on the " + model + " at many spots: " + tried);
            car.remove();
        }
        h.assertTrue(trapped.isEmpty(), "From anywhere on the roof, bonnet or boot lid the player can walk or jump down (" + trapped.size() + "): "
                + trapped.subList(0, Math.min(5, trapped.size())));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void playerUnderAnOpenDoorStaysUpright(GameTestHelper h) {
        floor(h, 1, 10, 0, 12);
        EntityVehicleF_Physics car = car(h, 5, 6, 0, "slimvehicle:g63");
        spawn(h, car);
        openDoor(car, 0);
        Player player = h.makeMockPlayer();
        Point3D door = openDoorBox(car).center;
        player.setPos(door.x, h.absoluteVec(new Vec3(0, 1, 0)).y, door.z);
        AABB standing = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1e-7);
        AABB crouching = player.getDimensions(Pose.CROUCHING).makeBoundingBox(player.position()).deflate(1e-7);
        h.assertFalse(h.getLevel().noCollision(player, standing) || h.getLevel().noCollision(player, crouching),
                "Test setup: the open door hangs into the player both standing and crouching");
        for (int tick = 0; tick < 3; tick++) player.tick();
        h.assertTrue(player.getPose() == Pose.STANDING, "A player under an open door stays upright instead of crawling, pose " + player.getPose());
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doorClosedOnPlayerNeverLetsThemIntoTheCabin(GameTestHelper h) {
        floor(h, 1, 10, 0, 12);
        EntityVehicleF_Physics car = car(h, 5, 6, 0);
        spawn(h, car);
        Player player = h.makeMockPlayer();
        double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;
        double side = car.worldToLocal(new Point3D(control(car, "door_fl").bounds.maxX, 0, 0)).x;
        Point3D start = car.localToWorld(new Point3D(side + player.getBbWidth() / 2 / 1.08 - .1, 0, .4));
        player.setPos(start.x, ground, start.z);
        h.assertTrue(car.hull().intersects(box(player.getBoundingBox().deflate(.02))) && !car.embeds(w(player)), "Test setup: the closed door reaches into the player's box");
        Vec3 inward = v(car.directionToWorld(new Point3D(-1, 0, 0)).scale(.1));
        double deepest = Double.MAX_VALUE;
        for (int tick = 0; tick < 40; tick++) {
            walk(car, player, inward);
            deepest = Math.min(deepest, car.worldToLocal(p(player.position())).x);
        }
        h.assertTrue(deepest > 1, "Walking into the door must not carry the player into the cabin: centre reached x=" + deepest);
        h.assertFalse(car.embeds(w(player)), "The player stays outside the car");

        openDoor(car, 0);
        int bestYaw = 0;
        double bestGap = 90;
        for (int yaw = 0; yaw < 90; yaw++) {
            car.setYaw(yaw);
            Hull.Box b = openDoorBox(car);
            Point3D a = b.axes[b.half.x > b.half.z ? 0 : 2];
            double gap = Math.abs(Math.abs(Math.toDegrees(Math.atan2(a.z, a.x))) % 90 - 45);
            if (gap < bestGap) {
                bestGap = gap;
                bestYaw = yaw;
            }
        }
        car.setYaw(bestYaw);
        Hull.Box open = openDoorBox(car);
        h.assertTrue(bestGap < 3, "Test setup: the open door lies at about 45 degrees to the world axes, off by " + bestGap);
        int thin = open.half.x < open.half.y ? (open.half.x < open.half.z ? 0 : 2) : (open.half.y < open.half.z ? 1 : 2);
        double thinHalf = thin == 0 ? open.half.x : thin == 1 ? open.half.y : open.half.z;
        double worst = 0;
        List<BoundingBox> slices = new ArrayList<>();
        open.collisionSlices(open.bounds.inflate(1), slices);
        for (BoundingBox slice : slices) {
            for (int i = 0; i < 8; i++) {
                Point3D corner = new Point3D((i & 1) == 0 ? slice.minX : slice.maxX, (i & 2) == 0 ? slice.minY : slice.maxY, (i & 4) == 0 ? slice.minZ : slice.maxZ);
                worst = Math.max(worst, Math.abs(corner.subtract(open.center).dot(open.axes[thin])) - thinHalf);
            }
        }
        h.assertTrue(worst < .08, "The collision of an open door at 45 degrees follows its surface: sticks out " + worst + " over " + slices.size() + " slices");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_wall_slide")
    public void glancingWallContactSlidesInsteadOfSticking(GameTestHelper h) {
        floor(h, 1, 24, 0, 44);
        for (int z = 0; z < 44; z++) for (int y = 1; y < 4; y++) h.setBlock(16, y, z, Blocks.STONE);
        EntityVehicleF_Physics car = car(h, 12.5, 5, -12);
        Player player = drive(car, h);
        h.assertTrue(car.canSpawnAtCurrentPosition(), "Course must be clear at start");
        double start = car.z();
        float yaw = car.yaw();
        for (int tick = 0; tick < 120; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            tick(car);
            h.assertTrue(car.canSpawnAtCurrentPosition(), "The hull must never enter the wall, tick=" + tick);
        }
        h.assertTrue(car.z() - start > 14, "A glancing wall contact must not stop the car: travel=" + (car.z() - start));
        h.assertTrue(car.yaw() > yaw + 2, "A nose hit against the wall must turn the car away from it: yaw=" + car.yaw());
        h.assertTrue(car.health() == DamageModel.FULL || car.health() > 90, "Brushing a wall at 18 km/h is cosmetic: " + car.health());
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_side_pillar")
    public void sideContactWithPillarKeepsTheCarMoving(GameTestHelper h) {
        floor(h, 1, 20, 0, 40);
        EntityVehicleF_Physics car = car(h, 8.5, 8.5, 0);
        double side = car.hull().bounds().maxX - car.x();
        car.setPosition(h.absoluteVec(new Vec3(12, 1, 6)).x - .03 - side, car.y(), car.z());
        for (int y = 1; y < 3; y++) h.setBlock(12, y, 6, Blocks.STONE);
        double start = car.z();
        car.push(new Point3D(.25, 0, .6), 0);
        for (int tick = 0; tick < 20; tick++) {
            tick(car);
            h.assertTrue(car.canSpawnAtCurrentPosition(), "Hull must not enter the pillar, tick=" + tick);
        }
        h.assertTrue(car.z() - start > 3, "Brushing a pillar sideways must not stop the car dead: travel=" + (car.z() - start));
        h.assertFalse(car.punctured(2), "A light side touch must not burst the tyre");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_wall_steer")
    public void steersAwayFromWallItTouches(GameTestHelper h) {
        floor(h, 1, 24, 0, 44);
        for (int z = 0; z < 44; z++) for (int y = 1; y < 4; y++) h.setBlock(14, y, z, Blocks.STONE);
        EntityVehicleF_Physics car = car(h, 10, 6, 0);
        double side = car.hull().bounds().maxX - car.x();
        car.setPosition(h.absoluteVec(new Vec3(14, 1, 0)).x - .02 - side, car.y(), car.z());
        Player player = drive(car, h);
        float yaw = car.yaw();
        int stalls = 0;
        for (int tick = 0; tick < 80; tick++) {
            float before = car.yaw();
            car.acceptInput(w(player), 1, 0, -1, false);
            tick(car);
            if (tick >= 5 && car.yaw() - before < 1e-4) stalls++;
            h.assertTrue(car.canSpawnAtCurrentPosition(), "The hull must never enter the wall, tick=" + tick);
        }
        h.assertTrue(stalls == 0, "Steering away from a wall must never be refused: stalled ticks=" + stalls);
        h.assertTrue(car.yaw() - yaw > 90, "A car against a wall must steer away as freely as in the open: turned=" + (car.yaw() - yaw));
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_cars", timeoutTicks = 200)
    public void movingCarShovesParkedCar(GameTestHelper h) {
        floor(h, 1, 14, 0, 40);
        EntityVehicleF_Physics moving = car(h, 7.5, 5, 0);
        EntityVehicleF_Physics parked = car(h, 7.5, 5, 0);
        double nose = moving.hull().bounds().maxZ - moving.z();
        double tail = parked.z() - parked.hull().bounds().minZ;
        parked.setPosition(parked.x(), parked.y(), moving.z() + nose + 1.2 + tail);
        spawn(h, moving);
        spawn(h, parked);
        List<ChunkPos> forced = new ArrayList<>();
        BlockPos a = h.absolutePos(new BlockPos(0, 0, 0));
        BlockPos b = h.absolutePos(new BlockPos(14, 0, 40));
        for (int x = Math.min(a.getX(), b.getX()) >> 4; x <= Math.max(a.getX(), b.getX()) >> 4; x++) {
            for (int z = Math.min(a.getZ(), b.getZ()) >> 4; z <= Math.max(a.getZ(), b.getZ()) >> 4; z++) {
                forced.add(new ChunkPos(x, z));
                h.getLevel().setChunkForced(x, z, true);
            }
        }
        double parkedStart = parked.z();
        boolean[] shoved = {false};
        h.onEachTick(() -> {
            boolean ticking = forced.stream().allMatch(c -> h.getLevel().isPositionEntityTicking(c.getWorldPosition()));
            if (!shoved[0] && ticking && moving.ticks() > 0 && parked.ticks() > 0) {
                moving.push(new Point3D(0, 0, .5), 0);
                shoved[0] = true;
            }
            h.assertFalse(moving.hull().intersects(parked.hull(), Point3D.ZERO), "Cars must not pass into each other");
        });
        h.succeedWhen(() -> {
            h.assertTrue(shoved[0] && parked.z() - parkedStart > .5 && parked.speed() == 0 && moving.speed() == 0,
                    "Waiting for the shove to end: moved=" + (parked.z() - parkedStart));
            for (ChunkPos chunk : forced) h.getLevel().setChunkForced(chunk.x, chunk.z, false);
            h.assertTrue(parked.z() - parkedStart < 6, "On its parking brake it must not roll away: moved=" + (parked.z() - parkedStart));
            h.assertTrue(parked.health() < DamageModel.FULL && moving.health() < DamageModel.FULL, "Both cars take the impact");
            moving.remove();
            parked.remove();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_smash")
    public void knocksDownGlassPanesOnlyAtSpeed(GameTestHelper h) {
        floor(h, 1, 22, 0, 34);
        for (int x = 1; x < 22; x++) for (int y = 1; y < 3; y++) h.setBlock(x, y, 14, Blocks.GLASS_PANE);
        double panes = h.absoluteVec(new Vec3(0, 0, 14.4375)).z;
        EntityVehicleF_Physics fast = car(h, 5.5, 6, 0);
        EntityVehicleF_Physics slow = car(h, 15.5, 6, 0);
        noseTo(fast, panes, 1);
        noseTo(slow, panes, .2);
        double start = fast.z();
        fast.push(new Point3D(0, 0, .6), 0);
        slow.push(new Point3D(0, 0, .15), 0);
        for (int tick = 0; tick < 25; tick++) {
            tick(fast);
            tick(slow);
        }
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 14)).isAir() && h.getBlockState(new BlockPos(5, 2, 14)).isAir(), "A fast car knocks down the panes in its way");
        h.assertTrue(fast.z() - start > 4, "...and keeps going: travel=" + (fast.z() - start));
        h.assertTrue(h.getBlockState(new BlockPos(15, 1, 14)).is(Blocks.GLASS_PANE), "A creeping car stops at a pane");
        h.assertTrue(slow.hull().bounds().maxZ <= panes + .001, "The creeping car stays in front of the pane");
        fast.remove();
        slow.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_pedestrian")
    public void runsOverAtSpeedAndShovesAtWalkingPace(GameTestHelper h) {
        floor(h, 1, 22, 0, 30);
        EntityVehicleF_Physics fast = car(h, 5.5, 6, 0);
        EntityVehicleF_Physics slow = car(h, 15.5, 6, 0);
        double noseFast = fast.hull().bounds().maxZ;
        double noseSlow = slow.hull().bounds().maxZ;
        Pig struck = h.spawn(EntityType.PIG, new Vec3(5.5, 1, noseFast - h.absoluteVec(Vec3.ZERO).z + 1.5));
        Pig nudged = h.spawn(EntityType.PIG, new Vec3(15.5, 1, noseSlow - h.absoluteVec(Vec3.ZERO).z + .6));
        struck.setNoAi(true);
        nudged.setNoAi(true);
        fast.push(new Point3D(0, 0, .7), 0);
        slow.push(new Point3D(0, 0, .15), 0);
        for (int tick = 0; tick < 12; tick++) {
            tick(fast);
            tick(slow);
        }
        h.assertTrue(!struck.isAlive() || struck.getHealth() < struck.getMaxHealth(), "A car at 50 km/h hurts what it hits");
        DamageSource source = struck.getLastDamageSource();
        h.assertTrue(source != null && source.is(WrapperWorld.RUN_OVER), "The hit uses the run-over damage type: " + source);
        h.assertTrue(nudged.getHealth() == nudged.getMaxHealth(), "A car at walking pace only shoves");
        fast.remove();
        slow.remove();
        struck.discard();
        nudged.discard();
        h.succeed();
    }
}
