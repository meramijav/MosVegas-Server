package studio.merami.slimcore;

import com.google.gson.JsonObject;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.JSONParser;
import studio.merami.slimcore.packloading.PackParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestPacks {
    public static final Path BUILT_IN = Path.of("slimcore", "src", "main", "resources");
    private static boolean loaded;

    private TestPacks() {}

    public static synchronized void load() {
        if (loaded) return;
        PackParser.load(List.of(new PackParser.Source("slimvehicle", List.of(BUILT_IN), true)), null);
        loaded = true;
    }

    public static JSONVehicle vehicle(String name) {
        load();
        return PackParser.vehicle(name);
    }

    public static JsonObject builtIn(String name) {
        try {
            return JSONParser.read(BUILT_IN.resolve("assets").resolve(SlimCore.MODID).resolve("vehicles").resolve(name + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JSONVehicle define(String name, JsonObject json) {
        load();
        JSONParser.applyWheelTemplate(json);
        JSONVehicle vehicle = JSONParser.parse(json, JSONVehicle.class, new ArrayList<>());
        vehicle.id = "test:" + name;
        vehicle.namespace = "test";
        vehicle.name = name;
        vehicle.bake();
        return vehicle;
    }
}
