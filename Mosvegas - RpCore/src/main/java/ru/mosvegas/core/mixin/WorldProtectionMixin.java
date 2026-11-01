package ru.mosvegas.core.mixin;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(World.class)
public abstract class WorldProtectionMixin {
    @Inject(method = "breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void mosvegas$break(BlockPos pos, boolean drop, Entity entity, int depth, CallbackInfoReturnable<Boolean> cir) {
        World world = (World)(Object)this;
        if (world.isClient) return;
        boolean allowed = entity instanceof ServerPlayerEntity player ? ZoneService.check(player, pos, ZoneAction.BUILD)
                : ZoneService.environment(world, pos, ZoneAction.TERRAIN);
        if (!allowed) cir.setReturnValue(false);
    }
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void mosvegas$fire(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        World world = (World)(Object)this;
        if (!world.isClient && state.getBlock() instanceof AbstractFireBlock && !ZoneService.environment(world, pos, ZoneAction.TERRAIN)) cir.setReturnValue(false);
    }
}
