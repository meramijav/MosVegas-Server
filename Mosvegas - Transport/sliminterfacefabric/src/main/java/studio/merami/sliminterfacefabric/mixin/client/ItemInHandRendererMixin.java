package studio.merami.sliminterfacefabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$steeringHands(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        if (vehicle != null && vehicle.driver(WrapperPlayer.of(player))) ci.cancel();
    }
}
