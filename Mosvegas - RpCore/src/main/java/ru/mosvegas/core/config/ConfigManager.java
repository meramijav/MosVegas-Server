package ru.mosvegas.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.mosvegas.core.MosvegasCore;
import ru.mosvegas.core.data.JsonFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mosvegas-core.json");
    private static MosvegasConfig config;
    private static Map<String, MosvegasConfig.Faction> factionsById = Map.of();
    private static Map<String, MosvegasConfig.Quest> questsById = Map.of();

    private ConfigManager() {}

    public static MosvegasConfig get() {
        if (config == null) load();
        return config;
    }

    public static void load() {
        MosvegasConfig previous = config;
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                config = MosvegasConfig.defaults();
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, MosvegasConfig.class);
            }
            if (config == null) config = MosvegasConfig.defaults();
            boolean migrated = migrateConfig();
            migrated |= migrateProperties();
            rebuildIndexes();
            if (migrated) save();
        } catch (Exception e) {
            MosvegasCore.LOGGER.error("Не удалось загрузить конфиг Mosvegas Core", e);
            // A failed reload keeps the objects already in memory; the next save must not replace them with defaults.
            if (previous != null) {
                config = previous;
                MosvegasCore.LOGGER.warn("Оставлен предыдущий конфиг Mosvegas Core. Исправьте {} и повторите /mosvegas reload.", FILE);
            } else {
                preserveBroken();
                config = MosvegasConfig.defaults();
            }
            rebuildIndexes();
        }
    }

    /** Startup falls back to defaults, which a later save writes over the file: keep the unreadable original next to it. */
    private static void preserveBroken() {
        if (Files.notExists(FILE)) return;
        Path copy = FILE.resolveSibling(FILE.getFileName() + ".broken");
        try {
            Files.copy(FILE, copy, StandardCopyOption.REPLACE_EXISTING);
            MosvegasCore.LOGGER.warn("Нечитаемый конфиг сохранён в {}", copy);
        } catch (IOException e) {
            MosvegasCore.LOGGER.error("Не удалось сохранить копию нечитаемого конфига", e);
        }
    }

    public static void save() {
        try {
            rebuildIndexes();
            JsonFile.write(FILE, get());
        } catch (Exception e) {
            MosvegasCore.LOGGER.error("Не удалось сохранить конфиг Mosvegas Core", e);
        }
    }

    public static Path path() { return FILE; }

    public static MosvegasConfig.Faction faction(String id) { return factionsById.get(id); }
    public static MosvegasConfig.Quest quest(String id) { return questsById.get(id); }

    private static void rebuildIndexes() {
        if (config == null) return;
        Map<String, MosvegasConfig.Faction> factions = new HashMap<>();
        if (config.factions != null) for (MosvegasConfig.Faction faction : config.factions) factions.put(faction.id, faction);
        Map<String, MosvegasConfig.Quest> quests = new HashMap<>();
        if (config.quests != null) for (MosvegasConfig.Quest quest : config.quests) quests.put(quest.id, quest);
        factionsById = Map.copyOf(factions);
        questsById = Map.copyOf(quests);
    }

    /** Older objects stored one min/max box; it becomes the first part of a multi-part boundary. */
    private static boolean migrateProperties() {
        if (config.properties == null) { config.properties = new ArrayList<>(); return true; }
        boolean changed = false;
        for (MosvegasConfig.Property property : config.properties) {
            if (property.areas == null) { property.areas = new ArrayList<>(); changed = true; }
            if (property.min != null && property.max != null && property.areas.isEmpty()) {
                property.areas.add(new MosvegasConfig.Area((int)Math.floor(property.min.x), (int)Math.floor(property.min.y), (int)Math.floor(property.min.z),
                        (int)Math.floor(property.max.x), (int)Math.floor(property.max.y), (int)Math.floor(property.max.z)));
            }
            if (property.min != null || property.max != null) { property.min = null; property.max = null; changed = true; }
        }
        return changed;
    }

    private static boolean migrateConfig() {
        if (config.schemaVersion >= 4) return false;
        MosvegasConfig defaults = MosvegasConfig.defaults();
        if (config.quests == null) config.quests = new ArrayList<>();
        Set<String> existing = new HashSet<>();
        for (MosvegasConfig.Quest quest : config.quests) existing.add(quest.id);
        for (MosvegasConfig.Quest example : defaults.quests) {
            if (!existing.contains(example.id)) config.quests.add(example);
        }
        if (config.licencePrices != null) config.licencePrices.remove("id");
        config.cashDenominations = new ArrayList<>(List.of(5000L, 2000L, 1000L, 500L, 200L, 100L, 50L, 10L, 5L, 2L, 1L));
        if (config.bankTerminals == null) config.bankTerminals = new ArrayList<>();
        boolean starterExists = config.quests.stream().anyMatch(q -> "starter_city".equals(q.id));
        if (!starterExists) defaults.quests.stream().filter(q -> "starter_city".equals(q.id)).findFirst().ifPresent(config.quests::add);
        config.schemaVersion = 4;
        return true;
    }
}
