package ru.mosvegas.core.mixin;

import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.core.zone.*;

@Mixin(HopperBlockEntity.class)
public abstract class HopperMixin {
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void mosvegas$hopper(Inventory source, Inventory target, ItemStack stack, Direction side, CallbackInfoReturnable<ItemStack> cir) {
        if (!allowed(source) || !allowed(target)) cir.setReturnValue(stack);
    }
    private static boolean allowed(Inventory inventory) {
        if (inventory instanceof BlockEntity block && block.getWorld() != null) return ZoneService.environment(block.getWorld(), block.getPos(), ZoneAction.CONTAINERS);
        if (inventory instanceof Entity entity) return ZoneService.environment(entity.getWorld(), entity.getBlockPos(), ZoneAction.CONTAINERS);
        if (inventory instanceof DoubleInventoryAccessor pair) return allowed(pair.mosvegas$first()) && allowed(pair.mosvegas$second());
        return true;
    }
}
