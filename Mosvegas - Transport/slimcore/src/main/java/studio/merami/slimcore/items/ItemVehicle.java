package studio.merami.slimcore.items;

import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONCraft;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.systems.VehicleSpawner;

public class ItemVehicle extends AItemBase {
    private static final double WATER_REACH = 6;
    private static final double WATER_STEP = .25;
    private static final double WATER_DEPTH = 8;
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
    public boolean usedInAir() {
        return watercraft();
    }

    @Override
    public boolean useInAir(IWrapperPlayer player, IWrapperItemStack stack) {
        Point3D water = aimedWater(player);
        return water != null && place(player, stack, water);
    }

    @Override
    public boolean useOnBlock(IWrapperPlayer player, IWrapperItemStack stack, Point3D at) {
        return place(player, stack, watercraft() ? afloat(player.world(), at) : at);
    }

    private boolean watercraft() {
        JSONCraft craft = definition().craft;
        return craft != null && craft.watercraft();
    }

    private static Point3D afloat(IWrapperWorld world, Point3D at) {
        double surface = world.waterSurface(at.x, at.z, at.y - 1, at.y + WATER_DEPTH);
        return Double.isNaN(surface) ? at : new Point3D(at.x, surface, at.z);
    }

    private static Point3D aimedWater(IWrapperPlayer player) {
        IWrapperWorld world = player.world();
        Point3D eye = player.eyePosition();
        Point3D look = player.lookAngle().normalize();
        for (double t = WATER_STEP; t <= WATER_REACH; t += WATER_STEP) {
            Point3D point = eye.add(look.scale(t));
            if (world.raycastBlocks(eye, point, player).hit()) return null;
            double surface = world.waterSurface(point.x, point.z, point.y, point.y);
            if (!Double.isNaN(surface) && point.y <= surface) return afloat(world, new Point3D(Math.floor(point.x) + .5, Math.floor(point.y), Math.floor(point.z) + .5));
        }
        return null;
    }

    private boolean place(IWrapperPlayer player, IWrapperItemStack stack, Point3D at) {
        EntityVehicleF_Physics vehicle = VehicleSpawner.spawn(player, definition(), at);
        if (vehicle == null) return false;
        if (!player.isCreative()) stack.shrink(1);
        return true;
    }
}
