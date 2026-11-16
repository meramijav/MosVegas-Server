package ru.mosvegas.items.rp;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.mosvegas.items.MosVegasItems;

import java.util.EnumMap;
import java.util.Map;

public final class RpItems {
    private static final Map<RpKind, RpItem> ITEMS = new EnumMap<>(RpKind.class);
    public static ItemGroup GROUP;

    private RpItems() {}

    public static void register() {
        for (RpKind kind : RpKind.values()) {
            RpItem item = new RpItem(new Item.Settings().maxCount(kind.stackSize), kind);
            Registry.register(Registries.ITEM, new Identifier(MosVegasItems.MOD_ID, kind.id), item);
            ITEMS.put(kind, item);
        }
        GROUP = Registry.register(Registries.ITEM_GROUP, new Identifier(MosVegasItems.MOD_ID, "rp"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup." + MosVegasItems.MOD_ID + ".rp"))
                        .icon(() -> new ItemStack(ITEMS.get(RpKind.POLICE_ID)))
                        .entries((context, entries) -> ITEMS.values().forEach(item -> entries.add(new ItemStack(item))))
                        .build());
        RpServer.register();
        MosVegasItems.LOGGER.info("Registered {} RP items", ITEMS.size());
    }

    public static RpItem item(RpKind kind) {
        return ITEMS.get(kind);
    }

    public static Iterable<RpItem> all() {
        return ITEMS.values();
    }
}
