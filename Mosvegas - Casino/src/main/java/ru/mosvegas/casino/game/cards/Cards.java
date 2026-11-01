package ru.mosvegas.casino.game.cards;

/**
 * A card is a plain int 0..51: {@code suit * 13 + rank}. Rank 0 = ace, 1..9 = 2..10, 10 = J, 11 = Q, 12 = K.
 * Suits: 0 spades, 1 hearts, 2 diamonds, 3 clubs. {@link #HIDDEN} marks a face-down card in snapshots.
 */
public final class Cards {
    public static final int HIDDEN = -1;
    public static final String[] SUIT_NAMES = {"spades", "hearts", "diamonds", "clubs"};
    public static final String[] RANK_NAMES = {"a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"};
    private static final String[] RANK_LABELS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final String[] SUIT_SYMBOLS = {"♠", "♥", "♦", "♣"};

    private Cards() {}

    public static int of(int suit, int rank) { return suit * 13 + rank; }
    public static int rank(int card) { return Math.floorMod(card, 13); }
    public static int suit(int card) { return Math.floorMod(card, 52) / 13; }
    public static boolean isAce(int card) { return rank(card) == 0; }
    public static boolean isRed(int card) { int s = suit(card); return s == 1 || s == 2; }
    public static boolean isTenValue(int card) { return rank(card) >= 9; }

    /** Blackjack value with ace counted as 11. */
    public static int value(int card) {
        int r = rank(card);
        return r == 0 ? 11 : Math.min(10, r + 1);
    }

    /** Rank order used for straights: A=1 .. K=13. */
    public static int straightRank(int card) { return rank(card) + 1; }

    public static String label(int card) {
        if (card < 0) return "??";
        return RANK_LABELS[rank(card)] + SUIT_SYMBOLS[suit(card)];
    }

    /** Resource name of a card face texture, e.g. {@code hearts_q}. */
    public static String textureName(int card) {
        return SUIT_NAMES[suit(card)] + "_" + RANK_NAMES[rank(card)];
    }
}
