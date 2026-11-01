package studio.merami.sliminterfacefabric.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$noSneakingInSeat(CallbackInfoReturnable<Boolean> cir) {
        if (((LocalPlayer) (Object) this).getVehicle() instanceof BuilderVehicle) cir.setReturnValue(false);
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$keepItemsInSeat(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (((LocalPlayer) (Object) this).getVehicle() instanceof BuilderVehicle) cir.setReturnValue(false);
    }
}
