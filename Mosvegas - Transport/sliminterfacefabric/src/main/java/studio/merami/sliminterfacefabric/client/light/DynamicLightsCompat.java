package studio.merami.sliminterfacefabric.client.light;

import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.registry.EntityRegistry;

public class DynamicLightsCompat implements DynamicLightsInitializer {
    private static final int HEADLIGHTS = 12;
    private static final int FIRE = 15;

    @Override
    public void onInitializeDynamicLights() {
        DynamicLightHandlers.registerDynamicLightHandler(EntityRegistry.VEHICLE, builder -> luminance(builder.vehicle()));
    }

    static int luminance(EntityVehicleF_Physics vehicle) {
        if (VehicleLights.active()) return 0;
        JSONVehicle definition = vehicle.definition();
        float fire = vehicle.fire();
        if (fire > 0 && definition.hasEngineBay()) return Math.round(FIRE * Math.min(1, .5f + fire));
        if (definition.lamps == null || definition.lamps.headlight == null || !vehicle.flag(EntityVehicleF_Physics.LIGHTS)) return 0;
        boolean left = !vehicle.lampBroken(EntityVehicleF_Physics.HEADLIGHT_LEFT);
        boolean right = !vehicle.lampBroken(EntityVehicleF_Physics.HEADLIGHT_RIGHT);
        return left && right ? HEADLIGHTS : left || right ? HEADLIGHTS - 2 : 0;
    }
}
