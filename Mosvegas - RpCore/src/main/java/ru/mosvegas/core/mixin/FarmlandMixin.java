package ru.mosvegas.core.mixin;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.*;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandMixin {
    @Inject(method = "setToDirt", at = @At("HEAD"), cancellable = true)
    private static void mosvegas$trample(Entity entity, BlockState state, World world, BlockPos pos, CallbackInfo ci) {
        if (!world.isClient && !(entity instanceof ServerPlayerEntity p ? ZoneService.can(p, world, pos, ZoneAction.BUILD)
                : ZoneService.environment(world, pos, ZoneAction.TERRAIN))) ci.cancel();
    }
}
