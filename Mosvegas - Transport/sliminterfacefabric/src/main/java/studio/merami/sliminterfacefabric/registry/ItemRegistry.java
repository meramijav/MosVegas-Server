package studio.merami.sliminterfacefabric.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.items.AItemBase;
import studio.merami.slimcore.items.ItemVehicle;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.item.BuilderItem;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ItemRegistry {
    private static final List<Item> VEHICLES = new ArrayList<>();
    private static final Map<AItemBase, Item> ITEMS = new IdentityHashMap<>();

    private ItemRegistry() {}

    public static Item item(AItemBase item) {
        return ITEMS.get(item);
    }

    private static Item register(AItemBase item) {
        Item registered = Registry.register(BuiltInRegistries.ITEM, Convert.location(item.id), new BuilderItem(item));
        ITEMS.put(item, registered);
        return registered;
    }

    public static void register() {
        Item key = register(SlimItems.KEY);
        Item fuelCan = register(SlimItems.FUEL_CAN);
        Item repairKit = register(SlimItems.REPAIR_KIT);
        register(SlimItems.SPRAY_GUN);
        Item zoneMarker = register(SlimItems.ZONE_MARKER);
        Item paintStand = register(SlimItems.PAINT_STAND);
        for (Map.Entry<JSONVehicle, ItemVehicle> entry : SlimItems.createVehicleItems().entrySet()) {
            ResourceLocation id = Convert.location(entry.getValue().id);
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                SlimCore.LOGGER.error("Vehicle {} has no item: {} is already taken", entry.getKey().id, id);
                continue;
            }
            VEHICLES.add(register(entry.getValue()));
        }
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SlimVehicle.id("vehicles"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.slimvehicle"))
                .icon(() -> new ItemStack(VEHICLES.isEmpty() ? key : VEHICLES.get(0)))
                .displayItems((parameters, output) -> {
                    VEHICLES.forEach(output::accept);
                    output.accept(key);
                    output.accept(fuelCan);
                    output.accept(repairKit);
                    output.accept(zoneMarker);
                    output.accept(paintStand);
                    output.accept(BlockRegistry.PLATE_TERMINAL_ITEM);
                }).build());
    }
}
