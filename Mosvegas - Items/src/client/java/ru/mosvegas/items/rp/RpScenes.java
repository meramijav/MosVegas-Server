package ru.mosvegas.items.rp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import ru.mosvegas.items.MosVegasItems;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class RpScenes implements SimpleSynchronousResourceReloadListener {
    public record Scene(String item, RpGeo geo, Identifier texture, Map<String, RpAnim> animations) {
        public RpAnim animation(String name) {
            return name == null ? null : animations.get(name);
        }

        public RpAnim idle() {
            for (var entry : animations.entrySet()) if (entry.getKey().endsWith(".idle")) return entry.getValue();
            return null;
        }
    }

    private static final Map<String, Scene> SCENES = new HashMap<>();

    public static Scene get(String item) {
        return SCENES.get(item);
    }

    @Override
    public Identifier getFabricId() {
        return new Identifier(MosVegasItems.MOD_ID, "rp_scenes");
    }

    @Override
    public void reload(ResourceManager manager) {
        SCENES.clear();
        JsonObject manifest = read(manager, new Identifier(MosVegasItems.MOD_ID, "rp/manifest.json"));
        if (manifest == null) return;
        Map<String, RpGeo> geos = new HashMap<>();
        Map<String, Map<String, RpAnim>> anims = new HashMap<>();
        for (var entry : manifest.entrySet()) {
            JsonObject item = entry.getValue().getAsJsonObject();
            String scene = item.get("scene").getAsString();
            try {
                RpGeo geo = geos.computeIfAbsent(scene, s -> {
                    JsonObject json = read(manager, new Identifier(MosVegasItems.MOD_ID, "rp/scene/" + s + ".geo.json"));
                    return json == null ? null : new RpGeo(json);
                });
                if (geo == null) continue;
                Map<String, RpAnim> animations = anims.computeIfAbsent(scene, s -> {
                    Map<String, RpAnim> map = new HashMap<>();
                    JsonObject json = read(manager, new Identifier(MosVegasItems.MOD_ID, "rp/anim/" + s + ".animation.json"));
                    if (json != null) for (RpAnim anim : RpAnim.parseFile(json)) map.put(anim.name, anim);
                    return map;
                });
                SCENES.put(entry.getKey(), new Scene(entry.getKey(), geo,
                        new Identifier(MosVegasItems.MOD_ID, "textures/rp/" + item.get("texture").getAsString() + ".png"),
                        animations));
            } catch (RuntimeException exception) {
                MosVegasItems.LOGGER.error("RP item {}: unreadable scene {}", entry.getKey(), scene, exception);
            }
        }
        MosVegasItems.LOGGER.info("Loaded {} RP item scenes", SCENES.size());
    }

    private static JsonObject read(ResourceManager manager, Identifier id) {
        var resource = manager.getResource(id);
        if (resource.isEmpty()) {
            MosVegasItems.LOGGER.warn("Missing RP resource {}", id);
            return null;
        }
        try (var reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            MosVegasItems.LOGGER.error("Unreadable RP resource {}", id, exception);
            return null;
        }
    }
}
