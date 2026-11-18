package ru.mosvegas.character.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class GeoMesh {
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private final List<Quad> quads;
    public final int texW, texH;

    private GeoMesh(List<Quad> quads, int texW, int texH) {
        this.quads = quads;
        this.texW = texW;
        this.texH = texH;
    }

    private record Quad(float[] pos, float[] uv, float nx, float ny, float nz) {}

    public static GeoMesh load(Reader reader) {
        return load(JsonParser.parseReader(reader).getAsJsonObject(), name -> true);
    }

    public static GeoMesh load(JsonObject root, Predicate<String> bones) {
        JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = geo.getAsJsonObject("description");
        int tw = desc.get("texture_width").getAsInt(), th = desc.get("texture_height").getAsInt();
        List<Quad> quads = new ArrayList<>();
        for (JsonElement b : geo.getAsJsonArray("bones")) {
            JsonObject bone = b.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (name.endsWith("_ref") || !bone.has("cubes") || !bones.test(name)) continue;
            for (JsonElement c : bone.getAsJsonArray("cubes")) addCube(quads, c.getAsJsonObject());
        }
        return quads.isEmpty() ? null : new GeoMesh(quads, tw, th);
    }

    private static float[] vec(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static void addCube(List<Quad> out, JsonObject cube) {
        float[] o = vec(cube, "origin"), s = vec(cube, "size");
        float inf = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
        float fx = -(o[0] + s[0]) - inf, tx = -o[0] + inf;
        float fy = o[1] - inf, ty = o[1] + s[1] + inf;
        float fz = o[2] - inf, tz = o[2] + s[2] + inf;
        JsonObject uv = cube.getAsJsonObject("uv");
        Matrix3f rot = null;
        float[] piv = null;
        if (cube.has("rotation")) {
            float[] r = vec(cube, "rotation");
            float[] pv = cube.has("pivot") ? vec(cube, "pivot") : new float[3];
            rot = new Matrix3f().rotateZ((float) Math.toRadians(r[2])).rotateY((float) Math.toRadians(-r[1])).rotateX((float) Math.toRadians(-r[0]));
            piv = new float[]{-pv[0], pv[1], pv[2]};
        }
        for (String face : FACES) {
            if (uv == null || !uv.has(face)) continue;
            JsonObject f = uv.getAsJsonObject(face);
            float[] u = {f.getAsJsonArray("uv").get(0).getAsFloat(), f.getAsJsonArray("uv").get(1).getAsFloat()};
            float[] us = {f.getAsJsonArray("uv_size").get(0).getAsFloat(), f.getAsJsonArray("uv_size").get(1).getAsFloat()};
            float u0 = u[0], v0 = u[1], u1 = u[0] + us[0], v1 = u[1] + us[1];
            float[][] p = switch (face) {
                case "north" -> new float[][]{{tx, ty, fz}, {fx, ty, fz}, {fx, fy, fz}, {tx, fy, fz}};
                case "south" -> new float[][]{{fx, ty, tz}, {tx, ty, tz}, {tx, fy, tz}, {fx, fy, tz}};
                case "east" -> new float[][]{{tx, ty, tz}, {tx, ty, fz}, {tx, fy, fz}, {tx, fy, tz}};
                case "west" -> new float[][]{{fx, ty, fz}, {fx, ty, tz}, {fx, fy, tz}, {fx, fy, fz}};
                case "up" -> new float[][]{{fx, ty, fz}, {tx, ty, fz}, {tx, ty, tz}, {fx, ty, tz}};
                default -> new float[][]{{fx, fy, tz}, {tx, fy, tz}, {tx, fy, fz}, {fx, fy, fz}};
            };
            float[] n = switch (face) {
                case "north" -> new float[]{0, 0, -1};
                case "south" -> new float[]{0, 0, 1};
                case "east" -> new float[]{1, 0, 0};
                case "west" -> new float[]{-1, 0, 0};
                case "up" -> new float[]{0, 1, 0};
                default -> new float[]{0, -1, 0};
            };
            if (rot != null) {
                for (int i = 0; i < 4; i++) {
                    Vector3f v = new Vector3f(p[i][0] - piv[0], p[i][1] - piv[1], p[i][2] - piv[2]);
                    rot.transform(v);
                    p[i] = new float[]{v.x + piv[0], v.y + piv[1], v.z + piv[2]};
                }
                Vector3f nv = rot.transform(new Vector3f(n[0], n[1], n[2]));
                n = new float[]{nv.x, nv.y, nv.z};
            }
            float[] pos = new float[12];
            for (int i = 0; i < 4; i++) {
                pos[i * 3] = -p[i][0];
                pos[i * 3 + 1] = -p[i][1];
                pos[i * 3 + 2] = p[i][2];
            }
            float[] uvs = {u0, v0, u1, v0, u1, v1, u0, v1};
            out.add(new Quad(pos, uvs, -n[0], -n[1], n[2]));
        }
    }

    public void render(MatrixStack matrices, VertexConsumer vc, int light, int overlay) {
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        Matrix3f nm = e.getNormalMatrix();
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, q.pos[i * 3] / 16f, q.pos[i * 3 + 1] / 16f, q.pos[i * 3 + 2] / 16f)
                        .color(1f, 1f, 1f, 1f)
                        .texture(q.uv[i * 2] / texW, q.uv[i * 2 + 1] / texH)
                        .overlay(overlay)
                        .light(light)
                        .normal(nm, q.nx, q.ny, q.nz)
                        .next();
            }
        }
    }
}
