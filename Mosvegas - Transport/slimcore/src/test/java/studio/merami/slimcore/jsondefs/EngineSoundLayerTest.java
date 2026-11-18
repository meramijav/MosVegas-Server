package studio.merami.slimcore.jsondefs;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSoundLayerTest {
    @Test
    void lixiangWhineFollowsSpeedWithoutAJumpBetweenLayers() {
        JSONVehicle l9 = TestPacks.vehicle("lixiang_l9");
        List<JSONSounds.EngineLayer> layers = l9.sounds.engine;
        JSONSounds.EngineLayer low = layers.get(0);
        JSONSounds.EngineLayer high = layers.get(1);
        assertTrue(low.followsSpeed() && high.followsSpeed());
        for (float kmh : new float[]{50, 55, 60}) {
            assertEquals(300 * low.pitch(kmh), 900 * high.pitch(kmh), 1e-3, "Both whine layers sound the same note at " + kmh + " km/h");
        }
        assertEquals(1, layers.get(3).pitch(20), 1e-6, "The range extender keeps its own revs");
        assertEquals(0, layers.get(3).blend(30), 1e-6, "The range extender is silent in town");
        assertEquals(0, layers.get(2).blend(40), 1e-6, "The pedestrian warning stops above 30 km/h");
        assertEquals(0, layers.get(4).blend(60), 1e-6, "The inverter is heard only while pulling away");
        assertEquals("slimvehicle:l9_ready", l9.sounds.start);
        assertEquals("slimvehicle:selector_click", l9.sounds.gearShift);
    }

    @Test
    void changanKeepsRevsAndHearsTheTurboUnderLoad() {
        JSONVehicle changan = TestPacks.vehicle("changan_univ");
        List<JSONSounds.EngineLayer> layers = changan.sounds.engine;
        assertTrue(layers.stream().noneMatch(JSONSounds.EngineLayer::followsSpeed));
        assertTrue(layers.stream().limit(5).allMatch(layer -> layer.inside != null), "Every engine layer has its cabin recording");
        for (int i = 0; i < 4; i++) {
            JSONSounds.EngineLayer lower = layers.get(i);
            JSONSounds.EngineLayer upper = layers.get(i + 1);
            float rpm = (float) ((upper.fadeIn[0] + upper.fadeIn[1]) / 2);
            assertEquals(lower.pitchRpm * lower.pitch(rpm), upper.pitchRpm * upper.pitch(rpm), 1e-2, "Recordings " + i + " and " + (i + 1) + " play the same revs at " + rpm);
        }
        assertEquals(1, layers.get(0).pitch(713), 1e-6);
        assertEquals("slimvehicle:changan_start", changan.sounds.start);
        assertEquals("slimvehicle:changan_start_inside", changan.sounds.startInside);
        JSONSounds.EngineLayer turbo = layers.get(5);
        assertEquals(1, turbo.loadShare(1), 1e-6);
        assertEquals(.15, turbo.loadShare(0), 1e-6);
        assertFalse(TestPacks.vehicle("volga").sounds.engine.stream().anyMatch(layer -> layer.load > 0), "Packs without the field keep full volume");
    }

    @Test
    void wrongLayerFieldsAreRefused() {
        for (String[] bad : new String[][]{{"follow", "\"wheel\""}, {"load", "1.5"}, {"volume", "-1"}, {"pitch_rpm", "0"}}) {
            JsonObject json = TestPacks.builtIn("lixiang_l9");
            JsonObject layer = json.getAsJsonObject("sounds").getAsJsonArray("engine").get(0).getAsJsonObject();
            layer.add(bad[0], com.google.gson.JsonParser.parseString(bad[1]));
            assertThrows(IllegalArgumentException.class, () -> TestPacks.define("bad_" + bad[0], json), bad[0]);
        }
    }
}
