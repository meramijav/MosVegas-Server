package ru.mosvegas.casino.economy;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.config.CasinoConfig;

import java.util.UUID;

/** Picks the money provider from the config; game code only talks to this facade. */
public final class Economy {
    private static CasinoEconomy provider;

    private Economy() {}

    public static void configure(CasinoConfig config) {
        String mode = config.economy.provider == null ? "auto" : config.economy.provider.toLowerCase();
        boolean core = FabricLoader.getInstance().isModLoaded("mosvegas_core");
        if ((mode.equals("auto") || mode.equals("rpcore")) && core) {
            provider = new RpCoreEconomy(config.economy.source);
        } else {
            if (mode.equals("rpcore")) MosVegasCasino.LOGGER.error("economy.provider=rpcore, но мод mosvegas_core не установлен: используется тестовый кошелёк");
            provider = new DevEconomy(config.economy.devStartBalance, config.economy.devCurrency);
        }
        MosVegasCasino.LOGGER.info("Экономика казино: {}", provider.name());
    }

    public static CasinoEconomy get() {
        if (provider == null) throw new IllegalStateException("Economy not configured");
        return provider;
    }

    public static long balance(ServerPlayerEntity player) { return get().balance(player); }
    public static boolean withdraw(ServerPlayerEntity player, long amount) { return get().withdraw(player, amount); }
    public static String format(long amount) { return get().format(amount); }

    public static void deposit(MinecraftServer server, UUID id, long amount) {
        if (amount <= 0) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player != null) get().deposit(player, amount);
        else get().depositOffline(server, id, amount);
    }

    public static boolean isDev() { return provider instanceof DevEconomy; }

    public static void devSet(UUID player, long amount) {
        if (provider instanceof DevEconomy dev) dev.set(player, amount);
    }
}
