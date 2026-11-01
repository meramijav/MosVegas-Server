package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import studio.merami.sliminterfacefabric.wrapper.IWrapperHolder;

@Mixin(Level.class)
public class LevelMixin implements IWrapperHolder {
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
}
