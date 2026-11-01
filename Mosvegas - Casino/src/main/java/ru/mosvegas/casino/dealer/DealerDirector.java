package ru.mosvegas.casino.dealer;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.casino.config.CasinoConfig;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.config.DealerProfile;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.net.CasinoNet;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.BlackjackTable;
import ru.mosvegas.casino.table.TableGeometry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Dealer lifecycle of one table: walk in from the staff entrance, sit down, greet, shuffle, deal for a
 * shift, then stand up, say goodbye and walk out while a colleague takes the chair.
 */
public final class DealerDirector {
    private enum Phase { NONE, ARRIVING, SITTING_DOWN, GREETING, ON_DUTY }

    private static final class Leaving {
        final DealerEntity entity;
        final DealerProfile profile;
        final long breakMillis;
        final Vec3d from;
        int ticks;
        boolean walking;

        Leaving(DealerEntity entity, DealerProfile profile, long breakMillis) {
            this.entity = entity;
            this.profile = profile;
            this.breakMillis = breakMillis;
            this.from = entity.getPos();
        }
    }

    private final BlackjackTable table;
    private DealerEntity current;
    private DealerProfile profile;
    private Phase phase = Phase.NONE;
    private int phaseTicks;
    private long dutyTicks;
    private int emptyTicks;
    private int summonCooldown;
    private String lastProfile;
    private Vec3d sitFrom;
    private Vec3d lookTarget;
    private final List<Leaving> leaving = new ArrayList<>();
    private int noDealerNoticeCooldown;
    private boolean handoverRequested;
    private long lastGesture;
    private int nextFidget = 200;
    private final Random random = new Random();

    public DealerDirector(BlackjackTable table) { this.table = table; }

    private CasinoConfig.Dealers cfg() { return ConfigManager.get().dealers; }

    private Vec3d seat() { return table.world(table.geometry().dealerSeat(), 0); }
    private Vec3d stand() { return table.world(table.geometry().dealerStand(), 0); }
    private float frontYaw() { return table.facing().asRotation(); }

    public void tick() {
        ServerWorld world = table.serverWorld();
        long time = world.getTime();
        Iterator<Leaving> it = leaving.iterator();
        while (it.hasNext()) if (tickLeaving(it.next(), time)) it.remove();
        if (current != null && current.isRemoved()) lost();
        if (current != null) current.claim(table.pos(), time);
        if (noDealerNoticeCooldown > 0) noDealerNoticeCooldown--;
        phaseTicks++;
        switch (phase) {
            case NONE -> {
                if (summonCooldown > 0) summonCooldown--;
                else if (needDealer()) summon(null);
            }
            case ARRIVING -> tickArriving();
            case SITTING_DOWN -> tickSittingDown();
            case GREETING -> tickGreeting();
            case ON_DUTY -> tickOnDuty();
        }
    }

    private boolean needDealer() {
        return table.isOpen() && (table.engine().seatedCount() > 0 || cfg().alwaysStaffed);
    }

    private void summon(DealerProfile chosen) {
        DealerProfile p = chosen != null ? chosen : DealerRoster.acquire(table.key(), table.style(), lastProfile);
        if (p == null) {
            summonCooldown = 100;
            if (noDealerNoticeCooldown == 0 && table.engine().seatedCount() > 0) {
                table.notifySeated(ConfigManager.phrase("no_dealer"));
                noDealerNoticeCooldown = 20 * 60;
            }
            return;
        }
        ServerWorld world = table.serverWorld();
        DealerEntity e = CasinoRegistry.DEALER.create(world);
        if (e == null) {
            DealerRoster.release(p.id, 0);
            summonCooldown = 100;
            return;
        }
        Vec3d entrance = table.entrance();
        Vec3d stand = stand();
        float yaw = TableGeometry.worldYaw(stand.x - entrance.x, stand.z - entrance.z);
        e.refreshPositionAndAngles(entrance.x, entrance.y, entrance.z, yaw, 0);
        e.setHeadYaw(yaw);
        e.setBodyYaw(yaw);
        e.setProfileId(p.id);
        if (cfg().showNameTags) {
            e.setCustomName(Text.literal("§6" + cfg().title + " §f" + p.name));
            e.setCustomNameVisible(true);
        }
        e.claim(table.pos(), world.getTime());
        world.spawnEntity(e);
        current = e;
        profile = p;
        setPhase(Phase.ARRIVING);
        walkTo(e, stand);
    }

    private void walkTo(DealerEntity e, Vec3d target) {
        e.getNavigation().startMovingTo(target.x, target.y, target.z, Math.max(0.2, cfg().walkSpeed));
    }

