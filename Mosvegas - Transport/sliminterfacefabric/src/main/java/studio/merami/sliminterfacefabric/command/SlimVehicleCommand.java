package studio.merami.sliminterfacefabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.baseclasses.BlockPosition;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.systems.RadioLibrary;
import studio.merami.slimcore.systems.TestArena;
import studio.merami.slimcore.systems.VehicleSpawner;
import studio.merami.sliminterfacefabric.network.RadioServer;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class SlimVehicleCommand {
    private SlimVehicleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("slimvehicle")
                .then(Commands.literal("spawn").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("vehicle", StringArgumentType.greedyString()).suggests(SlimVehicleCommand::suggestVehicles)
                                .executes(SlimVehicleCommand::spawn)))
                .then(Commands.literal("key").executes(SlimVehicleCommand::key))
                .then(Commands.literal("trust").then(Commands.argument("player", EntityArgument.player()).executes(context -> trust(context, true))))
                .then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player()).executes(context -> trust(context, false))))
                .then(Commands.literal("tint").then(Commands.argument("percent", IntegerArgumentType.integer(0, 95)).executes(SlimVehicleCommand::tint)))
                .then(Commands.literal("paint").then(Commands.argument("rgb", StringArgumentType.word()).executes(SlimVehicleCommand::paint)))
                .then(Commands.literal("repair").requires(source -> source.hasPermission(2)).executes(SlimVehicleCommand::repair))
                .then(Commands.literal("condition").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(SlimVehicleCommand::condition)))
                .then(Commands.literal("remove").requires(source -> source.hasPermission(2)).executes(SlimVehicleCommand::remove))
                .then(Commands.literal("packs").executes(SlimVehicleCommand::packs))
                .then(Commands.literal("radio").executes(SlimVehicleCommand::radio))
                .then(Commands.literal("testarena").requires(source -> source.hasPermission(2)).executes(SlimVehicleCommand::testArena))
                .then(Commands.literal("help").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("command.slimvehicle.help"), false);
                    return 1;
                })));
    }

    private static CompletableFuture<Suggestions> suggestVehicles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (JSONVehicle vehicle : PackParser.vehicles()) {
            builder.suggest(vehicle.id);
            builder.suggest(vehicle.name);
        }
        return builder.buildFuture();
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "vehicle").trim();
        Optional<JSONVehicle> vehicle = PackParser.findVehicle(id);
        if (vehicle.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.slimvehicle.unknown_vehicle", id));
            return 0;
        }
        return VehicleSpawner.spawn(WrapperPlayer.of(player), vehicle.get(), Convert.point(player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4)))) == null ? 0 : 1;
    }

    private static EntityVehicleF_Physics owned(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EntityVehicleF_Physics vehicle = VehicleSpawner.nearby(WrapperPlayer.of(player));
        return vehicle != null && vehicle.isOwner(WrapperPlayer.of(player)) ? vehicle : null;
    }

    private static int key(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityVehicleF_Physics vehicle = owned(context);
        if (vehicle == null) return 0;
        VehicleSpawner.giveKey(WrapperPlayer.of(context.getSource().getPlayerOrException()), vehicle);
        return 1;
    }

    private static int trust(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        EntityVehicleF_Physics vehicle = owned(context);
        if (vehicle == null) return 0;
        vehicle.trust(EntityArgument.getPlayer(context, "player").getUUID(), value);
        AEntityA_Base.message(WrapperPlayer.of(context.getSource().getPlayerOrException()), value ? "message.slimvehicle.trusted" : "message.slimvehicle.revoked");
        return 1;
    }

    private static int tint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityVehicleF_Physics vehicle = owned(context);
        if (vehicle == null) return 0;
        vehicle.setTint(IntegerArgumentType.getInteger(context, "percent"));
        return 1;
    }

    private static int paint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityVehicleF_Physics vehicle = owned(context);
        if (vehicle == null) return 0;
        try {
            vehicle.setColor(Integer.parseInt(StringArgumentType.getString(context, "rgb").replace("#", ""), 16));
            return 1;
        } catch (NumberFormatException e) {
            AEntityA_Base.message(WrapperPlayer.of(context.getSource().getPlayerOrException()), "message.slimvehicle.paint_format");
            return 0;
        }
    }

    private static int repair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EntityVehicleF_Physics vehicle = VehicleSpawner.nearby(WrapperPlayer.of(player));
        if (vehicle == null) return 0;
        vehicle.repair();
        AEntityA_Base.message(WrapperPlayer.of(player), "message.slimvehicle.repaired");
        return 1;
    }

    private static int condition(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EntityVehicleF_Physics vehicle = VehicleSpawner.nearby(WrapperPlayer.of(player));
        if (vehicle == null) return 0;
        int percent = IntegerArgumentType.getInteger(context, "percent");
        vehicle.setCondition(percent);
        AEntityA_Base.message(WrapperPlayer.of(player), "message.slimvehicle.condition", percent);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityVehicleF_Physics vehicle = VehicleSpawner.nearby(WrapperPlayer.of(context.getSource().getPlayerOrException()));
        if (vehicle == null || !vehicle.passengers().isEmpty()) return 0;
        vehicle.remove();
        return 1;
    }

    private static int packs(CommandContext<CommandSourceStack> context) {
        for (PackParser.Pack pack : PackParser.packs()) {
            String vehicles = pack.vehicles().stream().map(vehicle -> vehicle.id).collect(Collectors.joining(", "));
            context.getSource().sendSuccess(() -> Component.translatable("command.slimvehicle.pack", pack.meta().name, vehicles), false);
        }
        return PackParser.packs().size();
    }

    private static int radio(CommandContext<CommandSourceStack> context) {
        List<RadioLibrary.Station> stations = RadioServer.stations();
        if (stations.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("command.slimvehicle.radio.none"), false);
            return 0;
        }
        int tracks = stations.stream().mapToInt(station -> station.tracks().size()).sum();
        String megabytes = String.format(Locale.ROOT, "%.1f", stations.stream().mapToLong(RadioLibrary.Station::size).sum() / 1048576.0);
        context.getSource().sendSuccess(() -> Component.translatable("command.slimvehicle.radio.summary", stations.size(), tracks, megabytes), false);
        for (RadioLibrary.Station station : stations) {
            Component name = station.name().equals(RadioLibrary.OWN) ? Component.translatable("radio.slimvehicle.server") : Component.literal(station.name());
            context.getSource().sendSuccess(() -> Component.translatable("command.slimvehicle.radio.station", name, station.tracks().size(), Math.round(station.seconds() / 60)), false);
        }
        return stations.size();
    }

    private static int testArena(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        BlockPosition origin = new BlockPosition(feet.getX(), feet.getY(), feet.getZ());
        Map<String, BlockState> states = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (TestArena.Fill fill : TestArena.layout(origin)) {
            BlockState state = states.computeIfAbsent(fill.block(), id -> parse(level, id));
            for (int x = fill.minX(); x <= fill.maxX(); x++) {
                for (int z = fill.minZ(); z <= fill.maxZ(); z++) {
                    for (int y = fill.minY(); y <= fill.maxY(); y++) {
                        pos.set(x, y, z);
                        if (level.getBlockState(pos) == state) continue;
                        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        changed++;
                    }
                }
            }
        }
        Point3D pier = TestArena.pier(origin);
        player.teleportTo(level, pier.x, pier.y, pier.z, -90, 0);
        int blocks = changed;
        context.getSource().sendSuccess(() -> Component.translatable("command.slimvehicle.testarena.built", blocks), true);
        return 1;
    }

    private static BlockState parse(ServerLevel level, String id) {
        try {
            return BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), id, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(id, e);
        }
    }
}
