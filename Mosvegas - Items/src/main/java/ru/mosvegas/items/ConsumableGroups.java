package ru.mosvegas.items;

import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Items with the same {@code group} share one creative slot. The first of them in tab order
 * represents the group; clicking it lists every variant. A group of one is an ordinary item.
 */
public final class ConsumableGroups {
    private static Map<Item, List<MosVegasConsumableItem>> variants = Map.of();
    private static Map<Item, String> groups = Map.of();

    private ConsumableGroups() {}

    static void build(Collection<MosVegasConsumableItem> items) {
        Map<String, List<MosVegasConsumableItem>> members = new LinkedHashMap<>();
        for (MosVegasConsumableItem item : items) {
            String group = item.definition().group();
            if (!group.isEmpty()) members.computeIfAbsent(group, key -> new ArrayList<>()).add(item);
        }
        Map<Item, List<MosVegasConsumableItem>> variants = new HashMap<>();
        Map<Item, String> groups = new HashMap<>();
        for (var entry : members.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            List<MosVegasConsumableItem> list = List.copyOf(entry.getValue());
            variants.put(list.get(0), list);
            for (MosVegasConsumableItem item : list) groups.put(item, entry.getKey());
        }
        ConsumableGroups.variants = Map.copyOf(variants);
        ConsumableGroups.groups = Map.copyOf(groups);
    }

    /** Every variant behind a representative's slot, or an empty list for any other item. */
    public static List<MosVegasConsumableItem> variants(Item item) {
        return variants.getOrDefault(item, List.of());
    }

    /** Group ID of a grouped item, or an empty string. */
    public static String group(Item item) {
        return groups.getOrDefault(item, "");
    }

    /** Variants other than the representative appear only in creative search. */
    public static boolean hiddenInTab(Item item) {
        return groups.containsKey(item) && !variants.containsKey(item);
    }
}
