package studio.merami.sliminterfacefabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.client.CraftRiderPose;
import studio.merami.sliminterfacefabric.client.DriverPose;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
    @Shadow
    public ModelPart leftArm;
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftLeg;
    @Shadow
    public ModelPart rightLeg;
    @Shadow
    public ModelPart head;
    @Shadow
    public ModelPart hat;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void slimvehicle$resetArmScale(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        leftArm.yScale = rightArm.yScale = 1;
        leftLeg.yScale = rightLeg.yScale = 1;
        if ((Object) this instanceof PlayerModel<?> player) player.leftPants.yScale = player.rightPants.yScale = 1;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void slimvehicle$driverArms(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(entity);
        if (vehicle == null || !vehicle.seated(WrapperEntity.of(entity))) return;
        DriverPose.faceForward(head, hat);
        if (!WrapperEntity.of(entity).equals(vehicle.controllingPassenger())) return;
        float partial = Minecraft.getInstance().getFrameTime();
        DriverPose.arms(leftArm, rightArm, vehicle, partial);
        if (CraftRiderPose.legs(leftLeg, rightLeg, vehicle, partial) && (Object) this instanceof PlayerModel<?> player) {
            player.leftPants.yScale = leftLeg.yScale;
            player.rightPants.yScale = rightLeg.yScale;
        }
    }
}
