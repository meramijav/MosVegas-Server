package studio.merami.sliminterfacefabric.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import studio.merami.slimcore.sliminterface.IWrapperWorld;

public final class MosvegasCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("mosvegas_core");

    private MosvegasCompat() {}

    public static boolean allows(Level level, Entity player, IWrapperWorld.Access access, Entity vehicle) {
        if (!LOADED || !(level instanceof ServerLevel server)) return true;
        return MosvegasZones.allows(server, player instanceof ServerPlayer serverPlayer ? serverPlayer : null, access, vehicle);
    }

    public static boolean terrain(Level level, BlockPos pos) {
        return !LOADED || !(level instanceof ServerLevel server) || MosvegasZones.terrain(server, pos);
    }
}
