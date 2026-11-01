package studio.merami.slimcore.jsondefs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.rendering.ModelGeometry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleDefinitionTest {
    @Test
    void builtInPackLoads() {
        TestPacks.load();
        JSONVehicle volga = TestPacks.vehicle("volga");
        assertTrue(PackParser.vehicles().size() >= 4, "All built-in definitions load");
        assertEquals("slimvehicle:volga", volga.id);
        assertTrue(PackParser.vehicle("no-such-car") == volga, "An unknown id falls back to the first vehicle");
    }

    @Test
    void g63IsAnAllWheelDrive240Automatic() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        assertEquals(240, g63.physics.maxSpeedKmh);
        assertEquals(JSONVehicle.DRIVE_ALL, g63.physics.drive);
        assertTrue(g63.tune.allWheel() && g63.tune.automatic(), "The tune knows the drive and the gearbox");
        assertEquals(JSONVehicle.DRIVE_REAR, TestPacks.vehicle("volga").physics.drive, "A car without \"drive\" drives its rear wheels");
    }

    @Test
    void g63DriverReachesForTheSelector() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        Point3D lever = g63.driver.lever();
        assertNotNull(lever, "The G 63 has a selector for the driver's hand");
        Point3D seat = g63.seats.get(0).pos;
        Point3D grip = g63.driver.steering.grip(false, 0);
        assertTrue(lever.x < grip.x && lever.y < grip.y && Math.abs(lever.z - seat.z) < .6,
                "The selector sits on the console, inboard of and below the right hand on the wheel: " + lever);
    }

    @Test
    void g63ThirdBrakeLightAndBumperStripsGlow() {
        JSONVehicle g63 = TestPacks.vehicle("g63");
        List<JSONVehicle.Glow> glow = g63.lamps.glow;
        assertEquals(2, glow.size());
        assertTrue(glow.stream().anyMatch(g -> g.bone.equals("brakelight") && g.brakeOnly()), "The third brake light glows only when braking");
        assertTrue(glow.stream().anyMatch(g -> g.bone.equals("tail_strips") && !g.brakeOnly()), "The bumper strips glow with the tail lights");
        ModelGeometry model = ModelGeometry.load(g63.modelLocation);
        assertEquals("trunk", model.bone("brakelight").parent.name, "The brake light swings with the tailgate");
        assertEquals(1, model.bone("brakelight").cubes.size());
        assertEquals(2, model.bone("tail_strips").cubes.size());
        assertTrue(g63.paintBones.contains("tail_strips") && g63.dentBones.contains("tail_strips"), "The bumper strips keep the paint and the dents of the bumper");
    }

    @Test
    void wrongGlowIsRefused() {
        JsonObject json = TestPacks.builtIn("g63");
        JsonObject glow = new JsonObject();
        glow.addProperty("bone", "brakelight");
        glow.addProperty("when", "always");
        JsonArray list = new JsonArray();
        list.add(glow);
        json.getAsJsonObject("lamps").add("glow", list);
        assertThrows(IllegalArgumentException.class, () -> TestPacks.define("bad_glow", json));
    }

    @Test
    void g63HasWhiteLedHeadlightsAndTheVolgaHalogen() {
        assertEquals(JSONVehicle.HEADLIGHT_LED, TestPacks.vehicle("g63").lamps.headlightType);
        assertEquals(JSONVehicle.HEADLIGHT_HALOGEN, TestPacks.vehicle("volga").lamps.headlightType);
        assertEquals(JSONVehicle.HEADLIGHT_HALOGEN, TestPacks.vehicle("police").lamps.headlightType, "Cars made from the Volga keep its halogen lamps");
        JsonObject json = TestPacks.builtIn("g63");
        json.getAsJsonObject("lamps").remove("headlight_type");
        assertEquals(JSONVehicle.HEADLIGHT_HALOGEN, TestPacks.define("plain_lamps", json).lamps.headlightType, "Without headlight_type the lamps are halogen");
        json.getAsJsonObject("lamps").addProperty("headlight_type", "xenon");
        assertThrows(IllegalArgumentException.class, () -> TestPacks.define("bad_lamps", json));
    }

    @Test
    void enginesKeepRunningTwoMinutesAfterTheDriverLeaves() {
        TestPacks.load();
        for (JSONVehicle vehicle : PackParser.vehicles()) assertEquals(120, vehicle.engine.keepRunning, vehicle.id + ": engine.keep_running");
        JsonObject json = TestPacks.builtIn("g63");
        json.getAsJsonObject("engine").addProperty("keep_running", 30);
        assertEquals(30, TestPacks.define("short_idle", json).engine.keepRunning);
    }

    @Test
    void everyDriverSitsInTheCabinBox() {
        for (JSONVehicle vehicle : PackParser.vehicles()) {
            if (vehicle.cabinMuffle <= 0) continue;
            Point3D seat = vehicle.seats.get(0).pos;
            Point3D eye = seat.add(0, ChassisGeometry.seatedEyes(vehicle) * .5, 0);
            assertTrue(vehicle.inCabin(eye), vehicle.id + ": the driver's head is inside the cabin box");
            assertTrue(!vehicle.inCabin(vehicle.engine.pos == null ? new Point3D(0, 0, 50) : vehicle.engine.pos), vehicle.id + ": the engine bay is outside it");
        }
    }
}
