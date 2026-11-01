package studio.merami.slimcore.baseclasses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import studio.merami.slimcore.packloading.PackParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModelSkeleton {
    public record Bone(String name, String parent, Point3D pivot, Point3D rotation, List<Cube> cubes) {}

    public record Cube(Point3D center, Point3D half, Point3D pivot, Point3D rotation) {}

    private static final Map<ResourceId, List<Bone>> MODELS = new HashMap<>();

    private ModelSkeleton() {}

    public static synchronized List<Bone> bones(ResourceId model) {
        return MODELS.computeIfAbsent(model, ModelSkeleton::load);
    }

    public static Map<String, Bone> byName(List<Bone> bones) {
        Map<String, Bone> map = new HashMap<>();
        for (Bone bone : bones) map.put(bone.name(), bone);
        return map;
    }

    private static Point3D point(JsonArray a) {
        return new Point3D(-a.get(0).getAsDouble() / 16, a.get(1).getAsDouble() / 16, a.get(2).getAsDouble() / 16);
    }

    private static Point3D rotation(JsonObject o) {
        if (!o.has("rotation")) return Point3D.ZERO;
        JsonArray a = o.getAsJsonArray("rotation");
        return new Point3D(-Math.toRadians(a.get(0).getAsDouble()), -Math.toRadians(a.get(1).getAsDouble()), Math.toRadians(a.get(2).getAsDouble()));
    }

    private static List<Bone> load(ResourceId model) {
        try (InputStream in = PackParser.openAsset(model); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonArray bones = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject().getAsJsonArray("bones");
            List<Bone> result = new ArrayList<>();
            for (JsonElement element : bones) {
                JsonObject b = element.getAsJsonObject();
                List<Cube> cubes = new ArrayList<>();
                if (b.has("cubes")) {
                    for (JsonElement cubeElement : b.getAsJsonArray("cubes")) {
                        JsonObject c = cubeElement.getAsJsonObject();
                        JsonArray size = c.getAsJsonArray("size");
                        Point3D actualHalf = new Point3D(size.get(0).getAsDouble() / 32, size.get(1).getAsDouble() / 32, size.get(2).getAsDouble() / 32);
                        Point3D half = new Point3D(Math.max(.004, actualHalf.x), Math.max(.004, actualHalf.y), Math.max(.004, actualHalf.z));
                        cubes.add(new Cube(point(c.getAsJsonArray("origin")).add(-actualHalf.x, actualHalf.y, actualHalf.z), half,
                                c.has("pivot") ? point(c.getAsJsonArray("pivot")) : Point3D.ZERO, rotation(c)));
                    }
                }
                result.add(new Bone(b.get("name").getAsString(), b.has("parent") ? b.get("parent").getAsString() : null,
                        point(b.getAsJsonArray("pivot")), rotation(b), cubes));
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
