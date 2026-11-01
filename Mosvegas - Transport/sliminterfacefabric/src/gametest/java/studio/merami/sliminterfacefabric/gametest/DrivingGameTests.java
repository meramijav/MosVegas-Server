package studio.merami.sliminterfacefabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.VehicleAction;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class DrivingGameTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, batch = "terrain_corners")
    public void individualWheelsFollowSlabsAndHoles(GameTestHelper h) {
        for (int wheel = 0; wheel < 4; wheel++) {
            for (boolean hole : new boolean[]{false, true}) {
                for (int x = 1; x < 9; x++) {
                    for (int z = 0; z < 9; z++) {
                        h.setBlock(x, 0, z, Blocks.STONE);
                        h.setBlock(x, 1, z, Blocks.AIR);
                    }
                }
                EntityVehicleF_Physics car = createAt(h, new Vec3(4.5, 1.5, 4.35));
                BlockPos at = BlockPos.containing(v(car.localToWorld(car.wheel(wheel).center())));
                BlockPos floor = new BlockPos(at.getX(), h.absolutePos(new BlockPos(0, 0, 0)).getY(), at.getZ());
                h.getLevel().setBlockAndUpdate(floor.above(), hole ? Blocks.AIR.defaultBlockState() : Blocks.STONE_SLAB.defaultBlockState());
                if (hole) {
                    h.getLevel().setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
                    h.getLevel().setBlockAndUpdate(floor.below(), Blocks.STONE.defaultBlockState());
                }
                for (int tick = 0; tick < 70; tick++) tick(car);
                float expectedPitch = (wheel < 2 ? -1 : 1) * (hole ? -1 : 1);
                float expectedRoll = (wheel % 2 == 0 ? 1 : -1) * (hole ? -1 : 1);
                h.assertTrue(car.bodyPitch * expectedPitch > .004, "Pitch must follow the affected corner " + wheel + " hole=" + hole + " pitch=" + car.bodyPitch);
                h.assertTrue(car.bodyRoll * expectedRoll > .008, "Roll must follow the affected corner " + wheel + " hole=" + hole);
                assertWheelsAttached(h, car, "wheel=" + wheel + " hole=" + hole);
                int opposite = wheel ^ 3;
                double travel = (car.wheel(wheel).travel - car.wheel(opposite).travel) * (hole ? -1 : 1);
                h.assertTrue(travel > .12, "Wheel travel must be independent " + wheel + " travel=" + travel);
                if (hole) h.assertTrue(travel < .23 && car.bodyHeave > -.075, "A deep hole must only extend the short suspension travel");
                car.remove();
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "terrain_drive")
    public void drivesOverHalfBlocksAndTrapdoors(GameTestHelper h) {
        for (BlockState obstacle : new BlockState[]{Blocks.STONE_SLAB.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState()}) {
            for (int x = 1; x < 9; x++) {
                for (int z = 0; z < 22; z++) {
                    h.setBlock(x, 0, z, Blocks.STONE);
                    h.setBlock(x, 1, z, Blocks.AIR);
                }
            }
            for (int x = 1; x < 8; x++) h.setBlock(x, 1, 7, obstacle);
            EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.03, 3.4));
            Player player = drive(car, h);
            double start = car.z();
            double peak = car.y();
            for (int tick = 0; tick < 105; tick++) {
                car.acceptInput(w(player), 1, 0, 0, false);
                tick(car);
                peak = Math.max(peak, car.y());
                assertWheelsAttached(h, car, obstacle + " tick=" + tick);
                assertBodyAboveGround(h, car, obstacle + " tick=" + tick);
            }
            h.assertTrue(car.z() > start + 7, "Car must pass obstacle without hitting an invisible wall: " + obstacle + " distance=" + (car.z() - start));
            h.assertTrue(peak > h.absoluteVec(new Vec3(0, 1.1, 0)).y, "Wheels must physically climb the obstacle");
            car.remove();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "terrain_angles")
    public void climbsAtAnglesAndAcrossContactSeams(GameTestHelper h) {
        for (int scenario = 0; scenario < 5; scenario++) {
            for (int x = 1; x < 24; x++) {
                for (int z = 64; z < 91; z++) {
                    h.setBlock(x, 0, z, Blocks.STONE);
                    h.setBlock(x, 1, z, Blocks.AIR);
                }
            }
            boolean reverse = scenario == 3;
            boolean oneWheel = scenario == 4;
            int curb = 64 + (reverse ? 15 : 10);
            for (int x = 1; x < 24; x++) if (!oneWheel || x == 13) h.setBlock(x, 1, curb, Blocks.STONE_SLAB);
            EntityVehicleF_Physics car = create(h);
            car.setPosition(p(h.absoluteVec(new Vec3(oneWheel ? 12.5 : 12, 1.015, 64 + (reverse ? 20 : 6)))));
            car.setYaw(scenario == 1 ? 30 : scenario == 2 ? -30 : reverse ? 180 : 0);
            Player player = drive(car, h);
            h.assertTrue(car.canSpawnAtCurrentPosition(), "Test course must be clear at start, scenario=" + scenario);
            double start = car.z();
            double oldBody = car.y() + car.bodyHeave * car.scale();
            double largestJump = 0;
            for (int tick = 0; tick < 110; tick++) {
                if (tick % 3 == 0) car.setOnGround(false);
                car.acceptInput(w(player), 1, 0, 0, false);
                tick(car);
                double body = car.y() + car.bodyHeave * car.scale();
                if ((car.z() - start) * (reverse ? -1 : 1) < 11) largestJump = Math.max(largestJump, Math.abs(body - oldBody));
                oldBody = body;
            }
            h.assertTrue((car.z() - start) * (reverse ? -1 : 1) > 7, "Car must climb from rest, angled or with only one wheel: scenario=" + scenario + " travel=" + (car.z() - start));
            h.assertTrue(largestJump < .2, "Collision steps must not jerk the rendered body: " + largestJump);
            car.remove();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "terrain_clearance")
    public void stepCannotPushRoofThroughCeiling(GameTestHelper h) {
        for (int x = 1; x < 9; x++) {
            for (int z = 0; z < 15; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                h.setBlock(x, 1, z, Blocks.AIR);
                h.setBlock(x, 3, z, Blocks.STONE);
            }
        }
        for (int x = 1; x < 9; x++) h.setBlock(x, 1, 7, Blocks.STONE_SLAB);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.015, 3.4));
        Player player = drive(car, h);
        for (int tick = 0; tick < 90; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            tick(car);
        }
        h.assertTrue(car.z() < h.absoluteVec(new Vec3(0, 0, 7)).z, "Low ceiling must prevent a full step");
        h.assertTrue(car.hull().bounds().maxY <= h.absoluteVec(new Vec3(0, 3, 0)).y + .002, "Roof cannot penetrate ceiling");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "automatic")
    public void automaticEngagesDriveAndShiftsByItself(GameTestHelper h) {
        floor(h, 1, 10, 0, 70);
        EntityVehicleF_Physics car = create(h);
        car.setDefinition("g63");
        Vec3 at = h.absoluteVec(new Vec3(5, 1, 6));
        car.moveTo(at.x, at.y + .02, at.z, 0, 0);
        car.settleOnGround(1);
        Player player = h.makeMockPlayer();
        place(player, car.position());
        own(car, player);
        ride(player, car);
        car.action(w(player), VehicleAction.ENGINE);
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE) && car.gear() == 0, "The engine starts in neutral");
        int highest = 0;
        for (int tick = 0; tick < 50; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            step(car);
            h.assertTrue(car.gear() >= highest, "Accelerating never shifts down");
            highest = car.gear();
        }
        h.assertTrue(car.gear() >= 3 && car.speed() * 72 > 60, "Throttle engages D and the box shifts up by itself: gear " + car.gear() + ", " + car.speed() * 72 + " km/h");
        for (int tick = 0; tick < 160 && car.speed() > .001; tick++) {
            car.acceptInput(w(player), 0, 1, 0, false);
            step(car);
        }
        h.assertTrue(car.gear() == 1 && Math.abs(car.speed()) < .01, "Stopping shifts back to first: gear " + car.gear());
        place(player, car.position());
        for (int step = 0; step < 2; step++) {
            for (int tick = 0; tick < 10; tick++) {
                car.acceptInput(w(player), 0, 1, 0, false);
                step(car);
            }
            car.action(w(player), VehicleAction.GEAR_DOWN);
        }
        h.assertTrue(car.gear() == -1, "Z twice from D selects reverse: gear " + car.gear());
        for (int tick = 0; tick < 10; tick++) {
            step(car);
        }
        car.action(w(player), VehicleAction.GEAR_UP);
        h.assertTrue(car.gear() == 0, "X from reverse returns to neutral");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "exhaust_pops", timeoutTicks = 200)
    public void startIsStampedAndPopsComeFromTheServer(GameTestHelper h) {
        floor(h, 1, 10, 0, 70);
        EntityVehicleF_Physics volga = car(h, 3, 6, 0);
        Player player = h.makeMockPlayer();
        place(player, volga.position());
        own(volga, player);
        ride(player, volga);
        volga.action(w(player), VehicleAction.ENGINE);
        h.assertTrue(volga.flag(EntityVehicleF_Physics.ENGINE) && volga.startTick() == (int) h.getLevel().getGameTime(),
                "Starting stamps the server time the clients time the start sound, catch and smoke from");
        volga.action(w(player), VehicleAction.GEAR_UP);
        double limit = volga.definition().engine.limitRpm * .65;
        for (int tick = 0; tick < 80 && volga.rpm() <= limit; tick++) {
            volga.acceptInput(w(player), 1, 0, 0, false);
            step(volga);
        }
        int before = volga.pops();
        h.assertTrue(volga.rpm() > limit && before == 0, "First gear revs up without a pop: " + volga.rpm() + " rpm, " + before + " pops");
        volga.acceptInput(w(player), 0, 0, 0, false);
        step(volga);
        h.assertTrue(volga.pops() == before + 1, "Lifting off at high revs pops once, sound and flames from one event: " + volga.pops());
        player.stopRiding();
        volga.remove();
        EntityVehicleF_Physics g63 = car(h, 7, 6, 0);
        g63.setDefinition("g63");
        g63.settleOnGround(1);
        place(player, g63.position());
        own(g63, player);
        ride(player, g63);
        g63.action(w(player), VehicleAction.ENGINE);
        int shifts = 0;
        int gear = g63.gear();
        for (int tick = 0; tick < 90; tick++) {
            g63.acceptInput(w(player), 1, 0, 0, false);
            step(g63);
            if (g63.gear() > Math.max(1, gear)) shifts++;
            gear = g63.gear();
        }
        h.assertTrue(shifts > 0 && g63.pops() > 0 && g63.pops() <= shifts, "The AMG pops on full-throttle upshifts: " + g63.pops() + " pops in " + shifts + " shifts");
        g63.remove();
        h.succeed();
    }


    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void driverCanMoveAndStopAtWall(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        for (int x = 1; x < 7; x++) for (int y = 1; y < 4; y++) h.setBlock(x, y, 7, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.03, 3.4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        place(player, car.position());
        own(car, player);
        openDoor(car, 0);
        car.board(w(player), 0);
        h.assertTrue(car.driver(w(player)), "Seat zero must control the car");
        h.assertFalse(entity(car).isControlledByLocalInstance(), "Driver must still receive server position updates");
        car.action(w(player), VehicleAction.ENGINE);
        car.shift(1, w(player));
        double start = car.z();
        for (int i = 1; i <= 80; i++) {
            final int tick = i;
            h.runAtTickTime(tick, () -> car.acceptInput(w(player), 1, 0, 0, false));
        }
        h.runAtTickTime(85, () -> {
            h.assertTrue(car.z() > start + .12, "First gear must move the car with driver aboard");
            h.assertTrue(car.z() < start + .8, "Front bumper must stop at wall");
            h.assertTrue(Math.abs(car.bodyRoll) < .015, "Straight acceleration must not roll sideways");
            h.assertTrue(car.fuel() < 35, "Running engine must consume fuel");
            h.succeed();
        });
    }



    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnRestsOnGroundWithoutFalling(GameTestHelper h) {
        for (int x = 1; x < 10; x++) {
            for (int z = 1; z < 10; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                h.setBlock(x, 1, z, Blocks.SHORT_GRASS);
            }
        }
        Vec3 start = h.absoluteVec(new Vec3(5, 2.6, 5));
        EntityVehicleF_Physics car = create(h);
        car.moveTo(start.x, start.y, start.z, 0, 0);
        car.settleOnGround(4);
        h.assertTrue(Math.abs(car.y() - h.absoluteVec(new Vec3(0, 1, 0)).y) < .03, "Car must be placed on the ground, not dropped: y=" + car.y());
        spawn(h, car);
        double y = car.y();
        for (int tick = 0; tick < 10; tick++) tick(car);
        h.assertTrue(Math.abs(car.y() - y) < .005, "Settled car must not move vertically");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "terrain_turn")
    public void turnsSmoothlyAcrossPathEdges(GameTestHelper h) {
        for (int x = 1; x < 41; x++) {
            for (int z = 1; z < 41; z++) {
                h.setBlock(x, 0, z, x % 3 == 0 && z % 3 == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT_PATH);
                h.setBlock(x, 1, z, Blocks.AIR);
            }
        }
        Vec3 start = h.absoluteVec(new Vec3(12.5, 2, 20.5));
        EntityVehicleF_Physics car = create(h);
        car.moveTo(start.x, start.y, start.z, 0, 0);
        car.settleOnGround(2);
        spawn(h, car);
        Player player = drive(car, h);
        double total = 0;
        int counted = 0;
        double[] deltas = new double[160];
        for (int tick = 0; tick < 160; tick++) {
            float before = car.yaw();
            car.acceptInput(w(player), 1, 0, 1, false);
            tick(car);
            deltas[tick] = Math.abs(car.yaw() - before);
            if (tick >= 50) {
                total += deltas[tick];
                counted++;
            }
        }
        double mean = total / counted;
        h.assertTrue(mean > .5, "Car must keep turning: " + mean);
        for (int tick = 50; tick < 160; tick++) {
            h.assertTrue(deltas[tick] > mean * .6, "A steady turn must not stall on a path edge: tick=" + tick + " yaw step=" + deltas[tick] + " mean=" + mean);
        }
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "pose_sync")
    public void movingCarSendsAPoseEveryTickAndAParkedOneNone(GameTestHelper h) {
        floor(h, 1, 40, 1, 40);
        EntityVehicleF_Physics car = car(h, 10.5, 6.5, 0);
        Player player = drive(car, h);
        for (int tick = 0; tick < 5; tick++) {
            step(car);
        }
        int parked = car.poseTick();
        for (int tick = 0; tick < 20; tick++) {
            step(car);
        }
        h.assertTrue(car.poseTick() == parked, "A car at rest must not send poses: " + parked + " -> " + car.poseTick());
        for (int tick = 0; tick < 40; tick++) {
            car.acceptInput(w(player), 1, 0, tick > 20 ? 1 : 0, false);
            step(car);
            if (tick >= 5) h.assertTrue(car.poseTick() == car.ticks(), "A moving car must send its pose every tick: tick=" + tick + " pose=" + car.poseTick() + " now=" + car.ticks());
        }
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void multipleCarsKeepIndependentControls(GameTestHelper h) {
        EntityVehicleF_Physics[] cars = new EntityVehicleF_Physics[4];
        for (int i = 0; i < 4; i++) {
            cars[i] = createAt(h, new Vec3(20 + i * 8, 2, 4));
            spawn(h, cars[i]);
            Player player = h.makeMockPlayer();
            own(cars[i], player);
            place(player, cars[i].position());
            openDoor(cars[i], 0);
            cars[i].board(w(player), 0);
            h.assertTrue(cars[i].driver(w(player)), "Every vehicle must accept its own driver");
            cars[i].action(w(player), VehicleAction.LIGHTS);
            h.assertTrue(cars[i].flag(EntityVehicleF_Physics.LIGHTS), "Fourth vehicle must still accept controls");
            Point3D from = cars[i].localToWorld(new Point3D(-3, .9, .3));
            Point3D to = cars[i].localToWorld(new Point3D(0, .9, .3));
            EntityVehicleF_Physics car = cars[i];
            h.assertTrue(car.targetAlong(from, to).map(hit -> hit.zone().part() == car.opening("door_fr")).orElse(false), "Other door must stay interactable on every car");
        }
        for (EntityVehicleF_Physics car : cars) car.remove();
        h.succeed();
    }
}
