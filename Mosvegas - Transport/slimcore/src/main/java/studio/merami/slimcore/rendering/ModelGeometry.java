package studio.merami.slimcore.rendering;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.packloading.PackParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModelGeometry {
    public enum Face {
        WEST(-1, 0, 0),
        EAST(1, 0, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        UP(0, 1, 0),
        DOWN(0, -1, 0);

        final float nx;
        final float ny;
        final float nz;

        Face(float nx, float ny, float nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    public record Vertex(float x, float y, float z, float u, float v) {}

    public record Quad(Vertex[] vertices, float nx, float ny, float nz, Face face) {}

    public record Cube(Quad[] quads, double pivotX, double pivotY, double pivotZ, double rotX, double rotY, double rotZ,
                       double sizeX, double sizeY, double sizeZ, double inflate, boolean mirror) {}

    public static final class Bone {
        public final int index;
        public final String name;
        public final Bone parent;
        public final List<Bone> children = new ArrayList<>();
        public final List<Cube> cubes = new ArrayList<>();
        public final float pivotX;
        public final float pivotY;
        public final float pivotZ;
        public final float rotX;
        public final float rotY;
        public final float rotZ;
        public final Double inflate;
        public final boolean neverRender;

        Bone(int index, String name, Bone parent, float pivotX, float pivotY, float pivotZ, float rotX, float rotY, float rotZ, Double inflate, boolean neverRender) {
            this.index = index;
            this.name = name;
            this.parent = parent;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.inflate = inflate;
            this.neverRender = neverRender;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Map<ResourceId, ModelGeometry> MODELS = new HashMap<>();

    public final List<Bone> roots;
    public final List<Bone> bones;
    public final Map<String, Bone> byName;
    public final double textureWidth;
    public final double textureHeight;

    private ModelGeometry(List<Bone> roots, List<Bone> bones, Map<String, Bone> byName, double textureWidth, double textureHeight) {
        this.roots = Collections.unmodifiableList(roots);
        this.bones = Collections.unmodifiableList(bones);
        this.byName = Collections.unmodifiableMap(byName);
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static synchronized ModelGeometry load(ResourceId model) {
        ModelGeometry cached = MODELS.get(model);
        if (cached != null) return cached;
        try (InputStream in = PackParser.openAsset(model); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            ModelGeometry geometry = parse(JsonParser.parseReader(reader).getAsJsonObject());
            MODELS.put(model, geometry);
            return geometry;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("model " + model + ": " + e.getMessage(), e);
        }
    }

    public static synchronized void clearCache() {
        MODELS.clear();
    }

    public Bone bone(String name) {
        return byName.get(name);
    }

    public static ModelGeometry parse(JsonObject root) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        double textureWidth = description.get("texture_width").getAsDouble();
        double textureHeight = description.get("texture_height").getAsDouble();
        JsonArray rawBones = geometry.has("bones") ? geometry.getAsJsonArray("bones") : new JsonArray();
        Map<String, JsonObject> pending = new LinkedHashMap<>();
        for (JsonElement element : rawBones) {
            JsonObject bone = element.getAsJsonObject();
            pending.put(bone.get("name").getAsString(), bone);
        }
        List<Bone> roots = new ArrayList<>();
        List<Bone> bones = new ArrayList<>();
        Map<String, Bone> byName = new HashMap<>();
        while (!pending.isEmpty()) {
            boolean placed = false;
            for (var iterator = pending.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, JsonObject> entry = iterator.next();
                JsonObject raw = entry.getValue();
                String parentName = raw.has("parent") ? raw.get("parent").getAsString() : null;
                Bone parent = parentName == null ? null : byName.get(parentName);
                if (parentName != null && parent == null) continue;
                Bone bone = bone(bones.size(), entry.getKey(), parent, raw, textureWidth, textureHeight);
                bones.add(bone);
                byName.put(bone.name, bone);
                if (parent == null) roots.add(bone);
                else parent.children.add(bone);
                iterator.remove();
                placed = true;
            }
            if (!placed) throw new IllegalArgumentException("bones with a missing parent: " + pending.keySet());
        }
        return new ModelGeometry(roots, bones, byName, textureWidth, textureHeight);
    }

    private static double[] triple(JsonObject object, String key) {
        if (!object.has(key)) return new double[3];
        JsonArray array = object.getAsJsonArray(key);
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsDouble();
        return values;
    }

    private static Bone bone(int index, String name, Bone parent, JsonObject raw, double textureWidth, double textureHeight) {
        double[] rotation = triple(raw, "rotation");
        double[] pivot = triple(raw, "pivot");
        Double inflate = raw.has("inflate") ? raw.get("inflate").getAsDouble() : null;
        boolean neverRender = raw.has("neverRender") && raw.get("neverRender").getAsBoolean();
        Bone bone = new Bone(index, name, parent, (float) -pivot[0], (float) pivot[1], (float) pivot[2],
                (float) Math.toRadians(-rotation[0]), (float) Math.toRadians(-rotation[1]), (float) Math.toRadians(rotation[2]), inflate, neverRender);
        if (raw.has("cubes")) {
            for (JsonElement element : raw.getAsJsonArray("cubes")) bone.cubes.add(cube(element.getAsJsonObject(), bone, (float) textureWidth, (float) textureHeight));
        }
        return bone;
    }

    private static Cube cube(JsonObject raw, Bone bone, float textureWidth, float textureHeight) {
        boolean mirror = raw.has("mirror") && raw.get("mirror").getAsBoolean();
        double inflate = raw.has("inflate") ? raw.get("inflate").getAsDouble() / 16.0 : bone.inflate == null ? 0.0 : bone.inflate / 16.0;
        double[] size = triple(raw, "size");
        double[] origin = triple(raw, "origin");
        double[] rotation = triple(raw, "rotation");
        double[] pivot = triple(raw, "pivot");
        double originX = -(origin[0] + size[0]) / 16.0;
        double originY = origin[1] / 16.0;
        double originZ = origin[2] / 16.0;
        double sizeX = size[0] * .0625;
        double sizeY = size[1] * .0625;
        double sizeZ = size[2] * .0625;
        float[][] corners = {
                {(float) (originX - inflate), (float) (originY - inflate), (float) (originZ - inflate)},
                {(float) (originX - inflate), (float) (originY - inflate), (float) (originZ + sizeZ + inflate)},
                {(float) (originX - inflate), (float) (originY + sizeY + inflate), (float) (originZ - inflate)},
                {(float) (originX - inflate), (float) (originY + sizeY + inflate), (float) (originZ + sizeZ + inflate)},
                {(float) (originX + sizeX + inflate), (float) (originY + sizeY + inflate), (float) (originZ - inflate)},
                {(float) (originX + sizeX + inflate), (float) (originY + sizeY + inflate), (float) (originZ + sizeZ + inflate)},
                {(float) (originX + sizeX + inflate), (float) (originY - inflate), (float) (originZ - inflate)},
                {(float) (originX + sizeX + inflate), (float) (originY - inflate), (float) (originZ + sizeZ + inflate)}};
        JsonElement uv = raw.get("uv");
        boolean boxUv = uv == null || uv.isJsonArray();
        Quad[] quads = new Quad[6];
        for (Face face : Face.values()) quads[face.ordinal()] = quad(face, corners, uv, boxUv, size, mirror, textureWidth, textureHeight);
        return new Cube(quads, -pivot[0], pivot[1], pivot[2], Math.toRadians(-rotation[0]), Math.toRadians(-rotation[1]), Math.toRadians(rotation[2]),
                size[0], size[1], size[2], inflate, mirror);
    }

    private static int[] corners(Face face, boolean boxUv, boolean mirror) {
        return switch (face) {
            case WEST -> mirror ? new int[]{4, 5, 7, 6} : new int[]{3, 2, 0, 1};
            case EAST -> mirror ? new int[]{3, 2, 0, 1} : new int[]{4, 5, 7, 6};
            case NORTH -> new int[]{2, 4, 6, 0};
            case SOUTH -> new int[]{5, 3, 1, 7};
            case UP -> mirror && !boxUv ? new int[]{0, 6, 7, 1} : new int[]{3, 5, 4, 2};
            case DOWN -> mirror && !boxUv ? new int[]{3, 5, 4, 2} : new int[]{0, 6, 7, 1};
        };
    }

    private static Quad quad(Face face, float[][] corners, JsonElement uv, boolean boxUv, double[] size, boolean mirror, float textureWidth, float textureHeight) {
        float u;
        float v;
        float uSize;
        float vSize;
        if (!boxUv) {
            JsonObject faces = uv.getAsJsonObject();
            String key = face.name().toLowerCase(java.util.Locale.ROOT);
            if (!faces.has(key)) return null;
            JsonObject faceUv = faces.getAsJsonObject(key);
            double[] at = triple(faceUv, "uv");
            double[] extent = triple(faceUv, "uv_size");
            u = (float) at[0];
            v = (float) at[1];
            uSize = (float) extent[0];
            vSize = (float) extent[1];
        } else {
            double[] at = new double[2];
            if (uv != null) {
                JsonArray array = uv.getAsJsonArray();
                at[0] = array.get(0).getAsDouble();
                at[1] = array.get(1).getAsDouble();
            }
            double x = Math.floor(size[0]);
            double y = Math.floor(size[1]);
            double z = Math.floor(size[2]);
            double[][] data = switch (face) {
                case WEST -> new double[][]{{at[0] + z + x, at[1] + z}, {z, y}};
                case EAST -> new double[][]{{at[0], at[1] + z}, {z, y}};
                case NORTH -> new double[][]{{at[0] + z, at[1] + z}, {x, y}};
                case SOUTH -> new double[][]{{at[0] + z + x + z, at[1] + z}, {x, y}};
                case UP -> new double[][]{{at[0] + z, at[1]}, {x, z}};
                case DOWN -> new double[][]{{at[0] + z + x, at[1] + z}, {x, -z}};
            };
            u = (float) data[0][0];
            v = (float) data[0][1];
            uSize = (float) data[1][0];
            vSize = (float) data[1][1];
        }
        float uWidth = (u + uSize) / textureWidth;
        float vHeight = (v + vSize) / textureHeight;
        u /= textureWidth;
        v /= textureHeight;
        float nx = face.nx;
        float ny = face.ny;
        float nz = face.nz;
        if (!mirror) {
            float swap = uWidth;
            uWidth = u;
            u = swap;
        } else {
            nx *= -1;
        }
        int[] order = corners(face, boxUv, mirror);
        float[][] uvs = {{u, v}, {uWidth, v}, {uWidth, vHeight}, {u, vHeight}};
        Vertex[] vertices = new Vertex[4];
        for (int i = 0; i < 4; i++) {
            float[] p = corners[order[i]];
            vertices[i] = new Vertex(p[0], p[1], p[2], uvs[i][0], uvs[i][1]);
        }
        return new Quad(vertices, nx, ny, nz, face);
    }
}
