package studio.merami.sliminterfacefabric.shop;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.paint.PaintStations;
import studio.merami.sliminterfacefabric.network.PacketPaintStations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.UnaryOperator;

final class StationStore {
    private static final String FILE = "paint_stations.json";
    private static PaintStations current = PaintStations.EMPTY;
    private static MinecraftServer owner;
    private static Path file;

    private StationStore() {}

    static void load(MinecraftServer server) {
        owner = server;
        file = ShopFiles.file(server, FILE);
        current = PaintStations.EMPTY;
        if (!Files.exists(file)) return;
        try {
            current = PaintStations.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Path broken = file.resolveSibling(FILE + ".broken");
            SlimCore.LOGGER.error("Paint stations were not read from {}; it is moved to {} and no station is active", file, broken, e);
            try {
                Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailed) {
                SlimCore.LOGGER.error("Unreadable paint stations file {} could not be moved aside", file, moveFailed);
            }
        }
    }

    static void clear() {
        current = PaintStations.EMPTY;
        owner = null;
        file = null;
    }

    static PaintStations get() {
        return current;
    }

    static String world(Level level) {
        return level.dimension().location().toString();
    }

    static PaintStations update(UnaryOperator<PaintStations> change) {
        PaintStations next = change.apply(current);
        if (next == current) return next;
        current = next;
        ShopFiles.write(file, next.toJson(), "Paint stations");
        for (ServerPlayer player : owner.getPlayerList().getPlayers()) send(player);
        return next;
    }

    static void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, PacketPaintStations.TYPE)) ServerPlayNetworking.send(player, new PacketPaintStations(current.inWorld(world(player.level())).toJson()));
    }

    static void clearBlocks(PaintStations.Station station) {
        for (ServerLevel level : owner.getAllLevels()) {
            if (!world(level).equals(station.world())) continue;
            for (PaintStations.Stand stand : station.stands()) clearBlock(level, stand);
        }
    }

    static void clearBlock(ServerLevel level, PaintStations.Stand stand) {
        BlockPos pos = new BlockPos(stand.x(), stand.y(), stand.z());
        if (level.getBlockState(pos).is(Blocks.BARRIER)) level.removeBlock(pos, false);
    }
}
