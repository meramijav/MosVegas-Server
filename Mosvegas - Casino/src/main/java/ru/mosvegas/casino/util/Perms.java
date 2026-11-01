package ru.mosvegas.casino.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import ru.mosvegas.casino.config.ConfigManager;

/** Admin check: a Mosvegas Core permission node when the core is installed, otherwise the OP level. */
public final class Perms {
    private static final boolean CORE = FabricLoader.getInstance().isModLoaded("mosvegas_core");

    private Perms() {}

    public static boolean admin(ServerCommandSource source) {
        int level = ConfigManager.get().adminOpLevel;
        if (source.hasPermissionLevel(level)) return true;
        if (CORE && source.getPlayer() != null) {
            try {
                return CorePerms.has(source, ConfigManager.get().adminPermission, level);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static final class CorePerms {
        static boolean has(ServerCommandSource source, String node, int level) {
            return ru.mosvegas.core.util.Permissions.has(source, node, level);
        }
    }
}
