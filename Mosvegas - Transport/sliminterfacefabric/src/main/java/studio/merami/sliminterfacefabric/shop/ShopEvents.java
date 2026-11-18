package studio.merami.sliminterfacefabric.shop;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.items.AItemBase;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.paint.PaintStations;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.item.BuilderItem;
import studio.merami.sliminterfacefabric.network.PacketPaintOpen;
import studio.merami.sliminterfacefabric.network.PacketPaintOrder;
import studio.merami.sliminterfacefabric.network.PacketPaintSpray;
import studio.merami.sliminterfacefabric.network.PacketPlateRequest;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class ShopEvents {
    private static final int RANGE_CHECK_TICKS = 20;
    private static final int ZONE_HEADROOM = 5;
    private static final Map<UUID, BlockPos> CORNERS = new HashMap<>();

    private ShopEvents() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(ShopEvents::starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped());
        ServerTickEvents.END_SERVER_TICK.register(ShopEvents::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            EntityVehicleF_Physics vehicle = BuilderVehicle.core(entity);
            if (vehicle == null) return;
            PlateShop.loaded(vehicle);
            PaintJobs.loaded(vehicle);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> StationStore.send(player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StationStore.send(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            takeGuns(handler.getPlayer().getInventory());
            PlateShop.left(handler.getPlayer().getUUID());
            CORNERS.remove(handler.getPlayer().getUUID());
        });
        UseBlockCallback.EVENT.register(ShopEvents::useBlock);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> holdsGun(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> holdsGun(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> holdsGun(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerPlayNetworking.registerGlobalReceiver(PacketPaintOpen.TYPE, (packet, player, sender) -> vehicle(player, packet.vehicle(), PaintShopHandler::open));
        ServerPlayNetworking.registerGlobalReceiver(PacketPaintOrder.TYPE, (packet, player, sender) -> vehicle(player, packet.vehicle(), (who, target) -> PaintShopHandler.order(who, target, packet.order())));
        ServerPlayNetworking.registerGlobalReceiver(PacketPaintSpray.TYPE, (packet, player, sender) -> vehicle(player, packet.vehicle(), PaintJobs::spray));
        ServerPlayNetworking.registerGlobalReceiver(PacketPlateRequest.TYPE, (packet, player, sender) -> PlateShop.request(player, packet.action(), packet.region()));
    }

    private static void vehicle(ServerPlayer player, int id, BiConsumer<ServerPlayer, EntityVehicleF_Physics> handler) {
        if (player.serverLevel().getEntity(id) instanceof BuilderVehicle vehicle) handler.accept(player, vehicle.vehicle());
    }

    private static void starting(MinecraftServer server) {
        ShopFiles.Prices prices = ShopFiles.prices();
        PaintShopHandler.configure(prices.paint());
        StationStore.load(server);
        PaintJobs.load(server);
        PlateShop.load(server, prices.plates());
    }

    private static void stopped() {
        StationStore.clear();
        PaintJobs.clear();
        PlateShop.clear();
        CORNERS.clear();
    }

    private static void tick(MinecraftServer server) {
        PaintJobs.tick(server);
        if (server.getTickCount() % RANGE_CHECK_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlateShop.watch(player);
            if (gunCount(player.getInventory()) == 0) continue;
            double range = PaintStations.GUN_RANGE;
            if (StationStore.get().nearestStandSqr(StationStore.world(player.level()), player.getX(), player.getY(), player.getZ()) <= range * range) continue;
            takeGuns(player.getInventory());
            message(player, "message.slimvehicle.station.gun_returned");
        }
    }

    private static AItemBase item(ItemStack stack) {
        return stack.getItem() instanceof BuilderItem builder ? builder.item : null;
    }

    private static boolean holdsGun(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && PaintJobs.gun(player.getMainHandItem());
    }

    private static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        AItemBase held = item(player.getMainHandItem());
        if (level.isClientSide) return hand == InteractionHand.MAIN_HAND && (held == SlimItems.ZONE_MARKER || held == SlimItems.PAINT_STAND) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        String world = StationStore.world(level);
        PaintStations.Stand stand = StationStore.get().standAt(world, pos.getX(), pos.getY(), pos.getZ());
        if (stand == null && held != SlimItems.ZONE_MARKER && held != SlimItems.PAINT_STAND) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        if (stand != null) {
            if (held == SlimItems.ZONE_MARKER && server.isShiftKeyDown() && admin(server)) removeStand(server, pos, stand);
            else if (held == SlimItems.SPRAY_GUN) returnGun(server);
            else giveGun(server);
        } else if (held == SlimItems.ZONE_MARKER) {
            if (admin(server)) mark(server, pos);
        } else if (admin(server)) {
            placeStand(server, pos, hit.getDirection());
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean admin(ServerPlayer player) {
        if (player.hasPermissions(2)) return true;
        message(player, "message.slimvehicle.station.no_permission");
        return false;
    }

    private static void mark(ServerPlayer player, BlockPos pos) {
        String world = StationStore.world(player.level());
        if (player.isShiftKeyDown()) {
            PaintStations.Station station = StationStore.get().zoneAtBlock(world, pos.getX(), pos.getY() + 1, pos.getZ());
            if (station == null) station = StationStore.get().zoneAtBlock(world, pos.getX(), pos.getY(), pos.getZ());
            if (station == null) {
                message(player, "message.slimvehicle.station.no_zone_here");
                return;
            }
            int id = station.id();
            StationStore.update(stations -> stations.removeStation(id));
            StationStore.clearBlocks(station);
            message(player, "message.slimvehicle.station.zone_removed", id);
            return;
        }
        BlockPos corner = CORNERS.remove(player.getUUID());
        if (corner == null) {
            CORNERS.put(player.getUUID(), pos.immutable());
            message(player, "message.slimvehicle.station.corner_set", pos.getX(), pos.getY(), pos.getZ());
            return;
        }
        PaintStations.Zone zone = new PaintStations.Zone(Math.min(corner.getX(), pos.getX()), Math.min(corner.getY(), pos.getY()), Math.min(corner.getZ(), pos.getZ()),
                Math.max(corner.getX(), pos.getX()), Math.max(corner.getY(), pos.getY()) + ZONE_HEADROOM, Math.max(corner.getZ(), pos.getZ()));
        try {
            String name = "zone-" + zone.minX() + "-" + zone.minZ();
            PaintStations.Station created = StationStore.update(stations -> stations.addRegion(world, name, zone)).regionStation(world, name);
            message(player, "message.slimvehicle.station.zone_created", created.id());
        } catch (PaintStations.Rejected rejected) {
            message(player, rejected.key);
        }
    }

    private static void placeStand(ServerPlayer player, BlockPos clicked, Direction face) {
        ServerLevel level = player.serverLevel();
        BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        BlockState there = level.getBlockState(target);
        if (!there.canBeReplaced()) {
            message(player, "message.slimvehicle.station.not_free");
            return;
        }
        String world = StationStore.world(level);
        PaintStations.Stand stand = new PaintStations.Stand(target.getX(), target.getY(), target.getZ(), PaintStations.facingTowards(player.getYRot()));
        try {
            PaintStations stations = StationStore.update(current -> current.addStand(world, stand));
            level.setBlockAndUpdate(target, Blocks.BARRIER.defaultBlockState());
            for (PaintStations.Station station : stations.stations) {
                if (station.stands().contains(stand) && station.world().equals(world)) message(player, "message.slimvehicle.station.stand_placed", station.id());
            }
        } catch (PaintStations.Rejected rejected) {
            message(player, rejected.key);
        }
    }

    private static void removeStand(ServerPlayer player, BlockPos pos, PaintStations.Stand stand) {
        ServerLevel level = player.serverLevel();
        StationStore.update(stations -> stations.removeStand(StationStore.world(level), stand.x(), stand.y(), stand.z()));
        StationStore.clearBlock(level, stand);
        message(player, "message.slimvehicle.station.stand_removed");
    }

    private static void giveGun(ServerPlayer player) {
        if (PaintJobs.ownedBy(player.getUUID()) == null && !player.hasPermissions(2)) {
            message(player, "message.slimvehicle.station.order_first");
            return;
        }
        if (gunCount(player.getInventory()) > 0) {
            message(player, "message.slimvehicle.station.gun_already");
            return;
        }
        if (!player.getInventory().add(new ItemStack(ItemRegistry.item(SlimItems.SPRAY_GUN)))) {
            message(player, "message.slimvehicle.station.inventory_full");
            return;
        }
        message(player, "message.slimvehicle.station.gun_taken", (int) PaintStations.GUN_RANGE);
    }

    private static void returnGun(ServerPlayer player) {
        takeGuns(player.getInventory());
        message(player, "message.slimvehicle.station.gun_put_back");
    }

    private static int gunCount(Inventory inventory) {
        int count = 0;
        for (List<ItemStack> list : List.of(inventory.items, inventory.offhand, inventory.armor)) {
            for (ItemStack stack : list) {
                if (PaintJobs.gun(stack)) count++;
            }
        }
        return count;
    }

    private static void takeGuns(Inventory inventory) {
        for (List<ItemStack> list : List.of(inventory.items, inventory.offhand, inventory.armor)) {
            for (int i = 0; i < list.size(); i++) {
                if (PaintJobs.gun(list.get(i))) list.set(i, ItemStack.EMPTY);
            }
        }
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        WrapperPlayer.of(player).message(Text.translatable(key, args));
    }
}
