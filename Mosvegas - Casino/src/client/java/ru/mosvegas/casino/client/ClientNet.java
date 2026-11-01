package ru.mosvegas.casino.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.casino.client.dealer.DealerProfiles;
import ru.mosvegas.casino.client.screen.BlackjackScreen;
import ru.mosvegas.casino.client.state.ClientTables;
import ru.mosvegas.casino.client.voice.VoicePlayer;
import ru.mosvegas.casino.net.CasinoNet;
import ru.mosvegas.casino.net.TableAction;
import ru.mosvegas.casino.net.TableSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class ClientNet {
    private ClientNet() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.TABLE_STATE, (client, handler, buf, sender) -> {
            TableSnapshot snapshot = TableSnapshot.read(buf);
            client.execute(() -> ClientTables.update(snapshot));
        });
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.TABLE_REMOVE, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> ClientTables.remove(pos));
        });
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.OPEN_TABLE, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                if (client.currentScreen == null || client.currentScreen instanceof BlackjackScreen) client.setScreen(new BlackjackScreen(pos));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.PROFILES, (client, handler, buf, sender) -> {
            PacketByteBuf copy = PacketByteBufs.copy(buf);
            client.execute(() -> DealerProfiles.read(copy));
        });
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.VOICE, (client, handler, buf, sender) -> {
            int entity = buf.readVarInt();
            String set = buf.readString();
            float pitch = buf.readFloat();
            int n = Math.min(8, buf.readVarInt());
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < n; i++) keys.add(buf.readString());
            client.execute(() -> VoicePlayer.play(entity, set, pitch, keys));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ClientTables.clear();
            DealerProfiles.clear();
        }));
    }

    public static void send(BlockPos pos, TableAction action, int arg, long amount) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null || !ClientPlayNetworking.canSend(CasinoNet.ACTION)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(action.ordinal());
        buf.writeVarInt(arg + 1);
        buf.writeVarLong(amount);
        ClientPlayNetworking.send(CasinoNet.ACTION, buf);
    }
}
