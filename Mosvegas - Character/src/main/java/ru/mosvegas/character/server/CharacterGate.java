package ru.mosvegas.character.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.character.MosVegasCharacter;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.data.CharacterOptions;
import ru.mosvegas.character.net.CharacterNet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CharacterGate {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Vec3d> ANCHORS = new ConcurrentHashMap<>();

    private CharacterGate() {}

    public static boolean isPending(PlayerEntity player) {
        return player != null && PENDING.contains(player.getUuid());
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING.remove(handler.player.getUuid());
            ANCHORS.remove(handler.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(CharacterGate::tick);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity p && isPending(p)));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> deny(player));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> deny(player));
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> deny(player));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> deny(player));
        UseItemCallback.EVENT.register((player, world, hand) -> isPending(player)
                ? TypedActionResult.fail(player.getStackInHand(hand))
                : TypedActionResult.pass(player.getStackInHand(hand)));
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !isPending(player));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !isPending(sender));
    }

    private static ActionResult deny(PlayerEntity player) {
        return isPending(player) ? ActionResult.FAIL : ActionResult.PASS;
    }

    private static void onJoin(ServerPlayerEntity player, MinecraftServer server) {
        UUID id = player.getUuid();
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            Appearance a = MosVegasCharacter.STORE.get(other.getUuid());
            if (a != null) CharacterNet.sendAppearance(player, other.getUuid(), a);
        }
        Appearance own = MosVegasCharacter.STORE.get(id);
        if (own != null) {
            player.calculateDimensions();
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (other != player) CharacterNet.sendAppearance(other, id, own);
            }
            return;
        }
        if (!CharacterNet.hasClientMod(player)) {
            if (CharacterOptions.requireClientMod()) {
                player.networkHandler.disconnect(Text.literal("Для игры на MosVegas RP нужен клиент с модом создания персонажа."));
            }
            return;
        }
        lock(player);
    }

    public static void lock(ServerPlayerEntity player) {
        PENDING.add(player.getUuid());
        ANCHORS.put(player.getUuid(), player.getPos());
        player.closeHandledScreen();
        MosVegasCharacter.LOGGER.info("Character editor opened for {}", player.getGameProfile().getName());
        CharacterNet.openEditor(player, MosVegasCharacter.STORE.get(player.getUuid()), CharacterOptions.outfits());
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (UUID id : PENDING) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            Vec3d anchor = ANCHORS.computeIfAbsent(id, k -> p.getPos());
            p.fallDistance = 0;
            p.setFireTicks(0);
            p.setAir(p.getMaxAir());
            if (p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();
            if (p.squaredDistanceTo(anchor) > 0.25) {
                p.networkHandler.requestTeleport(anchor.x, anchor.y, anchor.z, p.getYaw(), p.getPitch());
            }
        }
    }

    public static void submit(ServerPlayerEntity player, Appearance appearance) {
        if (!isPending(player)) return;
        if (!appearance.isValid(CharacterOptions.outfits())) {
            MosVegasCharacter.LOGGER.warn("Rejected invalid appearance from {}", player.getGameProfile().getName());
            CharacterNet.openEditor(player, null, CharacterOptions.outfits());
            return;
        }
        MosVegasCharacter.STORE.put(player.getUuid(), appearance);
        MosVegasCharacter.LOGGER.info("Character created for {}", player.getGameProfile().getName());
        player.calculateDimensions();
        PENDING.remove(player.getUuid());
        ANCHORS.remove(player.getUuid());
        CharacterNet.closeEditor(player);
        CharacterNet.broadcastAppearance(player.getServer(), player.getUuid(), appearance);
        player.sendMessage(Text.literal("Персонаж создан. Добро пожаловать на MosVegas RP!").formatted(Formatting.GOLD));
    }

    public static boolean reset(MinecraftServer server, UUID id) {
        boolean had = MosVegasCharacter.STORE.remove(id);
        CharacterNet.broadcastAppearance(server, id, null);
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
        if (online != null) online.calculateDimensions();
        if (online != null && CharacterNet.hasClientMod(online)) lock(online);
        return had || online != null;
    }

    public static void clear() {
        PENDING.clear();
        ANCHORS.clear();
    }
}
