package ru.mosvegas.casino.table;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.config.CasinoConfig;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.config.TableStyle;
import ru.mosvegas.casino.dealer.DealerDirector;
import ru.mosvegas.casino.economy.Economy;
import ru.mosvegas.casino.game.blackjack.BjHand;
import ru.mosvegas.casino.game.blackjack.BlackjackEngine;
import ru.mosvegas.casino.game.blackjack.BlackjackHost;
import ru.mosvegas.casino.game.blackjack.Decision;
import ru.mosvegas.casino.game.blackjack.Participant;
import ru.mosvegas.casino.game.blackjack.SideBet;
import ru.mosvegas.casino.game.cards.Cards;
import ru.mosvegas.casino.net.CasinoNet;
import ru.mosvegas.casino.net.TableAction;
import ru.mosvegas.casino.net.TableSnapshot;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.registry.CasinoSounds;
import ru.mosvegas.casino.seat.SeatEntity;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server runtime of one blackjack table: owns the rules engine, the dealer director, the stools and the
 * client sync. Implements {@link BlackjackHost} so the pure engine can reach money, speech and animations.
 */
public final class BlackjackTable implements BlackjackHost {
    private final BlackjackTableBlockEntity be;
    private final ServerWorld world;
    private final BlockPos pos;
    private final Direction facing;
    private final TableSize size;
    private TableStyle style;
    private TableGeometry geometry;
    private int configRevision = -1;

    private final BlackjackEngine engine;
    private final DealerDirector director;
    private final SeatEntity[] seats = new SeatEntity[9];

    private boolean dirty = true;
    private int version;
    private int ticks;
    private final Map<UUID, Integer> sent = new HashMap<>();
    private byte[] sharedCache;
    private String status = "";
    private String pendingAnimation;
    private int pendingPriority = -1;
    private long pushedWagered, pushedPaid, pushedRounds;
    private boolean disposed;

    public BlackjackTable(BlackjackTableBlockEntity be, ServerWorld world) {
        this.be = be;
        this.world = world;
        this.pos = be.getPos();
        this.facing = be.getCachedState().contains(BlackjackTableBlock.FACING) ? be.getCachedState().get(BlackjackTableBlock.FACING) : Direction.NORTH;
        this.size = be.getCachedState().contains(BlackjackTableBlock.SIZE) ? be.getCachedState().get(BlackjackTableBlock.SIZE) : TableSize.LARGE;
        this.style = ConfigManager.get().style(be.style());
        this.geometry = new TableGeometry(size, style.boxCount());
        this.engine = new BlackjackEngine(this, style.rules(), style.boxCount(), new SecureRandom());
        this.director = new DealerDirector(this);
        applyConfig();
        TableRegistry.register(this);
    }

