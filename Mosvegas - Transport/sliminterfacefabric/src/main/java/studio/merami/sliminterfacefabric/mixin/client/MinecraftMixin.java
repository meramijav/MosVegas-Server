package studio.merami.sliminterfacefabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.slimcore.systems.SeatedInteractions;
import studio.merami.sliminterfacefabric.client.VehicleControls;
import studio.merami.sliminterfacefabric.compat.TaczCompat;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$noAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null && SeatedInteractions.seated(WrapperPlayer.of(mc.player))) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$noMining(boolean held, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || !SeatedInteractions.seated(WrapperPlayer.of(mc.player))) return;
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$useOwnVehicle(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || !SeatedInteractions.seated(WrapperPlayer.of(mc.player))) return;
        if (!TaczCompat.holdsGun(mc.player)) VehicleControls.interactSeated(mc);
        ci.cancel();
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$keepDrivingView(Screen screen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if ((screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) && mc.player != null
                && mc.player.getVehicle() instanceof BuilderVehicle) ci.cancel();
    }
}
