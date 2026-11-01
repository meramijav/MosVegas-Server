package ru.mosvegas.casino.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.casino.client.SeatCamera;

/** Lifts the first-person camera of a player seated at a casino table (see {@link SeatCamera}). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);

    @Shadow public abstract Vec3d getPos();

    @Inject(method = "update", at = @At("TAIL"))
    private void mosvegas_casino$tableView(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                           float tickDelta, CallbackInfo ci) {
        Vec3d offset = SeatCamera.offset(focusedEntity, thirdPerson);
        if (offset != null) setPos(getPos().add(offset));
    }
}
