package ru.mosvegas.core.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin extends Item {
    protected BucketItemMixin(Settings settings) { super(settings); }
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mosvegas$bucket(World world, PlayerEntity actor, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!(actor instanceof ServerPlayerEntity player)) return;
        BlockHitResult hit = raycast(world, player, RaycastContext.FluidHandling.ANY);
        if (hit.getType() == HitResult.Type.BLOCK && (!ZoneService.check(player, hit.getBlockPos(), ZoneAction.BUILD)
                || !ZoneService.check(player, hit.getBlockPos().offset(hit.getSide()), ZoneAction.BUILD)))
            cir.setReturnValue(TypedActionResult.fail(player.getStackInHand(hand)));
    }
}
