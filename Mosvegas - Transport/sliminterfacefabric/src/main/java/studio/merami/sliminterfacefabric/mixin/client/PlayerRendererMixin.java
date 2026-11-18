package studio.merami.sliminterfacefabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.client.DriverPose;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    @Unique
    private static EntityVehicleF_Physics slimvehicle$seat(AbstractClientPlayer player) {
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        return vehicle != null && vehicle.seated(WrapperEntity.of(player)) ? vehicle : null;
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void slimvehicle$seatedScale(AbstractClientPlayer player, PoseStack pose, float partial, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = slimvehicle$seat(player);
        if (vehicle == null) return;
        float scale = (float) vehicle.definition().rider.scale;
        pose.scale(scale, scale, scale);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("TAIL"))
    private void slimvehicle$seatedLean(AbstractClientPlayer player, PoseStack pose, float age, float yaw, float partial, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = slimvehicle$seat(player);
        if (vehicle != null) DriverPose.lean(pose, vehicle, partial, WrapperEntity.of(player).equals(vehicle.controllingPassenger()));
    }
}
