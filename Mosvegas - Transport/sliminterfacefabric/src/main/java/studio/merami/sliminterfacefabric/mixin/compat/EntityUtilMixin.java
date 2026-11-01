package studio.merami.sliminterfacefabric.mixin.compat;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.List;

@Pseudo
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public class EntityUtilMixin {
    @Inject(method = "findEntityOnPath", at = @At("RETURN"), cancellable = true, remap = false)
    private static void slimvehicle$ownCabin(Projectile bullet, Vec3 start, Vec3 end, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        EntityKineticBullet.EntityResult cabin = slimvehicle$cabin(bullet, start, end);
        EntityKineticBullet.EntityResult found = cir.getReturnValue();
        if (cabin != null && (found == null || start.distanceToSqr(cabin.getHitPos()) < start.distanceToSqr(found.getHitPos()))) cir.setReturnValue(cabin);
    }

    @Inject(method = "findEntitiesOnPath", at = @At("RETURN"), remap = false)
    private static void slimvehicle$ownCabinPierced(Projectile bullet, Vec3 start, Vec3 end, CallbackInfoReturnable<List<EntityKineticBullet.EntityResult>> cir) {
        EntityKineticBullet.EntityResult cabin = slimvehicle$cabin(bullet, start, end);
        if (cabin != null) cir.getReturnValue().add(cabin);
    }

    @Unique
    private static EntityKineticBullet.EntityResult slimvehicle$cabin(Projectile bullet, Vec3 start, Vec3 end) {
        Entity owner = bullet.getOwner();
        if (owner == null || !(owner.getRootVehicle() instanceof BuilderVehicle vehicle)) return null;
        return vehicle.vehicle().cabinHit(Convert.point(start), Convert.point(end))
                .map(hit -> new EntityKineticBullet.EntityResult(vehicle, Convert.vec(hit.point()), false)).orElse(null);
    }
}
