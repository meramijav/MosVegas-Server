package ru.mosvegas.character.data;

import net.minecraft.entity.player.PlayerEntity;
import ru.mosvegas.character.MosVegasCharacter;

import java.util.UUID;
import java.util.function.Function;

public final class AppearanceLookup {
    private static Function<UUID, Appearance> client = id -> null;

    private AppearanceLookup() {}

    public static void setClient(Function<UUID, Appearance> lookup) {
        client = lookup;
    }

    public static Appearance of(PlayerEntity player) {
        if (player == null || player.getWorld() == null || player.getUuid() == null) return null;
        return player.getWorld().isClient ? client.apply(player.getUuid()) : MosVegasCharacter.STORE.get(player.getUuid());
    }
}
