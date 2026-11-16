package ru.mosvegas.items.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.rp.RpPlayback;

@Mixin(HeadFeatureRenderer.class)
public abstract class RpHeadwearMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void mosvegas$sceneEyes(MatrixStack matrices, VertexConsumerProvider vertices, int light, LivingEntity entity,
                                    float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                                    float headYaw, float headPitch, CallbackInfo ci) {
        if (RpPlayback.firstPerson(entity)) ci.cancel();
    }
}
