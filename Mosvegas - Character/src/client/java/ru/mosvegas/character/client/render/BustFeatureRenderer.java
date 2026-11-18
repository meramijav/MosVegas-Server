package ru.mosvegas.character.client.render;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.RotationAxis;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.data.AppearanceLookup;

public final class BustFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final ModelPart INNER = build(17, 19, Dilation.NONE);
    private static final ModelPart OUTER = build(17, 35, new Dilation(0.25f));

    public BustFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    private static ModelPart build(int u, int v, Dilation dilation) {
        ModelData data = new ModelData();
        data.getRoot().addChild("bust", ModelPartBuilder.create().uv(u, v).cuboid(-4f, 0f, -3f, 8f, 4f, 3f, dilation), ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 64).createModel().getChild("bust");
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float progress, float headYaw, float headPitch) {
        if (player.isInvisible()) return;
        Appearance a = AppearanceLookup.of(player);
        float size = BodyShape.bust(a);
        if (size <= 0f || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) return;
        PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
        matrices.push();
        model.body.rotate(matrices);
        matrices.translate(0f, 2f / 16f, (-2f + 3f) / 16f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(-0.34f * size));
        var buffer = vertices.getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTextures().texture()));
        int overlay = LivingEntityRenderer.getOverlay(player, 0f);
        INNER.render(matrices, buffer, light, overlay);
        if (player.isPartVisible(PlayerModelPart.JACKET)) OUTER.render(matrices, buffer, light, overlay);
        matrices.pop();
    }
}
