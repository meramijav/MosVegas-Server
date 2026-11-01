package ru.mosvegas.casino.client.state;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.casino.dealer.DealerEntity;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.game.blackjack.TurnMode;
import ru.mosvegas.casino.net.TableSnapshot;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Last snapshot of a table plus the client-side choreography: every new card is scheduled to come out of the shoe
 * in the dealer's hand ({@link Deal}), chips are paid / taken box by box during the payout and the cards are swept
 * into the discard holder during the cleanup. The matching hand animations are started on the dealer from
 * {@link #tick}, so the hands and the objects they carry move together.
 */
public final class ClientTable {
    public record Placed(String key, int card) {}
    public record Sweep(List<SweepCard> cards, float startedAt) {}
    /** A card being collected: table-local position where it lay, orientation and pile index. */
    public record SweepCard(int card, double x, double z, double dirX, double dirZ, int stack) {}
    private record Cue(float at, String move) {}

    /** A card travelling from the shoe through the dealer's hands to its place. */
    public static final class Deal {
        public final float start;
        public final Choreo.DealMode mode;
        public final boolean instant;
        /** Last position in the palm (block-relative): the slide over the felt starts here. */
        public Vec3d held;

        Deal(float start, Choreo.DealMode mode, boolean instant) {
            this.start = start;
            this.mode = mode;
            this.instant = instant;
        }

        public float release() { return Choreo.release(mode); }
    }

    private TableSnapshot snapshot;
    private float receivedAt;
    private final Map<String, Deal> deals = new HashMap<>();
    private final Map<String, Float> flipped = new HashMap<>();
    private final Map<String, Integer> lastCard = new HashMap<>();
    private final Map<UUID, Float> resultShown = new HashMap<>();
    private final Map<Integer, Float> paySlots = new HashMap<>();
    private final Map<Integer, Float> sideSlots = new HashMap<>();
    private float sideSettleEnd = -1000;
    private final List<ChipThrow> throwsInFlight = new ArrayList<>();
    private int hoverSpot = NO_SPOT;
    private float hoverAt = -1000;
    private boolean bettingNotified;
    private final List<Cue> cues = new ArrayList<>();
    private final List<SweepCard> lastLayout = new ArrayList<>();
    private int lastRound = -1;
    private float lastDealStart = -1000;
    private float cleanupStart = -1000;
    private Sweep sweep;
    private boolean collectStarted;
    private boolean collected;
    private boolean decisionNotified;
    private boolean insuranceNotified;

    public static float now(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world == null ? 0 : mc.world.getTime() + tickDelta;
    }

    public void update(TableSnapshot next) {
        float t = now(0);
        TableSnapshot prev = snapshot;
        boolean first = prev == null;
        BlackjackPhase was = first ? BlackjackPhase.NO_DEALER : BlackjackPhase.byId(prev.phase());
        BlackjackPhase phase = BlackjackPhase.byId(next.phase());
        boolean newRound = !first && next.roundId() != lastRound;
        boolean cleared = !first && totalCards(next) == 0 && totalCards(prev) > 0;

        if (!first && phase == BlackjackPhase.CLEANUP && was != BlackjackPhase.CLEANUP) {
            cleanupStart = t;
            startCollect(t);
        }
        if (cleared && !collectStarted) startCollect(t);
        if (newRound || cleared) {
            deals.clear();
            flipped.clear();
            lastCard.clear();
            paySlots.clear();
            sideSlots.clear();
            sideSettleEnd = -1000;
        }
        if (newRound) {
            resultShown.clear();
            collectStarted = false;
            collected = false;
        }
        lastRound = next.roundId();

        // new cards: schedule them one after another out of the shoe
        TableGeometry g = new TableGeometry(TableSize.MEDIUM, next.boxes());
        Set<String> present = new HashSet<>();
        for (Placed p : placements(next)) {
            present.add(p.key);
            if (!deals.containsKey(p.key)) {
                Choreo.DealMode mode;
                String move;
                if (p.key.startsWith("d")) {
                    mode = Choreo.DealMode.SELF;
                    move = "deal_self";
                } else {
                    int bucket = p.key.startsWith("c") ? TableGeometry.bucket(90) : g.boxBucket(boxOf(next, p.key));
                    mode = bucket <= TableGeometry.LAST_LEFT_BUCKET ? Choreo.DealMode.LEFT_HAND : Choreo.DealMode.HANDOFF;
                    move = "deal_" + bucket;
                }
                float start = Math.max(t, lastDealStart + Choreo.DEAL_GAP * 20);
                boolean instant = first || collectStarted || start - t > 30;
                if (!instant) {
                    lastDealStart = start;
                    cues.add(new Cue(start, move));
                }
                deals.put(p.key, new Deal(instant ? -1000 : start, mode, instant));
            }
            Integer before = lastCard.get(p.key);
            if (before != null && before < 0 && p.card >= 0) flipped.put(p.key, t);
            lastCard.put(p.key, p.card);
        }
        deals.keySet().retainAll(present);

        if (!first && was == BlackjackPhase.DEALING && phase != BlackjackPhase.DEALING) startSideSettle(next, g, t);
        if (!first && phase == BlackjackPhase.PAYOUT && was != BlackjackPhase.PAYOUT) startPayout(next, g, t);
        if (!first && phase == BlackjackPhase.BETTING && was != BlackjackPhase.BETTING) bettingNotified = false;

        for (TableSnapshot.Player p : next.players()) {
            boolean hasResult = p.roundPayout() > 0 || p.hands().stream().anyMatch(h -> h.outcome() != 0) || p.followOutcome() != 0;
            if (hasResult) resultShown.putIfAbsent(p.id(), t);
        }
        snapshot = next;
        receivedAt = t;
    }

    /** Dealer's right first, like a real croupier: one reach per box that wins or loses something. */
    private void startPayout(TableSnapshot s, TableGeometry g, float t) {
        paySlots.clear();
        float at = Math.max(t + Choreo.PAY_LEAD * 20, sideSettleEnd);
        for (int box = s.boxes() - 1; box >= 0; box--) {
            boolean moves = false;
            for (TableSnapshot.Player p : s.atBox(box)) moves |= Settlement.movesAtPayout(p);
            if (!moves) continue;
            paySlots.put(box, at);
            cues.add(new Cue(at, "reach_" + g.boxBucket(box)));
            at += Choreo.PAY_SLOT * 20;
        }
    }

    /**
     * Right after the deal the dealer takes the losing pairs / 21+3 / hot 3 bets and pays the winning ones,
     * box by box, before the players decide (as at a live table).
     */
    private void startSideSettle(TableSnapshot s, TableGeometry g, float t) {
        sideSlots.clear();
        float at = Math.max(t + Choreo.PAY_LEAD * 20, lastDealStart + 0.6f * 20);
        for (int box = s.boxes() - 1; box >= 0; box--) {
            boolean moves = false;
            for (TableSnapshot.Player p : s.atBox(box)) moves |= Settlement.movesAfterDeal(p);
            if (!moves) continue;
            sideSlots.put(box, at);
            cues.add(new Cue(at, "reach_" + g.boxBucket(box)));
            at += Choreo.PAY_SLOT * 20;
        }
        sideSettleEnd = at;
    }

    // ------------------------------------------------------------------ chips thrown by the local player

    /** A chip flying from the player's hand to a spot: -1 main bet, otherwise the SideBet ordinal. */
    public record ChipThrow(int spot, long value, Vec3d from, float start, long base) {
        public static final float TICKS = 7f;
    }

    public void throwChip(int spot, long value, Vec3d fromWorld, long currentAmount) {
        throwsInFlight.add(new ChipThrow(spot, value, fromWorld, now(0), currentAmount));
    }

    /** Chips still in the air (older ones are dropped). */
    public List<ChipThrow> chipThrows(float now) {
        throwsInFlight.removeIf(c -> now - c.start() > ChipThrow.TICKS + 2);
        return throwsInFlight;
    }

    /** Spot under the mouse in the table menu (-1 main, SideBet ordinal, or {@link #NO_SPOT}). */
    public static final int NO_SPOT = -9;

    public void hover(int spot, float now) {
        hoverSpot = spot;
        hoverAt = now;
    }

    public int hoveredSpot(float now) { return now - hoverAt < 3 ? hoverSpot : NO_SPOT; }

    /** Edge-triggered: true once when a betting window opens for the seated local player. */
    public boolean consumeBettingNotice() {
        TableSnapshot.Player me = me();
        boolean open = me != null && phase() == BlackjackPhase.BETTING;
        boolean fire = open && !bettingNotified;
        if (open) bettingNotified = true;
        return fire;
    }

    private void startCollect(float t) {
        collectStarted = true;
        if (lastLayout.isEmpty()) return;
        sweep = new Sweep(new ArrayList<>(lastLayout), t);
        cues.add(new Cue(t, "collect"));
    }

    /** Client tick: start the hand moves that are due on the dealer of this table. */
    public void tick(MinecraftClient mc) {
        if (cues.isEmpty() || mc.world == null || snapshot == null) return;
        float t = now(0);
        Entity e = mc.world.getEntityById(snapshot.dealerEntity());
        Iterator<Cue> it = cues.iterator();
        while (it.hasNext()) {
            Cue cue = it.next();
            if (cue.at > t) continue;
            it.remove();
            if (t - cue.at < 10 && e instanceof DealerEntity dealer) dealer.playHands(cue.move);
        }
    }

    private static int totalCards(TableSnapshot s) {
        int n = s.dealerCards().length;
        for (TableSnapshot.Player p : s.players()) for (TableSnapshot.Hand h : p.hands()) n += h.cards().length;
        return n;
    }

    private static int boxOf(TableSnapshot s, String key) {
        int colon = key.indexOf(':');
        if (colon < 0) return 0;
        try {
            TableSnapshot.Player p = s.player(UUID.fromString(key.substring(0, colon)));
            return p == null ? 0 : p.box();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * Key of a card for the choreography. In the common-cards variant the first two cards are shared by
     * everybody and dealt once to the middle of the table ({@code c0}, {@code c1}).
     */
    public static String cardKey(TableSnapshot s, TableSnapshot.Player p, int hand, int index) {
        TurnMode mode = TurnMode.values()[Math.min(s.turnMode(), TurnMode.values().length - 1)];
        if (mode == TurnMode.COMMON && hand == 0 && index < 2) return "c" + index;
        return p.id() + ":" + hand + ":" + index;
    }

    /** Every card on the table, in deal order. */
    public static List<Placed> placements(TableSnapshot s) {
        List<Placed> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < s.dealerCards().length; i++) list.add(new Placed("d" + i, s.dealerCards()[i]));
        for (TableSnapshot.Player p : s.players()) {
            for (int h = 0; h < p.hands().size(); h++) {
                int[] cards = p.hands().get(h).cards();
                for (int i = 0; i < cards.length; i++) {
                    String key = cardKey(s, p, h, i);
                    if (seen.add(key)) list.add(new Placed(key, cards[i]));
                }
            }
        }
        return list;
    }

    /** Remembered by the renderer each frame so the sweep starts from where the cards lie. */
    public void rememberLayout(List<SweepCard> layout) {
        lastLayout.clear();
        lastLayout.addAll(layout);
    }

    public TableSnapshot snapshot() { return snapshot; }
    public Deal deal(String key) { return deals.get(key); }
    public float flippedAt(String key) { return flipped.getOrDefault(key, -1000f); }
    public float resultAt(UUID player) { return resultShown.getOrDefault(player, -1000f); }
    public Sweep sweep() { return sweep; }
    /** The sweep animation is over: the cards are in the discard holder until the server clears them. */
    public void finishSweep() {
        sweep = null;
        collected = true;
    }
    public boolean collected() { return collected; }
    /** Start of the pay/take move at a box during the payout, or null. */
    public Float paySlot(int box) { return paySlots.get(box); }
    /** Start of the move that settles the box's pairs / 21+3 / hot 3 bets after the deal, or null. */
    public Float sideSlot(int box) { return sideSlots.get(box); }
    public float cleanupStart() { return cleanupStart; }

    public BlackjackPhase phase() { return snapshot == null ? BlackjackPhase.NO_DEALER : BlackjackPhase.byId(snapshot.phase()); }
    public TurnMode turnMode() { return snapshot == null ? TurnMode.SEQUENTIAL : TurnMode.values()[Math.min(snapshot.turnMode(), TurnMode.values().length - 1)]; }

    public int remainingTicks(float tickDelta) {
        if (snapshot == null) return 0;
        return Math.max(0, (int) (snapshot.remaining() - (now(tickDelta) - receivedAt)));
    }

    public int remainingTicks(int serverRemaining, float tickDelta) {
        return Math.max(0, (int) (serverRemaining - (now(tickDelta) - receivedAt)));
    }

    public TableSnapshot.Player me() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (snapshot == null || mc.player == null) return null;
        return snapshot.player(mc.player.getUuid());
    }

    /** Edge-triggered: true once when a new decision becomes available to the local player. */
    public boolean consumeDecisionNotice() {
        TableSnapshot.Player me = me();
        boolean deciding = me != null && me.decidingHand() >= 0 && me.decisionMask() != 0;
        boolean fire = deciding && !decisionNotified;
        decisionNotified = deciding;
        return fire;
    }

    public boolean consumeInsuranceNotice() {
        TableSnapshot.Player me = me();
        boolean offered = me != null && phase() == BlackjackPhase.INSURANCE && me.playing() && me.controls() && !me.insuranceDecided();
        boolean fire = offered && !insuranceNotified;
        insuranceNotified = offered;
        return fire;
    }
}
