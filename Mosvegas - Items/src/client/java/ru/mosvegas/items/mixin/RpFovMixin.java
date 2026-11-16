package ru.mosvegas.items.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.items.rp.RpPlayback;

@Mixin(GameRenderer.class)
public abstract class RpFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mosvegas$sceneFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (changingFov && client.player != null && RpPlayback.of(client.player) != null) {
            cir.setReturnValue((double) client.options.getFov().getValue());
        }
    }
}
