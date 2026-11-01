package ru.mosvegas.items;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Stack-specific details of the built-in models: burning paper, glowing ember, pull tab. */
public final class ConsumableItemRenderer extends GeoItemRenderer<MosVegasConsumableItem> {
    public static final String CIGARETTE_MODEL = "mosvegas_items:geo/cigarette.geo.json";
    public static final String BEER_MODEL = "mosvegas_items:geo/beer.geo.json";
    /** The pull tab turns around its rivet in the middle of the lid. */
    private static final float TAB_HINGE_Y = 4.128f / 16;
    private CigaretteLook cigarette;
    private float tabDegrees;

    public ConsumableItemRenderer(GeoModel<MosVegasConsumableItem> model) {
        super(model);
    }

    @Override
    public long getInstanceId(MosVegasConsumableItem animatable) {
        // GeckoLib keeps every instance id forever and already separates perspectives.
        // Our looping animations need only idle/use buckets, not one id per stack copy.
        var stack = getCurrentItemStack();
        return stack != null && MosVegasConsumableItem.isBeingUsed.test(stack) ? 1 : 0;
    }

    @Override
    public void actuallyRender(MatrixStack matrices, MosVegasConsumableItem item, BakedGeoModel model,
                               RenderLayer layer, VertexConsumerProvider vertices, VertexConsumer buffer,
                               boolean isReRender, float tickDelta, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        var stack = getCurrentItemStack();
        String geometry = item.definition().model();
        cigarette = geometry.equals(CIGARETTE_MODEL) ? CigaretteLook.of(stack, tickDelta) : null;
        tabDegrees = 0;
        if (geometry.equals(BEER_MODEL)) {
            var player = MosVegasItemsClient.activePlayer(stack);
            boolean opening = player != null && item.isOpening(stack);
            tabDegrees = ConsumptionTimeline.tabDegrees(item.isOpened(stack), opening,
                    player == null ? 0 : player.getItemUseTime() + tickDelta, item.getMaxUseTime(stack));
        }
        super.actuallyRender(matrices, item, model, layer, vertices, buffer, isReRender, tickDelta,
                light, overlay, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(MatrixStack matrices, MosVegasConsumableItem item, GeoBone bone,
                                  RenderLayer layer, VertexConsumerProvider vertices, VertexConsumer buffer,
                                  boolean isReRender, float tickDelta, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        if (tabDegrees != 0 && bone.getName().equals("group")) {
            matrices.push();
            try {
                matrices.translate(0, TAB_HINGE_Y, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-tabDegrees));
                matrices.translate(0, -TAB_HINGE_Y, 0);
                super.renderRecursively(matrices, item, bone, layer, vertices, buffer, isReRender, tickDelta,
                        light, overlay, red, green, blue, alpha);
            } finally {
                matrices.pop();
            }
            return;
        }
        if (cigarette == null || !(bone.getName().equals("paper") || bone.getName().equals("ember"))) {
            super.renderRecursively(matrices, item, bone, layer, vertices, buffer, isReRender, tickDelta,
                    light, overlay, red, green, blue, alpha);
            return;
        }
        boolean ember = bone.getName().equals("ember");
        if (ember && !cigarette.lit()) return;
        float scaleY = bone.getScaleY();
        float posY = bone.getPosY();
        ConsumptionPose.applyCigaretteBurn(bone, cigarette.paper());
        try {
            if (ember) {
                // A glowing coal: full brightness, from dull red to bright orange while drawing.
                float glow = cigarette.glow();
                super.renderRecursively(matrices, item, bone, layer, vertices, buffer, isReRender, tickDelta,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, red * MathHelper.lerp(glow, 0.55f, 1),
                        green * MathHelper.lerp(glow, 0.25f, 1), blue * MathHelper.lerp(glow, 0.2f, 1), alpha);
            } else {
                super.renderRecursively(matrices, item, bone, layer, vertices, buffer, isReRender, tickDelta,
                        light, overlay, red, green, blue, alpha);
            }
        } finally {
            bone.setScaleY(scaleY);
            bone.setPosY(posY);
        }
    }
}
