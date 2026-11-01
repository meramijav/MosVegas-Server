package ru.mosvegas.casino.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.casino.client.dealer.DealerHands;
import ru.mosvegas.casino.client.screen.BlackjackScreen;
import ru.mosvegas.casino.client.state.Choreo;
import ru.mosvegas.casino.client.state.BetSpots;
import ru.mosvegas.casino.client.state.ClientTable;
import ru.mosvegas.casino.client.state.Settlement;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.game.blackjack.Outcome;
import ru.mosvegas.casino.game.blackjack.SideBet;
import ru.mosvegas.casino.game.blackjack.TurnMode;
import ru.mosvegas.casino.net.TableSnapshot;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cards, chips and floating labels drawn on top of the GeckoLib table model. Cards and chips travel in the
 * dealer's palms (see {@link Choreo} and {@link DealerHands}); without a rendered dealer they take a direct path.
 */
public final class TableOverlay {
    public static final float CARD_W = 0.19f;
    public static final float CARD_H = 0.266f;
    private static final float FLIP_TICKS = 6f;
    private static final float CHIP_R = 0.042f;
    private static final float CHIP_T = 0.0105f;
    private static final double Y = TableGeometry.FELT_Y + 0.004;
    /** A card or a chip stack held in the hand lies this far below the palm bone. */
    private static final double UNDER_PALM = 0.11;

    private TableOverlay() {}

    private record Ctx(MatrixStack ms, VertexConsumerProvider vcp, Direction facing, TableGeometry g, int light, float now,
                       ClientTable table, TableSnapshot s, Identifier back, Vec3d origin, DealerHands.Pose pose) {
        Vec3d at(double lx, double ly, double lz) { return TableGeometry.toBlockOffset(facing, lx, ly, lz); }
        Vec3d at(TableGeometry.Local l, double ly) { return at(l.x(), ly, l.z()); }
        Vec3d dir(double dx, double dz) {
            Vec3d r = TableGeometry.right(facing), f = TableGeometry.forward(facing);
            return r.multiply(dx).add(f.multiply(dz)).normalize();
        }
        /** The dealer's palm relative to the block, or null when the dealer is not rendered this frame. */
        Vec3d palm(boolean left) { return pose == null ? null : pose.hand(left).subtract(origin); }
        /** Where something held in that hand is drawn. */
        Vec3d inHand(Vec3d palm) { return new Vec3d(palm.x, Math.max(palm.y - UNDER_PALM, TableGeometry.FELT_Y + 0.008), palm.z); }
    }

    public static void render(Direction facing, TableSize size, ClientTable table, float tickDelta, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        if (table == null || table.snapshot() == null) return;
        TableSnapshot s = table.snapshot();
        float now = ClientTable.now(tickDelta);
        DealerHands.Pose pose = s.dealerEntity() >= 0 ? DealerHands.get(s.dealerEntity(), now) : null;
        Ctx c = new Ctx(ms, vcp, facing, new TableGeometry(size, s.boxes()), light, now, table, s, CasinoTextures.back(s.style()),
                Vec3d.of(s.pos()), pose);
        List<ClientTable.SweepCard> layout = new ArrayList<>();
        if (table.sweep() == null && !table.collected()) {
            drawDealer(c, layout);
            boolean common = TurnMode.values()[Math.min(s.turnMode(), 2)] == TurnMode.COMMON;
            if (common) drawCommon(c, layout);
            for (TableSnapshot.Player p : s.players()) if (p.owner() && !p.hands().isEmpty()) drawHands(c, p, common, layout);
        }
        drawChips(c);
        drawLabels(c, tickDelta);
        if (!layout.isEmpty()) table.rememberLayout(layout);
        if (table.sweep() != null) drawSweep(c);
    }

    private static Vec3d leftOf(Vec3d forward) { return new Vec3d(forward.z, 0, -forward.x); }

    // ------------------------------------------------------------------ cards

    /** True once the card is lying in its place (labels wait for it). */
    private static boolean landed(Ctx c, String key) {
        ClientTable.Deal deal = c.table.deal(key);
        return deal == null || deal.instant || (c.now - deal.start) / 20f >= deal.release() + Choreo.SLIDE;
    }

