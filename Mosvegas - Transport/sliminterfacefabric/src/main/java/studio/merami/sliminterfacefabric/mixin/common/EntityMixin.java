package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.IWrapperHolder;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

@Mixin(Entity.class)
public class EntityMixin implements IWrapperHolder {
    @Unique
    private Object slimvehicle$wrapper;

    @Override
    public Object slimvehicle$wrapper() {
        return slimvehicle$wrapper;
    }

    @Override
    public void slimvehicle$setWrapper(Object wrapper) {
        slimvehicle$wrapper = wrapper;
    }

    @Unique
    private float slimvehicle$seatedEyes() {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof Player)) return Float.NaN;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(entity);
        if (vehicle == null || !vehicle.seated(WrapperEntity.of(entity))) return Float.NaN;
        return ChassisGeometry.seatedEyes(vehicle.definition());
    }

    @Inject(method = "getEyeHeight()F", at = @At("RETURN"), cancellable = true)
    private void slimvehicle$seatedEyeHeight(CallbackInfoReturnable<Float> cir) {
        float eyes = slimvehicle$seatedEyes();
        if (!Float.isNaN(eyes)) cir.setReturnValue(eyes);
    }

    @Inject(method = "getEyeY", at = @At("RETURN"), cancellable = true)
    private void slimvehicle$seatedEyeY(CallbackInfoReturnable<Double> cir) {
        float eyes = slimvehicle$seatedEyes();
        if (!Float.isNaN(eyes)) cir.setReturnValue(((Entity) (Object) this).getY() + eyes);
    }
}
