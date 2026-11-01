package studio.merami.sliminterfacefabric.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import ru.mosvegas.core.api.TransportAccess;
import ru.mosvegas.core.api.ZoneAccess;
import studio.merami.slimcore.sliminterface.IWrapperWorld;

final class MosvegasZones {
    private MosvegasZones() {}

    static boolean allows(ServerLevel level, ServerPlayer player, IWrapperWorld.Access access, Entity vehicle) {
        return switch (access) {
            case ENTER -> TransportAccess.canEnter(player, vehicle);
            case USE -> TransportAccess.canUse(player, vehicle);
            case PLACE -> TransportAccess.canPlace(player, level, vehicle.getBoundingBox());
            case STORAGE -> TransportAccess.canOpenStorage(player, vehicle);
            case DAMAGE -> TransportAccess.canDamage(player, vehicle);
        };
    }

    static boolean terrain(ServerLevel level, BlockPos pos) {
        return ZoneAccess.canModifyTerrain(level, pos);
    }
}
