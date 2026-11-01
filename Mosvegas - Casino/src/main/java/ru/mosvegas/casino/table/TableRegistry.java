package ru.mosvegas.casino.table;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Live tables of loaded chunks, per dimension. Server thread only. */
public final class TableRegistry {
    private static final Map<RegistryKey<World>, Map<BlockPos, BlackjackTable>> TABLES = new HashMap<>();

    private TableRegistry() {}

    static void register(BlackjackTable table) {
        TABLES.computeIfAbsent(table.serverWorld().getRegistryKey(), k -> new HashMap<>()).put(table.pos(), table);
    }

    static void unregister(BlackjackTable table) {
        Map<BlockPos, BlackjackTable> map = TABLES.get(table.serverWorld().getRegistryKey());
        if (map != null && map.get(table.pos()) == table) map.remove(table.pos());
    }

    public static BlackjackTable get(World world, BlockPos pos) {
        if (world == null || pos == null) return null;
        Map<BlockPos, BlackjackTable> map = TABLES.get(world.getRegistryKey());
        return map == null ? null : map.get(pos);
    }

    public static List<BlackjackTable> all() {
        List<BlackjackTable> list = new ArrayList<>();
        for (Map<BlockPos, BlackjackTable> map : TABLES.values()) list.addAll(map.values());
        return list;
    }

    /** The table where this player sits or bets, if any. */
    public static BlackjackTable tableOf(UUID player) {
        for (BlackjackTable t : all()) if (t.engine().participant(player) != null) return t;
        return null;
    }

    public static BlackjackTable nearest(ServerPlayerEntity player, double maxDistance) {
        BlackjackTable best = null;
        double bestDist = maxDistance * maxDistance;
        Map<BlockPos, BlackjackTable> map = TABLES.get(player.getWorld().getRegistryKey());
        if (map == null) return null;
        for (BlackjackTable t : map.values()) {
            double d = t.center().squaredDistanceTo(player.getPos());
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    public static void disposeAll() {
        for (BlackjackTable t : all()) t.dispose();
        TABLES.clear();
    }
}
