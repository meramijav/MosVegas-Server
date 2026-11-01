package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$noThrowingFromSeat(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        boolean outside = slot == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE);
        if ((type == ClickType.THROW || outside) && player.getVehicle() instanceof BuilderVehicle) ci.cancel();
    }
}
