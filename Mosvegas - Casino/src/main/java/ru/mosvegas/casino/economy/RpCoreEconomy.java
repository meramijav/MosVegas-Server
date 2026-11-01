package ru.mosvegas.casino.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.mosvegas.core.data.DataStore;
import ru.mosvegas.core.service.EconomyService;
import ru.mosvegas.core.util.RpItems;

import java.util.UUID;

/**
 * Mosvegas Core (RpCore) money: the bank account ({@code EconomyService.debit/credit}) or physical
 * cash notes ({@code RpItems.takeCash/giveCash}). Loaded only when {@code mosvegas_core} is installed.
 */
final class RpCoreEconomy implements CasinoEconomy {
    enum Source { BANK, CASH, CASH_THEN_BANK }

    private final Source source;

    RpCoreEconomy(String source) {
        this.source = switch (source == null ? "bank" : source.toLowerCase()) {
            case "cash" -> Source.CASH;
            case "cash_then_bank", "cash_or_bank" -> Source.CASH_THEN_BANK;
            default -> Source.BANK;
        };
    }

    @Override public String name() { return "Mosvegas Core (" + source.name().toLowerCase() + ")"; }

    @Override
    public long balance(ServerPlayerEntity player) {
        long bank = DataStore.player(player.getUuid()).bankBalance;
        return switch (source) {
            case BANK -> bank;
            case CASH -> RpItems.cashBalance(player);
            case CASH_THEN_BANK -> safeAdd(RpItems.cashBalance(player), bank);
        };
    }

    @Override
    public boolean withdraw(ServerPlayerEntity player, long amount) {
        if (amount <= 0) return false;
        return switch (source) {
            case BANK -> EconomyService.debit(player, amount);
            case CASH -> RpItems.takeCash(player, amount);
            case CASH_THEN_BANK -> RpItems.takeCash(player, amount) || EconomyService.debit(player, amount);
        };
    }

    @Override
    public void deposit(ServerPlayerEntity player, long amount) {
        if (amount <= 0) return;
        if (source != Source.BANK && RpItems.giveCash(player, amount)) return;
        EconomyService.credit(player, amount);
    }

    @Override
    public void depositOffline(MinecraftServer server, UUID player, long amount) {
        if (amount <= 0) return;
        DataStore.player(player).bankBalance += amount;
        DataStore.save();
    }

    @Override public String format(long amount) { return EconomyService.money(amount); }

    private static long safeAdd(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
}
