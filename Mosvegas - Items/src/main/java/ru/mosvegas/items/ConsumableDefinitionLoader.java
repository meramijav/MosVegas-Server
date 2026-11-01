package ru.mosvegas.items;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class ConsumableDefinitionLoader {
    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve(MosVegasItems.MOD_ID);
    private static final String ASSETS = "assets/" + MosVegasItems.MOD_ID;
    private static final String BUILTIN = "data/" + MosVegasItems.MOD_ID + "/consumables";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> ASSET_FOLDERS = List.of("geo", "textures/item", "animations", "sounds", "models/item", "lang");
    /** Version 1.0.x marked its first run with "1" after copying these four examples. */
    private static final Set<String> FIRST_BUILTINS = Set.of("beer.json", "cigarette.json", "energy_drink.json", "snack.json");
    /** Creative tab order: {@code order}, then ID. */
    public static final Comparator<ConsumableDefinition> ORDER =
            Comparator.comparingInt(ConsumableDefinition::order).thenComparing(ConsumableDefinition::id);

    private ConsumableDefinitionLoader() {}

    public static List<ConsumableDefinition> load() {
        Path directory = ROOT.resolve("consumables");
        initialize(directory);

        Map<String, ConsumableDefinition> definitions = new HashMap<>();
        List<String> errors = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(ConsumableDefinitionLoader::isJsonFile).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) throw new IllegalArgumentException("Item configuration must be an object");
                    JsonObject json = parsed.getAsJsonObject();
                    if (!ConsumableDefinition.enabled(json)) continue;
                    ConsumableDefinition definition = ConsumableDefinition.fromJson(json);
                    if (definitions.putIfAbsent(definition.id(), definition) != null) {
                        throw new IllegalArgumentException("Duplicate id: " + definition.id());
                    }
                } catch (IOException | RuntimeException exception) {
                    errors.add(path + ": " + exception.getMessage());
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            errors.add(exception.toString());
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid MosVegas item configuration:\n" + String.join("\n", errors));
        }
        return definitions.values().stream().sorted(ORDER).toList();
    }

    private static void initialize(Path directory) {
        ModContainer mod = FabricLoader.getInstance().getModContainer(MosVegasItems.MOD_ID).orElseThrow();
        try {
            Files.createDirectories(directory);
            seedBuiltins(mod, directory);
            Path resources = ROOT.resolve("resources");
            for (String folder : ASSET_FOLDERS) Files.createDirectories(resources.resolve(ASSETS).resolve(folder));
            writePackMeta(resources, "MosVegas: custom assets");
        } catch (IOException | UncheckedIOException exception) {
            throw new IllegalStateException("Cannot initialize MosVegas configuration: " + ROOT, exception);
        }
        Path guide = ROOT.resolve("README.md");
        if (Files.notExists(guide)) {
            mod.findPath("mosvegas-guide.md").ifPresent(path -> {
                try {
                    Files.copy(path, guide);
                } catch (IOException exception) {
                    MosVegasItems.LOGGER.warn("Cannot export guide", exception);
                }
            });
        }
    }

    private static void seedBuiltins(ModContainer mod, Path directory) throws IOException {
        // Each bundled file is copied once and listed in the marker; deleting a copy afterwards
        // intentionally removes the item. Files added by a later mod version are copied on its first start.
        Path marker = ROOT.resolve(".initialized");
        Set<String> seeded = new TreeSet<>();
        if (Files.exists(marker)) {
            for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.equals("1")) seeded.addAll(FIRST_BUILTINS);
                else if (!line.isEmpty()) seeded.add(line);
            }
        }
        Path builtins = mod.findPath(BUILTIN).orElseThrow();
        List<Path> pending = new ArrayList<>();
        try (Stream<Path> files = Files.walk(builtins)) {
            for (Path file : files.filter(ConsumableDefinitionLoader::isJsonFile).sorted().toList()) {
                if (!seeded.contains(relative(builtins, file))) pending.add(file);
            }
        }
        if (pending.isEmpty() && Files.exists(marker)) return;
        // A new bundled item never replaces an item the owner already made with the same ID.
        Set<String> existingIds = configuredIds(directory);
        for (Path file : pending) {
            String name = relative(builtins, file);
            seeded.add(name);
            Path target = directory.resolve(name);
            if (Files.exists(target)) continue;
            String id = idOf(file);
            if (existingIds.contains(id)) {
                MosVegasItems.LOGGER.warn("Bundled item {} not added: the configuration already defines this ID", id);
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.copy(file, target);
        }
        Files.writeString(marker, String.join("\n", seeded) + "\n", StandardCharsets.UTF_8);
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static Set<String> configuredIds(Path directory) throws IOException {
        Set<String> ids = new HashSet<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(ConsumableDefinitionLoader::isJsonFile).toList()) {
                String id = idOf(path);
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return ids;
    }

    /** The "id" of a configuration file; empty when it cannot be read, so load() reports the file itself. */
    private static String idOf(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            JsonElement id = parsed.isJsonObject() ? parsed.getAsJsonObject().get("id") : null;
            if (id != null && id.isJsonPrimitive()) return id.getAsString();
        } catch (IOException | RuntimeException ignored) {
            // Reported with its path when the configuration is loaded.
        }
        return "";
    }

    private static boolean isJsonFile(Path path) {
        return path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path);
    }

    public static void writePackMeta(Path root, String description) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 22);
        pack.addProperty("description", description);
        JsonObject metadata = new JsonObject();
        metadata.add("pack", pack);
        writeIfChanged(root.resolve("pack.mcmeta"), GSON.toJson(metadata));
    }

    public static void generateItemModels(List<ConsumableDefinition> definitions) {
        Path root = ROOT.resolve("generated");
        String models = ASSETS + "/models/item/";
        try {
            writePackMeta(root, "MosVegas: automatic item models");
            for (ConsumableDefinition definition : definitions) {
                boolean flat = definition.model().isEmpty();
                JsonObject model = new JsonObject();
                model.addProperty("parent", flat ? "minecraft:item/generated"
                        : MosVegasItems.MOD_ID + ":item/consumable" + (definition.useAction().equals("smoke") ? "_smoke" : ""));
                JsonObject textures = new JsonObject();
                String texture = definition.texture().replace(":textures/", ":");
                if (texture.endsWith(".png")) texture = texture.substring(0, texture.length() - 4);
                textures.addProperty(flat ? "layer0" : "particle", texture);
                model.add("textures", textures);
                writeIfChanged(root.resolve(models + definition.id() + ".json"), GSON.toJson(model));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create item models", exception);
        }
    }

    private static void writeIfChanged(Path target, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Files.exists(target) && Files.size(target) == bytes.length && Arrays.equals(Files.readAllBytes(target), bytes)) return;
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
