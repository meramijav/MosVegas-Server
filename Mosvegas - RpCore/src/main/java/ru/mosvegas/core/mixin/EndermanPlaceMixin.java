package ru.mosvegas.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PlaceBlockGoal")
public abstract class EndermanPlaceMixin {
    @Inject(method = "canPlaceOn", at = @At("HEAD"), cancellable = true)
    private void mosvegas$enderman(World world, BlockPos pos, BlockState carried, BlockState target, BlockState below, BlockPos belowPos, CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneService.environment(world, pos, ZoneAction.TERRAIN)) cir.setReturnValue(false);
    }
}
