package ru.mosvegas.items.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.HeldConsumableRenderer;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class ThirdPersonConsumableMixin {
    // At HEAD the stack is still at the model root, before vanilla's arm transform;
    // the item is placed from the arm and head parts posed for this entity.
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void mosvegas$grip(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
                               MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!HeldConsumableRenderer.handles(stack)) return;
        if (!(((FeatureRenderer<?, ?>) (Object) this).getContextModel() instanceof BipedEntityModel<?> model)) return;
        HeldConsumableRenderer.thirdPerson(entity, stack, arm, model, matrices, vertices, light);
        ci.cancel();
    }
}
