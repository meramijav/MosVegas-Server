package studio.merami.sliminterfacefabric.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

public final class TaczCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");

    private TaczCompat() {}

    public static void register() {
        if (LOADED) TaczEvents.register();
    }

    public static boolean driving(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        return vehicle != null && vehicle.driver(WrapperPlayer.of(player));
    }

    public static boolean holdsGun(Player player) {
        return LOADED && TaczEvents.holdsGun(player);
    }
}
