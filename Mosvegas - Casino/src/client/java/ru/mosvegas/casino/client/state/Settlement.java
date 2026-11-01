package ru.mosvegas.casino.client.state;

import ru.mosvegas.casino.game.blackjack.SideBet;
import ru.mosvegas.casino.net.TableSnapshot;

/** How a participant's chips settle at the end of a round, from the snapshot fields. */
public final class Settlement {
    private Settlement() {}

    /** Chips lying on the main bet spot (all hands after a split / double). */
    public static long mainStake(TableSnapshot.Player p) {
        if (p.controls() && !p.hands().isEmpty()) {
            long stake = 0;
            for (TableSnapshot.Hand h : p.hands()) stake += h.bet() + h.freeBet();
            return stake;
        }
        return p.main();
    }

    public static long sideStake(TableSnapshot.Player p, int index) {
        return p.side().length > index ? p.side()[index] : 0;
    }

    public static long sidePayout(TableSnapshot.Player p, int index) {
        return p.sidePayout().length > index ? p.sidePayout()[index] : 0;
    }

    /** What the main bet returned (the round payout minus side bets and insurance). */
    public static long mainPayout(TableSnapshot.Player p) {
        long sides = 0;
        for (long v : p.sidePayout()) sides += v;
        return Math.max(0, p.roundPayout() - sides - p.insurancePayout());
    }

    public static boolean mainLost(TableSnapshot.Player p) {
        return p.playing() && mainStake(p) > 0 && mainPayout(p) == 0;
    }

    /** Extra chips the dealer pays next to the main bet. */
    public static long mainWin(TableSnapshot.Player p) {
        long stake = mainStake(p);
        return stake > 0 ? Math.max(0, mainPayout(p) - stake) : 0;
    }

    /** True when the dealer has to take or pay something at this participant's box at the end of the round. */
    public static boolean movesAtPayout(TableSnapshot.Player p) {
        if (!p.playing()) return false;
        if (mainLost(p) || mainWin(p) > 0) return true;
        for (SideBet bet : SideBet.values()) if (!bet.resolvedAfterDeal() && sideStake(p, bet.ordinal()) > 0) return true;
        return p.insurance() > 0;
    }

    /** True when the participant has side bets that are settled right after the deal (pairs, 21+3, hot 3). */
    public static boolean movesAfterDeal(TableSnapshot.Player p) {
        if (!p.playing() || !p.owner()) return false;
        for (SideBet bet : SideBet.values()) if (bet.resolvedAfterDeal() && sideStake(p, bet.ordinal()) > 0) return true;
        return false;
    }

    public static long sum(long[] values) {
        long t = 0;
        for (long v : values) t += v;
        return t;
    }
}
