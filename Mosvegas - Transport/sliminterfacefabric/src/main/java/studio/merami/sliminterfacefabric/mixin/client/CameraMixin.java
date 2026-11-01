package studio.merami.sliminterfacefabric.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.sliminterfacefabric.client.DriverPose;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private static final double SLIMVEHICLE_CLEARANCE = .3;
    @Shadow
    private BlockGetter level;
    @Shadow
    private Entity entity;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "getMaxZoom", at = @At("RETURN"), cancellable = true)
    private void slimvehicle$keepOutOfGround(double start, CallbackInfoReturnable<Double> cir) {
        if (entity == null || !(entity.getVehicle() instanceof BuilderVehicle)) return;
        double distance = cir.getReturnValueD();
        Vec3 back = new Vec3(-forwards.x(), -forwards.y(), -forwards.z());
        HitResult hit = level.clip(new ClipContext(position, position.add(back.scale(distance + SLIMVEHICLE_CLEARANCE)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) distance = Math.min(distance, hit.getLocation().distanceTo(position) - SLIMVEHICLE_CLEARANCE);
        cir.setReturnValue(Math.max(0, distance));
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void slimvehicle$driverNeck(BlockGetter area, Entity viewer, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        if (!detached && viewer != null && DriverPose.firstPersonDriver(viewer)) setPosition(DriverPose.eye(viewer, partial));
    }
}
