package studio.merami.sliminterfacefabric.client.sound;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONSounds;
import studio.merami.slimcore.systems.RadioLibrary;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.RadioServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public final class RadioStations {
    private static final String SOUNDS = "sounds/radio";
    private static final int CHECK_TICKS = 100;
    private static final RadioLibrary FOLDER = new RadioLibrary(RadioServer.FOLDER, RadioServer.README);

    public record Track(String title, Path file, ResourceLocation resource, String hash, double seconds) {
        ByteBuffer load() throws IOException {
            byte[] bytes;
            if (file != null) {
                bytes = Files.readAllBytes(file);
            } else {
                Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(resource);
                if (found.isEmpty()) throw new IOException("Missing " + resource);
                try (InputStream in = found.get().open()) {
                    bytes = in.readAllBytes();
                }
            }
            ByteBuffer data = MemoryUtil.memAlloc(Math.max(1, bytes.length));
            data.put(bytes).flip();
            return data;
        }

        public boolean ready() {
            return hash == null || RadioDownloads.ready(hash);
        }
    }

    public record Station(String name, List<Track> tracks, double seconds, boolean remote) {
        public Component title() {
            if (!name.equals(RadioLibrary.OWN)) return Component.literal(name);
            return Component.translatable(remote ? "radio.slimvehicle.server" : "radio.slimvehicle.own");
        }
    }

    public record Tuned(Station station, int track, double offset) {
        public Track playing() {
            return station.tracks().get(track);
        }

        public boolean ready() {
            return playing().ready();
        }
    }

    private static volatile List<Station> stations = List.of();
    private static volatile int generation;
    private static List<Station> folderStations = List.of();
    private static List<Station> packStations = List.of();
    private static List<Station> serverStations = List.of();
    private static String announced;
    private static boolean checking;
    private static int clock = CHECK_TICKS;

    private RadioStations() {}

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SlimVehicle.id("radio");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                Map<ResourceLocation, Resource> found = manager.listResources(SOUNDS, id -> id.getPath().endsWith(RadioLibrary.OGG));
                CompletableFuture.supplyAsync(() -> scanPacks(found), Util.backgroundExecutor())
                        .thenAcceptAsync(result -> {
                            packStations = result;
                            publish();
                        }, Minecraft.getInstance());
            }
        });
    }

    public static void tick(Minecraft mc) {
        announce(mc);
        if (checking || ++clock < CHECK_TICKS) return;
        clock = 0;
        checking = true;
        CompletableFuture.supplyAsync(FOLDER::refresh, Util.backgroundExecutor()).whenCompleteAsync((changed, error) -> {
            checking = false;
            if (!Boolean.TRUE.equals(changed)) return;
            folderStations = FOLDER.stations().stream().map(station -> new Station(station.name(),
                    station.tracks().stream().map(track -> new Track(track.title(), track.file(), null, null, track.seconds())).toList(),
                    station.seconds(), false)).toList();
            publish();
        }, mc);
    }

    private static void announce(Minecraft mc) {
        EntityVehicleF_Physics vehicle = mc.player == null || mc.level == null ? null : BuilderVehicle.ridden(mc.player);
        Tuned tuned = vehicle == null || !vehicle.flag(EntityVehicleF_Physics.RADIO) || JSONSounds.silent(vehicle.definition().sounds.radio)
                ? null : tune(vehicle.radioStation(), mc.level.getGameTime() / 20.0);
        int percent = tuned == null || tuned.ready() ? -1 : RadioDownloads.percent(tuned.playing().hash());
        String now = tuned == null ? null : tuned.station().name() + "/" + tuned.track() + "/" + percent;
        if (now != null && !now.equals(announced)) {
            mc.player.displayClientMessage(percent < 0
                    ? Component.translatable("radio.slimvehicle.now_playing", tuned.station().title(), tuned.playing().title())
                    : Component.translatable("radio.slimvehicle.loading", tuned.station().title(), percent), true);
        }
        announced = now;
    }

    public static int generation() {
        return generation;
    }

    public static boolean any() {
        return !stations.isEmpty();
    }

    public static Tuned tune(int counter, double time) {
        List<Station> list = stations;
        if (list.isEmpty()) return null;
        Station station = list.get(Math.floorMod(counter, list.size()));
        double at = (time + (station.name().hashCode() & 0xffff) * 3.7) % station.seconds();
        for (int i = 0; i < station.tracks().size(); i++) {
            Track track = station.tracks().get(i);
            if (at < track.seconds()) return new Tuned(station, i, at);
            at -= track.seconds();
        }
        return new Tuned(station, 0, 0);
    }

    static void useServer(List<Station> list) {
        serverStations = list;
        publish();
    }

    static void changed() {
        generation++;
    }

    private static void publish() {
        if (!serverStations.isEmpty()) {
            stations = serverStations;
        } else {
            Map<String, List<Track>> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (List<Station> source : List.of(folderStations, packStations)) {
                for (Station station : source) merged.computeIfAbsent(station.name(), name -> new ArrayList<>()).addAll(station.tracks());
            }
            stations = stations(merged);
        }
        generation++;
    }

    private static List<Station> stations(Map<String, List<Track>> tracks) {
        List<Station> list = new ArrayList<>();
        for (Map.Entry<String, List<Track>> entry : tracks.entrySet()) {
            double seconds = entry.getValue().stream().mapToDouble(Track::seconds).sum();
            if (seconds > 0) list.add(new Station(entry.getKey(), List.copyOf(entry.getValue()), seconds, false));
        }
        return List.copyOf(list);
    }

    private static List<Station> scanPacks(Map<ResourceLocation, Resource> resources) {
        Map<String, List<Track>> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<ResourceLocation, Resource> entry : new TreeMap<>(resources).entrySet()) {
            ResourceLocation id = entry.getKey();
            String[] path = id.getPath().substring(SOUNDS.length() + 1).split("/");
            String station = path.length > 1 ? path[0] : id.getNamespace();
            double seconds;
            try (InputStream in = entry.getValue().open()) {
                seconds = RadioLibrary.length(in.readAllBytes());
            } catch (IOException e) {
                seconds = 0;
            }
            if (seconds > 0) found.computeIfAbsent(station, name -> new ArrayList<>()).add(new Track(RadioLibrary.title(path[path.length - 1]), null, id, null, seconds));
        }
        return stations(found);
    }
}
