package ru.mosvegas.core.mixin;

import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(LivingEntity.class)
public abstract class StatusEffectMixin {
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void mosvegas$potions(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (effect.getEffectType().isBeneficial() || !((Object)this instanceof ServerPlayerEntity victim)) return;
        Entity actor = source instanceof ProjectileEntity projectile ? projectile.getOwner() : source;
        if (actor instanceof ServerPlayerEntity player && player != victim
                && (!ZoneService.can(player, victim.getWorld(), victim.getBlockPos(), ZoneAction.PVP)
                || !ZoneService.can(player, player.getWorld(), player.getBlockPos(), ZoneAction.PVP))) cir.setReturnValue(false);
    }
}
