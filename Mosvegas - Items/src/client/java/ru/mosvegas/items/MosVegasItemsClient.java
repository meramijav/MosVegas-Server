package ru.mosvegas.items;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.model.GeoModel;

public final class MosVegasItemsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MosVegasConsumableItem.rendererFactory = item -> new RenderProvider() {
            private final ConsumableItemRenderer renderer = new ConsumableItemRenderer(new MosVegasItemModel(item.definition()));
            @Override
            public BuiltinModelItemRenderer getCustomRenderer() { return renderer; }
        };
        MosVegasConsumableItem.isBeingUsed = MosVegasItemsClient::isBeingUsed;
        ParticleFactoryRegistry.getInstance().register(MosVegasParticles.CIGARETTE_SMOKE, sprites -> new SmokeParticle.Factory(sprites, false));
        ParticleFactoryRegistry.getInstance().register(MosVegasParticles.EXHALED_SMOKE, sprites -> new SmokeParticle.Factory(sprites, true));
        EntityRendererRegistry.register(MosVegasItems.LITTER, LitterEntityRenderer::new);
        ClientTickEvents.END_WORLD_TICK.register(SmokeEmitter::tick);
        // 3D models for vanilla food; players can switch it off under Resource Packs.
        FabricLoader.getInstance().getModContainer(MosVegasItems.MOD_ID).ifPresent(mod ->
                ResourceManagerHelper.registerBuiltinResourcePack(new Identifier(MosVegasItems.MOD_ID, "food_3d"), mod,
                        Text.translatable("pack.mosvegas_items.food_3d"), ResourcePackActivationType.DEFAULT_ENABLED));
    }

    private static boolean isBeingUsed(ItemStack stack) {
        return activePlayer(stack) != null;
    }

    static AbstractClientPlayerEntity activePlayer(ItemStack stack) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return null;
        var players = world.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            if (player.isUsingItem() && player.getActiveItem() == stack) return player;
        }
        return null;
    }

    private static final class MosVegasItemModel extends GeoModel<MosVegasConsumableItem> {
        // Each renderer belongs to one item; its startup definition never changes.
        private final ConsumableDefinition definition;
        private Identifier model;
        private Identifier texture;
        private Identifier animation;

        private MosVegasItemModel(ConsumableDefinition definition) {
            this.definition = definition;
        }

        @Override
        public Identifier getModelResource(MosVegasConsumableItem item) {
            if (model == null) model = new Identifier(definition.model());
            return model;
        }

        @Override
        public Identifier getTextureResource(MosVegasConsumableItem item) {
            if (texture == null) texture = new Identifier(definition.texture());
            return texture;
        }

        @Override
        public Identifier getAnimationResource(MosVegasConsumableItem item) {
            if (animation == null) animation = new Identifier(definition.animationFile());
            return animation;
        }
    }
}
