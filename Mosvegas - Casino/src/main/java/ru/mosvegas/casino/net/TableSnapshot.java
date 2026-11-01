package ru.mosvegas.casino.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything a client needs to draw a table and its menu. Built on the server from the engine,
 * read on the client. The dealer's face-down card is sent as -1 and never leaves the server.
 */
public record TableSnapshot(
        BlockPos pos,
        String style,
        String styleName,
        int variant,
        int turnMode,
        int boxes,
        int phase,
        int remaining,
        int phaseLength,
        long minBet,
        long maxBet,
        long sideMax,
        int sideBetMask,
        List<Long> chips,
        int dealerEntity,
        String dealerName,
        boolean dealerPresent,
        int[] dealerCards,
        String dealerValue,
        int roundId,
        int shoeRemaining,
        int shoeSize,
        int activeBox,
        UUID activePlayer,
        int activeHand,
        boolean commonOpen,
        boolean closed,
        String status,
        String currency,
        List<Player> players,
        long balance
) {
    public static final int HAND_DOUBLED = 1, HAND_SPLIT = 2, HAND_SURRENDER = 4, HAND_DONE = 8, HAND_AWAITING = 16,
            HAND_PENDING = 32, HAND_CHARLIE = 64, HAND_NATURAL = 128, HAND_BUST = 256;

    public record Hand(int[] cards, long bet, long freeBet, int flags, int outcome, long payout, int remaining, String value) {
        public boolean has(int flag) { return (flags & flag) != 0; }
    }

    public record Player(UUID id, String name, int box, boolean owner, boolean controls, long main, long[] side,
                         long insurance, boolean insuranceDecided, boolean ready, boolean playing, boolean connected,
                         int followOutcome, long followPayout, long roundPayout, long[] sidePayout, long insurancePayout,
                         List<Hand> hands, int decidingHand, int decisionMask, long[] mainChips, long[][] sideChips) {
        /** Chips of a spot bottom-up as they were put down (-1 = main bet, else SideBet ordinal). */
        public long[] pile(int spot) {
            if (spot < 0) return mainChips;
            return spot < sideChips.length ? sideChips[spot] : new long[0];
        }
    }

    public Player player(UUID id) {
        for (Player p : players) if (p.id.equals(id)) return p;
        return null;
    }

    public Player owner(int box) {
        for (Player p : players) if (p.owner && p.box == box) return p;
        return null;
    }

    public List<Player> atBox(int box) {
        List<Player> list = new ArrayList<>();
        for (Player p : players) if (p.box == box) list.add(p);
        return list;
    }

    /** Writes everything except the per-recipient balance (see {@link #writeBalance}). */
    public void writeShared(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeString(style);
        buf.writeString(styleName);
        buf.writeVarInt(variant);
        buf.writeVarInt(turnMode);
        buf.writeVarInt(boxes);
        buf.writeVarInt(phase);
        buf.writeVarInt(remaining);
        buf.writeVarInt(phaseLength);
        buf.writeVarLong(minBet);
        buf.writeVarLong(maxBet);
        buf.writeVarLong(sideMax);
        buf.writeVarInt(sideBetMask);
        buf.writeVarInt(chips.size());
        for (long c : chips) buf.writeVarLong(c);
        buf.writeVarInt(dealerEntity);
        buf.writeString(dealerName);
        buf.writeBoolean(dealerPresent);
        writeCards(buf, dealerCards);
        buf.writeString(dealerValue);
        buf.writeVarInt(roundId);
        buf.writeVarInt(shoeRemaining);
        buf.writeVarInt(shoeSize);
        buf.writeVarInt(activeBox + 1);
        buf.writeBoolean(activePlayer != null);
        if (activePlayer != null) buf.writeUuid(activePlayer);
        buf.writeVarInt(activeHand + 1);
        buf.writeBoolean(commonOpen);
        buf.writeBoolean(closed);
        buf.writeString(status);
        buf.writeString(currency);
        buf.writeVarInt(players.size());
        for (Player p : players) {
            buf.writeUuid(p.id);
            buf.writeString(p.name);
            buf.writeVarInt(p.box);
            buf.writeBoolean(p.owner);
            buf.writeBoolean(p.controls);
            buf.writeVarLong(p.main);
            writeLongs(buf, p.side);
            buf.writeVarLong(p.insurance);
            buf.writeBoolean(p.insuranceDecided);
            buf.writeBoolean(p.ready);
            buf.writeBoolean(p.playing);
            buf.writeBoolean(p.connected);
            buf.writeVarInt(p.followOutcome);
            buf.writeVarLong(p.followPayout);
            buf.writeVarLong(p.roundPayout);
            writeLongs(buf, p.sidePayout);
            buf.writeVarLong(p.insurancePayout);
            buf.writeVarInt(p.hands.size());
            for (Hand h : p.hands) {
                writeCards(buf, h.cards);
                buf.writeVarLong(h.bet);
                buf.writeVarLong(h.freeBet);
                buf.writeVarInt(h.flags);
                buf.writeVarInt(h.outcome);
                buf.writeVarLong(h.payout);
                buf.writeVarInt(h.remaining);
                buf.writeString(h.value);
            }
            buf.writeVarInt(p.decidingHand + 1);
            buf.writeVarInt(p.decisionMask);
            writeLongs(buf, p.mainChips);
            buf.writeVarInt(p.sideChips.length);
            for (long[] pile : p.sideChips) writeLongs(buf, pile);
        }
    }

    public static void writeBalance(PacketByteBuf buf, long balance) { buf.writeVarLong(balance); }

    public static TableSnapshot read(PacketByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String style = buf.readString();
        String styleName = buf.readString();
        int variant = buf.readVarInt();
        int turnMode = buf.readVarInt();
        int boxes = buf.readVarInt();
        int phase = buf.readVarInt();
        int remaining = buf.readVarInt();
        int phaseLength = buf.readVarInt();
        long minBet = buf.readVarLong();
        long maxBet = buf.readVarLong();
        long sideMax = buf.readVarLong();
        int sideMask = buf.readVarInt();
        int chipCount = buf.readVarInt();
        List<Long> chips = new ArrayList<>();
        for (int i = 0; i < chipCount; i++) chips.add(buf.readVarLong());
        int dealerEntity = buf.readVarInt();
        String dealerName = buf.readString();
        boolean dealerPresent = buf.readBoolean();
        int[] dealerCards = readCards(buf);
        String dealerValue = buf.readString();
        int roundId = buf.readVarInt();
        int shoeRemaining = buf.readVarInt();
        int shoeSize = buf.readVarInt();
        int activeBox = buf.readVarInt() - 1;
        UUID activePlayer = buf.readBoolean() ? buf.readUuid() : null;
        int activeHand = buf.readVarInt() - 1;
        boolean commonOpen = buf.readBoolean();
        boolean closed = buf.readBoolean();
        String status = buf.readString();
        String currency = buf.readString();
        int count = buf.readVarInt();
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUuid();
            String name = buf.readString();
            int box = buf.readVarInt();
            boolean owner = buf.readBoolean();
            boolean controls = buf.readBoolean();
            long main = buf.readVarLong();
            long[] side = readLongs(buf);
            long insurance = buf.readVarLong();
            boolean insuranceDecided = buf.readBoolean();
            boolean ready = buf.readBoolean();
            boolean playing = buf.readBoolean();
            boolean connected = buf.readBoolean();
            int followOutcome = buf.readVarInt();
            long followPayout = buf.readVarLong();
            long roundPayout = buf.readVarLong();
            long[] sidePayout = readLongs(buf);
            long insurancePayout = buf.readVarLong();
            int handCount = buf.readVarInt();
            List<Hand> hands = new ArrayList<>();
            for (int h = 0; h < handCount; h++) {
                hands.add(new Hand(readCards(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarInt(), buf.readString()));
            }
            int deciding = buf.readVarInt() - 1;
            int mask = buf.readVarInt();
            long[] mainChips = readLongs(buf);
            long[][] sideChips = new long[Math.min(16, buf.readVarInt())][];
            for (int s = 0; s < sideChips.length; s++) sideChips[s] = readLongs(buf);
            players.add(new Player(id, name, box, owner, controls, main, side, insurance, insuranceDecided, ready, playing,
                    connected, followOutcome, followPayout, roundPayout, sidePayout, insurancePayout, hands, deciding, mask, mainChips, sideChips));
        }
        long balance = buf.readVarLong();
        return new TableSnapshot(pos, style, styleName, variant, turnMode, boxes, phase, remaining, phaseLength, minBet, maxBet,
                sideMax, sideMask, chips, dealerEntity, dealerName, dealerPresent, dealerCards, dealerValue, roundId, shoeRemaining,
                shoeSize, activeBox, activePlayer, activeHand, commonOpen, closed, status, currency, players, balance);
    }

    private static void writeCards(PacketByteBuf buf, int[] cards) {
        buf.writeVarInt(cards.length);
        for (int c : cards) buf.writeVarInt(c + 1);
    }

    private static int[] readCards(PacketByteBuf buf) {
        int n = buf.readVarInt();
        int[] cards = new int[n];
        for (int i = 0; i < n; i++) cards[i] = buf.readVarInt() - 1;
        return cards;
    }

    private static void writeLongs(PacketByteBuf buf, long[] values) {
        buf.writeVarInt(values.length);
        for (long v : values) buf.writeVarLong(v);
    }

    private static long[] readLongs(PacketByteBuf buf) {
        int n = buf.readVarInt();
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = buf.readVarLong();
        return values;
    }
}
