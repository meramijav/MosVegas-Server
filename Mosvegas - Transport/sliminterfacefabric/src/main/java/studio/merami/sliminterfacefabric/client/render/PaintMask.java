package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import studio.merami.slimcore.SlimCore;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PaintMask {
    private static final Map<String, ResourceLocation> MASKS = new HashMap<>();

    private PaintMask() {}

    static ResourceLocation keep(ResourceLocation texture, double dark, double chroma) {
        if (dark <= 0 && chroma >= 1) return null;
        String key = texture + "@" + dark + "@" + chroma;
        if (!MASKS.containsKey(key)) MASKS.put(key, build(texture, dark, chroma));
        return MASKS.get(key);
    }

    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation mask : MASKS.values()) {
            if (mask != null) mc.getTextureManager().release(mask);
        }
        MASKS.clear();
    }

    private static ResourceLocation build(ResourceLocation texture, double dark, double chroma) {
        Minecraft mc = Minecraft.getInstance();
        float limit = (float) (dark * 255);
        float colourful = (float) (chroma * 255);
        try (InputStream in = mc.getResourceManager().open(texture); NativeImage source = NativeImage.read(in)) {
            NativeImage kept = new NativeImage(source.getWidth(), source.getHeight(), true);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getPixelRGBA(x, y);
                    int red = abgr & 255;
                    int green = abgr >> 8 & 255;
                    int blue = abgr >> 16 & 255;
                    float luminance = .3f * red + .59f * green + .11f * blue;
                    int spread = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
                    boolean keep = luminance < limit || spread > colourful;
                    kept.setPixelRGBA(x, y, abgr >>> 24 >= 128 && keep ? abgr | 0xff000000 : 0);
                }
            }
            ResourceLocation mask = new ResourceLocation(texture.getNamespace(), texture.getPath() + String.format(Locale.ROOT, "_keep_%03d_%03d", Math.round(dark * 100), Math.round(chroma * 100)));
            mc.getTextureManager().register(mask, new DynamicTexture(kept));
            return mask;
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Cannot build the unpainted texels of {}: {}", texture, e.getMessage());
            return null;
        }
    }
}
