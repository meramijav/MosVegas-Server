package ru.mosvegas.items.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.HeldConsumableRenderer;

@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonConsumableMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void mosvegas$heldModel(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
                                    float swing, ItemStack stack, float equip, MatrixStack matrices,
                                    VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!HeldConsumableRenderer.handles(stack)) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        HeldConsumableRenderer.firstPerson(player, stack, arm, delta, equip, swing, matrices, vertices, light);
        ci.cancel();
    }
}
