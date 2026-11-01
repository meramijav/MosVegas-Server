package studio.merami.slimcore.physics;

import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspensionTest {
    @Test
    void accelerationLiftsTheNoseWithoutRoll() {
        Suspension suspension = new Suspension();
        for (int i = 0; i < 20; i++) suspension.step(.01, 0, true);
        assertTrue(suspension.pitch < 0 && Math.abs(suspension.roll) < 1e-9, "Acceleration must lift the nose without side roll");
        Suspension braking = new Suspension();
        for (int i = 0; i < 20; i++) braking.step(-.01, 0, true);
        assertTrue(braking.pitch > 0, "Braking dips the nose");
        Suspension flying = new Suspension();
        for (int i = 0; i < 20; i++) flying.step(.01, .02, false);
        assertTrue(Math.abs(flying.pitch) < 1e-9 && Math.abs(flying.roll) < 1e-9, "A body in the air does not lean");
    }

    @Test
    void aTurnLeansTheBodyALittle() {
        Suspension turn = new Suspension();
        for (int i = 0; i < 50; i++) turn.step(0, .016, true);
        assertTrue(turn.roll > .02 && turn.roll < .04, "A left turn must produce a small, controlled lean: " + turn.roll);
    }

    @Test
    void aHeavyTallBodyLeansFurther() {
        JSONVehicle volga = TestPacks.vehicle("volga");
        JSONVehicle g63 = TestPacks.vehicle("g63");
        Suspension sedan = new Suspension();
        sedan.carry(volga.massRatio, volga.size.height);
        Suspension tall = new Suspension();
        tall.carry(g63.massRatio, g63.size.height);
        for (int i = 0; i < 40; i++) {
            sedan.step(0, .02, true);
            tall.step(0, .02, true);
        }
        assertTrue(sedan.sway == 1 && Math.abs(tall.roll) > Math.abs(sedan.roll) * 1.2, "A heavy, tall body leans further in a turn: " + tall.roll + " vs " + sedan.roll);
    }

    @Test
    void launchSquatStaysSmall() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        Suspension body = new Suspension();
        body.carry(g63.massRatio, g63.size.height);
        double speed = 0;
        double deepest = 0;
        for (int tick = 0; tick < 60; tick++) {
            double next = DrivePhysics.step(g63.tune, speed, 0, 0, 1, true, 100, 0, new DrivePhysics.Input(1, 0, 0, false)).forward();
            body.step(next - speed, 0, true);
            deepest = Math.min(deepest, body.pitch);
            speed = next;
        }
        assertTrue(deepest < 0 && Math.toDegrees(-deepest) < 2.5, "A full-throttle launch squats the G 63 by under 2.5 degrees: " + Math.toDegrees(-deepest));
    }
}
