package ru.mosvegas.core.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.ZoneService;

@Mixin(BowItem.class)
public abstract class BowItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void mosvegas$bow(ItemStack stack, World world, LivingEntity user, int remaining, CallbackInfo ci) {
        if (!ZoneService.shoot(user)) ci.cancel();
    }
}
