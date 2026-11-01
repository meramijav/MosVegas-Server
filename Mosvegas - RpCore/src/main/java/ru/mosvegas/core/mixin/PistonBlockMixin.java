package ru.mosvegas.core.mixin;

import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void mosvegas$piston(World world, BlockPos pos, Direction direction, boolean extending, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        if (!ZoneService.environment(world, pos, ZoneAction.TERRAIN) || !ZoneService.environment(world, pos.offset(direction), ZoneAction.TERRAIN)) {
            cir.setReturnValue(false); return;
        }
        PistonHandler handler = new PistonHandler(world, pos, direction, extending);
        if (!handler.calculatePush()) return;
        Direction movement = extending ? direction : direction.getOpposite();
        for (BlockPos moved : handler.getMovedBlocks()) {
            if (!ZoneService.environment(world, moved, ZoneAction.TERRAIN) || !ZoneService.environment(world, moved.offset(movement), ZoneAction.TERRAIN)) {
                cir.setReturnValue(false); return;
            }
        }
        for (BlockPos broken : handler.getBrokenBlocks()) if (!ZoneService.environment(world, broken, ZoneAction.TERRAIN)) { cir.setReturnValue(false); return; }
    }
}
