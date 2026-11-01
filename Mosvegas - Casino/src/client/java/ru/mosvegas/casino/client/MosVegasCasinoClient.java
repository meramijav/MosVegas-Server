package ru.mosvegas.casino.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.client.dealer.DealerRenderer;
import ru.mosvegas.casino.client.dealer.DealerSkins;
import ru.mosvegas.casino.client.render.CasinoTextures;
import ru.mosvegas.casino.client.render.TableRenderer;
import ru.mosvegas.casino.registry.CasinoRegistry;

public final class MosVegasCasinoClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(CasinoRegistry.BLACKJACK_TABLE_ENTITY, TableRenderer::new);
        EntityRendererRegistry.register(CasinoRegistry.DEALER, DealerRenderer::new);
        EntityRendererRegistry.register(CasinoRegistry.SEAT, EmptyEntityRenderer::new);
        ClientNet.register();
        ClientSetup.register();
        ru.mosvegas.casino.client.dev.AutoTest.register();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return MosVegasCasino.id("texture_cache"); }

            @Override
            public void reload(ResourceManager manager) {
                CasinoTextures.clearCache();
                DealerSkins.clear();
            }
        });
    }
}
