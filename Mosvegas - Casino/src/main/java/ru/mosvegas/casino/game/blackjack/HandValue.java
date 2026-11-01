package ru.mosvegas.casino.game.blackjack;

import ru.mosvegas.casino.game.cards.Cards;

import java.util.List;

/** Best blackjack total of a set of cards; {@code soft} means an ace is still counted as 11. */
public record HandValue(int total, boolean soft) {
    public static HandValue of(List<Integer> cards) {
        int total = 0;
        int aces = 0;
        for (int card : cards) {
            if (card < 0) continue;
            total += Cards.value(card);
            if (Cards.isAce(card)) aces++;
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return new HandValue(total, aces > 0);
    }

    public boolean bust() { return total > 21; }

    /** Text as shown on live tables: {@code 7/17} for soft hands, plain total otherwise. */
    public String display() {
        if (soft && total < 21) return (total - 10) + "/" + total;
        return Integer.toString(total);
    }
}
