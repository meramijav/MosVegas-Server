package ru.mosvegas.items.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.MosVegasConsumableItem;

@Mixin(LivingEntity.class)
public abstract class ConsumptionEffectsMixin {
    // Suppress vanilla consume sounds, which otherwise duplicate or override configured sounds.
    @Inject(method = "spawnConsumptionEffects", at = @At("HEAD"), cancellable = true)
    private void mosvegas$effects(ItemStack stack, int count, CallbackInfo ci) {
        if (!(stack.getItem() instanceof MosVegasConsumableItem)) return;
        ci.cancel();
    }
}
