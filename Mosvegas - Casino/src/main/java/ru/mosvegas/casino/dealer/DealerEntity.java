package ru.mosvegas.casino.dealer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableRegistry;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The croupier. Not saved to disk: every dealer belongs to a table's {@link DealerDirector}, which
 * re-claims it each tick; an unclaimed dealer (unloaded table, crash) removes itself.
 * Animations live in {@code animations/entity/dealer.animation.json} and can be edited in Blockbench.
 */
public final class DealerEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<String> PROFILE = DataTracker.registerData(DealerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> SITTING = DataTracker.registerData(DealerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> SPEECH = DataTracker.registerData(DealerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> SPEECH_ID = DataTracker.registerData(DealerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final String BASE = "base";
    public static final String TALK = "talk";
    public static final String ACTION = "action";
    public static final String HANDS = "hands";
    /** Gestures triggered by the server; ids map to {@code animation.dealer.<id>}. */
    public static final List<String> ACTIONS;
    /**
     * Hand choreography triggered by the client itself, in sync with the cards and chips it draws: {@code deal_<k>},
     * {@code reach_<k>} for the 7 direction buckets of TableGeometry, {@code deal_self}, {@code collect}.
     * This controller is evaluated last, so the hands win over a gesture that happens at the same moment.
     */
    public static final List<String> HAND_MOVES;

    static {
        List<String> actions = new ArrayList<>(List.of("flip", "peek", "shuffle", "present", "wave", "bow", "nod", "clap",
                "shake_head", "shrug", "think", "tidy", "sit_down", "stand_up"));
        List<String> hands = new ArrayList<>(List.of("deal_self", "collect"));
        for (int k = 0; k < TableGeometry.BUCKETS; k++) {
            actions.add("point_" + k);
            hands.add("deal_" + k);
            hands.add("reach_" + k);
        }
        ACTIONS = List.copyOf(actions);
        HAND_MOVES = List.copyOf(hands);
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dealer.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dealer.walk");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.dealer.sit");
    private static final RawAnimation TALKING = RawAnimation.begin().thenLoop("animation.dealer.talk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos tablePos;
    private long claimedAt = Long.MIN_VALUE;
    /** Client only: world time (ticks) until which the dealer is speaking. */
    private float talkUntil;

    public DealerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setInvulnerable(true);
        setCanPickUpLoot(false);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createDealerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(PROFILE, "");
        dataTracker.startTracking(SITTING, false);
        dataTracker.startTracking(SPEECH, "");
        dataTracker.startTracking(SPEECH_ID, 0);
    }

    @Override protected void initGoals() {}

    @Override
    protected BodyControl createBodyControl() {
        return new BodyControl(this) {
            @Override
            public void tick() {
                if (isSitting()) {
                    bodyYaw = getYaw();
                    prevBodyYaw = bodyYaw;
                } else super.tick();
            }
        };
    }

    // ------------------------------------------------------------------ state

    public String profileId() { return dataTracker.get(PROFILE); }
    public void setProfileId(String id) { dataTracker.set(PROFILE, id == null ? "" : id); }
    public boolean isSitting() { return dataTracker.get(SITTING); }
    public void setSitting(boolean sitting) { dataTracker.set(SITTING, sitting); }
    public String speech() { return dataTracker.get(SPEECH); }
    public int speechId() { return dataTracker.get(SPEECH_ID); }

    public void say(String text) {
        dataTracker.set(SPEECH, text == null ? "" : text.length() > 200 ? text.substring(0, 200) : text);
        dataTracker.set(SPEECH_ID, speechId() + 1);
    }

    /** Client: keeps the mouth and the talking head moving until the given world time. */
    public void talkUntil(float worldTime) { talkUntil = Math.max(talkUntil, worldTime); }

    public boolean isTalking(float worldTime) { return worldTime < talkUntil; }

    public BlockPos tablePos() { return tablePos; }

    public void claim(BlockPos table, long worldTime) {
        this.tablePos = table;
        this.claimedAt = worldTime;
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && (tablePos == null || getWorld().getTime() - claimedAt > 40)) discard();
    }

    // ------------------------------------------------------------------ untouchable

    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void pushAwayFrom(Entity entity) {}
    @Override protected void pushAway(Entity entity) {}
    @Override public boolean canBeLeashedBy(PlayerEntity player) { return false; }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override protected boolean isAffectedByDaylight() { return false; }
    @Override public boolean shouldRenderName() { return true; }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp && tablePos != null) {
            var table = TableRegistry.get(getWorld(), tablePos);
            if (table != null) table.interactWithDealer(sp);
        }
        return ActionResult.success(getWorld().isClient);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, BASE, 8, state -> {
            if (isSitting()) return state.setAndContinue(SIT);
            if (state.isMoving()) return state.setAndContinue(WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, TALK, 4, state ->
                isTalking(getWorld().getTime() + state.getPartialTick()) ? state.setAndContinue(TALKING) : PlayState.STOP));
        AnimationController<DealerEntity> action = new AnimationController<>(this, ACTION, 3, state -> PlayState.STOP);
        for (String id : ACTIONS) action.triggerableAnim(id, RawAnimation.begin().thenPlay("animation.dealer." + id));
        controllers.add(action);
        AnimationController<DealerEntity> hands = new AnimationController<>(this, HANDS, 2, state -> PlayState.STOP);
        for (String id : HAND_MOVES) hands.triggerableAnim(id, RawAnimation.begin().thenPlay("animation.dealer." + id));
        controllers.add(hands);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** Server: a gesture for everybody around. */
    public void play(String action) {
        if (ACTIONS.contains(action)) triggerAnim(ACTION, action);
    }

    /** Client: a hand move of the local card/chip choreography. */
    public void playHands(String move) {
        if (HAND_MOVES.contains(move)) triggerAnim(HANDS, move);
    }
}
