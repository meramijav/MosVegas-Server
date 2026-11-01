package ru.mosvegas.items;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Draws the thrown can or butt with the same model and size as in the hand. */
public final class LitterEntityRenderer extends EntityRenderer<LitterEntity> {
    private static final ModelTransformationMode MODE = ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;

    public LitterEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 0.08f;
    }

    @Override
    public void render(LitterEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light) {
        var stack = entity.getStack();
        if (!(stack.getItem() instanceof MosVegasConsumableItem)) return;
        var items = MinecraftClient.getInstance().getItemRenderer();
        boolean butt = entity.isButt();
        float scale = items.getModel(stack, entity.getWorld(), null, entity.getId()).getTransformation().getTransformation(MODE).scale.y();
        float age = entity.age + tickDelta;
        matrices.push();
        try {
            // Lies on its side, resting on the half-width of the can or filter.
            matrices.translate(0, (butt ? 0.5f : 1.92f) * scale / 16, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.isOnGround() ? 90 : age * (butt ? 40 : 25)));
            float glow = butt ? 0.6f * MathHelper.clamp(1 - age / LitterEntity.SMOULDER, 0, 1) : 0;
            CigaretteLook.withLitterGlow(glow, () -> items.renderItem(stack, MODE, light, OverlayTexture.DEFAULT_UV,
                    matrices, vertices, entity.getWorld(), entity.getId()));
            if (glow > 0) SmokeEmitter.record(entity, matrices.peek(),
                    HeldConsumableRenderer.scaled(new Vector3f(0, ConsumptionPose.emberTop(CigaretteLook.BUTT_PAPER), 0), scale), false, glow);
        } finally {
            matrices.pop();
        }
        super.render(entity, yaw, tickDelta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(LitterEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
