package ru.mosvegas.character.mixin;

import net.minecraft.network.message.LastSeenMessageList;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.character.server.CharacterGate;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "handleCommandExecution", at = @At("HEAD"), cancellable = true)
    private void mosvegasCharacter$command(CommandExecutionC2SPacket packet, LastSeenMessageList lastSeen, CallbackInfo ci) {
        if (CharacterGate.isPending(player)) {
            player.sendMessage(Text.literal("Сначала создайте персонажа.").formatted(Formatting.RED));
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void mosvegasCharacter$action(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (CharacterGate.isPending(player)) ci.cancel();
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void mosvegasCharacter$click(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (CharacterGate.isPending(player)) ci.cancel();
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
    private void mosvegasCharacter$creative(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (CharacterGate.isPending(player)) ci.cancel();
    }

    @Inject(method = "onPickFromInventory", at = @At("HEAD"), cancellable = true)
    private void mosvegasCharacter$pick(PickFromInventoryC2SPacket packet, CallbackInfo ci) {
        if (CharacterGate.isPending(player)) ci.cancel();
    }
}
