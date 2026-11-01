package ru.mosvegas.casino.game.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Multi-deck shoe with a cut card. Reshuffles only between rounds (see {@link #needsShuffle()}). */
public final class Shoe {
    private final List<Integer> cards = new ArrayList<>();
    private final Random random;
    private int decks;
    private double penetration;
    private int position;
    private int cutPosition;

    public Shoe(int decks, double penetration, Random random) {
        this.random = random;
        configure(decks, penetration);
        shuffle();
    }

    public void configure(int decks, double penetration) {
        this.decks = Math.max(1, Math.min(12, decks));
        this.penetration = Math.max(0.3, Math.min(0.95, penetration));
    }

    public void shuffle() {
        cards.clear();
        for (int d = 0; d < decks; d++) for (int c = 0; c < 52; c++) cards.add(c);
        Collections.shuffle(cards, random);
        position = 0;
        cutPosition = (int) Math.round(cards.size() * penetration);
    }

    /** Draws the next card; an exhausted shoe is silently reshuffled so a round can always finish. */
    public int draw() {
        if (position >= cards.size()) shuffle();
        return cards.get(position++);
    }

    /** Test hook: forces the next cards to come out in the given order. */
    public void stack(int... next) {
        for (int i = 0; i < next.length; i++) {
            int at = position + i;
            if (at >= cards.size()) cards.add(next[i]);
            else cards.set(at, next[i]);
        }
    }

    public boolean needsShuffle() { return position >= cutPosition; }
    public int remaining() { return cards.size() - position; }
    public int size() { return cards.size(); }
    public int decks() { return decks; }
}
