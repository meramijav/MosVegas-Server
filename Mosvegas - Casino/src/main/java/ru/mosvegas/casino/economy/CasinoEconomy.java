package ru.mosvegas.casino.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Money provider used by every casino game. All calls run on the server thread. */
public interface CasinoEconomy {
    String name();

    long balance(ServerPlayerEntity player);

    /** Takes the amount or nothing at all. */
    boolean withdraw(ServerPlayerEntity player, long amount);

    void deposit(ServerPlayerEntity player, long amount);

    /** Pays a player who is offline (winnings of a round they left). */
    void depositOffline(MinecraftServer server, UUID player, long amount);

    String format(long amount);
}
