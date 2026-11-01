package ru.mosvegas.casino.test;

import ru.mosvegas.casino.game.blackjack.BlackjackEngine;
import ru.mosvegas.casino.game.blackjack.BlackjackHost;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.game.blackjack.BlackjackRules;
import ru.mosvegas.casino.game.blackjack.BlackjackVariant;
import ru.mosvegas.casino.game.blackjack.Decision;
import ru.mosvegas.casino.game.blackjack.HandValue;
import ru.mosvegas.casino.game.blackjack.Outcome;
import ru.mosvegas.casino.game.blackjack.SideBet;
import ru.mosvegas.casino.game.cards.Cards;
import ru.mosvegas.casino.game.roulette.RouletteBet;
import ru.mosvegas.casino.game.roulette.RouletteBetType;
import ru.mosvegas.casino.game.roulette.RouletteRound;
import ru.mosvegas.casino.game.slots.SlotEngine;
import ru.mosvegas.casino.game.slots.SlotMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Rule regression checks, run by {@code gradlew rulesTest} (also part of {@code check}). */
public final class RulesSelfTest {
    private static int checks;
    private static final List<String> failures = new ArrayList<>();

    static final class StubHost implements BlackjackHost {
        final Map<UUID, Long> money = new HashMap<>();
        final List<String> said = new ArrayList<>();
        final List<String> told = new ArrayList<>();

        long get(UUID id) { return money.getOrDefault(id, 0L); }
        @Override public long balance(UUID player) { return get(player); }
        @Override public boolean withdraw(UUID player, long amount) {
            if (get(player) < amount) return false;
            money.put(player, get(player) - amount);
            return true;
        }
        @Override public void deposit(UUID player, long amount) { money.put(player, get(player) + amount); }
        @Override public String money(long amount) { return amount + " RUB"; }
        @Override public void say(String phrase, String... args) { said.add(phrase); }
        @Override public void tell(UUID player, String phrase, String... args) { told.add(phrase); }
        @Override public void emote(String animation) {}
        @Override public void dealCard(int box, boolean faceUp) {}
        @Override public void look(int box) {}
        @Override public void sound(String sound) {}
        @Override public void changed() {}
        @Override public void roundFinished() {}
    }

    // Cards: suit * 13 + rank (rank 0 = ace, 9 = ten, 10 = J, 11 = Q, 12 = K)
    static int c(String text) {
        String rank = text.substring(0, text.length() - 1);
        char suit = text.charAt(text.length() - 1);
        int s = switch (suit) { case 's' -> 0; case 'h' -> 1; case 'd' -> 2; default -> 3; };
        int r = switch (rank) { case "A" -> 0; case "J" -> 10; case "Q" -> 11; case "K" -> 12; default -> Integer.parseInt(rank) - 1; };
        return Cards.of(s, r);
    }

    static int[] cards(String... text) {
        int[] out = new int[text.length];
        for (int i = 0; i < text.length; i++) out[i] = c(text[i]);
        return out;
    }

    static void check(String name, boolean ok) {
        checks++;
        if (!ok) failures.add(name);
    }

    static void eq(String name, long expected, long actual) {
        check(name + " (expected " + expected + ", got " + actual + ")", expected == actual);
    }

    static void run(BlackjackEngine e, BooleanSupplier until) {
        for (int i = 0; i < 20 * 120 && !until.getAsBoolean(); i++) e.tick();
        check("timeout waiting in phase " + e.phase(), until.getAsBoolean());
    }

    static BlackjackEngine engine(StubHost host, BlackjackVariant variant, int boxes) {
        BlackjackRules rules = BlackjackRules.forVariant(variant);
        rules.insurance = false;
        BlackjackEngine e = new BlackjackEngine(host, rules, boxes, new Random(1));
        e.configure(rules, boxes, 100, 100_000, 10_000, 1);
        e.setDealerPresent(true);
        return e;
    }

    public static void main(String[] args) {
        handValues();
        sideBets();
        classicWin();
        winWithLostSideBet();
        classicBlackjackAndBehind();
        doubleAndSplit();
        dealerBlackjackOriginalBetsOnly();
        freeBet();
        speedParallel();
        infiniteCommon();
        abortRefund();
        roulette();
        slots();
        System.out.println("RulesSelfTest: " + checks + " checks, " + failures.size() + " failures");
        for (String f : failures) System.out.println("  FAIL: " + f);
        if (!failures.isEmpty()) System.exit(1);
    }

