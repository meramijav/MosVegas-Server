package studio.merami.sliminterfacefabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.client.DriverPose;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Unique
    private static EntityVehicleF_Physics slimvehicle$seat(LivingEntity entity) {
        if (entity instanceof Player) return null;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(entity);
        return vehicle != null && vehicle.seated(WrapperEntity.of(entity)) ? vehicle : null;
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", shift = At.Shift.AFTER))
    private void slimvehicle$seatedLean(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = slimvehicle$seat(entity);
        if (vehicle != null) DriverPose.lean(pose, vehicle, partial);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", shift = At.Shift.AFTER))
    private void slimvehicle$seatedScale(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = slimvehicle$seat(entity);
        if (vehicle == null) return;
        float scale = (float) vehicle.definition().rider.scale;
        pose.scale(scale, scale, scale);
    }
}
