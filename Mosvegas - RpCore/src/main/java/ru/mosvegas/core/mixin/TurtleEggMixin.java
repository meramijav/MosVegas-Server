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

@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggMixin {
    @Inject(method = "tryBreakEgg", at = @At("HEAD"), cancellable = true)
    private void mosvegas$egg(World world, BlockState state, BlockPos pos, Entity entity, int chance, CallbackInfo ci) {
        if (!world.isClient && !(entity instanceof ServerPlayerEntity p ? ZoneService.can(p, world, pos, ZoneAction.BUILD)
                : ZoneService.environment(world, pos, ZoneAction.TERRAIN))) ci.cancel();
    }
}
