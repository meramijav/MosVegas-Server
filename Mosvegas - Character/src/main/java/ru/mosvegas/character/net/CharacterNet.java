package ru.mosvegas.character.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.MosVegasCharacter;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.server.CharacterGate;

import java.util.List;
import java.util.UUID;

public final class CharacterNet {
    public static final Identifier OPEN_EDITOR = MosVegasCharacter.id("open_editor");
    public static final Identifier CLOSE_EDITOR = MosVegasCharacter.id("close_editor");
    public static final Identifier APPEARANCE = MosVegasCharacter.id("appearance");
    public static final Identifier SUBMIT = MosVegasCharacter.id("submit");

    private CharacterNet() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SUBMIT, (server, player, handler, buf, sender) -> {
            Appearance appearance = Appearance.read(buf);
            server.execute(() -> CharacterGate.submit(player, appearance));
        });
    }

    public static boolean hasClientMod(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, OPEN_EDITOR);
    }

    public static void openEditor(ServerPlayerEntity player, Appearance current, List<String> outfits) {
        if (!hasClientMod(player)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(current != null);
        if (current != null) current.write(buf);
        buf.writeVarInt(outfits.size());
        for (String id : outfits) buf.writeString(id, 64);
        ServerPlayNetworking.send(player, OPEN_EDITOR, buf);
    }

    public static void closeEditor(ServerPlayerEntity player) {
        if (!hasClientMod(player)) return;
        ServerPlayNetworking.send(player, CLOSE_EDITOR, PacketByteBufs.empty());
    }

    public static void sendAppearance(ServerPlayerEntity to, UUID owner, Appearance appearance) {
        if (!ServerPlayNetworking.canSend(to, APPEARANCE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(owner);
        buf.writeBoolean(appearance != null);
        if (appearance != null) appearance.write(buf);
        ServerPlayNetworking.send(to, APPEARANCE, buf);
    }

    public static void broadcastAppearance(MinecraftServer server, UUID owner, Appearance appearance) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendAppearance(p, owner, appearance);
    }
}
