package studio.merami.sliminterfacefabric.client.paint;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.client.VehicleControls;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.PacketPaintMenu;
import studio.merami.sliminterfacefabric.network.PacketPaintOpen;
import studio.merami.sliminterfacefabric.network.PacketPaintOrder;
import studio.merami.sliminterfacefabric.network.PacketPaintResult;

public final class PaintShopClient {
    private static final int REQUEST_COOLDOWN = 10;
    private static long requestedAt = -100;

    private PaintShopClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketPaintMenu.TYPE, (packet, player, sender) -> open(packet));
        ClientPlayNetworking.registerGlobalReceiver(PacketPaintResult.TYPE, (packet, player, sender) -> result(packet));
    }

    public static EntityVehicleF_Physics parkedVehicle(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !ClientPlayNetworking.canSend(PacketPaintOpen.TYPE)) return null;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        if (vehicle == null || vehicle.painting() || player.getVehicle().getControllingPassenger() != player) return null;
        if (!VehicleControls.holdsKey(vehicle) && !player.hasPermissions(2)) return null;
        return PaintShop.parked(vehicle, PaintStationView.stations(), null) ? vehicle : null;
    }

    public static void request(Minecraft mc, EntityVehicleF_Physics vehicle) {
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (now - requestedAt < REQUEST_COOLDOWN) return;
        requestedAt = now;
        ClientPlayNetworking.send(new PacketPaintOpen(vehicle.id()));
    }

    public static void order(EntityVehicleF_Physics vehicle, PaintShop.Order order) {
        ClientPlayNetworking.send(new PacketPaintOrder(vehicle.id(), order));
    }

    private static void open(PacketPaintMenu packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.vehicle()) instanceof BuilderVehicle vehicle) || BuilderVehicle.ridden(mc.player) != vehicle.vehicle()) return;
        mc.setScreen(new PaintShopScreen(vehicle, packet.prices(), packet.balance(), packet.balanceText(), packet.free()));
    }

    private static void result(PacketPaintResult packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PaintShopScreen screen && screen.vehicleId() == packet.vehicle()) screen.result(packet);
        else if (mc.player != null) mc.player.displayClientMessage(Component.translatable(packet.key()), true);
    }
}
