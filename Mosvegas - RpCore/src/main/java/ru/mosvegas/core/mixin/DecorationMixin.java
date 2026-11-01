package ru.mosvegas.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.*;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.ZoneService;

@Mixin({AbstractDecorationEntity.class, ItemFrameEntity.class})
public abstract class DecorationMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void mosvegas$decoration(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!((Entity)(Object)this).getWorld().isClient && !ZoneService.decorationDamage((Entity)(Object)this, source)) cir.setReturnValue(false);
    }
}
