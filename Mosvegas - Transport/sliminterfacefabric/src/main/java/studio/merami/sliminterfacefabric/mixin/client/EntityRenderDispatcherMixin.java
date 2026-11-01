package studio.merami.sliminterfacefabric.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.client.hud.InteractionOverlay;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void slimvehicle$vehicleContours(PoseStack pose, VertexConsumer buffer, Entity entity, float partial, CallbackInfo ci) {
        if (!(entity instanceof BuilderVehicle builder)) return;
        EntityVehicleF_Physics vehicle = builder.vehicle();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(builder.getYRot() - Mth.rotLerp(partial, builder.yRotO, builder.getYRot())));
        for (Hull.Box box : vehicle.hull().boxes()) {
            if (box.part == null) InteractionOverlay.draw(pose, buffer, box, vehicle.position(), box.glass >= 0 ? 0x62dded : 0xd6dde0, 1);
        }
        for (InteractionZone zone : vehicle.interactionZones()) InteractionOverlay.draw(pose, buffer, zone.box(), vehicle.position(), zone.color(), 1);
        pose.popPose();
        ci.cancel();
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"))
    private boolean slimvehicle$noRiderShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float strength, float partial, LevelReader level, float radius) {
        return !(entity.getVehicle() instanceof BuilderVehicle);
    }
}
