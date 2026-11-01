package ru.mosvegas.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.ZoneService;

@Mixin(ArmorStandEntity.class)
public abstract class ArmorStandMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void mosvegas$stand(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!((Entity)(Object)this).getWorld().isClient && !ZoneService.decorationDamage((Entity)(Object)this, source)) cir.setReturnValue(false);
    }
}
