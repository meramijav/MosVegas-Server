package ru.mosvegas.character.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.MosVegasCharacter;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TextCache {
    private static final int MAX = 600;
    private static final int PAD = 2;
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final Map<String, Font> BASE = new HashMap<>();
    private static final Map<String, Font> SIZED = new HashMap<>();
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(128, 0.75f, true);
    private static int serial;

    public record Entry(Identifier id, int w, int h, float baseline, float advance) {}

    private TextCache() {}

    private static Font base(boolean bold) {
        String name = bold ? "unbounded_bold.ttf" : "unbounded_medium.ttf";
        return BASE.computeIfAbsent(name, n -> {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(MosVegasCharacter.id("font/" + n));
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    return Font.createFont(Font.TRUETYPE_FONT, in);
                } catch (Exception e) {
                    MosVegasCharacter.LOGGER.error("Failed to load font {}", n, e);
                }
            }
            return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 12);
        });
    }

    private static Font font(boolean bold, float sizePx) {
        String key = (bold ? "b" : "m") + sizePx;
        return SIZED.computeIfAbsent(key, k -> base(bold).deriveFont(sizePx));
    }

    public static float advance(String s, boolean bold, float sizePx, float trackingPx) {
        if (s.isEmpty()) return 0;
        Font f = font(bold, sizePx);
        if (trackingPx == 0) return (float) f.getStringBounds(s, FRC).getWidth();
        float total = 0;
        for (int i = 0; i < s.length(); i++) total += (float) f.getStringBounds(String.valueOf(s.charAt(i)), FRC).getWidth();
        return total + trackingPx * (s.length() - 1);
    }

    public static LineMetrics metrics(boolean bold, float sizePx) {
        return font(bold, sizePx).getLineMetrics("Ag", FRC);
    }

    public static Entry get(String s, boolean bold, float sizePx, float trackingPx) {
        String key = (bold ? 'b' : 'm') + "|" + sizePx + "|" + trackingPx + "|" + s;
        Entry e = CACHE.get(key);
        if (e != null) return e;
        Font f = font(bold, sizePx);
        LineMetrics lm = f.getLineMetrics(s.isEmpty() ? "A" : s, FRC);
        float adv = advance(s, bold, sizePx, trackingPx);
        int w = Math.max(1, (int) Math.ceil(adv) + PAD * 2);
        int h = Math.max(1, (int) Math.ceil(lm.getAscent() + lm.getDescent()) + PAD * 2);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setFont(f);
        g.setColor(java.awt.Color.WHITE);
        float baseline = PAD + lm.getAscent();
        if (trackingPx == 0) {
            g.drawString(s, (float) PAD, baseline);
        } else {
            float at = PAD;
            for (int i = 0; i < s.length(); i++) {
                String ch = String.valueOf(s.charAt(i));
                g.drawString(ch, at, baseline);
                at += (float) f.getStringBounds(ch, FRC).getWidth() + trackingPx;
            }
        }
        g.dispose();
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = img.getRGB(x, y) >>> 24;
                ni.setColor(x, y, a << 24 | 0xFFFFFF);
            }
        }
        Identifier id = MosVegasCharacter.id("dynamic/text_" + (serial++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(ni));
        e = new Entry(id, w, h, baseline, adv);
        CACHE.put(key, e);
        if (CACHE.size() > MAX) {
            var it = CACHE.entrySet().iterator();
            Identifier old = it.next().getValue().id();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return e;
    }

    public static void clear() {
        CACHE.values().forEach(e -> MinecraftClient.getInstance().getTextureManager().destroyTexture(e.id()));
        CACHE.clear();
        SIZED.clear();
        BASE.clear();
    }
}
