package studio.merami.slimcore.items;

import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.PackParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SlimItems {
    public static final ItemKey KEY = new ItemKey();
    public static final AItemBase FUEL_CAN = new AItemBase(SlimCore.id("fuel_can"), 1);
    public static final AItemBase REPAIR_KIT = new AItemBase(SlimCore.id("repair_kit"), 16);

    private static Map<JSONVehicle, ItemVehicle> vehicles = Map.of();

    private SlimItems() {}

    public static Map<JSONVehicle, ItemVehicle> createVehicleItems() {
        Map<JSONVehicle, ItemVehicle> items = new LinkedHashMap<>();
        for (JSONVehicle vehicle : PackParser.vehicles()) {
            if (vehicle.item) items.put(vehicle, new ItemVehicle(vehicle));
        }
        vehicles = Collections.unmodifiableMap(items);
        return vehicles;
    }

    public static Map<JSONVehicle, ItemVehicle> vehicles() {
        return vehicles;
    }
}
