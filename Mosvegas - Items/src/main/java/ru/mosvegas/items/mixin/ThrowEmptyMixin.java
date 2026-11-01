package ru.mosvegas.items.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.items.LitterEntity;
import ru.mosvegas.items.MosVegasConsumableItem;

@Mixin(ServerPlayerEntity.class)
public abstract class ThrowEmptyMixin {
    // The drop key (and dropping from the inventory screen) throws an empty can where the
    // player looks instead of dropping an item entity. Death drops are thrown randomly and keep vanilla behavior.
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void mosvegas$throwEmpty(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (throwRandomly || !retainOwnership || !(stack.getItem() instanceof MosVegasConsumableItem)
                || !MosVegasConsumableItem.isSpent(stack)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        LitterEntity.toss(player.getServerWorld(), player, stack);
        player.increaseStat(Stats.DROPPED.getOrCreateStat(stack.getItem()), stack.getCount());
        cir.setReturnValue(null);
    }
}
