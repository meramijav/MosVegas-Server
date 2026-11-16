package ru.mosvegas.items.rp;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.items.MosVegasItems;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RpServer {
    public static final Identifier PLAY = new Identifier(MosVegasItems.MOD_ID, "rp_play");
    public static final Identifier CLEAR = new Identifier(MosVegasItems.MOD_ID, "rp_clear");
    public static final int ROLE_ACTOR = 0, ROLE_TARGET = 1;
    private static final double REACH = 3.5;
    private static final int STUN_TICKS = 60;

    record Playback(RpKind kind, String animation, int role, long start, boolean hold, float yaw, int partner) {}

    record State(RpKind kind, UUID by, long until) {}

    private record Task(long at, Runnable run) {}

    private static final Map<String, Float> LENGTHS = new HashMap<>();
    private static final Map<Integer, Playback> PLAYING = new ConcurrentHashMap<>();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BUSY = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> MOUNTED = new ConcurrentHashMap<>();
    private static final double FOLLOW_NEAR = 2.2, BOARD_NEAR = 4.5, FOLLOW_TELEPORT = 24, FOLLOW_STEP = 0.2, FOLLOW_STEP_MAX = 0.4, FOLLOW_RUN = 6;
    private static final int NAV_REPATH = 10;
    private static final List<Task> TASKS = new ArrayList<>();
    private static MinecraftServer server;

    private RpServer() {}

    public static void register() {
        loadLengths();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            PLAYING.clear();
            STATES.clear();
            BUSY.clear();
            MOUNTED.clear();
            TASKS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(RpServer::tick);
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            Playback playback = PLAYING.get(entity.getId());
            if (playback != null) send(player, entity, playback);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            Playback playback = PLAYING.get(handler.player.getId());
            if (playback != null) send(handler.player, handler.player, playback);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> restrained(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> restrained(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> restrained(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> restrained(player)
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (restrained(player)) return ActionResult.FAIL;

            if (!world.isClient && hand == Hand.MAIN_HAND && player.isSneaking() && player.getMainHandStack().isEmpty()
                    && player instanceof ServerPlayerEntity releaser && STATES.containsKey(entity.getUuid())) {
                release(entity, releaser);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                CommandManager.literal("rpitems").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("play")
                                .then(CommandManager.argument("actor", EntityArgumentType.entity())
                                        .then(CommandManager.argument("item", StringArgumentType.word())
                                                .executes(c -> play(c.getSource(), EntityArgumentType.getEntity(c, "actor"),
                                                        StringArgumentType.getString(c, "item"), null))
                                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                                        .executes(c -> play(c.getSource(), EntityArgumentType.getEntity(c, "actor"),
                                                                StringArgumentType.getString(c, "item"), EntityArgumentType.getEntity(c, "target")))))))
                        .then(CommandManager.literal("release")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .executes(c -> {
                                            release(EntityArgumentType.getEntity(c, "target"), null);
                                            return 1;
                                        })))));
    }

    private static int play(net.minecraft.server.command.ServerCommandSource source, Entity actor, String item, Entity target) {
        RpKind kind = RpKind.byId(item);
        if (kind == null || kind.animation == null || !(actor instanceof LivingEntity living)) {
            source.sendError(Text.literal("Unknown RP item " + item));
            return 0;
        }
        if (kind.action == RpKind.Action.TARGET) {
            if (!(target instanceof LivingEntity other)) {
                source.sendError(Text.literal("RP item " + item + " needs a target"));
                return 0;
            }
            start(living, kind, other, false);
        } else {
            start(living, kind, null, false);
        }
        return 1;
    }

    public static boolean busy(ServerPlayerEntity player) {
        Long until = BUSY.get(player.getUuid());
        return until != null && server != null && server.getTicks() < until || restrained(player);
    }

    public static boolean restrained(PlayerEntity player) {
        if (player.getWorld().isClient) return false;
        State state = STATES.get(player.getUuid());
        return state != null;
    }

    public static void useSelf(ServerPlayerEntity player, Hand hand, RpKind kind) {
        if (kind.animation == null) return;
        start(player, kind, null, true);
    }

    public static void useOnTarget(ServerPlayerEntity player, Hand hand, RpKind kind, LivingEntity target) {
        if (target == player || target.squaredDistanceTo(player) > REACH * REACH) return;
        State state = STATES.get(target.getUuid());
        if (state != null && (kind.effect == RpKind.Effect.CUFF || kind.effect == RpKind.Effect.TIE)) {
            player.sendMessage(Text.translatable("message.mosvegas_items.rp.already_bound"), true);
            return;
        }
        if (target instanceof PlayerEntity other && busy((ServerPlayerEntity) other) && state == null) return;
        start(player, kind, target, true);
    }

    static void start(LivingEntity actor, RpKind kind, LivingEntity target, boolean effects) {
        if (!(actor.getWorld() instanceof ServerWorld world) || server == null) return;
        float length = length(kind);
        int ticks = Math.max(1, MathHelper.ceil(length * 20));
        long now = server.getTicks();
        float yaw = actor.getHeadYaw();
        actor.setBodyYaw(yaw);
        if (target != null) place(actor, target, kind, yaw);
        BUSY.put(actor.getUuid(), now + ticks);

        actor.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 6, false, false, false));
        broadcast(actor, new Playback(kind, kind.animation, ROLE_ACTOR, now, false, yaw, target == null ? -1 : target.getId()));
        if (target != null) {
            BUSY.put(target.getUuid(), now + ticks);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 6, false, false, false));
            broadcast(target, new Playback(kind, kind.animation, ROLE_TARGET, now, effects && holdsTarget(kind), yaw + kind.targetYaw, actor.getId()));
        }
        sound(world, actor, kind, 0);
        TASKS.add(new Task(now + ticks, () -> finish(actor, kind, target, effects)));
    }

    private static boolean holdsTarget(RpKind kind) {
        return kind.effect == RpKind.Effect.CUFF || kind.effect == RpKind.Effect.TIE || kind.effect == RpKind.Effect.STUN;
    }

    private static void place(LivingEntity actor, LivingEntity target, RpKind kind, float yaw) {
        double distance = -kind.offsetZ / 16.0;
        float rad = yaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d at = actor.getPos().add(-MathHelper.sin(rad) * distance, 0, MathHelper.cos(rad) * distance);
        float targetYaw = MathHelper.wrapDegrees(yaw + kind.targetYaw);
        if (target instanceof ServerPlayerEntity player) {
            player.teleport(player.getServerWorld(), at.x, actor.getY(), at.z, targetYaw, 0);
        } else {
            target.refreshPositionAndAngles(at.x, actor.getY(), at.z, targetYaw, 0);
            target.setVelocity(Vec3d.ZERO);
        }
        target.setBodyYaw(targetYaw);
        target.setHeadYaw(targetYaw);
    }

    private static void finish(LivingEntity actor, RpKind kind, LivingEntity target, boolean effects) {
        PLAYING.remove(actor.getId());
        if (target != null && !(effects && holdsTarget(kind))) PLAYING.remove(target.getId());
        if (!actor.isAlive()) return;
        if (actor.getWorld() instanceof ServerWorld world) sound(world, target != null ? target : actor, kind, 1);
        ItemStack held = actor.getMainHandStack().getItem() instanceof RpItem item && item.kind == kind
                ? actor.getMainHandStack() : actor.getOffHandStack();
        boolean hasItem = held.getItem() instanceof RpItem item && item.kind == kind;
        switch (kind.effect) {
            case HEAL_SMALL -> {
                if (effects && !hasItem) return;
                actor.heal(6);
                consume(actor, held, effects);
            }
            case HEAL_PILLS -> {
                if (effects && !hasItem) return;
                actor.heal(4);
                actor.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
                consume(actor, held, effects);
            }
            case HEAL_FULL -> {
                if (effects && !hasItem) return;
                actor.heal(actor.getMaxHealth());
                clearBad(actor);
            }
            case CUFF, TIE -> {
                if (target == null || !target.isAlive()) return;
                STATES.put(target.getUuid(), new State(kind, actor.getUuid(), Long.MAX_VALUE));
                if (effects && hasItem) consume(actor, held, true);
            }
            case STUN -> {
                if (target == null || !target.isAlive()) return;
                long until = server.getTicks() + STUN_TICKS;
                STATES.put(target.getUuid(), new State(kind, actor.getUuid(), until));
                target.damage(actor.getDamageSources().mobAttack(actor), 1);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STUN_TICKS, 9, false, false, false));
            }
            case REVIVE -> {
                if (target == null || !target.isAlive()) return;
                target.setHealth(Math.max(target.getHealth(), Math.min(target.getMaxHealth(), 10)));
                clearBad(target);
            }
            default -> {
            }
        }
    }

    private static void consume(LivingEntity actor, ItemStack stack, boolean effects) {
        if (!effects || actor instanceof PlayerEntity player && player.getAbilities().creativeMode) return;
        stack.decrement(1);
    }

    private static void clearBad(LivingEntity entity) {
        entity.removeStatusEffect(StatusEffects.POISON);
        entity.removeStatusEffect(StatusEffects.WITHER);
        entity.removeStatusEffect(StatusEffects.NAUSEA);
        entity.removeStatusEffect(StatusEffects.BLINDNESS);
        entity.removeStatusEffect(StatusEffects.WEAKNESS);
        entity.setFireTicks(0);
    }

    public static void release(Entity target, ServerPlayerEntity releaser) {
        State state = STATES.remove(target.getUuid());
        MOUNTED.remove(target.getUuid());
        PLAYING.remove(target.getId());
        BUSY.remove(target.getUuid());
        if (target.getWorld() instanceof ServerWorld world) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(target.getId());
            for (ServerPlayerEntity player : PlayerLookup.tracking(target)) ServerPlayNetworking.send(player, CLEAR, buf);
            if (target instanceof ServerPlayerEntity self) ServerPlayNetworking.send(self, CLEAR, buf);
            world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        if (target instanceof LivingEntity living) living.removeStatusEffect(StatusEffects.SLOWNESS);
        if (target instanceof MobEntity mob) {
            mob.getNavigation().stop();
            mob.setSprinting(false);
        }
        if (state != null && state.kind() == RpKind.HANDCUFFS && releaser != null && !releaser.getAbilities().creativeMode) {
            releaser.giveItemStack(new ItemStack(RpItems.item(RpKind.HANDCUFFS)));
        }
    }

    private static void tick(MinecraftServer s) {
        long now = s.getTicks();
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.at() <= now) {
                due.add(task);
                it.remove();
            }
        }
        due.forEach(task -> {
            try {
                task.run().run();
            } catch (RuntimeException exception) {
                MosVegasItems.LOGGER.error("RP item effect failed", exception);
            }
        });
        BUSY.values().removeIf(until -> until < now - 1);
        for (var it = STATES.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity entity = find(s, entry.getKey());
            State state = entry.getValue();
            if (state.until() <= now) {
                it.remove();
                if (entity != null) release(entity, null);
                continue;
            }
            if (entity instanceof LivingEntity living && now % 20 == 0) {
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, state.kind() == RpKind.STUN_GUN ? 9 : 1, false, false, false));
            }
            if (entity instanceof LivingEntity living && state.kind() != RpKind.STUN_GUN) lead(s, living, state, now);
        }
    }

    private static void lead(MinecraftServer s, LivingEntity bound, State state, long now) {
        ServerPlayerEntity leader = s.getPlayerManager().getPlayer(state.by());
        if (leader == null || !leader.isAlive() || leader.getWorld() != bound.getWorld()) return;
        if (BUSY.containsKey(bound.getUuid())) return;
        Entity car = leader.hasVehicle() ? leader.getRootVehicle() : null;
        UUID rode = MOUNTED.get(bound.getUuid());
        if (car != null) {
            if (bound.hasVehicle()) return;
            double toCar = bound.distanceTo(car);
            if (toCar <= BOARD_NEAR || toCar > FOLLOW_TELEPORT) {
                if (now % 5 == 0 && board(bound, car)) MOUNTED.put(bound.getUuid(), car.getUuid());
                return;
            }
            walk(bound, car, toCar);
            return;
        }
        if (bound.hasVehicle()) {
            if (rode == null || !rode.equals(bound.getRootVehicle().getUuid())) return;
            bound.stopRiding();
            MOUNTED.remove(bound.getUuid());
            Vec3d behind = behind(leader, 1.3);
            teleport(bound, behind, leader.getYaw());
            return;
        }
        MOUNTED.remove(bound.getUuid());
        double distance = bound.distanceTo(leader);
        if (distance > FOLLOW_TELEPORT) {
            teleport(bound, behind(leader, 1.3), leader.getYaw());
            return;
        }
        walk(bound, leader, distance);
    }

    private static void walk(LivingEntity bound, Entity goal, double distance) {
        if (distance <= FOLLOW_NEAR) {
            if (bound instanceof MobEntity mob) {
                mob.getNavigation().stop();
                mob.setSprinting(false);
            }
            return;
        }
        if (bound instanceof MobEntity mob && navigate(mob, goal, distance)) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-(goal.getX() - bound.getX()), goal.getZ() - bound.getZ()));
        double speed = Math.min(FOLLOW_STEP_MAX, FOLLOW_STEP + (distance - FOLLOW_NEAR) * 0.05);
        Vec3d step = new Vec3d(goal.getX() - bound.getX(), 0, goal.getZ() - bound.getZ()).normalize()
                .multiply(Math.min(speed, distance - FOLLOW_NEAR));
        bound.setYaw(yaw);
        bound.setBodyYaw(yaw);
        bound.setHeadYaw(yaw);
        if (bound instanceof ServerPlayerEntity player) {
            player.setVelocity(step.x, player.getVelocity().y, step.z);
            player.velocityModified = true;
        } else {
            bound.move(MovementType.SELF, new Vec3d(step.x, bound.isOnGround() ? 0 : -0.3, step.z));
            if (bound.horizontalCollision && bound.isOnGround()) bound.move(MovementType.SELF, new Vec3d(0, 1.05, 0));
        }
    }

    private static boolean navigate(MobEntity mob, Entity goal, double distance) {
        EntityNavigation navigation = mob.getNavigation();
        boolean far = distance > FOLLOW_RUN;
        if (!navigation.isIdle() && mob.age % NAV_REPATH != 0 && far == mob.isSprinting()) return true;
        double attribute = mob.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        double speed = Math.sqrt(far ? 0.13 : 0.1) / (attribute > 0.001 ? attribute : Math.sqrt(0.1));
        mob.setSprinting(far);
        return navigation.startMovingTo(goal, speed);
    }

    private static boolean board(LivingEntity bound, Entity car) {
        if (bound.startRiding(car, false)) return true;
        for (Entity part : car.getPassengersDeep()) {
            if (part instanceof LivingEntity || part.hasPassengers()) continue;
            if (bound.startRiding(part, false)) return true;
        }
        return false;
    }

    private static Vec3d behind(ServerPlayerEntity leader, double distance) {
        float rad = leader.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return leader.getPos().add(MathHelper.sin(rad) * distance, 0, -MathHelper.cos(rad) * distance);
    }

    private static void teleport(LivingEntity bound, Vec3d at, float yaw) {
        if (bound instanceof ServerPlayerEntity player) {
            player.teleport(player.getServerWorld(), at.x, at.y, at.z, yaw, 0);
        } else {
            bound.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            bound.setVelocity(Vec3d.ZERO);
            bound.setHeadYaw(yaw);
            bound.setBodyYaw(yaw);
        }
    }

    private static Entity find(MinecraftServer s, UUID uuid) {
        for (ServerWorld world : s.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    public static boolean isRestrained(UUID uuid) {
        return STATES.containsKey(uuid);
    }

    private static void broadcast(Entity entity, Playback playback) {
        PLAYING.put(entity.getId(), playback);
        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) send(player, entity, playback);
        if (entity instanceof ServerPlayerEntity self) send(self, entity, playback);
    }

    private static void send(ServerPlayerEntity player, Entity entity, Playback playback) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entity.getId());
        buf.writeString(playback.kind().id);
        buf.writeString(playback.animation());
        buf.writeByte(playback.role());
        buf.writeVarInt(server == null ? 0 : (int) Math.max(0, server.getTicks() - playback.start()));
        buf.writeBoolean(playback.hold());
        buf.writeFloat(playback.yaw());
        buf.writeVarInt(playback.partner());
        ServerPlayNetworking.send(player, PLAY, buf);
    }

    private static void sound(ServerWorld world, Entity at, RpKind kind, int moment) {
        SoundEvent sound = switch (kind.effect) {
            case CUFF -> moment == 1 ? SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE : SoundEvents.ITEM_ARMOR_EQUIP_CHAIN;
            case TIE -> SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
            case STUN -> moment == 1 ? SoundEvents.ENTITY_PLAYER_HURT : SoundEvents.BLOCK_BEEHIVE_WORK;
            case REVIVE -> moment == 1 ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
            case HEAL_SMALL, HEAL_PILLS, HEAL_FULL -> moment == 1 ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
            default -> moment == 0 ? SoundEvents.ITEM_BOOK_PAGE_TURN : null;
        };
        if (sound == null) return;
        world.playSound(null, at.getBlockPos(), sound, SoundCategory.PLAYERS, moment == 1 && kind.effect.name().startsWith("HEAL") ? 0.25f : 0.8f, 1);
    }

    public static float length(RpKind kind) {
        Float length = LENGTHS.get(kind.animation);
        return length == null ? 3 : length;
    }

    private static void loadLengths() {
        for (RpKind kind : RpKind.values()) {
            if (kind.animation == null) continue;
            String path = "/assets/mosvegas_items/rp/anim/" + kind.scene() + ".animation.json";
            try (InputStream in = RpServer.class.getResourceAsStream(path)) {
                if (in == null) continue;
                var root = JsonParser.parseReader(new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getAsJsonObject();
                for (var entry : root.getAsJsonObject("animations").entrySet()) {
                    LENGTHS.put(entry.getKey(), entry.getValue().getAsJsonObject().get("animation_length").getAsFloat());
                }
            } catch (Exception exception) {
                MosVegasItems.LOGGER.error("Unreadable RP animation {}", path, exception);
            }
        }
    }
}