    private static void drawCard(Ctx c, String key, int card, Vec3d target, Vec3d top) {
        ClientTable.Deal deal = c.table.deal(key);
        boolean faceDown = card < 0;
        Identifier front = faceDown ? c.back : CasinoTextures.face(card);
        Vec3d pos = target;
        float flip = 0f;
        boolean moving = false;
        if (deal != null && !deal.instant) {
            float t = (c.now - deal.start) / 20f;
            float release = deal.release();
            if (t < release + Choreo.SLIDE) {
                if (t < Choreo.GRAB - 0.05f) return; // still in the shoe
                moving = true;
                Vec3d held = held(c, deal, t, target);
                if (t < Choreo.GRAB) {
                    Vec3d mouth = c.at(c.g.shoeMouth(), TableGeometry.FELT_Y + 0.12);
                    pos = mouth.lerp(held, (t - (Choreo.GRAB - 0.05f)) / 0.05f);
                    flip = 1f;
                } else if (t < release) {
                    pos = held;
                    deal.held = held;
                    flip = 1f;
                } else {
                    // let go: the card slides over the felt and turns face up on the way
                    float e = Choreo.easeOut((t - release) / Choreo.SLIDE);
                    Vec3d from = deal.held != null ? deal.held : held;
                    pos = from.lerp(target, e).add(0, Math.sin(e * Math.PI) * 0.012, 0);
                    flip = 1f - e;
                }
            }
        }
        if (!moving && !faceDown) {
            float f = (c.now - c.table.flippedAt(key)) / FLIP_TICKS;
            if (f >= 0 && f < 1) {
                flip = 1 - f;
                pos = pos.add(0, Math.sin(f * Math.PI) * 0.05, 0);
            }
        }
        CasinoDraw.card(c.ms, c.vcp, front, c.back, pos, top, leftOf(top), CARD_W, CARD_H, faceDown ? 0f : flip, c.light);
    }

    /** Position of a card in the dealer's hand at time t of its deal. */
    private static Vec3d held(Ctx c, ClientTable.Deal deal, float t, Vec3d target) {
        Vec3d left = c.palm(true), right = c.palm(false);
        if (left == null || right == null) return c.inHand(fallbackPalm(c, deal, t, target));
        if (deal.mode != Choreo.DealMode.HANDOFF) return c.inHand(left);
        float k = MathHelper.clamp((t - (Choreo.HANDOFF - 0.02f)) / 0.05f, 0f, 1f);
        return c.inHand(left.lerp(right, Choreo.ease(k)));
    }

    /** Dealer not rendered: straight from the shoe towards the target, released in front of the dealer. */
    private static Vec3d fallbackPalm(Ctx c, ClientTable.Deal deal, float t, Vec3d target) {
        Vec3d seat = c.at(c.g.dealerSeat(), 1.0);
        Vec3d flat = new Vec3d(target.x - seat.x, 0, target.z - seat.z);
        Vec3d release = flat.lengthSquared() < 1e-6 ? seat : seat.add(flat.normalize().multiply(0.75));
        Vec3d mouth = c.at(c.g.shoeMouth(), 1.0);
        float k = MathHelper.clamp((t - Choreo.GRAB) / (deal.release() - Choreo.GRAB), 0f, 1f);
        return mouth.lerp(release, Choreo.ease(k));
    }

    private static void drawDealer(Ctx c, List<ClientTable.SweepCard> layout) {
        int[] cards = c.s.dealerCards();
        Vec3d top = c.dir(0, -1);
        for (int i = 0; i < cards.length; i++) {
            TableGeometry.Local l = c.g.dealerCard(i);
            drawCard(c, "d" + i, cards[i], c.at(l, Y + i * 0.0012), top);
            layout.add(new ClientTable.SweepCard(cards[i], l.x(), l.z(), 0, -1, i));
        }
    }

    /** Shared first two cards of the common-cards variant, in the middle of the table. */
    private static void drawCommon(Ctx c, List<ClientTable.SweepCard> layout) {
        TableSnapshot.Player any = null;
        for (TableSnapshot.Player p : c.s.players()) if (p.playing() && !p.hands().isEmpty() && p.hands().get(0).cards().length >= 1) any = p;
        if (any == null) return;
        int[] cards = any.hands().get(0).cards();
        int n = Math.min(2, cards.length);
        Vec3d top = c.dir(0, -1);
        TableGeometry.Local center = c.g.commonCards();
        for (int i = 0; i < n; i++) {
            double x = center.x() + (i - (n - 1) / 2.0) * 0.17;
            drawCard(c, "c" + i, cards[i], c.at(x, Y, center.z()), top);
            layout.add(new ClientTable.SweepCard(cards[i], x, center.z(), 0, -1, i));
        }
    }

