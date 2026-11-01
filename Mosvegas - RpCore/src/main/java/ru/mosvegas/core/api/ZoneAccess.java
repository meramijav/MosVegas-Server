package ru.mosvegas.core.api;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.core.zone.*;

/** Public integration API. Call before changing server state, including inside custom packet handlers. */
public final class ZoneAccess {
    public static final int API_VERSION = 1;
    private ZoneAccess() {}
    public static boolean can(ServerPlayerEntity actor, ServerWorld world, BlockPos pos, ZoneAction action) {
        return ZoneService.can(actor, world, pos, action);
    }
    public static boolean canModifyTerrain(ServerWorld world, BlockPos pos) {
        return ZoneService.environment(world, pos, ZoneAction.TERRAIN);
    }
    public static void registerFactionProvider(FactionAccessProvider provider) { ZoneService.addFactionProvider(provider); }
}
