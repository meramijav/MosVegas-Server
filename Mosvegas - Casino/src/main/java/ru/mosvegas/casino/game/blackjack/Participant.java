package ru.mosvegas.casino.game.blackjack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A person at the table: the seated box owner or somebody betting behind a box. */
public final class Participant {
    public record BetAction(SideBet side, long amount) {}

    public final UUID id;
    public String name;
    public final int box;
    public final boolean owner;
    public final long joinOrder;

    public long main;
    public final EnumMap<SideBet, Long> side = new EnumMap<>(SideBet.class);
    public long lastMain;
    public final EnumMap<SideBet, Long> lastSide = new EnumMap<>(SideBet.class);
    public final Deque<BetAction> history = new ArrayDeque<>();
    public boolean ready;

    public boolean playing;
    public boolean connected = true;
    public final List<BjHand> hands = new ArrayList<>();
    public long insurance;
    public boolean insuranceDecided;
    public long insurancePayout;
    public final EnumMap<SideBet, Long> sidePayout = new EnumMap<>(SideBet.class);
    /** Bet behind: result copied from the owner's first hand. */
    public Outcome followOutcome = Outcome.NONE;
    public long followPayout;
    /** Money taken from the player this round and not settled yet; refunded if the round is aborted. */
    public long unsettled;
    public long roundPayout;
    /** Chips in the order they were put down on the main spot / each side spot (drawn as a pile bottom-up). */
    public final List<Long> mainChips = new ArrayList<>();
    public final EnumMap<SideBet, List<Long>> sideChips = new EnumMap<>(SideBet.class);

    public Participant(UUID id, String name, int box, boolean owner, long joinOrder) {
        this.id = id;
        this.name = name;
        this.box = box;
        this.owner = owner;
        this.joinOrder = joinOrder;
    }

    public long sideTotal() {
        long total = 0;
        for (long v : side.values()) total += v;
        return total;
    }

    public long totalBet() {
        long total = main + sideTotal() + insurance;
        for (int i = 1; i < hands.size(); i++) total += hands.get(i).bet;
        if (!hands.isEmpty()) total += Math.max(0, hands.get(0).bet - main);
        return total;
    }

    public List<Long> chips(SideBet side) {
        return side == null ? mainChips : sideChips.computeIfAbsent(side, k -> new ArrayList<>());
    }

    public long sideBet(SideBet bet) {
        Long v = side.get(bet);
        return v == null ? 0 : v;
    }

    public long sidePaid(SideBet bet) {
        Long v = sidePayout.get(bet);
        return v == null ? 0 : v;
    }

    void resetRound() {
        main = 0;
        side.clear();
        history.clear();
        ready = false;
        playing = false;
        hands.clear();
        insurance = 0;
        insuranceDecided = false;
        insurancePayout = 0;
        sidePayout.clear();
        followOutcome = Outcome.NONE;
        followPayout = 0;
        unsettled = 0;
        roundPayout = 0;
        mainChips.clear();
        sideChips.clear();
    }

    public Map<SideBet, Long> sideView() { return side; }
}
