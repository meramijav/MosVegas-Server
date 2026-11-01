package ru.mosvegas.casino.client.state;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.casino.net.TableSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Tables the server told this client about. Client thread only. */
public final class ClientTables {
    private static final Map<BlockPos, ClientTable> TABLES = new HashMap<>();

    private ClientTables() {}

    public static ClientTable get(BlockPos pos) { return TABLES.get(pos); }
    public static Collection<ClientTable> all() { return TABLES.values(); }

    public static void update(TableSnapshot snapshot) {
        TABLES.computeIfAbsent(snapshot.pos(), p -> new ClientTable()).update(snapshot);
    }

    public static void tickAll(MinecraftClient mc) {
        for (ClientTable t : TABLES.values()) t.tick(mc);
    }

    public static void remove(BlockPos pos) { TABLES.remove(pos); }
    public static void clear() { TABLES.clear(); }

    /** The table where the local player sits or bets (not one they already left while its round finishes). */
    public static ClientTable mine() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        for (ClientTable t : TABLES.values()) {
            TableSnapshot.Player me = t.snapshot() == null ? null : t.snapshot().player(mc.player.getUuid());
            if (me != null && me.connected()) return t;
        }
        return null;
    }

    public static ClientTable nearest(double maxDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        ClientTable best = null;
        double bestDist = maxDistance * maxDistance;
        for (ClientTable t : TABLES.values()) {
            if (t.snapshot() == null) continue;
            double d = mc.player.getPos().squaredDistanceTo(t.snapshot().pos().toCenterPos());
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }
}
