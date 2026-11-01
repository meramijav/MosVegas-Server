package ru.mosvegas.casino.game.roulette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Bets of one spin and their settlement. A roulette table will reuse the dealer lifecycle and the
 * economy bridge of blackjack: open bets -> "no more bets" -> {@link #spin} -> pay {@link #settle}.
 */
public final class RouletteRound {
    private final List<RouletteBet> bets = new ArrayList<>();
    private int result = -1;

    public void add(RouletteBet bet) {
        if (result >= 0) throw new IllegalStateException("Round already spun");
        bets.add(bet);
    }

    public List<RouletteBet> bets() { return List.copyOf(bets); }

    public int spin(Random random) {
        result = RouletteWheel.spin(random);
        return result;
    }

    public void forceResult(int number) { result = number; }

    public int result() { return result; }

    /** Total payout per player for the drawn number. */
    public Map<UUID, Long> settle() {
        if (result < 0) throw new IllegalStateException("Spin first");
        Map<UUID, Long> payouts = new HashMap<>();
        for (RouletteBet bet : bets) {
            long pay = bet.payout(result);
            if (pay > 0) payouts.merge(bet.player(), pay, Long::sum);
        }
        return payouts;
    }
}
