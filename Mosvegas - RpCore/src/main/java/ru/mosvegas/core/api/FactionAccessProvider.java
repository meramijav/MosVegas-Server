package ru.mosvegas.core.api;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Set;

/** Called on the server thread. Return stable faction IDs, without network or disk operations. */
@FunctionalInterface
public interface FactionAccessProvider {
    Set<String> factions(ServerPlayerEntity player);
}
