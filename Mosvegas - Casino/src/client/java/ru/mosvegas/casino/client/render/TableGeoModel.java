package ru.mosvegas.casino.client.render;

import net.minecraft.util.Identifier;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.table.TableSize;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.model.GeoModel;

/**
 * Model/texture per table style: {@code geo/block/blackjack_table_<style>.geo.json},
 * {@code textures/block/table/<style>.png}. Unknown styles fall back to the default model of their size.
 */
public final class TableGeoModel extends GeoModel<BlackjackTableBlockEntity> {
    private static final Identifier ANIMATION = MosVegasCasino.id("animations/block/blackjack_table.animation.json");

    private static String fallbackStyle(BlackjackTableBlockEntity be) {
        TableSize size = be.getCachedState().contains(BlackjackTableBlock.SIZE) ? be.getCachedState().get(BlackjackTableBlock.SIZE) : TableSize.LARGE;
        return size == TableSize.LARGE ? "classic_black" : "free_bet_purple";
    }

    @Override
    public Identifier getModelResource(BlackjackTableBlockEntity be) {
        Identifier id = MosVegasCasino.id("geo/block/blackjack_table_" + be.style() + ".geo.json");
        if (GeckoLibCache.getBakedModels().containsKey(id)) return id;
        return MosVegasCasino.id("geo/block/blackjack_table_" + fallbackStyle(be) + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(BlackjackTableBlockEntity be) {
        Identifier id = MosVegasCasino.id("textures/block/table/" + be.style() + ".png");
        if (CasinoTextures.exists(id)) return id;
        return MosVegasCasino.id("textures/block/table/" + fallbackStyle(be) + ".png");
    }

    @Override
    public Identifier getAnimationResource(BlackjackTableBlockEntity be) { return ANIMATION; }
}
