package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void slimvehicle$pick(Entity source, Vec3 start, Vec3 end, AABB query, Predicate<Entity> predicate, double distance, CallbackInfoReturnable<EntityHitResult> cir) {
        if (!source.level().getEntitiesOfClass(BuilderVehicle.class, query).isEmpty()) {
            cir.setReturnValue(slimvehicle$trace(source.level(), source, start, end, query, predicate, distance, 0, true));
        }
    }

    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void slimvehicle$projectile(Level level, Entity source, Vec3 start, Vec3 end, AABB query, Predicate<Entity> predicate, float margin, CallbackInfoReturnable<EntityHitResult> cir) {
        if (!level.getEntitiesOfClass(BuilderVehicle.class, query).isEmpty()) {
            cir.setReturnValue(slimvehicle$trace(level, source, start, end, query, predicate, Double.MAX_VALUE, margin, false));
        }
    }

    @Unique
    private static EntityHitResult slimvehicle$trace(Level level, Entity source, Vec3 start, Vec3 end, AABB query, Predicate<Entity> predicate, double nearest, float margin, boolean picking) {
        EntityHitResult result = null;
        Point3D from = Convert.point(start);
        Point3D to = Convert.point(end);
        for (Entity entity : level.getEntities(source, query, predicate)) {
            if (picking && entity.isPassengerOfSameVehicle(source)) continue;
            Optional<Vec3> point;
            if (entity instanceof BuilderVehicle builder) {
                EntityVehicleF_Physics vehicle = builder.vehicle();
                Optional<Point3D> hit = picking ? vehicle.targetAlong(from, to).map(InteractionZone.Hit::point).or(() -> vehicle.hull().ray(from, to).map(Hull.Hit::point))
                        : vehicle.hull().ray(from, to).map(Hull.Hit::point);
                point = hit.map(Convert::vec);
            } else {
                point = entity.getBoundingBox().inflate(picking ? entity.getPickRadius() : margin).clip(start, end);
            }
            if (point.isPresent() && start.distanceToSqr(point.get()) < nearest) {
                nearest = start.distanceToSqr(point.get());
                result = new EntityHitResult(entity, point.get());
            }
        }
        return result;
    }
}
