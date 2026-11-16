package ru.mosvegas.items.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.rp.RpPlayback;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class RpHeldItemMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void mosvegas$rpHeld(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
                                 MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (RpPlayback.acting(entity)) {
            ci.cancel();
            return;
        }
    }
}
