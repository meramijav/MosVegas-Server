package studio.merami.slimcore.packloading;

import com.google.gson.JsonObject;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.jsondefs.AJSONItem;
import studio.merami.slimcore.jsondefs.JSONPack;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.rendering.AnimationFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class PackParser {
    public static final String PACK_FILE = "slimpack.json";

    public record Source(String name, List<Path> roots, boolean builtIn) {}

    public record Pack(Source source, JSONPack meta, List<JSONVehicle> vehicles) {}

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final List<Pack> PACKS = new ArrayList<>();
    private static final Map<String, JSONVehicle> VEHICLES = new LinkedHashMap<>();
    private static final Map<String, JSONVehicle> BY_NAME = new HashMap<>();
    private static final Map<String, JsonObject> CONFIGS = new LinkedHashMap<>();
    private static JSONVehicle defaultVehicle;
    private static Path configFolder;
    private static Path legacyOverrides;
    private static Map<String, JsonObject> remoteConfigs;

    private PackParser() {}

    public static synchronized void load(List<Source> sources, Path overrides) {
        load(sources, null, overrides);
    }

    public static synchronized void load(List<Source> sources, Path configs, Path overrides) {
        configFolder = configs;
        legacyOverrides = overrides;
        remoteConfigs = null;
        reload(sources);
    }

    public static synchronized void useRemoteConfigs(Map<String, JsonObject> configs) {
        if (configs == null && remoteConfigs == null) return;
        remoteConfigs = configs;
        reload(List.copyOf(SOURCES));
    }

    public static synchronized Map<String, JsonObject> configs() {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : CONFIGS.entrySet()) copy.put(entry.getKey(), entry.getValue().deepCopy());
        return copy;
    }

    private static void reload(List<Source> sources) {
        Path overrides = legacyOverrides;
        SOURCES.clear();
        SOURCES.addAll(sources);
        PACKS.clear();
        VEHICLES.clear();
        BY_NAME.clear();
        CONFIGS.clear();
        Map<String, JsonObject> raw = new LinkedHashMap<>();
        Map<String, Source> origin = new HashMap<>();
        Map<Source, List<String>> order = new LinkedHashMap<>();
        Map<Source, JSONPack> metas = new HashMap<>();
        for (Source source : sources) {
            JSONPack meta = readMeta(source);
            metas.put(source, meta);
            List<String> found = new ArrayList<>();
            for (Path root : source.roots()) collect(source, root, raw, origin, found);
            order.put(source, ordered(meta, found));
        }
        Map<String, JsonObject> resolved = new HashMap<>();
        for (Map.Entry<Source, List<String>> entry : order.entrySet()) {
            Source source = entry.getKey();
            List<JSONVehicle> vehicles = new ArrayList<>();
            for (String id : entry.getValue()) {
                try {
                    JSONVehicle vehicle = build(id, source, resolve(id, raw, resolved, new HashSet<>()), overrides);
                    vehicles.add(vehicle);
                    VEHICLES.put(id, vehicle);
                    BY_NAME.putIfAbsent(vehicle.name, vehicle);
                } catch (RuntimeException e) {
                    if (source.builtIn()) throw new IllegalStateException("Vehicle definition " + id + ": " + e.getMessage(), e);
                    SlimCore.LOGGER.error("Skipping vehicle {} of pack {}: {}", id, source.name(), e.getMessage());
                }
            }
            PACKS.add(new Pack(source, metas.get(source), List.copyOf(vehicles)));
        }
        if (VEHICLES.isEmpty()) throw new IllegalStateException("No vehicle definitions in any pack");
        defaultVehicle = VEHICLES.values().iterator().next();
        SlimCore.LOGGER.info("Loaded {} vehicles from {} packs", VEHICLES.size(), PACKS.size());
    }

    private static JSONPack readMeta(Source source) {
        for (Path root : source.roots()) {
            Path file = root.resolve(PACK_FILE);
            if (!Files.isRegularFile(file)) continue;
            try {
                JSONPack meta = JSONParser.GSON.fromJson(JSONParser.read(file), JSONPack.class);
                if (meta.name == null) meta.name = source.name();
                return meta;
            } catch (IOException | RuntimeException e) {
                SlimCore.LOGGER.error("Unreadable {} in pack {}: {}", PACK_FILE, source.name(), e.getMessage());
            }
        }
        JSONPack meta = new JSONPack();
        meta.name = source.name();
        return meta;
    }

    private static void collect(Source source, Path root, Map<String, JsonObject> raw, Map<String, Source> origin, List<String> found) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> namespaces = Files.list(assets)) {
            for (Path namespace : namespaces.sorted().toList()) {
                Path folder = namespace.resolve("vehicles");
                if (!Files.isDirectory(folder)) continue;
                try (Stream<Path> files = Files.list(folder)) {
                    for (Path file : files.sorted().toList()) {
                        String fileName = name(file);
                        if (!fileName.endsWith(".json")) continue;
                        String id = name(namespace) + ":" + fileName.substring(0, fileName.length() - 5);
                        if (raw.containsKey(id)) {
                            SlimCore.LOGGER.warn("Vehicle {} of pack {} is already defined by pack {}", id, source.name(), origin.get(id).name());
                            continue;
                        }
                        try {
                            raw.put(id, JSONParser.read(file));
                            origin.put(id, source);
                            found.add(id);
                        } catch (IOException | RuntimeException e) {
                            if (source.builtIn()) throw new IllegalStateException("Unreadable vehicle " + id, e);
                            SlimCore.LOGGER.error("Unreadable vehicle {} in pack {}: {}", id, source.name(), e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String name(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static List<String> ordered(JSONPack meta, List<String> found) {
        List<String> result = new ArrayList<>();
        for (String wanted : meta.vehicles) {
            for (String id : found) {
                if (!result.contains(id) && (id.equals(wanted) || id.endsWith(":" + wanted))) result.add(id);
            }
        }
        for (String id : found) {
            if (!result.contains(id)) result.add(id);
        }
        return result;
    }

    private static JsonObject resolve(String id, Map<String, JsonObject> raw, Map<String, JsonObject> done, Set<String> seen) {
        JsonObject cached = done.get(id);
        if (cached != null) return cached;
        if (!seen.add(id)) throw new IllegalStateException("parent loop at " + id);
        JsonObject own = raw.get(id);
        if (own == null) throw new IllegalStateException("no definition " + id);
        JsonObject result = own;
        if (own.has("parent")) {
            String parent = own.get("parent").getAsString();
            if (!parent.contains(":")) parent = id.substring(0, id.indexOf(':') + 1) + parent;
            result = JSONParser.merge(resolve(parent, raw, done, seen).deepCopy(), own);
        }
        done.put(id, result);
        return result;
    }

    private static JSONVehicle build(String id, Source source, JsonObject resolved, Path overrides) {
        JsonObject json = resolved.deepCopy();
        if (configFolder == null && remoteConfigs == null) return finish(id, source, json, overrides);
        JsonObject defaults = VehicleConfig.defaults(JSONParser.parse(resolved.deepCopy(), JSONVehicle.class, new ArrayList<>()));
        JsonObject config = remoteConfigs != null ? remoteConfigs.getOrDefault(id, defaults) : VehicleConfig.local(configFolder, id, defaults);
        try {
            JSONVehicle vehicle = finish(id, source, JSONParser.merge(json.deepCopy(), config), overrides);
            CONFIGS.put(id, config);
            return vehicle;
        } catch (RuntimeException e) {
            SlimCore.LOGGER.error("Vehicle config of {} does not fit ({}); the pack's values are used", id, e.getMessage());
            CONFIGS.put(id, defaults);
            return finish(id, source, json, overrides);
        }
    }

    private static JSONVehicle finish(String id, Source source, JsonObject json, Path overrides) {
        String namespace = id.substring(0, id.indexOf(':'));
        String name = id.substring(id.indexOf(':') + 1);
        if (overrides != null) {
            Path override = overrides.resolve(namespace).resolve(name + ".json");
            if (Files.isRegularFile(override)) {
                try {
                    JSONParser.merge(json, JSONParser.read(override));
                    SlimCore.LOGGER.info("Applied config override {}", override);
                } catch (IOException | RuntimeException e) {
                    SlimCore.LOGGER.error("Unreadable override {}: {}", override, e.getMessage());
                }
            }
        }
        JSONParser.applyWheelTemplate(json);
        List<String> unknown = new ArrayList<>();
        JSONVehicle vehicle = JSONParser.parse(json, JSONVehicle.class, unknown);
        if (!unknown.isEmpty()) SlimCore.LOGGER.warn("Vehicle {} has unknown keys (ignored): {}", id, unknown);
        vehicle.id = id;
        vehicle.namespace = namespace;
        vehicle.name = name;
        vehicle.packName = source.name();
        vehicle.bake();
        checkAnimations(vehicle);
        return vehicle;
    }

    private static void checkAnimations(JSONVehicle vehicle) {
        if (vehicle.animations.isEmpty()) return;
        AnimationFile file = AnimationFile.load(vehicle.animationLocation);
        for (AJSONItem.Animation animation : vehicle.animations) {
            if (file.clip(animation.name) == null) throw new IllegalArgumentException("animation \"" + animation.name + "\" is not in " + vehicle.animationLocation);
        }
    }

    public static synchronized Collection<JSONVehicle> vehicles() {
        requireLoaded();
        return Collections.unmodifiableCollection(VEHICLES.values());
    }

    public static synchronized JSONVehicle vehicle(String id) {
        return findVehicle(id).orElse(defaultVehicle());
    }

    public static synchronized Optional<JSONVehicle> findVehicle(String id) {
        requireLoaded();
        if (id == null) return Optional.empty();
        JSONVehicle exact = VEHICLES.get(id);
        return exact != null ? Optional.of(exact) : Optional.ofNullable(BY_NAME.get(id));
    }

    public static synchronized JSONVehicle defaultVehicle() {
        requireLoaded();
        return defaultVehicle;
    }

    public static synchronized List<Pack> packs() {
        return List.copyOf(PACKS);
    }

    public static synchronized Optional<Path> findAsset(ResourceId location) {
        for (Source source : SOURCES) {
            for (Path root : source.roots()) {
                Path file = root.resolve("assets").resolve(location.namespace()).resolve(location.path());
                if (Files.isRegularFile(file)) return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    public static InputStream openAsset(ResourceId location) throws IOException {
        Path file = findAsset(location).orElseThrow(() -> new FileNotFoundException("No pack has " + location));
        return Files.newInputStream(file);
    }

    private static void requireLoaded() {
        if (defaultVehicle == null) throw new IllegalStateException("Vehicle packs are not loaded yet");
    }
}
