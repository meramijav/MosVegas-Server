package studio.merami.sliminterfacefabric.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

public final class EntityRegistry {
    public static final EntityType<BuilderVehicle> VEHICLE = Registry.register(BuiltInRegistries.ENTITY_TYPE, SlimVehicle.id("vehicle"),
            FabricEntityTypeBuilder.<BuilderVehicle>create(MobCategory.MISC, BuilderVehicle::new)
                    .dimensions(EntityDimensions.fixed(2.0f, 1.82f)).trackRangeBlocks(160).trackedUpdateRate(2).build());

    private EntityRegistry() {}

    public static void register() {}
}
