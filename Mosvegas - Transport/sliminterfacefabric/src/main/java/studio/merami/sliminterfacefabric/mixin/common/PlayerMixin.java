package studio.merami.sliminterfacefabric.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(Player.class)
public class PlayerMixin {
    @WrapOperation(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean slimvehicle$vehiclesNeverForcePose(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return BuilderVehicle.ignoringVehicles(() -> original.call(level, entity, box));
    }

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$shiftKeepsTheSeat(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof BuilderVehicle) cir.setReturnValue(false);
    }

    @Inject(method = "touch", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$noPickupFromSeat(Entity entity, CallbackInfo ci) {
        if ((entity instanceof ItemEntity || entity instanceof AbstractArrow) && ((Player) (Object) this).getVehicle() instanceof BuilderVehicle) ci.cancel();
    }
}
