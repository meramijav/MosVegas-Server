package ru.mosvegas.character.mixin;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.character.data.AppearanceLookup;
import ru.mosvegas.character.data.CharacterPalette;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getActiveEyeHeight", at = @At("RETURN"), cancellable = true)
    private void mosvegasCharacter$eyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        float s = CharacterPalette.heightScale(AppearanceLookup.of((PlayerEntity) (Object) this));
        if (s != 1f) cir.setReturnValue(Math.min(cir.getReturnValueF() * s, dimensions.height - 0.05f));
    }
}
