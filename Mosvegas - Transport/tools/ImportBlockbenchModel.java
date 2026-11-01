import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportBlockbenchModel {
    static final String[] FACES = {"north", "east", "south", "west", "up", "down"};
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static final double PANE_REACH = 6;
    static final double PANE_DEPTH = 2.5;
    static final double PANE_STEP = .25;
    static final double PANE_OVERHANG = 4;

    record Group(String name, double[] origin, double[] rotation, Group parent, List<Object> children) {}

    record Cube(String name, JsonObject element, Group group) {}

    static final class Bone {
        String name;
        String parent;
        double[] pivot = {0, 0, 0};
        double[] rotation = {0, 0, 0};
        boolean glass;
        final List<Cube> cubes = new ArrayList<>();
    }

    static final Map<String, Group> groups = new LinkedHashMap<>();
    static final List<Cube> cubes = new ArrayList<>();
    static int textureWidth;
    static int textureHeight;
    static BufferedImage texture;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: ImportBlockbenchModel <model.bbmodel> (<rig.json> <assets dir> | --compare <geo.json>)");
            System.exit(2);
        }
        load(Path.of(args[0]));
        if (args[1].equals("--compare")) {
            System.exit(compare(plainBones(), read(Path.of(args[2]))) == 0 ? 0 : 1);
        }
        JsonObject rig = read(Path.of(args[1])).getAsJsonObject();
        String name = rig.get("name").getAsString();
        List<Bone> bones = rigBones(rig);
        Map<Cube, double[]> panes = rig.has("glass") ? paintPanes(rig.getAsJsonObject("glass"), bones) : Map.of();
        Path assets = Path.of(args[2]);
        Path geo = assets.resolve("geo").resolve(name + ".geo.json");
        Path png = assets.resolve("textures").resolve("entity").resolve(name + ".png");
        Files.createDirectories(geo.getParent());
        Files.createDirectories(png.getParent());
        Files.writeString(geo, geometry(name, rig, bones, panes), StandardCharsets.UTF_8);
        ImageIO.write(texture, "png", png.toFile());
        int count = 0;
        for (Bone bone : bones) count += bone.cubes.size();
        System.out.println("Wrote " + geo + " (" + bones.size() + " bones, " + count + " cubes) and " + png);
    }

    static JsonElement read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
    }

    static void load(Path path) throws IOException {
        JsonObject model = read(path).getAsJsonObject();
        Map<String, JsonObject> elements = new HashMap<>();
        for (JsonElement e : model.getAsJsonArray("elements")) elements.put(e.getAsJsonObject().get("uuid").getAsString(), e.getAsJsonObject());
        Map<String, JsonObject> groupData = new HashMap<>();
        if (model.has("groups")) {
            for (JsonElement g : model.getAsJsonArray("groups")) groupData.put(g.getAsJsonObject().get("uuid").getAsString(), g.getAsJsonObject());
        }
        Group root = new Group("", new double[3], new double[3], null, new ArrayList<>());
        for (JsonElement node : model.getAsJsonArray("outliner")) walk(node, root, elements, groupData);
        JsonObject tex = model.getAsJsonArray("textures").get(0).getAsJsonObject();
        String source = tex.get("source").getAsString();
        texture = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1))));
        JsonObject resolution = model.getAsJsonObject("resolution");
        textureWidth = tex.has("uv_width") ? tex.get("uv_width").getAsInt() : resolution.get("width").getAsInt();
        textureHeight = tex.has("uv_height") ? tex.get("uv_height").getAsInt() : resolution.get("height").getAsInt();
    }

    static void walk(JsonElement node, Group parent, Map<String, JsonObject> elements, Map<String, JsonObject> groupData) {
        if (node.isJsonPrimitive()) {
            JsonObject element = elements.get(node.getAsString());
            if (element == null || element.has("export") && !element.get("export").getAsBoolean()) return;
            Cube cube = new Cube(element.get("name").getAsString(), element, parent);
            cubes.add(cube);
            parent.children().add(cube);
            return;
        }
        JsonObject outline = node.getAsJsonObject();
        JsonObject data = outline.has("name") ? outline : groupData.get(outline.get("uuid").getAsString());
        if (data.has("export") && !data.get("export").getAsBoolean()) return;
        String name = data.get("name").getAsString();
        if (groups.containsKey(name)) throw new IllegalStateException("Two groups are named " + name);
        Group group = new Group(name, vector(data, "origin"), vector(data, "rotation"), parent, new ArrayList<>());
        groups.put(name, group);
        parent.children().add(group);
        for (JsonElement child : outline.getAsJsonArray("children")) walk(child, group, elements, groupData);
    }

    static double[] vector(JsonObject o, String key) {
        double[] v = new double[3];
        if (!o.has(key)) return v;
        JsonArray a = o.getAsJsonArray(key);
        for (int i = 0; i < 3; i++) v[i] = a.get(i).getAsDouble();
        return v;
    }

    static List<Bone> plainBones() {
        List<Bone> bones = new ArrayList<>();
        for (Group group : groups.values()) {
            Bone bone = new Bone();
            bone.name = group.name();
            bone.parent = group.parent().name().isEmpty() ? null : group.parent().name();
            bone.pivot = group.origin();
            bone.rotation = group.rotation();
            for (Object child : group.children()) if (child instanceof Cube cube) bone.cubes.add(cube);
            bones.add(bone);
        }
        return bones;
    }

    static List<Bone> rigBones(JsonObject rig) {
        Map<String, Bone> byName = new LinkedHashMap<>();
        Map<String, Bone> byGroup = new HashMap<>();
        Map<Cube, Bone> placed = new IdentityHashMap<>();
        for (JsonElement e : rig.getAsJsonArray("bones")) {
            JsonObject o = e.getAsJsonObject();
            Bone bone = new Bone();
            bone.name = o.get("name").getAsString();
            if (byName.containsKey(bone.name)) throw new IllegalStateException("Bone " + bone.name + " is listed twice");
            if (o.has("parent")) {
                bone.parent = o.get("parent").getAsString();
                if (!byName.containsKey(bone.parent)) throw new IllegalStateException("Bone " + bone.name + " needs its parent " + bone.parent + " listed before it");
            }
            if (o.has("like")) {
                Group like = group(o.get("like").getAsString());
                bone.pivot = like.origin().clone();
                bone.rotation = like.rotation().clone();
            }
            if (o.has("pivot")) bone.pivot = vector(o, "pivot");
            if (o.has("rotation")) bone.rotation = vector(o, "rotation");
            bone.glass = o.has("glass") && o.get("glass").getAsBoolean();
            if (o.has("groups")) {
                for (JsonElement g : o.getAsJsonArray("groups")) {
                    String name = group(g.getAsString()).name();
                    if (byGroup.put(name, bone) != null) throw new IllegalStateException("Group " + name + " is given to two bones");
                }
            }
            if (o.has("cubes")) {
                for (JsonElement c : o.getAsJsonArray("cubes")) {
                    for (String name : expand(c.getAsString())) {
                        Cube cube = cube(name);
                        if (placed.put(cube, bone) != null) throw new IllegalStateException("Cube " + name + " is given to two bones");
                        bone.cubes.add(cube);
                    }
                }
            }
            byName.put(bone.name, bone);
        }
        for (Cube cube : cubes) {
            if (placed.containsKey(cube)) continue;
            Bone bone = null;
            for (Group g = cube.group(); g != null && bone == null; g = g.parent()) bone = byGroup.get(g.name());
            if (bone == null) throw new IllegalStateException("Cube " + cube.name() + " in group " + cube.group().name() + " has no bone");
            placed.put(cube, bone);
            bone.cubes.add(cube);
        }
        for (Map.Entry<Cube, Bone> entry : placed.entrySet()) {
            double[] source = chain(entry.getKey().group());
            double[] target = chain(entry.getValue(), byName);
            for (int i = 0; i < 16; i++) {
                if (Math.abs(source[i] - target[i]) > 1e-4) {
                    throw new IllegalStateException("Cube " + entry.getKey().name() + " would move: bone " + entry.getValue().name
                            + " does not repeat the pivots and rotations of group " + entry.getKey().group().name());
                }
            }
        }
        return new ArrayList<>(byName.values());
    }

    static Group group(String name) {
        Group group = groups.get(name);
        if (group == null) throw new IllegalStateException("No group " + name);
        return group;
    }

    static Cube cube(String name) {
        Cube found = null;
        for (Cube cube : cubes) {
            if (!cube.name().equals(name)) continue;
            if (found != null) throw new IllegalStateException("Two cubes are named " + name);
            found = cube;
        }
        if (found == null) throw new IllegalStateException("No cube " + name);
        return found;
    }

    static List<String> expand(String name) {
        int dots = name.indexOf("..");
        if (dots < 0) return List.of(name);
        int underscore = name.lastIndexOf('_', dots);
        String prefix = name.substring(0, underscore + 1);
        int from = Integer.parseInt(name.substring(underscore + 1, dots));
        int to = Integer.parseInt(name.substring(dots + 2));
        List<String> names = new ArrayList<>();
        for (int i = from; i <= to; i++) names.add(prefix + i);
        return names;
    }

    static double[] chain(Group group) {
        double[] m = identity();
        List<Group> path = new ArrayList<>();
        for (Group g = group; g != null && !g.name().isEmpty(); g = g.parent()) path.add(0, g);
        for (Group g : path) m = multiply(m, local(g.origin(), g.rotation()));
        return m;
    }

    static double[] chain(Bone bone, Map<String, Bone> bones) {
        double[] m = identity();
        List<Bone> path = new ArrayList<>();
        for (Bone b = bone; b != null; b = b.parent == null ? null : bones.get(b.parent)) path.add(0, b);
        for (Bone b : path) m = multiply(m, local(b.pivot, b.rotation));
        return m;
    }

    static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static double[] local(double[] pivot, double[] rotation) {
        double[] m = translation(pivot[0], pivot[1], pivot[2]);
        m = multiply(m, rotationZ(Math.toRadians(rotation[2])));
        m = multiply(m, rotationY(Math.toRadians(rotation[1])));
        m = multiply(m, rotationX(Math.toRadians(rotation[0])));
        return multiply(m, translation(-pivot[0], -pivot[1], -pivot[2]));
    }

    static double[] translation(double x, double y, double z) {
        return new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    static double[] rotationX(double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    static double[] rotationY(double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    static double[] rotationZ(double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static double[] multiply(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) sum += a[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = sum;
            }
        }
        return r;
    }

    record Frame(double[] inverse, double[] from, double[] to) {}

    static Map<Cube, double[]> paintPanes(JsonObject glass, List<Bone> bones) {
        int rgb = Integer.parseInt(glass.get("color").getAsString().substring(1), 16);
        double scale = texture.getWidth() / (double) textureWidth;
        List<Cube> panes = new ArrayList<>();
        List<Cube> solid = new ArrayList<>();
        for (Bone bone : bones) (bone.glass ? panes : solid).addAll(bone.cubes);
        int width = texture.getWidth();
        int height = texture.getHeight();
        boolean[] taken = new boolean[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) taken[y * width + x] = (texture.getRGB(x, y) >>> 24) != 0;
        for (Cube cube : solid) {
            JsonObject faces = cube.element().getAsJsonObject("faces");
            for (String key : FACES) if (textured(faces, key)) take(taken, width, height, uv(faces.getAsJsonObject(key)), scale);
        }
        Map<Cube, double[]> uvs = new IdentityHashMap<>();
        for (Cube cube : panes) {
            JsonObject faces = cube.element().getAsJsonObject("faces");
            String key = paneFace(vector(cube.element(), "from"), vector(cube.element(), "to"), faces);
            if (!textured(faces, key)) continue;
            double[] u = uv(faces.getAsJsonObject(key));
            int w = Math.max(1, (int) Math.round(Math.abs(u[2] - u[0]) * scale));
            int h = Math.max(1, (int) Math.round(Math.abs(u[3] - u[1]) * scale));
            int[] spot = free(taken, width, height, w + 2, h + 2);
            boolean[][] mask = paneMask(cube, key, w, h, solid);
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) texture.setRGB(spot[0] + 1 + i, spot[1] + 1 + j, mask[j][i] ? 0xff000000 | rgb : 0);
            uvs.put(cube, new double[]{(spot[0] + 1) / scale, (spot[1] + 1) / scale, (spot[0] + 1 + w) / scale, (spot[1] + 1 + h) / scale});
        }
        return uvs;
    }

    static double[] uv(JsonObject face) {
        JsonArray a = face.getAsJsonArray("uv");
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()};
    }

    static void take(boolean[] taken, int width, int height, double[] u, double scale) {
        int x0 = Math.max(0, (int) Math.floor(Math.min(u[0], u[2]) * scale) - 1);
        int x1 = Math.min(width, (int) Math.ceil(Math.max(u[0], u[2]) * scale) + 1);
        int y0 = Math.max(0, (int) Math.floor(Math.min(u[1], u[3]) * scale) - 1);
        int y1 = Math.min(height, (int) Math.ceil(Math.max(u[1], u[3]) * scale) + 1);
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) taken[y * width + x] = true;
    }

    static int[] free(boolean[] taken, int width, int height, int w, int h) {
        int[] sum = new int[(width + 1) * (height + 1)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sum[(y + 1) * (width + 1) + x + 1] = (taken[y * width + x] ? 1 : 0) + sum[y * (width + 1) + x + 1] + sum[(y + 1) * (width + 1) + x] - sum[y * (width + 1) + x];
            }
        }
        for (int y = 0; y + h <= height; y++) {
            for (int x = 0; x + w <= width; x++) {
                int used = sum[(y + h) * (width + 1) + x + w] - sum[y * (width + 1) + x + w] - sum[(y + h) * (width + 1) + x] + sum[y * (width + 1) + x];
                if (used != 0) continue;
                for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++) taken[py * width + px] = true;
                return new int[]{x, y};
            }
        }
        throw new IllegalStateException("No free " + w + "x" + h + " area left in the texture for a pane");
    }

    static boolean[][] paneMask(Cube pane, String key, int w, int h, List<Cube> solid) {
        double[] from = vector(pane.element(), "from");
        double[] to = vector(pane.element(), "to");
        double[] matrix = cubeMatrix(pane);
        double[] normal = transform(matrix, faceNormal(key), 0);
        double[][] axes = faceAxes(from, to, key);
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        bounds(matrix, from, to, lo, hi);
        List<Frame> frames = new ArrayList<>();
        for (Cube cube : solid) {
            double inflate = cube.element().has("inflate") ? cube.element().get("inflate").getAsDouble() : 0;
            double[] f = vector(cube.element(), "from");
            double[] t = vector(cube.element(), "to");
            for (int i = 0; i < 3; i++) {
                f[i] -= inflate;
                t[i] += inflate;
            }
            double[] m = cubeMatrix(cube);
            double[] clo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] chi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            bounds(m, f, t, clo, chi);
            boolean near = true;
            for (int i = 0; i < 3; i++) near &= chi[i] > lo[i] - PANE_REACH && clo[i] < hi[i] + PANE_REACH;
            if (near) frames.add(new Frame(rigidInverse(m), f, t));
        }
        double lengthS = length(axes[1]);
        double lengthT = length(axes[2]);
        boolean[][] mask = new boolean[h][w];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                double s = (i + .5) / w;
                double t = (j + .5) / h;
                mask[j][i] = framed(matrix, axes, normal, frames, s, t, 1 / lengthS, 0, (1 - s) * lengthS)
                        && framed(matrix, axes, normal, frames, s, t, -1 / lengthS, 0, s * lengthS)
                        && framed(matrix, axes, normal, frames, s, t, 0, 1 / lengthT, (1 - t) * lengthT)
                        && framed(matrix, axes, normal, frames, s, t, 0, -1 / lengthT, t * lengthT);
            }
        }
        return mask;
    }

    static boolean framed(double[] matrix, double[][] axes, double[] normal, List<Frame> frames, double s, double t, double ds, double dt, double toEdge) {
        for (double d = 0; d <= toEdge + PANE_OVERHANG; d += PANE_STEP) {
            double[] local = new double[3];
            for (int k = 0; k < 3; k++) local[k] = axes[0][k] + axes[1][k] * (s + ds * d) + axes[2][k] * (t + dt * d);
            double[] point = transform(matrix, local, 1);
            for (Frame frame : frames) if (crosses(frame, point, normal)) return true;
        }
        return false;
    }

    static boolean crosses(Frame frame, double[] point, double[] normal) {
        double[] o = transform(frame.inverse(), point, 1);
        double[] d = transform(frame.inverse(), normal, 0);
        double enter = -PANE_DEPTH;
        double leave = PANE_DEPTH;
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-9) {
                if (o[k] < frame.from()[k] || o[k] > frame.to()[k]) return false;
                continue;
            }
            double a = (frame.from()[k] - o[k]) / d[k];
            double b = (frame.to()[k] - o[k]) / d[k];
            enter = Math.max(enter, Math.min(a, b));
            leave = Math.min(leave, Math.max(a, b));
            if (enter > leave) return false;
        }
        return true;
    }

    static double[][] faceAxes(double[] from, double[] to, String key) {
        double sx = to[0] - from[0];
        double sy = to[1] - from[1];
        double sz = to[2] - from[2];
        return switch (key) {
            case "west" -> new double[][]{{from[0], to[1], from[2]}, {0, 0, sz}, {0, -sy, 0}};
            case "east" -> new double[][]{{to[0], to[1], to[2]}, {0, 0, -sz}, {0, -sy, 0}};
            case "north" -> new double[][]{{to[0], to[1], from[2]}, {-sx, 0, 0}, {0, -sy, 0}};
            case "south" -> new double[][]{{from[0], to[1], to[2]}, {sx, 0, 0}, {0, -sy, 0}};
            case "up" -> new double[][]{{from[0], to[1], from[2]}, {sx, 0, 0}, {0, 0, sz}};
            default -> new double[][]{{from[0], from[1], to[2]}, {sx, 0, 0}, {0, 0, -sz}};
        };
    }

    static double[] faceNormal(String key) {
        return switch (key) {
            case "west" -> new double[]{-1, 0, 0};
            case "east" -> new double[]{1, 0, 0};
            case "north" -> new double[]{0, 0, -1};
            case "south" -> new double[]{0, 0, 1};
            case "up" -> new double[]{0, 1, 0};
            default -> new double[]{0, -1, 0};
        };
    }

    static double[] cubeMatrix(Cube cube) {
        return multiply(chain(cube.group()), local(vector(cube.element(), "origin"), vector(cube.element(), "rotation")));
    }

    static double[] transform(double[] m, double[] v, double w) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = m[i * 4] * v[0] + m[i * 4 + 1] * v[1] + m[i * 4 + 2] * v[2] + m[i * 4 + 3] * w;
        return r;
    }

    static double[] rigidInverse(double[] m) {
        double[] r = new double[16];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) r[i * 4 + j] = m[j * 4 + i];
        for (int i = 0; i < 3; i++) r[i * 4 + 3] = -(r[i * 4] * m[3] + r[i * 4 + 1] * m[7] + r[i * 4 + 2] * m[11]);
        r[15] = 1;
        return r;
    }

    static void bounds(double[] m, double[] from, double[] to, double[] lo, double[] hi) {
        for (int c = 0; c < 8; c++) {
            double[] p = transform(m, new double[]{(c & 1) == 0 ? from[0] : to[0], (c & 2) == 0 ? from[1] : to[1], (c & 4) == 0 ? from[2] : to[2]}, 1);
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], p[k]);
                hi[k] = Math.max(hi[k], p[k]);
            }
        }
    }

    static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    static JsonObject exportCube(Cube cube, double[] paneUv) {
        JsonObject e = cube.element();
        double[] from = vector(e, "from");
        double[] to = vector(e, "to");
        double[] rotation = vector(e, "rotation");
        double[] origin = vector(e, "origin");
        double inflate = e.has("inflate") ? e.get("inflate").getAsDouble() : 0;
        JsonObject out = new JsonObject();
        out.add("origin", array(-to[0], from[1], from[2]));
        out.add("size", array(to[0] - from[0], to[1] - from[1], to[2] - from[2]));
        if (inflate != 0) out.add("inflate", number(inflate));
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            out.add("pivot", array(-origin[0], origin[1], origin[2]));
            out.add("rotation", array(-rotation[0], -rotation[1], rotation[2]));
        }
        JsonObject uv = new JsonObject();
        JsonObject faces = e.getAsJsonObject("faces");
        String pane = paneUv == null ? null : paneFace(from, to, faces);
        for (String key : FACES) {
            if (!faces.has(key) || pane != null && !key.equals(pane)) continue;
            JsonObject face = faces.getAsJsonObject(key);
            if (!face.has("texture") || face.get("texture").isJsonNull()) continue;
            double[] u = new double[4];
            JsonArray a = face.getAsJsonArray("uv");
            for (int i = 0; i < 4; i++) u[i] = a.get(i).getAsDouble();
            if (paneUv != null) u = paneUv.clone();
            double[] start = {u[0], u[1]};
            double[] size = {u[2] - u[0], u[3] - u[1]};
            if (key.equals("up") || key.equals("down")) {
                start = new double[]{u[0] + size[0], u[1] + size[1]};
                size = new double[]{-size[0], -size[1]};
            }
            JsonObject f = new JsonObject();
            f.add("uv", array(start));
            f.add("uv_size", array(size));
            if (paneUv == null && face.has("rotation") && face.get("rotation").getAsDouble() != 0) f.add("uv_rotation", number(face.get("rotation").getAsDouble()));
            uv.add(key, f);
        }
        out.add("uv", uv);
        return out;
    }

    static String paneFace(double[] from, double[] to, JsonObject faces) {
        double[] size = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        int thin = 0;
        for (int i = 1; i < 3; i++) if (size[i] < size[thin]) thin = i;
        boolean positive = thin == 1 || (from[thin] + to[thin]) / 2 >= 0;
        String[][] sides = {{"west", "east"}, {"down", "up"}, {"north", "south"}};
        String outer = sides[thin][positive ? 1 : 0];
        String inner = sides[thin][positive ? 0 : 1];
        return textured(faces, outer) || !textured(faces, inner) ? outer : inner;
    }

    static boolean textured(JsonObject faces, String key) {
        return faces.has(key) && faces.getAsJsonObject(key).has("texture") && !faces.getAsJsonObject(key).get("texture").isJsonNull();
    }

    static JsonArray array(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) a.add(number(v));
        return a;
    }

    static JsonPrimitive number(double v) {
        BigDecimal d = BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        if (d.signum() == 0) return new JsonPrimitive(0);
        return d.scale() <= 0 ? new JsonPrimitive(d.longValueExact()) : new JsonPrimitive(d);
    }

    static JsonObject exportBone(Bone bone) {
        JsonObject out = new JsonObject();
        out.addProperty("name", bone.name);
        if (bone.parent != null) out.addProperty("parent", bone.parent);
        out.add("pivot", array(-bone.pivot[0], bone.pivot[1], bone.pivot[2]));
        if (bone.rotation[0] != 0 || bone.rotation[1] != 0 || bone.rotation[2] != 0) out.add("rotation", array(-bone.rotation[0], -bone.rotation[1], bone.rotation[2]));
        return out;
    }

    static String geometry(String name, JsonObject rig, List<Bone> bones, Map<Cube, double[]> panes) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry." + name);
        description.addProperty("texture_width", textureWidth);
        description.addProperty("texture_height", textureHeight);
        JsonObject bounds = rig.has("visible_bounds") ? rig.getAsJsonObject("visible_bounds") : new JsonObject();
        description.add("visible_bounds_width", number(bounds.has("width") ? bounds.get("width").getAsDouble() : 8));
        description.add("visible_bounds_height", number(bounds.has("height") ? bounds.get("height").getAsDouble() : 3.5));
        description.add("visible_bounds_offset", bounds.has("offset") ? bounds.getAsJsonArray("offset") : array(0, 1.25, 0));
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n      \"description\": ")
                .append(GSON.toJson(description)).append(",\n      \"bones\": [\n");
        for (int b = 0; b < bones.size(); b++) {
            Bone bone = bones.get(b);
            String head = GSON.toJson(exportBone(bone));
            sb.append("        ").append(head, 0, head.length() - 1);
            if (!bone.cubes.isEmpty()) {
                sb.append(",\"cubes\":[\n");
                for (int c = 0; c < bone.cubes.size(); c++) {
                    sb.append("          ").append(GSON.toJson(exportCube(bone.cubes.get(c), panes.get(bone.cubes.get(c)))));
                    sb.append(c + 1 < bone.cubes.size() ? ",\n" : "\n");
                }
                sb.append("        ]");
            }
            sb.append("}").append(b + 1 < bones.size() ? ",\n" : "\n");
        }
        sb.append("      ]\n    }\n  ]\n}\n");
        return sb.toString();
    }

    static int compare(List<Bone> bones, JsonElement reference) {
        Map<String, JsonObject> expected = new HashMap<>();
        for (JsonElement b : reference.getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            expected.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        }
        int mismatches = 0;
        int checked = 0;
        for (Bone bone : bones) {
            JsonObject want = expected.get(bone.name);
            if (want == null) {
                System.out.println("missing bone " + bone.name);
                mismatches++;
                continue;
            }
            JsonObject got = exportBone(bone);
            mismatches += differ(bone.name + ".pivot", got.get("pivot"), want.get("pivot"));
            mismatches += differ(bone.name + ".rotation", got.get("rotation"), want.get("rotation"));
            JsonArray wantCubes = want.has("cubes") ? want.getAsJsonArray("cubes") : new JsonArray();
            if (wantCubes.size() != bone.cubes.size()) {
                System.out.println(bone.name + ": " + bone.cubes.size() + " cubes, reference has " + wantCubes.size());
                mismatches++;
                continue;
            }
            for (int i = 0; i < bone.cubes.size(); i++) {
                JsonObject cube = exportCube(bone.cubes.get(i), null);
                JsonObject ref = wantCubes.get(i).getAsJsonObject();
                String at = bone.name + "[" + i + "] " + bone.cubes.get(i).name();
                for (String key : new String[]{"origin", "size", "pivot", "rotation", "inflate"}) mismatches += differ(at + "." + key, cube.get(key), ref.get(key));
                JsonObject uv = cube.getAsJsonObject("uv");
                JsonObject refUv = ref.has("uv") ? ref.getAsJsonObject("uv") : new JsonObject();
                for (String face : FACES) {
                    JsonObject a = uv.has(face) ? uv.getAsJsonObject(face) : null;
                    JsonObject r = refUv.has(face) ? refUv.getAsJsonObject(face) : null;
                    if (a == null || r == null) {
                        if (a != r) {
                            System.out.println(at + "." + face + ": face present only on one side");
                            mismatches++;
                        }
                        continue;
                    }
                    mismatches += differ(at + "." + face + ".uv", a.get("uv"), r.get("uv"));
                    mismatches += differ(at + "." + face + ".uv_size", a.get("uv_size"), r.get("uv_size"));
                }
                checked++;
            }
        }
        System.out.println("compared " + checked + " cubes, " + mismatches + " mismatches");
        return mismatches;
    }

    static int differ(String what, JsonElement a, JsonElement b) {
        double[] x = values(a);
        double[] y = values(b);
        boolean same = x.length == y.length;
        for (int i = 0; same && i < x.length; i++) same = Math.abs(x[i] - y[i]) < 1e-3;
        if (same) return 0;
        System.out.println(what + ": " + a + " vs " + b);
        return 1;
    }

    static double[] values(JsonElement e) {
        if (e == null || e.isJsonNull()) return new double[0];
        if (e.isJsonPrimitive()) return new double[]{e.getAsDouble()};
        JsonArray a = e.getAsJsonArray();
        double[] v = new double[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsDouble();
        boolean zero = true;
        for (double d : v) zero &= d == 0;
        return zero ? new double[0] : v;
    }
}
