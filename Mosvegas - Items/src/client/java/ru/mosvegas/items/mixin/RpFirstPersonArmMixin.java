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
import ru.mosvegas.items.rp.RpHeld;
import ru.mosvegas.items.rp.RpPoser;

@Mixin(HeldItemRenderer.class)
public abstract class RpFirstPersonArmMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void mosvegas$handUnderDocument(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
                                            float swing, ItemStack stack, float equip, MatrixStack matrices,
                                            VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!RpHeld.heldInView(stack) || RpPoser.cameraActive()) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        RpHeld.firstPersonArm(player, stack, arm, equip, swing, matrices, vertices, light);
    }
}
