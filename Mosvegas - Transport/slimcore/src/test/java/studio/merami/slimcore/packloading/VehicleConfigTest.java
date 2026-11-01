package studio.merami.slimcore.packloading;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleConfigTest {
    private static JsonObject defaults(String name) {
        return VehicleConfig.defaults(JSONParser.parse(TestPacks.builtIn(name), JSONVehicle.class, new ArrayList<>()));
    }

    @Test
    void eachCarGetsAFileOfItsOwnValuesThatSurvivesUpdates() throws IOException {
        Path folder = Files.createTempDirectory("slimvehicle-config");
        try {
            JsonObject g63 = defaults("g63");
            assertEquals(2560, g63.get("mass").getAsDouble(), 1e-9, "The file starts from the car's own mass");
            assertEquals("all", g63.getAsJsonObject("physics").get("drive").getAsString(), "and its drive");
            assertFalse(g63.getAsJsonObject("physics").has("acceleration"), "A value the pack leaves to the mod is not written");
            assertEquals(g63, VehicleConfig.local(folder, "slimvehicle:g63", g63), "A new file holds the pack's values");
            Path file = VehicleConfig.file(folder, "slimvehicle:g63");
            assertTrue(Files.isRegularFile(file), "The file is written as config/.../slimvehicle/g63.json");

            String edited = Files.readString(file, StandardCharsets.UTF_8).replace("\"mass\": 2560", "\"mass\": 3000");
            Files.writeString(file, edited, StandardCharsets.UTF_8);
            JsonObject update = g63.deepCopy();
            update.getAsJsonObject("physics").addProperty("grip", .4);
            JsonObject effective = VehicleConfig.local(folder, "slimvehicle:g63", update);
            assertEquals(3000, effective.get("mass").getAsDouble(), 1e-9, "The admin's mass stays");
            assertEquals(.4, effective.getAsJsonObject("physics").get("grip").getAsDouble(), 1e-9, "An untouched value follows the mod's new default");
            assertEquals(effective, JSONParser.read(file), "The file shows what is in force");

            Path volga = VehicleConfig.file(folder, "slimvehicle:volga");
            Files.createDirectories(volga.getParent());
            Files.writeString(volga, "{\"physics\": {\"drive\": \"rocket\"}}", StandardCharsets.UTF_8);
            List<PackParser.Source> builtIn = List.of(new PackParser.Source("slimvehicle", List.of(TestPacks.BUILT_IN), true));
            PackParser.load(builtIn, folder, null);
            assertEquals(3000, PackParser.vehicle("g63").mass, 1e-9, "The config is applied when packs load");
            assertEquals("rear", PackParser.vehicle("volga").physics.drive, "A value that does not fit falls back to the pack instead of stopping the server");
            assertEquals(3000, PackParser.configs().get("slimvehicle:g63").get("mass").getAsDouble(), 1e-9, "The server sends what is in force");
            JsonObject remote = PackParser.configs().get("slimvehicle:g63");
            remote.addProperty("mass", 1800);
            PackParser.useRemoteConfigs(Map.of("slimvehicle:g63", remote));
            assertEquals(1800, PackParser.vehicle("g63").mass, 1e-9, "A client takes the server's values");
            PackParser.useRemoteConfigs(null);
            assertEquals(3000, PackParser.vehicle("g63").mass, 1e-9, "and its own again after leaving");
        } finally {
            PackParser.load(List.of(new PackParser.Source("slimvehicle", List.of(TestPacks.BUILT_IN), true)), null);
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path path : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
