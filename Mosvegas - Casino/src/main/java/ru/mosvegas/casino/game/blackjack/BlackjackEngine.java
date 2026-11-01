package ru.mosvegas.casino.game.blackjack;

import ru.mosvegas.casino.game.cards.Cards;
import ru.mosvegas.casino.game.cards.Shoe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative blackjack round state machine. Knows nothing about Minecraft: the world is reached
 * through {@link BlackjackHost}. {@link #tick()} is called 20 times a second. Card dealing is paced by a
 * step queue so every card matches one dealer animation on the clients.
 */
public final class BlackjackEngine {
    public static final int TICKS = 20;

    private record Step(int delayAfter, Runnable action) {}

    private final BlackjackHost host;
    private final Random random;
    private BlackjackRules rules;
    private final Shoe shoe;
    private int boxes;
    private long minBet = 100;
    private long maxBet = 50_000;
    private long sideMax = 5_000;
    private int minPlayers = 1;

    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final List<Integer> dealer = new ArrayList<>();
    private boolean holeHidden;
    private BlackjackPhase phase = BlackjackPhase.NO_DEALER;
    private int now;
    private int phaseStart;
    private int phaseEnd;
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private int stepWait;
    private Participant active;
    private int activeHand = -1;
    private boolean commonRoundOpen;
    private int roundId;
    private boolean dealerPresent;
    private boolean closed;
    private int idleUntil;
    private long joinCounter;
    private boolean lastCallSaid;

    private long statWagered;
    private long statPaid;
    private long statRounds;

    public BlackjackEngine(BlackjackHost host, BlackjackRules rules, int boxes, Random random) {
        this.host = host;
        this.random = random;
        this.rules = rules.sanitize();
        this.boxes = Math.max(1, Math.min(9, boxes));
        this.shoe = new Shoe(rules.decks, rules.penetration, random);
    }

    // ------------------------------------------------------------------ configuration

    public void configure(BlackjackRules rules, int boxes, long minBet, long maxBet, long sideMax, int minPlayers) {
        this.rules = rules.sanitize();
        this.boxes = Math.max(1, Math.min(9, boxes));
        this.minBet = Math.max(1, minBet);
        this.maxBet = Math.max(this.minBet, maxBet);
        this.sideMax = Math.max(1, sideMax);
        this.minPlayers = Math.max(1, minPlayers);
        if (shoe.decks() != rules.decks && !phase.roundRunning()) {
            shoe.configure(rules.decks, rules.penetration);
            shoe.shuffle();
        } else shoe.configure(rules.decks, rules.penetration);
        changed();
    }

    public void setDealerPresent(boolean present) {
        if (dealerPresent == present) return;
        dealerPresent = present;
        changed();
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
        changed();
    }

    // ------------------------------------------------------------------ seats

    public boolean sit(int box, UUID id, String name) {
        if (box < 0 || box >= boxes || participants.containsKey(id) || owner(box) != null) return false;
        participants.put(id, new Participant(id, name, box, true, joinCounter++));
        changed();
        return true;
    }

    public boolean joinBehind(int box, UUID id, String name) {
        if (box < 0 || box >= boxes || participants.containsKey(id)) return false;
        if (!rules.behindPlayersOwnHands && owner(box) == null) return false;
        if (atBox(box).size() >= rules.maxBettorsPerBox) return false;
        participants.put(id, new Participant(id, name, box, false, joinCounter++));
        changed();
        return true;
    }

    /** A player stands up, disconnects or walks away. Money of an unfinished round stays in play and is paid later. */
    public void leave(UUID id) {
        Participant p = participants.get(id);
        if (p == null) return;
        if (p.playing && phase.roundRunning()) {
            p.connected = false;
            for (BjHand h : p.hands) if (!h.done && !h.awaitingCard && h.cards.size() >= 2) standAutomatically(p, h);
        } else {
            refund(p);
            participants.remove(id);
            if (p.owner) for (Participant follower : atBox(p.box)) {
                if (!rules.behindPlayersOwnHands && !follower.playing) {
                    refund(follower);
                    participants.remove(follower.id);
                    host.tell(follower.id, "behind_owner_left");
                }
            }
        }
        changed();
    }

    public Participant owner(int box) {
        for (Participant p : participants.values()) if (p.owner && p.box == box) return p;
        return null;
    }

    public List<Participant> atBox(int box) {
        List<Participant> list = new ArrayList<>();
        for (Participant p : participants.values()) if (p.box == box) list.add(p);
        list.sort(Comparator.comparing((Participant p) -> !p.owner).thenComparingLong(p -> p.joinOrder));
        return list;
    }

    public Participant participant(UUID id) { return participants.get(id); }
    public Collection<Participant> participants() { return Collections.unmodifiableCollection(participants.values()); }

    public int seatedCount() {
        int n = 0;
        for (Participant p : participants.values()) if (p.connected) n++;
        return n;
    }

    /** Whether this participant decides for own hands (box owner, or anybody at shared-card tables). */
    public boolean controls(Participant p) { return p.owner || rules.behindPlayersOwnHands; }

    public boolean canSideBet(Participant p) { return controls(p); }

    // ------------------------------------------------------------------ betting

    public String placeBet(UUID id, SideBet side, long amount) {
        Participant p = participants.get(id);
        if (p == null) return "err_not_seated";
        if (phase == BlackjackPhase.IDLE && dealerPresent && !closed) startBetting();
        if (phase != BlackjackPhase.BETTING) return "err_betting_closed";
        if (amount <= 0) return "err_invalid";
        if (side == null) {
            if (p.main + amount > maxBet) return "err_over_max";
        } else {
            if (!rules.sideBetEnabled(side) || !canSideBet(p)) return "err_side_unavailable";
            if (p.sideBet(side) + amount > sideMax) return "err_over_side_max";
        }
        if (!host.withdraw(id, amount)) return "err_no_money";
        if (side == null) p.main += amount;
        else p.side.merge(side, amount, Long::sum);
        p.unsettled += amount;
        statWagered += amount;
        p.history.push(new Participant.BetAction(side, amount));
        p.chips(side).add(amount);
        p.ready = false;
        host.sound("chip_place");
        changed();
        return null;
    }

    public String undo(UUID id) {
        Participant p = participants.get(id);
        if (p == null || phase != BlackjackPhase.BETTING) return "err_betting_closed";
        Participant.BetAction last = p.history.poll();
        if (last == null) return "err_nothing";
        if (last.side() == null) p.main -= last.amount();
        else p.side.merge(last.side(), -last.amount(), Long::sum);
        p.side.values().removeIf(v -> v <= 0);
        List<Long> pile = p.chips(last.side());
        if (!pile.isEmpty()) pile.remove(pile.size() - 1);
        payBack(p, last.amount());
        p.ready = false;
        changed();
        return null;
    }

    public String clearBets(UUID id) {
        Participant p = participants.get(id);
        if (p == null || phase != BlackjackPhase.BETTING) return "err_betting_closed";
        long total = p.main + p.sideTotal();
        if (total <= 0) return "err_nothing";
        p.main = 0;
        p.side.clear();
        p.history.clear();
        p.mainChips.clear();
        p.sideChips.clear();
        payBack(p, total);
        p.ready = false;
        changed();
        return null;
    }

    public String rebet(UUID id) {
        Participant p = participants.get(id);
        if (p == null) return "err_not_seated";
        if (p.lastMain <= 0) return "err_nothing";
        String err = placeBet(id, null, p.lastMain);
        if (err != null) return err;
        for (Map.Entry<SideBet, Long> e : p.lastSide.entrySet()) {
            if (e.getValue() > 0 && rules.sideBetEnabled(e.getKey()) && canSideBet(p)) placeBet(id, e.getKey(), e.getValue());
        }
        return null;
    }

    public String doubleBets(UUID id) {
        Participant p = participants.get(id);
        if (p == null || phase != BlackjackPhase.BETTING) return "err_betting_closed";
        if (p.main <= 0) return "err_nothing";
        if (p.main * 2 > maxBet) return "err_over_max";
        long sides = 0;
        for (Map.Entry<SideBet, Long> e : p.side.entrySet()) if (e.getValue() * 2 <= sideMax) sides += e.getValue();
        if (host.balance(id) < p.main + sides) return "err_no_money";
        String err = placeBet(id, null, p.main);
        if (err != null) return err;
        for (Map.Entry<SideBet, Long> e : new ArrayList<>(p.side.entrySet())) if (e.getValue() * 2 <= sideMax) placeBet(id, e.getKey(), e.getValue());
        return null;
    }

    public String ready(UUID id) {
        Participant p = participants.get(id);
        if (p == null || phase != BlackjackPhase.BETTING) return "err_betting_closed";
        if (p.main < minBet) return "err_under_min";
        p.ready = true;
        changed();
        return null;
    }

    public String insurance(UUID id, boolean take) {
        Participant p = participants.get(id);
        if (p == null || phase != BlackjackPhase.INSURANCE || !p.playing || !controls(p) || p.insuranceDecided) return "err_not_now";
        if (take) {
            long cost = p.main / 2;
            if (cost <= 0) return "err_invalid";
            if (!host.withdraw(id, cost)) return "err_no_money";
            p.insurance = cost;
            p.unsettled += cost;
            statWagered += cost;
            host.sound("chip_place");
        }
        p.insuranceDecided = true;
        changed();
        return null;
    }

    // ------------------------------------------------------------------ decisions

    public BjHand decidingHand(Participant p) {
        if (p == null || !p.playing || !controls(p) || phase != BlackjackPhase.PLAYER_TURNS) return null;
        switch (rules.turnMode) {
            case SEQUENTIAL -> {
                if (active != p || activeHand < 0 || activeHand >= p.hands.size()) return null;
                BjHand h = p.hands.get(activeHand);
                return h.done || h.awaitingCard || h.cards.size() < 2 ? null : h;
            }
            case PARALLEL -> {
                for (BjHand h : p.hands) if (!h.done && !h.awaitingCard && h.cards.size() >= 2) return h;
                return null;
            }
            default -> {
                if (!commonRoundOpen) return null;
                for (BjHand h : p.hands) if (!h.done && !h.awaitingCard && h.pending == null && h.cards.size() >= 2) return h;
                return null;
            }
        }
    }

    public Set<Decision> available(Participant p, BjHand h) {
        EnumSet<Decision> set = EnumSet.noneOf(Decision.class);
        if (h == null || h.done) return set;
        HandValue v = h.value();
        if (v.total() >= 21) return set;
        set.add(Decision.STAND);
        set.add(Decision.HIT);
        boolean twoCards = h.cards.size() == 2;
        if (twoCards && (!h.fromSplit || rules.doubleAfterSplit) && rules.canDoubleOn(v.total()) && !(h.splitAces && !rules.hitSplitAces)) {
            if (freeDouble(h) || host.balance(p.id) >= h.stake()) set.add(Decision.DOUBLE);
        }
        if (twoCards && p.hands.size() < rules.maxHands && splittable(h)) {
            if (freeSplit(h) || host.balance(p.id) >= h.stake()) set.add(Decision.SPLIT);
        }
        if (twoCards && rules.surrender && !h.fromSplit && p.hands.size() == 1) set.add(Decision.SURRENDER);
        if (h.splitAces && !rules.hitSplitAces) set.remove(Decision.HIT);
        return set;
    }

    private boolean splittable(BjHand h) {
        int a = h.cards.get(0), b = h.cards.get(1);
        boolean pair = Cards.rank(a) == Cards.rank(b) || (rules.splitByValue && Cards.value(a) == Cards.value(b));
        if (!pair) return false;
        return !(h.splitAces && !rules.resplitAces);
    }

    private boolean freeDouble(BjHand h) {
        if (!rules.freeBet) return false;
        HandValue v = h.value();
        return !v.soft() && v.total() >= 9 && v.total() <= 11;
    }

    private boolean freeSplit(BjHand h) {
        return rules.freeBet && !Cards.isTenValue(h.cards.get(0));
    }

    public String decide(UUID id, Decision decision) {
        Participant p = participants.get(id);
        if (p == null || !p.playing) return "err_not_playing";
        if (phase != BlackjackPhase.PLAYER_TURNS) return "err_not_now";
        BjHand h = decidingHand(p);
        if (h == null) return "err_not_your_turn";
        if (!available(p, h).contains(decision)) return "err_unavailable";
        if (rules.turnMode == TurnMode.COMMON) {
            if (decision == Decision.DOUBLE && !payDouble(p, h)) return "err_no_money";
            if (decision == Decision.SPLIT && !paySplitStake(p, h)) return "err_no_money";
            h.pending = decision;
            host.sound(decision == Decision.STAND ? "knock" : "chip_place");
            changed();
            return null;
        }
        return apply(p, h, decision, false);
    }

    private String apply(Participant p, BjHand h, Decision decision, boolean automatic) {
        switch (decision) {
            case STAND -> {
                h.stood = true;
                h.done = true;
                if (!automatic) host.sound("knock");
            }
            case HIT -> queueCard(p, h, false);
            case DOUBLE -> {
                if (!payDouble(p, h)) return "err_no_money";
                h.doubled = true;
                queueCard(p, h, true);
            }
            case SPLIT -> {
                if (!paySplitStake(p, h)) return "err_no_money";
                BjHand second = performSplit(p, h);
                if (rules.turnMode == TurnMode.PARALLEL) {
                    queueCard(p, h, false);
                    queueCard(p, second, false);
                }
            }
            case SURRENDER -> {
                h.surrendered = true;
                h.done = true;
                host.say("player_surrender", "player", p.name);
            }
        }
        h.deadline = 0;
        if (rules.turnMode == TurnMode.SEQUENTIAL) steps.add(new Step(2, this::advanceTurn));
        changed();
        return null;
    }

    private boolean payDouble(Participant p, BjHand h) {
        long stake = h.stake();
        if (freeDouble(h)) {
            h.freeBet += stake;
            return true;
        }
        if (!host.withdraw(p.id, stake)) return false;
        h.bet += stake;
        p.unsettled += stake;
        statWagered += stake;
        host.sound("chip_place");
        return true;
    }

    /** Pays for a split; the new hand is created by {@link #performSplit} with the same stake. */
    private boolean paySplitStake(Participant p, BjHand h) {
        if (freeSplit(h)) return true;
        long stake = h.stake();
        if (!host.withdraw(p.id, stake)) return false;
        p.unsettled += stake;
        statWagered += stake;
        host.sound("chip_place");
        return true;
    }

    private BjHand performSplit(Participant p, BjHand h) {
        boolean free = freeSplit(h);
        long stake = h.stake();
        BjHand second = new BjHand();
        if (free) second.freeBet = stake;
        else second.bet = stake;
        second.originalPart = 0;
        second.cards.add(h.cards.remove(1));
        h.fromSplit = true;
        second.fromSplit = true;
        if (Cards.isAce(h.cards.get(0))) {
            h.splitAces = true;
            second.splitAces = true;
        }
        p.hands.add(p.hands.indexOf(h) + 1, second);
        host.say("player_split", "player", p.name);
        return second;
    }

    private void queueCard(Participant p, BjHand h, boolean doubleCard) {
        h.awaitingCard = true;
        steps.add(new Step(rules.cardTicks, () -> {
            h.awaitingCard = false;
            dealTo(p, h);
            if (doubleCard) h.done = true;
            checkHand(p, h);
        }));
    }

    private void dealTo(Participant p, BjHand h) {
        h.cards.add(shoe.draw());
        host.dealCard(p.box, true);
        host.sound("card_deal");
    }

    /** Marks finished hands (bust, 21, charlie, split aces). Returns true when the hand needs no decision. */
    private boolean checkHand(Participant p, BjHand h) {
        if (h.cards.size() < 2) return h.done;
        HandValue v = h.value();
        if (v.bust()) {
            if (!h.done) host.say("player_bust", "player", p.name, "value", Integer.toString(v.total()));
            h.done = true;
            host.emote("shake_head");
            return true;
        }
        if (rules.sixCardCharlie && h.cards.size() >= 6) {
            h.charlie = true;
            h.done = true;
            host.say("player_charlie", "player", p.name);
            host.emote("clap");
            return true;
        }
        if (v.total() == 21) {
            h.done = true;
            return true;
        }
        if (h.splitAces && !rules.hitSplitAces) {
            h.done = true;
            return true;
        }
        return h.done;
    }

    private void standAutomatically(Participant p, BjHand h) {
        if (rules.turnMode == TurnMode.COMMON) {
            if (h.pending == null) h.pending = Decision.STAND;
        } else apply(p, h, Decision.STAND, true);
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        now++;
        if (closed) {
            if (phase != BlackjackPhase.CLOSED) {
                abortRound("table_closed");
                setPhase(BlackjackPhase.CLOSED, 0);
            }
            return;
        }
        if (phase == BlackjackPhase.CLOSED) setPhase(dealerPresent ? BlackjackPhase.IDLE : BlackjackPhase.NO_DEALER, 0);
        if (!dealerPresent) {
            if (phase != BlackjackPhase.NO_DEALER) {
                if (phase.roundRunning() || hasUnsettled()) abortRound("dealer_left");
                steps.clear();
                stepWait = 0;
                setPhase(BlackjackPhase.NO_DEALER, 0);
            }
            return;
        }
        if (stepWait > 0) stepWait--;
        else if (!steps.isEmpty()) {
            Step step = steps.poll();
            step.action.run();
            stepWait = step.delayAfter;
            changed();
        }
        boolean idleSteps = steps.isEmpty() && stepWait == 0;
        switch (phase) {
            case NO_DEALER -> setPhase(BlackjackPhase.IDLE, 0);
            case IDLE -> {
                if (seatedCount() >= minPlayers && now >= idleUntil) startBetting();
            }
            case BETTING -> tickBetting();
            case INSURANCE -> {
                if (idleSteps) tickInsurance();
            }
            case PLAYER_TURNS -> tickTurns(idleSteps);
            case PAYOUT -> {
                if (now >= phaseEnd) startCleanup();
            }
            case CLEANUP -> {
                if (idleSteps && now >= phaseEnd) finishCleanup();
            }
            case SHUFFLING -> {
                if (now >= phaseEnd) afterShuffle();
            }
            default -> {}
        }
    }

    private void tickBetting() {
        if (seatedCount() == 0) {
            refundAll();
            setPhase(BlackjackPhase.IDLE, 0);
            return;
        }
        int remaining = phaseEnd - now;
        if (!lastCallSaid && remaining <= 5 * TICKS) {
            lastCallSaid = true;
            if (anyBets()) host.say("betting_last");
        }
        int bettors = countBettors();
        boolean allReady = bettors > 0;
        for (Participant p : participants.values()) if (p.main > 0 && !p.ready && p.connected) allReady = false;
        if (bettors >= minPlayers && allReady) {
            closeBets();
            return;
        }
        if (now < phaseEnd) return;
        if (bettors >= minPlayers) closeBets();
        else if (anyBets()) {
            host.say("waiting_players");
            setPhase(BlackjackPhase.BETTING, rules.betSeconds);
            lastCallSaid = false;
        } else {
            setPhase(BlackjackPhase.IDLE, 0);
            idleUntil = now + 3 * TICKS;
        }
    }

    private void startBetting() {
        lastCallSaid = false;
        for (Participant p : participants.values()) p.ready = false;
        setPhase(BlackjackPhase.BETTING, rules.betSeconds);
        host.say("betting_open");
        host.emote("present");
    }

    private boolean anyBets() {
        for (Participant p : participants.values()) if (p.main > 0 || p.sideTotal() > 0) return true;
        return false;
    }

    private int countBettors() {
        int n = 0;
        for (Participant p : participants.values()) if (p.main >= minBet && (controls(p) || ownerBets(p.box))) n++;
        return n;
    }

    private boolean ownerBets(int box) {
        Participant o = owner(box);
        return o != null && o.main >= minBet && o.connected;
    }

    private void closeBets() {
        for (Participant p : new ArrayList<>(participants.values())) {
            boolean valid = p.connected && p.main >= minBet && (controls(p) || ownerBets(p.box));
            if (!valid) {
                if (p.main > 0 || p.sideTotal() > 0) {
                    refund(p);
                    host.tell(p.id, p.main > 0 && p.main < minBet ? "err_under_min" : "bet_returned");
                }
                p.resetRound();
                continue;
            }
            p.playing = true;
            p.lastMain = p.main;
            p.lastSide.clear();
            p.lastSide.putAll(p.side);
            p.history.clear();
            if (controls(p)) {
                BjHand h = new BjHand();
                h.bet = p.main;
                h.originalPart = p.main;
                p.hands.add(h);
            }
        }
        List<Participant> order = playingControllers();
        if (order.isEmpty()) {
            setPhase(BlackjackPhase.IDLE, 0);
            idleUntil = now + 3 * TICKS;
            return;
        }
        roundId++;
        statRounds++;
        dealer.clear();
        holeHidden = false;
        host.say("bets_closed");
        setPhase(BlackjackPhase.DEALING, 0);
        int t = rules.cardTicks;
        if (rules.turnMode == TurnMode.COMMON) {
            steps.add(new Step(t, this::dealCommonCard));
            steps.add(new Step(t, () -> dealDealer(true)));
            steps.add(new Step(t, this::dealCommonCard));
        } else {
            for (Participant p : order) steps.add(new Step(t, () -> dealTo(p, p.hands.get(0))));
            steps.add(new Step(t, () -> dealDealer(true)));
            for (Participant p : order) steps.add(new Step(t, () -> dealTo(p, p.hands.get(0))));
        }
        if (rules.holeCard) steps.add(new Step(t, () -> dealDealer(false)));
        steps.add(new Step(4, this::afterDeal));
    }

    private void dealCommonCard() {
        int card = shoe.draw();
        for (Participant p : playingControllers()) for (BjHand h : p.hands) if (h.cards.size() < 2) h.cards.add(card);
        host.dealCard(BlackjackHost.CENTER, true);
        host.sound("card_deal");
    }

    private void dealDealer(boolean faceUp) {
        dealer.add(shoe.draw());
        if (!faceUp) holeHidden = true;
        host.dealCard(BlackjackHost.DEALER, faceUp);
        host.sound("card_deal");
    }

    private void afterDeal() {
        resolveSideBetsAfterDeal();
        boolean anyNatural = false;
        for (Participant p : playingControllers()) {
            for (BjHand h : p.hands) {
                if (h.isNatural()) {
                    h.done = true;
                    if (!anyNatural || rules.turnMode != TurnMode.COMMON) {
                        host.say(rules.turnMode == TurnMode.COMMON ? "common_blackjack" : "player_blackjack", "player", p.name);
                    }
                    anyNatural = true;
                }
            }
        }
        if (anyNatural) host.emote("clap");
        int up = dealer.get(0);
        if (rules.insurance && Cards.isAce(up)) startInsurance();
        else if (rules.holeCard && (Cards.isAce(up) || Cards.isTenValue(up))) peek();
        else startTurns();
    }

    private void resolveSideBetsAfterDeal() {
        int up = dealer.get(0);
        for (Participant p : participants.values()) {
            if (!p.playing || p.hands.isEmpty()) continue;
            BjHand h = p.hands.get(0);
            if (h.cards.size() < 2) continue;
            int a = h.cards.get(0), b = h.cards.get(1);
            for (SideBet bet : SideBet.values()) {
                long stake = p.sideBet(bet);
                if (stake <= 0 || !bet.resolvedAfterDeal()) continue;
                int mult = switch (bet) {
                    case PERFECT_PAIRS -> SideBet.perfectPairs(a, b);
                    case TWENTY_ONE_PLUS_THREE -> SideBet.twentyOnePlusThree(a, b, up);
                    case HOT_THREE -> SideBet.hotThree(a, b, up);
                    default -> 0;
                };
                long pay = mult > 0 ? stake * (mult + 1) : 0;
                p.sidePayout.put(bet, pay);
                p.unsettled -= stake;
                if (pay > 0) {
                    host.deposit(p.id, pay);
                    statPaid += pay;
                    p.roundPayout += pay;
                    host.say("side_win", "player", p.name, "bet", bet.title, "mult", Integer.toString(mult));
                    host.tell(p.id, "side_win_private", "bet", bet.title, "amount", host.money(pay));
                } else {
                    // a real dealer takes the losing side bets right after the deal; say so, the main hand plays on
                    host.tell(p.id, "side_lose_private", "bet", bet.title, "amount", host.money(stake));
                }
            }
        }
    }

    private void startInsurance() {
        for (Participant p : participants.values()) p.insuranceDecided = !p.playing || !controls(p);
        setPhase(BlackjackPhase.INSURANCE, rules.insuranceSeconds);
        host.say("insurance_offer");
    }

    private void tickInsurance() {
        boolean all = true;
        for (Participant p : participants.values()) if (p.playing && controls(p) && !p.insuranceDecided && p.connected) all = false;
        if (!all && now < phaseEnd) return;
        for (Participant p : participants.values()) p.insuranceDecided = true;
        host.say("insurance_closed");
        if (rules.holeCard) peek();
        else startTurns();
    }

    private void peek() {
        host.emote("peek");
        steps.add(new Step(rules.cardTicks * 2, () -> {
            if (HandValue.of(dealer).total() == 21) {
                holeHidden = false;
                host.emote("flip");
                host.sound("card_flip");
                host.say("dealer_blackjack");
                steps.add(new Step(rules.cardTicks, this::settle));
            } else {
                for (Participant p : participants.values()) {
                    if (p.insurance > 0) {
                        p.unsettled -= p.insurance;
                        p.insurancePayout = 0;
                    }
                }
                host.say("dealer_no_blackjack");
                startTurns();
            }
            changed();
        }));
        setPhase(BlackjackPhase.DEALING, 0);
    }

    private void startTurns() {
        setPhase(BlackjackPhase.PLAYER_TURNS, 0);
        active = null;
        activeHand = -1;
        switch (rules.turnMode) {
            case SEQUENTIAL -> advanceTurn();
            case PARALLEL -> host.say("decide_all");
            case COMMON -> openCommonRound();
        }
    }

    private void advanceTurn() {
        if (phase != BlackjackPhase.PLAYER_TURNS) return;
        for (Participant p : playingControllers()) {
            for (int i = 0; i < p.hands.size(); i++) {
                BjHand h = p.hands.get(i);
                if (h.done) continue;
                if (h.awaitingCard) return;
                if (h.cards.size() < 2) {
                    queueCard(p, h, false);
                    steps.add(new Step(2, this::advanceTurn));
                    return;
                }
                if (checkHand(p, h)) continue;
                boolean newTurn = active != p || activeHand != i;
                active = p;
                activeHand = i;
                h.deadline = now + rules.decisionSeconds * TICKS;
                phaseEnd = h.deadline;
                host.look(p.box);
                if (newTurn) {
                    host.say("your_turn", "player", p.name, "value", h.value().display());
                    host.emote(BlackjackHost.pointAt(p.box));
                }
                if (!p.connected) standAutomatically(p, h);
                changed();
                return;
            }
        }
        active = null;
        activeHand = -1;
        startDealerTurn();
    }

    private void tickTurns(boolean idleSteps) {
        switch (rules.turnMode) {
            case SEQUENTIAL -> {
                if (!idleSteps || active == null) return;
                BjHand h = activeHand >= 0 && activeHand < active.hands.size() ? active.hands.get(activeHand) : null;
                if (h == null || h.done) {
                    advanceTurn();
                    return;
                }
                if (now >= h.deadline || !active.connected) standAutomatically(active, h);
            }
            case PARALLEL -> {
                boolean anyOpen = false;
                for (Participant p : playingControllers()) {
                    for (BjHand h : new ArrayList<>(p.hands)) {
                        if (h.done) continue;
                        anyOpen = true;
                        if (h.awaitingCard) continue;
                        if (h.cards.size() < 2) {
                            queueCard(p, h, false);
                            continue;
                        }
                        if (checkHand(p, h)) continue;
                        if (h.deadline == 0) {
                            h.deadline = now + rules.decisionSeconds * TICKS;
                            changed();
                        }
                        if (now >= h.deadline || !p.connected) standAutomatically(p, h);
                    }
                }
                if (!anyOpen && idleSteps) startDealerTurn();
            }
            case COMMON -> {
                if (idleSteps) tickCommon();
            }
        }
    }

    private void openCommonRound() {
        commonRoundOpen = true;
        phaseEnd = now + rules.decisionSeconds * TICKS;
        boolean any = false;
        for (Participant p : playingControllers()) {
            for (BjHand h : p.hands) {
                if (h.done) continue;
                checkHand(p, h);
                if (!h.done && !h.awaitingCard && h.cards.size() >= 2) {
                    h.pending = null;
                    h.deadline = phaseEnd;
                    any = true;
                    if (!p.connected) h.pending = Decision.STAND;
                }
            }
        }
        if (any) host.say("decide_all");
        changed();
    }

    private void tickCommon() {
        List<BjHand> deciders = new ArrayList<>();
        boolean requests = false;
        for (Participant p : playingControllers()) {
            for (BjHand h : p.hands) {
                if (h.done) continue;
                if (h.awaitingCard || h.cards.size() < 2) requests = true;
                else deciders.add(h);
            }
        }
        if (deciders.isEmpty() && !requests) {
            commonRoundOpen = false;
            startDealerTurn();
            return;
        }
        boolean allDecided = true;
        for (BjHand h : deciders) if (h.pending == null) allDecided = false;
        if (!allDecided && now < phaseEnd && commonRoundOpen) return;
        commonRoundOpen = false;
        for (Participant p : playingControllers()) {
            for (BjHand h : new ArrayList<>(p.hands)) {
                if (h.done || h.awaitingCard || h.cards.size() < 2) {
                    if (!h.done && h.cards.size() < 2) h.awaitingCard = true;
                    continue;
                }
                Decision d = h.pending == null ? Decision.STAND : h.pending;
                h.pending = null;
                h.deadline = 0;
                switch (d) {
                    case STAND -> {
                        h.stood = true;
                        h.done = true;
                    }
                    case SURRENDER -> {
                        h.surrendered = true;
                        h.done = true;
                    }
                    case HIT -> h.awaitingCard = true;
                    case DOUBLE -> {
                        h.doubled = true;
                        h.awaitingCard = true;
                    }
                    case SPLIT -> {
                        BjHand second = performSplit(p, h);
                        h.awaitingCard = true;
                        second.awaitingCard = true;
                    }
                }
            }
        }
        if (anyAwaitingCard()) steps.add(new Step(rules.cardTicks, this::commonDraw));
        steps.add(new Step(2, this::openCommonRound));
        changed();
    }

    private boolean anyAwaitingCard() {
        for (Participant p : playingControllers()) for (BjHand h : p.hands) if (!h.done && h.awaitingCard) return true;
        return false;
    }

    /** One common card for every hand that asked for it (one hand per player per draw). */
    private void commonDraw() {
        int card = shoe.draw();
        for (Participant p : playingControllers()) {
            for (BjHand h : p.hands) {
                if (h.done || !h.awaitingCard) continue;
                h.awaitingCard = false;
                h.cards.add(card);
                if (h.doubled) h.done = true;
                checkHand(p, h);
                break;
            }
        }
        host.dealCard(BlackjackHost.CENTER, true);
        host.sound("card_deal");
        if (anyAwaitingCard()) steps.addFirst(new Step(rules.cardTicks, this::commonDraw));
    }

    private void startDealerTurn() {
        setPhase(BlackjackPhase.DEALER_TURN, 0);
        active = null;
        activeHand = -1;
        host.look(BlackjackHost.DEALER);
        if (dealer.size() < 2) steps.add(new Step(rules.cardTicks, () -> dealDealer(true)));
        else if (holeHidden) steps.add(new Step(rules.cardTicks, () -> {
            holeHidden = false;
            host.emote("flip");
            host.sound("card_flip");
        }));
        steps.add(new Step(rules.cardTicks / 2, this::dealerContinue));
    }

    private void dealerContinue() {
        HandValue v = HandValue.of(dealer);
        boolean blackjack = dealer.size() == 2 && v.total() == 21;
        boolean mustHit = v.total() < 17 || (v.total() == 17 && v.soft() && rules.dealerHitsSoft17);
        if (!blackjack && mustHit && dealerMustPlay()) {
            steps.add(new Step(rules.cardTicks, () -> dealDealer(true)));
            steps.add(new Step(rules.cardTicks / 2, this::dealerContinue));
            return;
        }
        if (blackjack) {
            host.say("dealer_blackjack");
            host.emote("nod");
        } else if (v.bust()) {
            host.say("dealer_bust", "value", Integer.toString(v.total()));
            host.emote("shrug");
        } else host.say("dealer_total", "value", Integer.toString(v.total()));
        steps.add(new Step(rules.cardTicks, this::settle));
    }

    private boolean dealerMustPlay() {
        for (Participant p : playingControllers()) for (BjHand h : p.hands) if (h.live()) return true;
        for (Participant p : participants.values()) if (p.playing && p.sideBet(SideBet.BUST_IT) > 0) return true;
        return false;
    }

    private void settle() {
        HandValue dv = HandValue.of(dealer);
        boolean dealerBJ = dealer.size() == 2 && dv.total() == 21;
        boolean dealerBust = dv.bust();
        boolean push22 = rules.dealer22Push && dv.total() == 22;
        boolean bigWin = false;
        // the hands first: bets behind follow the owner's result whatever order the participants joined in
        for (Participant p : participants.values()) {
            if (p.playing && controls(p)) for (BjHand h : p.hands) settleHand(h, dv, dealerBJ, dealerBust, push22);
        }
        for (Participant p : participants.values()) {
            if (!p.playing) continue;
            long ret = 0;
            if (controls(p)) {
                for (BjHand h : p.hands) {
                    ret += h.payout;
                    if (h.outcome == Outcome.BLACKJACK || h.payout >= h.bet * 3 && h.bet > 0) bigWin = true;
                }
            } else {
                Participant o = owner(p.box);
                Outcome outcome = o != null && !o.hands.isEmpty() ? o.hands.get(0).outcome : Outcome.PUSH;
                long b = p.main;
                long pay = switch (outcome) {
                    case BLACKJACK -> b + (long) Math.floor(b * rules.blackjackPayout);
                    case WIN, CHARLIE -> b * 2;
                    case PUSH, NONE -> b;
                    case SURRENDER -> b / 2;
                    default -> 0;
                };
                p.followOutcome = outcome;
                p.followPayout = pay;
                ret += pay;
            }
            if (p.insurance > 0) {
                p.insurancePayout = dealerBJ ? p.insurance * 3 : 0;
                ret += p.insurancePayout;
            }
            long bust = p.sideBet(SideBet.BUST_IT);
            if (bust > 0) {
                int mult = SideBet.bustIt(dealer);
                long pay = mult > 0 ? bust * (mult + 1) : 0;
                p.sidePayout.put(SideBet.BUST_IT, pay);
                ret += pay;
                if (pay > 0) host.say("side_win", "player", p.name, "bet", SideBet.BUST_IT.title, "mult", Integer.toString(mult));
            }
            p.unsettled = 0;
            if (ret > 0) {
                host.deposit(p.id, ret);
                statPaid += ret;
            }
            p.roundPayout += ret;
            reportResult(p, dv.total());
        }
        if (bigWin) host.say("big_win");
        // the dealer pays and takes box by box (about 0.65 s each, see the client choreography)
        Set<Integer> boxes = new HashSet<>();
        for (Participant p : participants.values()) if (p.playing) boxes.add(p.box);
        setPhase(BlackjackPhase.PAYOUT, Math.max(rules.payoutSeconds, (int) Math.ceil(1.0 + 0.65 * boxes.size())));
        host.sound("chip_stack");
    }

    private void settleHand(BjHand h, HandValue dealerValue, boolean dealerBJ, boolean dealerBust, boolean push22) {
        long bet = h.bet;
        int player = h.value().total();
        if (h.surrendered) {
            h.outcome = Outcome.SURRENDER;
            h.payout = bet / 2;
        } else if (h.busted()) {
            h.outcome = Outcome.BUST;
            h.payout = 0;
        } else if (h.isNatural()) {
            if (dealerBJ) {
                h.outcome = Outcome.PUSH;
                h.payout = bet;
            } else {
                h.outcome = Outcome.BLACKJACK;
                h.payout = bet + (long) Math.floor(bet * rules.blackjackPayout);
            }
        } else if (h.charlie) {
            h.outcome = Outcome.CHARLIE;
            h.payout = 2 * bet + h.freeBet;
        } else if (dealerBJ) {
            h.outcome = Outcome.LOSE;
            h.payout = rules.originalBetsOnly && !rules.holeCard ? Math.max(0, bet - h.originalPart) : 0;
        } else if (push22) {
            h.outcome = Outcome.PUSH;
            h.payout = bet;
        } else if (dealerBust || player > dealerValue.total()) {
            h.outcome = Outcome.WIN;
            h.payout = 2 * bet + h.freeBet;
        } else if (player == dealerValue.total()) {
            h.outcome = Outcome.PUSH;
            h.payout = bet;
        } else {
            h.outcome = Outcome.LOSE;
            h.payout = 0;
        }
    }

    /**
     * Private summary of the round, part by part: every hand against the dealer, the side bets, the insurance and,
     * when more than one part was in play, the total. (A won hand plus a lost side bet is not a "push".)
     */
    private void reportResult(Participant p, int dealerTotal) {
        String dealer = host.phrase(dealerTotal > 21 ? "result_dealer_bust" : "result_dealer", "value", Integer.toString(dealerTotal));
        int parts = 0;
        if (controls(p)) {
            int n = p.hands.size();
            for (int i = 0; i < n; i++) {
                BjHand h = p.hands.get(i);
                String label = n > 1 ? host.phrase("result_hand_n", "n", Integer.toString(i + 1)) : host.phrase("result_hand_one");
                tellHand(p, label, h.outcome, h.bet, h.payout, h.value().total(), dealer);
                parts++;
            }
        } else if (p.main > 0) {
            Participant o = owner(p.box);
            int value = o != null && !o.hands.isEmpty() ? o.hands.get(0).value().total() : 0;
            tellHand(p, host.phrase("result_behind"), p.followOutcome, p.main, p.followPayout, value, dealer);
            parts++;
        }
        long sideStake = p.sideTotal(), sidePaid = 0;
        for (long v : p.sidePayout.values()) sidePaid += v;
        if (sideStake > 0) {
            host.tell(p.id, "result_side", "sign", sign(sidePaid - sideStake), "amount", host.money(Math.abs(sidePaid - sideStake)));
            parts++;
        }
        if (p.insurance > 0) {
            long net = p.insurancePayout - p.insurance;
            host.tell(p.id, "result_insurance", "sign", sign(net), "amount", host.money(Math.abs(net)));
            parts++;
        }
        if (parts > 1) {
            long net = p.roundPayout - p.totalBet();
            host.tell(p.id, "result_total", "sign", sign(net), "amount", host.money(Math.abs(net)));
        }
    }

    private void tellHand(Participant p, String label, Outcome outcome, long bet, long payout, int value, String dealer) {
        String key;
        long amount;
        switch (outcome) {
            case BLACKJACK -> { key = "result_hand_blackjack"; amount = payout - bet; }
            case WIN, CHARLIE -> { key = "result_hand_win"; amount = payout - bet; }
            case PUSH, NONE -> { key = "result_hand_push"; amount = bet; }
            case SURRENDER -> { key = "result_hand_surrender"; amount = payout; }
            case BUST -> { key = "result_hand_bust"; amount = bet; }
            default -> { key = "result_hand_lose"; amount = Math.max(0, bet - payout); }
        }
        host.tell(p.id, key, "hand", label, "value", Integer.toString(value), "dealer", dealer, "amount", host.money(amount));
    }

    private static String sign(long v) { return v > 0 ? "+" : v < 0 ? "-" : ""; }

    private void startCleanup() {
        // the clients play the sweep (2.2 s): hands gather the cards and drop them into the discard holder
        setPhase(BlackjackPhase.CLEANUP, 3);
        host.sound("cards_collect");
    }

    private void finishCleanup() {
        dealer.clear();
        holeHidden = false;
        for (Participant p : new ArrayList<>(participants.values())) {
            p.resetRound();
            if (!p.connected) participants.remove(p.id);
        }
        host.roundFinished();
        if (shoe.needsShuffle()) {
            shoe.shuffle();
            setPhase(BlackjackPhase.SHUFFLING, 4);
            host.emote("shuffle");
            host.say("shuffle");
            host.sound("shuffle");
        } else afterShuffle();
    }

    private void afterShuffle() {
        if (dealerPresent && seatedCount() >= minPlayers) startBetting();
        else setPhase(BlackjackPhase.IDLE, 0);
    }

    /** Forces a reshuffle with animation, e.g. right after a new dealer sits down. */
    public void freshShoe() {
        if (phase.roundRunning()) return;
        shoe.shuffle();
        setPhase(BlackjackPhase.SHUFFLING, 4);
        host.emote("shuffle");
        host.sound("shuffle");
    }

    // ------------------------------------------------------------------ refunds

    private boolean hasUnsettled() {
        for (Participant p : participants.values()) if (p.unsettled > 0) return true;
        return false;
    }

    private void refund(Participant p) {
        if (p.unsettled > 0) payBack(p, p.unsettled);
        p.resetRound();
    }

    private void payBack(Participant p, long amount) {
        if (amount <= 0) return;
        host.deposit(p.id, amount);
        p.unsettled = Math.max(0, p.unsettled - amount);
        statWagered -= amount;
    }

    private void refundAll() {
        for (Participant p : participants.values()) refund(p);
    }

    /** Returns every unsettled stake: used on server stop, table removal and lost dealer. */
    public void abortRound(String reason) {
        boolean any = false;
        for (Participant p : new ArrayList<>(participants.values())) {
            if (p.unsettled > 0) {
                any = true;
                host.tell(p.id, "round_aborted", "amount", host.money(p.unsettled));
            }
            refund(p);
            if (!p.connected) participants.remove(p.id);
        }
        steps.clear();
        stepWait = 0;
        dealer.clear();
        holeHidden = false;
        active = null;
        activeHand = -1;
        if (any) host.say(reason);
        setPhase(dealerPresent && !closed ? BlackjackPhase.IDLE : BlackjackPhase.NO_DEALER, 0);
        idleUntil = now + 3 * TICKS;
    }

    /** Server shutdown: refund and forget everybody. */
    public void shutdown() {
        abortRound("round_aborted_all");
        participants.clear();
    }

    // ------------------------------------------------------------------ helpers & views

    private List<Participant> playingControllers() {
        List<Participant> list = new ArrayList<>();
        for (Participant p : participants.values()) if (p.playing && controls(p)) list.add(p);
        list.sort(Comparator.comparingInt((Participant p) -> p.box).thenComparing(p -> !p.owner).thenComparingLong(p -> p.joinOrder));
        return list;
    }

    private void setPhase(BlackjackPhase next, int seconds) {
        phase = next;
        phaseStart = now;
        phaseEnd = seconds > 0 ? now + seconds * TICKS : now;
        changed();
    }

    private void changed() { host.changed(); }

    public boolean betweenRounds() {
        return switch (phase) {
            case NO_DEALER, IDLE, SHUFFLING, CLOSED -> true;
            case BETTING -> !anyBets();
            default -> false;
        };
    }

    public BlackjackPhase phase() { return phase; }
    public int remainingTicks() { return Math.max(0, phaseEnd - now); }
    public int phaseLengthTicks() { return Math.max(0, phaseEnd - phaseStart); }
    public int ticksUntil(int deadline) { return deadline <= 0 ? 0 : Math.max(0, deadline - now); }
    public List<Integer> dealerCards() { return Collections.unmodifiableList(dealer); }
    public boolean holeHidden() { return holeHidden; }
    public Participant activeParticipant() { return active; }
    public int activeHand() { return activeHand; }
    public int roundId() { return roundId; }
    public int boxes() { return boxes; }
    public BlackjackRules rules() { return rules; }
    public long minBet() { return minBet; }
    public long maxBet() { return maxBet; }
    public long sideMax() { return sideMax; }
    public int shoeRemaining() { return shoe.remaining(); }
    public int shoeSize() { return shoe.size(); }
    public boolean dealerPresent() { return dealerPresent; }
    public boolean closed() { return closed; }
    public long statWagered() { return statWagered; }
    public long statPaid() { return statPaid; }
    public long statRounds() { return statRounds; }
    public Shoe shoe() { return shoe; }
    public boolean commonRoundOpen() { return commonRoundOpen; }
    public int now() { return now; }

    /** Dealer total as visible to the players (face-down card excluded). */
    public HandValue visibleDealerValue() {
        if (!holeHidden) return HandValue.of(dealer);
        return HandValue.of(dealer.isEmpty() ? List.of() : List.of(dealer.get(0)));
    }

    public Random random() { return random; }
}
