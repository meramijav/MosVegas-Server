package ru.mosvegas.items.rp;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.resource.ResourceType;

public final class RpClient {
    private RpClient() {}

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new RpScenes());
        ClientPlayNetworking.registerGlobalReceiver(RpServer.PLAY, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            String item = buf.readString();
            String animation = buf.readString();
            int role = buf.readByte();
            int elapsed = buf.readVarInt();
            boolean hold = buf.readBoolean();
            float yaw = buf.readFloat();
            int partner = buf.readVarInt();
            client.execute(() -> RpPlayback.play(id, item, animation, role, elapsed, hold, yaw, partner));
        });
        ClientPlayNetworking.registerGlobalReceiver(RpServer.CLEAR, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            client.execute(() -> RpPlayback.clear(id));
        });
        ClientTickEvents.END_CLIENT_TICK.register(RpPlayback::tick);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) ->
                helper.register(new Props<>(renderer)));
        for (RpItem item : RpItems.all()) {
            BuiltinItemRendererRegistry.INSTANCE.register(item, RpHeld::builtin);
        }
    }

    static final class Props<T extends LivingEntity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {
        Props(FeatureRendererContext<T, M> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, T entity, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            RpPoser.drawProps(entity, matrices, vertices, light);
        }
    }
}
