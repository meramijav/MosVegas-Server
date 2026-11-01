package cn.sh1rocu.tacz.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    @Final
    private static Map<InputConstants.Key, KeyMapping> MAP;

    @Shadow
    private InputConstants.Key key;

    @Shadow
    private int clickCount;

    @Shadow
    public abstract void setDown(boolean bl);

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void tacz$ignoreGunKeyConflict(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        KeyMapping self = (KeyMapping) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Options options = mc.options;
        if (options == null) {
            return;
        }

        // ShootKey does not conflict with vanilla attack key (LMB)
        if ((self == ShootKey.SHOOT_KEY && other == options.keyAttack) ||
                (self == options.keyAttack && other == ShootKey.SHOOT_KEY)) {
            cir.setReturnValue(false);
        }
        // AimKey does not conflict with vanilla use key (RMB)
        else if ((self == AimKey.AIM_KEY && other == options.keyUse) ||
                (self == options.keyUse && other == AimKey.AIM_KEY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "click", at = @At("HEAD"))
    private static void tacz$clickAllMatching(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping primary = MAP.get(key);
        for (KeyMapping mapping : ALL.values()) {
            if (mapping != primary) {
                InputConstants.Key mappingKey = ((KeyMappingMixin) (Object) mapping).key;
                if (mappingKey != null && mappingKey.equals(key)) {
                    ((KeyMappingMixin) (Object) mapping).clickCount++;
                }
            }
        }
    }

    @Inject(method = "set", at = @At("HEAD"))
    private static void tacz$setAllMatching(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeyMapping primary = MAP.get(key);
        for (KeyMapping mapping : ALL.values()) {
            if (mapping != primary) {
                InputConstants.Key mappingKey = ((KeyMappingMixin) (Object) mapping).key;
                if (mappingKey != null && mappingKey.equals(key)) {
                    mapping.setDown(held);
                }
            }
        }
    }
}

