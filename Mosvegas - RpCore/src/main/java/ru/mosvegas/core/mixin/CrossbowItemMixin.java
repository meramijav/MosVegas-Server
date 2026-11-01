package ru.mosvegas.core.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.ZoneService;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    @Inject(method = "shootAll", at = @At("HEAD"), cancellable = true)
    private static void mosvegas$crossbow(World world, LivingEntity user, Hand hand, ItemStack stack, float speed, float divergence, CallbackInfo ci) {
        if (!ZoneService.shoot(user)) ci.cancel();
    }
}
