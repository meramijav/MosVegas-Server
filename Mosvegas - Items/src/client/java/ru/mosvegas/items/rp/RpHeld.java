package ru.mosvegas.items.rp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.mosvegas.items.HeldConsumableRenderer;
import ru.mosvegas.items.Pose;

public final class RpHeld {
    private RpHeld() {}

    public static boolean handles(ItemStack stack) {
        return stack.getItem() instanceof RpItem;
    }

    public static boolean heldInView(ItemStack stack) {
        return stack.getItem() instanceof RpItem item && item.kind.action == RpKind.Action.SHOW;
    }

    public static void firstPersonArm(AbstractClientPlayerEntity player, ItemStack stack, Arm arm, float equip, float swing,
                                      MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        if (player.isInvisible()) return;
        RpScenes.Scene scene = scene(stack);
        if (scene == null) return;
        int side = arm == Arm.RIGHT ? 1 : -1;
        Vector3f grip = bottomEdge(player, stack, scene, side, equip);
        if (grip == null) return;
        grip.add(0, 0, GRIP_BACK);
        Pose rig = HeldConsumableRenderer.firstPersonRig(side, 0, swing);
        Vector3f hand = new Vector3f(-side * (HeldConsumableRenderer.isSlim(player) ? 0.5f : 1f), 10.5f, -1f).div(16);
        Pose pose = HeldConsumableRenderer.firstPersonArm(rig.transform(HeldConsumableRenderer.shoulder(side)), grip,
                new Vector3f(0, 0.3f, -1), hand);
        HeldConsumableRenderer.renderArm(player, arm, pose, matrices, vertices, light);
    }

    private static final float GRIP_DEPTH = 0.35f, GRIP_MIN = 0.03f, GRIP_MAX = 0.1f;
    private static final float GRIP_BACK = -0.02f;

    private static Vector3f bottomEdge(AbstractClientPlayerEntity player, ItemStack stack, RpScenes.Scene scene, int side, float equip) {
        ModelTransformationMode mode = side == 1 ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        RpGeo geo = scene.geo();
        RpGeo.Bone spin = geo.byName.get("item_spin");
        if (spin == null) return null;
        Matrix4f[] world = idlePose(scene);
        Matrix4f toItem = new Matrix4f().scale(-1, 1, 1)
                .translate(-spin.pivot[0], -spin.pivot[1], -spin.pivot[2])
                .mul(new Matrix4f(world[spin.index]).invert());
        float[] b = bounds(geo, world, toItem);
        if (b[0] > b[3]) return null;
        MatrixStack view = new MatrixStack();
        view.translate(side * 0.56f, -0.52f - 0.6f * equip, -0.72f);
        MinecraftClient.getInstance().getItemRenderer().getModel(stack, player.getWorld(), player, player.getId())
                .getTransformation().getTransformation(mode).apply(side == -1, view);
        Matrix4f toView = new Matrix4f(view.peek().getPositionMatrix()).scale(1 / 16f);
        Vector3f low = null, sum = new Vector3f();
        float top = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3f c = toView.transformPosition(new Vector3f(b[(i & 1) * 3], b[1 + (i >> 1 & 1) * 3], b[2 + (i >> 2 & 1) * 3]));
            sum.add(c);
            if (low == null || c.y < low.y) low = c;
            top = Math.max(top, c.y);
        }
        sum.div(8);
        float lift = Math.max(GRIP_MIN, Math.min(GRIP_MAX, (top - low.y) * GRIP_DEPTH));
        return new Vector3f(sum.x, low.y + lift, sum.z);
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
