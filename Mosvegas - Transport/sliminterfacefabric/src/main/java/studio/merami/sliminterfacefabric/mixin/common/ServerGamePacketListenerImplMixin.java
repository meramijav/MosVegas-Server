package studio.merami.sliminterfacefabric.mixin.common;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$keepServerAuthority(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (player.getRootVehicle() instanceof BuilderVehicle) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void slimvehicle$noSeatedActions(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!(player.getVehicle() instanceof BuilderVehicle)) return;
        switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND, START_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> ci.cancel();
            default -> {
            }
        }
    }
}
