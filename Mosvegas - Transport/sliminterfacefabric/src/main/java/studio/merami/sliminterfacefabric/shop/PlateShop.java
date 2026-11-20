package studio.merami.sliminterfacefabric.shop;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.plates.PlateNumber;
import studio.merami.slimcore.plates.PlatePrices;
import studio.merami.slimcore.plates.PlateRegions;
import studio.merami.slimcore.plates.PlateRegistry;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.PacketPlateMenu;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class PlateShop {
    public static final int OPEN = 0;
    private static final String FILE = "plates.json";
    private static final PlateNumber.Spin[] SPINS = PlateNumber.Spin.values();
    private static final Random RANDOM = new SecureRandom();
    private static final double REACH = 8;
    private static final Map<UUID, Desk> DESKS = new HashMap<>();
    private static PlateRegistry registry = new PlateRegistry();
    private static PlatePrices prices = PlatePrices.DEFAULT;
    private static Path file;

    private record Desk(ResourceKey<Level> level, BlockPos pos) {}

    private record Customer(UUID vehicle, PlateRegistry.Car car, String error) {}

    private PlateShop() {}

    static void load(MinecraftServer server, PlatePrices configured) {
        prices = configured;
        file = ShopFiles.file(server, FILE);
        registry = new PlateRegistry();
        try {
            if (Files.exists(file)) registry = PlateRegistry.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Number plates were not read from {}; plates on cars stay, but nothing is known about them until they are driven again", file, e);
        }
    }

    static void clear() {
        registry = new PlateRegistry();
        DESKS.clear();
        file = null;
    }

    static void left(UUID player) {
        DESKS.remove(player);
    }

    static void watch(ServerPlayer player) {
        EntityVehicleF_Physics ridden = BuilderVehicle.ridden(player);
        if (ridden == null || !WrapperPlayer.of(player).equals(ridden.controllingPassenger())) return;
        apply(ridden);
        unique(ridden);
        if (registry.drove(player.getUUID(), ridden.uuid(), ridden.owner(), style(ridden), ridden.plate())) save();
    }

    public static void loaded(EntityVehicleF_Physics vehicle) {
        if (file == null) return;
        apply(vehicle);
        unique(vehicle);
        if (registry.car(vehicle.uuid()) != null && registry.seen(vehicle.uuid(), vehicle.owner(), style(vehicle), vehicle.plate())) save();
    }

    public static void destroyed(UUID vehicle) {
        if (file != null && registry.forget(vehicle)) save();
    }

    private static void unique(EntityVehicleF_Physics vehicle) {
        String plate = vehicle.plate();
        if (!PlateNumber.valid(plate)) return;
        UUID holder = registry.holder(plate);
        if (holder == null || holder.equals(vehicle.uuid())) return;
        vehicle.setPlate(PlateNumber.NONE);
        SlimCore.LOGGER.warn("Vehicle {} carried the number plate {} of vehicle {}; the copy was taken off", vehicle.uuid(), plate, holder);
    }

    private static void apply(EntityVehicleF_Physics vehicle) {
        String pending = registry.pending(vehicle.uuid());
        if (pending == null) return;
        vehicle.setPlate(pending);
        if (registry.applied(vehicle.uuid(), pending)) save();
    }

    private static String style(EntityVehicleF_Physics vehicle) {
        return vehicle.definition().plateFaces.isEmpty() ? "" : vehicle.definition().plates.style;
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        DESKS.put(player.getUUID(), new Desk(player.level().dimension(), pos.immutable()));
        Customer customer = customer(player);
        if (customer.error != null) {
            WrapperPlayer.of(player).message(Text.translatable(customer.error));
            return;
        }
        menu(player, customer, "", false);
    }

    public static void request(ServerPlayer player, int action, String region, int slot) {
        Customer customer = customer(player);
        if (action <= OPEN || action > SPINS.length) {
            if (customer.error == null) menu(player, customer, "", false);
            return;
        }
        if (customer.error != null) {
            menu(player, customer, customer.error, false);
            return;
        }
        if (!PlateRegions.known(region)) {
            menu(player, customer, "message.slimvehicle.plates.bad_region", false);
            return;
        }
        PlateNumber.Spin spin = SPINS[action - 1];
        PlateRegistry.Car car = customer.car;
        if (spin != PlateNumber.Spin.FULL && !car.numbered()) {
            menu(player, customer, "message.slimvehicle.plates.need_number", false);
            return;
        }
        if ((spin == PlateNumber.Spin.LETTER || spin == PlateNumber.Spin.DIGIT) && !PlateNumber.slotFits(car.style(), spin, slot)) {
            menu(player, customer, "message.slimvehicle.plates.bad_slot", false);
            return;
        }
        String target = spin == PlateNumber.Spin.REGION ? PlateNumber.region(car.plate()) : region;
        String next = PlateNumber.spin(car.plate(), car.style(), target, spin, slot, RANDOM, registry::taken);
        if (next == null) {
            menu(player, customer, "message.slimvehicle.plates.exhausted", false);
            return;
        }
        Economy economy = Economy.find();
        long price = prices.price(spin, car.numbered());
        boolean free = price == 0 || free(player, economy);
        if (!free) {
            if (economy == null) {
                menu(player, customer, "message.slimvehicle.plates.no_economy", false);
                return;
            }
            if (economy.balance(player) < price || !economy.withdraw(player, price)) {
                menu(player, customer, "message.slimvehicle.plates.no_money", false);
                return;
            }
        }
        registry.assign(customer.vehicle, next);
        save();
        deliver(player.server, customer.vehicle);
        SlimCore.LOGGER.info("{} paid {} for the number plate {} of vehicle {} (was {})", player.getGameProfile().getName(), free ? 0 : price, next, customer.vehicle,
                car.numbered() ? car.plate() : "none");
        menu(player, new Customer(customer.vehicle, registry.car(customer.vehicle), null), "message.slimvehicle.plates.done", true);
    }

    private static void deliver(MinecraftServer server, UUID vehicle) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(vehicle);
            EntityVehicleF_Physics found = BuilderVehicle.core(entity);
            if (found != null) {
                apply(found);
                return;
            }
        }
    }

    private static Customer customer(ServerPlayer player) {
        Desk desk = DESKS.get(player.getUUID());
        if (desk == null || desk.level != player.level().dimension() || !player.blockPosition().closerThan(desk.pos, REACH)
                || !(player.level().getBlockState(desk.pos).getBlock() instanceof PlateTerminalBlock)) {
            return new Customer(null, null, "message.slimvehicle.plates.not_here");
        }
        UUID vehicle = registry.lastDriven(player.getUUID());
        PlateRegistry.Car car = vehicle == null ? null : registry.car(vehicle);
        if (car == null) return new Customer(null, null, "message.slimvehicle.plates.no_car");
        if (!player.getUUID().equals(car.owner())) return new Customer(vehicle, car, "message.slimvehicle.plates.owner_only");
        if (car.style().isEmpty()) return new Customer(vehicle, car, "message.slimvehicle.plates.no_place");
        return new Customer(vehicle, car, null);
    }

    private static void menu(ServerPlayer player, Customer customer, String result, boolean ok) {
        JsonObject json = new JsonObject();
        json.addProperty("result", result);
        json.addProperty("ok", ok);
        if (customer.car != null) {
            json.addProperty("vehicle", customer.vehicle.toString());
            json.addProperty("style", customer.car.style().isEmpty() ? PlateNumber.STANDARD : customer.car.style());
            json.addProperty("plate", customer.car.plate());
        }
        Economy economy = Economy.find();
        json.addProperty("free", free(player, economy));
        if (economy != null) {
            double balance = economy.balance(player);
            json.addProperty("balance", balance);
            json.addProperty("balance_text", economy.format(balance));
        }
        boolean numbered = customer.car != null && customer.car.numbered();
        JsonObject price = new JsonObject();
        for (PlateNumber.Spin spin : SPINS) {
            long amount = prices.price(spin, numbered);
            JsonObject entry = new JsonObject();
            entry.addProperty("amount", amount);
            entry.addProperty("text", economy == null ? Long.toString(amount) : economy.format(amount));
            price.add(spin.name().toLowerCase(Locale.ROOT), entry);
        }
        json.add("prices", price);
        ServerPlayNetworking.send(player, new PacketPlateMenu(json.toString()));
    }

    private static boolean free(ServerPlayer player, Economy economy) {
        return economy == null && player.hasPermissions(2);
    }

    private static void save() {
        ShopFiles.write(file, registry.toJson(), "Number plates");
    }
}