    private static Vec3d handCardPos(Ctx c, int box, int hand, int hands, int index) {
        TableGeometry.Local anchor = c.g.cardAnchor(box), t = c.g.tangent(box), u = c.g.inward(box);
        double spacing = c.g.size == TableSize.LARGE ? 0.30 : 0.33;
        double off = (hand - (hands - 1) / 2.0) * spacing;
        double x = anchor.x() + t.x() * (off + 0.055 * index) + u.x() * (0.075 * index);
        double z = anchor.z() + t.z() * (off + 0.055 * index) + u.z() * (0.075 * index);
        return new Vec3d(x, 0, z);
    }

    /** Fan index of a card at its box (the shared cards of the common variant lie in the middle instead). */
    private static int fanIndex(boolean common, int hand, int index) {
        return common && hand == 0 ? index - 2 : index;
    }

    private static void drawHands(Ctx c, TableSnapshot.Player p, boolean common, List<ClientTable.SweepCard> layout) {
        TableGeometry.Local u = c.g.inward(p.box());
        Vec3d top = c.dir(u.x(), u.z());
        int hands = p.hands().size();
        for (int h = 0; h < hands; h++) {
            int[] cards = p.hands().get(h).cards();
            for (int i = 0; i < cards.length; i++) {
                if (common && h == 0 && i < 2) continue;
                int fan = fanIndex(common, h, i);
                Vec3d local = handCardPos(c, p.box(), h, hands, fan);
                drawCard(c, ClientTable.cardKey(c.s, p, h, i), cards[i], c.at(local.x, Y + fan * 0.0012 + h * 0.0004, local.z), top);
                layout.add(new ClientTable.SweepCard(cards[i], local.x, local.z, u.x(), u.z(), fan));
            }
        }
    }

    /** End of the round: the hands sweep the cards together and drop the pile into the discard holder. */
    private static void drawSweep(Ctx c) {
        ClientTable.Sweep sweep = c.table.sweep();
        float t = (c.now - sweep.startedAt()) / 20f;
        if (t >= Choreo.COLLECT_END || t < 0) {
            c.table.finishSweep();
            return;
        }
        Vec3d drop = c.at(c.g.discardDrop(), TableGeometry.FELT_Y + 0.10);
        int index = 0;
        for (ClientTable.SweepCard card : sweep.cards()) {
            index++;
            double angle = TableGeometry.angleOf(card.x(), card.z());
            boolean leftSide = angle >= 90;
            float pick = Choreo.pickTime(angle);
            Vec3d spot = c.at(card.x(), Y + card.stack() * 0.0012, card.z());
            Vec3d top = c.dir(card.dirX(), card.dirZ());
            Vec3d pos;
            boolean faceUp = t < pick;
            if (t < pick) pos = spot;
            else {
                Vec3d inHand = c.inHand(sweepPalm(c, leftSide, t)).add(0, index * 0.0015, 0);
                if (t < pick + Choreo.PULL) pos = spot.lerp(inHand, Choreo.ease((t - pick) / Choreo.PULL));
                else if (t < Choreo.DROP_FROM) pos = inHand;
                else if (t < Choreo.DROP_TO) pos = inHand.lerp(drop, Choreo.ease((t - Choreo.DROP_FROM) / (Choreo.DROP_TO - Choreo.DROP_FROM)));
                else continue; // in the discard holder
            }
            Identifier front = faceUp && card.card() >= 0 ? CasinoTextures.face(card.card()) : c.back;
            CasinoDraw.card(c.ms, c.vcp, front, c.back, pos, top, leftOf(top), CARD_W, CARD_H, 0, c.light);
        }
    }

