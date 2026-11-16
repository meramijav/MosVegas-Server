package ru.mosvegas.items.rp;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;

public final class RpHeld {
    private RpHeld() {}

    public static boolean handles(ItemStack stack) {
        return stack.getItem() instanceof RpItem;
    }

    static RpScenes.Scene scene(ItemStack stack) {
        return stack.getItem() instanceof RpItem item ? RpScenes.get(item.kind.id) : null;
    }

    static Matrix4f[] idlePose(RpScenes.Scene scene) {
        return scene.geo().pose(scene.idle(), 0);
    }

    static boolean inHand(ModelTransformationMode mode) {
        return mode.isFirstPerson() || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
    }

    public static void builtin(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                               VertexConsumerProvider vertices, int light, int overlay) {
        RpScenes.Scene scene = scene(stack);
        if (scene == null) return;
        RpGeo geo = scene.geo();
        Matrix4f[] world = idlePose(scene);
        RpGeo.Bone spin = geo.byName.get("item_spin");
        if (spin == null) return;
        Matrix4f toItem = new Matrix4f().scale(-1, 1, 1)
                .translate(-spin.pivot[0], -spin.pivot[1], -spin.pivot[2])
                .mul(new Matrix4f(world[spin.index]).invert());
        float scale = 1;
        if (!inHand(mode)) {
            float[] b = bounds(geo, world, toItem);
            float size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
            if (size <= 0) return;
            float fill = mode == ModelTransformationMode.GUI ? 0.92f : mode == ModelTransformationMode.FIXED ? 0.8f : 0.55f;
            scale = 16 * fill / size;
            toItem = new Matrix4f().translate(-(b[0] + b[3]) / 2, -(b[1] + b[4]) / 2, -(b[2] + b[5]) / 2).mul(toItem);
        }
        Matrix4f base = new Matrix4f(RpGeo.MODEL_TO_RAW).scale(scale).mul(toItem);
        matrices.push();
        try {
            matrices.translate(0.5f, 0.5f, 0.5f);
            var buffer = vertices.getBuffer(RenderLayer.getEntityTranslucent(scene.texture()));
            geo.draw(matrices, buffer, world, base, RpGeo.Bone::isInHand, light, overlay, 1);
        } finally {
            matrices.pop();
        }
    }

    private static float[] bounds(RpGeo geo, Matrix4f[] world, Matrix4f toItem) {
        Matrix4f[] local = new Matrix4f[world.length];
        for (int i = 0; i < world.length; i++) local[i] = new Matrix4f(toItem).mul(world[i]);
        return geo.boundsOf(local, RpGeo.Bone::isInHand);
    }
}
