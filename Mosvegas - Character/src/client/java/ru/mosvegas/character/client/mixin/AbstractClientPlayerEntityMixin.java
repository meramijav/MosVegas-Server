package ru.mosvegas.character.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.character.client.skin.SkinOverride;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void mosvegasCharacter$skin(CallbackInfoReturnable<SkinTextures> cir) {
        SkinTextures out = SkinOverride.apply(((AbstractClientPlayerEntity) (Object) this).getUuid(), cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