    private static Vec3d sweepPalm(Ctx c, boolean leftSide, float t) {
        Vec3d left = c.palm(true), right = c.palm(false);
        if (left == null || right == null) {
            // no dealer on screen: gather in front of the tray, then to the discard holder
            Vec3d gather = c.at(leftSide ? -0.07 : 0.07, 1.0, -0.14);
            return t < Choreo.DROP_FROM ? gather : gather.lerp(c.at(c.g.discardDrop(), 1.05), Choreo.ease((t - Choreo.DROP_FROM) / 0.12f));
        }
        if (!leftSide) return right;
        float k = MathHelper.clamp((t - Choreo.MERGE_FROM) / (Choreo.MERGE_TO - Choreo.MERGE_FROM), 0f, 1f);
        return left.lerp(right, Choreo.ease(k));
    }

    // ------------------------------------------------------------------ chips

    static List<Long> decompose(long amount, List<Long> chips) {
        List<Long> out = new ArrayList<>();
        if (amount <= 0 || chips.isEmpty()) return out;
        for (int i = chips.size() - 1; i >= 0 && out.size() < 24; i--) {
            long v = chips.get(i);
            while (amount >= v && out.size() < 24) {
                out.add(v);
                amount -= v;
            }
        }
        if (amount > 0 && out.size() < 24) out.add(chips.get(0));
        return out;
    }

    private static final long[] NO_PILE = new long[0];

    /** A chip stack whose lowest chip lies at {@code base} (block-relative), in the casino's own chips. */
    private static void stackAt(Ctx c, Vec3d base, long amount) { stackAt(c, base, amount, NO_PILE); }

    /** The chips a player put down, bottom-up, then whatever else makes up the amount (a double, a split). */
    private static List<Long> chipsFor(Ctx c, long[] pile, long amount) {
        List<Long> out = new ArrayList<>();
        long sum = 0;
        for (long v : pile) {
            if (sum + v > amount || out.size() >= 40) break;
            out.addAll(decompose(v, c.s.chips()));
            sum += v;
        }
        if (sum < amount) out.addAll(decompose(amount - sum, c.s.chips()));
        return out.size() > 40 ? out.subList(0, 40) : out;
    }

    private static void stackAt(Ctx c, Vec3d base, long amount, long[] pile) {
        List<Long> chips = chipsFor(c, pile, amount);
        for (int i = 0; i < chips.size(); i++) {
            double jx = ((i * 37) % 7 - 3) * 0.0007, jz = ((i * 53) % 7 - 3) * 0.0007;
            CasinoDraw.chip(c.ms, c.vcp, CasinoTextures.chip(chips.get(i), c.s.chips()), base.add(jx, i * CHIP_T, jz), CHIP_R, CHIP_T, c.light);
        }
    }

    private static Vec3d felt(Ctx c, double lx, double lz) { return c.at(lx, TableGeometry.FELT_Y + 0.001, lz); }

    /** The hand that serves a box during the payout, at time t of its reach (or a direct path without a dealer). */
    private static Vec3d payPalm(Ctx c, boolean leftHand, float t, Vec3d bet) {
        Vec3d palm = c.palm(leftHand);
        if (palm != null) return c.inHand(palm);
        Vec3d tray = c.at(leftHand ? -0.18 : 0.18, TableGeometry.FELT_Y + 0.02, -0.30);
        Vec3d seat = c.at(c.g.dealerSeat(), TableGeometry.FELT_Y + 0.02);
        Vec3d flat = new Vec3d(bet.x - seat.x, 0, bet.z - seat.z);
        Vec3d reach = flat.lengthSquared() < 1e-6 ? seat : seat.add(flat.normalize().multiply(0.8));
        if (t < Choreo.REACH_AT_BET) return tray.lerp(reach, Choreo.ease((t - Choreo.REACH_AT_TRAY) / (Choreo.REACH_AT_BET - Choreo.REACH_AT_TRAY)));
        return reach.lerp(tray, Choreo.ease((t - Choreo.REACH_AT_BET) / (Choreo.REACH_BACK - Choreo.REACH_AT_BET)));
    }

