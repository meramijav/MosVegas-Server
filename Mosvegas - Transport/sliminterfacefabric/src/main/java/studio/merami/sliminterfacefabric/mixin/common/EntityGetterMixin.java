package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

import java.util.ArrayList;
import java.util.List;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin {
    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    default void slimvehicle$vehicleContours(Entity entity, AABB query, CallbackInfoReturnable<List<VoxelShape>> cir) {
        EntityGetter level = (EntityGetter) this;
        List<BuilderVehicle> vehicles = level.getEntitiesOfClass(BuilderVehicle.class, query.inflate(1e-7),
                vehicle -> vehicle != entity && (entity == null || !vehicle.isPassengerOfSameVehicle(entity)));
        if (vehicles.isEmpty()) return;
        List<VoxelShape> result = new ArrayList<>();
        BoundingBox area = Convert.box(query);
        List<BoundingBox> slices = new ArrayList<>();
        for (Entity other : level.getEntities(entity, query.inflate(1e-7), e -> !e.isSpectator()
                && (entity == null ? e.canBeCollidedWith() : entity.canCollideWith(e)))) {
            if (other instanceof BuilderVehicle vehicle) {
                if (BuilderVehicle.ignoringVehicles()) continue;
                slices.clear();
                vehicle.vehicle().collisionBoxes(WrapperEntity.of(entity), area, slices);
                for (BoundingBox slice : slices) result.add(Shapes.create(Convert.aabb(slice)));
            } else {
                result.add(Shapes.create(other.getBoundingBox()));
            }
        }
        cir.setReturnValue(result);
    }
}
