import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RenderItemIcon {
    static final int SIZE = 32;
    static final int SAMPLES = 8;
    static final int[] GLASS = {78, 104, 128};
    static final double GLASS_COVER = .82;
    static final double[] LIGHT = normalize(new double[]{.35, .9, -.45});

    record Face(double[][] corners, double[][] uv, double shade, double gloss, boolean pane) {}

    static BufferedImage texture;
    static int textureWidth;
    static int textureHeight;
    static final Map<String, JsonObject> bones = new LinkedHashMap<>();
    static final Set<String> glass = new HashSet<>();
    static final List<Face> faces = new ArrayList<>();
    static double yaw = Math.toRadians(35);
    static double pitch = Math.toRadians(-32);
    static double gloss;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: RenderItemIcon <assets dir> <vehicle> [--gloss n] [--yaw degrees] [--pitch degrees]");
            System.exit(2);
        }
        Path assets = Path.of(args[0]);
        String name = args[1];
        for (int i = 2; i + 1 < args.length; i += 2) {
            double value = Double.parseDouble(args[i + 1]);
            switch (args[i]) {
                case "--gloss" -> gloss = value;
                case "--yaw" -> yaw = Math.toRadians(value);
                case "--pitch" -> pitch = Math.toRadians(value);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        JsonObject vehicle = read(assets.resolve("vehicles").resolve(name + ".json"));
        if (vehicle.has("glass")) {
            for (JsonElement pane : vehicle.getAsJsonArray("glass")) glass.add(pane.getAsJsonObject().get("bone").getAsString());
        }
        String model = vehicle.get("model").getAsString();
        String skin = vehicle.has("texture") ? vehicle.get("texture").getAsString() : model;
        JsonObject geo = read(assets.resolve("geo").resolve(model + ".geo.json")).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        texture = ImageIO.read(assets.resolve("textures").resolve("entity").resolve(skin + ".png").toFile());
        textureWidth = geo.getAsJsonObject("description").get("texture_width").getAsInt();
        textureHeight = geo.getAsJsonObject("description").get("texture_height").getAsInt();
        for (JsonElement b : geo.getAsJsonArray("bones")) bones.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        for (JsonObject bone : bones.values()) {
            if (!bone.has("cubes")) continue;
            String bName = bone.get("name").getAsString();
            double[] matrix = chain(bName);
            boolean pane = underGlass(bName);
            for (JsonElement cube : bone.getAsJsonArray("cubes")) cube(matrix, cube.getAsJsonObject(), pane);
        }
        Path out = assets.resolve("textures").resolve("item").resolve(name + ".png");
        ImageIO.write(downsample(render()), "png", out.toFile());
        System.out.println("Wrote " + out);
    }

    static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static BufferedImage render() {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Face face : faces) {
            for (double[] p : face.corners()) {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
        }
        int s = SIZE * SAMPLES;
        double margin = s * .02;
        double scale = Math.min((s - 2 * margin) / (maxX - minX), (s - 2 * margin) / (maxY - minY));
        double ox = (s - (maxX - minX) * scale) / 2 - minX * scale;
        double oy = (s - (maxY - minY) * scale) / 2 + maxY * scale;
        double[] depth = new double[s * s];
        double[] paneDepth = new double[s * s];
        double[] paneShade = new double[s * s];
        int[] color = new int[s * s];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        Arrays.fill(paneDepth, Double.NEGATIVE_INFINITY);
        for (Face face : faces) {
            double[][] p = new double[4][];
            for (int k = 0; k < 4; k++) p[k] = new double[]{ox + face.corners()[k][0] * scale, oy - face.corners()[k][1] * scale, face.corners()[k][2]};
            double[][] t = face.uv();
            triangle(s, face, p[0], p[1], p[2], t[0], t[1], t[2], depth, paneDepth, paneShade, color);
            triangle(s, face, p[0], p[2], p[3], t[0], t[2], t[3], depth, paneDepth, paneShade, color);
        }
        BufferedImage image = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < s * s; i++) {
            boolean solid = depth[i] > Double.NEGATIVE_INFINITY;
            int c = solid ? color[i] : 0;
            if (paneDepth[i] > depth[i]) {
                int[] behind = solid ? new int[]{(c >> 16) & 255, (c >> 8) & 255, c & 255} : GLASS;
                int[] mixed = new int[3];
                for (int k = 0; k < 3; k++) mixed[k] = (int) (behind[k] * (1 - GLASS_COVER) + GLASS[k] * paneShade[i] * GLASS_COVER);
                c = 0xff000000 | mixed[0] << 16 | mixed[1] << 8 | mixed[2];
            }
            image.setRGB(i % s, i / s, c);
        }
        return image;
    }

    static BufferedImage downsample(BufferedImage full) {
        boolean[] filled = new boolean[SIZE * SIZE];
        int[][] rgb = new int[SIZE * SIZE][];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                long r = 0, g = 0, b = 0, n = 0;
                for (int sy = 0; sy < SAMPLES; sy++) {
                    for (int sx = 0; sx < SAMPLES; sx++) {
                        int c = full.getRGB(x * SAMPLES + sx, y * SAMPLES + sy);
                        if ((c >>> 24) == 0) continue;
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        n++;
                    }
                }
                if (n * 10 < SAMPLES * SAMPLES * 4L) continue;
                filled[y * SIZE + x] = true;
                rgb[y * SIZE + x] = new int[]{(int) (r / n), (int) (g / n), (int) (b / n)};
            }
        }
        BufferedImage icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                if (!filled[i]) continue;
                boolean edge = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1 || !filled[i - 1] || !filled[i + 1] || !filled[i - SIZE] || !filled[i + SIZE];
                int[] c = new int[3];
                for (int k = 0; k < 3; k++) {
                    c[k] = (int) Math.max(0, Math.min(255, Math.round((rgb[i][k] - 128) * 1.12 + 128)));
                    if (edge) c[k] = (int) (c[k] * (k == 2 ? .5 : .45));
                }
                icon.setRGB(x, y, 0xff000000 | c[0] << 16 | c[1] << 8 | c[2]);
            }
        }
        return icon;
    }

    static boolean underGlass(String name) {
        for (String b = name; b != null; b = bones.get(b).has("parent") ? bones.get(b).get("parent").getAsString() : null) {
            if (glass.contains(b)) return true;
        }
        return false;
    }

    static double[] chain(String name) {
        JsonObject bone = bones.get(name);
        double[] parent = bone.has("parent") ? chain(bone.get("parent").getAsString()) : identity();
        double[] p = vector(bone, "pivot");
        double[] r = vector(bone, "rotation");
        return multiply(parent, local(new double[]{-p[0], p[1], p[2]}, new double[]{Math.toRadians(-r[0]), Math.toRadians(-r[1]), Math.toRadians(r[2])}));
    }

    static double[] vector(JsonObject o, String key) {
        double[] v = new double[3];
        if (!o.has(key)) return v;
        for (int i = 0; i < 3; i++) v[i] = o.getAsJsonArray(key).get(i).getAsDouble();
        return v;
    }

    static void cube(double[] bone, JsonObject cube, boolean pane) {
        double[] o = vector(cube, "origin");
        double[] s = vector(cube, "size");
        double inflate = cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0;
        double x0 = -(o[0] + s[0]) - inflate, y0 = o[1] - inflate, z0 = o[2] - inflate;
        double x1 = -o[0] + inflate, y1 = o[1] + s[1] + inflate, z1 = o[2] + s[2] + inflate;
        double[] m = bone;
        if (cube.has("rotation")) {
            double[] p = vector(cube, "pivot");
            double[] r = vector(cube, "rotation");
            m = multiply(bone, local(new double[]{-p[0], p[1], p[2]}, new double[]{Math.toRadians(-r[0]), Math.toRadians(-r[1]), Math.toRadians(r[2])}));
        }
        JsonObject uv = cube.getAsJsonObject("uv");
        face(m, uv, "north", new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, pane);
        face(m, uv, "south", new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, pane);
        face(m, uv, "east", new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, pane);
        face(m, uv, "west", new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, pane);
        face(m, uv, "up", new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, pane);
        face(m, uv, "down", new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, pane);
    }

    static void face(double[] m, JsonObject uvs, String key, double[][] corners, boolean pane) {
        if (!uvs.has(key)) return;
        JsonObject f = uvs.getAsJsonObject(key);
        double[] uv = {f.getAsJsonArray("uv").get(0).getAsDouble(), f.getAsJsonArray("uv").get(1).getAsDouble()};
        double[] size = {f.getAsJsonArray("uv_size").get(0).getAsDouble(), f.getAsJsonArray("uv_size").get(1).getAsDouble()};
        boolean flat = key.equals("up") || key.equals("down");
        double u1 = flat ? uv[0] + size[0] : uv[0], v1 = flat ? uv[1] + size[1] : uv[1];
        double u2 = flat ? uv[0] : uv[0] + size[0], v2 = flat ? uv[1] : uv[1] + size[1];
        double[][] world = new double[4][];
        double[][] screen = new double[4][];
        for (int i = 0; i < 4; i++) {
            world[i] = apply(m, corners[i]);
            screen[i] = project(world[i]);
        }
        double[] normal = normalize(cross(subtract(world[1], world[0]), subtract(world[3], world[0])));
        double shade = .58 + .42 * Math.abs(dot(normal, LIGHT));
        faces.add(new Face(screen, new double[][]{{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}}, shade, Math.pow(Math.abs(normal[1]), 3) * gloss, pane));
    }

    static double[] project(double[] w) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double x = w[0] * c + w[2] * s;
        double z = -w[0] * s + w[2] * c;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        return new double[]{-x, w[1] * cp - z * sp, -(w[1] * sp + z * cp)};
    }

    static void triangle(int s, Face face, double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc,
                         double[] depth, double[] paneDepth, double[] paneShade, int[] color) {
        int minX = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = (int) Math.min(s - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = (int) Math.min(s - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < 1e-9) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + .5, py = y + .5;
                double w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
                double w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int i = y * s + x;
                if (face.pane()) {
                    if (z > paneDepth[i]) {
                        paneDepth[i] = z;
                        paneShade[i] = face.shade();
                    }
                    continue;
                }
                if (z <= depth[i]) continue;
                double u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0];
                double v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
                int tx = Math.max(0, Math.min(texture.getWidth() - 1, (int) Math.floor(u * texture.getWidth() / textureWidth)));
                int ty = Math.max(0, Math.min(texture.getHeight() - 1, (int) Math.floor(v * texture.getHeight() / textureHeight)));
                int argb = texture.getRGB(tx, ty);
                if ((argb >>> 24) < 128) continue;
                depth[i] = z;
                int r = (int) Math.min(255, ((argb >> 16) & 255) * face.shade() + face.gloss());
                int g = (int) Math.min(255, ((argb >> 8) & 255) * face.shade() + face.gloss());
                int bl = (int) Math.min(255, (argb & 255) * face.shade() + face.gloss() * 1.1);
                color[i] = 0xff000000 | r << 16 | g << 8 | bl;
            }
        }
    }

    static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static double[] local(double[] p, double[] r) {
        double[] m = translation(p[0], p[1], p[2]);
        m = multiply(m, rotation(r[2], 2));
        m = multiply(m, rotation(r[1], 1));
        m = multiply(m, rotation(r[0], 0));
        return multiply(m, translation(-p[0], -p[1], -p[2]));
    }

    static double[] translation(double x, double y, double z) {
        return new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    static double[] rotation(double a, int axis) {
        double c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            case 0 -> new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
            case 1 -> new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
            default -> new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        };
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

    static double[] apply(double[] m, double[] p) {
        return new double[]{m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3], m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7], m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]};
    }

    static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] normalize(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-12 ? a : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }
}
