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

@Mixin(AbstractPressurePlateBlock.class)
public abstract class PressurePlateMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void mosvegas$plate(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!world.isClient && !ZoneService.can(entity instanceof ServerPlayerEntity p ? p : null, world, pos, ZoneAction.INTERACT)) ci.cancel();
    }
}
