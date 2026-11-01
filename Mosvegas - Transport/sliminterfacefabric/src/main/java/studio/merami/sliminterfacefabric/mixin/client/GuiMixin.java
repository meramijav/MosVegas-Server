package studio.merami.sliminterfacefabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$hideDriverHotbar(float partial, GuiGraphics gui, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(mc.player);
        if (vehicle != null && vehicle.driver(WrapperPlayer.of(mc.player))) ci.cancel();
    }
}
