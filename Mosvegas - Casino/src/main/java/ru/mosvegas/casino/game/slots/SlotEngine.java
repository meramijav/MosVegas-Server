package ru.mosvegas.casino.game.slots;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Spins and evaluates a {@link SlotMachine}. Pure maths; the slot block will only animate the reel stops. */
public final class SlotEngine {
    public record LineWin(int line, String symbol, int count, long amount) {}

    public record SpinResult(int[] stops, String[][] window, List<LineWin> wins, long scatterWin, long total) {}

    private final SlotMachine machine;

    public SlotEngine(SlotMachine machine) { this.machine = machine; }

    public SpinResult spin(Random random, long lineBet) {
        int[] stops = new int[machine.reelCount()];
        for (int r = 0; r < stops.length; r++) stops[r] = random.nextInt(machine.reels.get(r).size());
        return evaluate(stops, lineBet);
    }

    public String[][] window(int[] stops) {
        String[][] window = new String[machine.reelCount()][machine.rows];
        for (int r = 0; r < machine.reelCount(); r++) {
            List<String> strip = machine.reels.get(r);
            for (int row = 0; row < machine.rows; row++) window[r][row] = strip.get(Math.floorMod(stops[r] + row, strip.size()));
        }
        return window;
    }

    public SpinResult evaluate(int[] stops, long lineBet) {
        String[][] window = window(stops);
        List<LineWin> wins = new ArrayList<>();
        long total = 0;
        for (int line = 0; line < machine.paylines.size(); line++) {
            int[] rows = machine.paylines.get(line);
            String target = null;
            int count = 0;
            for (int r = 0; r < rows.length && r < window.length; r++) {
                String id = window[r][rows[r]];
                SlotMachine.Symbol s = machine.symbol(id);
                if (s == null || s.scatter) break;
                if (target == null && !s.wild) target = id;
                if (s.wild || id.equals(target)) count++;
                else break;
            }
            if (target == null && count > 0) target = firstWild();
            SlotMachine.Symbol symbol = target == null ? null : machine.symbol(target);
            int mult = symbol == null ? 0 : symbol.pay(count);
            if (mult > 0) {
                long amount = lineBet * mult;
                wins.add(new LineWin(line, target, count, amount));
                total += amount;
            }
        }
        long scatterWin = 0;
        for (SlotMachine.Symbol s : machine.symbols) {
            if (!s.scatter) continue;
            int count = 0;
            for (String[] reel : window) for (String id : reel) if (id.equals(s.id)) count++;
            scatterWin += (long) s.pay(count) * lineBet * machine.paylines.size();
        }
        total += scatterWin;
        return new SpinResult(stops, window, wins, scatterWin, total);
    }

    private String firstWild() {
        for (SlotMachine.Symbol s : machine.symbols) if (s.wild) return s.id;
        return null;
    }

    /** Monte-Carlo return-to-player, for balancing reel strips. */
    public double estimateRtp(Random random, int spins) {
        long bet = (long) machine.paylines.size();
        long paid = 0;
        for (int i = 0; i < spins; i++) paid += spin(random, 1).total();
        return paid / (double) (bet * spins);
    }

    public SlotMachine machine() { return machine; }
}
