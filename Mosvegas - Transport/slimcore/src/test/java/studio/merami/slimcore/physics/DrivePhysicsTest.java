package studio.merami.slimcore.physics;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrivePhysicsTest {
    private static final DrivePhysics.Input GAS = new DrivePhysics.Input(1, 0, 0, false);
    private static final DrivePhysics.Input BRAKE = new DrivePhysics.Input(0, 1, 0, false);
    private static final double KMH = 72;
    private static final double G = 9.81 / 400;

    private record Run(double seconds100, double seconds200, double top, int gear) {}

    private static Run floorIt(JSONVehicle definition, int seconds) {
        DrivePhysics.Tune tune = definition.tune;
        double speed = 0;
        int gear = 1;
        int since = 100;
        double to100 = -1;
        double to200 = -1;
        for (int tick = 0; tick < seconds * 20; tick++) {
            if (since++ >= 8) {
                int next = DrivePhysics.automaticGear(tune, gear, speed, 1, 1, 0, definition.transmission.shiftUp, definition.transmission.shiftDown);
                assertTrue(next >= gear, "Full throttle never shifts down: " + gear + " -> " + next + " at " + speed * KMH);
                if (next != gear) {
                    gear = next;
                    since = 0;
                }
            }
            speed = DrivePhysics.step(tune, speed, 0, 0, gear, true, 100, 0, GAS).forward();
            if (to100 < 0 && speed * KMH >= 100) to100 = (tick + 1) / 20.0;
            if (to200 < 0 && speed * KMH >= 200) to200 = (tick + 1) / 20.0;
        }
        return new Run(to100, to200, speed * KMH, gear);
    }

    private static double brakingDistance(DrivePhysics.Tune tune, double kmh, int gear) {
        double speed = kmh / KMH;
        double distance = 0;
        float pedal = 0;
        for (int tick = 0; tick < 2000 && speed > 0; tick++) {
            pedal = DrivePhysics.brakePressure(pedal, 1);
            speed = DrivePhysics.step(tune, speed, 0, 0, gear, true, 100, 0, new DrivePhysics.Input(0, pedal, 0, false)).forward();
            distance += speed;
        }
        return distance;
    }

    @Test
    void onlyFirstAndReverseLaunchFromRest() {
        DrivePhysics.Tune tune = TestPacks.vehicle("volga").tune;
        assertTrue(DrivePhysics.step(tune, 0, 0, 0, 1, true, 35, 0, GAS).forward() > 0, "First must launch");
        assertEquals(0, DrivePhysics.step(tune, 0, 0, 0, 2, true, 35, 0, GAS).forward(), "Second must not launch");
        assertTrue(DrivePhysics.step(tune, 0, 0, 0, -1, true, 35, 0, GAS).forward() < 0, "Reverse launches backwards");
        assertFalse(DrivePhysics.canShift(1, 2, 0, 5), "Cannot select second at rest");
        assertTrue(DrivePhysics.canShift(5, 4, 0, 5), "A stopped car must be able to downshift from fifth");
        assertFalse(DrivePhysics.canShift(1, -1, .2, 5), "No reverse while rolling forward");
    }

    @Test
    void manualCarsReachTheirTopSpeedGearByGear() {
        for (String id : new String[]{"volga", "police", "ambulance"}) {
            JSONVehicle definition = TestPacks.vehicle(id);
            double speed = 0;
            for (int gear = 1; gear <= definition.gears(); gear++) {
                for (int tick = 0; tick < 1000; tick++) {
                    double next = DrivePhysics.step(definition.tune, speed, 0, 0, gear, true, 35, 0, GAS).forward();
                    assertTrue(Math.abs(next - speed) < .025, "Transmission must change speed continuously");
                    speed = next;
                }
                assertTrue(speed <= DrivePhysics.speedLimit(definition.tune, gear) + .001, "Gear must respect its speed governor");
            }
            assertTrue(speed * KMH > 98 && speed * KMH <= 100, id + " reaches about 100 km/h: " + speed * KMH);
            double downshift = DrivePhysics.step(definition.tune, speed, 0, 0, 1, true, 35, 0, GAS).forward();
            assertTrue(speed - downshift < .025, "Downshift must not teleport or clamp momentum");
        }
    }

    @Test
    void g63AcceleratesLikeTheRealCar() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        Run run = floorIt(g63, 90);
        assertTrue(run.seconds100() > 3.6 && run.seconds100() < 5, "0-100 km/h of a G 63 takes about 4.5 s: " + run.seconds100());
        assertTrue(run.seconds200() > 13 && run.seconds200() < 21, "0-200 km/h takes about 17 s: " + run.seconds200());
        assertTrue(run.gear() == 9 && run.top() > 238 && run.top() <= 240.001, "Top speed is 240 km/h in ninth: " + run.top() + " in " + run.gear());
    }

    @Test
    void topSpeedIsWherePowerMeetsDrag() {
        for (String id : new String[]{"volga", "g63", "scooter"}) {
            DrivePhysics.Tune tune = TestPacks.vehicle(id).tune;
            double top = tune.maxSpeed();
            double air = DrivePhysics.drag(tune) * top * top;
            assertTrue(air > 0, id + " has air drag");
            double free = top;
            int ticks = 0;
            while (free > top / 2 && ticks++ < 20 * 600) free = DrivePhysics.step(tune, free, 0, 0, tune.gearbox().gears(), false, 100, 0, DrivePhysics.IDLE).forward();
            assertTrue(free <= top / 2 && ticks > 20, id + " coasts down from its top speed to half of it with the engine off, slowly: " + ticks / 20.0 + " s");
        }
        DrivePhysics.Tune g63 = TestPacks.vehicle("g63").tune;
        double kmh100 = 100 / KMH;
        double decel = (kmh100 - DrivePhysics.step(g63, kmh100, 0, 0, 0, true, 100, 0, DrivePhysics.IDLE).forward()) * 400;
        assertTrue(decel > .25 && decel < .8, "Air and tyres slow a G 63 at 100 km/h by about half a metre per second squared: " + decel);
    }

    @Test
    void brakesStopWithinTyreGrip() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        double distance = brakingDistance(g63.tune, 100, 1);
        assertTrue(distance > 35 && distance < 55, "A G 63 stops from 100 km/h in about 40-50 m with the pedal travel: " + distance);
        double perTick = 1 - DrivePhysics.step(g63.tune, 1, 0, 0, 1, true, 100, 0, BRAKE).forward();
        assertTrue(perTick <= DrivePhysics.friction(g63.tune) * 1.15 + .002, "Braking never exceeds the tyre grip: " + perTick * 400 / 9.81 + " g");
        DrivePhysics.Tune volga = TestPacks.vehicle("volga").tune;
        double stopping = .78;
        for (int i = 0; i < 12; i++) stopping = DrivePhysics.step(volga, stopping, 0, 0, 5, true, 35, 0, new DrivePhysics.Input(1, 1, 0, false)).forward();
        assertTrue(stopping > .4 && stopping < .78, "Service brake must retain momentum after twelve ticks: " + stopping);
        for (int i = 0; i < 60; i++) stopping = DrivePhysics.step(volga, stopping, 0, 0, 5, true, 35, 0, new DrivePhysics.Input(1, 1, 0, false)).forward();
        assertEquals(0, stopping, "Service brake must eventually stop without reversing");
        float pressure = DrivePhysics.brakePressure(0, 1);
        assertTrue(pressure > 0 && pressure < .15, "Pedal pressure must ramp up");
    }

    @Test
    void fullLockTurnsTightlyAndHoldsTheLine() {
        double[] speeds = {30, 60, 90};
        double[] widest = {9.5, 13, 26};
        for (String id : new String[]{"g63", "volga"}) {
            JSONVehicle definition = TestPacks.vehicle(id);
            DrivePhysics.Tune tune = definition.tune;
            double limit = DrivePhysics.cornering(tune) / G;
            for (int run = 0; run < speeds.length; run++) {
                double kmh = speeds[run];
                double forward = kmh / KMH;
                double lateral = 0;
                double steer = 0;
                int gear = 1;
                while (gear < tune.gearbox().gears() && DrivePhysics.speedLimit(tune, gear) * .8 < forward) gear++;
                DrivePhysics.Drift drift = new DrivePhysics.Drift(0, 0);
                DrivePhysics.Result result = null;
                double worst = 0;
                for (int tick = 0; tick < 40; tick++) {
                    DrivePhysics.Input pedals = new DrivePhysics.Input(Math.hypot(forward, lateral) * KMH < kmh ? 1 : 0, 0, 1, false);
                    double target = definition.physics.steerLock / (1 + Math.abs(forward) * definition.physics.steerFalloff);
                    steer += Math.max(-.05, Math.min(.05, target - steer));
                    drift = DrivePhysics.drift(tune, forward, steer, pedals, drift);
                    result = DrivePhysics.step(tune, forward, lateral, steer, gear, true, 100, 0, pedals, drift);
                    forward = result.forward();
                    lateral = result.lateral();
                    worst = Math.max(worst, Math.toDegrees(Math.atan2(Math.abs(lateral), Math.abs(forward))));
                }
                double speed = Math.hypot(forward, lateral);
                double yawRate = Math.abs(Math.toRadians(result.yawDelta()));
                double radius = speed / yawRate;
                double g = speed * yawRate / G;
                assertTrue(speed * KMH > kmh * .8 && speed * KMH < kmh + 4, id + " keeps most of its speed through a two-second turn: " + speed * KMH + " of " + kmh);
                assertTrue(radius < widest[run], id + " at " + kmh + " km/h turns on a radius of at most " + widest[run] + " blocks: " + radius);
                assertTrue(g < limit, id + " at " + kmh + " km/h corners within its tyres (" + limit + " g): " + g + " g");
                assertTrue(worst < 3 && result.slip() < .05, id + " at " + kmh + " km/h holds the line without sliding or squealing: " + worst + " deg");
            }
        }
    }

    @Test
    void keyboardThrottleOrBrakeInATurnDoesNotSlide() {
        for (String id : new String[]{"g63", "volga"}) {
            JSONVehicle definition = TestPacks.vehicle(id);
            DrivePhysics.Tune tune = definition.tune;
            for (DrivePhysics.Input pedals : new DrivePhysics.Input[]{new DrivePhysics.Input(1, 0, 1, false), new DrivePhysics.Input(0, 1, 1, false)}) {
                for (double kmh : new double[]{15, 30, 45, 60, 90, 120}) {
                    double forward = kmh / KMH;
                    double lateral = 0;
                    double steer = 0;
                    int gear = 1;
                    while (gear < tune.gearbox().gears() && DrivePhysics.speedLimit(tune, gear) * .9 < forward) gear++;
                    DrivePhysics.Drift drift = new DrivePhysics.Drift(0, 0);
                    double worst = 0;
                    for (int tick = 0; tick < 60; tick++) {
                        double target = definition.physics.steerLock / (1 + Math.abs(forward) * definition.physics.steerFalloff);
                        steer += Math.max(-.05, Math.min(.05, target - steer));
                        drift = DrivePhysics.drift(tune, forward, steer, pedals, drift);
                        DrivePhysics.Result result = DrivePhysics.step(tune, forward, lateral, steer, gear, true, 100, 0, pedals, drift);
                        forward = result.forward();
                        lateral = result.lateral();
                        if (Math.hypot(forward, lateral) > .03) worst = Math.max(worst, Math.toDegrees(Math.atan2(Math.abs(lateral), Math.abs(forward))));
                    }
                    assertTrue(worst < 3, id + " holds its line with throttle " + pedals.throttle() + " and brake " + pedals.brake()
                            + " through a full-lock turn from " + kmh + " km/h, slip angle " + worst);
                }
            }
        }
    }

    @Test
    void revsFollowTheWheelsAndIdleInNeutral() {
        DrivePhysics.Tune g63 = TestPacks.vehicle("g63").tune;
        DrivePhysics.Tune volga = TestPacks.vehicle("volga").tune;
        assertEquals(g63.revs().idle(), DrivePhysics.engineRpm(g63, 0, 50 / KMH, 0, true), 1e-9, "Coasting in neutral the engine idles");
        assertEquals(0, DrivePhysics.engineRpm(g63, 3, 50 / KMH, 1, false), "An engine that is off has no revs");
        double third = DrivePhysics.speedLimit(volga, 3);
        assertEquals(volga.revs().atLimit() / 2, DrivePhysics.engineRpm(volga, 3, third / 2, 0, true), 1e-6, "In gear the revs follow the wheels");
        assertEquals(DrivePhysics.engineRpm(volga, 3, third / 2, 0, true), DrivePhysics.engineRpm(volga, 3, third / 2, 1, true), 1e-9,
                "A manual's revs do not jump with the throttle once the clutch is in");
        double stall = DrivePhysics.engineRpm(g63, 1, 0, 1, true);
        assertTrue(stall > 2000 && stall < 2600, "The automatic's converter holds the engine near its stall speed when pulling away: " + stall);
        assertTrue(DrivePhysics.engineRpm(g63, 2, 40 / KMH, 1, true) < DrivePhysics.engineRpm(g63, 1, 40 / KMH, 1, true), "A higher gear turns slower");
        assertEquals(1000 + 260, DrivePhysics.revsToward(1000, 5000), 1e-9, "Revs climb at the engine's pace");
        assertEquals(5000 - 380, DrivePhysics.revsToward(5000, 1000), 1e-9, "and fall a little faster");
        assertEquals(800, DrivePhysics.revsToward(0, 800), 1e-9, "A start goes straight to idle");
    }

    @Test
    void handbrakeStartsADriftAndGripEndsIt() {
        DrivePhysics.Tune tune = TestPacks.vehicle("volga").tune;
        double straight = .5;
        double lateral = 0;
        DrivePhysics.Drift drift = new DrivePhysics.Drift(0, 0);
        for (int i = 0; i < 200; i++) {
            drift = DrivePhysics.drift(tune, straight, 0, GAS, drift);
            DrivePhysics.Result r = DrivePhysics.step(tune, straight, lateral, 0, 5, true, 35, 0, GAS, drift);
            straight = r.forward();
            lateral = r.lateral();
        }
        assertTrue(Math.abs(lateral) < 1e-9 && Math.abs(drift.yawRate()) < 1e-9, "Straight fifth gear cannot create lateral force");
        DrivePhysics.Input hand = new DrivePhysics.Input(1, 0, 1, true);
        double f = .55;
        double l = 0;
        double maxSlip = 0;
        for (int i = 0; i < 20; i++) {
            drift = DrivePhysics.drift(tune, f, .28, hand, drift);
            DrivePhysics.Result r = DrivePhysics.step(tune, f, l, .28, 5, true, 35, 0, hand, drift);
            f = r.forward();
            l = r.lateral();
            maxSlip = Math.max(maxSlip, r.slip());
        }
        assertTrue(maxSlip > .08 && l < 0, "Left drift must produce outward (right) lateral momentum");
        for (int i = 0; i < 120; i++) {
            drift = DrivePhysics.drift(tune, f, 0, GAS, drift);
            DrivePhysics.Result r = DrivePhysics.step(tune, f, l, 0, 5, true, 35, 0, GAS, drift);
            f = r.forward();
            l = r.lateral();
        }
        assertTrue(Math.abs(l) < .001, "Grip must recover after a drift");
        DrivePhysics.Result left = DrivePhysics.step(tune, .4, 0, .3, 2, true, 35, 0, new DrivePhysics.Input(1, 0, 1, false));
        assertTrue(left.yawDelta() < 0, "Left steering must turn toward driver's left in Minecraft coordinates");
    }

    @Test
    void flatTyresAndADamagedEngineCostSpeed() {
        DrivePhysics.Tune tune = TestPacks.vehicle("volga").tune;
        double healthy = 0;
        double flat = 0;
        double allFlat = 0;
        for (int i = 0; i < 250; i++) {
            healthy = DrivePhysics.step(tune, healthy, 0, 0, 1, true, 35, 0, GAS).forward();
            flat = DrivePhysics.step(tune, flat, 0, 0, 1, true, 35, 1, GAS).forward();
            allFlat = DrivePhysics.step(tune, allFlat, 0, 0, 1, true, 35, 4, GAS).forward();
        }
        assertTrue(flat < healthy * .65 && allFlat < flat * .4, "Flat tyres must strongly reduce acceleration and attainable speed");
        double rolling = DrivePhysics.step(tune, .5, 0, 0, 5, false, 35, 0, DrivePhysics.IDLE).forward();
        double dragging = DrivePhysics.step(tune, .5, 0, 0, 5, false, 35, 2, DrivePhysics.IDLE).forward();
        assertTrue(dragging < rolling - .01, "Flat tyres must increase rolling resistance without engine power");
        DrivePhysics.Drift drift = new DrivePhysics.Drift(0, 0);
        double strong = 0;
        double weak = 0;
        double strongEarly = 0;
        double weakEarly = 0;
        for (int i = 0; i < 300; i++) {
            strong = DrivePhysics.step(tune, strong, 0, 0, 1, true, 35, 0, GAS, drift, DamageModel.power(100)).forward();
            weak = DrivePhysics.step(tune, weak, 0, 0, 1, true, 35, 0, GAS, drift, DamageModel.power(20)).forward();
            if (i == 8) {
                strongEarly = strong;
                weakEarly = weak;
            }
        }
        assertTrue(weakEarly < strongEarly * .75, "A badly damaged engine pulls weakly: " + weakEarly + " vs " + strongEarly);
        assertTrue(weak < strong * .9 && weak > strong * .6, "...and tops out lower: " + weak + " vs " + strong);
    }

    @Test
    void weightSlowsTurningBrakingAndCoasting() {
        JSONVehicle volga = TestPacks.vehicle("volga");
        JsonObject json = TestPacks.builtIn("volga");
        json.addProperty("mass", 2800);
        JSONVehicle heavy = TestPacks.define("heavy", json);
        assertEquals(.0135, volga.tune.acceleration(), 1e-12, "74 kW in 1400 kg gives the tuned acceleration");
        assertEquals(volga.tune.acceleration() / 2, heavy.tune.acceleration(), 1e-12, "Twice the mass, half the acceleration");
        assertEquals(2, heavy.massRatio, "Twice the share of a collision");
        DrivePhysics.Input steer = new DrivePhysics.Input(0, 0, .5, false);
        double light = DrivePhysics.drift(volga.tune, .5, .2, steer, new DrivePhysics.Drift(0, 0)).yawRate();
        double slow = DrivePhysics.drift(heavy.tune, .5, .2, steer, new DrivePhysics.Drift(0, 0)).yawRate();
        assertTrue(Math.abs(slow) < Math.abs(light) * .9, "A heavier car answers the wheel later: " + slow + " vs " + light);
        double lightBrake = .5 - DrivePhysics.step(volga.tune, .5, 0, 0, 1, true, 10, 0, BRAKE).forward();
        double heavyBrake = .5 - DrivePhysics.step(heavy.tune, .5, 0, 0, 1, true, 10, 0, BRAKE).forward();
        assertTrue(heavyBrake < lightBrake * .9, "The same brakes stop a heavier car later: " + heavyBrake + " vs " + lightBrake);
        double lightCoast = .5 - DrivePhysics.step(volga.tune, .5, 0, 0, 0, true, 10, 0, DrivePhysics.IDLE).forward();
        double heavyCoast = .5 - DrivePhysics.step(heavy.tune, .5, 0, 0, 0, true, 10, 0, DrivePhysics.IDLE).forward();
        assertTrue(heavyCoast < lightCoast, "A heavier car rolls on further: " + heavyCoast + " vs " + lightCoast);
        double lightSlide = DrivePhysics.step(volga.tune, .5, .1, 0, 0, true, 10, 0, DrivePhysics.IDLE).lateral();
        double heavySlide = DrivePhysics.step(heavy.tune, .5, .1, 0, 0, true, 10, 0, DrivePhysics.IDLE).lateral();
        assertTrue(Math.abs(heavySlide) > Math.abs(lightSlide), "A heavier car keeps more of a sideways slide: " + heavySlide + " vs " + lightSlide);
        assertTrue(DrivePhysics.weight(1, -.3) == 1 && DrivePhysics.weight(1000, .5) == 1.4 && DrivePhysics.weight(.001, .5) == .7,
                "The Volga is the reference and weight stays within its bounds");
    }

    @Test
    void allWheelDriveLaunchesHarder() {
        JsonObject json = TestPacks.builtIn("g63");
        json.getAsJsonObject("physics").addProperty("drive", "rear");
        JSONVehicle rear = TestPacks.define("rear_g63", json);
        JSONVehicle all = TestPacks.vehicle("g63");
        assertTrue(all.tune.allWheel() && !rear.tune.allWheel(), "The G 63 drives all four wheels");
        assertTrue(DrivePhysics.traction(all.tune) > DrivePhysics.traction(rear.tune) * 1.5, "Four driven wheels put down more force");
        double first = DrivePhysics.step(all.tune, 0, 0, 0, 1, true, 100, 0, GAS).forward();
        double second = DrivePhysics.step(rear.tune, 0, 0, 0, 1, true, 100, 0, GAS).forward();
        assertTrue(first > second, "...so the car leaves the line harder: " + first + " vs " + second);
        assertTrue(DrivePhysics.step(rear.tune, .02, 0, 0, 1, true, 100, 0, GAS).slip() > DrivePhysics.step(all.tune, .02, 0, 0, 1, true, 100, 0, GAS).slip(),
                "Two driven wheels spin up more at a full-throttle launch");
    }

    @Test
    void automaticShiftsByItself() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        DrivePhysics.Tune tune = g63.tune;
        assertTrue(g63.transmission.automatic && g63.gears() == 9 && Math.abs(DrivePhysics.speedLimit(tune, 9) * KMH - 240) < 1e-6,
                "The G 63 has a nine-speed automatic up to 240 km/h");
        double speed = 240 / KMH;
        int gear = 9;
        int since = 100;
        for (int tick = 0; tick < 20 * 30 && speed > 0; tick++) {
            if (since++ >= 8) {
                int next = DrivePhysics.automaticGear(tune, gear, speed, 0, 1, 0, g63.transmission.shiftUp, g63.transmission.shiftDown);
                assertTrue(next <= gear, "Braking never shifts up");
                if (next != gear) {
                    gear = next;
                    since = 0;
                }
            }
            speed = DrivePhysics.step(tune, speed, 0, 0, gear, true, 100, 0, BRAKE).forward();
        }
        assertTrue(gear == 1 && speed <= 0, "Braking to a stop shifts back down to first: gear " + gear);
        double second = DrivePhysics.reachableSpeed(tune, 2, 1, 0);
        assertTrue(DrivePhysics.automaticGear(tune, 3, second * .75, 1, 1, 0, .92, .55) == 2
                && DrivePhysics.automaticGear(tune, 3, second * .75, .5, 1, 0, .92, .55) == 3, "Floored throttle kicks down a gear that half throttle keeps");
        assertEquals(2, DrivePhysics.automaticGear(tune, 1, DrivePhysics.reachableSpeed(tune, 1, .3, 0) * .95, 1, .3, 0, .92, .55),
                "A weak engine still shifts up at the speed it can reach");
        JsonObject wrong = TestPacks.builtIn("g63");
        wrong.getAsJsonObject("transmission").addProperty("shift_down", 0.95);
        assertThrows(IllegalArgumentException.class, () -> TestPacks.define("wrong", wrong), "Shift points that would hunt between gears are refused");
    }

    @Test
    void gearsFollowTheTopSpeed() {
        JSONVehicle volga = TestPacks.vehicle("volga");
        assertEquals(5, volga.gears(), "The Volga has five gears");
        assertEquals(23, DrivePhysics.speedLimit(volga.tune, 1) * KMH, 1e-6, "Listed first gear speed is kept");
        assertEquals(18, DrivePhysics.speedLimit(volga.tune, -1) * KMH, 1e-6, "Listed reverse speed is kept");
        JsonObject json = TestPacks.builtIn("volga");
        json.getAsJsonObject("physics").addProperty("max_speed_kmh", 150);
        JsonObject transmission = new JsonObject();
        transmission.addProperty("gears", 3);
        json.add("transmission", transmission);
        JSONVehicle three = TestPacks.define("three", json);
        assertEquals(3, three.gears(), "Three gears from the count");
        double previous = 0;
        double previousRatio = Double.MAX_VALUE;
        for (int gear = 1; gear <= 3; gear++) {
            double limit = DrivePhysics.speedLimit(three.tune, gear) * KMH;
            double ratio = three.tune.gearbox().ratio(gear);
            assertTrue(limit > previous && ratio < previousRatio, "Each gear is faster and longer than the one before: gear " + gear);
            previous = limit;
            previousRatio = ratio;
        }
        assertEquals(150, previous, 1e-6, "The top gear reaches the top speed");
        assertFalse(DrivePhysics.canShift(3, 4, 1, three.gears()), "There is no fourth gear");
        double speed = 0;
        for (int gear = 1; gear <= 3; gear++) {
            for (int tick = 0; tick < 1500; tick++) speed = DrivePhysics.step(three.tune, speed, 0, 0, gear, true, 35, 0, GAS).forward();
        }
        assertTrue(speed * KMH > 145, "A three-speed car still reaches its top speed: " + speed * KMH);
    }

    @Test
    void wrongDriveIsRefused() {
        JsonObject json = TestPacks.builtIn("volga");
        json.getAsJsonObject("physics").addProperty("drive", "sideways");
        assertThrows(IllegalArgumentException.class, () -> TestPacks.define("sideways", json));
    }
}
