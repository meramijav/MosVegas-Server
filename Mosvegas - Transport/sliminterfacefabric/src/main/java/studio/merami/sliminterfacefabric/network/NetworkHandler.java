package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(PacketVehicleInput.TYPE, (packet, player, sender) -> {
            if (player.serverLevel().getEntity(packet.vehicle()) instanceof BuilderVehicle vehicle) {
                vehicle.vehicle().acceptInput(WrapperPlayer.of(player), packet.throttle(), packet.brake(), packet.steer(), packet.handbrake());
                vehicle.vehicle().hornInput(WrapperPlayer.of(player), packet.horn());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PacketVehicleAction.TYPE, (packet, player, sender) -> {
            if (player.serverLevel().getEntity(packet.vehicle()) instanceof BuilderVehicle vehicle) vehicle.vehicle().action(WrapperPlayer.of(player), packet.action());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!server.isSingleplayerOwner(handler.getPlayer().getGameProfile()) && ServerPlayNetworking.canSend(handler.getPlayer(), PacketVehicleConfigs.TYPE)) {
                sender.sendPacket(PacketVehicleConfigs.of(PackParser.configs()));
            }
        });
    }
}
