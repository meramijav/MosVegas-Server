import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Generates the default (placeholder) assets of MosVegas Casino: cards, chips, table felts/models,
 * dealer skins, GeckoLib geometry and animations. Every output is a normal resource that can be
 * replaced by hand-made art or Blockbench models (same file names).
 *
 * Run from the project root with JDK 21:  java tools/AssetGen.java
 * Table numbers must match ru.mosvegas.casino.table.TableGeometry.
 */
public class AssetGen {
    static Path RES = Paths.get("src/main/resources/assets/mosvegas_casino");
    static final double CENTER_Z = -0.45;
    static final double FELT_Y = 14.0;
    // orientation switches for the felt texture / rotated cubes (GeckoLib conventions)
    static boolean FELT_FLIP_U = Boolean.parseBoolean(System.getProperty("feltFlipU", "false"));
    static boolean FELT_FLIP_V = Boolean.parseBoolean(System.getProperty("feltFlipV", "false"));
    static double ROT_SIGN = Double.parseDouble(System.getProperty("rotSign", "1"));
    static double X_SIGN = Double.parseDouble(System.getProperty("xSign", "-1"));

    record Style(String id, String title, String variant, double radius, int boxes, Color felt, Color print, Color accent,
                 Color rim, Color wood, Color metal, Color chair, String logo, String[] rules) {}

    static final List<Style> STYLES = List.of(
            new Style("classic_black", "Blackjack Classic", "CLASSIC", 2.4, 7, new Color(0x141517), new Color(0xD4AF5A), new Color(0xB0882E),
                    new Color(0x151515), new Color(0x2B1A10), new Color(0xB8933A), new Color(0x1C1C1C), "MOSVEGAS",
                    new String[]{"BLACKJACK PAYS 3 TO 2", "Dealer must stand on 17 and must draw to 16", "INSURANCE PAYS 2 TO 1"}),
            new Style("speed_green", "Speed Blackjack", "SPEED", 2.4, 7, new Color(0x0D5E30), new Color(0xF4D35E), new Color(0xD1342F),
                    new Color(0x3A2414), new Color(0x4A2C18), new Color(0xC9A24A), new Color(0x2A1A10), "SPEED",
                    new String[]{"BLACKJACK PAYS 3 TO 2", "Dealer must stand on 17 and must draw to 16", "INSURANCE PAYS 2 TO 1"}),
            new Style("infinite_grey", "Infinite Blackjack", "INFINITE", 2.4, 7, new Color(0x2B2E33), new Color(0xE8ECF2), new Color(0x3A7BD5),
                    new Color(0x111214), new Color(0x1C1D20), new Color(0x8FA3BF), new Color(0x18191C), "INFINITE",
                    new String[]{"BLACKJACK PAYS 3 TO 2", "Dealer stands on soft 17", "SIX PLAYER CARDS = AUTOMATIC WIN"}),
            new Style("free_bet_purple", "Free Bet Blackjack", "FREE_BET", 1.45, 5, new Color(0x3B1D5C), new Color(0xF2C46D), new Color(0xE0568E),
                    new Color(0x1A1020), new Color(0x2A1530), new Color(0xC9A24A), new Color(0x22122E), "FREE BET",
                    new String[]{"BLACKJACK PAYS 3 TO 2", "Dealer hits soft 17 • Dealer 22 pushes", "FREE DOUBLES 9 10 11 • FREE SPLITS"}),
            new Style("salon_prive", "Salon Prive", "CLASSIC", 1.45, 3, new Color(0xC8B285), new Color(0x2E2416), new Color(0x9C7A2E),
                    new Color(0x3A2A1A), new Color(0x5A3A1A), new Color(0xD4AF37), new Color(0x2E1F12), "SALON PRIVE",
                    new String[]{"BLACKJACK PAYS 3 TO 2", "Dealer must stand on 17 and must draw to 16", "INSURANCE PAYS 2 TO 1"})
    );

    static final long[] CHIPS = {100, 500, 1000, 5000, 25000, 100000};
    static final Color[] CHIP_COLORS = {new Color(0xEDEDED), new Color(0xC62828), new Color(0x2E7D32), new Color(0x1A1A1A),
            new Color(0x6A1B9A), new Color(0xE0A100), new Color(0x1565C0), new Color(0xEF6C00)};

    public static void main(String[] args) throws Exception {
        if (args.length > 0) RES = Paths.get(args[0]);
        Random random = new Random(7);
        cards();
        chips();
        for (Style s : STYLES) {
            tableTexture(s, random);
            tableModel(s);
            cardBack(s.id, s.accent, s.felt);
        }
        cardBack(null, new Color(0xA01818), new Color(0x2A0A0A));
        tableAnimation();
        dealerModel(false);
        dealerModel(true);
        dealerAnimations();
        skins();
        icons();
        spotRing();
        System.out.println("Assets written to " + RES.toAbsolutePath());
    }

