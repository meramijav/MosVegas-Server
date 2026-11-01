package ru.mosvegas.items;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/** Translucent grey smoke that rises, spreads and fades instead of vanilla's dark puffs. */
public final class SmokeParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final boolean cloud;
    private final float startScale, growth, startAlpha;

    private SmokeParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                          SpriteProvider sprites, boolean exhaled) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.cloud = exhaled;
        velocityX = vx;
        velocityY = vy;
        velocityZ = vz;
        if (exhaled) {
            // A breath: pushed out of the mouth, slows down and billows.
            maxAge = 40 + random.nextInt(20);
            growth = 3.6f;
            velocityMultiplier = 0.88f;
            gravityStrength = -0.02f;
            // Right in front of the smoker's own eyes a full cloud would cover the view.
            Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
            float near = !camera.isThirdPerson() && camera.getPos().squaredDistanceTo(x, y, z) < 0.36 ? 0.5f : 1;
            startScale = (0.11f + random.nextFloat() * 0.07f) * near;
            startAlpha = 0.62f * near;
        } else {
            // A thin thread from the ember.
            maxAge = 28 + random.nextInt(18);
            startScale = 0.02f + random.nextFloat() * 0.012f;
            growth = 2.4f;
            startAlpha = 0.36f;
            velocityMultiplier = 0.96f;
            gravityStrength = -0.03f;
        }
        float grey = 0.72f + random.nextFloat() * 0.12f;
        setColor(grey, grey, grey);
        scale = startScale;
        alpha = 0;
        collidesWithWorld = false;
        // A breath keeps one round cloud sprite; the thin thread thins out with age.
        if (cloud) setSprite(sprites);
        else setSpriteForAge(sprites);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (dead) return;
        if (!cloud) setSpriteForAge(sprites);
        float life = age / (float) maxAge;
        scale = startScale * MathHelper.lerp(life, 1, growth);
        alpha = startAlpha * Math.min(1, age / 3f) * (float) Math.pow(1 - life, 1.4);
        // Gentle sideways drift of rising smoke.
        velocityX += (random.nextFloat() - 0.5f) * 0.0016f;
        velocityZ += (random.nextFloat() - 0.5f) * 0.0016f;
    }

    public record Factory(SpriteProvider sprites, boolean exhaled) implements ParticleFactory<DefaultParticleType> {
        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SmokeParticle(world, x, y, z, vx, vy, vz, sprites, exhaled);
        }
    }
}
