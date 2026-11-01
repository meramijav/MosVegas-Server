package ru.mosvegas.core.mixin;

import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.*;

@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {
    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void mosvegas$fall(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!ZoneService.environment(world, pos, ZoneAction.TERRAIN)) ci.cancel();
    }
}
