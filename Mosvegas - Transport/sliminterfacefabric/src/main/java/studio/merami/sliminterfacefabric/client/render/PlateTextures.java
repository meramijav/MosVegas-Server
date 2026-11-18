package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.plates.PlateNumber;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PlateTextures {
    public static final String CHARACTERS = PlateNumber.LETTERS + PlateNumber.DIGITS;
    public static final int WIDTH = 1040;
    public static final int HEIGHT = 224;
    public static final int CELL_WIDTH = 104;
    public static final int CELL_HEIGHT = 152;
    private static final int GUTTER = 8;
    public static final int ATLAS_WIDTH = (CELL_WIDTH + GUTTER) * 8;
    public static final int ATLAS_HEIGHT = (CELL_HEIGHT + GUTTER) * 3;
    public static final int SLOT_Y = 36;
    public static final float REGION_SCALE = .7f;
    public static final int REGION_Y = 22;
    public static final ResourceLocation GLYPHS = SlimVehicle.id("textures/plates/glyphs.png");
    private static final int COLUMNS = 8;
    private static final int REGION_CENTER = 905;
    private static final int REGION_ADVANCE = 66;
    private static final int[] STANDARD_SLOTS = {54, 180, 288, 396, 524, 632};
    private static final int[] POLICE_SLOTS = {98, 240, 344, 448, 552};
    private static final int STANDARD_INK = 0x111111;
    private static final int POLICE_INK = 0xF4F6F8;
    private static final int MODEL_SHRINK = 2;
    private static final int CACHED = 32;
    private static final int MIP_LEVELS = 4;
    private static final float MIP_SHARPEN = -.75f;
    private static final Map<String, NativeImage> BASES = new HashMap<>();
    private static final LinkedHashMap<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(16, .75f, true);
    private static NativeImage glyphs;
    private static boolean broken;

    private static final class Mipmapped extends DynamicTexture {
        Mipmapped(NativeImage image) {
            super(image);
            bind();
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, MIP_LEVELS);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, MIP_LEVELS);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, MIP_SHARPEN);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            super.setFilter(true, true);
        }
    }

    private PlateTextures() {}

    public static ResourceLocation base(String style) {
        return SlimVehicle.id("textures/plates/" + style + ".png");
    }

    public static int[] slots(String style) {
        return PlateNumber.POLICE.equals(style) ? POLICE_SLOTS : STANDARD_SLOTS;
    }

    public static int ink(String style) {
        return PlateNumber.POLICE.equals(style) ? POLICE_INK : STANDARD_INK;
    }

    public static int glyphU(char character) {
        return CHARACTERS.indexOf(character) % COLUMNS * (CELL_WIDTH + GUTTER) + GUTTER / 2;
    }

    public static int glyphV(char character) {
        return CHARACTERS.indexOf(character) / COLUMNS * (CELL_HEIGHT + GUTTER) + GUTTER / 2;
    }

    public static int regionX(String region, int index) {
        return Math.round(REGION_CENTER - region.length() * REGION_ADVANCE / 2f + index * REGION_ADVANCE + (REGION_ADVANCE - CELL_WIDTH * REGION_SCALE) / 2);
    }

    public static ResourceLocation texture(String style, String number) {
        if (broken) return null;
        if (!PlateNumber.valid(number)) return base(style);
        String key = style + "/" + number;
        ResourceLocation found = TEXTURES.get(key);
        if (found != null) return found;
        NativeImage plate = compose(style, number);
        if (plate == null) return null;
        ResourceLocation location = SlimVehicle.id("plate/" + style + "/" + number.toLowerCase(Locale.ROOT));
        Minecraft.getInstance().getTextureManager().register(location, new Mipmapped(plate));
        TEXTURES.put(key, location);
        if (TEXTURES.size() > CACHED) {
            Iterator<Map.Entry<String, ResourceLocation>> oldest = TEXTURES.entrySet().iterator();
            Minecraft.getInstance().getTextureManager().release(oldest.next().getValue());
            oldest.remove();
        }
        return location;
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation texture : TEXTURES.values()) mc.getTextureManager().release(texture);
        TEXTURES.clear();
        BASES.values().forEach(NativeImage::close);
        BASES.clear();
        if (glyphs != null) glyphs.close();
        glyphs = null;
        broken = false;
    }

    private static NativeImage compose(String style, String number) {
        NativeImage plate = base(style, true);
        if (plate == null || glyphs == null) return null;
        NativeImage canvas = new NativeImage(WIDTH, HEIGHT, true);
        canvas.copyFrom(plate);
        String body = PlateNumber.body(number);
        String region = PlateNumber.region(number);
        int ink = abgr(ink(style));
        for (int i = 0; i < body.length(); i++) stamp(canvas, body.charAt(i), slots(style)[i], SLOT_Y, 1, ink);
        for (int i = 0; i < region.length(); i++) stamp(canvas, region.charAt(i), regionX(region, i), REGION_Y, REGION_SCALE, ink);
        NativeImage small = shrink(canvas);
        canvas.close();
        return small;
    }

    private static NativeImage base(String style, boolean withGlyphs) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (withGlyphs && glyphs == null) {
                try (InputStream stream = mc.getResourceManager().open(GLYPHS)) {
                    glyphs = NativeImage.read(stream);
                }
            }
            NativeImage found = BASES.get(style);
            if (found != null) return found;
            try (InputStream stream = mc.getResourceManager().open(base(style))) {
                NativeImage read = NativeImage.read(stream);
                BASES.put(style, read);
                return read;
            }
        } catch (IOException | RuntimeException e) {
            broken = true;
            SlimCore.LOGGER.error("Cannot load the {} number plate: {}", style, e.getMessage());
            return null;
        }
    }

    private static void stamp(NativeImage canvas, char character, int x, int y, float scale, int ink) {
        int u = glyphU(character);
        int v = glyphV(character);
        int width = Math.round(CELL_WIDTH * scale);
        int height = Math.round(CELL_HEIGHT * scale);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int alpha = glyphs.getPixelRGBA(u + (int) (px / scale), v + (int) (py / scale)) >>> 24;
                if (alpha == 0) continue;
                int cx = x + px;
                int cy = y + py;
                if (cx >= 0 && cy >= 0 && cx < WIDTH && cy < HEIGHT) canvas.setPixelRGBA(cx, cy, mix(canvas.getPixelRGBA(cx, cy), ink, alpha));
            }
        }
    }

    private static int mix(int below, int ink, int alpha) {
        int result = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int channel = ((below >> shift & 255) * (255 - alpha) + (ink >> shift & 255) * alpha) / 255;
            result |= channel << shift;
        }
        return result;
    }

    private static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 255) << 16 | rgb & 0xFF00 | rgb >> 16 & 255;
    }

    private static NativeImage shrink(NativeImage image) {
        int width = WIDTH / MODEL_SHRINK;
        int height = HEIGHT / MODEL_SHRINK;
        NativeImage small = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] sum = new int[4];
                for (int sy = 0; sy < MODEL_SHRINK; sy++) {
                    for (int sx = 0; sx < MODEL_SHRINK; sx++) {
                        int pixel = image.getPixelRGBA(x * MODEL_SHRINK + sx, y * MODEL_SHRINK + sy);
                        for (int c = 0; c < 4; c++) sum[c] += pixel >>> (c * 8) & 255;
                    }
                }
                int out = 0;
                for (int c = 0; c < 4; c++) out |= sum[c] / (MODEL_SHRINK * MODEL_SHRINK) << (c * 8);
                small.setPixelRGBA(x, y, out);
            }
        }
        return small;
    }
}
