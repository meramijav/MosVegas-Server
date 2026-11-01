package ru.mosvegas.items.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.HeldConsumableRenderer;

@Mixin(BipedEntityModel.class)
public abstract class ConsumptionArmMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    // Runs before PlayerEntityModel copies the arm transforms to jacket sleeves.
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mosvegas$consumePose(LivingEntity entity, float limbAngle, float limbDistance,
                                      float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (HeldConsumableRenderer.renderingFirstPersonArm()) {
            // The first-person renderer places the whole arm itself.
            for (ModelPart arm : new ModelPart[]{rightArm, leftArm}) {
                arm.setPivot(0, 0, 0);
                arm.setAngles(0, 0, 0);
            }
            return;
        }
        HeldConsumableRenderer.poseModel((BipedEntityModel<?>) (Object) this, entity,
                MathHelper.clamp(animationProgress - entity.age, 0, 1));
    }
}
