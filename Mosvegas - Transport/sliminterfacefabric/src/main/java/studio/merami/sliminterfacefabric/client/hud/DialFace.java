package studio.merami.sliminterfacefabric.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import studio.merami.slimcore.SlimCore;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

final class DialFace {
    static final int SIZE = 128;
    static final float CENTRE = 63.5f;
    static final float[] MAJOR = {-137.6f, -99.4f, -60.3f, -27f, 27f, 60.3f, 99.4f, 137.6f};
    private static final float LABEL_RADIUS = 41;
    private static final float FACE_RADIUS = 62.5f;
    private static final float TICK_RADIUS = 44;
    private static final int FACE = 0xf0343b44;
    private static final int LABEL = 0xffffffff;
    private static final int RED = 0xffe23b3b;
    private static final int DARK_RED = 0xff7a1d1d;
    private static final ResourceLocation RING = SlimVehicle.id("textures/gui/dial.png");
    private static final ResourceLocation DIGITS = SlimVehicle.id("textures/gui/dial_digits.png");
    private static final Map<String, ResourceLocation> FACES = new HashMap<>();

    private DialFace() {}

    static float angle(float value, float step) {
        float at = Mth.clamp(value / step, 0, MAJOR.length - 1 + .06f);
        int i = Math.min((int) at, MAJOR.length - 2);
        return Mth.lerp(at - i, MAJOR[i], MAJOR[i + 1]);
    }

    static ResourceLocation face(int step, float redFrom) {
        String key = step + "_" + Math.round(redFrom * 100);
        if (!FACES.containsKey(key)) FACES.put(key, build(key, step, redFrom));
        return FACES.get(key);
    }

    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation face : FACES.values()) {
            if (face != null) mc.getTextureManager().release(face);
        }
        FACES.clear();
    }

    private static ResourceLocation build(String key, int step, float redFrom) {
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage ring = read(mc, RING); NativeImage digits = read(mc, DIGITS)) {
            NativeImage face = new NativeImage(SIZE, SIZE, true);
            float red = redFrom < 0 ? Float.MAX_VALUE : angle(redFrom, step);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float dx = x - CENTRE;
                    float dy = y - CENTRE;
                    float r = (float) Math.hypot(dx, dy);
                    float a = (float) Math.toDegrees(Math.atan2(dx, -dy));
                    int pixel = ring.getPixelRGBA(x, y);
                    int color;
                    if ((pixel >>> 24) != 0) {
                        boolean white = (pixel & 0xffffff) == 0xffffff;
                        color = r > TICK_RADIUS && r < FACE_RADIUS && a >= red - 1 ? (white ? RED : DARK_RED) : argb(pixel);
                    } else if (r < FACE_RADIUS) {
                        color = a >= red && r >= 59.5f && r < 61.5f ? RED : FACE;
                    } else {
                        color = 0;
                    }
                    face.setPixelRGBA(x, y, abgr(color));
                }
            }
            for (int i = 0; i < MAJOR.length; i++) {
                int value = i * step;
                label(face, digits, Integer.toString(value), MAJOR[i], redFrom >= 0 && value >= redFrom ? RED : LABEL);
            }
            ResourceLocation id = SlimVehicle.id("dial_" + key);
            mc.getTextureManager().register(id, new DynamicTexture(face));
            return id;
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Cannot build the dial face {}: {}", key, e.getMessage());
            return null;
        }
    }

    private static void label(NativeImage face, NativeImage digits, String text, float angle, int color) {
        int width = -1;
        for (char c : text.toCharArray()) width += glyphWidth(digits, c - '0') + 1;
        double a = Math.toRadians(angle);
        int left = Math.round((float) (CENTRE + Math.sin(a) * LABEL_RADIUS) - (width - 1) / 2f);
        int top = Math.round((float) (CENTRE - Math.cos(a) * LABEL_RADIUS) - 2.5f);
        for (char c : text.toCharArray()) {
            int digit = c - '0';
            int glyph = glyphWidth(digits, digit);
            for (int y = 0; y < digits.getHeight(); y++) {
                for (int x = 0; x < glyph; x++) {
                    if ((digits.getPixelRGBA(digit * 5 + x, y) >>> 24) != 0) face.setPixelRGBA(left + x, top + y, abgr(color));
                }
            }
            left += glyph + 1;
        }
    }

    private static int glyphWidth(NativeImage digits, int digit) {
        int width = 0;
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < digits.getHeight(); y++) {
                if ((digits.getPixelRGBA(digit * 5 + x, y) >>> 24) != 0) width = x + 1;
            }
        }
        return width;
    }

    private static NativeImage read(Minecraft mc, ResourceLocation texture) throws IOException {
        try (InputStream in = mc.getResourceManager().open(texture)) {
            return NativeImage.read(in);
        }
    }

    private static int argb(int abgr) {
        return abgr & 0xff00ff00 | (abgr & 0xff) << 16 | (abgr >> 16) & 0xff;
    }

    private static int abgr(int argb) {
        return argb & 0xff00ff00 | (argb & 0xff) << 16 | (argb >> 16) & 0xff;
    }
}
