package ru.mosvegas.core.api;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import ru.mosvegas.core.zone.ZoneAction;

/** Boarding/controls/spawning/damage permissions; never a restriction on travelling through the city. */
public final class TransportAccess {
    private TransportAccess() {}
    public static boolean canEnter(ServerPlayerEntity player, Entity vehicle) { return vehicle(player, vehicle, ZoneAction.VEHICLE_ENTER); }
    public static boolean canUse(ServerPlayerEntity player, Entity vehicle) { return vehicle(player, vehicle, ZoneAction.VEHICLE_USE); }
    public static boolean canDamage(ServerPlayerEntity actor, Entity vehicle) { return vehicle(actor, vehicle, ZoneAction.VEHICLE_DAMAGE); }
    public static boolean canPlace(ServerPlayerEntity actor, ServerWorld world, Box footprint) { return footprint(actor, world, footprint, ZoneAction.VEHICLE_PLACE); }
    public static boolean canOpenStorage(ServerPlayerEntity actor, Entity vehicle) { return vehicle(actor, vehicle, ZoneAction.CONTAINERS); }
    private static boolean vehicle(ServerPlayerEntity actor, Entity entity, ZoneAction action) {
        return entity.getWorld() instanceof ServerWorld world && footprint(actor, world, entity.getBoundingBox(), action);
    }
    private static boolean footprint(ServerPlayerEntity actor, ServerWorld world, Box box, ZoneAction action) {
        if (!Double.isFinite(box.minX + box.minY + box.minZ + box.maxX + box.maxY + box.maxZ)) return false;
        if (Math.abs(box.minX) > 30_000_000 || Math.abs(box.maxX) > 30_000_000
                || Math.abs(box.minZ) > 30_000_000 || Math.abs(box.maxZ) > 30_000_000
                || box.minY < -20_000_000 || box.maxY > 20_000_000) return false;
        int minX = MathHelper.floor(box.minX), minY = MathHelper.floor(box.minY), minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(Math.nextDown(box.maxX)), maxY = MathHelper.floor(Math.nextDown(box.maxY)), maxZ = MathHelper.floor(Math.nextDown(box.maxZ));
        if ((long)maxX - minX >= 4096 || (long)maxY - minY >= 4096 || (long)maxZ - minZ >= 4096) return false;
        long volume = (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (maxX < minX || maxY < minY || maxZ < minZ || volume <= 0 || volume > 4096) return false;
        for (BlockPos pos : BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ)) if (!ZoneAccess.can(actor, world, pos, action)) return false;
        return true;
    }
}
