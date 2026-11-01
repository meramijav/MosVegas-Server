package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.rendering.ModelGeometry;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class DentMesh {
    private static final double CELL = .11;
    private static final int MAX_CUTS = 14;
    private static final Patch PLAIN = new Patch(0, 0, null, null, null, null);

    private record Patch(int cols, int rows, float[] position, float[] uv, float[] shade, float[] normal) {}

    private final List<DamageModel.Dent> dents;
    private final Map<ModelGeometry.Quad, Patch> patches = new IdentityHashMap<>();
    private final Map<ModelGeometry.Cube, Matrix4f> rotations = new IdentityHashMap<>();

    DentMesh(List<DamageModel.Dent> dents) {
        this.dents = dents;
    }

    boolean current(List<DamageModel.Dent> dents) {
        return this.dents == dents;
    }

    private static Point3D chassis(float x, float y, float z) {
        return new Point3D(-x, y, -z);
    }

    void renderCube(PoseStack pose, ModelGeometry.Cube cube, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
        Matrix4f bone = pose.last().pose();
        Matrix3f boneNormal = pose.last().normal();
        Matrix4f rotation = rotations.computeIfAbsent(cube, DentMesh::cubeRotation);
        Matrix4f full = new Matrix4f(bone).mul(rotation);
        Matrix3f fullNormal = new Matrix3f(boneNormal).mul(new Matrix3f(rotation));
        Vector4f p = new Vector4f();
        Vector3f n = new Vector3f();
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            Patch patch = patches.computeIfAbsent(quad, q -> build(cube, q, rotation));
            if (patch == PLAIN) {
                fullNormal.transform(n.set(quad.nx(), quad.ny(), quad.nz()));
                VehicleRenderer.fixInvertedFlatCube(cube, n);
                for (ModelGeometry.Vertex v : quad.vertices()) {
                    full.transform(p.set(v.x(), v.y(), v.z(), 1));
                    buffer.vertex(p.x(), p.y(), p.z(), red, green, blue, alpha, v.u(), v.v(), overlay, light, n.x(), n.y(), n.z());
                }
                continue;
            }
            int stride = patch.cols + 1;
            for (int row = 0; row < patch.rows; row++) {
                for (int col = 0; col < patch.cols; col++) {
                    int cell = (row * patch.cols + col) * 3;
                    boneNormal.transform(n.set(patch.normal[cell], patch.normal[cell + 1], patch.normal[cell + 2]));
                    for (int corner = 0; corner < 4; corner++) {
                        int i = (row + (corner >= 2 ? 1 : 0)) * stride + col + (corner == 1 || corner == 2 ? 1 : 0);
                        bone.transform(p.set(patch.position[i * 3], patch.position[i * 3 + 1], patch.position[i * 3 + 2], 1));
                        float shade = patch.shade[i];
                        buffer.vertex(p.x(), p.y(), p.z(), red * shade, green * shade, blue * shade, alpha, patch.uv[i * 2], patch.uv[i * 2 + 1], overlay, light, n.x(), n.y(), n.z());
                    }
                }
            }
        }
    }

    private static Matrix4f cubeRotation(ModelGeometry.Cube cube) {
        PoseStack local = new PoseStack();
        VehicleRenderer.cubeTransform(local, cube);
        return new Matrix4f(local.last().pose());
    }

    private Patch build(ModelGeometry.Cube cube, ModelGeometry.Quad quad, Matrix4f rotation) {
        ModelGeometry.Vertex[] v = quad.vertices();
        if (v.length != 4) return PLAIN;
        Vector3f[] corner = new Vector3f[4];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            corner[i] = rotation.transformPosition(new Vector3f(v[i].x(), v[i].y(), v[i].z()));
            minX = Math.min(minX, corner[i].x);
            minY = Math.min(minY, corner[i].y);
            minZ = Math.min(minZ, corner[i].z);
            maxX = Math.max(maxX, corner[i].x);
            maxY = Math.max(maxY, corner[i].y);
            maxZ = Math.max(maxZ, corner[i].z);
        }
        if (!reached(minX, minY, minZ, maxX, maxY, maxZ)) return PLAIN;
        int cols = cuts(v[0], v[1]);
        int rows = cuts(v[0], v[3]);
        int stride = cols + 1;
        int points = stride * (rows + 1);
        float[] position = new float[points * 3];
        float[] uv = new float[points * 2];
        float[] shade = new float[points];
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f at = new Vector3f();
        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col <= cols; col++) {
                float s = col / (float) cols;
                float t = row / (float) rows;
                corner[0].lerp(corner[1], s, a);
                corner[3].lerp(corner[2], s, b);
                a.lerp(b, t, at);
                int i = row * stride + col;
                Point3D push = DamageModel.displacement(dents, chassis(at.x, at.y, at.z));
                position[i * 3] = at.x - (float) push.x;
                position[i * 3 + 1] = at.y + (float) push.y;
                position[i * 3 + 2] = at.z - (float) push.z;
                float u0 = Mth.lerp(s, v[0].u(), v[1].u());
                float u1 = Mth.lerp(s, v[3].u(), v[2].u());
                float w0 = Mth.lerp(s, v[0].v(), v[1].v());
                float w1 = Mth.lerp(s, v[3].v(), v[2].v());
                uv[i * 2] = Mth.lerp(t, u0, u1);
                uv[i * 2 + 1] = Mth.lerp(t, w0, w1);
                shade[i] = 1 - .38f * Mth.clamp((float) push.length() / .2f, 0, 1);
            }
        }
        Vector3f flat = new Matrix3f(rotation).transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
        VehicleRenderer.fixInvertedFlatCube(cube, flat);
        float[] normal = new float[cols * rows * 3];
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p3 = new Vector3f();
        Vector3f e1 = new Vector3f();
        Vector3f e2 = new Vector3f();
        Vector3f cross = new Vector3f();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = row * stride + col;
                p0.set(position[i * 3], position[i * 3 + 1], position[i * 3 + 2]);
                p1.set(position[(i + 1) * 3], position[(i + 1) * 3 + 1], position[(i + 1) * 3 + 2]);
                p3.set(position[(i + stride) * 3], position[(i + stride) * 3 + 1], position[(i + stride) * 3 + 2]);
                p1.sub(p0, e1);
                p3.sub(p0, e2);
                e1.cross(e2, cross);
                if (cross.lengthSquared() < 1e-12) cross.set(flat);
                else cross.normalize();
                if (cross.dot(flat) < 0) cross.negate();
                int k = (row * cols + col) * 3;
                normal[k] = cross.x;
                normal[k + 1] = cross.y;
                normal[k + 2] = cross.z;
            }
        }
        return new Patch(cols, rows, position, uv, shade, normal);
    }

    private static int cuts(ModelGeometry.Vertex from, ModelGeometry.Vertex to) {
        return Mth.clamp((int) Math.ceil(new Vector3f(from.x(), from.y(), from.z()).distance(to.x(), to.y(), to.z()) / CELL), 1, MAX_CUTS);
    }

    private boolean reached(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (DamageModel.Dent dent : dents) {
            double x = Mth.clamp(dent.pos().x, -maxX, -minX);
            double y = Mth.clamp(dent.pos().y, minY, maxY);
            double z = Mth.clamp(dent.pos().z, -maxZ, -minZ);
            if (dent.pos().distanceToSqr(x, y, z) < dent.radius() * dent.radius()) return true;
        }
        return false;
    }
}
