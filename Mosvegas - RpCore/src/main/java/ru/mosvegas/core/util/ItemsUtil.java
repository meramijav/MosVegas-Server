package ru.mosvegas.core.util;

import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ItemsUtil {
    private ItemsUtil() {}

    public static Item byId(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? Items.AIR : Registries.ITEM.get(identifier);
    }

    public static boolean take(ServerPlayerEntity player, Item item, int count) {
        int present = 0;
        for (int i=0; i<player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) present += stack.getCount();
        }
        if (present < count) return false;
        int left = count;
        for (int i=0; i<player.getInventory().size() && left>0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(item)) continue;
            int remove = Math.min(left, stack.getCount());
            stack.decrement(remove);
            left -= remove;
        }
        return true;
    }

    public static void give(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }
}
