package ru.mosvegas.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.*;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "flow", at = @At("HEAD"), cancellable = true)
    private void mosvegas$fluid(WorldAccess access, BlockPos pos, BlockState state, Direction direction, FluidState fluid, CallbackInfo ci) {
        if (access instanceof World world && !world.isClient && !ZoneService.environment(world, pos, ZoneAction.TERRAIN)) ci.cancel();
    }
}
