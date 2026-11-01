package ru.mosvegas.casino.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory wallet for testing without Mosvegas Core. Balances reset on restart. */
final class DevEconomy implements CasinoEconomy {
    private final Map<UUID, Long> balances = new HashMap<>();
    private final long start;
    private final String currency;

    DevEconomy(long start, String currency) {
        this.start = start;
        this.currency = currency == null ? "" : currency;
    }

    private long get(UUID id) { return balances.computeIfAbsent(id, k -> start); }

    @Override public String name() { return "dev wallet"; }
    @Override public long balance(ServerPlayerEntity player) { return get(player.getUuid()); }

    @Override
    public boolean withdraw(ServerPlayerEntity player, long amount) {
        long have = get(player.getUuid());
        if (amount <= 0 || have < amount) return false;
        balances.put(player.getUuid(), have - amount);
        return true;
    }

    @Override public void deposit(ServerPlayerEntity player, long amount) { balances.put(player.getUuid(), get(player.getUuid()) + amount); }
    @Override public void depositOffline(MinecraftServer server, UUID player, long amount) { balances.put(player, get(player) + amount); }

    @Override
    public String format(long amount) {
        return String.format("%,d", amount).replace(',', ' ') + " " + currency;
    }

    void set(UUID player, long amount) { balances.put(player, amount); }
}
