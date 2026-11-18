package ru.mosvegas.character.client.skin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.MosVegasCharacter;
import ru.mosvegas.character.client.render.HairModels;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.data.CharacterPalette;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SkinComposer {
    private static final int MAX_CACHED = 256;
    private static final NativeImage MISSING = new NativeImage(64, 64, true);
    private static final Map<String, NativeImage> LAYERS = new HashMap<>();
    private static final LinkedHashMap<String, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75f, true);

    private SkinComposer() {}

    public static Identifier texture(Appearance a) {
        if (!RenderSystem.isOnRenderThread()) return null;
        String key = key(a);
        Identifier id = TEXTURES.get(key);
        if (id != null) return id;
        id = MosVegasCharacter.id("dynamic/skin_" + key);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(compose(a)));
        TEXTURES.put(key, id);
        if (TEXTURES.size() > MAX_CACHED) {
            var it = TEXTURES.entrySet().iterator();
            Identifier old = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return id;
    }

    public static void reload() {
        LAYERS.values().forEach(img -> {
            if (img != MISSING) img.close();
        });
        LAYERS.clear();
        TEXTURES.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        TEXTURES.clear();
    }

    private static String key(Appearance a) {
        return a.gender + "_" + a.skin + "_" + a.eyes + "_" + a.hair + "_" + a.hairColor + "_" + a.beard + "_" + a.outfit.replaceAll("[^a-z0-9_]", "");
    }

    public static NativeImage compose(Appearance a) {
        String g = a.gender == Appearance.FEMALE ? "female" : "male";
        NativeImage out = new NativeImage(64, 64, true);
        out.fillRect(0, 0, 64, 64, 0);
        tint(out, layer("body/" + g + "_skin"), CharacterPalette.SKIN[a.skin % CharacterPalette.SKIN.length]);
        blend(out, layer("body/" + g + "_fixed"));
        tint(out, layer("body/" + g + "_eyes"), CharacterPalette.EYES[a.eyes % CharacterPalette.EYES.length]);
        NativeImage scalp = layer("hair/" + HairModels.hairId(a) + "_scalp");
        if (scalp == MISSING) scalp = layer("body/" + g + "_hair");
        tint(out, scalp, CharacterPalette.HAIR[a.hairColor % CharacterPalette.HAIR.length]);
        if (a.beard > 0 && a.gender != Appearance.FEMALE)
            tint(out, layer(String.format("beard/beard_%02d_skin", a.beard)), CharacterPalette.HAIR[a.hairColor % CharacterPalette.HAIR.length]);
        if (!a.outfit.isEmpty()) {
            NativeImage outfit = a.gender == Appearance.FEMALE ? layer("outfit/" + a.outfit + "_slim") : MISSING;
            if (outfit == MISSING) outfit = layer("outfit/" + a.outfit);
            blend(out, outfit);
        }
        return out;
    }

    private static NativeImage layer(String path) {
        return LAYERS.computeIfAbsent(path, p -> {
            Identifier id = MosVegasCharacter.id("textures/" + p + ".png");
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return MISSING;
            try (InputStream in = res.get().getInputStream()) {
                return NativeImage.read(in);
            } catch (Exception e) {
                MosVegasCharacter.LOGGER.error("Failed to read skin layer {}", id, e);
                return MISSING;
            }
        });
    }

    private static void tint(NativeImage out, NativeImage layer, int rgb) {
        if (layer == MISSING) return;
        int tr = rgb >> 16 & 255, tg = rgb >> 8 & 255, tb = rgb & 255;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int c = layer.getColor(x, y);
                int a = c >>> 24;
                if (a == 0) continue;
                int l = c & 255;
                put(out, x, y, clamp(tr * l / 200), clamp(tg * l / 200), clamp(tb * l / 200), a);
            }
        }
    }

    private static void blend(NativeImage out, NativeImage layer) {
        if (layer == MISSING) return;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int c = layer.getColor(x, y);
                int a = c >>> 24;
                if (a == 0) continue;
                put(out, x, y, c & 255, c >> 8 & 255, c >> 16 & 255, a);
            }
        }
    }

    private static void put(NativeImage out, int x, int y, int r, int g, int b, int a) {
        if (a < 255) {
            int d = out.getColor(x, y);
            int da = d >>> 24;
            if (da > 0) {
                r = (r * a + (d & 255) * (255 - a)) / 255;
                g = (g * a + (d >> 8 & 255) * (255 - a)) / 255;
                b = (b * a + (d >> 16 & 255) * (255 - a)) / 255;
                a = Math.max(a, da);
            }
        }
        out.setColor(x, y, a << 24 | b << 16 | g << 8 | r);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
