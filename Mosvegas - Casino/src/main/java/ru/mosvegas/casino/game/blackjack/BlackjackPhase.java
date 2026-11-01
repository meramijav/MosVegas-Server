package ru.mosvegas.casino.game.blackjack;

public enum BlackjackPhase {
    /** No dealer at the table: players may sit down and wait. */
    NO_DEALER,
    /** Dealer is seated, nobody plays yet. */
    IDLE,
    BETTING,
    DEALING,
    INSURANCE,
    PLAYER_TURNS,
    DEALER_TURN,
    PAYOUT,
    CLEANUP,
    SHUFFLING,
    CLOSED;

    public static BlackjackPhase byId(int id) {
        BlackjackPhase[] values = values();
        return id >= 0 && id < values.length ? values[id] : NO_DEALER;
    }

    public boolean roundRunning() {
        return this == DEALING || this == INSURANCE || this == PLAYER_TURNS || this == DEALER_TURN || this == PAYOUT;
    }
}
