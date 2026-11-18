package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import studio.merami.slimcore.SlimCore;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

final class LampGlow {
    private static final int LIT = 90;
    private static final float DOMINANCE = 1.6f;
    private static final int WHITE = 215;
    private static final Map<ResourceLocation, ResourceLocation> MASKS = new HashMap<>();
    private static final Map<String, ResourceLocation> LENSES = new HashMap<>();

    private LampGlow() {}

    static ResourceLocation mask(ResourceLocation texture) {
        if (!MASKS.containsKey(texture)) MASKS.put(texture, build(texture));
        return MASKS.get(texture);
    }

    static ResourceLocation lens(ResourceLocation texture, boolean white) {
        String key = texture + (white ? "@white" : "@red");
        if (!LENSES.containsKey(key)) LENSES.put(key, buildLens(texture, white));
        return LENSES.get(key);
    }

    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation mask : MASKS.values()) {
            if (mask != null) mc.getTextureManager().release(mask);
        }
        for (ResourceLocation mask : LENSES.values()) {
            if (mask != null) mc.getTextureManager().release(mask);
        }
        MASKS.clear();
        LENSES.clear();
    }

    static boolean lamp(int red, int green, int blue, int alpha) {
        return alpha >= 128 && red > LIT && red > DOMINANCE * green && red > DOMINANCE * blue;
    }

    static boolean white(int red, int green, int blue, int alpha) {
        return alpha >= 128 && red > WHITE && green > WHITE && blue > WHITE;
    }

    private static ResourceLocation buildLens(ResourceLocation texture, boolean white) {
        Minecraft mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().open(texture); NativeImage source = NativeImage.read(in)) {
            NativeImage lens = new NativeImage(source.getWidth(), source.getHeight(), true);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getPixelRGBA(x, y);
                    int red = abgr & 255;
                    int green = abgr >> 8 & 255;
                    int blue = abgr >> 16 & 255;
                    int alpha = abgr >>> 24;
                    boolean lit = white ? white(red, green, blue, alpha) : lamp(red, green, blue, alpha);
                    lens.setPixelRGBA(x, y, lit ? 0xffffffff : 0);
                }
            }
            ResourceLocation mask = new ResourceLocation(texture.getNamespace(), texture.getPath() + (white ? "_lens_white" : "_lens_red"));
            mc.getTextureManager().register(mask, new DynamicTexture(lens));
            return mask;
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Cannot build the lamp lenses of {}: {}", texture, e.getMessage());
            return null;
        }
    }

    private static ResourceLocation build(ResourceLocation texture) {
        Minecraft mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().open(texture); NativeImage source = NativeImage.read(in)) {
            NativeImage glow = new NativeImage(source.getWidth(), source.getHeight(), true);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getPixelRGBA(x, y);
                    int red = abgr & 255;
                    int green = abgr >> 8 & 255;
                    int blue = abgr >> 16 & 255;
                    if (!lamp(red, green, blue, abgr >>> 24)) {
                        glow.setPixelRGBA(x, y, 0);
                        continue;
                    }
                    float gain = 255f / red;
                    glow.setPixelRGBA(x, y, 0xff000000 | Math.round(blue * gain) << 16 | Math.round(green * gain) << 8 | 255);
                }
            }
            ResourceLocation mask = new ResourceLocation(texture.getNamespace(), texture.getPath() + "_glow");
            mc.getTextureManager().register(mask, new DynamicTexture(glow));
            return mask;
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Cannot build the lamp glow of {}: {}", texture, e.getMessage());
            return null;
        }
    }
}
