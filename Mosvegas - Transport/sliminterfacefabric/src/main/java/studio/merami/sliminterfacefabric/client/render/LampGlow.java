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
    private static final Map<ResourceLocation, ResourceLocation> MASKS = new HashMap<>();

    private LampGlow() {}

    static ResourceLocation mask(ResourceLocation texture) {
        if (!MASKS.containsKey(texture)) MASKS.put(texture, build(texture));
        return MASKS.get(texture);
    }

    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation mask : MASKS.values()) {
            if (mask != null) mc.getTextureManager().release(mask);
        }
        MASKS.clear();
    }

    static boolean lamp(int red, int green, int blue, int alpha) {
        return alpha >= 128 && red > LIT && red > DOMINANCE * green && red > DOMINANCE * blue;
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
