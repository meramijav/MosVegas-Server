package ru.mosvegas.casino.game.blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * Every rule of one table. Plain public fields so the object is (de)serialised by Gson from
 * {@code config/mosvegas-casino.json}; missing fields keep the preset of the table variant.
 */
public final class BlackjackRules {
    public int decks = 6;
    /** Share of the shoe dealt before the cut card (0.75 = reshuffle after 75%). */
    public double penetration = 0.75;
    public boolean dealerHitsSoft17 = false;
    /** false = European "no hole card": the dealer takes the second card after the players. */
    public boolean holeCard = false;
    /** With no hole card: a dealer blackjack takes only the original bets, doubles and splits are returned. */
    public boolean originalBetsOnly = true;
    public double blackjackPayout = 1.5;
    /** "any", "9-11" or "10-11". */
    public String doubleOn = "any";
    public boolean doubleAfterSplit = true;
    public int maxHands = 4;
    public boolean resplitAces = false;
    public boolean hitSplitAces = false;
    /** Allow splitting any two 10-value cards (10-K) instead of equal ranks only. */
    public boolean splitByValue = false;
    public boolean surrender = false;
    public boolean insurance = true;
    /** Six cards without busting win automatically. */
    public boolean sixCardCharlie = false;
    /** Free doubles on hard 9/10/11 and free splits of every pair except 10-value cards. */
    public boolean freeBet = false;
    /** A dealer 22 pushes every hand that did not bust (a natural blackjack still wins). */
    public boolean dealer22Push = false;
    public List<String> sideBets = new ArrayList<>(List.of(SideBet.PERFECT_PAIRS.id, SideBet.TWENTY_ONE_PLUS_THREE.id));
    public TurnMode turnMode = TurnMode.SEQUENTIAL;
    /** People per box: the seated owner plus players betting behind. */
    public int maxBettorsPerBox = 3;
    /** In shared-card games every bettor (also standing ones) plays an own hand. */
    public boolean behindPlayersOwnHands = false;

    public int betSeconds = 15;
    public int decisionSeconds = 12;
    public int insuranceSeconds = 7;
    /** Ticks between two dealt cards; the dealer's deal animation takes 0.56 s, so keep it at 12 or more. */
    public int cardTicks = 14;
    public int payoutSeconds = 4;

    public static BlackjackRules forVariant(BlackjackVariant variant) {
        BlackjackRules r = new BlackjackRules();
        switch (variant) {
            case CLASSIC -> {
                r.decks = 6;
                r.maxBettorsPerBox = 3;
            }
            case SPEED -> {
                r.decks = 8;
                r.turnMode = TurnMode.PARALLEL;
                r.betSeconds = 12;
                r.decisionSeconds = 10;
                r.cardTicks = 12;
            }
            case INFINITE -> {
                r.decks = 8;
                r.turnMode = TurnMode.COMMON;
                r.sixCardCharlie = true;
                r.maxHands = 2;
                r.maxBettorsPerBox = 8;
                r.behindPlayersOwnHands = true;
                r.sideBets = new ArrayList<>(List.of(SideBet.PERFECT_PAIRS.id, SideBet.TWENTY_ONE_PLUS_THREE.id, SideBet.HOT_THREE.id, SideBet.BUST_IT.id));
            }
            case FREE_BET -> {
                r.decks = 6;
                r.freeBet = true;
                r.dealer22Push = true;
                r.dealerHitsSoft17 = true;
                r.sideBets = new ArrayList<>(List.of(SideBet.PERFECT_PAIRS.id, SideBet.TWENTY_ONE_PLUS_THREE.id, SideBet.BUST_IT.id));
            }
        }
        return r;
    }

    public boolean sideBetEnabled(SideBet bet) {
        return sideBets != null && sideBets.contains(bet.id);
    }

    public List<SideBet> enabledSideBets() {
        List<SideBet> list = new ArrayList<>();
        for (SideBet bet : SideBet.values()) if (sideBetEnabled(bet)) list.add(bet);
        return list;
    }

    public boolean canDoubleOn(int hardOrSoftTotal) {
        return switch (doubleOn == null ? "any" : doubleOn) {
            case "9-11" -> hardOrSoftTotal >= 9 && hardOrSoftTotal <= 11;
            case "10-11" -> hardOrSoftTotal == 10 || hardOrSoftTotal == 11;
            default -> true;
        };
    }

    /** Clamps values a hand-edited config might break. */
    public BlackjackRules sanitize() {
        decks = Math.max(1, Math.min(12, decks));
        penetration = Math.max(0.3, Math.min(0.95, penetration));
        blackjackPayout = Math.max(1.0, Math.min(3.0, blackjackPayout));
        maxHands = Math.max(1, Math.min(8, maxHands));
        maxBettorsPerBox = Math.max(1, Math.min(16, maxBettorsPerBox));
        betSeconds = Math.max(5, betSeconds);
        decisionSeconds = Math.max(3, decisionSeconds);
        insuranceSeconds = Math.max(3, insuranceSeconds);
        cardTicks = Math.max(10, Math.min(40, cardTicks));
        payoutSeconds = Math.max(1, payoutSeconds);
        if (turnMode == null) turnMode = TurnMode.SEQUENTIAL;
        if (sideBets == null) sideBets = new ArrayList<>();
        return this;
    }
}
