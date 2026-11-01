package ru.mosvegas.casino.client.dealer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import ru.mosvegas.casino.client.screen.BlackjackScreen;
import ru.mosvegas.casino.client.state.ClientTable;
import ru.mosvegas.casino.client.state.ClientTables;
import ru.mosvegas.casino.dealer.DealerEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GeckoLib dealer with a speech bubble above the name tag. */
public final class DealerRenderer extends GeoEntityRenderer<DealerEntity> {
    private static final int BUBBLE_TICKS = 110;
    private final Map<Integer, int[]> speechSeen = new HashMap<>();
    private final Map<Integer, Float> speechStart = new HashMap<>();

    public DealerRenderer(EntityRendererFactory.Context context) {
        super(context, new DealerGeoModel());
        shadowRadius = 0.45f;
    }

    @Override
    public void render(DealerEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        GeoModel<DealerEntity> model = getGeoModel();
        model.getBone("leftHand").ifPresent(b -> b.setTrackingMatrices(true));
        model.getBone("rightHand").ifPresent(b -> b.setTrackingMatrices(true));
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        // the bones are shared by all dealers: read this one's palms right after it was posed
        Optional<GeoBone> left = model.getBone("leftHand"), right = model.getBone("rightHand");
        if (left.isPresent() && right.isPresent()) {
            Vector3d l = left.get().getWorldPosition(), r = right.get().getWorldPosition();
            DealerHands.update(entity.getId(), new Vec3d(l.x, l.y, l.z), new Vec3d(r.x, r.y, r.z), entity.getWorld().getTime() + tickDelta);
        }
        renderSpeech(entity, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, DealerEntity animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        // the open mouth samples the lips pixel of the skin; darken it into a mouth opening
        if ("mouth".equals(bone.getName())) {
            red *= 0.32f;
            green *= 0.12f;
            blue *= 0.12f;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private void renderSpeech(DealerEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        String text = entity.speech();
        if (text == null || text.isEmpty()) return;
        float now = entity.age + tickDelta;
        int[] seen = speechSeen.get(entity.getId());
        if (seen == null || seen[0] != entity.speechId()) {
            boolean fresh = seen != null || entity.age <= 40;
            speechSeen.put(entity.getId(), new int[]{entity.speechId()});
            speechStart.put(entity.getId(), fresh ? now : now - BUBBLE_TICKS);
            // lips move while the bubble is fresh; a voice line extends this to its real length
            if (fresh) entity.talkUntil(entity.getWorld().getTime() + tickDelta + MathHelper.clamp(text.length() * 1.2f, 20f, 110f));
        }
        float age = now - speechStart.getOrDefault(entity.getId(), now);
        if (age > BUBBLE_TICKS || age < 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.squaredDistanceTo(entity) > 24 * 24) return;
        TextRenderer tr = getTextRenderer();
        List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(text), 150);
        float alpha = age > BUBBLE_TICKS - 15 ? (BUBBLE_TICKS - age) / 15f : Math.min(1f, age / 4f);
        int a = Math.max(8, (int) (alpha * 255));
        int textColor = (a << 24) | 0xFFFFFF;
        int bg = ((int) (alpha * 0.55f * 255) << 24);
        matrices.push();
        float base = entity.getHeight() + (entity.isSitting() ? 0.45f : 0.75f) + (entity.hasCustomName() ? 0.25f : 0f);
        matrices.translate(0, base, 0);
        matrices.multiply(dispatcher.getRotation());
        float scale = 0.02f;
        matrices.scale(-scale, -scale, scale);
        int lineHeight = 10;
        int y = -lines.size() * lineHeight;
        for (OrderedText line : lines) {
            float x = -tr.getWidth(line) / 2f;
            tr.draw(line, x, y, textColor, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, bg, light);
            y += lineHeight;
        }
        matrices.pop();
    }

    @Override
    protected void renderLabelIfPresent(DealerEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // the open table menu names the dealer in its top bar; the floating tag would cover the timer
        if (MinecraftClient.getInstance().currentScreen instanceof BlackjackScreen screen) {
            ClientTable table = ClientTables.get(screen.tablePos());
            if (table != null && table.snapshot() != null && table.snapshot().dealerEntity() == entity.getId()) return;
        }
        if (entity.isSitting()) {
            matrices.push();
            matrices.translate(0, -0.35, 0);
            super.renderLabelIfPresent(entity, text, matrices, vertexConsumers, light);
            matrices.pop();
        } else super.renderLabelIfPresent(entity, text, matrices, vertexConsumers, light);
    }
}
