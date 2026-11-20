package studio.merami.slimcore.items;

import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.systems.VehicleSpawner;

public class ItemVehicle extends AItemBase {
    public final String vehicleId;

    public ItemVehicle(JSONVehicle vehicle) {
        super(new ResourceId(vehicle.namespace, vehicle.name), 1);
        this.vehicleId = vehicle.id;
    }

    public JSONVehicle definition() {
        return PackParser.vehicle(vehicleId);
    }

    @Override
    public Text displayName() {
        return definition().displayName();
    }

    @Override
    public boolean usedOnBlock() {
        return true;
    }

    @Override
    public boolean useOnBlock(IWrapperPlayer player, IWrapperItemStack stack, Point3D at) {
        EntityVehicleF_Physics vehicle = VehicleSpawner.spawn(player, definition(), at);
        if (vehicle == null) return false;
        if (!player.isCreative()) stack.shrink(1);
        return true;
    }
}
