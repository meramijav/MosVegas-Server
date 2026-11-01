package ru.mosvegas.core.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.mosvegas.core.data.DataStore;

import java.util.*;

public final class Permissions {
    private Permissions() {}

    public static boolean has(ServerCommandSource source, String node, int fallbackLevel) {
        if (source.getEntity() == null) return true;
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return source.hasPermissionLevel(fallbackLevel);
        return has(player, node, fallbackLevel);
    }

    public static boolean has(ServerPlayerEntity player, String node, int fallbackLevel) {
        Set<String> nodes = DataStore.permissions().getOrDefault(player.getUuidAsString(), Set.of());
        if (nodes.contains("*") || nodes.contains(node)) return true;
        String current = node;
        while (current.contains(".")) {
            current = current.substring(0, current.lastIndexOf('.'));
            if (nodes.contains(current + ".*")) return true;
        }
        return player.hasPermissionLevel(fallbackLevel);
    }

    public static boolean hasAny(ServerCommandSource source,int fallbackLevel,String... nodes){for(String node:nodes)if(has(source,node,fallbackLevel))return true;return false;}
    public static boolean hasAny(ServerPlayerEntity player,int fallbackLevel,String... nodes){for(String node:nodes)if(has(player,node,fallbackLevel))return true;return false;}

    public static void grant(UUID player, String node) {
        DataStore.permissions().computeIfAbsent(player.toString(), ignored -> new LinkedHashSet<>()).add(node);
        DataStore.save();
    }

    public static void revoke(UUID player, String node) {
        Set<String> nodes = DataStore.permissions().get(player.toString());
        if (nodes != null) nodes.remove(node);
        DataStore.save();
    }
}
