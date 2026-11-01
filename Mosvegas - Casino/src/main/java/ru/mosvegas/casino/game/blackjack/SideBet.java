package ru.mosvegas.casino.game.blackjack;

import ru.mosvegas.casino.game.cards.Cards;

import java.util.Arrays;
import java.util.List;

/**
 * Side bets of live blackjack tables. {@link #payoutMultiplier} returns the "X to 1" win multiplier,
 * 0 for a loss. The returned stake is added by the caller (total return = bet * (mult + 1)).
 */
public enum SideBet {
    PERFECT_PAIRS("perfect_pairs", "Perfect Pairs"),
    TWENTY_ONE_PLUS_THREE("21+3", "21+3"),
    HOT_THREE("hot3", "Hot 3"),
    BUST_IT("bust_it", "Bust It");

    public final String id;
    public final String title;

    SideBet(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public static SideBet byId(String id) {
        for (SideBet bet : values()) if (bet.id.equalsIgnoreCase(id)) return bet;
        return null;
    }

    /** Side bets decided by the first two player cards plus the dealer up card. */
    public boolean resolvedAfterDeal() { return this != BUST_IT; }

    /** Perfect pairs: perfect (same suit) 25:1, coloured 12:1, mixed 6:1. */
    public static int perfectPairs(int a, int b) {
        if (Cards.rank(a) != Cards.rank(b)) return 0;
        if (Cards.suit(a) == Cards.suit(b)) return 25;
        if (Cards.isRed(a) == Cards.isRed(b)) return 12;
        return 6;
    }

    /** 21+3 poker hand from two player cards and the dealer up card. */
    public static int twentyOnePlusThree(int a, int b, int dealer) {
        int[] ranks = {Cards.straightRank(a), Cards.straightRank(b), Cards.straightRank(dealer)};
        Arrays.sort(ranks);
        boolean flush = Cards.suit(a) == Cards.suit(b) && Cards.suit(b) == Cards.suit(dealer);
        boolean trips = ranks[0] == ranks[1] && ranks[1] == ranks[2];
        boolean straight = !trips && ((ranks[0] + 1 == ranks[1] && ranks[1] + 1 == ranks[2])
                || (ranks[0] == 1 && ranks[1] == 12 && ranks[2] == 13));
        if (trips && flush) return 100;
        if (straight && flush) return 40;
        if (trips) return 30;
        if (straight) return 10;
        if (flush) return 5;
        return 0;
    }

    /** Hot 3: 7-7-7 100:1, suited 21 20:1, 21 4:1, 20 2:1, 19 1:1. */
    public static int hotThree(int a, int b, int dealer) {
        if (Cards.rank(a) == 6 && Cards.rank(b) == 6 && Cards.rank(dealer) == 6) return 100;
        int total = HandValue.of(List.of(a, b, dealer)).total();
        boolean suited = Cards.suit(a) == Cards.suit(b) && Cards.suit(b) == Cards.suit(dealer);
        if (total == 21) return suited ? 20 : 4;
        if (total == 20) return 2;
        if (total == 19) return 1;
        return 0;
    }

    /** Bust It: the dealer busts; the more cards, the bigger the prize. */
    public static int bustIt(List<Integer> dealerCards) {
        if (!HandValue.of(dealerCards).bust()) return 0;
        return switch (dealerCards.size()) {
            case 0, 1, 2 -> 0;
            case 3 -> 1;
            case 4 -> 2;
            case 5 -> 9;
            case 6 -> 50;
            case 7 -> 100;
            default -> 250;
        };
    }
}
