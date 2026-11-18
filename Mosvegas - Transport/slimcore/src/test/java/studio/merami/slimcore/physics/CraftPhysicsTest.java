package studio.merami.slimcore.physics;

import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.jsondefs.JSONCraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftPhysicsTest {
    private static final CraftPhysics.Input UP = new CraftPhysics.Input(0, 0, 0, 1);
    private static final CraftPhysics.Input FORWARD = new CraftPhysics.Input(1, 0, 0, 0);
    private static final CraftPhysics.Input LEFT = new CraftPhysics.Input(1, 0, 1, 0);

    private static JSONCraft helicopter() {
        return TestPacks.vehicle("r44").craft;
    }

    private static JSONCraft jetski() {
        return TestPacks.vehicle("jetski").craft;
    }

    @Test
    void definitionsAreCrafts() {
        assertTrue(helicopter().helicopter());
        assertTrue(jetski().watercraft());
    }

    @Test
    void helicopterNeedsSpooledRotorToClimb() {
        JSONCraft craft = helicopter();
        CraftPhysics.State state = new CraftPhysics.State(0, 0, 0, 0);
        CraftPhysics.Result result = CraftPhysics.flight(craft, state, UP, true, true, 1);
        assertTrue(result.vertical() <= 0);
        int ticks = 0;
        while (state.rotor() < CraftPhysics.HOVER_ROTOR && ticks < 400) {
            result = CraftPhysics.flight(craft, state, UP, true, true, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
            ticks++;
        }
        assertTrue(ticks > craft.spool * 20 * .8 && ticks < craft.spool * 20 * 1.1, "spool ticks " + ticks);
        for (int i = 0; i < 40; i++) {
            result = CraftPhysics.flight(craft, state, UP, true, false, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
        }
        assertEquals(craft.climb, state.vertical(), 1e-6);
    }

    @Test
    void helicopterHoversWithoutLiftAndFallsWithoutEngine() {
        JSONCraft craft = helicopter();
        CraftPhysics.State state = new CraftPhysics.State(0, 0, .2, 1);
        for (int i = 0; i < 40; i++) {
            CraftPhysics.Result result = CraftPhysics.flight(craft, state, CraftPhysics.Input.IDLE, true, false, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
        }
        assertEquals(0, state.vertical(), 1e-6);
        for (int i = 0; i < 400; i++) {
            CraftPhysics.Result result = CraftPhysics.flight(craft, state, CraftPhysics.Input.IDLE, false, false, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
        }
        assertTrue(state.rotor() < .2);
        assertTrue(state.vertical() < -.8, "falls at " + state.vertical());
        assertTrue(state.vertical() >= CraftPhysics.TERMINAL);
    }

    @Test
    void helicopterReachesTopSpeedNoseDownAndBanksIntoTurns() {
        JSONCraft craft = helicopter();
        CraftPhysics.State state = new CraftPhysics.State(0, 0, 0, 1);
        CraftPhysics.Result result = null;
        for (int i = 0; i < 600; i++) {
            result = CraftPhysics.flight(craft, state, FORWARD, true, false, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
        }
        assertEquals(craft.maxSpeed(), state.forward(), 1e-6);
        assertTrue(result.pitch() > 0);
        result = CraftPhysics.flight(craft, state, LEFT, true, false, 1);
        assertTrue(result.yawDelta() < 0);
        assertTrue(result.roll() < 0);
    }

    @Test
    void helicopterDoesNotTaxiOnTheGround() {
        JSONCraft craft = helicopter();
        CraftPhysics.Result result = CraftPhysics.flight(craft, new CraftPhysics.State(0, 0, 0, 1), FORWARD, true, true, 1);
        assertEquals(0, result.forward(), 1e-9);
    }

    @Test
    void jetskiFloatsAtItsDraftAndPlanes() {
        JSONCraft craft = jetski();
        double y = -2;
        CraftPhysics.State state = new CraftPhysics.State(0, 0, 0, 0);
        for (int i = 0; i < 200; i++) {
            CraftPhysics.Result result = CraftPhysics.water(craft, state, CraftPhysics.Input.IDLE, true, -craft.draft - y, false, false, 1);
            y += result.vertical();
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), 0);
        }
        assertEquals(-craft.draft, y, .02);
        CraftPhysics.Result result = null;
        for (int i = 0; i < 400; i++) {
            result = CraftPhysics.water(craft, state, FORWARD, true, 0, false, false, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), 0);
        }
        assertEquals(craft.maxSpeed(), state.forward(), .02);
        assertTrue(result.pitch() < 0);
    }

    @Test
    void jetskiStallsOnLandAndWithoutEngine() {
        JSONCraft craft = jetski();
        CraftPhysics.Result beached = CraftPhysics.water(craft, new CraftPhysics.State(1, 0, 0, 0), FORWARD, true, Double.NaN, true, false, 1);
        assertTrue(beached.forward() < 1);
        assertTrue(beached.vertical() < 0);
        assertEquals(0, beached.yawDelta(), 1e-9);
        CraftPhysics.Result off = CraftPhysics.water(craft, new CraftPhysics.State(0, 0, 0, 0), FORWARD, false, 0, false, false, 1);
        assertEquals(0, off.forward(), 1e-9);
    }

    @Test
    void helicopterBrakesHardAndFlaresNoseUp() {
        JSONCraft craft = helicopter();
        CraftPhysics.State state = new CraftPhysics.State(craft.maxSpeed(), 0, 0, 1);
        CraftPhysics.Input brake = new CraftPhysics.Input(0, 1, 0, 0);
        CraftPhysics.Result result = CraftPhysics.flight(craft, state, brake, true, false, 1);
        assertTrue(result.pitch() < -craft.tilt * .3, "nose up while braking, pitch " + result.pitch());
        double travelled = 0;
        for (int i = 0; i < 200 && state.forward() > 0; i++) {
            result = CraftPhysics.flight(craft, state, brake, true, false, 1);
            travelled += result.forward();
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
        }
        assertTrue(travelled < 30, "stops within 30 blocks, took " + travelled);
        state = new CraftPhysics.State(craft.maxSpeed(), 0, 0, 1);
        travelled = 0;
        for (int i = 0; i < 400 && state.forward() > 0; i++) {
            result = CraftPhysics.flight(craft, state, CraftPhysics.Input.IDLE, true, false, 1);
            travelled += result.forward();
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor());
        }
        assertTrue(travelled < 60, "coasts to a stop within 60 blocks, took " + travelled);
    }

    @Test
    void turningKeepsMomentumAsADrift() {
        JSONCraft craft = helicopter();
        CraftPhysics.Result result = CraftPhysics.flight(craft, new CraftPhysics.State(craft.maxSpeed(), 0, 0, 1), LEFT, true, false, 1);
        assertTrue(result.lateral() < 0, "the old heading carries the craft sideways, lateral " + result.lateral());
    }

    @Test
    void jetskiHopsAndFliesOverTheWater() {
        JSONCraft craft = jetski();
        CraftPhysics.Result hop = CraftPhysics.water(craft, new CraftPhysics.State(1, 0, 0, 0), FORWARD, true, 0, false, true, 1);
        assertEquals(CraftPhysics.HOP + CraftPhysics.HOP_SPEED * Math.min(1, 1 / craft.maxSpeed()), hop.vertical(), 1e-9);
        CraftPhysics.Result standing = CraftPhysics.water(craft, new CraftPhysics.State(0, 0, 0, 0), CraftPhysics.Input.IDLE, true, 0, false, true, 1);
        assertTrue(standing.vertical() < hop.vertical(), "a hop at speed goes higher than one from a standstill");
        CraftPhysics.Result air = CraftPhysics.water(craft, new CraftPhysics.State(1, 0, .4, 0), FORWARD, true, Double.NaN, false, false, 1);
        assertTrue(air.forward() > .99, "keeps its speed in the air");
        assertTrue(air.vertical() < .4 && air.pitch() < 0, "falls under gravity with the nose up while rising");
        CraftPhysics.Result splash = CraftPhysics.water(craft, new CraftPhysics.State(1, 0, -.6, 0), CraftPhysics.Input.IDLE, true, .1, false, false, 1);
        assertTrue(splash.forward() < .9, "landing in water costs speed");
    }

    @Test
    void groundCushionSoftensTheLastMetres() {
        JSONCraft craft = helicopter();
        CraftPhysics.Input down = new CraftPhysics.Input(0, 0, 0, -1);
        CraftPhysics.State state = new CraftPhysics.State(0, 0, -craft.climb, 1);
        CraftPhysics.Result high = CraftPhysics.flight(craft, state, down, true, false, 1, 20);
        CraftPhysics.Result low = CraftPhysics.flight(craft, state, down, true, false, 1, .3);
        assertTrue(low.vertical() > high.vertical(), "sinks slower right above the ground");
        double vertical = -craft.climb;
        for (int i = 0; i < 30; i++) {
            vertical = CraftPhysics.flight(craft, new CraftPhysics.State(0, 0, vertical, 1), down, true, false, 1, .2).vertical();
        }
        assertTrue(vertical > -craft.climb * .35, "touches down gently, sink " + vertical);
    }

    @Test
    void pedalsTakeAMomentToBuildTheTurn() {
        JSONCraft craft = helicopter();
        CraftPhysics.Result first = CraftPhysics.flight(craft, new CraftPhysics.State(0, 0, 0, 1), LEFT, true, false, 1);
        assertTrue(-first.yawDelta() < craft.yawRate * .5, "first tick turn " + first.yawDelta());
        CraftPhysics.State state = new CraftPhysics.State(0, 0, 0, 1);
        CraftPhysics.Result result = first;
        for (int i = 0; i < 20; i++) {
            result = CraftPhysics.flight(craft, state, LEFT, true, false, 1);
            state = new CraftPhysics.State(result.forward(), result.lateral(), result.vertical(), result.rotor(), -result.yawDelta());
        }
        assertEquals(craft.yawRate, -result.yawDelta(), 1e-6);
    }

    @Test
    void beaconFlashesWithTheEngineAndNavLightsFollowTheLightSwitch() {
        JSONCraft.Light beacon = new JSONCraft.Light();
        beacon.when = JSONCraft.LIGHT_BEACON;
        JSONCraft.Light nav = new JSONCraft.Light();
        assertTrue(JSONCraft.lit(beacon, false, true, 0));
        assertTrue(!JSONCraft.lit(beacon, false, true, JSONCraft.BEACON_ON));
        assertTrue(!JSONCraft.lit(beacon, true, false, 0));
        assertTrue(JSONCraft.lit(nav, true, false, 7));
        assertTrue(!JSONCraft.lit(nav, false, true, 7));
    }
}
