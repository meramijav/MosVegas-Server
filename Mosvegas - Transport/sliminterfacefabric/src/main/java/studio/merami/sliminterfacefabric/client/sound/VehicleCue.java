package studio.merami.sliminterfacefabric.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;

public class VehicleCue extends AbstractTickableSoundInstance {
    private final EntityVehicleF_Physics vehicle;

    public VehicleCue(EntityVehicleF_Physics vehicle, SoundEvent event, boolean inside) {
        super(event, SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        looping = false;
        delay = 0;
        volume = 1;
        if (inside) {
            relative = true;
            attenuation = Attenuation.NONE;
        } else {
            follow();
        }
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) {
            stop();
            return;
        }
        if (!relative) follow();
    }

    private void follow() {
        Point3D at = vehicle.enginePosition();
        x = at.x;
        y = at.y;
        z = at.z;
    }
}