    /** Lost chips: pulled into the dealer's hand at the bet and dropped into the tray. */
    private static void drawTaken(Ctx c, Vec3d spot, long amount, long[] pile, float t, boolean leftHand) {
        if (t < Choreo.REACH_PICK) {
            stackAt(c, spot, amount, pile);
            return;
        }
        if (t >= Choreo.REACH_BACK + Choreo.REACH_DROP) return;
        Vec3d hand = payPalm(c, leftHand, t, spot);
        Vec3d pos;
        if (t < Choreo.REACH_AT_BET) pos = spot.lerp(hand, Choreo.ease((t - Choreo.REACH_PICK) / (Choreo.REACH_AT_BET - Choreo.REACH_PICK)));
        else if (t < Choreo.REACH_BACK) pos = hand;
        else pos = hand.add(0, -0.03 * (t - Choreo.REACH_BACK) / Choreo.REACH_DROP, 0);
        stackAt(c, pos, amount, pile);
    }

    /** Winnings: taken from the tray, carried out and put down next to the bet. */
    private static void drawPaid(Ctx c, Vec3d target, long amount, float t, boolean leftHand) {
        if (t < Choreo.REACH_AT_TRAY) return;
        if (t >= Choreo.REACH_AT_BET + 0.12f) {
            stackAt(c, target, amount);
            return;
        }
        Vec3d hand = payPalm(c, leftHand, Math.min(t, Choreo.REACH_AT_BET), target);
        Vec3d pos = t < Choreo.REACH_AT_BET ? hand : hand.lerp(target, Choreo.easeOut((t - Choreo.REACH_AT_BET) / 0.12f));
        stackAt(c, pos, amount);
    }

    /** After the payout the players take what is left on their spots. */
    private static void drawLeaving(Ctx c, Vec3d spot, long amount, long[] pile, Vec3d seat, float sinceCleanup) {
        if (sinceCleanup < 0.1f) stackAt(c, spot, amount, pile);
        else if (sinceCleanup < 0.5f) stackAt(c, spot.lerp(seat, Choreo.ease((sinceCleanup - 0.1f) / 0.4f)), amount, pile);
    }

    /** One stack of a settled bet, by phase: lost chips go to the dealer, winnings come from the tray. */
    private static void settledStack(Ctx c, Vec3d spot, long stake, long[] pile, long payout, Vec3d winSpot, float t, boolean leftHand,
                                     boolean cleanup, float sinceCleanup, Vec3d seat) {
        if (stake <= 0) return;
        if (payout == 0) {
            if (!cleanup) drawTaken(c, spot, stake, pile, t, leftHand);
            return;
        }
        if (cleanup) drawLeaving(c, spot, stake, pile, seat, sinceCleanup);
        else stackAt(c, spot, stake, pile);
        long win = payout - stake;
        if (win <= 0) return;
        if (cleanup) drawLeaving(c, winSpot, win, NO_PILE, seat, sinceCleanup);
        else drawPaid(c, winSpot, win, t, leftHand);
    }

    /** The local player's own stacks lag behind while a thrown chip is still in the air. */
    private static long shownAmount(Ctx c, int spot, long amount) {
        long shown = amount;
        for (ClientTable.ChipThrow t : c.table.chipThrows(c.now)) {
            if (t.spot() == spot && c.now - t.start() < ClientTable.ChipThrow.TICKS) shown = Math.min(shown, t.base());
        }
        return shown;
    }

    /** Thrown chips fly in an arc from the player's hand and land on top of their pile. */
    private static void drawThrows(Ctx c, Map<Integer, Vec3d> myStacks, Map<Integer, long[]> myPiles) {
        for (ClientTable.ChipThrow t : c.table.chipThrows(c.now)) {
            Vec3d stack = myStacks.get(t.spot());
            float k = (c.now - t.start()) / ClientTable.ChipThrow.TICKS;
            if (stack == null || k < 0 || k >= 1) continue;
            Vec3d to = stack.add(0, chipsFor(c, myPiles.getOrDefault(t.spot(), NO_PILE), t.base()).size() * CHIP_T, 0);
            Vec3d from = t.from().subtract(c.origin);
            float e = Choreo.easeOut(k);
            Vec3d pos = from.lerp(to, e).add(0, Math.sin(k * Math.PI) * 0.22, 0);
            CasinoDraw.chip(c.ms, c.vcp, CasinoTextures.chip(t.value(), c.s.chips()), pos, CHIP_R, CHIP_T, c.light);
        }
    }

    private static final Identifier RING = new Identifier("mosvegas_casino", "textures/misc/spot_ring.png");

