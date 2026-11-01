package ru.mosvegas.casino.client.dealer;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.dealer.DealerEntity;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Player-skin compatible dealer: {@code dealer_wide} (Steve arms) / {@code dealer_slim} (Alex arms) use the
 * standard 64x64 skin layout, so any Minecraft skin works. A profile may point to its own geo/animation files.
 */
public final class DealerGeoModel extends GeoModel<DealerEntity> {
    private static final Identifier WIDE = MosVegasCasino.id("geo/entity/dealer_wide.geo.json");
    private static final Identifier SLIM = MosVegasCasino.id("geo/entity/dealer_slim.geo.json");
    private static final Identifier ANIMATIONS = MosVegasCasino.id("animations/entity/dealer.animation.json");

    private static DealerProfiles.Profile profile(DealerEntity e) { return DealerProfiles.get(e.profileId()); }

    @Override
    public Identifier getModelResource(DealerEntity e) {
        DealerProfiles.Profile p = profile(e);
        if (p != null && !p.model().isEmpty()) {
            Identifier custom = Identifier.tryParse(p.model());
            if (custom != null && GeckoLibCache.getBakedModels().containsKey(custom)) return custom;
        }
        return p == null || p.slim() ? SLIM : WIDE;
    }

    @Override
    public Identifier getTextureResource(DealerEntity e) {
        DealerProfiles.Profile p = profile(e);
        if (p == null) return DealerSkins.fallback(true);
        return DealerSkins.texture(p.skin(), p.slim());
    }

    @Override
    public Identifier getAnimationResource(DealerEntity e) {
        DealerProfiles.Profile p = profile(e);
        if (p != null && !p.animations().isEmpty()) {
            Identifier custom = Identifier.tryParse(p.animations());
            if (custom != null && GeckoLibCache.getBakedAnimations().containsKey(custom)) return custom;
        }
        return ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(DealerEntity animatable, long instanceId, AnimationState<DealerEntity> state) {
        float now = animatable.getWorld().getTime() + state.getPartialTick();
        // lips: open and close at a syllable-like rhythm while speaking
        CoreGeoBone mouth = getAnimationProcessor().getBone("mouth");
        if (mouth != null) {
            float open = 0f;
            if (animatable.isTalking(now)) {
                double wave = Math.sin(now * 1.7) * 0.6 + Math.sin(now * 2.9 + 1.3) * 0.4;
                open = (float) MathHelper.clamp(0.35 + wave * 0.75, 0, 1);
            }
            mouth.setScaleX(1f);
            mouth.setScaleZ(1f);
            mouth.setScaleY(open);
        }
        // a blink every ~4 s, out of phase between dealers
        CoreGeoBone lids = getAnimationProcessor().getBone("eyelids");
        if (lids != null) {
            float cycle = (now + Math.floorMod(animatable.getId() * 37, 97)) % 83f;
            lids.setScaleX(1f);
            lids.setScaleZ(1f);
            lids.setScaleY(cycle < 2.5f ? 1f : 0f);
        }
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        float pitch = MathHelper.clamp(data.headPitch(), -25f, 18f);
        float yaw = MathHelper.clamp(data.netHeadYaw(), -70f, 70f);
        head.setRotX(head.getRotX() + pitch * MathHelper.RADIANS_PER_DEGREE);
        head.setRotY(head.getRotY() + yaw * MathHelper.RADIANS_PER_DEGREE);
    }
}
