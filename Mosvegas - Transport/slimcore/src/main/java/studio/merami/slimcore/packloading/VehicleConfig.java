package studio.merami.slimcore.packloading;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VehicleConfig {
    public static final List<String> FIELDS = List.of(
            "mass",
            "physics.max_speed_kmh", "physics.acceleration", "physics.brake", "physics.grip", "physics.steer_lock", "physics.steer_falloff", "physics.drive",
            "transmission.gears", "transmission.automatic", "transmission.reverse_kmh", "transmission.shift_up", "transmission.shift_down",
            "engine.power_kw", "engine.idle_rpm", "engine.limit_rpm", "engine.max_rpm", "engine.redline_rpm", "engine.keep_running",
            "fuel.tank", "fuel.per_tick",
            "render.speedometer");
    static final String DEFAULTS = ".defaults";
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson READ = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).serializeSpecialFloatingPointValues().create();

    private VehicleConfig() {}

    public static JsonObject defaults(JSONVehicle parsed) {
        JsonObject all = new JsonObject();
        all.addProperty("mass", parsed.mass);
        all.add("physics", READ.toJsonTree(parsed.physics));
        all.add("transmission", READ.toJsonTree(parsed.transmission));
        all.add("engine", READ.toJsonTree(parsed.engine));
        all.add("fuel", READ.toJsonTree(parsed.fuel));
        all.add("render", READ.toJsonTree(parsed.render));
        Map<String, JsonElement> picked = new LinkedHashMap<>();
        for (String field : FIELDS) {
            JsonElement value = at(all, field);
            if (value == null || !value.isJsonPrimitive()) continue;
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                double number = primitive.getAsDouble();
                if (!Double.isFinite(number)) continue;
                value = number == Math.rint(number) && Math.abs(number) < 1e9 ? new JsonPrimitive((long) number) : new JsonPrimitive(number);
            }
            picked.put(field, value);
        }
        return nest(picked);
    }

    public static JsonObject local(Path folder, String id, JsonObject defaults) {
        Path file = file(folder, id);
        Path saved = file(folder.resolve(DEFAULTS), id);
        try {
            if (!Files.isRegularFile(file)) {
                write(file, defaults);
                write(saved, defaults);
                return defaults;
            }
            JsonObject user = JSONParser.read(file);
            JsonObject before = Files.isRegularFile(saved) ? JSONParser.read(saved) : new JsonObject();
            JsonObject effective = rebase(user, before, defaults);
            if (!effective.equals(user)) write(file, effective);
            if (!defaults.equals(before)) write(saved, defaults);
            return effective;
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Unreadable vehicle config {}: {}; the pack's values are used", file, e.getMessage());
            return defaults;
        }
    }

    public static JsonObject rebase(JsonObject user, JsonObject before, JsonObject defaults) {
        Map<String, JsonElement> edited = flatten(user);
        Map<String, JsonElement> old = flatten(before);
        Map<String, JsonElement> result = flatten(defaults);
        for (Map.Entry<String, JsonElement> entry : edited.entrySet()) {
            if (!Objects.equals(old.get(entry.getKey()), entry.getValue())) result.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return nest(result);
    }

    static Path file(Path folder, String id) {
        int colon = id.indexOf(':');
        return folder.resolve(id.substring(0, colon)).resolve(id.substring(colon + 1) + ".json");
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, PRETTY.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static JsonElement at(JsonObject root, String path) {
        JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static Map<String, JsonElement> flatten(JsonObject json) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        flatten("", json, out);
        return out;
    }

    private static void flatten(String prefix, JsonObject json, Map<String, JsonElement> out) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String path = prefix + entry.getKey();
            if (entry.getValue().isJsonObject()) flatten(path + ".", entry.getValue().getAsJsonObject(), out);
            else out.put(path, entry.getValue());
        }
    }

    private static JsonObject nest(Map<String, JsonElement> flat) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : flat.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            JsonObject parent = root;
            for (int i = 0; i < parts.length - 1; i++) {
                JsonElement child = parent.get(parts[i]);
                if (child == null || !child.isJsonObject()) {
                    child = new JsonObject();
                    parent.add(parts[i], child);
                }
                parent = child.getAsJsonObject();
            }
            parent.add(parts[parts.length - 1], entry.getValue());
        }
        return root;
    }
}
