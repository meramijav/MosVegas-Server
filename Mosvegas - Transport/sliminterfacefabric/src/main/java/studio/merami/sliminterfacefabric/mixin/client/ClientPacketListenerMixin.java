package studio.merami.sliminterfacefabric.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @WrapWithCondition(method = "handleSetEntityPassengersPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private boolean slimvehicle$noShiftHint(Gui gui, Component message, boolean animate) {
        return !slimvehicle$seated();
    }

    @WrapWithCondition(method = "handleSetEntityPassengersPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/GameNarrator;sayNow(Lnet/minecraft/network/chat/Component;)V"))
    private boolean slimvehicle$noShiftNarration(GameNarrator narrator, Component message) {
        return !slimvehicle$seated();
    }

    @Unique
    private static boolean slimvehicle$seated() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof BuilderVehicle;
    }
}
