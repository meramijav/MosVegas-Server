package studio.merami.sliminterfacefabric.client.sound;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.systems.RadioLibrary;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.PacketRadioChunk;
import studio.merami.sliminterfacefabric.network.PacketRadioLibrary;
import studio.merami.sliminterfacefabric.network.PacketRadioRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class RadioDownloads {
    public static final Path CACHE = FabricLoader.getInstance().getGameDir().resolve("slimvehicle").resolve("cache").resolve("radio");
    private static final String PART = ".part";
    private static final int WINDOW = 8;
    private static final int STALL_TICKS = 200;
    private static final int TRIES = 3;
    private static final long KEEP = TimeUnit.DAYS.toMillis(30);
    private static final Set<String> READY = ConcurrentHashMap.newKeySet();
    private static final Set<String> VERIFYING = new HashSet<>();
    private static final Map<String, Integer> FAILURES = new HashMap<>();
    private static List<RadioLibrary.Station> library = List.of();
    private static Download active;

    private static final class Download {
        final RadioLibrary.Track track;
        final FileChannel channel;
        final int chunks;
        final BitSet received = new BitSet();
        int requested;
        long bytes;
        int idle;

        Download(RadioLibrary.Track track, FileChannel channel) {
            this.track = track;
            this.channel = channel;
            this.chunks = (int) ((track.size() + PacketRadioChunk.SIZE - 1) / PacketRadioChunk.SIZE);
        }
    }

    private RadioDownloads() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketRadioLibrary.TYPE, (packet, player, sender) -> accept(packet.stations()));
        ClientPlayNetworking.registerGlobalReceiver(PacketRadioChunk.TYPE, (packet, player, sender) -> accept(packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RadioDownloads::reset));
    }

    public static boolean ready(String hash) {
        return READY.contains(hash);
    }

    public static int percent(String hash) {
        Download download = active;
        return download != null && download.track.hash().equals(hash) ? (int) (download.bytes * 100 / download.track.size()) : 0;
    }

    private static Path file(String hash) {
        return CACHE.resolve(hash + RadioLibrary.OGG);
    }

    private static Path part(String hash) {
        return CACHE.resolve(hash + PART);
    }

    private static void accept(List<RadioLibrary.Station> stations) {
        library = stations;
        Set<String> hashes = hashes(stations);
        if (active != null && !hashes.contains(active.track.hash())) cancel();
        RadioStations.useServer(stations.stream().map(station -> new RadioStations.Station(station.name(),
                station.tracks().stream().map(track -> new RadioStations.Track(track.title(), file(track.hash()), null, track.hash(), track.seconds())).toList(),
                station.seconds(), true)).toList());
        if (stations.isEmpty()) return;
        CompletableFuture.supplyAsync(() -> cached(stations, hashes), Util.ioPool()).thenAcceptAsync(found -> {
            if (READY.addAll(found)) RadioStations.changed();
        }, Minecraft.getInstance());
    }

    private static Set<String> hashes(List<RadioLibrary.Station> stations) {
        Set<String> hashes = new HashSet<>();
        for (RadioLibrary.Station station : stations) for (RadioLibrary.Track track : station.tracks()) hashes.add(track.hash());
        return hashes;
    }

    private static Set<String> cached(List<RadioLibrary.Station> stations, Set<String> hashes) {
        Set<String> found = new HashSet<>();
        FileTime now = FileTime.fromMillis(System.currentTimeMillis());
        for (RadioLibrary.Station station : stations) {
            for (RadioLibrary.Track track : station.tracks()) {
                Path file = file(track.hash());
                try {
                    if (Files.size(file) == track.size()) {
                        Files.setLastModifiedTime(file, now);
                        found.add(track.hash());
                    }
                } catch (IOException ignored) {
                }
            }
        }
        try (Stream<Path> files = Files.list(CACHE)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                String hash = name.substring(0, Math.max(0, name.indexOf('.')));
                if (!hashes.contains(hash) && now.toMillis() - Files.getLastModifiedTime(file).toMillis() > KEEP) Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
        return found;
    }

    public static void tick(Minecraft mc) {
        if (library.isEmpty() || mc.getConnection() == null) return;
        Download download = active;
        if (download != null) {
            if (++download.idle > STALL_TICKS) {
                SlimCore.LOGGER.warn("Radio: the server stopped sending {}", download.track.title());
                cancel();
                fail(download.track);
            }
            return;
        }
        RadioLibrary.Track next = next(mc);
        if (next != null) start(next);
    }

    private static RadioLibrary.Track next(Minecraft mc) {
        EntityVehicleF_Physics vehicle = mc.player == null || mc.level == null ? null : BuilderVehicle.ridden(mc.player);
        if (vehicle != null) {
            RadioStations.Tuned tuned = RadioStations.tune(vehicle.radioStation(), mc.level.getGameTime() / 20.0);
            RadioLibrary.Station station = library.get(Math.floorMod(vehicle.radioStation(), library.size()));
            int first = tuned == null || !tuned.station().remote() ? 0 : tuned.track();
            for (int i = 0; i < station.tracks().size(); i++) {
                RadioLibrary.Track track = station.tracks().get((first + i) % station.tracks().size());
                if (wanted(track)) return track;
            }
        }
        for (RadioLibrary.Station station : library) {
            for (RadioLibrary.Track track : station.tracks()) if (wanted(track)) return track;
        }
        return null;
    }

    private static boolean wanted(RadioLibrary.Track track) {
        return !READY.contains(track.hash()) && !VERIFYING.contains(track.hash()) && FAILURES.getOrDefault(track.hash(), 0) < TRIES;
    }

    private static void start(RadioLibrary.Track track) {
        try {
            Files.createDirectories(CACHE);
            active = new Download(track, FileChannel.open(part(track.hash()), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Radio: could not write to {}: {}", CACHE, e.getMessage());
            fail(track);
            return;
        }
        for (int i = 0; i < WINDOW; i++) request(active);
    }

    private static void request(Download download) {
        if (download.requested >= download.chunks) return;
        ClientPlayNetworking.send(new PacketRadioRequest(download.track.hash(), download.requested * PacketRadioChunk.SIZE));
        download.requested++;
    }

    private static void accept(PacketRadioChunk packet) {
        Download download = active;
        if (download == null || !download.track.hash().equals(packet.hash()) || packet.offset() % PacketRadioChunk.SIZE != 0) return;
        int index = packet.offset() / PacketRadioChunk.SIZE;
        if (index >= download.chunks || download.received.get(index) || packet.data().length != Math.min(PacketRadioChunk.SIZE, download.track.size() - packet.offset())) return;
        try {
            ByteBuffer data = ByteBuffer.wrap(packet.data());
            while (data.hasRemaining()) download.channel.write(data, packet.offset() + data.position());
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Radio: could not write {}: {}", part(download.track.hash()), e.getMessage());
            cancel();
            fail(download.track);
            return;
        }
        download.received.set(index);
        download.bytes += packet.data().length;
        download.idle = 0;
        request(download);
        if (download.received.cardinality() == download.chunks) finish(download);
    }

    private static void finish(Download download) {
        active = null;
        close(download);
        RadioLibrary.Track track = download.track;
        VERIFYING.add(track.hash());
        CompletableFuture.supplyAsync(() -> verify(track.hash()), Util.ioPool()).thenAcceptAsync(valid -> {
            VERIFYING.remove(track.hash());
            if (valid) {
                READY.add(track.hash());
                RadioStations.changed();
                SlimCore.LOGGER.info("Radio: got {} from the server ({} KB)", track.title(), track.size() >> 10);
            } else {
                fail(track);
            }
        }, Minecraft.getInstance());
    }

    private static boolean verify(String hash) {
        Path part = part(hash);
        try {
            if (RadioLibrary.hash(Files.readAllBytes(part)).equals(hash)) {
                Files.move(part, file(hash), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
            Files.deleteIfExists(part);
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Radio: could not store {}: {}", file(hash), e.getMessage());
        }
        return false;
    }

    private static void fail(RadioLibrary.Track track) {
        if (FAILURES.merge(track.hash(), 1, Integer::sum) == TRIES) SlimCore.LOGGER.warn("Radio: gave up on {} after {} tries", track.title(), TRIES);
    }

    private static void cancel() {
        Download download = active;
        if (download == null) return;
        active = null;
        close(download);
        try {
            Files.deleteIfExists(part(download.track.hash()));
        } catch (IOException ignored) {
        }
    }

    private static void close(Download download) {
        try {
            download.channel.close();
        } catch (IOException ignored) {
        }
    }

    private static void reset() {
        cancel();
        library = List.of();
        FAILURES.clear();
        RadioStations.useServer(List.of());
    }
}
