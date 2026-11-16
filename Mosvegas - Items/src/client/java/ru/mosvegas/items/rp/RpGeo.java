package ru.mosvegas.items.rp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class RpGeo {
    public final int textureWidth, textureHeight;
    public final List<Bone> bones = new ArrayList<>();
    public final Map<String, Bone> byName = new HashMap<>();

    public static final class Bone {
        public final String name;
        public final int index;
        Bone parent;
        String parentName;
        final float[] pivot;
        final float[] rotation;
        final List<Cube> cubes = new ArrayList<>();

        boolean target;

        boolean inHand;

        Bone(String name, int index, float[] pivot, float[] rotation) {
            this.name = name;
            this.index = index;
            this.pivot = pivot;
            this.rotation = rotation;
        }

        public boolean isTarget() {
            return target;
        }

        public boolean isInHand() {
            return inHand;
        }

        public Bone parent() {
            return parent;
        }

        public boolean hasCubes() {
            return !cubes.isEmpty();
        }
    }

    record Face(float[][] corners, float[][] uv, float[] normal) {}

    static final class Cube {
        final Matrix4f local;
        final List<Face> faces = new ArrayList<>();

        Cube(Matrix4f local) {
            this.local = local;
        }
    }

    RpGeo(JsonObject root) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        textureWidth = description.get("texture_width").getAsInt();
        textureHeight = description.get("texture_height").getAsInt();
        for (var element : geometry.getAsJsonArray("bones")) {
            JsonObject b = element.getAsJsonObject();
            Bone bone = new Bone(b.get("name").getAsString(), bones.size(), vec(b.getAsJsonArray("pivot"), 0),
                    vec(b.getAsJsonArray("rotation"), 0));
            bone.parentName = b.has("parent") ? b.get("parent").getAsString() : null;
            if (b.has("cubes")) {
                for (var c : b.getAsJsonArray("cubes")) bone.cubes.add(cube(c.getAsJsonObject()));
            }
            bones.add(bone);
            byName.put(bone.name, bone);
        }
        for (Bone bone : bones) bone.parent = bone.parentName == null ? null : byName.get(bone.parentName);
        for (Bone bone : bones) {
            for (Bone b = bone; b != null; b = b.parent) {
                if (b.name.equals("t_player")) bone.target = true;
                if (b.name.equals("item") || b.name.equals("item_spin")) bone.inHand = true;
            }
        }
    }

    private Cube cube(JsonObject c) {
        float[] origin = vec(c.getAsJsonArray("origin"), 0), size = vec(c.getAsJsonArray("size"), 0);
        float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
        float[] cubePivot = vec(c.getAsJsonArray("pivot"), 0), cubeRotation = vec(c.getAsJsonArray("rotation"), 0);
        Cube cube = new Cube(pivotRotation(cubePivot, cubeRotation));
        float[] min = {origin[0] - inflate, origin[1] - inflate, origin[2] - inflate};
        float[] max = {origin[0] + size[0] + inflate, origin[1] + size[1] + inflate, origin[2] + size[2] + inflate};
        JsonObject uv = c.has("uv") && c.get("uv").isJsonObject() ? c.getAsJsonObject("uv") : null;
        if (uv == null) return cube;
        for (String face : new String[]{"north", "east", "south", "west", "up", "down"}) {
            if (!uv.has(face)) continue;
            JsonObject f = uv.getAsJsonObject(face);
            float[] start = vec(f.getAsJsonArray("uv"), 0);
            float[] span = f.has("uv_size") ? vec(f.getAsJsonArray("uv_size"), 0) : new float[]{0, 0};
            int shift = (f.has("uv_rotation") ? f.get("uv_rotation").getAsInt() : 0) / 90;
            float[][] raw = corners(min, max, face);
            float[][] ordered = face.equals("up") || face.equals("down")
                    ? new float[][]{raw[3], raw[2], raw[1], raw[0]}
                    : new float[][]{raw[1], raw[0], raw[3], raw[2]};
            float[][] rotated = new float[4][];
            for (int i = 0; i < 4; i++) rotated[i] = ordered[(i + shift) % 4];
            float u0 = start[0] / textureWidth, v0 = start[1] / textureHeight;
            float u1 = (start[0] + span[0]) / textureWidth, v1 = (start[1] + span[1]) / textureHeight;
            float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            cube.faces.add(new Face(rotated, uvs, normal(face)));
        }
        return cube;
    }

    private static float[] normal(String face) {
        return switch (face) {
            case "north" -> new float[]{0, 0, -1};
            case "south" -> new float[]{0, 0, 1};
            case "east" -> new float[]{-1, 0, 0};
            case "west" -> new float[]{1, 0, 0};
            case "up" -> new float[]{0, 1, 0};
            default -> new float[]{0, -1, 0};
        };
    }

    private static float[][] corners(float[] min, float[] max, String face) {
        float x0 = min[0], y0 = min[1], z0 = min[2], x1 = max[0], y1 = max[1], z1 = max[2];
        return switch (face) {
            case "north" -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case "south" -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case "east" -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case "west" -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case "up" -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            default -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    static float[] vec(JsonArray array, float fallback) {
        float[] out = {fallback, fallback, fallback};
        if (array != null) for (int k = 0; k < 3 && k < array.size(); k++) out[k] = array.get(k).getAsFloat();
        return out;
    }

    static Matrix4f pivotRotation(float[] pivot, float[] rotation) {
        Matrix4f m = new Matrix4f().translate(pivot[0], pivot[1], pivot[2]);
        rotateBedrock(m, rotation[0], rotation[1], rotation[2]);
        return m.translate(-pivot[0], -pivot[1], -pivot[2]);
    }

    static void rotateBedrock(Matrix4f m, float rx, float ry, float rz) {
        float d = (float) (Math.PI / 180);
        m.rotateZ(-rz * d).rotateY(ry * d).rotateX(-rx * d);
    }

    public Matrix4f[] pose(RpAnim anim, float t) {
        Matrix4f[] world = new Matrix4f[bones.size()];
        float[] rot = new float[3], pos = new float[3], scl = new float[3];
        for (Bone bone : bones) solve(bone, anim, t, world, rot, pos, scl);
        return world;
    }

    private Matrix4f solve(Bone bone, RpAnim anim, float t, Matrix4f[] world, float[] rot, float[] pos, float[] scl) {
        if (world[bone.index] != null) return world[bone.index];
        Matrix4f parent = bone.parent == null ? new Matrix4f() : solve(bone.parent, anim, t, world, rot, pos, scl);
        float rx = bone.rotation[0], ry = bone.rotation[1], rz = bone.rotation[2];
        float px = 0, py = 0, pz = 0, sx = 1, sy = 1, sz = 1;
        if (anim != null) {
            if (anim.sample(bone.name, RpAnim.ROTATION, t, rot) != null) {
                rx += rot[0];
                ry += rot[1];
                rz += rot[2];
            }
            if (anim.sample(bone.name, RpAnim.POSITION, t, pos) != null) {
                px = pos[0];
                py = pos[1];
                pz = pos[2];
            }
            if (anim.sample(bone.name, RpAnim.SCALE, t, scl) != null) {
                sx = scl[0];
                sy = scl[1];
                sz = scl[2];
            }
        }
        float[] p = bone.pivot;
        Matrix4f local = new Matrix4f().translate(p[0] + px, p[1] + py, p[2] + pz);
        rotateBedrock(local, rx, ry, rz);
        local.scale(sx, sy, sz).translate(-p[0], -p[1], -p[2]);
        Matrix4f m = new Matrix4f(parent).mul(local);
        world[bone.index] = m;
        return m;
    }

    public Matrix4f rest(String name) {
        Bone bone = byName.get(name);
        if (bone == null) return new Matrix4f();
        Matrix4f[] world = pose(null, 0);
        return world[bone.index];
    }

    public static final Matrix4f RAW_TO_MODEL = new Matrix4f().translate(0, 24, 0).scale(1, -1, 1);
    public static final Matrix4f MODEL_TO_RAW = new Matrix4f(RAW_TO_MODEL).invert();

    public void draw(MatrixStack matrices, VertexConsumer buffer, Matrix4f[] world, Matrix4f base, Predicate<Bone> select,
                     int light, int overlay, float alpha) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = new Matrix4f(entry.getPositionMatrix()).scale(1 / 16f).mul(RAW_TO_MODEL);
        if (base != null) position.mul(base);
        Matrix3f normalMatrix = entry.getNormalMatrix();
        Vector3f v = new Vector3f(), n = new Vector3f();
        Matrix4f full = new Matrix4f(), rawOnly = new Matrix4f();
        for (Bone bone : bones) {
            if (bone.cubes.isEmpty() || !select.test(bone)) continue;
            Matrix4f boneWorld = world[bone.index];
            if (degenerate(boneWorld)) continue;
            for (Cube cube : bone.cubes) {
                full.set(position).mul(boneWorld).mul(cube.local);
                rawOnly.set(base == null ? new Matrix4f() : base).mul(boneWorld).mul(cube.local);
                for (Face face : cube.faces) {
                    rawOnly.transformDirection(n.set(face.normal()[0], face.normal()[1], face.normal()[2]));

                    n.set(n.x, -n.y, n.z);
                    if (n.lengthSquared() < 1e-12f) continue;
                    n.normalize();
                    for (int i = 0; i < 4; i++) {
                        float[] c = face.corners()[i];
                        full.transformPosition(v.set(c[0], c[1], c[2]));
                        buffer.vertex(v.x, v.y, v.z, 1, 1, 1, alpha, face.uv()[i][0], face.uv()[i][1], overlay, light,
                                normalMatrix.m00 * n.x + normalMatrix.m10 * n.y + normalMatrix.m20 * n.z,
                                normalMatrix.m01 * n.x + normalMatrix.m11 * n.y + normalMatrix.m21 * n.z,
                                normalMatrix.m02 * n.x + normalMatrix.m12 * n.y + normalMatrix.m22 * n.z);
                    }
                }
            }
        }
    }

    private static boolean degenerate(Matrix4f m) {
        return Math.abs(m.determinant3x3()) < 1e-9f;
    }

    public float[] bounds(Predicate<Bone> select) {
        return boundsOf(pose(null, 0), select);
    }

    public float[] boundsOf(Matrix4f[] world, Predicate<Bone> select) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f v = new Vector3f();
        Matrix4f m = new Matrix4f();
        for (Bone bone : bones) {
            if (bone.cubes.isEmpty() || !select.test(bone) || degenerate(world[bone.index])) continue;
            for (Cube cube : bone.cubes) {
                m.set(world[bone.index]).mul(cube.local);
                for (Face face : cube.faces) {
                    for (float[] c : face.corners()) {
                        m.transformPosition(v.set(c[0], c[1], c[2]));
                        b[0] = Math.min(b[0], v.x);
                        b[1] = Math.min(b[1], v.y);
                        b[2] = Math.min(b[2], v.z);
                        b[3] = Math.max(b[3], v.x);
                        b[4] = Math.max(b[4], v.y);
                        b[5] = Math.max(b[5], v.z);
                    }
                }
            }
        }
        return b;
    }
}
