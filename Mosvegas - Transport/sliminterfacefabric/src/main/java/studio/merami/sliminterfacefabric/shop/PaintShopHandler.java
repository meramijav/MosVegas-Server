package studio.merami.sliminterfacefabric.shop;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.network.PacketPaintMenu;
import studio.merami.sliminterfacefabric.network.PacketPaintResult;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

final class PaintShopHandler {
    private static PaintShop.Prices prices = PaintShop.Prices.DEFAULT;

    private PaintShopHandler() {}

    static void configure(PaintShop.Prices configured) {
        prices = configured;
    }

    static void open(ServerPlayer player, EntityVehicleF_Physics vehicle) {
        String refused = refusal(player, vehicle);
        if (refused != null) {
            WrapperPlayer.of(player).message(Text.translatable(refused));
            return;
        }
        Economy economy = Economy.find();
        double balance = economy == null ? Double.NaN : economy.balance(player);
        String text = economy == null ? "" : economy.format(balance);
        ServerPlayNetworking.send(player, new PacketPaintMenu(vehicle.id(), balance, text, prices, free(player, economy)));
    }

    static void order(ServerPlayer player, EntityVehicleF_Physics vehicle, PaintShop.Order order) {
        String refused = refusal(player, vehicle);
        if (refused == null) refused = PaintShop.check(vehicle, order);
        Economy economy = Economy.find();
        if (refused != null) {
            result(player, vehicle, false, refused, economy);
            return;
        }
        long price = order.price(prices);
        boolean free = free(player, economy);
        if (!free) {
            if (economy == null) {
                result(player, vehicle, false, "message.slimvehicle.paint.no_economy", null);
                return;
            }
            if (economy.balance(player) < price || !economy.withdraw(player, price)) {
                result(player, vehicle, false, "message.slimvehicle.paint.no_money", economy);
                return;
            }
        }
        PaintJobs.start(player, vehicle, order, free ? 0 : price);
        SlimCore.LOGGER.info("{} paid {} for painting vehicle {}", player.getGameProfile().getName(), free ? 0 : price, vehicle.uuid());
        result(player, vehicle, true, "message.slimvehicle.paint.paid", economy);
    }

    private static boolean free(ServerPlayer player, Economy economy) {
        return economy == null && player.hasPermissions(2);
    }

    private static String refusal(ServerPlayer player, EntityVehicleF_Physics vehicle) {
        if (!PaintShop.customer(vehicle, WrapperPlayer.of(player))) return "message.slimvehicle.paint.owner_only";
        if (PaintJobs.of(vehicle.uuid()) != null) return "message.slimvehicle.paint.in_progress";
        if (PaintJobs.ownedBy(player.getUUID()) != null) return "message.slimvehicle.paint.one_job";
        if (!PaintShop.parked(vehicle, StationStore.get(), StationStore.world(player.level()))) return "message.slimvehicle.paint.park_first";
        return null;
    }

    private static void result(ServerPlayer player, EntityVehicleF_Physics vehicle, boolean ok, String key, Economy economy) {
        double balance = economy == null ? Double.NaN : economy.balance(player);
        ServerPlayNetworking.send(player, new PacketPaintResult(vehicle.id(), ok, key, balance, economy == null ? "" : economy.format(balance)));
    }
}
