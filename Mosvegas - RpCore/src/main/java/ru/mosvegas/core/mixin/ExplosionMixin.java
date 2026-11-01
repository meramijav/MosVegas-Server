package ru.mosvegas.core.mixin;

import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.core.zone.*;
import java.util.List;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private World world;
    @Shadow public abstract List<BlockPos> getAffectedBlocks();
    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void mosvegas$explosion(boolean particles, CallbackInfo ci) {
        if (!world.isClient) getAffectedBlocks().removeIf(pos -> !ZoneService.environment(world, pos, ZoneAction.TERRAIN));
    }
}
