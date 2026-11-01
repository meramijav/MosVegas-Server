package ru.mosvegas.core.mixin;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ru.mosvegas.core.zone.*;

@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal")
public abstract class EndermanPickupMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState mosvegas$enderman(World world, BlockPos pos) {
        return ZoneService.environment(world, pos, ZoneAction.TERRAIN) ? world.getBlockState(pos) : Blocks.BEDROCK.getDefaultState();
    }
}
