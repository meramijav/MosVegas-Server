package ru.mosvegas.core.mixin;

import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void mosvegas$place(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return;
        ItemPlacementContext actual = ((BlockItem)(Object)this).getPlacementContext(context);
        if (actual == null) return;
        BlockPos pos = actual.getBlockPos();
        Block block = ((BlockItem)(Object)this).getBlock();
        boolean allowed = ZoneService.check(player, pos, ZoneAction.BUILD);
        if (block instanceof TallPlantBlock || block instanceof DoorBlock) allowed &= ZoneService.check(player, pos.up(), ZoneAction.BUILD);
        if (block instanceof BedBlock) allowed &= ZoneService.check(player, pos.offset(actual.getHorizontalPlayerFacing()), ZoneAction.BUILD);
        if (!allowed) cir.setReturnValue(ActionResult.FAIL);
    }
}
