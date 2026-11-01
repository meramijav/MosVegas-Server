package studio.merami.slimcore.systems;

import studio.merami.slimcore.SlimCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class RadioLibrary {
    public static final String OGG = ".ogg";
    public static final String OWN = "";
    public static final long MAX_SIZE = 128L << 20;
    private static final int DEPTH = 4;
    private static final byte[] CAPTURE = "OggS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VORBIS = "vorbis".getBytes(StandardCharsets.US_ASCII);

    public record Track(String title, String hash, long size, double seconds, Path file) {}

    public record Station(String name, List<Track> tracks) {
        public double seconds() {
            return tracks.stream().mapToDouble(Track::seconds).sum();
        }

        public long size() {
            return tracks.stream().mapToLong(Track::size).sum();
        }
    }

    private final Path folder;
    private final List<String> readme;
    private final Map<String, Track> known = new ConcurrentHashMap<>();
    private volatile List<Station> stations = List.of();
    private Map<Path, String> seen;
    private String signature;

    public RadioLibrary(Path folder, List<String> readme) {
        this.folder = folder;
        this.readme = readme;
    }

    public Path folder() {
        return folder;
    }

    public List<Station> stations() {
        return stations;
    }

    public synchronized boolean refresh() {
        Map<Path, String> keys = new LinkedHashMap<>();
        for (Path file : files()) keys.put(file, key(file));
        boolean settling = seen != null && !keys.equals(seen);
        seen = keys;
        String now = String.join("\n", keys.values());
        if (settling || now.equals(signature)) return false;
        signature = now;
        Map<String, List<Track>> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<Path, String> entry : keys.entrySet()) {
            Track track = known.get(entry.getValue());
            if (track == null) {
                track = read(entry.getKey());
                if (track == null) {
                    signature = null;
                    continue;
                }
                known.put(entry.getValue(), track);
            }
            if (track.seconds() <= 0) continue;
            Path relative = folder.relativize(entry.getKey());
            String station = relative.getNameCount() > 1 ? relative.getName(0).toString() : OWN;
            found.computeIfAbsent(station, name -> new ArrayList<>()).add(track);
        }
        known.keySet().retainAll(keys.values());
        List<Station> list = new ArrayList<>();
        found.forEach((name, tracks) -> list.add(new Station(name, List.copyOf(tracks))));
        stations = List.copyOf(list);
        return true;
    }

    private List<Path> files() {
        try {
            Files.createDirectories(folder);
            Path file = folder.resolve("README.txt");
            if (!Files.exists(file)) Files.write(file, readme, StandardCharsets.UTF_8);
            try (Stream<Path> walk = Files.walk(folder, DEPTH)) {
                return walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(OGG)).sorted().toList();
            }
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Could not read radio music in {}: {}", folder, e.getMessage());
            return List.of();
        }
    }

    private static String key(Path file) {
        try {
            return file + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return file + "|?";
        }
    }

    private static Track read(Path file) {
        String title = title(file.getFileName().toString());
        try {
            if (Files.size(file) > MAX_SIZE) {
                SlimCore.LOGGER.warn("Radio: {} is larger than {} MB", file, MAX_SIZE >> 20);
                return new Track(title, "", 0, 0, file);
            }
            byte[] bytes = Files.readAllBytes(file);
            double seconds = length(bytes);
            if (seconds <= 0) SlimCore.LOGGER.warn("Radio: {} is not an Ogg Vorbis file", file);
            return new Track(title, hash(bytes), bytes.length, seconds, file);
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Radio: could not read {}: {}", file, e.getMessage());
            return null;
        }
    }

    public static String title(String name) {
        return name.substring(0, name.length() - OGG.length()).replace('_', ' ');
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static double length(byte[] data) {
        if (!at(data, 0, CAPTURE)) return 0;
        long rate = 0;
        long samples = -1;
        int serial = 0;
        int page = 0;
        while (page + 27 <= data.length) {
            if (!at(data, page, CAPTURE)) {
                page++;
                continue;
            }
            int segments = data[page + 26] & 0xff;
            int body = page + 27 + segments;
            if (body > data.length) break;
            int size = 0;
            for (int i = 0; i < segments; i++) size += data[page + 27 + i] & 0xff;
            if (rate == 0) {
                if (body + 16 > data.length || data[body] != 1 || !at(data, body + 1, VORBIS)) return 0;
                serial = int32(data, page + 14);
                rate = int32(data, body + 12) & 0xffffffffL;
                if (rate == 0) return 0;
            } else if (int32(data, page + 14) == serial && body + size <= data.length) {
                long granule = int32(data, page + 6) & 0xffffffffL | (long) int32(data, page + 10) << 32;
                if (granule > 0) samples = granule;
            }
            page = body + size;
        }
        return samples > 0 ? samples / (double) rate : 0;
    }

    private static boolean at(byte[] data, int offset, byte[] pattern) {
        if (offset + pattern.length > data.length) return false;
        for (int i = 0; i < pattern.length; i++) if (data[offset + i] != pattern[i]) return false;
        return true;
    }

    private static int int32(byte[] data, int offset) {
        return data[offset] & 0xff | (data[offset + 1] & 0xff) << 8 | (data[offset + 2] & 0xff) << 16 | (data[offset + 3] & 0xff) << 24;
    }
}
