package ru.mosvegas.character.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import ru.mosvegas.character.MosVegasCharacter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class CharacterOptions {
    public static final int GENDERS = 2;
    public static final int HEIGHTS = 5;
    public static final int BUILDS = 4;
    public static final int SKIN_TONES = 8;
    public static final int EYE_COLORS = 8;
    public static final int HAIR_COLORS = 12;
    public static final int HAIRS_PER_GENDER = 10;
    public static final int BEARDS = 11;

    private static final Pattern OUTFIT_ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static List<String> outfits = List.of("casual", "street", "classic");
    private static boolean requireClientMod = true;

    private CharacterOptions() {}

    public static List<String> outfits() {
        return outfits;
    }

    public static boolean requireClientMod() {
        return requireClientMod;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("mosvegas_character.json");
        try {
            if (!Files.exists(file)) {
                write(file);
                return;
            }
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            List<String> list = new ArrayList<>();
            if (root.has("outfits")) {
                root.getAsJsonArray("outfits").forEach(e -> {
                    String id = e.getAsString();
                    if (OUTFIT_ID.matcher(id).matches() && !list.contains(id)) list.add(id);
                    else MosVegasCharacter.LOGGER.warn("Skipped outfit id '{}'", id);
                });
            }
            outfits = List.copyOf(list);
            if (root.has("requireClientMod")) requireClientMod = root.get("requireClientMod").getAsBoolean();
        } catch (IOException | RuntimeException e) {
            MosVegasCharacter.LOGGER.error("Failed to read {}, using defaults", file, e);
        }
    }

    private static void write(Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.add("outfits", GSON.toJsonTree(outfits));
        root.addProperty("requireClientMod", requireClientMod);
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
