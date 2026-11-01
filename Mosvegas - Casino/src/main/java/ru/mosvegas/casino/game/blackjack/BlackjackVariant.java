package ru.mosvegas.casino.game.blackjack;

/** Table game variants. Each one only selects a preset of {@link BlackjackRules}; every rule stays configurable. */
public enum BlackjackVariant {
    /** Live "Blackjack Classic": 7 boxes, European no-hole-card, dealer stands on 17, bet behind up to 3 people per box. */
    CLASSIC("Blackjack Classic"),
    /** "Speed Blackjack": every box decides at the same time, the fastest player gets the next card. */
    SPEED("Speed Blackjack"),
    /** "Infinite Blackjack": unlimited players share the initial cards, everyone decides for their own hand. */
    INFINITE("Infinite Blackjack"),
    /** "Free Bet Blackjack": free doubles on hard 9-11, free splits, dealer 22 pushes. */
    FREE_BET("Free Bet Blackjack");

    public final String title;

    BlackjackVariant(String title) { this.title = title; }

    public static BlackjackVariant parse(String id, BlackjackVariant fallback) {
        if (id == null) return fallback;
        for (BlackjackVariant v : values()) if (v.name().equalsIgnoreCase(id.replace('-', '_'))) return v;
        return fallback;
    }
}
