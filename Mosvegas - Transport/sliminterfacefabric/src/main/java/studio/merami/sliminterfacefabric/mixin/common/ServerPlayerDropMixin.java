package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.sliminterfacefabric.item.BuilderItem;

@Mixin(ServerPlayer.class)
public class ServerPlayerDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$gunStaysAtTheStand(ItemStack stack, boolean randomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (stack.getItem() instanceof BuilderItem builder && builder.item == SlimItems.SPRAY_GUN) cir.setReturnValue(null);
    }
}