    private void applyConfig() {
        configRevision = ConfigManager.revision();
        style = ConfigManager.get().style(be.style());
        int boxes = Math.min(style.boxCount(), size == TableSize.MEDIUM ? 5 : 7);
        geometry = new TableGeometry(size, boxes);
        long min = be.minBetOverride() > 0 ? be.minBetOverride() : style.minBet;
        long max = be.maxBetOverride() > 0 ? be.maxBetOverride() : style.maxBet;
        engine.configure(style.rules(), boxes, min, max, style.sideMax, style.minPlayers);
        engine.setClosed(!be.isOpen());
        dirty = true;
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (disposed) return;
        ticks++;
        if (configRevision != ConfigManager.revision() || !style.id.equals(be.style())) applyConfig();
        engine.setClosed(!be.isOpen());
        validateSeats();
        if (ticks % 10 == 0) validateBehind();
        director.tick();
        engine.tick();
        flushAnimation();
        if (ticks % 200 == 0) pushStats();
        sync();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        engine.shutdown();
        director.dispose();
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null) seats[i].discard();
            seats[i] = null;
        }
        for (UUID id : sent.keySet()) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
            if (p != null) CasinoNet.sendRemove(p, pos);
        }
        sent.clear();
        pushStats();
        TableRegistry.unregister(this);
    }

    private void pushStats() {
        long w = engine.statWagered(), p = engine.statPaid(), r = engine.statRounds();
        if (w != pushedWagered || p != pushedPaid || r != pushedRounds) {
            be.addStats(w - pushedWagered, p - pushedPaid, r - pushedRounds);
            pushedWagered = w;
            pushedPaid = p;
            pushedRounds = r;
        }
    }

    // ------------------------------------------------------------------ seats & players

    public void interact(ServerPlayerEntity player, Vec3d hit) {
        Participant existing = engine.participant(player.getUuid());
        if (existing != null) {
            if (player.isSneaking()) leave(player.getUuid(), true);
            else CasinoNet.sendOpen(player, pos);
            return;
        }
        if (!be.isOpen()) {
            tellText(player, ConfigManager.phrase("table_closed_info"));
            return;
        }
        BlackjackTable other = TableRegistry.tableOf(player.getUuid());
        if (other != null && other != this) {
            tellText(player, ConfigManager.phrase("already_at_table"));
            return;
        }
        TableGeometry.Local local = TableGeometry.toLocal(pos, facing, hit);
        int box = geometry.boxAt(local.x(), local.z());
        if (engine.owner(box) == null) sit(player, box);
        else joinBehind(player, box);
    }

    public void interactWithDealer(ServerPlayerEntity player) {
        if (engine.participant(player.getUuid()) != null) CasinoNet.sendOpen(player, pos);
        else tellText(player, "§7" + (director.onDuty() ? ConfigManager.phrase("welcome", "player", player.getName().getString()) : ConfigManager.phrase("no_dealer")));
    }

    public boolean sit(ServerPlayerEntity player, int box) {
        if (box < 0 || box >= geometry.boxes) return false;
        if (player.hasVehicle()) player.stopRiding();
        if (!engine.sit(box, player.getUuid(), player.getName().getString())) {
            tellText(player, ConfigManager.phrase("seat_taken"));
            return false;
        }
        double angle = geometry.boxAngle(box);
        Vec3d seatPos = world(geometry.seat(box), 0);
        Vec3d dismount = world(geometry.polar(angle, geometry.radius + 1.3), 0);
        SeatEntity seat = CasinoRegistry.SEAT.create(world);
        if (seat == null) {
            engine.leave(player.getUuid());
            return false;
        }
        TableGeometry.Local spot = geometry.betSpot(box);
        TableGeometry.Local s = geometry.seat(box);
        float yaw = TableGeometry.yawOf(facing, spot.x() - s.x(), spot.z() - s.z());
        seat.refreshPositionAndAngles(seatPos.x, seatPos.y, seatPos.z, yaw, 0);
        seat.setup(pos, box, dismount);
        seat.claim(world.getTime());
        world.spawnEntity(seat);
        player.startRiding(seat, true);
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, world(geometry.cardAnchor(box), TableGeometry.FELT_Y));
        seats[box] = seat;
        tellText(player, ConfigManager.phrase("seated", "box", Integer.toString(box + 1)));
        tellText(player, ConfigManager.phrase("stand_up_hint"));
        if (director.onDuty()) {
            director.speak("welcome", "player", player.getName().getString());
            director.play("bow");
        }
        CasinoNet.sendOpen(player, pos);
        dirty = true;
        return true;
    }

    public boolean joinBehind(ServerPlayerEntity player, int box) {
        Participant owner = engine.owner(box);
        if (!engine.joinBehind(box, player.getUuid(), player.getName().getString())) {
            tellText(player, ConfigManager.phrase(owner == null ? "seat_taken" : "box_full"));
            return false;
        }
        tellText(player, ConfigManager.phrase("behind_joined", "owner", owner == null ? "-" : owner.name, "box", Integer.toString(box + 1)));
        if (director.onDuty()) director.speak("welcome_behind", "player", player.getName().getString(), "box", Integer.toString(box + 1));
        CasinoNet.sendOpen(player, pos);
        dirty = true;
        return true;
    }

    public void leave(UUID id, boolean notify) {
        Participant p = engine.participant(id);
        if (p == null) return;
        engine.leave(id);
        if (p.owner && seats[p.box] != null) {
            SeatEntity seat = seats[p.box];
            seats[p.box] = null;
            seat.removeAllPassengers();
            seat.discard();
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
        if (notify && player != null) tellText(player, ConfigManager.phrase("left_table"));
        if (director.onDuty() && player != null) director.speak("goodbye_player", "player", p.name);
        dirty = true;
    }

    private void validateSeats() {
        long time = world.getTime();
        for (int box = 0; box < seats.length; box++) {
            SeatEntity seat = seats[box];
            Participant owner = box < geometry.boxes ? engine.owner(box) : null;
            if (seat != null) {
                seat.claim(time);
                boolean ok = !seat.isRemoved() && owner != null && seat.hasPassenger(e -> e.getUuid().equals(owner.id));
                if (!ok) {
                    seats[box] = null;
                    seat.discard();
                    if (owner != null && owner.connected) leave(owner.id, true);
                }
            } else if (owner != null && owner.connected) {
                leave(owner.id, true);
            }
        }
    }

    private void validateBehind() {
        double max = ConfigManager.get().behindMaxDistance;
        for (Participant p : new ArrayList<>(engine.participants())) {
            if (p.owner || !p.connected) continue;
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(p.id);
            Vec3d spot = world(geometry.betSpot(p.box), 0);
            if (player == null || player.getWorld() != world || player.getPos().squaredDistanceTo(spot) > (max + geometry.radius) * (max + geometry.radius)) {
                leave(p.id, player != null);
            }
        }
    }

    // ------------------------------------------------------------------ client actions

    public void handleAction(ServerPlayerEntity player, TableAction action, int arg, long amount) {
        if (player.getWorld() != world || player.getPos().squaredDistanceTo(center()) > 16 * 16) return;
        UUID id = player.getUuid();
        String error = switch (action) {
            case BET -> {
                SideBet side = arg >= 0 && arg < SideBet.values().length ? SideBet.values()[arg] : null;
                yield amount > 0 ? engine.placeBet(id, side, amount) : "err_invalid";
            }
            case UNDO -> engine.undo(id);
            case CLEAR -> engine.clearBets(id);
            case REBET -> engine.rebet(id);
            case DOUBLE_BETS -> engine.doubleBets(id);
            case READY -> engine.ready(id);
            case DECIDE -> {
                Decision d = Decision.byId(arg);
                yield d == null ? "err_invalid" : engine.decide(id, d);
            }
            case INSURANCE -> engine.insurance(id, arg == 1);
            case LEAVE -> {
                leave(id, true);
                yield null;
            }
            case SIT -> {
                if (engine.participant(id) == null && be.isOpen()) sit(player, arg);
                yield null;
            }
            case BEHIND -> {
                if (engine.participant(id) == null && be.isOpen()) joinBehind(player, arg);
                yield null;
            }
            case SYNC -> {
                sent.remove(id);
                yield null;
            }
        };
        if (error != null) tellText(player, ConfigManager.phrase(error));
        else if (action == TableAction.BET || action == TableAction.REBET) director.acknowledge();
        sent.remove(id);
        dirty = true;
    }

    // ------------------------------------------------------------------ BlackjackHost

    private ServerPlayerEntity online(UUID id) { return world.getServer().getPlayerManager().getPlayer(id); }

    @Override
    public long balance(UUID player) {
        ServerPlayerEntity p = online(player);
        return p == null ? 0 : Economy.balance(p);
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        ServerPlayerEntity p = online(player);
        return p != null && Economy.withdraw(p, amount);
    }

    @Override public void deposit(UUID player, long amount) { Economy.deposit(world.getServer(), player, amount); }
    @Override public String money(long amount) { return Economy.format(amount); }

    @Override
    public void say(String phrase, String... args) {
        String[] all = new String[args.length + 2];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = "dealer";
        all[args.length + 1] = director.dealerName();
        status = ConfigManager.phrase(phrase, all);
        director.speak(phrase, args);
        dirty = true;
    }

    @Override
    public void tell(UUID player, String phrase, String... args) {
        ServerPlayerEntity p = online(player);
        if (p != null) tellText(p, ConfigManager.phrase(phrase, args));
    }

    @Override
    public String phrase(String phrase, String... args) { return ConfigManager.phrase(phrase, args); }

    private void tellText(ServerPlayerEntity player, String text) {
        player.sendMessage(Text.literal(ConfigManager.get().chatPrefix + text), false);
    }

    @Override
    public void emote(String animation) {
        if (animation.startsWith("point@")) {
            int box = Integer.parseInt(animation.substring(6));
            if (box < 0 || box >= geometry.boxes) return;
            animation = "point_" + geometry.boxBucket(box);
        }
        int priority = switch (animation) {
            case "flip", "peek", "shuffle" -> 1;
            default -> 0;
        };
        queueAnimation(animation, priority);
    }

    /**
     * The hands of the dealer are animated by every client together with the card it draws (see the client
     * choreography), the server only turns the dealer's head to the box.
     */
    @Override
    public void dealCard(int box, boolean faceUp) {
        if (box >= 0 || box == DEALER || box == CENTER) look(box);
    }

    private void queueAnimation(String animation, int priority) {
        if (priority >= pendingPriority) {
            pendingAnimation = animation;
            pendingPriority = priority;
        }
    }

    private void flushAnimation() {
        if (pendingAnimation != null) director.play(pendingAnimation);
        pendingAnimation = null;
        pendingPriority = -1;
    }

    @Override
    public void look(int box) {
        Vec3d target;
        if (box >= 0 && box < geometry.boxes) target = world(geometry.seat(box), 1.25);
        else if (box == CENTER) target = world(geometry.commonCards(), TableGeometry.FELT_Y);
        else target = world(new TableGeometry.Local(0, TableGeometry.CENTER_Z + geometry.radius * 0.6), 1.1);
        director.look(target);
    }

    @Override
    public void sound(String sound) {
        SoundEvent event = CasinoSounds.get(sound);
        if (event == null) return;
        Vec3d c = world(new TableGeometry.Local(0, 0), 1.0);
        world.playSound(null, c.x, c.y, c.z, event, SoundCategory.BLOCKS, 0.8f, 0.95f + world.random.nextFloat() * 0.1f);
    }

    @Override public void changed() { dirty = true; }

    @Override public void roundFinished() { pushStats(); }

    // ------------------------------------------------------------------ chat helpers for the director

    public void broadcastSpeech(String speaker, String text) {
        int radius = ConfigManager.get().dealers.chatRadius;
        if (radius <= 0) return;
        Vec3d c = center();
        Text message = Text.literal("§6[" + speaker + "]§f " + text);
        for (ServerPlayerEntity p : world.getPlayers()) if (p.getPos().squaredDistanceTo(c) <= radius * radius) p.sendMessage(message, false);
    }

    public void notifySeated(String text) {
        for (Participant p : engine.participants()) {
            ServerPlayerEntity player = online(p.id);
            if (player != null) tellText(player, "§7" + text);
        }
    }

    // ------------------------------------------------------------------ sync

    private void sync() {
        boolean periodic = ticks % 10 == 0;
        if (!dirty && !periodic) return;
        if (dirty) {
            version++;
            dirty = false;
            sharedCache = null;
        }
        CasinoConfig config = ConfigManager.get();
        double r2 = (double) config.syncRadius * config.syncRadius;
        Vec3d c = center();
        Set<UUID> inRange = new HashSet<>();
        boolean refreshBalances = ticks % 40 == 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().squaredDistanceTo(c) > r2) continue;
            inRange.add(player.getUuid());
            Integer last = sent.get(player.getUuid());
            boolean participant = engine.participant(player.getUuid()) != null;
            if (last != null && last == version && !(refreshBalances && participant)) continue;
            if (!ServerPlayNetworking.canSend(player, CasinoNet.TABLE_STATE)) continue;
            if (sharedCache == null) {
                PacketByteBuf shared = new PacketByteBuf(Unpooled.buffer());
                snapshot().writeShared(shared);
                sharedCache = new byte[shared.readableBytes()];
                shared.readBytes(sharedCache);
            }
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(sharedCache);
            TableSnapshot.writeBalance(buf, Economy.balance(player));
            ServerPlayNetworking.send(player, CasinoNet.TABLE_STATE, buf);
            sent.put(player.getUuid(), version);
        }
        for (UUID id : new ArrayList<>(sent.keySet())) {
            if (inRange.contains(id)) continue;
            sent.remove(id);
            ServerPlayerEntity p = online(id);
            if (p != null) CasinoNet.sendRemove(p, pos);
        }
    }

    private static long[] pile(List<Long> chips) {
        if (chips == null) return new long[0];
        long[] out = new long[chips.size()];
        for (int i = 0; i < out.length; i++) out[i] = chips.get(i);
        return out;
    }

    private TableSnapshot snapshot() {
        List<TableSnapshot.Player> players = new ArrayList<>();
        for (Participant p : engine.participants()) {
            List<TableSnapshot.Hand> hands = new ArrayList<>();
            for (BjHand h : p.hands) {
                int[] cards = h.cards.stream().mapToInt(Integer::intValue).toArray();
                int flags = (h.doubled ? TableSnapshot.HAND_DOUBLED : 0) | (h.fromSplit ? TableSnapshot.HAND_SPLIT : 0)
                        | (h.surrendered ? TableSnapshot.HAND_SURRENDER : 0) | (h.done ? TableSnapshot.HAND_DONE : 0)
                        | (h.awaitingCard ? TableSnapshot.HAND_AWAITING : 0) | (h.pending != null ? TableSnapshot.HAND_PENDING : 0)
                        | (h.charlie ? TableSnapshot.HAND_CHARLIE : 0) | (h.isNatural() ? TableSnapshot.HAND_NATURAL : 0)
                        | (h.busted() ? TableSnapshot.HAND_BUST : 0);
                hands.add(new TableSnapshot.Hand(cards, h.bet, h.freeBet, flags, h.outcome.ordinal(), h.payout,
                        engine.ticksUntil(h.deadline), h.cards.isEmpty() ? "" : h.value().display()));
            }
            BjHand deciding = engine.decidingHand(p);
            int mask = 0;
            if (deciding != null) for (Decision d : engine.available(p, deciding)) mask |= 1 << d.ordinal();
            long[] side = new long[SideBet.values().length];
            long[] sidePay = new long[SideBet.values().length];
            for (SideBet s : SideBet.values()) {
                side[s.ordinal()] = p.sideBet(s);
                sidePay[s.ordinal()] = p.sidePaid(s);
            }
            long[][] sidePiles = new long[SideBet.values().length][];
            for (SideBet s : SideBet.values()) sidePiles[s.ordinal()] = pile(p.sideChips.get(s));
            players.add(new TableSnapshot.Player(p.id, p.name, p.box, p.owner, engine.controls(p), p.main, side, p.insurance,
                    p.insuranceDecided, p.ready, p.playing, p.connected, p.followOutcome.ordinal(), p.followPayout, p.roundPayout,
                    sidePay, p.insurancePayout, hands, deciding == null ? -1 : p.hands.indexOf(deciding), mask,
                    pile(p.mainChips), sidePiles));
        }
        List<Integer> dealerList = engine.dealerCards();
        int[] dealer = new int[dealerList.size()];
        for (int i = 0; i < dealer.length; i++) dealer[i] = (i == 1 && engine.holeHidden()) ? Cards.HIDDEN : dealerList.get(i);
        int sideMask = 0;
        for (SideBet s : engine.rules().enabledSideBets()) sideMask |= 1 << s.ordinal();
        Participant active = engine.activeParticipant();
        return new TableSnapshot(pos, style.id, style.name, style.variant().ordinal(), engine.rules().turnMode.ordinal(), geometry.boxes,
                engine.phase().ordinal(), engine.remainingTicks(), engine.phaseLengthTicks(), engine.minBet(), engine.maxBet(),
                engine.sideMax(), sideMask, ConfigManager.get().chips, director.entityId(), director.dealerName(), engine.dealerPresent(),
                dealer, dealer.length == 0 ? "" : engine.visibleDealerValue().display(), engine.roundId(), engine.shoeRemaining(),
                engine.shoeSize(), active == null ? -1 : active.box, active == null ? null : active.id, engine.activeHand(),
                engine.commonRoundOpen(), engine.closed(), status, currency(), players, 0);
    }

    private static String currency() {
        String zero = Economy.format(0).trim();
        return zero.startsWith("0") ? zero.substring(1).trim() : "";
    }

    // ------------------------------------------------------------------ accessors

    public Vec3d world(TableGeometry.Local local, double y) { return TableGeometry.toWorld(pos, facing, local, y); }
    public Vec3d center() { return world(new TableGeometry.Local(0, TableGeometry.CENTER_Z + geometry.radius * 0.4), 0.9); }

    public Vec3d entrance() {
        BlockPos e = be.entrance();
        if (e != null) return Vec3d.ofBottomCenter(e);
        return world(geometry.defaultEntrance(), 0);
    }

    public BlackjackEngine engine() { return engine; }
    public DealerDirector director() { return director; }
    public ServerWorld serverWorld() { return world; }
    public BlockPos pos() { return pos; }
    public Direction facing() { return facing; }
    public TableGeometry geometry() { return geometry; }
    public TableStyle style() { return style; }
    public boolean isOpen() { return be.isOpen(); }
    public BlackjackTableBlockEntity blockEntity() { return be; }
    public void markDirty() { dirty = true; }
    public String key() { return world.getRegistryKey().getValue().getPath() + " " + pos.toShortString(); }
}
