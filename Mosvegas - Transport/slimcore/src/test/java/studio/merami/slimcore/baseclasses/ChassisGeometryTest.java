package studio.merami.slimcore.baseclasses;

import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChassisGeometryTest {
    private static final Point3D FORWARD = new Point3D(0, 0, 1);

    @Test
    void headlightsStayLevelWhenTheBodySquats() {
        double squat = -.04;
        Point3D beam = ChassisGeometry.levelled(FORWARD, squat, squat, 0, 0);
        assertEquals(0, beam.y, 1e-6, "A launch that lifts the nose leaves the beam on the road: " + beam);
        Point3D unlevelled = ChassisGeometry.levelled(FORWARD, squat, 0, 0, 0);
        assertTrue(unlevelled.y > .03, "Without levelling the same squat aims the beam into the sky: " + unlevelled);
        Point3D uphill = ChassisGeometry.levelled(FORWARD, -.1 + squat, squat, 0, 0);
        assertEquals(Math.sin(.1), uphill.y, 1e-3, "On a slope the beam follows the road: " + uphill);
        Point3D turned = ChassisGeometry.levelled(FORWARD, squat, squat, 0, 90);
        assertEquals(-1, turned.x, 1e-6, "The beam turns with the car's heading: " + turned);
    }

    @Test
    void theCabinTestUsesTheCameraAndCarOfOneFrame() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        Point3D eye = new Point3D(.6, 1.96, .09);
        assertTrue(g63.inCabin(eye), "The driver's eye is in the cabin box");
        double perTick = 120 / 72.0;
        Point3D frame = new Point3D(10, 64, 10);
        Point3D tick = frame.add(0, 0, perTick * 1.5);
        Point3D camera = frame.add(eye);
        assertFalse(g63.inCabin(ChassisGeometry.toLocal(camera, tick, 0, 1)),
                "Compared with where the car is a tick and a half later, the camera of a car doing 120 km/h falls out of the cabin box");
        assertTrue(g63.inCabin(ChassisGeometry.toLocal(camera, frame, 0, 1)), "Compared with the car in the same frame it stays inside");
        Point3D turned = ChassisGeometry.toLocal(frame.add(new Point3D(-eye.x, eye.y, -eye.z)), frame, 180, 1);
        assertEquals(eye.x, turned.x, 1e-6, "A car facing the other way mirrors the offset back into chassis space");
        assertEquals(eye.z, turned.z, 1e-6);
    }

    @Test
    void everyPunctureLowersItsOwnCorner() {
        JSONVehicle volga = TestPacks.vehicle("volga");
        for (int wheel = 0; wheel < 4; wheel++) {
            double[] support = {0, 0, 0, 0};
            support[wheel] = -ChassisGeometry.PUNCTURE_DROP;
            ChassisGeometry.Pose pose = ChassisGeometry.wheelPlane(volga, support);
            assertTrue((wheel < 2 ? pose.pitch() > 0 : pose.pitch() < 0) && (wheel % 2 == 0 ? pose.roll() < 0 : pose.roll() > 0), "Every puncture must lower its own corner: " + wheel);
        }
    }

    @Test
    void handsNeverCrossAtFullSteeringLock() {
        JSONVehicle volga = TestPacks.vehicle("volga");
        for (int i = -100; i <= 100; i++) {
            float steer = i * .0052f;
            Point3D left = volga.driver.steering.grip(true, steer);
            Point3D right = volga.driver.steering.grip(false, steer);
            assertTrue(left.x - right.x > .15, "Hands must stay separated at every steering angle: " + steer);
            if (i < 100) assertTrue(left.distanceTo(volga.driver.steering.grip(true, steer + .0052f)) < .02, "Regripping must be continuous");
            if (i == 0) assertTrue(left.x > right.x, "At neutral steering the left hand remains on the driver's left");
        }
    }
}
