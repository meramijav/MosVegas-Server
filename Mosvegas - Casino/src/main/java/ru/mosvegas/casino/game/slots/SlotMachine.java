package ru.mosvegas.casino.game.slots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data-driven slot machine: weighted reel strips, paylines over a reels x rows window and a pay table.
 * Gson-friendly so machines can later live in the config next to blackjack tables.
 */
public final class SlotMachine {
    public static final class Symbol {
        public String id;
        /** Pays for 3, 4, 5 ... equal symbols on a line, as a multiplier of the line bet. */
        public int[] pays = new int[0];
        public boolean wild;
        /** Scatter pays anywhere on the window as a multiplier of the total bet. */
        public boolean scatter;

        public Symbol() {}

        public Symbol(String id, boolean wild, boolean scatter, int... pays) {
            this.id = id;
            this.wild = wild;
            this.scatter = scatter;
            this.pays = pays;
        }

        public int pay(int count) {
            int index = count - 3;
            return index >= 0 && index < pays.length ? pays[index] : 0;
        }
    }

    public String id = "classic";
    public int rows = 3;
    /** Reel strips by symbol id, top to bottom; the window shows {@link #rows} consecutive stops. */
    public List<List<String>> reels = new ArrayList<>();
    /** Each payline lists the row (0 = top) for every reel. */
    public List<int[]> paylines = new ArrayList<>();
    public List<Symbol> symbols = new ArrayList<>();

    public Symbol symbol(String id) {
        for (Symbol s : symbols) if (s.id.equals(id)) return s;
        return null;
    }

    public int reelCount() { return reels.size(); }

    /** Five-reel, ten-line fruit machine with wild "7" and scatter "star"; RTP checked by the rules test. */
    public static SlotMachine classicFruits() {
        SlotMachine m = new SlotMachine();
        m.symbols.add(new Symbol("cherry", false, false, 10, 30, 120));
        m.symbols.add(new Symbol("lemon", false, false, 10, 30, 120));
        m.symbols.add(new Symbol("orange", false, false, 12, 40, 160));
        m.symbols.add(new Symbol("plum", false, false, 15, 50, 200));
        m.symbols.add(new Symbol("grape", false, false, 25, 100, 400));
        m.symbols.add(new Symbol("melon", false, false, 40, 150, 600));
        m.symbols.add(new Symbol("bell", false, false, 60, 250, 1200));
        m.symbols.add(new Symbol("seven", true, false, 150, 600, 2500));
        m.symbols.add(new Symbol("star", false, true, 3, 15, 80));
        Map<String, Integer> weights = Map.of("cherry", 9, "lemon", 9, "orange", 8, "plum", 7, "grape", 5,
                "melon", 4, "bell", 3, "seven", 1, "star", 2);
        for (int reel = 0; reel < 5; reel++) {
            List<String> strip = new ArrayList<>();
            int offset = reel * 3;
            List<String> order = List.of("cherry", "lemon", "orange", "plum", "grape", "melon", "bell", "seven", "star");
            for (int round = 0; round < 9; round++) {
                for (int i = 0; i < order.size(); i++) {
                    String id = order.get((i + offset + round) % order.size());
                    if (round < weights.get(id)) strip.add(id);
                }
            }
            m.reels.add(strip);
        }
        m.paylines.add(new int[]{1, 1, 1, 1, 1});
        m.paylines.add(new int[]{0, 0, 0, 0, 0});
        m.paylines.add(new int[]{2, 2, 2, 2, 2});
        m.paylines.add(new int[]{0, 1, 2, 1, 0});
        m.paylines.add(new int[]{2, 1, 0, 1, 2});
        m.paylines.add(new int[]{1, 0, 0, 0, 1});
        m.paylines.add(new int[]{1, 2, 2, 2, 1});
        m.paylines.add(new int[]{0, 0, 1, 2, 2});
        m.paylines.add(new int[]{2, 2, 1, 0, 0});
        m.paylines.add(new int[]{1, 0, 1, 2, 1});
        return m;
    }
}