    /** White glowing ring decal for the betting spot highlight (tinted in game). */
    static void spotRing() throws IOException {
        int n = 128;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        double c = (n - 1) / 2.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double r = Math.hypot(x - c, y - c) / c;
                double ring = Math.exp(-Math.pow((r - 0.78) / 0.07, 2));
                double glow = r < 0.78 ? 0.18 * Math.pow(r / 0.78, 3) : 0;
                double a = Math.min(1, ring + glow);
                if (r > 1) a = 0;
                img.setRGB(x, y, ((int) Math.round(a * 255) << 24) | 0xFFFFFF);
            }
        }
        png(img, "textures/misc/spot_ring.png");
    }

    // =================================================================== helpers

    static void png(BufferedImage img, String path) throws IOException {
        Path p = RES.resolve(path);
        Files.createDirectories(p.getParent());
        ImageIO.write(img, "png", p.toFile());
    }

    static void text(String content, String path) throws IOException {
        Path p = RES.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    static Graphics2D g2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static String f(double v) {
        if (Math.abs(v) < 1e-9) v = 0;
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.equals("-0") ? "0" : s;
    }

    static Font font(String name, int style, float size) {
        return new Font(name, style, 10).deriveFont(size);
    }

    // =================================================================== suits

    static Shape suit(int suit, double cx, double cy, double size) {
        Path2D p = new Path2D.Double();
        double s = size;
        switch (suit) {
            case 1 -> { // heart
                p.moveTo(cx, cy + s * 0.45);
                p.curveTo(cx - s * 0.62, cy + s * 0.02, cx - s * 0.55, cy - s * 0.52, cx, cy - s * 0.22);
                p.curveTo(cx + s * 0.55, cy - s * 0.52, cx + s * 0.62, cy + s * 0.02, cx, cy + s * 0.45);
            }
            case 2 -> { // diamond
                p.moveTo(cx, cy - s * 0.5);
                p.quadTo(cx + s * 0.18, cy - s * 0.18, cx + s * 0.38, cy);
                p.quadTo(cx + s * 0.18, cy + s * 0.18, cx, cy + s * 0.5);
                p.quadTo(cx - s * 0.18, cy + s * 0.18, cx - s * 0.38, cy);
                p.quadTo(cx - s * 0.18, cy - s * 0.18, cx, cy - s * 0.5);
            }
            case 0 -> { // spade
                p.moveTo(cx, cy - s * 0.48);
                p.curveTo(cx + s * 0.62, cy - s * 0.02, cx + s * 0.5, cy + s * 0.42, cx + s * 0.05, cy + s * 0.18);
                p.lineTo(cx + s * 0.16, cy + s * 0.48);
                p.lineTo(cx - s * 0.16, cy + s * 0.48);
                p.lineTo(cx - s * 0.05, cy + s * 0.18);
                p.curveTo(cx - s * 0.5, cy + s * 0.42, cx - s * 0.62, cy - s * 0.02, cx, cy - s * 0.48);
            }
            default -> { // club
                Area a = new Area(new Ellipse2D.Double(cx - s * 0.17, cy - s * 0.48, s * 0.34, s * 0.34));
                a.add(new Area(new Ellipse2D.Double(cx - s * 0.46, cy - s * 0.13, s * 0.34, s * 0.34)));
                a.add(new Area(new Ellipse2D.Double(cx + s * 0.12, cy - s * 0.13, s * 0.34, s * 0.34)));
                Path2D stem = new Path2D.Double();
                stem.moveTo(cx - s * 0.05, cy);
                stem.lineTo(cx + s * 0.05, cy);
                stem.lineTo(cx + s * 0.16, cy + s * 0.48);
                stem.lineTo(cx - s * 0.16, cy + s * 0.48);
                stem.closePath();
                a.add(new Area(stem));
                return a;
            }
        }
        p.closePath();
        return p;
    }

    // =================================================================== cards

    static final String[] SUITS = {"spades", "hearts", "diamonds", "clubs"};
    static final String[] RANKS = {"a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"};
    static final String[] LABELS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    static final int CW = 128, CH = 176;

    static void cards() throws IOException {
        for (int s = 0; s < 4; s++) for (int r = 0; r < 13; r++) png(card(s, r), "textures/card/" + SUITS[s] + "_" + RANKS[r] + ".png");
    }

    static BufferedImage card(int suit, int rank) {
        BufferedImage img = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, CW - 2, CH - 2, 18, 18);
        g.setColor(new Color(0xFBFAF6));
        g.fill(shape);
        g.setColor(new Color(0xB9B6AE));
        g.setStroke(new BasicStroke(2f));
        g.draw(shape);
        Color ink = (suit == 1 || suit == 2) ? new Color(0xC8102E) : new Color(0x151515);
        g.setColor(ink);
        // corner indices
        Font f = font("Georgia", Font.BOLD, rank == 9 ? 26f : 30f);
        g.setFont(f);
        String label = LABELS[rank];
        int lw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, 18 - lw / 2 + 2, 32);
        g.fill(suit(suit, 20, 47, 20));
        AffineTransform old = g.getTransform();
        g.rotate(Math.PI, CW / 2.0, CH / 2.0);
        g.drawString(label, 18 - lw / 2 + 2, 32);
        g.fill(suit(suit, 20, 47, 20));
        g.setTransform(old);
        // centre
        if (rank == 0) {
            g.fill(suit(suit, CW / 2.0, CH / 2.0, 62));
        } else if (rank >= 10) {
            Rectangle2D frame = new Rectangle2D.Double(30, 30, CW - 60, CH - 60);
            g.setColor(new Color(0xF2E6C4));
            g.fill(frame);
            g.setColor(ink);
            g.setStroke(new BasicStroke(2f));
            g.draw(frame);
            g.setColor(new Color(0xD4AF37));
            g.fill(crown(CW / 2.0, 58, 34));
            g.setColor(ink);
            g.setFont(font("Georgia", Font.BOLD, 44f));
            int w = g.getFontMetrics().stringWidth(label);
            g.drawString(label, CW / 2 - w / 2, 112);
            g.fill(suit(suit, CW / 2.0, 128, 22));
        } else {
            int n = rank + 1;
            for (double[] p : pips(n)) {
                double x = 44 + p[0] * 40, y = 40 + p[1] * 96;
                Shape pip = suit(suit, x, y, 24);
                if (p[1] > 0.55) {
                    AffineTransform t = g.getTransform();
                    g.rotate(Math.PI, x, y);
                    g.fill(pip);
                    g.setTransform(t);
                } else g.fill(pip);
            }
        }
        g.dispose();
        return clip(img, shape);
    }

    static Shape crown(double cx, double cy, double w) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx - w / 2, cy + w * 0.3);
        p.lineTo(cx - w / 2, cy - w * 0.15);
        p.lineTo(cx - w / 4, cy + w * 0.05);
        p.lineTo(cx, cy - w * 0.3);
        p.lineTo(cx + w / 4, cy + w * 0.05);
        p.lineTo(cx + w / 2, cy - w * 0.15);
        p.lineTo(cx + w / 2, cy + w * 0.3);
        p.closePath();
        return p;
    }

    static double[][] pips(int n) {
        return switch (n) {
            case 2 -> new double[][]{{0.5, 0}, {0.5, 1}};
            case 3 -> new double[][]{{0.5, 0}, {0.5, 0.5}, {0.5, 1}};
            case 4 -> new double[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            case 5 -> new double[][]{{0, 0}, {1, 0}, {0.5, 0.5}, {0, 1}, {1, 1}};
            case 6 -> new double[][]{{0, 0}, {1, 0}, {0, 0.5}, {1, 0.5}, {0, 1}, {1, 1}};
            case 7 -> new double[][]{{0, 0}, {1, 0}, {0.5, 0.25}, {0, 0.5}, {1, 0.5}, {0, 1}, {1, 1}};
            case 8 -> new double[][]{{0, 0}, {1, 0}, {0.5, 0.25}, {0, 0.5}, {1, 0.5}, {0.5, 0.75}, {0, 1}, {1, 1}};
            case 9 -> new double[][]{{0, 0}, {1, 0}, {0, 0.33}, {1, 0.33}, {0.5, 0.5}, {0, 0.67}, {1, 0.67}, {0, 1}, {1, 1}};
            default -> new double[][]{{0, 0}, {1, 0}, {0.5, 0.17}, {0, 0.33}, {1, 0.33}, {0, 0.67}, {1, 0.67}, {0.5, 0.83}, {0, 1}, {1, 1}};
        };
    }

    static BufferedImage clip(BufferedImage img, Shape shape) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(out);
        g.setClip(shape);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        // hard alpha for cutout rendering
        for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) {
            int argb = out.getRGB(x, y);
            int a = argb >>> 24;
            out.setRGB(x, y, a < 128 ? 0 : (argb | 0xFF000000));
        }
        return out;
    }

    static void cardBack(String style, Color accent, Color dark) throws IOException {
        BufferedImage img = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, CW - 2, CH - 2, 18, 18);
        g.setColor(new Color(0xFBFAF6));
        g.fill(shape);
        RoundRectangle2D inner = new RoundRectangle2D.Double(9, 9, CW - 18, CH - 18, 10, 10);
        g.setColor(accent);
        g.fill(inner);
        g.setClip(inner);
        g.setColor(accent.darker());
        g.setStroke(new BasicStroke(2f));
        for (int i = -CH; i < CW + CH; i += 12) {
            g.drawLine(i, 0, i + CH, CH);
            g.drawLine(i, CH, i + CH, 0);
        }
        g.setClip(null);
        g.setColor(new Color(0xFBFAF6));
        g.fill(new Ellipse2D.Double(CW / 2.0 - 30, CH / 2.0 - 30, 60, 60));
        g.setColor(accent.darker());
        g.setFont(font("Georgia", Font.BOLD, 26f));
        int w = g.getFontMetrics().stringWidth("MV");
        g.drawString("MV", CW / 2 - w / 2, CH / 2 + 9);
        g.setColor(new Color(0xB9B6AE));
        g.draw(shape);
        g.dispose();
        png(clip(img, shape), "textures/card/" + (style == null ? "back" : "back_" + style) + ".png");
    }

    // =================================================================== chips

    static void chips() throws IOException {
        for (int i = 0; i < CHIPS.length; i++) png(chip(CHIP_COLORS[i], label(CHIPS[i])), "textures/chip/chip_" + CHIPS[i] + ".png");
        for (int i = 0; i < CHIP_COLORS.length; i++) png(chip(CHIP_COLORS[i], ""), "textures/chip/chip_slot" + i + ".png");
    }

    static String label(long v) {
        if (v >= 1_000_000 && v % 1_000_000 == 0) return (v / 1_000_000) + "M";
        if (v >= 1_000 && v % 1_000 == 0) return (v / 1_000) + "K";
        return Long.toString(v);
    }

    static BufferedImage chip(Color base, String text) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(img);
        Ellipse2D disc = new Ellipse2D.Double(0.5, 0.5, n - 1, n - 1);
        g.setColor(base);
        g.fill(disc);
        boolean light = base.getRed() + base.getGreen() + base.getBlue() > 600;
        Color stripe = light ? new Color(0x1E5AA8) : new Color(0xF5F5F5);
        g.setColor(stripe);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.fill(new Arc2D.Double(0.5, 0.5, n - 1, n - 1, Math.toDegrees(a) - 7, 14, Arc2D.PIE));
        }
        g.setColor(base);
        g.fill(new Ellipse2D.Double(8, 8, n - 16, n - 16));
        g.setColor(stripe);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0));
        g.draw(new Ellipse2D.Double(11, 11, n - 22, n - 22));
        g.setColor(light ? new Color(0x222222) : new Color(0xFFFFFF));
        if (!text.isEmpty()) {
            g.setFont(font("Arial", Font.BOLD, text.length() > 3 ? 15f : 18f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, n / 2 - fm.stringWidth(text) / 2, n / 2 + fm.getAscent() / 2 - 2);
        }
        g.dispose();
        return clip(img, disc);
    }

    // =================================================================== table texture

    static final int ATLAS = 1024;
    static final int SW = 64; // swatch size, row starts at y = 512

    enum Swatch { RIM, WOOD, METAL, CHAIR, STEEL, SHOE, FELT_EDGE, CUSHION, TRAY, ACRYLIC, UNDER, CLEAR }

    static int[] swatch(Swatch s) {
        int i = s.ordinal();
        return new int[]{(i % 16) * SW, 512 + (i / 16) * SW};
    }

    static void tableTexture(Style s, Random random) throws IOException {
        BufferedImage atlas = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
        BufferedImage felt = felt(s, random);
        Graphics2D g = g2(atlas);
        AffineTransform t = new AffineTransform();
        if (FELT_FLIP_U) {
            t.translate(1024, 0);
            t.scale(-1, 1);
        }
        if (FELT_FLIP_V) {
            t.translate(0, 512);
            t.scale(1, -1);
        }
        g.drawImage(felt, t, null);
        fillSwatch(g, Swatch.RIM, s.rim, random, 0.06, true);
        fillSwatch(g, Swatch.WOOD, s.wood, random, 0.10, false);
        fillSwatch(g, Swatch.METAL, s.metal, random, 0.08, false);
        fillSwatch(g, Swatch.CHAIR, s.chair, random, 0.06, true);
        fillSwatch(g, Swatch.STEEL, new Color(0x3C3F44), random, 0.05, false);
        fillSwatch(g, Swatch.SHOE, new Color(0xD8DBDE), random, 0.03, false);
        fillSwatch(g, Swatch.FELT_EDGE, s.felt.darker(), random, 0.04, false);
        fillSwatch(g, Swatch.CUSHION, s.rim.brighter(), random, 0.06, true);
        fillSwatch(g, Swatch.ACRYLIC, new Color(0xBFD3DC), random, 0.02, false);
        fillSwatch(g, Swatch.UNDER, new Color(0x101010), random, 0.02, false);
        int[] tray = swatch(Swatch.TRAY);
        g.setColor(new Color(0x111111));
        g.fillRect(tray[0], tray[1], SW, SW);
        for (int i = 0; i < 8; i++) {
            g.setColor(CHIP_COLORS[i % CHIP_COLORS.length]);
            g.fillRect(tray[0] + 2 + i * 7 + (i > 3 ? 4 : 0), tray[1] + 4, 6, SW - 8);
            g.setColor(new Color(0, 0, 0, 90));
            for (int y = tray[1] + 6; y < tray[1] + SW - 4; y += 3) g.drawLine(tray[0] + 2 + i * 7 + (i > 3 ? 4 : 0), y, tray[0] + 7 + i * 7 + (i > 3 ? 4 : 0), y);
        }
        g.dispose();
        png(atlas, "textures/block/table/" + s.id + ".png");
    }

    static void fillSwatch(Graphics2D g, Swatch sw, Color c, Random random, double noise, boolean stitched) {
        int[] p = swatch(sw);
        for (int y = 0; y < SW; y++) for (int x = 0; x < SW; x++) {
            double k = 1 + (random.nextDouble() - 0.5) * noise;
            if (sw == Swatch.WOOD) k *= 1 + 0.08 * Math.sin((y + Math.sin(x * 0.2) * 3) * 0.6);
            g.setColor(shade(c, k));
            g.fillRect(p[0] + x, p[1] + y, 1, 1);
        }
        if (stitched) {
            g.setColor(shade(c, 1.35));
            for (int x = 0; x < SW; x += 4) g.fillRect(p[0] + x, p[1] + 6, 2, 1);
            for (int x = 0; x < SW; x += 4) g.fillRect(p[0] + x, p[1] + SW - 7, 2, 1);
        }
    }

    static Color shade(Color c, double k) {
        return new Color(clamp(c.getRed() * k), clamp(c.getGreen() * k), clamp(c.getBlue() * k));
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    /** Felt in "player view": image top = dealer edge, bottom = players' arc, left = players' left (dealer's right). */
    static BufferedImage felt(Style s, Random random) {
        int W = 1024, H = 512;
        double ppb = 512.0 / s.radius; // image pixels per block
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(img);
        double cx = 512, cy = 0;
        Shape disc = new Arc2D.Double(cx - 512, cy - 512, 1024, 1024, 180, 180, Arc2D.PIE);
        // fabric
        BufferedImage fabric = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double k = 1 + (random.nextDouble() - 0.5) * 0.07;
            double dist = Math.hypot(x - cx, y - cy) / 512.0;
            k *= 1.06 - 0.12 * dist;
            fabric.setRGB(x, y, shade(s.felt, k).getRGB());
        }
        g.setClip(disc);
        g.drawImage(fabric, 0, 0, null);
        float line = (float) Math.max(2, ppb * 0.012);
        // speed table: coloured arc band like the live green table
        if (s.variant.equals("SPEED")) {
            g.setStroke(new BasicStroke((float) (ppb * 0.13)));
            g.setColor(s.accent);
            double r = 512 * 0.55;
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 200, 140, Arc2D.OPEN));
            g.setStroke(new BasicStroke((float) (ppb * 0.025)));
            g.setColor(s.print);
            double r2 = 512 * 0.55 + ppb * 0.08;
            g.draw(new Arc2D.Double(cx - r2, cy - r2, 2 * r2, 2 * r2, 200, 140, Arc2D.OPEN));
        }
        // outer print line
        g.setColor(s.print);
        g.setStroke(new BasicStroke(line));
        double rOut = 512 - ppb * 0.2;
        g.draw(new Arc2D.Double(cx - rOut, cy - rOut, 2 * rOut, 2 * rOut, 180, 180, Arc2D.OPEN));
        // texts
        Font big = font("Georgia", Font.BOLD, (float) (ppb * 0.085));
        Font small = font("Georgia", Font.PLAIN, (float) (ppb * 0.052));
        arcText(g, s.rules[0], cx, cy, 512 * 0.36, big, s.print);
        arcText(g, s.rules[1], cx, cy, 512 * 0.36 + ppb * 0.1, small, s.print);
        Color band = s.variant.equals("SPEED") ? s.print : s.print;
        arcText(g, s.rules[2], cx, cy, 512 * 0.53 + (s.variant.equals("SPEED") ? -ppb * 0.02 : 0), small, s.variant.equals("SPEED") ? new Color(0xFFFFFF) : band);
        // insurance band lines
        if (!s.variant.equals("SPEED")) {
            g.setStroke(new BasicStroke(line * 0.8f));
            for (double rr : new double[]{512 * 0.53 - ppb * 0.075, 512 * 0.53 + ppb * 0.05}) {
                g.draw(new Arc2D.Double(cx - rr, cy - rr, 2 * rr, 2 * rr, 205, 130, Arc2D.OPEN));
            }
        }
        // logo near the dealer
        g.setFont(font("Georgia", Font.BOLD | Font.ITALIC, (float) (ppb * 0.075)));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(s.accent);
        g.drawString(s.logo, (int) (cx - fm.stringWidth(s.logo) / 2.0), (int) (ppb * 0.26));
        // betting spots
        for (int b = 0; b < s.boxes; b++) {
            double ang = boxAngle(b, s.boxes);
            double[] spot = img(s, ang, s.radius * 0.70);
            double r = ppb * 0.10;
            g.setColor(s.print);
            g.setStroke(new BasicStroke(line));
            if (s.variant.equals("CLASSIC") || s.variant.equals("FREE_BET")) {
                g.draw(new RoundRectangle2D.Double(spot[0] - r, spot[1] - r, 2 * r, 2 * r, r * 0.7, r * 0.7));
            } else g.draw(new Ellipse2D.Double(spot[0] - r, spot[1] - r, 2 * r, 2 * r));
            g.setFont(font("Georgia", Font.BOLD, (float) (ppb * 0.06)));
            String num = Integer.toString(b + 1);
            fm = g.getFontMetrics();
            double[] numPos = img(s, ang, s.radius * 0.70 + 0.16);
            rotatedText(g, num, numPos[0], numPos[1], ang, s.print);
            // side bet spots
            String[] side = sideLabels(s);
            for (int i = 0; i < side.length; i++) {
                double[] p = sideSpot(s, b, i);
                double sr = ppb * 0.052;
                g.setStroke(new BasicStroke(line * 0.8f));
                g.draw(new Ellipse2D.Double(p[0] - sr, p[1] - sr, 2 * sr, 2 * sr));
                g.setFont(font("Arial", Font.BOLD, (float) (ppb * 0.024)));
                rotatedText(g, side[i], p[0], p[1] + ppb * 0.009, ang, s.print);
            }
        }
        g.setClip(null);
        g.dispose();
        return img;
    }

    static String[] sideLabels(Style s) {
        return switch (s.variant) {
            case "INFINITE" -> new String[]{"PP", "21+3", "HOT3", "BUST"};
            case "FREE_BET" -> new String[]{"PP", "21+3", "BUST"};
            default -> new String[]{"PP", "21+3"};
        };
    }

    static double boxAngle(int box, int boxes) {
        if (boxes == 1) return 90;
        double span = boxes <= 3 ? 110 : 140;
        double start = 90 + span / 2;
        return start - box * (span / (boxes - 1));
    }

    /** Table-local polar point -> felt image coordinates. */
    static double[] img(Style s, double angleDeg, double dist) {
        double a = Math.toRadians(angleDeg);
        double lx = Math.cos(a) * dist, lz = CENTER_Z + Math.sin(a) * dist;
        return imgLocal(s, lx, lz);
    }

    static double[] imgLocal(Style s, double lx, double lz) {
        double x = 512 - lx / s.radius * 512;
        double y = (lz - CENTER_Z) / s.radius * 512;
        return new double[]{x, y};
    }

    static double[] sideSpot(Style s, int box, int index) {
        double angle = boxAngle(box, s.boxes) + (index % 2 == 0 ? 8 : -8) * (2.4 / s.radius);
        double dist = s.radius * 0.86;
        double[] p = img(s, angle, dist);
        if (index >= 2) {
            double a = Math.toRadians(angle);
            double lx = Math.cos(a) * dist - (-Math.cos(Math.toRadians(boxAngle(box, s.boxes)))) * 0.12;
            double lz = CENTER_Z + Math.sin(a) * dist - (-Math.sin(Math.toRadians(boxAngle(box, s.boxes)))) * 0.12;
            p = imgLocal(s, lx, lz);
        }
        return p;
    }

    static void rotatedText(Graphics2D g, String text, double x, double y, double tableAngle, Color c) {
        // image angle of the point: the glyph "up" must face the dealer (felt centre)
        double imgAngle = 180 - tableAngle; // mirrored x
        double beta = Math.toRadians(imgAngle - 90);
        AffineTransform t = g.getTransform();
        g.translate(x, y);
        g.rotate(beta);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, -fm.stringWidth(text) / 2f, fm.getAscent() / 2f - 1);
        g.setTransform(t);
    }

    /** Text along an arc below (cx, cy), readable from the players' side, glyph tops towards the centre. */
    static void arcText(Graphics2D g, String text, double cx, double cy, double radius, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        double total = fm.stringWidth(text);
        double arc = total / radius; // radians
        double a = Math.PI / 2 + arc / 2; // start on the left
        for (char ch : text.toCharArray()) {
            String str = String.valueOf(ch);
            double w = fm.stringWidth(str);
            double mid = a - (w / 2) / radius;
            double x = cx + Math.cos(mid) * radius, y = cy + Math.sin(mid) * radius;
            AffineTransform t = g.getTransform();
            g.translate(x, y);
            g.rotate(mid - Math.PI / 2);
            g.drawString(str, (float) (-w / 2), (float) (fm.getAscent() * 0.35));
            g.setTransform(t);
            a -= w / radius;
        }
    }

    // =================================================================== geo json

    static final class Geo {
        final StringBuilder bones = new StringBuilder();
        boolean firstBone = true;
        final int texW, texH;

        Geo(int w, int h) {
            texW = w;
            texH = h;
        }

        Bone bone(String name, String parent, double px, double py, double pz) {
            return new Bone(this, name, parent, px, py, pz);
        }

        String build(String identifier) {
            return "{\n  \"format_version\": \"1.12.0\",\n  \"minecraft:geometry\": [\n    {\n      \"description\": {\n"
                    + "        \"identifier\": \"geometry." + identifier + "\",\n        \"texture_width\": " + texW + ",\n        \"texture_height\": " + texH + ",\n"
                    + "        \"visible_bounds_width\": 8,\n        \"visible_bounds_height\": 4,\n        \"visible_bounds_offset\": [0, 1, 0]\n      },\n"
                    + "      \"bones\": [\n" + bones + "\n      ]\n    }\n  ]\n}\n";
        }
    }

    static final class Bone {
        final Geo geo;
        final StringBuilder cubes = new StringBuilder();
        final String name, parent;
        final double px, py, pz;
        boolean first = true;

        Bone(Geo geo, String name, String parent, double px, double py, double pz) {
            this.geo = geo;
            this.name = name;
            this.parent = parent;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }

        /** Box-UV cube. */
        Bone box(double x, double y, double z, double sx, double sy, double sz, int u, int v, double inflate) {
            sep();
            cubes.append("            {\"origin\": [").append(f(x)).append(", ").append(f(y)).append(", ").append(f(z)).append("], \"size\": [")
                    .append(f(sx)).append(", ").append(f(sy)).append(", ").append(f(sz)).append("], \"uv\": [").append(u).append(", ").append(v).append("]");
            if (inflate != 0) cubes.append(", \"inflate\": ").append(f(inflate));
            cubes.append("}");
            return this;
        }

        /** Per-face UV cube; faces = map of face -> {u, v, w, h}. */
        Bone face(double x, double y, double z, double sx, double sy, double sz, double[] pivot, double[] rotation, Map<String, double[]> faces) {
            sep();
            cubes.append("            {\"origin\": [").append(f(x)).append(", ").append(f(y)).append(", ").append(f(z)).append("], \"size\": [")
                    .append(f(sx)).append(", ").append(f(sy)).append(", ").append(f(sz)).append("]");
            if (pivot != null) cubes.append(", \"pivot\": [").append(f(pivot[0])).append(", ").append(f(pivot[1])).append(", ").append(f(pivot[2])).append("]");
            if (rotation != null) cubes.append(", \"rotation\": [").append(f(rotation[0])).append(", ").append(f(rotation[1])).append(", ").append(f(rotation[2])).append("]");
            cubes.append(", \"uv\": {");
            boolean firstFace = true;
            for (String key : List.of("north", "east", "south", "west", "up", "down")) {
                double[] uv = faces.get(key);
                if (uv == null) continue;
                if (!firstFace) cubes.append(", ");
                firstFace = false;
                cubes.append("\"").append(key).append("\": {\"uv\": [").append(f(uv[0])).append(", ").append(f(uv[1])).append("], \"uv_size\": [")
                        .append(f(uv[2])).append(", ").append(f(uv[3])).append("]}");
            }
            cubes.append("}}");
            return this;
        }

        private void sep() {
            if (!first) cubes.append(",\n");
            first = false;
        }

        void end() {
            if (!geo.firstBone) geo.bones.append(",\n");
            geo.firstBone = false;
            geo.bones.append("        {\"name\": \"").append(name).append("\"");
            if (parent != null) geo.bones.append(", \"parent\": \"").append(parent).append("\"");
            geo.bones.append(", \"pivot\": [").append(f(px)).append(", ").append(f(py)).append(", ").append(f(pz)).append("]");
            if (cubes.length() > 0) geo.bones.append(", \"cubes\": [\n").append(cubes).append("\n          ]");
            geo.bones.append("}");
        }
    }

    static Map<String, double[]> all(Swatch s) {
        int[] p = swatch(s);
        double[] uv = {p[0] + 8, p[1] + 8, 16, 16};
        Map<String, double[]> m = new LinkedHashMap<>();
        for (String k : List.of("north", "east", "south", "west", "up", "down")) m.put(k, uv);
        return m;
    }

    static Map<String, double[]> topOnly(Swatch side, double[] up) {
        Map<String, double[]> m = all(side);
        m.put("up", up);
        return m;
    }

    // bedrock coordinates of a table-local point (pixels)
    static double bx(double lx) { return X_SIGN * lx * 16; }
    static double bz(double lz) { return -lz * 16; }

    /** Axis-aligned cube centred on a table-local point. */
    static void localBox(Bone bone, double lx, double lz, double y0, double sxBlocks, double h, double szBlocks, double yawDeg, Map<String, double[]> faces) {
        double cx = bx(lx), cz = bz(lz);
        double sx = sxBlocks * 16, sz = szBlocks * 16;
        double[] rot = Math.abs(yawDeg) > 1e-6 ? new double[]{0, ROT_SIGN * yawDeg, 0} : null;
        bone.face(cx - sx / 2, y0, cz - sz / 2, sx, h, sz, rot == null ? null : new double[]{cx, y0, cz}, rot, faces);
    }

    static void arcSegments(Bone bone, double radius, int segments, double y0, double h, double thickness, Swatch swatch, double extra) {
        for (int k = 0; k < segments; k++) {
            double t0 = 180.0 * k / segments, t1 = 180.0 * (k + 1) / segments, tc = (t0 + t1) / 2;
            double len = 2 * radius * Math.sin(Math.toRadians(t1 - t0) / 2) * 1.08 + extra;
            double a = Math.toRadians(tc);
            double lx = Math.cos(a) * radius, lz = CENTER_Z + Math.sin(a) * radius;
            localBox(bone, lx, lz, y0, len, h, thickness, 90 - tc, all(swatch));
        }
    }

    static void tableModel(Style s) throws IOException {
        double R = s.radius;
        Geo geo = new Geo(ATLAS, ATLAS);
        Bone root = geo.bone("table", null, 0, 0, 0);
        root.end();

        // felt (top face carries the printed half disc)
        Bone felt = geo.bone("felt", "table", 0, 0, 0);
        double x0 = Math.min(bx(-R), bx(R)), z0 = bz(CENTER_Z + R);
        // only the top face is visible: sides and bottom map to a transparent swatch (no dark corners)
        Map<String, double[]> feltFaces = all(Swatch.CLEAR);
        feltFaces.put("up", new double[]{0, 0, 1024, 512});
        felt.face(x0, FELT_Y - 0.25, z0, 32 * R, 0.25, 16 * R, null, null, feltFaces);
        felt.end();

        // padded rim and straight dealer edge
        Bone rim = geo.bone("rim", "table", 0, 0, 0);
        int segments = R > 2 ? 26 : 18;
        arcSegments(rim, R - 0.05, segments, FELT_Y - 0.5, 1.9, 0.16, Swatch.RIM, 0.02);
        localBox(rim, 0, CENTER_Z - 0.06, FELT_Y - 0.6, 2 * R + 0.04, 1.2, 0.12, 0, all(Swatch.WOOD));
        localBox(rim, 0, CENTER_Z - 0.125, FELT_Y + 0.6, 2 * R + 0.04, 0.25, 0.02, 0, all(Swatch.METAL));
        rim.end();

        // apron, foot rail, pedestal
        Bone base = geo.bone("base", "table", 0, 0, 0);
        arcSegments(base, R - 0.13, segments, 7.5, FELT_Y - 7.9, 0.06, Swatch.WOOD, 0.03);
        arcSegments(base, R - 0.22, segments, 1.4, 0.7, 0.06, Swatch.METAL, 0.03);
        double pedFront = Math.max(0.35, CENTER_Z + R * 0.62);
        localBox(base, 0, (0.12 + pedFront) / 2, 0, R * 0.9, FELT_Y - 0.4, pedFront - 0.12, 0, all(Swatch.WOOD));
        localBox(base, 0, (0.12 + pedFront) / 2, 0, R * 0.95, 0.6, pedFront - 0.06, 0, all(Swatch.METAL));
        base.end();

        // chip tray in front of the dealer
        Bone tray = geo.bone("chip_tray", "table", 0, 0, 0);
        int[] tp = swatch(Swatch.TRAY);
        localBox(tray, 0, -0.30, FELT_Y - 0.1, Math.min(0.95, R * 0.5), 1.0, 0.22, 0, topOnly(Swatch.STEEL, new double[]{tp[0], tp[1], SW, SW}));
        tray.end();

        // card shoe (dealer's left) and discard holder (dealer's right), within reach of the seated dealer's hands
        Bone shoe = geo.bone("shoe", "table", 0, 0, 0);
        localBox(shoe, SHOE[0], SHOE[1], FELT_Y, 0.22, 2.6, 0.36, -35, all(Swatch.SHOE));
        localBox(shoe, SHOE[0] + 0.02, SHOE[1] + 0.12, FELT_Y, 0.16, 1.2, 0.08, -35, all(Swatch.STEEL));
        shoe.end();
        Bone discard = geo.bone("discard", "table", 0, 0, 0);
        localBox(discard, DISCARD[0], DISCARD[1], FELT_Y, 0.24, 0.3, 0.3, 35, all(Swatch.ACRYLIC));
        localBox(discard, DISCARD[0], DISCARD[1] - 0.14, FELT_Y, 0.24, 3.2, 0.02, 35, all(Swatch.ACRYLIC));
        discard.end();

        // dealer chair
        Bone chair = geo.bone("dealer_chair", "table", 0, 0, 0);
        double cz = SEAT_Z - 0.06;
        localBox(chair, 0, cz, 0, 0.42, 1.2, 0.42, 0, all(Swatch.STEEL));
        localBox(chair, 0, cz, 1.2, 0.1, 5.2, 0.1, 0, all(Swatch.STEEL));
        localBox(chair, 0, cz, 6.4, 0.46, 1.4, 0.46, 0, all(Swatch.CHAIR));
        localBox(chair, 0, cz - 0.24, 7.6, 0.46, 9.0, 0.08, 0, all(Swatch.CHAIR));
        chair.end();

        // players' stools
        Bone stools = geo.bone("stools", "table", 0, 0, 0);
        for (int b = 0; b < s.boxes; b++) {
            double ang = boxAngle(b, s.boxes);
            double a = Math.toRadians(ang), dist = R + 0.48;
            double lx = Math.cos(a) * dist, lz = CENTER_Z + Math.sin(a) * dist;
            double yaw = 90 - ang;
            localBox(stools, lx, lz, 0, 0.34, 0.8, 0.34, yaw, all(Swatch.STEEL));
            localBox(stools, lx, lz, 0.8, 0.09, 5.6, 0.09, yaw, all(Swatch.METAL));
            localBox(stools, lx, lz, 3.0, 0.3, 0.4, 0.3, yaw, all(Swatch.METAL));
            localBox(stools, lx, lz, 6.4, 0.42, 1.5, 0.42, yaw, all(Swatch.CUSHION));
        }
        stools.end();

        text(geo.build("blackjack_table_" + s.id), "geo/block/blackjack_table_" + s.id + ".geo.json");
    }

    static void tableAnimation() throws IOException {
        text("{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n    \"animation.table.idle\": {\n      \"loop\": true,\n      \"animation_length\": 1,\n      \"bones\": {}\n    }\n  },\n  \"geckolib_format_version\": 2\n}\n",
                "animations/block/blackjack_table.animation.json");
    }

    // =================================================================== dealer model

    static void dealerModel(boolean slim) throws IOException {
        Geo geo = new Geo(64, 64);
        geo.bone("root", null, 0, 0, 0).end();
        Bone body = geo.bone("body", "root", 0, 12, 0);
        body.box(-4, 12, -2, 8, 12, 4, 16, 16, 0).box(-4, 12, -2, 8, 12, 4, 16, 32, 0.25);
        body.end();
        Bone head = geo.bone("head", "body", 0, 24, 0);
        head.box(-4, 24, -4, 8, 8, 8, 0, 0, 0).box(-4, 24, -4, 8, 8, 8, 32, 0, 0.5);
        head.end();
        // talking mouth and blinking eyelids: tiny cubes on the face, scaled by DealerGeoModel (0 = hidden).
        // They sample the skin itself (mouth pixel 11,14 and cheek pixel 11,12), so any 64x64 skin works.
        Bone mouth = geo.bone("mouth", "head", 0, 25.5, -4);
        mouth.face(-1, 24.6, -4.07, 2, 0.9, 0.05, null, null, uniform(11, 14));
        mouth.end();
        Bone lids = geo.bone("eyelids", "head", 0, 28, -4);
        lids.face(-3, 27, -4.07, 2, 1, 0.05, null, null, uniform(11, 12)).face(1, 27, -4.07, 2, 1, 0.05, null, null, uniform(11, 12));
        lids.end();
        int aw = slim ? 3 : 4;
        double py = slim ? 21.5 : 22;
        Bone rightArm = geo.bone("rightArm", "body", -5, py, 0);
        rightArm.box(-4 - aw, 12, -2, aw, 12, 4, 40, 16, 0).box(-4 - aw, 12, -2, aw, 12, 4, 40, 32, 0.25);
        rightArm.end();
        Bone leftArm = geo.bone("leftArm", "body", 5, py, 0);
        leftArm.box(4, 12, -2, aw, 12, 4, 32, 48, 0).box(4, 12, -2, aw, 12, 4, 48, 48, 0.25);
        leftArm.end();
        // empty locator bones in the palms: the client reads their world position to put cards and chips in the hands
        geo.bone("rightHand", "rightArm", -4 - aw / 2.0, 12.5, 0).end();
        geo.bone("leftHand", "leftArm", 4 + aw / 2.0, 12.5, 0).end();
        Bone rightLeg = geo.bone("rightLeg", "root", -1.9, 12, 0);
        rightLeg.box(-3.9, 0, -2, 4, 12, 4, 0, 16, 0).box(-3.9, 0, -2, 4, 12, 4, 0, 32, 0.25);
        rightLeg.end();
        Bone leftLeg = geo.bone("leftLeg", "root", 1.9, 12, 0);
        leftLeg.box(-0.1, 0, -2, 4, 12, 4, 16, 48, 0).box(-0.1, 0, -2, 4, 12, 4, 0, 48, 0.25);
        leftLeg.end();
        text(geo.build(slim ? "dealer_slim" : "dealer_wide"), "geo/entity/" + (slim ? "dealer_slim" : "dealer_wide") + ".geo.json");
    }

    // =================================================================== dealer animations

    /** Keyframed bone channels: time -> [x, y, z]. */
    static final class Anim {
        final String name;
        final double length;
        final boolean loop;
        final Map<String, Map<String, TreeMap<Double, double[]>>> bones = new LinkedHashMap<>();

        Anim(String name, double length, boolean loop) {
            this.name = name;
            this.length = length;
            this.loop = loop;
        }

        Anim key(String bone, String channel, double time, double x, double y, double z) {
            bones.computeIfAbsent(bone, k -> new LinkedHashMap<>()).computeIfAbsent(channel, k -> new TreeMap<>()).put(time, new double[]{x, y, z});
            return this;
        }

        Anim rot(String bone, double time, double x, double y, double z) { return key(bone, "rotation", time, x, y, z); }
        Anim pos(String bone, double time, double x, double y, double z) { return key(bone, "position", time, x, y, z); }

        String json() {
            StringBuilder sb = new StringBuilder();
            sb.append("    \"animation.dealer.").append(name).append("\": {\n");
            if (loop) sb.append("      \"loop\": true,\n");
            sb.append("      \"animation_length\": ").append(f(length)).append(",\n      \"bones\": {\n");
            boolean firstBone = true;
            for (var bone : bones.entrySet()) {
                if (!firstBone) sb.append(",\n");
                firstBone = false;
                sb.append("        \"").append(bone.getKey()).append("\": {\n");
                boolean firstChannel = true;
                for (var channel : bone.getValue().entrySet()) {
                    if (!firstChannel) sb.append(",\n");
                    firstChannel = false;
                    sb.append("          \"").append(channel.getKey()).append("\": {\n");
                    boolean firstKey = true;
                    for (var k : channel.getValue().entrySet()) {
                        if (!firstKey) sb.append(",\n");
                        firstKey = false;
                        double[] v = k.getValue();
                        sb.append("            \"").append(f(k.getKey())).append("\": {\"post\": [").append(f(v[0])).append(", ").append(f(v[1])).append(", ").append(f(v[2]))
                                .append("], \"lerp_mode\": \"catmullrom\"}");
                    }
                    sb.append("\n          }");
                }
                sb.append("\n        }");
            }
            sb.append("\n      }\n    }");
            return sb.toString();
        }
    }

    // seated rest pose (degrees; negative X raises a limb forward)
    static final double[] R_ARM = {-70, 12, 0}, L_ARM = {-70, -12, 0}, HEAD_DOWN = {12, 0, 0};

    static Anim seatedArms(Anim a, double... times) {
        for (double t : times) {
            a.rot("rightArm", t, R_ARM[0], R_ARM[1], R_ARM[2]);
            a.rot("leftArm", t, L_ARM[0], L_ARM[1], L_ARM[2]);
        }
        return a;
    }

    static void dealerAnimations() throws IOException {
        List<Anim> list = new ArrayList<>();
        list.add(new Anim("idle", 3, true)
                .rot("rightArm", 0, 0, 0, 2).rot("rightArm", 1.5, -3, 0, 3).rot("rightArm", 3, 0, 0, 2)
                .rot("leftArm", 0, 0, 0, -2).rot("leftArm", 1.5, -3, 0, -3).rot("leftArm", 3, 0, 0, -2)
                .rot("head", 0, 0, 0, 0).rot("head", 1.5, 2, 3, 0).rot("head", 3, 0, 0, 0)
                .rot("body", 0, 0, 0, 0).rot("body", 1.5, 1, 0, 0).rot("body", 3, 0, 0, 0));
        list.add(new Anim("walk", 1, true)
                .rot("rightLeg", 0, 28, 0, 0).rot("rightLeg", 0.5, -28, 0, 0).rot("rightLeg", 1, 28, 0, 0)
                .rot("leftLeg", 0, -28, 0, 0).rot("leftLeg", 0.5, 28, 0, 0).rot("leftLeg", 1, -28, 0, 0)
                .rot("rightArm", 0, -22, 0, 3).rot("rightArm", 0.5, 22, 0, 3).rot("rightArm", 1, -22, 0, 3)
                .rot("leftArm", 0, 22, 0, -3).rot("leftArm", 0.5, -22, 0, -3).rot("leftArm", 1, 22, 0, -3)
                .pos("root", 0, 0, 0, 0).pos("root", 0.25, 0, 0.5, 0).pos("root", 0.5, 0, 0, 0).pos("root", 0.75, 0, 0.5, 0).pos("root", 1, 0, 0, 0));
        Anim sit = new Anim("sit", 4, true)
                .pos("root", 0, 0, -4, 0).pos("root", 4, 0, -4, 0)
                .rot("rightLeg", 0, -88, 6, 0).rot("rightLeg", 4, -88, 6, 0)
                .rot("leftLeg", 0, -88, -6, 0).rot("leftLeg", 4, -88, -6, 0)
                .rot("body", 0, 0, 0, 0).rot("body", 2, 1.5, 0, 0).rot("body", 4, 0, 0, 0)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 2, HEAD_DOWN[0] + 2, 2, 0).rot("head", 4, HEAD_DOWN[0], 0, 0);
        seatedArms(sit, 0, 4);
        sit.rot("rightArm", 2, R_ARM[0] - 2, R_ARM[1], 0).rot("leftArm", 2, L_ARM[0] - 2, L_ARM[1], 0);
        list.add(sit);

        // hand choreography played by the client in sync with the cards and chips it draws
        // (timings are mirrored in ru.mosvegas.casino.client.state.Choreo)
        for (int k = 0; k < BUCKETS; k++) {
            double angle = bucketAngle(k);
            list.add(dealTo("deal_" + k, felt(angle, 2.4 * 0.56, HAND_Y), k <= LAST_LEFT_BUCKET));
            list.add(reach("reach_" + k, felt(angle, 2.4 * 0.70, HAND_Y), k <= LAST_LEFT_BUCKET));
            list.add(point("point_" + k, felt(angle, 2.4 + 0.48, 1.32), k <= LAST_LEFT_BUCKET));
        }
        list.add(dealSelf());
        list.add(collect());
        // hole card: the right hand turns it over / lifts the corner
        double[] hole = aim(1, 12, HOLE);
        Anim flip = new Anim("flip", 0.6, false);
        rest(flip, 0, 0);
        arm(flip, "rightArm", 0.18, hole);
        flip.rot("rightArm", 0.3, hole[0] - 4, hole[1], -35).rot("rightArm", 0.42, hole[0], hole[1], 15);
        lean(flip, 0.18, 12);
        lean(flip, 0.42, 12);
        rest(flip, 0.6, 0);
        flip.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.2, 26, 0, 0).rot("head", 0.6, HEAD_DOWN[0], 0, 0);
        list.add(flip);
        Anim peek = new Anim("peek", 0.9, false);
        rest(peek, 0, 0);
        arm(peek, "rightArm", 0.25, hole);
        peek.rot("rightArm", 0.4, hole[0] - 6, hole[1], -20).rot("rightArm", 0.6, hole[0] - 6, hole[1], -20);
        arm(peek, "rightArm", 0.72, hole);
        lean(peek, 0.25, 14);
        lean(peek, 0.72, 14);
        rest(peek, 0.9, 0);
        peek.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.3, 32, 0, 0).rot("head", 0.65, 32, 0, 0).rot("head", 0.9, HEAD_DOWN[0], 0, 0);
        list.add(peek);
        // idle fidget: squares the chip stacks in the tray
        Anim tidy = new Anim("tidy", 1.8, false);
        rest(tidy, 0, 0);
        arm(tidy, "leftArm", 0.3, aim(-1, 6, TRAY_L));
        arm(tidy, "rightArm", 0.3, aim(1, 6, TRAY_R));
        arm(tidy, "leftArm", 0.65, aim(-1, 6, new double[]{-0.32, HAND_Y, -0.30}));
        arm(tidy, "rightArm", 0.65, aim(1, 6, new double[]{0.06, HAND_Y, -0.30}));
        arm(tidy, "leftArm", 1.0, aim(-1, 6, new double[]{-0.06, HAND_Y, -0.30}));
        arm(tidy, "rightArm", 1.0, aim(1, 6, new double[]{0.32, HAND_Y, -0.30}));
        arm(tidy, "leftArm", 1.3, aim(-1, 6, TRAY_L));
        arm(tidy, "rightArm", 1.3, aim(1, 6, TRAY_R));
        lean(tidy, 0.3, 6);
        lean(tidy, 1.3, 6);
        rest(tidy, 1.8, 0);
        tidy.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.3, 24, 0, 0).rot("head", 1.3, 24, 0, 0).rot("head", 1.8, HEAD_DOWN[0], 0, 0);
        list.add(tidy);
        // while speaking (own controller, head only, so it mixes with any hand animation)
        list.add(new Anim("talk", 1.6, true)
                .rot("head", 0, 0, 0, 0).rot("head", 0.3, -3, 4, 2).rot("head", 0.65, 2, -3, -2).rot("head", 1.0, -2, 2, 1)
                .rot("head", 1.3, 1, -4, -1).rot("head", 1.6, 0, 0, 0));
        Anim shuffle = new Anim("shuffle", 3.6, false).rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.4, 28, 0, 0).rot("head", 3.2, 28, 0, 0).rot("head", 3.6, HEAD_DOWN[0], 0, 0);
        shuffle.rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("leftArm", 0, L_ARM[0], L_ARM[1], 0);
        for (int i = 0; i < 7; i++) {
            double t = 0.4 + i * 0.4;
            double spread = i % 2 == 0 ? 18 : 6;
            shuffle.rot("rightArm", t, -64, spread, -8).rot("leftArm", t, -64, -spread, 8);
        }
        shuffle.rot("rightArm", 3.6, R_ARM[0], R_ARM[1], 0).rot("leftArm", 3.6, L_ARM[0], L_ARM[1], 0);
        list.add(shuffle);
        list.add(new Anim("present", 1.4, false)
                .rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("rightArm", 0.4, -66, -38, 18).rot("rightArm", 1.0, -66, -38, 18).rot("rightArm", 1.4, R_ARM[0], R_ARM[1], 0)
                .rot("leftArm", 0, L_ARM[0], L_ARM[1], 0).rot("leftArm", 0.4, -66, 38, -18).rot("leftArm", 1.0, -66, 38, -18).rot("leftArm", 1.4, L_ARM[0], L_ARM[1], 0)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.5, -4, 0, 0).rot("head", 1.4, HEAD_DOWN[0], 0, 0));
        Anim wave = new Anim("wave", 1.6, false).rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("rightArm", 0.3, -150, 0, 20);
        for (int i = 0; i < 4; i++) wave.rot("rightArm", 0.5 + i * 0.22, -150, 0, i % 2 == 0 ? -10 : 25);
        wave.rot("rightArm", 1.6, R_ARM[0], R_ARM[1], 0).rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.4, -5, 0, -6).rot("head", 1.6, HEAD_DOWN[0], 0, 0);
        list.add(wave);
        list.add(new Anim("bow", 1.6, false)
                .rot("body", 0, 0, 0, 0).rot("body", 0.5, 24, 0, 0).rot("body", 1.0, 24, 0, 0).rot("body", 1.6, 0, 0, 0)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.5, 20, 0, 0).rot("head", 1.6, HEAD_DOWN[0], 0, 0)
                .rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("rightArm", 0.5, -20, 0, 0).rot("rightArm", 1.0, -20, 0, 0).rot("rightArm", 1.6, R_ARM[0], R_ARM[1], 0)
                .rot("leftArm", 0, L_ARM[0], L_ARM[1], 0).rot("leftArm", 0.5, -20, 0, 0).rot("leftArm", 1.0, -20, 0, 0).rot("leftArm", 1.6, L_ARM[0], L_ARM[1], 0));
        list.add(new Anim("nod", 0.9, false)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.2, 26, 0, 0).rot("head", 0.4, 4, 0, 0).rot("head", 0.6, 22, 0, 0).rot("head", 0.9, HEAD_DOWN[0], 0, 0));
        Anim clap = new Anim("clap", 1.6, false).rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("leftArm", 0, L_ARM[0], L_ARM[1], 0);
        for (int i = 0; i < 6; i++) {
            double t = 0.2 + i * 0.2;
            double y = i % 2 == 0 ? 28 : 8;
            clap.rot("rightArm", t, -78, -y, 0).rot("leftArm", t, -78, y, 0);
        }
        clap.rot("rightArm", 1.6, R_ARM[0], R_ARM[1], 0).rot("leftArm", 1.6, L_ARM[0], L_ARM[1], 0)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.4, -6, 0, 0).rot("head", 1.6, HEAD_DOWN[0], 0, 0);
        list.add(clap);
        list.add(new Anim("shake_head", 1.0, false)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.2, HEAD_DOWN[0], 20, 0).rot("head", 0.45, HEAD_DOWN[0], -20, 0).rot("head", 0.7, HEAD_DOWN[0], 14, 0).rot("head", 1.0, HEAD_DOWN[0], 0, 0));
        list.add(new Anim("shrug", 1.3, false)
                .rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("rightArm", 0.35, -52, -20, 28).rot("rightArm", 0.9, -52, -20, 28).rot("rightArm", 1.3, R_ARM[0], R_ARM[1], 0)
                .rot("leftArm", 0, L_ARM[0], L_ARM[1], 0).rot("leftArm", 0.35, -52, 20, -28).rot("leftArm", 0.9, -52, 20, -28).rot("leftArm", 1.3, L_ARM[0], L_ARM[1], 0)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.35, 0, 0, 14).rot("head", 0.9, 0, 0, 14).rot("head", 1.3, HEAD_DOWN[0], 0, 0)
                .pos("body", 0, 0, 0, 0).pos("body", 0.35, 0, 0.6, 0).pos("body", 0.9, 0, 0.6, 0).pos("body", 1.3, 0, 0, 0));
        list.add(new Anim("think", 2.0, false)
                .rot("rightArm", 0, R_ARM[0], R_ARM[1], 0).rot("rightArm", 0.4, -130, 35, 0).rot("rightArm", 1.6, -130, 35, 0).rot("rightArm", 2.0, R_ARM[0], R_ARM[1], 0)
                .rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.4, -4, 0, 8).rot("head", 1.6, -4, 0, 8).rot("head", 2.0, HEAD_DOWN[0], 0, 0));
        list.add(new Anim("sit_down", 0.8, false)
                .pos("root", 0, 0, 0, 0).pos("root", 0.8, 0, -4, 0)
                .rot("rightLeg", 0, 0, 0, 0).rot("rightLeg", 0.8, -88, 6, 0)
                .rot("leftLeg", 0, 0, 0, 0).rot("leftLeg", 0.8, -88, -6, 0));
        list.add(new Anim("stand_up", 0.8, false)
                .pos("root", 0, 0, -4, 0).pos("root", 0.8, 0, 0, 0)
                .rot("rightLeg", 0, -88, 6, 0).rot("rightLeg", 0.8, 0, 0, 0)
                .rot("leftLeg", 0, -88, -6, 0).rot("leftLeg", 0.8, 0, 0, 0)
                .rot("body", 0, 0, 0, 0).rot("body", 0.4, 14, 0, 0).rot("body", 0.8, 0, 0, 0));

        StringBuilder sb = new StringBuilder("{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(list.get(i).json());
        }
        sb.append("\n  },\n  \"geckolib_format_version\": 2\n}\n");
        text(sb.toString(), "animations/entity/dealer.animation.json");
    }

    // =================================================================== seated dealer reach
    // Table-local blocks like TableGeometry: x = dealer's right, z = towards the players, y = up from the floor.
    // The seated dealer (root lowered 4 px) has hips at 0.5, shoulders 10 px above the hips and 5 px to the side,
    // hands 9.5 px down the arm. The body leans forward with a positive X rotation around the hips.
    static final double SEAT_Z = -0.80, HIP_Y = 0.5, SHOULDER_UP = 10 / 16.0, SHOULDER_SIDE = 5 / 16.0, ARM = 9.5 / 16.0;
    /** Palm height when the hand works on the felt (felt at 0.875 + half the arm thickness). */
    static final double HAND_Y = 1.0;
    static final double[] SHOE = {-0.66, -0.40}, DISCARD = {0.66, -0.40};
    static final double[] SHOE_MOUTH = {-0.56, 1.0, -0.27}, PULL = {-0.32, HAND_Y, -0.22},
            HANDOFF_L = {-0.06, HAND_Y, -0.17}, HANDOFF_R = {0.06, HAND_Y, -0.17},
            TRAY_L = {-0.18, HAND_Y, -0.30}, TRAY_R = {0.18, HAND_Y, -0.30},
            GATHER_L = {-0.07, HAND_Y, -0.14}, GATHER_R = {0.07, HAND_Y, -0.14},
            DISCARD_DROP = {0.60, 1.06, -0.34}, HOLE = {-0.07, HAND_Y, -0.02}, OWN_CARDS = {-0.07, HAND_Y, -0.02},
            REST_L = {-0.40, 0.98, -0.36}, REST_R = {0.40, 0.98, -0.36};
    /** Box directions are grouped in 7 buckets from the dealer's left (160 deg) to the right (20 deg). */
    static final int BUCKETS = 7, LAST_LEFT_BUCKET = 2;

    static double bucketAngle(int k) { return 160 - k * (140.0 / (BUCKETS - 1)); }

    static double[] felt(double angleDeg, double dist, double y) {
        double a = Math.toRadians(angleDeg);
        return new double[]{Math.cos(a) * dist, y, CENTER_Z + Math.sin(a) * dist};
    }

    /**
     * Bedrock rotation {x, y, 0} pointing an arm (side +1 = right, -1 = left) at a table-local point for a given
     * body lean. The hand keeps the target height and gets as close as the arm allows horizontally.
     */
    static double[] aim(int side, double lean, double[] target) {
        double l = Math.toRadians(lean);
        double sx = side * SHOULDER_SIDE, sy = HIP_Y + SHOULDER_UP * Math.cos(l), sz = SEAT_Z + SHOULDER_UP * Math.sin(l);
        double dx = target[0] - sx, dz = target[2] - sz, dh = Math.hypot(dx, dz);
        double dy = Math.max(-0.9 * ARM, Math.min(0.45 * ARM, target[1] - sy));
        double r = Math.sqrt(ARM * ARM - dy * dy);
        double ux = dh < 1e-6 ? 0 : dx / dh, uz = dh < 1e-6 ? 1 : dz / dh;
        // world direction -> GeckoLib model space (x right, y up, z backwards), then undo the body lean
        double mx = ux * r / ARM, my = dy / ARM, mz = -uz * r / ARM;
        double ax = mx, ay = my * Math.cos(l) - mz * Math.sin(l), az = my * Math.sin(l) + mz * Math.cos(l);
        double a = -Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, -ay))));
        double s = Math.sin(Math.toRadians(a));
        double b = Math.abs(s) < 1e-4 ? 0 : Math.toDegrees(Math.atan2(-ax / s, az / s));
        return new double[]{round1(a), round1(b), 0};
    }

    static double round1(double v) { return Math.round(v * 10) / 10.0; }

    static Anim arm(Anim a, String bone, double t, double[] r) { return a.rot(bone, t, r[0], r[1], r[2]); }
    static Anim lean(Anim a, double t, double deg) { return a.rot("body", t, deg, 0, 0); }

    /** Relaxed hands on the table edge for the given lean. */
    static Anim rest(Anim a, double t, double leanDeg) {
        if (leanDeg == 0) a.rot("rightArm", t, R_ARM[0], R_ARM[1], R_ARM[2]).rot("leftArm", t, L_ARM[0], L_ARM[1], L_ARM[2]);
        else {
            arm(a, "rightArm", t, aim(1, leanDeg, REST_R));
            arm(a, "leftArm", t, aim(-1, leanDeg, REST_L));
        }
        return lean(a, t, leanDeg);
    }

    /**
     * Deals one card: the left hand slides it out of the shoe; to the dealer's left side it carries the card itself,
     * otherwise it hands it to the right hand in front of the chest. Release at 0.36 s (left) / 0.40 s (right).
     */
    static Anim dealTo(String name, double[] target, boolean leftHand) {
        Anim a = new Anim(name, 0.56, false);
        rest(a, 0, 0);
        arm(a, "leftArm", 0.10, aim(-1, 6, SHOE_MOUTH));
        lean(a, 0.10, 6);
        if (leftHand) {
            arm(a, "leftArm", 0.20, aim(-1, 8, PULL));
            lean(a, 0.20, 8);
            arm(a, "leftArm", 0.36, aim(-1, 22, target));
            arm(a, "rightArm", 0.36, aim(1, 22, REST_R));
            lean(a, 0.36, 22);
        } else {
            arm(a, "leftArm", 0.22, aim(-1, 8, HANDOFF_L));
            arm(a, "rightArm", 0.22, aim(1, 8, HANDOFF_R));
            lean(a, 0.22, 8);
            arm(a, "leftArm", 0.36, aim(-1, 16, REST_L));
            arm(a, "rightArm", 0.40, aim(1, 22, target));
            lean(a, 0.40, 22);
        }
        rest(a, 0.56, 0);
        return a.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.12, 26, 0, 0).rot("head", 0.4, 20, 0, 0).rot("head", 0.56, HEAD_DOWN[0], 0, 0);
    }

    /** Dealer's own card: out of the shoe and laid right in front of the chip tray (release at 0.34 s). */
    static Anim dealSelf() {
        Anim a = new Anim("deal_self", 0.52, false);
        rest(a, 0, 0);
        arm(a, "leftArm", 0.10, aim(-1, 6, SHOE_MOUTH));
        arm(a, "leftArm", 0.20, aim(-1, 8, PULL));
        arm(a, "leftArm", 0.34, aim(-1, 12, OWN_CARDS));
        lean(a, 0.10, 6);
        lean(a, 0.20, 8);
        lean(a, 0.34, 12);
        rest(a, 0.52, 0);
        return a.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.12, 26, 0, 0).rot("head", 0.34, 28, 0, 0).rot("head", 0.52, HEAD_DOWN[0], 0, 0);
    }

    /** Pays or takes the chips of one box: hand at the tray (0.06 s), at the bet (0.28 s), back at the tray (0.52 s). */
    static Anim reach(String name, double[] spot, boolean leftHand) {
        Anim a = new Anim(name, 0.6, false);
        int side = leftHand ? -1 : 1;
        String bone = leftHand ? "leftArm" : "rightArm";
        double[] tray = leftHand ? TRAY_L : TRAY_R;
        rest(a, 0, 0);
        arm(a, bone, 0.06, aim(side, 4, tray));
        arm(a, bone, 0.28, aim(side, 22, spot));
        arm(a, bone, 0.52, aim(side, 4, tray));
        lean(a, 0.06, 4);
        lean(a, 0.28, 22);
        lean(a, 0.52, 4);
        rest(a, 0.6, 0);
        return a.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.28, 22, 0, 0).rot("head", 0.6, HEAD_DOWN[0], 0, 0);
    }

    /** Open-palm gesture towards a player's chair ("your decision, please"). */
    static Anim point(String name, double[] seat, boolean leftHand) {
        Anim a = new Anim(name, 1.1, false);
        int side = leftHand ? -1 : 1;
        String bone = leftHand ? "leftArm" : "rightArm";
        double[] r = aim(side, 4, seat);
        rest(a, 0, 0);
        a.rot(bone, 0.25, r[0], r[1], side * -12).rot(bone, 0.8, r[0], r[1], side * -12);
        lean(a, 0.25, 4);
        lean(a, 0.8, 4);
        rest(a, 1.1, 0);
        return a.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.25, 0, 0, 0).rot("head", 0.8, 0, 0, 0).rot("head", 1.1, HEAD_DOWN[0], 0, 0);
    }

    /**
     * End of the round: both hands sweep the felt from the sides to the middle (cards are picked up as the hand
     * passes them, 0.25..0.85 s), meet in front of the dealer (1.1 s), the right hand drops the pile in the discard
     * holder (1.7 s).
     */
    static Anim collect() {
        Anim a = new Anim("collect", 2.2, false);
        rest(a, 0, 0);
        double[][] sweep = {{0.25, 20, 160}, {0.55, 55, 125}, {0.85, 86, 94}};
        for (double[] k : sweep) {
            arm(a, "rightArm", k[0], aim(1, 20, felt(k[1], 1.25, HAND_Y)));
            arm(a, "leftArm", k[0], aim(-1, 20, felt(k[2], 1.25, HAND_Y)));
            lean(a, k[0], 20);
        }
        arm(a, "rightArm", 1.1, aim(1, 8, GATHER_R));
        arm(a, "leftArm", 1.1, aim(-1, 8, GATHER_L));
        lean(a, 1.1, 8);
        arm(a, "rightArm", 1.3, aim(1, 6, GATHER_R));
        arm(a, "leftArm", 1.4, aim(-1, 6, REST_L));
        arm(a, "rightArm", 1.68, aim(1, 6, DISCARD_DROP));
        arm(a, "rightArm", 1.8, aim(1, 6, DISCARD_DROP));
        lean(a, 1.3, 6);
        lean(a, 1.8, 6);
        rest(a, 2.2, 0);
        return a.rot("head", 0, HEAD_DOWN[0], 0, 0).rot("head", 0.3, 24, 0, 0).rot("head", 1.2, 24, 0, 0).rot("head", 2.2, HEAD_DOWN[0], 0, 0);
    }

    static Map<String, double[]> uniform(double u, double v) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (String k : List.of("north", "east", "south", "west", "up", "down")) m.put(k, new double[]{u, v, 1, 1});
        return m;
    }

    // =================================================================== skins

    record Look(String id, boolean female, Color skin, Color hair, Color top, Color vest, Color accent, Color bottom,
                Color shoes, Color eyes, boolean longHair, boolean beard, boolean sleeves) {}

    static void skins() throws IOException {
        List<Look> looks = List.of(
                new Look("anna", true, new Color(0xF1C7A5), new Color(0x3B2314), new Color(0x121212), null, new Color(0xB01E3A), new Color(0x121212), new Color(0x0A0A0A), new Color(0x4A6A3A), true, false, false),
                new Look("maria", true, new Color(0xF4CFB2), new Color(0xE6C373), new Color(0xF4F4F4), new Color(0x161616), new Color(0x7A3FA0), new Color(0x161616), new Color(0x0A0A0A), new Color(0x3A6BA0), true, false, true),
                new Look("elena", true, new Color(0xE8B996), new Color(0x8A2F12), new Color(0x0F5A3C), null, new Color(0xD4AF37), new Color(0x0F5A3C), new Color(0x101010), new Color(0x5A4020), true, false, false),
                new Look("maxim", false, new Color(0xEAC09C), new Color(0x231812), new Color(0xF5F5F5), new Color(0x141414), new Color(0x0A0A0A), new Color(0x161616), new Color(0x0A0A0A), new Color(0x3A2A1A), false, false, true),
                new Look("artem", false, new Color(0xF0C6A2), new Color(0x6B4426), new Color(0xF5F5F5), new Color(0x5C1420), new Color(0x0A0A0A), new Color(0x1A1A1A), new Color(0x0A0A0A), new Color(0x3A5A7A), false, false, true),
                new Look("david", false, new Color(0xB07D56), new Color(0x0E0B0A), new Color(0xF2F2F2), new Color(0x4A4D52), new Color(0xA01818), new Color(0x1A1A1A), new Color(0x0A0A0A), new Color(0x2A1A0A), false, true, true)
        );
        for (Look look : looks) png(skin(look), "textures/entity/dealer/" + look.id + ".png");
    }

    static BufferedImage SKIN;

    static void px(int x, int y, Color c) {
        if (c != null) SKIN.setRGB(x, y, c.getRGB());
    }

    static void rect(int x, int y, int w, int h, Color c) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, c);
    }

    static void noise(int x, int y, int w, int h, Color c, Random r, double amount) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, shade(c, 1 + (r.nextDouble() - 0.5) * amount));
    }

    /** Fills the six faces of a box-UV cuboid (u, v, width, height, depth). */
    static void cuboid(int u, int v, int w, int h, int d, Color c, Random r) {
        noise(u + d, v, w, d, c, r, 0.06);          // top
        noise(u + d + w, v, w, d, shade(c, 0.85), r, 0.06); // bottom
        noise(u, v + d, d, h, shade(c, 0.9), r, 0.06);      // right side
        noise(u + d, v + d, w, h, c, r, 0.06);              // front
        noise(u + d + w, v + d, d, h, shade(c, 0.9), r, 0.06); // left side
        noise(u + 2 * d + w, v + d, w, h, shade(c, 0.82), r, 0.06); // back
    }

    static BufferedImage skin(Look l) {
        SKIN = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(l.id.hashCode());
        int aw = l.female ? 3 : 4;
        // head
        cuboid(0, 0, 8, 8, 8, l.skin, r);
        // hair: top, back, sides upper part, fringe
        noise(8, 0, 8, 8, l.hair, r, 0.12);
        noise(24, 8, 8, 8, l.hair, r, 0.12);
        noise(0, 8, 8, l.longHair ? 8 : 3, l.hair, r, 0.12);
        noise(16, 8, 8, l.longHair ? 8 : 3, l.hair, r, 0.12);
        noise(8, 8, 8, 2, l.hair, r, 0.12);
        if (l.longHair) {
            px(8, 10, l.hair); px(15, 10, l.hair); px(8, 11, l.hair); px(15, 11, l.hair);
            // face skin back where the long hair overlapped the cheeks
        }
        // face: brows, eyes, nose, mouth (front face at 8..15, 8..15)
        Color brow = shade(l.hair, 0.8);
        px(9, 11, brow); px(10, 11, brow); px(13, 11, brow); px(14, 11, brow);
        px(9, 12, Color.WHITE); px(10, 12, l.eyes); px(13, 12, l.eyes); px(14, 12, Color.WHITE);
        px(11, 13, shade(l.skin, 0.88)); px(12, 13, shade(l.skin, 0.88));
        Color mouth = l.female ? new Color(0xB0303A) : shade(l.skin, 0.7);
        px(10, 14, mouth); px(11, 14, mouth); px(12, 14, mouth); px(13, 14, mouth);
        if (l.beard) {
            Color beard = shade(l.hair, 1.1);
            rect(8, 14, 2, 2, beard); rect(14, 14, 2, 2, beard); rect(9, 15, 6, 1, beard);
            px(10, 14, mouth); px(13, 14, mouth);
        }
        if (l.female) {
            px(9, 13, shade(l.skin, 0.95));
            px(14, 13, new Color(0xE8A0A0));
            px(9, 13, new Color(0xE8A0A0));
        }
        // hat layer: hair volume for long hair (sides/back lower rows)
        if (l.longHair) {
            noise(32 + 8, 0, 8, 8, l.hair, r, 0.12);
            noise(32, 8 + 4, 8, 4, l.hair, r, 0.14);
            noise(32 + 16, 8 + 4, 8, 4, l.hair, r, 0.14);
            noise(32 + 24, 8, 8, 8, l.hair, r, 0.14);
        }
        // body
        Color torso = l.vest != null ? l.vest : l.top;
        cuboid(16, 16, 8, 12, 4, torso, r);
        if (l.vest != null) {
            // shirt visible in the middle of the vest, bow tie, buttons
            noise(22, 20, 4, 12, l.top, r, 0.04);
            px(21, 20, l.top); px(26, 20, l.top);
            Color tie = l.accent;
            rect(22, 20, 4, 1, tie);
            px(23, 21, tie); px(24, 21, tie);
            for (int y = 23; y < 31; y += 2) px(23, y, shade(l.top, 0.75));
            rect(20, 30, 8, 2, shade(l.vest, 0.8));
            px(20, 24, shade(l.vest, 1.3)); px(27, 24, shade(l.vest, 1.3));
        } else {
            // dress with neckline and accent necklace
            rect(22, 20, 4, 2, l.skin);
            px(23, 22, l.skin); px(24, 22, l.skin);
            px(22, 21, l.accent); px(25, 21, l.accent); px(23, 22, l.accent);
            rect(20, 27, 8, 1, shade(l.top, 1.25));
        }
        // arms
        Color sleeve = l.sleeves ? l.top : l.skin;
        armUv(40, 16, aw, sleeve, l.skin, r);
        armUv(32, 48, aw, sleeve, l.skin, r);
        // legs
        Color legs = l.female ? l.skin : l.bottom;
        cuboid(0, 16, 4, 12, 4, legs, r);
        cuboid(16, 48, 4, 12, 4, legs, r);
        if (l.female) {
            // skirt down to the knees, stockings
            for (int[] leg : new int[][]{{0, 16}, {16, 48}}) {
                noise(leg[0], leg[1] + 4, 16, 5, l.bottom, r, 0.06);
                noise(leg[0] + 4, leg[1], 4, 4, l.bottom, r, 0.06);
                noise(leg[0], leg[1] + 9, 16, 5, shade(l.skin, 0.85), r, 0.05);
            }
        }
        for (int[] leg : new int[][]{{0, 16}, {16, 48}}) {
            noise(leg[0], leg[1] + 14, 16, 2, l.shoes, r, 0.1);
            noise(leg[0] + 8, leg[1], 4, 4, l.shoes, r, 0.1);
        }
        return SKIN;
    }

    static void armUv(int u, int v, int aw, Color sleeve, Color skin, Random r) {
        int d = 4;
        noise(u + d, v, aw, d, sleeve, r, 0.06);
        noise(u + d + aw, v, aw, d, skin, r, 0.06);
        noise(u, v + d, d, 12, shade(sleeve, 0.9), r, 0.06);
        noise(u + d, v + d, aw, 12, sleeve, r, 0.06);
        noise(u + d + aw, v + d, d, 12, shade(sleeve, 0.9), r, 0.06);
        noise(u + 2 * d + aw, v + d, aw, 12, shade(sleeve, 0.82), r, 0.06);
        // hands: last 3 rows
        noise(u, v + d + 9, 2 * d + 2 * aw, 3, skin, r, 0.05);
        // white cuff line
        if (!sleeve.equals(skin)) noise(u, v + d + 8, 2 * d + 2 * aw, 1, shade(sleeve, 1.1), r, 0.02);
    }

    // =================================================================== icons

    static void icons() throws IOException {
        BufferedImage item = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(item);
        g.setColor(new Color(0x2B1A10));
        g.fill(new Arc2D.Double(1, -14, 30, 40, 180, 180, Arc2D.PIE));
        g.setColor(new Color(0x0D5E30));
        g.fill(new Arc2D.Double(3, -11, 26, 34, 180, 180, Arc2D.PIE));
        g.setColor(new Color(0xF4D35E));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Arc2D.Double(8, -4, 16, 20, 200, 140, Arc2D.OPEN));
        g.setColor(Color.WHITE);
        g.fillRect(10, 12, 5, 7);
        g.fillRect(17, 12, 5, 7);
        g.setColor(new Color(0xC8102E));
        g.fillRect(11, 13, 1, 2);
        g.setColor(Color.BLACK);
        g.fillRect(18, 13, 1, 2);
        g.dispose();
        png(item, "textures/item/blackjack_table.png");

        BufferedImage icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        g = g2(icon);
        g.drawImage(chip(CHIP_COLORS[3], "MV"), 8, 40, 80, 80, null);
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(-12), 64, 64);
        g.drawImage(card(0, 0), 52, 6, 52, 72, null);
        g.rotate(Math.toRadians(18), 64, 64);
        g.drawImage(card(1, 12), 68, 18, 52, 72, null);
        g.setTransform(t);
        g.dispose();
        png(icon, "icon.png");
    }
}
