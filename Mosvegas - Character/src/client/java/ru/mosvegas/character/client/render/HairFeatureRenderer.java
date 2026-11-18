package ru.mosvegas.character.client.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.data.AppearanceLookup;

public final class HairFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public HairFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float progress, float headYaw, float headPitch) {
        if (player.isInvisible() || !getContextModel().head.visible) return;
        Appearance a = AppearanceLookup.of(player);
        if (a == null) return;
        int overlay = LivingEntityRenderer.getOverlay(player, 0f);
        String beard = HairModels.beardId(a);
        if (beard != null) {
            GeoMesh mesh = HairModels.mesh(beard);
            Identifier tex = mesh == null ? null : HairModels.texture(beard, a.hairColor);
            if (tex != null) {
                matrices.push();
                getContextModel().head.rotate(matrices);
                mesh.render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(tex)), light, overlay);
                matrices.pop();
            }
        }
        if (player.getEquippedStack(EquipmentSlot.HEAD).getItem() instanceof ArmorItem) return;
        String id = HairModels.hairId(a);
        GeoMesh mesh = HairModels.mesh(id);
        GeoMesh body = HairModels.bodyMesh(id);
        if (mesh == null && body == null) return;
        Identifier tex = HairModels.texture(id, a.hairColor);
        if (tex == null) return;
        VertexConsumer vc = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
        if (mesh != null) {
            matrices.push();
            getContextModel().head.rotate(matrices);
            mesh.render(matrices, vc, light, overlay);
            matrices.pop();
        }
        if (body != null && getContextModel().body.visible) {
            matrices.push();
            getContextModel().body.rotate(matrices);
            body.render(matrices, vc, light, overlay);
            matrices.pop();
        }
    }
}