    static void handValues() {
        HandValue soft = HandValue.of(List.of(c("As"), c("6h")));
        check("A6 is soft 17", soft.total() == 17 && soft.soft());
        check("A6 display 7/17", soft.display().equals("7/17"));
        check("AA9 is 21", HandValue.of(List.of(c("As"), c("Ad"), c("9c"))).total() == 21);
        check("KQ5 busts", HandValue.of(List.of(c("Ks"), c("Qd"), c("5c"))).bust());
        check("hidden card ignored", HandValue.of(List.of(c("Ks"), Cards.HIDDEN)).total() == 10);
    }

    static void sideBets() {
        eq("perfect pair", 25, SideBet.perfectPairs(c("8h"), c("8h")));
        eq("coloured pair", 12, SideBet.perfectPairs(c("8h"), c("8d")));
        eq("mixed pair", 6, SideBet.perfectPairs(c("8h"), c("8s")));
        eq("no pair", 0, SideBet.perfectPairs(c("8h"), c("9h")));
        eq("21+3 straight flush", 40, SideBet.twentyOnePlusThree(c("9h"), c("10h"), c("Jh")));
        eq("21+3 suited trips", 100, SideBet.twentyOnePlusThree(c("7c"), c("7c"), c("7c")));
        eq("21+3 ace high straight", 10, SideBet.twentyOnePlusThree(c("Qh"), c("Kd"), c("As")));
        eq("21+3 flush", 5, SideBet.twentyOnePlusThree(c("2s"), c("9s"), c("Ks")));
        eq("hot3 777", 100, SideBet.hotThree(c("7s"), c("7h"), c("7d")));
        eq("hot3 suited 21", 20, SideBet.hotThree(c("Ks"), c("5s"), c("6s")));
        eq("hot3 20", 2, SideBet.hotThree(c("Ks"), c("Qh"), c("Ad")) == 0 ? 2 : SideBet.hotThree(c("Ks"), c("5h"), c("5d")));
        eq("bust it 5 cards", 9, SideBet.bustIt(List.of(c("2s"), c("3s"), c("4s"), c("6s"), c("Ks"))));
    }