    /** While betting: the player's own spots glow softly, the one under the mouse brightly. */
    private static void drawSpotGlow(Ctx c, Map<Integer, Vec3d> myStacks) {
        int hovered = c.table.hoveredSpot(c.now);
        boolean menu = MinecraftClient.getInstance().currentScreen instanceof BlackjackScreen;
        float pulse = 0.5f + 0.5f * (float) Math.sin(c.now * 0.15);
        for (Map.Entry<Integer, Vec3d> e : myStacks.entrySet()) {
            int spot = e.getKey();
            boolean hover = menu && spot == hovered;
            float r = (float) BetSpots.radius(spot) * (hover ? 1.35f : 1.15f);
            int alpha = hover ? 230 : spot == BetSpots.MAIN ? (int) (70 + 60 * pulse) : (int) (35 + 30 * pulse);
            int color = (alpha << 24) | (hover ? 0xFFE38A : 0xE0B040);
            CasinoDraw.decal(c.ms, c.vcp, RING, e.getValue().add(0, 0.003, 0), r, color);
        }
    }

    private static void drawChips(Ctx c) {
        BlackjackPhase phase = BlackjackPhase.byId(c.s.phase());
        boolean payout = phase == BlackjackPhase.PAYOUT, cleanup = phase == BlackjackPhase.CLEANUP;
        float sinceCleanup = (c.now - c.table.cleanupStart()) / 20f;
        TableSnapshot.Player me = c.table.me();
        Map<Integer, Vec3d> myStacks = new HashMap<>();
        Map<Integer, long[]> myPiles = new HashMap<>();
        for (int box = 0; box < c.s.boxes(); box++) {
            List<TableSnapshot.Player> people = c.s.atBox(box);
            TableGeometry.Local spot = c.g.betSpot(box), t = c.g.tangent(box), in = c.g.inward(box);
            Float slot = c.table.paySlot(box);
            float tb = slot == null ? -100f : (c.now - slot) / 20f;
            boolean leftHand = c.g.boxBucket(box) <= TableGeometry.LAST_LEFT_BUCKET;
            Vec3d seat = c.at(c.g.polar(c.g.boxAngle(box), c.g.radius + 0.1), TableGeometry.FELT_Y + 0.02);
            Float early = c.table.sideSlot(box);
            for (TableSnapshot.Player p : people) {
                boolean mine = me != null && p.id().equals(me.id());
                Map<Integer, TableGeometry.Local> spots = BetSpots.of(c.g, c.s, p);
                long stake = Settlement.mainStake(p);
                TableGeometry.Local main = spots.get(BetSpots.MAIN);
                double x = main.x(), z = main.z();
                boolean settling = (payout || cleanup) && p.playing();
                Vec3d spotPos = felt(c, x, z);
                long[] mainPile = p.pile(BetSpots.MAIN);
                if (settling) {
                    settledStack(c, spotPos, stake, mainPile, Settlement.mainPayout(p), felt(c, x + t.x() * 0.095, z + t.z() * 0.095),
                            tb, leftHand, cleanup, sinceCleanup, seat);
                } else if (stake > 0 && !(cleanup && !p.playing())) {
                    stackAt(c, spotPos, mine ? shownAmount(c, BetSpots.MAIN, stake) : stake, mainPile);
                }
                if (mine) {
                    myStacks.put(BetSpots.MAIN, spotPos);
                    myPiles.put(BetSpots.MAIN, mainPile);
                }
                if (!p.owner()) continue;
                for (SideBet bet : SideBet.values()) {
                    TableGeometry.Local ss = spots.get(bet.ordinal());
                    if (ss == null) continue;
                    Vec3d at = felt(c, ss.x(), ss.z());
                    long[] sidePile = p.pile(bet.ordinal());
                    if (mine) {
                        myStacks.put(bet.ordinal(), at);
                        myPiles.put(bet.ordinal(), sidePile);
                    }
                    long amount = Settlement.sideStake(p, bet.ordinal());
                    if (amount <= 0) continue;
                    long paid = Settlement.sidePayout(p, bet.ordinal());
                    Vec3d winAt = felt(c, ss.x() + in.x() * 0.09, ss.z() + in.z() * 0.09);
                    if (bet.resolvedAfterDeal() && early != null) {
                        // pairs / 21+3 / hot 3 were settled by hand right after the deal
                        settledStack(c, at, amount, sidePile, paid, winAt, (c.now - early) / 20f, leftHand, cleanup, sinceCleanup, seat);
                    } else if (settling) {
                        settledStack(c, at, amount, sidePile, paid, winAt, tb, leftHand, cleanup, sinceCleanup, seat);
                    } else stackAt(c, at, mine ? shownAmount(c, bet.ordinal(), amount) : amount, sidePile);
                }
                if (p.insurance() > 0) {
                    TableGeometry.Local ins = c.g.insuranceSpot(box);
                    if (settling) {
                        settledStack(c, felt(c, ins.x(), ins.z()), p.insurance(), NO_PILE, p.insurancePayout(),
                                felt(c, ins.x() + t.x() * 0.09, ins.z() + t.z() * 0.09), tb, leftHand, cleanup, sinceCleanup, seat);
                    } else stackAt(c, felt(c, ins.x(), ins.z()), p.insurance());
                }
            }
        }
        if (me != null && phase == BlackjackPhase.BETTING) drawSpotGlow(c, myStacks);
        drawThrows(c, myStacks, myPiles);
    }

