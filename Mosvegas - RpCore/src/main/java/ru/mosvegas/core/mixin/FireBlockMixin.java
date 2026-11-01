package ru.mosvegas.core.mixin;

import net.minecraft.block.FireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.*;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void mosvegas$burn(World world, BlockPos pos, int chance, Random random, int age, CallbackInfo ci) {
        if (!world.isClient && !ZoneService.environment(world, pos, ZoneAction.TERRAIN)) ci.cancel();
    }
}
