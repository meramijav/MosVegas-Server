package ru.mosvegas.casino.game;

/**
 * Game families of the casino. Blackjack is fully playable; roulette and slots already have their
 * maths in {@code game.roulette} / {@code game.slots} and plug into the same table/dealer/economy layers.
 */
public enum CasinoGameType {
    BLACKJACK(true),
    ROULETTE(false),
    SLOTS(false);

    public final boolean playable;

    CasinoGameType(boolean playable) { this.playable = playable; }
}