    private void tickArriving() {
        Vec3d stand = stand();
        if (phaseTicks % 20 == 0 && current.getNavigation().isIdle()) walkTo(current, stand);
        double dist = current.getPos().squaredDistanceTo(stand);
        if (dist < 0.9 || (dist < 4 && current.getNavigation().isIdle() && phaseTicks > 40)) startSitting();
        else if (phaseTicks > cfg().walkTimeoutSeconds * 20) {
            current.refreshPositionAndAngles(stand.x, stand.y, stand.z, frontYaw(), 0);
            startSitting();
        }
    }

    private void startSitting() {
        current.getNavigation().stop();
        sitFrom = current.getPos();
        setPhase(Phase.SITTING_DOWN);
    }

    private void tickSittingDown() {
        Vec3d seat = seat();
        double t = Math.min(1.0, phaseTicks / 12.0);
        Vec3d pos = sitFrom.lerp(seat, t);
        hold(pos);
        if (phaseTicks == 3) current.setSitting(true);
        if (phaseTicks >= 18) setPhase(Phase.GREETING);
    }

    private void tickGreeting() {
        hold(seat());
        if (phaseTicks == 1) {
            play("wave");
            speak("greeting", "dealer", profile.name);
        }
        if (phaseTicks >= 34) {
            ru.mosvegas.casino.MosVegasCasino.LOGGER.info("Крупье {} работает за столом {}", profile.name, table.key());
            setPhase(Phase.ON_DUTY);
            dutyTicks = 0;
            emptyTicks = 0;
            table.engine().setDealerPresent(true);
            if (table.engine().seatedCount() > 0) table.engine().freshShoe();
        }
    }

    private void tickOnDuty() {
        hold(seat());
        if (lookTarget != null) current.getLookControl().lookAt(lookTarget.x, lookTarget.y, lookTarget.z, 30f, 30f);
        dutyTicks++;
        if (table.engine().seatedCount() == 0) emptyTicks++;
        else emptyTicks = 0;
        fidget();
        if (!table.engine().betweenRounds()) return;
        long breakMillis = (long) (cfg().breakMinutes * 60_000);
        if (!table.isOpen()) {
            leave("farewell", 0);
            return;
        }
        if (!cfg().alwaysStaffed && emptyTicks > cfg().idleLeaveSeconds * 20L) {
            leave("idle_leave", 0);
            return;
        }
        if (handoverRequested) {
            handoverRequested = false;
            handover(breakMillis);
            return;
        }
        if (dutyTicks >= (long) (cfg().shiftMinutes * 1200)) handover(breakMillis);
    }

    /** While nothing happens the dealer is not a statue: squares the chips, nods, thinks. */
    private void fidget() {
        int every = cfg().fidgetSeconds;
        BlackjackPhase phase = table.engine().phase();
        if (every <= 0 || (phase != BlackjackPhase.IDLE && phase != BlackjackPhase.BETTING)) return;
        long time = table.serverWorld().getTime();
        if (time - lastGesture < nextFidget) return;
        float r = random.nextFloat();
        play(phase == BlackjackPhase.IDLE && r < 0.25f ? "think" : r < 0.8f ? "tidy" : "nod");
        nextFidget = every * 20 / 2 + random.nextInt(every * 20 + 1);
    }

    /** A short nod when somebody puts chips down (not more often than every few seconds). */
    public void acknowledge() {
        if (current == null || phase != Phase.ON_DUTY) return;
        if (table.serverWorld().getTime() - lastGesture > 80) play("nod");
    }

    private void handover(long breakMillis) {
        DealerProfile next = DealerRoster.acquire(table.key(), table.style(), profile.id);
        if (next == null) {
            if (cfg().leaveWithoutReplacement) leave("break", breakMillis);
            else dutyTicks -= 60 * 20;
            return;
        }
        leave("handover", breakMillis, "next", next.name, "next_id", next.id);
        summon(next);
    }

    /** Current dealer says the phrase (key, args), stands up and walks back to the staff entrance. */
    public void leave(String phraseKey, long breakMillis, String... args) {
        if (current == null) return;
        table.engine().setDealerPresent(false);
        current.setSitting(false);
        if (phraseKey != null && !phraseKey.isEmpty()) speak(phraseKey, args);
        current.play("stand_up");
        leaving.add(new Leaving(current, profile, breakMillis));
        lastProfile = profile.id;
        current = null;
        profile = null;
        lookTarget = null;
        setPhase(Phase.NONE);
        summonCooldown = 60;
    }

