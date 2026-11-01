package studio.merami.sliminterfacefabric.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import studio.merami.sliminterfacefabric.SlimVehicle;

public final class ParticleRegistry {
    public static final SimpleParticleType EXHAUST = particle("exhaust");
    public static final SimpleParticleType TYRE_SMOKE = particle("tyre_smoke");
    public static final SimpleParticleType WHEEL_DIRT = particle("wheel_dirt");
    public static final SimpleParticleType WHEEL_DUST = particle("wheel_dust");
    public static final SimpleParticleType WHEEL_SNOW = particle("wheel_snow");
    public static final SimpleParticleType BACKFIRE = particle("backfire");
    public static final SimpleParticleType FLAME = particle("flame");

    private ParticleRegistry() {}

    public static void register() {}

    private static SimpleParticleType particle(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, SlimVehicle.id(name), FabricParticleTypes.simple());
    }
}
