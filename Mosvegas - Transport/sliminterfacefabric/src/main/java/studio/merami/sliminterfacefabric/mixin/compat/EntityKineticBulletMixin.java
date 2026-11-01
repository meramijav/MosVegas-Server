package studio.merami.sliminterfacefabric.mixin.compat;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperDamage;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class EntityKineticBulletMixin {
    @Shadow
    public abstract float getDamage(Vec3 hitVec);

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void slimvehicle$vehicleImpact(TacHitResult result, Vec3 start, Vec3 end, CallbackInfo ci) {
        if (!(result.getEntity() instanceof BuilderVehicle vehicle)) return;
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (bullet.getOwner() != null && bullet.getOwner().getRootVehicle() == vehicle) {
            vehicle.vehicle().cabinShot(Convert.point(start), Convert.point(end), getDamage(result.getLocation()));
            ci.cancel();
            return;
        }
        vehicle.vehicle().projectileHit(Convert.point(start), Convert.point(end), new WrapperDamage(vehicle.damageSources().thrown(bullet, bullet.getOwner())),
                getDamage(result.getLocation()));
        ci.cancel();
    }
}
