package studio.merami.slimcore.systems;

import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.components.AEntityE_Interactable;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.items.ItemKey;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.sliminterface.InterfaceManager;

import java.util.Comparator;
import java.util.Optional;

public final class VehicleSpawner {
    private VehicleSpawner() {}

    public static EntityVehicleF_Physics spawn(IWrapperPlayer player, JSONVehicle definition, Point3D pos) {
        IWrapperWorld world = player.world();
        EntityVehicleF_Physics vehicle = world.createVehicle();
        if (vehicle == null) return null;
        vehicle.setDefinition(definition.id);
        vehicle.setOwner(player.uuid());
        vehicle.moveTo(pos.x, pos.y, pos.z, player.yaw(), 0);
        vehicle.keepClearOf(player);
        if (!vehicle.canSpawnAtCurrentPosition()) {
            AEntityA_Base.message(player, "message.slimvehicle.no_room");
            return null;
        }
        vehicle.settleOnGround(96);
        Optional<Point3D> safe = vehicle.spawnClearance(player);
        if (safe.isEmpty()) {
            AEntityA_Base.message(player, "message.slimvehicle.no_room_for_player");
            return null;
        }
        if (!world.allows(player, IWrapperWorld.Access.PLACE, vehicle)) {
            AEntityA_Base.message(player, "message.slimvehicle.zone.place");
            return null;
        }
        if (!world.addVehicle(vehicle)) return null;
        Point3D outside = safe.get();
        if (outside.distanceToSqr(player.position()) > .001) {
            player.teleport(outside);
            player.setVelocity(Point3D.ZERO);
            player.resetFallDistance();
        }
        giveKey(player, vehicle);
        return vehicle;
    }

    public static void giveKey(IWrapperPlayer player, AEntityE_Interactable<?> vehicle) {
        IWrapperItemStack key = InterfaceManager.coreInterface.createStack(SlimItems.KEY);
        key.dataOrCreate().setUUID(ItemKey.VEHICLE, vehicle.uuid());
        key.setName(Text.translatable("item.slimvehicle.key.named", vehicle.definition().displayName()));
        player.give(key);
    }

    public static EntityVehicleF_Physics nearby(IWrapperPlayer player) {
        if (player.vehicle() instanceof EntityVehicleF_Physics vehicle) return vehicle;
        return player.world().coreEntities(player.bounds().inflate(6)).stream()
                .filter(EntityVehicleF_Physics.class::isInstance).map(EntityVehicleF_Physics.class::cast)
                .min(Comparator.comparingDouble(vehicle -> player.distanceToSqr(vehicle.host))).orElse(null);
    }
}
