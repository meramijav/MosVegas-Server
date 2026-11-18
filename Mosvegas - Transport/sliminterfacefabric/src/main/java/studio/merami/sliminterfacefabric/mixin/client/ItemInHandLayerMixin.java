package studio.merami.sliminterfacefabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.client.paint.GunHolder;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void slimvehicle$hideDriverItems(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                             float a, float b, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(entity);
        if (vehicle != null && WrapperEntity.of(entity).equals(vehicle.controllingPassenger())) ci.cancel();
        else GunHolder.set(entity);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN"))
    private void slimvehicle$forgetHolder(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                          float a, float b, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        GunHolder.set(null);
    }
}
