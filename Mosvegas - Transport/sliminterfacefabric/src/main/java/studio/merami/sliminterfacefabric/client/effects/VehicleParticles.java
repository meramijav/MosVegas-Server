package studio.merami.sliminterfacefabric.client.effects;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.sliminterfacefabric.registry.ParticleRegistry;

public final class VehicleParticles {
    public enum Kind {
        EXHAUST(true, false, 0, .955f),
        TYRE_SMOKE(false, false, 0, .93f),
        DIRT(false, true, .9f, .96f),
        DUST(false, false, 0, .92f),
        SNOW(false, true, .35f, .94f),
        BACKFIRE(true, false, 0, .8f),
        FLAME(false, false, 0, .9f),
        EMBER(false, false, 0, .96f),
        FIRE_SMOKE(true, false, 0, .97f);

        final boolean animated;
        final boolean physics;
        final float gravity;
        final float friction;

        Kind(boolean animated, boolean physics, float gravity, float friction) {
            this.animated = animated;
            this.physics = physics;
            this.gravity = gravity;
            this.friction = friction;
        }

        Kind sheet() {
            return switch (this) {
                case EMBER -> FLAME;
                case FIRE_SMOKE -> EXHAUST;
                default -> this;
            };
        }

        boolean glows() {
            return this == BACKFIRE || this == FLAME || this == EMBER;
        }
    }

    private static final SpriteSet[] SPRITES = new SpriteSet[Kind.values().length];

    private VehicleParticles() {}

    public static void register() {
        register(ParticleRegistry.EXHAUST, Kind.EXHAUST);
        register(ParticleRegistry.TYRE_SMOKE, Kind.TYRE_SMOKE);
        register(ParticleRegistry.WHEEL_DIRT, Kind.DIRT);
        register(ParticleRegistry.WHEEL_DUST, Kind.DUST);
        register(ParticleRegistry.WHEEL_SNOW, Kind.SNOW);
        register(ParticleRegistry.BACKFIRE, Kind.BACKFIRE);
        register(ParticleRegistry.FLAME, Kind.FLAME);
    }

    private static void register(SimpleParticleType type, Kind kind) {
        ParticleFactoryRegistry.getInstance().register(type, set -> {
            SPRITES[kind.ordinal()] = set;
            return (options, level, x, y, z, dx, dy, dz) -> new Puff(level, kind, set, x, y, z, new Vec3(dx, dy, dz), 20, .3f, .5f, .6f, 1f);
        });
    }

    public static void spawn(Kind kind, Point3D at, Point3D velocity, int lifetime, float size0, float size1, float alpha, float shade) {
        Minecraft mc = Minecraft.getInstance();
        SpriteSet set = SPRITES[kind.sheet().ordinal()];
        if (mc.level == null || set == null) return;
        mc.particleEngine.add(new Puff(mc.level, kind, set, at.x, at.y, at.z, new Vec3(velocity.x, velocity.y, velocity.z), lifetime, size0, size1, alpha, shade));
    }

    static final class Puff extends TextureSheetParticle {
        private final Kind kind;
        private final SpriteSet set;
        private final float size0;
        private final float size1;
        private final float startAlpha;
        private final float shade;
        private final int frame;
        private final float phase;

        Puff(ClientLevel level, Kind kind, SpriteSet set, double x, double y, double z, Vec3 velocity, int lifetime, float size0, float size1, float alpha, float shade) {
            super(level, x, y, z);
            this.kind = kind;
            this.set = set;
            this.size0 = size0;
            this.size1 = size1;
            this.startAlpha = alpha;
            this.shade = shade;
            xd = velocity.x;
            yd = velocity.y;
            zd = velocity.z;
            this.lifetime = Math.max(2, lifetime);
            gravity = kind.gravity;
            friction = kind.friction;
            hasPhysics = kind.physics;
            rCol = gCol = bCol = Math.min(1, shade);
            this.alpha = alpha;
            roll = oRoll = kind == Kind.FLAME ? (random.nextFloat() - .5f) * .25f : random.nextFloat() * Mth.TWO_PI;
            frame = random.nextInt(7);
            phase = random.nextFloat() * Mth.TWO_PI;
            if (kind.animated) setSpriteFromAge(set);
            else if (kind == Kind.FLAME || kind == Kind.EMBER) flicker();
            else pickSprite(set);
            quadSize = size0;
            if (kind == Kind.FLAME || kind == Kind.EMBER) tint(0);
        }

        private void flicker() {
            setSprite(set.get((frame + age / 2) % 7, 6));
        }

        private void tint(float life) {
            if (kind == Kind.EMBER) {
                rCol = Math.min(1, shade);
                gCol = Math.min(1, shade * Mth.lerp(life, .62f, .25f));
                bCol = Math.min(1, shade * Mth.lerp(life, .22f, .05f));
                return;
            }
            float r = 1;
            float g = life < .35f ? Mth.lerp(life / .35f, .95f, .68f) : Mth.lerp((life - .35f) / .65f, .68f, .30f);
            float b = life < .35f ? Mth.lerp(life / .35f, .62f, .26f) : Mth.lerp((life - .35f) / .65f, .26f, .10f);
            rCol = Math.min(1, r * shade);
            gCol = Math.min(1, g * shade);
            bCol = Math.min(1, b * shade);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            if (kind.animated) setSpriteFromAge(set);
            float life = (float) age / lifetime;
            switch (kind) {
                case FLAME -> {
                    flicker();
                    tint(life);
                    yd += .0035;
                    xd += (random.nextFloat() - .5f) * .004f;
                    zd += (random.nextFloat() - .5f) * .004f;
                    alpha = startAlpha * Mth.clamp((1 - life) / .3f, 0, 1);
                }
                case EMBER -> {
                    flicker();
                    tint(life);
                    yd += .0015;
                    xd += Mth.sin(phase + age * .45f) * .0025f;
                    zd += Mth.cos(phase + age * .37f) * .0025f;
                    alpha = startAlpha * Mth.clamp((1 - life) / .4f, 0, 1) * (random.nextFloat() < .15f ? .35f : 1);
                }
                case FIRE_SMOKE -> {
                    oRoll = roll;
                    roll += .008f;
                    yd += .0009;
                    alpha = startAlpha * Mth.clamp(life / .08f, 0, 1) * Mth.clamp((1 - life) / .55f, 0, 1);
                }
                default -> {
                    alpha = startAlpha * Mth.clamp((1 - life) / .45f, 0, 1);
                    if (kind == Kind.EXHAUST || kind == Kind.TYRE_SMOKE || kind == Kind.DUST) {
                        oRoll = roll;
                        roll += .012f;
                        yd += .0012;
                    }
                }
            }
        }

        @Override
        public float getQuadSize(float partial) {
            float t = Mth.clamp((age + partial) / lifetime, 0, 1);
            if (kind == Kind.FLAME) return t < .25f ? Mth.lerp(t / .25f, size0, size1) : Mth.lerp((t - .25f) / .75f, size1, size1 * .3f);
            return Mth.lerp(1 - (1 - t) * (1 - t), size0, size1);
        }

        @Override
        public int getLightColor(float partial) {
            return kind.glows() ? 0xf000f0 : super.getLightColor(partial);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
