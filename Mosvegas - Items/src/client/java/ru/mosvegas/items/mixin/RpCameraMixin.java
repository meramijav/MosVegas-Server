package ru.mosvegas.items.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.rp.RpPoser;

@Mixin(Camera.class)
public abstract class RpCameraMixin {
    @Shadow private boolean thirdPerson;

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Shadow protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("TAIL"))
    private void mosvegas$sceneEyes(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView,
                                    float tickDelta, CallbackInfo ci) {
        RpPoser.Shot shot = RpPoser.camera(focused, tickDelta);
        if (shot == null) return;
        setRotation(shot.yaw(), shot.pitch());
        setPos(shot.position());
        this.thirdPerson = true;
    }
}