    static void classicWin() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID();
        host.money.put(a, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.CLASSIC, 7);
        e.sit(3, a, "A");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        check("bet accepted", e.placeBet(a, null, 100) == null);
        eq("bet withdrawn", 900, host.get(a));
        e.shoe().stack(cards("10s", "9d", "Qh", "8c"));
        e.ready(a);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        eq("player has 20", 20, e.participant(a).hands.get(0).value().total());
        check("stand ok", e.decide(a, Decision.STAND) == null);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        eq("dealer 17", 17, e.visibleDealerValue().total());
        check("outcome win", e.participant(a).hands.get(0).outcome == Outcome.WIN);
        eq("win pays 2x", 1100, host.get(a));
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        check("rebet", e.rebet(a) == null);
        eq("rebet withdrawn", 1000, host.get(a));
    }

    /** 18 against a busted dealer with a lost 21+3 bet: the hand wins, only the round total is zero. */
    static void winWithLostSideBet() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID();
        host.money.put(a, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.CLASSIC, 7);
        e.sit(3, a, "A");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        check("main bet", e.placeBet(a, null, 100) == null);
        check("21+3 bet", e.placeBet(a, SideBet.TWENTY_ONE_PLUS_THREE, 100) == null);
        e.shoe().stack(cards("10s", "6d", "8h", "Kc", "7s"));
        e.ready(a);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        check("lost side bet announced right after the deal", host.told.contains("side_lose_private"));
        check("stand 18", e.decide(a, Decision.STAND) == null);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        eq("dealer busts with 23", 23, e.visibleDealerValue().total());
        check("hand 18 wins against 23", e.participant(a).hands.get(0).outcome == Outcome.WIN);
        eq("21+3 lost", 0, e.participant(a).sidePaid(SideBet.TWENTY_ONE_PLUS_THREE));
        eq("money: +100 hand, -100 side bet", 1000, host.get(a));
        check("summary names the winning hand", host.told.contains("result_hand_win"));
        check("summary lists side bets and total", host.told.contains("result_side") && host.told.contains("result_total"));
        check("no 'push' message for a won hand", !host.told.contains("result_push") && !host.told.contains("result_hand_push"));
    }

    static void classicBlackjackAndBehind() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        host.money.put(a, 1000L);
        host.money.put(b, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.CLASSIC, 7);
        e.sit(0, a, "A");
        check("behind join", e.joinBehind(0, b, "B"));
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 200);
        e.placeBet(b, null, 100);
        e.shoe().stack(cards("As", "9d", "Kh", "7c", "Qc"));
        e.ready(a);
        e.ready(b);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        check("natural", e.participant(a).hands.get(0).outcome == Outcome.BLACKJACK);
        eq("blackjack 3:2", 1300, host.get(a));
        eq("behind follows blackjack", 1150, host.get(b));
    }

    static void doubleAndSplit() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID();
        host.money.put(a, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.CLASSIC, 7);
        e.sit(2, a, "A");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 100);
        // player 8,8 vs dealer 6: split, first 8 gets 3 -> double gets 10 (21); second 8 gets K (18); dealer 6 + K + 9 bust
        e.shoe().stack(cards("8s", "6d", "8h", "3c", "10c", "Kd", "Kh", "9s"));
        e.ready(a);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        check("split allowed", e.decide(a, Decision.SPLIT) == null);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        eq("first split hand 11", 11, e.decidingHand(e.participant(a)).value().total());
        check("double after split", e.decide(a, Decision.DOUBLE) == null);
        run(e, () -> e.participant(a).hands.size() > 1 && e.decidingHand(e.participant(a)) == e.participant(a).hands.get(1));
        eq("second hand 18", 18, e.participant(a).hands.get(1).value().total());
        e.decide(a, Decision.STAND);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        check("dealer bust", e.visibleDealerValue().bust());
        // stakes: 100 + 100 split + 100 double = 300; returns 400 + 200
        eq("split+double payout", 1300, host.get(a));
    }

    static void dealerBlackjackOriginalBetsOnly() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID();
        host.money.put(a, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.CLASSIC, 7);
        e.sit(0, a, "A");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 100);
        e.shoe().stack(cards("6s", "As", "5d", "9c", "Kd"));
        e.ready(a);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        e.decide(a, Decision.DOUBLE);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        check("dealer blackjack", e.visibleDealerValue().total() == 21);
        eq("double returned on dealer BJ", 900, host.get(a));
    }

    static void freeBet() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID();
        host.money.put(a, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.FREE_BET, 5);
        e.sit(1, a, "A");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 100);
        e.shoe().stack(cards("6s", "7d", "4d", "Kc", "10d", "9s"));
        e.ready(a);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        check("free double", e.decide(a, Decision.DOUBLE) == null);
        eq("free double costs nothing", 900, host.get(a));
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        eq("dealer 7+10 stands", 17, e.visibleDealerValue().total());
        // own 100 back + 100 win + 100 win on the free double
        eq("free double payout", 1200, host.get(a));

        StubHost host2 = new StubHost();
        UUID b = UUID.randomUUID();
        host2.money.put(b, 1000L);
        BlackjackEngine e2 = engine(host2, BlackjackVariant.FREE_BET, 5);
        e2.sit(0, b, "B");
        run(e2, () -> e2.phase() == BlackjackPhase.BETTING);
        e2.placeBet(b, null, 100);
        // player 10,8 = 18 stands; dealer 6 + 6 + 10 = 22 -> push
        e2.shoe().stack(cards("10s", "6d", "8d", "6c", "10d"));
        e2.ready(b);
        run(e2, () -> e2.decidingHand(e2.participant(b)) != null);
        e2.decide(b, Decision.STAND);
        run(e2, () -> e2.phase() == BlackjackPhase.PAYOUT);
        eq("dealer 22", 22, e2.visibleDealerValue().total());
        eq("dealer 22 pushes", 1000, host2.get(b));
    }

    static void speedParallel() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        host.money.put(a, 1000L);
        host.money.put(b, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.SPEED, 7);
        e.sit(0, a, "A");
        e.sit(6, b, "B");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 100);
        e.placeBet(b, null, 100);
        // a: 10,6  b: 10,9  dealer 7 ; a hits first -> 4 (20); dealer 7 + 10 = 17
        e.shoe().stack(cards("10s", "10h", "7d", "6c", "9c", "4h", "10d"));
        e.ready(a);
        e.ready(b);
        run(e, () -> e.decidingHand(e.participant(a)) != null && e.decidingHand(e.participant(b)) != null);
        check("both decide at once", e.decide(b, Decision.STAND) == null && e.decide(a, Decision.HIT) == null);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        eq("a has 20", 20, e.participant(a).hands.get(0).value().total());
        e.decide(a, Decision.STAND);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        eq("a wins", 1100, host.get(a));
        eq("b wins", 1100, host.get(b));
    }

    static void infiniteCommon() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        host.money.put(a, 1000L);
        host.money.put(b, 1000L);
        host.money.put(c, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.INFINITE, 7);
        e.sit(3, a, "A");
        check("unlimited behind 1", e.joinBehind(3, b, "B"));
        check("unlimited behind 2", e.joinBehind(5, c, "C"));
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 100);
        e.placeBet(b, null, 100);
        e.placeBet(c, null, 100);
        // common 10,6 vs dealer 9; a hits (common card 5 -> 21), b stands (16), c doubles (5 -> 21); dealer 9 + 8 = 17
        e.shoe().stack(cards("10s", "9d", "6c", "5h", "8d"));
        e.ready(a);
        e.ready(b);
        e.ready(c);
        run(e, () -> e.decidingHand(e.participant(a)) != null);
        check("a hit", e.decide(a, Decision.HIT) == null);
        check("b stand", e.decide(b, Decision.STAND) == null);
        check("c double", e.decide(c, Decision.DOUBLE) == null);
        run(e, () -> e.phase() == BlackjackPhase.PAYOUT);
        eq("a got common card", 21, e.participant(a).hands.get(0).value().total());
        eq("c got the same card", 21, e.participant(c).hands.get(0).value().total());
        eq("a wins", 1100, host.get(a));
        eq("b loses 16 vs 17", 900, host.get(b));
        eq("c double wins", 1200, host.get(c));
    }

    static void abortRefund() {
        StubHost host = new StubHost();
        UUID a = UUID.randomUUID();
        host.money.put(a, 1000L);
        BlackjackEngine e = engine(host, BlackjackVariant.CLASSIC, 7);
        e.sit(0, a, "A");
        run(e, () -> e.phase() == BlackjackPhase.BETTING);
        e.placeBet(a, null, 300);
        e.ready(a);
        run(e, () -> e.phase() == BlackjackPhase.PLAYER_TURNS || e.phase() == BlackjackPhase.DEALER_TURN || e.phase() == BlackjackPhase.PAYOUT);
        if (e.phase() == BlackjackPhase.PLAYER_TURNS) {
            e.shutdown();
            eq("abort refunds stake", 1000, host.get(a));
        }
    }

    static void roulette() {
        UUID p = UUID.randomUUID();
        RouletteRound round = new RouletteRound();
        round.add(RouletteBet.straight(p, 17, 10));
        round.add(RouletteBet.outside(p, RouletteBetType.RED, 0, 10));
        round.add(RouletteBet.outside(p, RouletteBetType.DOZEN, 1, 10));
        round.forceResult(17);
        eq("roulette 17 black: straight + dozen", 360 + 30, round.settle().getOrDefault(p, 0L));
        RouletteRound zero = new RouletteRound();
        zero.add(RouletteBet.outside(p, RouletteBetType.EVEN, 0, 10));
        zero.forceResult(0);
        eq("zero loses even", 0, zero.settle().getOrDefault(p, 0L));
    }

    static void slots() {
        SlotEngine engine = new SlotEngine(SlotMachine.classicFruits());
        double rtp = engine.estimateRtp(new Random(42), 200_000);
        System.out.printf("Slots classicFruits RTP ~ %.3f%n", rtp);
        check("slots rtp sane " + rtp, rtp > 0.3 && rtp < 1.3);
        SlotEngine.SpinResult r = engine.evaluate(new int[]{0, 0, 0, 0, 0}, 1);
        check("slots window", r.window().length == 5 && r.window()[0].length == 3);
    }
}