    // ------------------------------------------------------------------ labels

    private static int outcomeColor(Outcome o) {
        return switch (o) {
            case WIN, CHARLIE -> 0xFF55FF55;
            case BLACKJACK -> 0xFFFFD84A;
            case PUSH -> 0xFFFFFF88;
            case SURRENDER -> 0xFFDDDDDD;
            default -> 0xFFFF5555;
        };
    }

    private static void drawLabels(Ctx c, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int bg = (int) (mc.options.getTextBackgroundOpacity(0.35f) * 255f) << 24;
        TableSnapshot s = c.s;
        BlackjackPhase phase = BlackjackPhase.byId(s.phase());
        boolean cardsVisible = c.table.sweep() == null && !c.table.collected();
        boolean common = TurnMode.values()[Math.min(s.turnMode(), 2)] == TurnMode.COMMON;
        // dealer total (once the last card has landed)
        if (cardsVisible && s.dealerCards().length > 0 && !s.dealerValue().isEmpty() && landed(c, "d" + (s.dealerCards().length - 1))) {
            TableGeometry.Local l = c.g.dealerCard(Math.max(0, s.dealerCards().length - 1) / 2);
            Vec3d at = c.at(l.x() + 0.07, TableGeometry.FELT_Y + 0.2, l.z());
            int color = s.dealerValue().equals("21") ? 0xFF55FF55 : 0xFFFFFFFF;
            try {
                if (Integer.parseInt(s.dealerValue()) > 21) color = 0xFFFF5555;
            } catch (NumberFormatException ignored) {}
            CasinoDraw.label(c.ms, c.vcp, at, Text.literal(s.dealerValue()), color, bg, 0.012f, c.light);
        }
        // hands
        for (TableSnapshot.Player p : s.players()) {
            if (!cardsVisible || !p.owner() || p.hands().isEmpty()) continue;
            int hands = p.hands().size();
            for (int h = 0; h < hands; h++) {
                TableSnapshot.Hand hand = p.hands().get(h);
                int last = hand.cards().length - 1;
                if (last < 0 || !landed(c, ClientTable.cardKey(s, p, h, last))) continue;
                Vec3d local = handCardPos(c, p.box(), h, hands, Math.max(0, fanIndex(common, h, last)));
                Vec3d at = c.at(local.x, TableGeometry.FELT_Y + 0.13, local.z);
                Outcome outcome = Outcome.byId(hand.outcome());
                String value = hand.has(TableSnapshot.HAND_NATURAL) ? "BJ" : hand.value();
                int color = hand.has(TableSnapshot.HAND_BUST) ? 0xFFFF5555 : hand.has(TableSnapshot.HAND_NATURAL) ? 0xFFFFD84A
                        : "21".equals(value) ? 0xFF55FF55 : 0xFFFFFFFF;
                if (outcome != Outcome.NONE) {
                    Text[] lines = {Text.translatable("mosvegas_casino.outcome." + outcome.name().toLowerCase()), Text.literal(value)};
                    CasinoDraw.labels(c.ms, c.vcp, at, lines, new int[]{outcomeColor(outcome), color}, bg, 0.0085f, c.light);
                } else {
                    CasinoDraw.label(c.ms, c.vcp, at, Text.literal(value), color, bg, 0.009f, c.light);
                }
            }
        }
        // active box marker
        if (phase == BlackjackPhase.PLAYER_TURNS && s.activeBox() >= 0) {
            TableGeometry.Local seat = c.g.seat(s.activeBox());
            float bob = (float) Math.sin(c.now * 0.25) * 0.03f;
            CasinoDraw.label(c.ms, c.vcp, c.at(seat, 2.35 + bob), Text.literal("▼"), 0xFFFFD84A, 0, 0.03f, c.light);
        }
        // phase and timer above the table
        int remaining = c.table.remainingTicks(tickDelta);
        Text title = Text.translatable("mosvegas_casino.phase." + phase.name().toLowerCase());
        String seconds = "";
        if (phase == BlackjackPhase.BETTING || phase == BlackjackPhase.INSURANCE) seconds = Integer.toString((remaining + 19) / 20);
        if (phase == BlackjackPhase.PLAYER_TURNS) {
            int handRemaining = 0;
            for (TableSnapshot.Player p : s.players()) for (TableSnapshot.Hand h : p.hands()) handRemaining = Math.max(handRemaining, c.table.remainingTicks(h.remaining(), tickDelta));
            if (handRemaining > 0) seconds = Integer.toString((handRemaining + 19) / 20);
            if (s.activePlayer() != null) {
                TableSnapshot.Player a = s.player(s.activePlayer());
                if (a != null) title = Text.literal(a.name()).append(Text.translatable("mosvegas_casino.phase.decides"));
            }
        }
        // stacked over the dealer: name tag (lowered while sitting) < phase < speech bubble; the open menu has its own banner
        Vec3d at = c.at(c.g.dealerSeat(), 2.12);
        if (mc.currentScreen instanceof BlackjackScreen) {
            // nothing: the banner of the screen shows phase and timer
        } else if (!seconds.isEmpty()) {
            CasinoDraw.labels(c.ms, c.vcp, at, new Text[]{title, Text.literal(seconds)}, new int[]{0xFFFFE9A8, 0xFFFFFFFF}, bg, 0.014f, c.light);
        } else if (phase != BlackjackPhase.DEALING && phase != BlackjackPhase.DEALER_TURN) {
            CasinoDraw.label(c.ms, c.vcp, at, title, 0xFFFFE9A8, bg, 0.014f, c.light);
        }
        // free box hint for people walking by: only the chair nearest to the viewer, so the labels never pile up
        if (mc.player != null && s.player(mc.player.getUuid()) == null && !s.closed()) {
            int nearest = -1;
            double best = 36;
            for (int box = 0; box < s.boxes(); box++) {
                if (s.owner(box) != null) continue;
                double d = mc.player.getPos().squaredDistanceTo(Vec3d.of(s.pos()).add(c.at(c.g.seat(box), 0.95)));
                if (d < best) {
                    best = d;
                    nearest = box;
                }
            }
            if (nearest >= 0) {
                CasinoDraw.label(c.ms, c.vcp, c.at(c.g.seat(nearest), 1.05), Text.translatable("mosvegas_casino.hint.free_box", nearest + 1),
                        0xFFDDDDDD, bg, 0.010f, c.light);
            }
        }
        // win popups
        for (TableSnapshot.Player p : s.players()) {
            if (!p.playing() || p.roundPayout() <= 0) continue;
            float shown = c.table.resultAt(p.id());
            float age = c.now - shown;
            if (age < 0 || age > 60) continue;
            long staked = p.main() + sum(p.side()) + p.insurance();
            for (int i = 1; i < p.hands().size(); i++) staked += p.hands().get(i).bet();
            if (!p.hands().isEmpty()) staked += Math.max(0, p.hands().get(0).bet() - p.main());
            long net = p.roundPayout() - staked;
            if (net <= 0) continue;
            TableGeometry.Local spot = c.g.betSpot(p.box());
            Vec3d pos = c.at(spot, TableGeometry.FELT_Y + 0.2 + age * 0.003);
            CasinoDraw.label(c.ms, c.vcp, pos, Text.literal("+" + String.format("%,d", net).replace(',', ' ')), 0xFF55FF55, 0, 0.0095f, c.light);
        }
    }

    private static long sum(long[] values) {
        long t = 0;
        for (long v : values) t += v;
        return t;
    }
}
