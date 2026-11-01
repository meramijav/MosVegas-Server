package ru.mosvegas.items;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MosVegasItems implements ModInitializer {
    public static final String MOD_ID = "mosvegas_items";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Map<String, MosVegasConsumableItem> ITEMS = new LinkedHashMap<>();
    private static final Map<String, MosVegasConsumableItem> ITEM_VIEW = Collections.unmodifiableMap(ITEMS);
    public static ItemGroup ITEM_GROUP;
    public static final EntityType<LitterEntity> LITTER = EntityType.Builder.<LitterEntity>create(LitterEntity::new, SpawnGroup.MISC)
            .setDimensions(0.25f, 0.25f).maxTrackingRange(6).trackingTickInterval(2)
            .disableSaving().disableSummon().build(MOD_ID + ":litter");

    @Override
    public void onInitialize() {
        MosVegasParticles.register();
        Registry.register(Registries.ENTITY_TYPE, new Identifier(MOD_ID, "litter"), LITTER);
        var definitions = ConsumableDefinitionLoader.load();
        ConsumableDefinitionLoader.generateItemModels(definitions);
        for (ConsumableDefinition definition : definitions) {
            Identifier identifier = new Identifier(MOD_ID, definition.id());
            MosVegasConsumableItem item = new MosVegasConsumableItem(
                    new Item.Settings().maxCount(definition.stackSize()), definition);
            Registry.register(Registries.ITEM, identifier, item);
            ITEMS.put(definition.id(), item);
        }
        ConsumableGroups.build(ITEMS.values());

        MosVegasConsumableItem iconItem = ITEMS.get("beer");
        if (iconItem == null && !ITEMS.isEmpty()) {
            iconItem = ITEMS.values().iterator().next();
        }
        MosVegasConsumableItem finalIconItem = iconItem;
        ITEM_GROUP = Registry.register(Registries.ITEM_GROUP, new Identifier(MOD_ID, "items"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup." + MOD_ID + ".items"))
                        .icon(() -> finalIconItem == null ? new ItemStack(net.minecraft.item.Items.APPLE) : new ItemStack(finalIconItem))
                        .entries((context, entries) -> ITEMS.values().forEach(item -> entries.add(new ItemStack(item),
                                ConsumableGroups.hiddenInTab(item) ? ItemGroup.StackVisibility.SEARCH_TAB_ONLY
                                        : ItemGroup.StackVisibility.PARENT_AND_SEARCH_TABS)))
                        .build());

        LOGGER.info("Loaded {} MosVegas consumables", ITEMS.size());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ITEMS.values().forEach(MosVegasConsumableItem::resolveRegistryReferences));
    }

    public static Map<String, MosVegasConsumableItem> items() {
        return ITEM_VIEW;
    }
}

