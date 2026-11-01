package ru.mosvegas.casino.game.blackjack;

public enum Outcome {
    NONE, WIN, BLACKJACK, CHARLIE, PUSH, LOSE, BUST, SURRENDER;

    public boolean positive() { return this == WIN || this == BLACKJACK || this == CHARLIE; }

    public static Outcome byId(int id) {
        Outcome[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
