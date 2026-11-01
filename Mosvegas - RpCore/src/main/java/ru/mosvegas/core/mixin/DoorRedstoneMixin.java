package ru.mosvegas.core.mixin;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.*;

@Mixin({DoorBlock.class, TrapdoorBlock.class, FenceGateBlock.class})
public abstract class DoorRedstoneMixin {
    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    private void mosvegas$redstone(BlockState state, World world, BlockPos pos, Block source, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
        if (world.isClient) return;
        if (!ZoneService.environment(world, pos, ZoneAction.DOORS)) ci.cancel();
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos other = state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            if (!ZoneService.environment(world, other, ZoneAction.DOORS)) ci.cancel();
        }
    }
}
