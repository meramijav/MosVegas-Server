package studio.merami.sliminterfacefabric.mixin.client;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.sliminterfacefabric.client.DriverPose;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "turn", at = @At("TAIL"))
    private void slimvehicle$clampDriverLook(double yaw, double pitch, CallbackInfo ci) {
        DriverPose.clampLook((Entity) (Object) this);
    }
}
