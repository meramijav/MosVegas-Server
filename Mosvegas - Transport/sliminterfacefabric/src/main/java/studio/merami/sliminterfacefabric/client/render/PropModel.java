package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.rendering.ModelGeometry;
import studio.merami.sliminterfacefabric.SlimVehicle;

public final class PropModel {
    public static final PropModel PAINT_STATION = new PropModel("paint_station");
    public static final PropModel SPRAY_GUN = new PropModel("spray_gun");
    public static final PropModel ZONE_MARKER = new PropModel("zone_marker");
    public static final PropModel PLATE_TERMINAL = new PropModel("plate_terminal");

    private final ResourceId model;
    private final ResourceLocation texture;
    private volatile boolean broken;

    private PropModel(String name) {
        model = SlimCore.id("geo/" + name + ".geo.json");
        texture = SlimVehicle.id("textures/entity/" + name + ".png");
    }

    public void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ModelGeometry geometry = geometry();
        if (geometry == null) return;
        VertexConsumer buffer = buffers.getBuffer(renderType());
        for (ModelGeometry.Bone bone : geometry.roots) drawBone(pose, buffer, bone, light, overlay);
    }

    public RenderType renderType() {
        return RenderType.entityCutoutNoCull(texture);
    }

    private ModelGeometry geometry() {
        if (broken) return null;
        try {
            return ModelGeometry.load(model);
        } catch (RuntimeException e) {
            broken = true;
            SlimCore.LOGGER.error("Cannot draw {}: {}", model, e.getMessage());
            return null;
        }
    }

    public static void reloadAll() {
        for (PropModel model : new PropModel[] {PAINT_STATION, SPRAY_GUN, ZONE_MARKER, PLATE_TERMINAL}) model.broken = false;
    }

    private static void drawBone(PoseStack pose, VertexConsumer buffer, ModelGeometry.Bone bone, int light, int overlay) {
        if (bone.neverRender) return;
        pose.pushPose();
        pose.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
        if (bone.rotZ != 0) pose.mulPose(Axis.ZP.rotation(bone.rotZ));
        if (bone.rotY != 0) pose.mulPose(Axis.YP.rotation(bone.rotY));
        if (bone.rotX != 0) pose.mulPose(Axis.XP.rotation(bone.rotX));
        pose.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
        for (ModelGeometry.Cube cube : bone.cubes) {
            pose.pushPose();
            VehicleRenderer.cubeTransform(pose, cube);
            Matrix3f normalPose = pose.last().normal();
            Matrix4f position = new Matrix4f(pose.last().pose());
            for (ModelGeometry.Quad quad : cube.quads()) {
                if (quad == null) continue;
                Vector3f normal = normalPose.transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
                VehicleRenderer.fixInvertedFlatCube(cube, normal);
                for (ModelGeometry.Vertex vertex : quad.vertices()) {
                    Vector4f at = position.transform(new Vector4f(vertex.x(), vertex.y(), vertex.z(), 1.0F));
                    buffer.vertex(at.x(), at.y(), at.z(), 1, 1, 1, 1, vertex.u(), vertex.v(), overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
            pose.popPose();
        }
        for (ModelGeometry.Bone child : bone.children) drawBone(pose, buffer, child, light, overlay);
        pose.popPose();
    }
}
