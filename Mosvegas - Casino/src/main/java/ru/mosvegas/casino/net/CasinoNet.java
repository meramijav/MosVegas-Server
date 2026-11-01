package ru.mosvegas.casino.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.config.DealerProfile;
import ru.mosvegas.casino.dealer.SkinResolver;
import ru.mosvegas.casino.table.BlackjackTable;
import ru.mosvegas.casino.table.TableRegistry;

import java.util.List;

/** Channel ids and the server side of the casino protocol. */
public final class CasinoNet {
    public static final Identifier TABLE_STATE = MosVegasCasino.id("table_state");
    public static final Identifier TABLE_REMOVE = MosVegasCasino.id("table_remove");
    public static final Identifier OPEN_TABLE = MosVegasCasino.id("open_table");
    public static final Identifier PROFILES = MosVegasCasino.id("dealer_profiles");
    public static final Identifier ACTION = MosVegasCasino.id("table_action");
    /** Dealer voice line: entity id, voice set, pitch, candidate line keys (the first one the client has is played). */
    public static final Identifier VOICE = MosVegasCasino.id("dealer_voice");

    private CasinoNet() {}

    public static void sendVoice(ServerPlayerEntity player, int entityId, String voiceSet, float pitch, List<String> keys) {
        if (!ServerPlayNetworking.canSend(player, VOICE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeString(voiceSet);
        buf.writeFloat(pitch);
        buf.writeVarInt(keys.size());
        for (String k : keys) buf.writeString(k);
        ServerPlayNetworking.send(player, VOICE, buf);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            TableAction action = TableAction.byId(buf.readVarInt());
            int arg = buf.readVarInt() - 1;
            long amount = buf.readVarLong();
            server.execute(() -> {
                if (action == null) return;
                BlackjackTable table = TableRegistry.get(player.getWorld(), pos);
                if (table != null) table.handleAction(player, action, arg, amount);
            });
        });
    }

    public static void sendOpen(ServerPlayerEntity player, BlockPos pos) {
        if (!ServerPlayNetworking.canSend(player, OPEN_TABLE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, OPEN_TABLE, buf);
    }

    public static void sendRemove(ServerPlayerEntity player, BlockPos pos) {
        if (!ServerPlayNetworking.canSend(player, TABLE_REMOVE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, TABLE_REMOVE, buf);
    }

    /** Dealer looks: id, display name, slim arms, skin reference (resource id or http URL), optional model/animations. */
    public static void sendProfiles(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, PROFILES)) return;
        List<DealerProfile> profiles = ConfigManager.get().dealers.profiles;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(profiles.size());
        for (DealerProfile p : profiles) {
            SkinResolver.Resolved skin = SkinResolver.resolve(p);
            buf.writeString(p.id);
            buf.writeString(p.name);
            buf.writeBoolean(skin.slim());
            buf.writeString(skin.reference());
            buf.writeString(p.model == null ? "" : p.model);
            buf.writeString(p.animations == null ? "" : p.animations);
        }
        ServerPlayNetworking.send(player, PROFILES, buf);
    }
}
