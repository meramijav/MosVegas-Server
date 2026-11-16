package ru.mosvegas.items.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.rp.RpPoser;

@Mixin(LivingEntityRenderer.class)
public abstract class RpPoseMixin {
    @Shadow protected EntityModel<?> model;

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V"))
    private void mosvegas$restPose(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                   VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (model instanceof BipedEntityModel<?> biped) {
            for (ModelPart part : new ModelPart[]{biped.head, biped.body, biped.rightArm, biped.leftArm, biped.rightLeg, biped.leftLeg}) {
                part.resetTransform();
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void mosvegas$scenePose(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                    VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (model instanceof BipedEntityModel<?> biped) RpPoser.pose(entity, biped, tickDelta);
    }
}
