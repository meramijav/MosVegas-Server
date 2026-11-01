package studio.merami.slimcore.items;

import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.instances.AEntityVehicleE_Powered;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;

import java.util.UUID;

public class ItemKey extends AItemBase {
    public static final double REACH = 32;
    public static final String VEHICLE = "Vehicle";

    public ItemKey() {
        super(SlimCore.id("key"), 1);
    }

    public static UUID vehicleOf(IWrapperItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.item() instanceof ItemKey) || !stack.hasData() || !stack.data().hasUUID(VEHICLE)) return null;
        return stack.data().getUUID(VEHICLE);
    }

    public static AEntityVehicleE_Powered find(IWrapperPlayer player, UUID vehicle) {
        Point3D at = player.position();
        BoundingBox area = new BoundingBox(at.x - REACH, at.y - REACH, at.z - REACH, at.x + REACH, at.y + REACH, at.z + REACH);
        AEntityVehicleE_Powered nearest = null;
        for (AEntityA_Base entity : player.world().coreEntities(area)) {
            if (!(entity instanceof AEntityVehicleE_Powered car) || !car.isAlive() || !car.uuid().equals(vehicle) || car.distanceToSqr(at) > REACH * REACH) continue;
            if (nearest == null || car.distanceToSqr(at) < nearest.distanceToSqr(at)) nearest = car;
        }
        return nearest;
    }

    @Override
    public boolean usedInAir() {
        return true;
    }

    @Override
    public boolean useInAir(IWrapperPlayer player, IWrapperItemStack stack) {
        UUID vehicle = vehicleOf(stack);
        if (vehicle == null) {
            player.message(Text.translatable("message.slimvehicle.key_blank"));
            return true;
        }
        AEntityVehicleE_Powered car = find(player, vehicle);
        if (car == null) {
            player.message(Text.translatable("message.slimvehicle.key_out_of_range"));
            return true;
        }
        car.toggleLock(player);
        return true;
    }
}