    private boolean tickLeaving(Leaving l, long time) {
        DealerEntity e = l.entity;
        if (e.isRemoved()) {
            DealerRoster.release(l.profile.id, l.breakMillis);
            return true;
        }
        e.claim(table.pos(), time);
        l.ticks++;
        if (l.ticks <= 14) {
            Vec3d pos = l.from.lerp(stand(), l.ticks / 14.0);
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, frontYaw(), 0);
            e.setVelocity(Vec3d.ZERO);
        }
        if (l.ticks == 16) e.play("wave");
        if (l.ticks == 44) {
            l.walking = true;
            walkTo(e, table.entrance());
        }
        if (l.walking) {
            Vec3d entrance = table.entrance();
            if (l.ticks % 20 == 0 && e.getNavigation().isIdle()) walkTo(e, entrance);
            boolean arrived = e.getPos().squaredDistanceTo(entrance) < 1.5;
            if (arrived || l.ticks > 44 + cfg().walkTimeoutSeconds * 20) {
                e.discard();
                DealerRoster.release(l.profile.id, l.breakMillis);
                return true;
            }
        }
        return false;
    }

    private void hold(Vec3d pos) {
        current.getNavigation().stop();
        current.setVelocity(Vec3d.ZERO);
        current.refreshPositionAndAngles(pos.x, pos.y, pos.z, frontYaw(), current.getPitch());
        current.setBodyYaw(frontYaw());
    }

    private void lost() {
        if (profile != null) DealerRoster.release(profile.id, 0);
        current = null;
        profile = null;
        table.engine().setDealerPresent(false);
        setPhase(Phase.NONE);
        summonCooldown = 60;
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        table.markDirty();
    }

    /** Table removed or server stopping: everybody vanishes immediately, no cooldowns. */
    public void dispose() {
        if (current != null) {
            current.discard();
            DealerRoster.release(profile.id, 0);
        }
        for (Leaving l : leaving) {
            l.entity.discard();
            DealerRoster.release(l.profile.id, l.breakMillis);
        }
        leaving.clear();
        current = null;
        profile = null;
        phase = Phase.NONE;
    }

    /** Ends the current shift as soon as the running round is over (a free colleague takes the chair). */
    public boolean handoverNow() {
        if (current == null) return false;
        handoverRequested = true;
        return true;
    }

    /** Immediate dismissal ordered by an admin. */
    public void dismiss() {
        if (current != null) leave("break", 0);
    }

    public void call() {
        if (phase == Phase.NONE) {
            summonCooldown = 0;
            summon(null);
        }
    }

    // ------------------------------------------------------------------ hooks for the game

    public void play(String animation) {
        if (current != null && (phase == Phase.ON_DUTY || phase == Phase.GREETING)) {
            current.play(animation);
            lastGesture = table.serverWorld().getTime();
        }
    }

    /** Phrase by key (see the config) with name/value args: speech bubble, chat line and the voice line. */
    public void speak(String key, String... args) {
        if (current == null || profile == null) return;
        String[] all = new String[args.length + 2];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = "dealer";
        all[args.length + 1] = profile.name;
        say(ConfigManager.phrase(key, all));
        voice(key, args);
    }

    public void say(String text) {
        if (current == null || text == null || text.isEmpty()) return;
        if (cfg().speechBubbles) current.say(text);
        table.broadcastSpeech(cfg().title + " " + profile.name, text);
    }

    /**
     * Sends the voice line to the players around. The client plays the first key it has a sound for:
     * {@code dealer_total_19} before {@code dealer_total}, {@code greeting_anna} before {@code greeting}, ...
     */
    private void voice(String key, String... args) {
        int radius = cfg().voiceRadius;
        if (radius <= 0) return;
        List<String> keys = new ArrayList<>();
        String value = arg(args, "value"), next = arg(args, "next_id");
        if (value != null) keys.add(key + "_" + value.replace('/', '_'));
        if (next != null) keys.add(key + "_" + next);
        if ("greeting".equals(key)) keys.add(key + "_" + profile.id);
        keys.add(key);
        Vec3d at = current.getPos();
        for (ServerPlayerEntity player : table.serverWorld().getPlayers()) {
            if (player.squaredDistanceTo(at) <= (double) radius * radius) {
                CasinoNet.sendVoice(player, current.getId(), profile.voiceSet(), profile.pitch(), keys);
            }
        }
    }

    private static String arg(String[] args, String name) {
        for (int i = 0; i + 1 < args.length; i += 2) if (name.equals(args[i])) return args[i + 1];
        return null;
    }

    public void look(Vec3d target) { this.lookTarget = target; }

    public boolean onDuty() { return phase == Phase.ON_DUTY; }
    public String dealerName() { return profile != null ? profile.name : ""; }
    public int entityId() { return current != null ? current.getId() : -1; }
    public String statusText() { return phase.name().toLowerCase() + (profile != null ? " (" + profile.name + ", смена " + dutyTicks / 1200 + " мин)" : ""); }
}
