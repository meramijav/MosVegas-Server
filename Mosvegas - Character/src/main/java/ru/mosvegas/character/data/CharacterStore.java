package ru.mosvegas.character.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import ru.mosvegas.character.MosVegasCharacter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CharacterStore {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Appearance> characters = new HashMap<>();
    private Path file;

    public void load(MinecraftServer server) {
        characters.clear();
        file = server.getSavePath(WorldSavePath.ROOT).resolve("mosvegas_character").resolve("characters.json");
        Path source = Files.exists(file) ? file : file.resolveSibling("characters.json.bak");
        if (!Files.exists(source)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(source, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject list = root.getAsJsonObject("characters");
            for (Map.Entry<String, JsonElement> e : list.entrySet()) {
                try {
                    characters.put(UUID.fromString(e.getKey()), GSON.fromJson(e.getValue(), Appearance.class));
                } catch (RuntimeException bad) {
                    MosVegasCharacter.LOGGER.warn("Skipped broken character entry {}", e.getKey());
                }
            }
            MosVegasCharacter.LOGGER.info("Loaded {} characters", characters.size());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read " + source + ", fix or remove it", e);
        }
    }

    public Appearance get(UUID id) {
        return characters.get(id);
    }

    public boolean has(UUID id) {
        return characters.containsKey(id);
    }

    public void put(UUID id, Appearance appearance) {
        characters.put(id, appearance.copy());
        save();
    }

    public boolean remove(UUID id) {
        boolean removed = characters.remove(id) != null;
        if (removed) save();
        return removed;
    }

    public void save() {
        if (file == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject list = new JsonObject();
        characters.forEach((id, a) -> list.add(id.toString(), GSON.toJsonTree(a)));
        root.add("characters", list);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("characters.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            if (Files.exists(file)) Files.copy(file, file.resolveSibling("characters.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            MosVegasCharacter.LOGGER.error("Failed to save characters to {}", file, e);
        }
    }

    public void unload() {
        characters.clear();
        file = null;
    }
}
