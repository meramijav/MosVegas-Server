package ru.mosvegas.character.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.MosVegasCharacter;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.data.CharacterPalette;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class HairModels {
    public static final String BODY_BONE = "hair_body";
    private static final Map<String, Optional<GeoMesh>> MESHES = new HashMap<>();
    private static final Map<String, Optional<GeoMesh>> BODY_MESHES = new HashMap<>();
    private static final Map<String, Optional<NativeImage>> MASKS = new HashMap<>();
    private static final Map<String, Identifier> TINTED = new HashMap<>();

    private HairModels() {}

    public static String beardId(Appearance a) {
        return a.beard > 0 && a.gender != Appearance.FEMALE ? String.format("beard_%02d", a.beard) : null;
    }

    private static String folder(String id) {
        return id.startsWith("beard_") ? "beard/" : "hair/";
    }

    public static String hairId(Appearance a) {
        return String.format("%s_%02d", a.gender == Appearance.FEMALE ? "female" : "male", a.hair + 1);
    }

    public static GeoMesh mesh(String id) {
        load(id);
        return MESHES.get(id).orElse(null);
    }

    public static GeoMesh bodyMesh(String id) {
        load(id);
        return BODY_MESHES.get(id).orElse(null);
    }

    private static void load(String id) {
        if (MESHES.containsKey(id)) return;
        GeoMesh head = null, body = null;
        Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(MosVegasCharacter.id("geo/" + folder(id) + id + ".geo.json"));
        if (res.isPresent()) {
            try (var reader = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                head = GeoMesh.load(root, name -> !name.equals(BODY_BONE));
                body = GeoMesh.load(root, name -> name.equals(BODY_BONE));
            } catch (Exception e) {
                MosVegasCharacter.LOGGER.error("Failed to load hair model {}", id, e);
            }
        }
        MESHES.put(id, Optional.ofNullable(head));
        BODY_MESHES.put(id, Optional.ofNullable(body));
    }

    public static Identifier texture(String id, int color) {
        if (!RenderSystem.isOnRenderThread()) return null;
        String key = id + "_" + color;
        Identifier done = TINTED.get(key);
        if (done != null) return done;
        NativeImage mask = MASKS.computeIfAbsent(id, k -> {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(MosVegasCharacter.id("textures/" + folder(k) + k + ".png"));
            if (res.isEmpty()) return Optional.empty();
            try (InputStream in = res.get().getInputStream()) {
                return Optional.of(NativeImage.read(in));
            } catch (Exception e) {
                MosVegasCharacter.LOGGER.error("Failed to read hair texture {}", k, e);
                return Optional.empty();
            }
        }).orElse(null);
        if (mask == null) return null;
        int rgb = CharacterPalette.HAIR[Math.floorMod(color, CharacterPalette.HAIR.length)];
        int tr = rgb >> 16 & 255, tg = rgb >> 8 & 255, tb = rgb & 255;
        NativeImage out = new NativeImage(mask.getWidth(), mask.getHeight(), true);
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                int c = mask.getColor(x, y);
                int a = c >>> 24;
                if (a == 0) {
                    out.setColor(x, y, 0);
                    continue;
                }
                int l = c & 255;
                int r = Math.min(255, tr * l / 200), g = Math.min(255, tg * l / 200), b = Math.min(255, tb * l / 200);
                out.setColor(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        Identifier id2 = MosVegasCharacter.id("dynamic/hair_" + key);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id2, new NativeImageBackedTexture(out));
        TINTED.put(key, id2);
        return id2;
    }

    public static void reload() {
        MESHES.clear();
        BODY_MESHES.clear();
        MASKS.values().forEach(o -> o.ifPresent(NativeImage::close));
        MASKS.clear();
        TINTED.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        TINTED.clear();
    }
}
