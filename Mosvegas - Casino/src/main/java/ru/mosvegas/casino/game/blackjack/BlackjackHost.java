package ru.mosvegas.casino.game.blackjack;

import java.util.UUID;

/**
 * Everything the rules engine needs from the outside world. The Minecraft table implements it;
 * tests use an in-memory stub. All calls happen on the server thread.
 */
public interface BlackjackHost {
    /** Box index for card/look targets meaning "the dealer's own hand". */
    int DEALER = -1;
    /** Box index meaning "the common hand in the middle of the table". */
    int CENTER = -2;

    long balance(UUID player);

    /** Takes money from the player; false (and nothing taken) when it is not affordable. */
    boolean withdraw(UUID player, long amount);

    /** Pays a player, also when they went offline. */
    void deposit(UUID player, long amount);

    String money(long amount);

    /** Dealer speech by phrase key (see the config). Args are name/value pairs: "player", "Steve". */
    void say(String phrase, String... args);

    /** Private message to one player by phrase key. */
    void tell(UUID player, String phrase, String... args);

    /** Text of a phrase (for pieces put into other messages). */
    default String phrase(String phrase, String... args) { return phrase; }

    /** One-shot dealer animation id (wave, clap, shuffle, ...) or {@link #pointAt(int)}. */
    void emote(String animation);

    /** Emote id for "open palm towards the player of this box"; the table turns it into a direction. */
    static String pointAt(int box) { return "point@" + box; }

    /** Dealer deals a card towards a box, {@link #DEALER} or {@link #CENTER}. */
    void dealCard(int box, boolean faceUp);

    void look(int box);

    void sound(String sound);

    /** Something visible changed: send a fresh snapshot to the clients. */
    void changed();

    /** Called between two rounds, the right moment for a dealer change. */
    void roundFinished();
}
