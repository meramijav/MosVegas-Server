package ru.mosvegas.casino.game.blackjack;

public enum TurnMode {
    /** One hand at a time, from the dealer's left to the dealer's right. */
    SEQUENTIAL,
    /** Every unfinished hand may act at once; each action is resolved immediately. */
    PARALLEL,
    /** Everyone shares the same cards: decisions are collected, then one common card goes to every hand that asked. */
    COMMON
}
