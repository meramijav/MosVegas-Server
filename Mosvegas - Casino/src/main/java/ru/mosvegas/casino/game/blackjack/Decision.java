package ru.mosvegas.casino.game.blackjack;

public enum Decision {
    HIT, STAND, DOUBLE, SPLIT, SURRENDER;

    public static Decision byId(int id) {
        Decision[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
