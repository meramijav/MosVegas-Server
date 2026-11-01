package studio.merami.sliminterfacefabric.mixin.compat;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.sliminterfacefabric.compat.TaczCompat;

@Pseudo
@Mixin(targets = "com.tacz.guns.util.InputExtraCheck", remap = false)
public class InputExtraCheckMixin {
    @Inject(method = "isInGame", at = @At("HEAD"), cancellable = true, remap = false)
    private static void slimvehicle$notWhileDriving(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && TaczCompat.driving(mc.player)) cir.setReturnValue(false);
    }
}
