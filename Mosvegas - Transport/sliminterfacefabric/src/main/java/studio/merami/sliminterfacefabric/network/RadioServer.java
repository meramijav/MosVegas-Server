package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.systems.RadioLibrary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RadioServer {
    public static final Path FOLDER = FabricLoader.getInstance().getGameDir().resolve("slimvehicle").resolve("radio");
    public static final List<String> README = List.of(
            "Slim Vehicle — музыка для радио в машинах",
            "",
            "Положите сюда треки в формате .ogg (Ogg Vorbis). Другие форматы (mp3, wav, m4a) игра не читает:",
            "переведите их в .ogg, например в Audacity (Файл → Экспорт → Ogg Vorbis) или командой",
            "ffmpeg -i песня.mp3 -c:a libvorbis -q:a 5 песня.ogg",
            "",
            "Файлы прямо в этой папке — отдельная станция. Каждая папка внутри — станция с именем папки:",
            "  radio/песня.ogg            → «Моя музыка» (на сервере — «Радио сервера»)",
            "  radio/Rock FM/трек1.ogg    → станция «Rock FM»",
            "Треки станции идут по алфавиту и по кругу. Мод сам замечает новые файлы через несколько секунд, перезапуск не нужен.",
            "",
            "На сервере: это папка сервера. Её станции получают все игроки с модом: музыка сама скачивается",
            "им в slimvehicle/cache/radio при входе и после каждого изменения папки. Пока станция скачивается,",
            "над хотбаром видно «загрузка» и играет обычная мелодия радио. Если на сервере есть музыка,",
            "все слышат только её: одна и та же станция и песня у всех в машине. /slimvehicle radio показывает станции сервера.",
            "",
            "В одиночной игре это ваша музыка. В машине: M — включить радио (двигатель должен работать),",
            ". и , — следующая и предыдущая станция. Клавиши меняются в Настройки → Управление → Slim Vehicle.",
            "",
            "Slim Vehicle — radio music",
            "Put .ogg (Ogg Vorbis) files here. Loose files form one station; every subfolder is a station named after the folder.",
            "On a server this is the server's folder: every player with the mod downloads its stations automatically and hears them.",
            "In a car: M — radio on/off, . and , — next and previous station.");
    private static final int CHECK_TICKS = 100;
    private static final int PER_PLAYER = 8;
    private static final int PER_TICK = 12;
    private static final int QUEUE = 64;
    private static final RadioLibrary LIBRARY = new RadioLibrary(FOLDER, README);
    private static final Map<UUID, ArrayDeque<PacketRadioRequest>> QUEUES = new LinkedHashMap<>();
    private static volatile Map<String, RadioLibrary.Track> files = Map.of();
    private static PacketRadioLibrary library = new PacketRadioLibrary(List.of());
    private static boolean checking;
    private static int clock;

    private RadioServer() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            QUEUES.clear();
            checking = false;
            clock = CHECK_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(RadioServer::tick);
        ServerPlayNetworking.registerGlobalReceiver(PacketRadioRequest.TYPE, (packet, player, sender) -> request(player, packet));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!files.isEmpty() && receives(server, handler.getPlayer())) sender.sendPacket(library);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> QUEUES.remove(handler.getPlayer().getUUID())));
    }

    public static List<RadioLibrary.Station> stations() {
        return library.stations();
    }

    private static boolean receives(MinecraftServer server, ServerPlayer player) {
        return !server.isSingleplayerOwner(player.getGameProfile()) && ServerPlayNetworking.canSend(player, PacketRadioLibrary.TYPE);
    }

    private static void tick(MinecraftServer server) {
        serve(server);
        if (checking || ++clock < CHECK_TICKS) return;
        clock = 0;
        checking = true;
        CompletableFuture.supplyAsync(LIBRARY::refresh, Util.backgroundExecutor()).whenCompleteAsync((changed, error) -> {
            checking = false;
            if (error != null) SlimCore.LOGGER.warn("Radio: could not scan {}", FOLDER, error);
            else if (changed) publish(server);
        }, server);
    }

    private static void publish(MinecraftServer server) {
        List<RadioLibrary.Station> stations = LIBRARY.stations();
        Map<String, RadioLibrary.Track> byHash = new HashMap<>();
        for (RadioLibrary.Station station : stations) for (RadioLibrary.Track track : station.tracks()) byHash.put(track.hash(), track);
        boolean was = !files.isEmpty();
        files = Map.copyOf(byHash);
        library = new PacketRadioLibrary(stations);
        if (was || !files.isEmpty()) {
            long bytes = byHash.values().stream().mapToLong(RadioLibrary.Track::size).sum();
            SlimCore.LOGGER.info("Radio: {} stations, {} tracks ({} MB) in {}", stations.size(), byHash.size(), String.format(Locale.ROOT, "%.1f", bytes / 1048576.0), FOLDER);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (receives(server, player)) ServerPlayNetworking.send(player, library);
        }
    }

    private static void request(ServerPlayer player, PacketRadioRequest packet) {
        RadioLibrary.Track track = files.get(packet.hash());
        if (track == null || packet.offset() < 0 || packet.offset() >= track.size()) return;
        ArrayDeque<PacketRadioRequest> queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.size() < QUEUE) queue.add(packet);
    }

    private static void serve(MinecraftServer server) {
        int budget = PER_TICK;
        for (UUID id : new ArrayList<>(QUEUES.keySet())) {
            if (budget <= 0) break;
            ArrayDeque<PacketRadioRequest> queue = QUEUES.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            for (int sent = 0; sent < PER_PLAYER && budget > 0 && !queue.isEmpty(); sent++, budget--) {
                PacketRadioChunk chunk = chunk(queue.poll());
                if (chunk != null) ServerPlayNetworking.send(player, chunk);
            }
            if (!queue.isEmpty()) QUEUES.put(id, queue);
        }
    }

    private static PacketRadioChunk chunk(PacketRadioRequest request) {
        RadioLibrary.Track track = files.get(request.hash());
        if (track == null) return null;
        ByteBuffer data = ByteBuffer.allocate((int) Math.min(PacketRadioChunk.SIZE, track.size() - request.offset()));
        try (FileChannel channel = FileChannel.open(track.file(), StandardOpenOption.READ)) {
            int read;
            do {
                read = channel.read(data, request.offset() + data.position());
            } while (read > 0 && data.hasRemaining());
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Radio: could not read {}: {}", track.file(), e.getMessage());
            return null;
        }
        return data.hasRemaining() ? null : new PacketRadioChunk(request.hash(), request.offset(), data.array());
    }
}
