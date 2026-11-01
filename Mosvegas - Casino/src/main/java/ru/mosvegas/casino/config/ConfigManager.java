package ru.mosvegas.casino.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.mosvegas.casino.MosVegasCasino;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static CasinoConfig config = new CasinoConfig();
    private static int revision;

    private ConfigManager() {}

    public static CasinoConfig get() { return config; }

    /** Increments on every (re)load so live tables pick up new rules between rounds. */
    public static int revision() { return revision; }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("mosvegas-casino.json"); }

    /** Loads the config; a broken file keeps the previous config and is not overwritten. */
    public static boolean load() {
        Path file = path();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    CasinoConfig loaded = GSON.fromJson(reader, CasinoConfig.class);
                    if (loaded == null) loaded = new CasinoConfig();
                    loaded.normalize();
                    config = loaded;
                }
            } else {
                config = new CasinoConfig();
                config.normalize();
            }
            revision++;
            save();
            return true;
        } catch (Exception e) {
            MosVegasCasino.LOGGER.error("Не удалось прочитать {}: {}", file, e.toString());
            return false;
        }
    }

    public static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            MosVegasCasino.LOGGER.error("Не удалось сохранить {}", file, e);
        }
    }

    public static String phrase(String key, String... args) {
        String template = config.phrases.get(key);
        if (template == null) template = Phrases.defaults().getOrDefault(key, key);
        return Phrases.format(template, args);
    }
}
