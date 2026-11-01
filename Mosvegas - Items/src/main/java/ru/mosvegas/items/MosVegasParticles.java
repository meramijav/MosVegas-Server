package ru.mosvegas.items;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Soft cigarette smoke: a thin thread from the ember and a cloud breathed out of the mouth. */
public final class MosVegasParticles {
    public static final DefaultParticleType CIGARETTE_SMOKE = FabricParticleTypes.simple();
    public static final DefaultParticleType EXHALED_SMOKE = FabricParticleTypes.simple();

    private MosVegasParticles() {}

    static void register() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(MosVegasItems.MOD_ID, "cigarette_smoke"), CIGARETTE_SMOKE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(MosVegasItems.MOD_ID, "exhaled_smoke"), EXHALED_SMOKE);
    }
}
