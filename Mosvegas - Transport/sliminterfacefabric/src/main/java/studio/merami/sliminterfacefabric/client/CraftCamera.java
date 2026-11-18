package studio.merami.sliminterfacefabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartSeat;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

public final class CraftCamera {
    private CraftCamera() {}

    public static Vec3 eye(Entity viewer, Vec3 upright, float partial) {
        if (viewer != Minecraft.getInstance().player) return null;
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(viewer);
        if (vehicle == null || !vehicle.craft()) return null;
        int index = vehicle.seatOf(WrapperEntity.of(viewer));
        if (index < 0) return null;
        PartSeat seat = vehicle.seat(index);
        if (seat.standing()) return null;
        Point3D hip = seat.pos().add(0, ChassisGeometry.riderThighRadius(vehicle.definition()), 0);
        Point3D offset = Convert.point(upright).subtract(vehicle.chassisToWorld(hip, partial));
        Point3D local = vehicle.directionToLocal(offset).scale(1 / vehicle.scale());
        return Convert.vec(vehicle.chassisToWorld(hip.add(local), partial));
    }
}
