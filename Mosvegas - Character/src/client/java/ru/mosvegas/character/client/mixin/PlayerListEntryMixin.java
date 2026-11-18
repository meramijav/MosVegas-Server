package ru.mosvegas.character.client.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.character.client.skin.SkinOverride;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Shadow public abstract GameProfile getProfile();

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void mosvegasCharacter$skin(CallbackInfoReturnable<SkinTextures> cir) {
        SkinTextures out = SkinOverride.apply(getProfile().getId(), cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
