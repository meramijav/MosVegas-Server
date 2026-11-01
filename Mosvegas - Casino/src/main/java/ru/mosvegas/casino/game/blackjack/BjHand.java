package ru.mosvegas.casino.game.blackjack;

import java.util.ArrayList;
import java.util.List;

/** One player hand. Money is in whole currency units. */
public final class BjHand {
    public final List<Integer> cards = new ArrayList<>();
    /** The player's own money on this hand. */
    public long bet;
    /** Casino-funded stake of Free Bet doubles/splits: wins pay it, losses cost nothing. */
    public long freeBet;
    /** Part of {@link #bet} that is an original wager (not a double or split), used by "original bets only". */
    public long originalPart;
    public boolean doubled;
    public boolean fromSplit;
    public boolean splitAces;
    public boolean surrendered;
    public boolean stood;
    public boolean charlie;
    public boolean done;
    /** A card is queued for this hand (or, in shared-card games, the hand asked for the next common card). */
    public boolean awaitingCard;
    /** Shared-card games: the decision waiting for the common draw. */
    public Decision pending;
    /** Engine tick when the hand stands automatically, 0 when no timer runs. */
    public int deadline;
    public Outcome outcome = Outcome.NONE;
    public long payout;

    public HandValue value() { return HandValue.of(cards); }
    public boolean busted() { return value().bust(); }
    public boolean isNatural() { return !fromSplit && cards.size() == 2 && value().total() == 21; }
    public long stake() { return bet + freeBet; }

    /** Still competes against the dealer (the dealer has to finish drawing). */
    public boolean live() { return !busted() && !surrendered && !charlie && !isNatural(); }
}
