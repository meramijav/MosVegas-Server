import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class GenerateParticleTextures {
    private static final int[] FIRE = {0x8f2a08, 0xc8460c, 0xee7414, 0xfba52a, 0xffd04a, 0xfff2b0};

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "sliminterfacefabric/src/main/resources/assets/slimvehicle/textures/particle");
        Files.createDirectories(out);
        for (int frame = 0; frame < 7; frame++) write(out, "fire_" + frame, fire(frame));
        for (int frame = 0; frame < 5; frame++) write(out, "backfire_" + frame, burst(frame));
        for (int frame = 0; frame < 12; frame++) write(out, "smoke_" + frame, smoke(frame));
        for (int variant = 0; variant < 5; variant++) write(out, "smoke_cluster_" + variant, cluster(variant));
        for (int variant = 0; variant < 4; variant++) write(out, "dirt_" + variant, dirt(variant));
        for (int variant = 0; variant < 3; variant++) write(out, "dust_" + variant, dust(variant));
        for (int variant = 0; variant < 2; variant++) write(out, "snow_" + variant, snow(variant));
    }

    private static void write(Path folder, String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xffffff);
    }

    private static int gray(int alpha, int level) {
        level = Math.max(0, Math.min(255, level));
        return argb(alpha, level << 16 | level << 8 | level);
    }

    private static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xffffff) / (double) 0xffffff;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double noise(double x, double y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double a = hash(ix, iy, seed) + (hash(ix + 1, iy, seed) - hash(ix, iy, seed)) * fx;
        double b = hash(ix, iy + 1, seed) + (hash(ix + 1, iy + 1, seed) - hash(ix, iy + 1, seed)) * fx;
        return a + (b - a) * fy;
    }

    private static double fbm(double x, double y, int seed) {
        return .55 * noise(x, y, seed) + .3 * noise(x * 2.1, y * 2.1, seed + 7) + .15 * noise(x * 4.3, y * 4.3, seed + 13);
    }

    private static BufferedImage fire(int frame) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        double t = frame / 7.0 * Math.PI * 2;
        double shrink = frame >= 5 ? 1 - (frame - 4) * .07 : 1;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double u = (x + .5) / 16 - .5;
                double v = (1 - (y + .5) / 16) / shrink;
                double ragged = fbm(x * .55, y * .5 + frame * 1.9, 3);
                double flicker = fbm(x * .95, y * .8 + frame * 2.7, 9);
                double sway = .06 * Math.sin(t + v * 5) * v;
                double base = 1 - Math.hypot(u / .3, (v - .2) / .22);
                double width = .27 * Math.pow(Math.max(0, 1 - v), .55) * (.65 + .7 * ragged);
                double tongue = (1 - Math.abs(u - sway) / Math.max(.001, width)) * (1 - .3 * v);
                double heat = Math.max(base, tongue) + .4 * (flicker - .5) - .12 * v;
                if (v < .07) heat -= .25;
                if (heat < .1 || v > 1) continue;
                double core = heat + .25 * (1 - v);
                int level = core > .95 ? 5 : core > .75 ? 4 : core > .55 ? 3 : core > .38 ? 2 : core > .24 ? 1 : 0;
                image.setRGB(x, y, argb(level == 0 ? 200 : 255, FIRE[level]));
            }
        }
        return image;
    }

    private static BufferedImage burst(int frame) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        double[] radius = {4.2, 5.6, 6.6, 7.2, 7.4};
        double[] heat = {1.25, 1.05, .82, .6, .38};
        double[] holes = {0, 0, .12, .38, .62};
        double spikes = frame < 2 ? .9 : .35;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5;
                double dy = y - 7.5;
                double angle = Math.atan2(dy, dx);
                double edge = radius[frame] * (1 + .16 * spikes * Math.sin(angle * 7 + frame * 1.3) + .3 * (fbm(Math.cos(angle) * 2.2 + frame, Math.sin(angle) * 2.2, 17) - .5));
                double d = Math.hypot(dx, dy) / Math.max(.5, edge);
                if (d >= 1) continue;
                double grain = fbm(x * .6 + frame * 3.1, y * .6, 23);
                if (grain < holes[frame] * (.6 + .8 * d)) continue;
                double core = heat[frame] * (1 - d * d) + .25 * (grain - .5);
                int level = core > 1 ? 5 : core > .78 ? 4 : core > .56 ? 3 : core > .36 ? 2 : core > .18 ? 1 : 0;
                image.setRGB(x, y, argb(level == 0 ? 170 : 255, FIRE[level]));
            }
        }
        return image;
    }

    private static double puffs(double x, double y, double[][] blobs, double scale) {
        double field = 0;
        for (double[] blob : blobs) {
            double dx = x - blob[0];
            double dy = y - blob[1];
            double r = blob[2] * scale;
            field += Math.exp(-(dx * dx + dy * dy) / (r * r));
        }
        return field;
    }

    private static double[][] blobs(int seed, int count, double spread, double size, double cx, double cy) {
        Random random = new Random(seed);
        double[][] blobs = new double[count][];
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = i == 0 ? 0 : spread * (.35 + .65 * random.nextDouble());
            blobs[i] = new double[]{cx + Math.cos(a) * d, cy + Math.sin(a) * d * .8, size * (.65 + .5 * random.nextDouble())};
        }
        return blobs;
    }

    private static int shade(double field, double lightX, double lightY, double grain, int alpha) {
        double light = .6 + .4 * Math.max(-1, Math.min(1, lightX + lightY)) + .1 * (grain - .5);
        int level = 186 + (int) Math.round(Math.max(0, Math.min(1, light)) * 3) * 23;
        return gray(alpha, level);
    }

    private static BufferedImage smoke(int frame) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        double[][] blobs = blobs(41, 7, 3.6, 2.5, 7.5, 8.2);
        double grow = frame <= 6 ? .45 + frame * .16 : 1.41 + (frame - 6) * .03;
        double erosion = frame <= 6 ? 0 : 1.9 * Math.pow((frame - 6) / 5.0, .8);
        double[][] field = new double[18][18];
        for (int y = -1; y < 17; y++) {
            for (int x = -1; x < 17; x++) {
                double px = 7.5 + (x - 7.5) / grow;
                double py = 8.2 + (y - 8.2) / grow;
                field[y + 1][x + 1] = puffs(px, py, blobs, 1) + .35 * (fbm(px * .5, py * .5, 61) - .5) - erosion * (.7 + 1.5 * fbm(x * .55, y * .55, 23 + frame));
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double f = field[y + 1][x + 1];
                if (f < .5) continue;
                double lx = (field[y + 1][x + 2] - field[y + 1][x]) * 1.4;
                double ly = (field[y + 2][x + 1] - field[y][x + 1]) * 1.4;
                image.setRGB(x, y, shade(f, lx, ly, hash(x, y, frame), f > .72 ? 255 : 175));
            }
        }
        return image;
    }

    private static BufferedImage cluster(int variant) {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double[][] blobs = blobs(97 + variant * 31, 12, 9.5, 4.1, 15.5, 16.5);
        double[][] field = new double[size + 2][size + 2];
        for (int y = -1; y <= size; y++) {
            for (int x = -1; x <= size; x++) field[y + 1][x + 1] = puffs(x, y, blobs, 1) + .55 * (fbm(x * .25, y * .25, 5 + variant) - .5);
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double f = field[y + 1][x + 1];
                if (f < .35) continue;
                double density = Math.min(1, (f - .35) / 1.1);
                double lx = (field[y + 1][x + 2] - field[y + 1][x]) * 1.6;
                double ly = (field[y + 2][x + 1] - field[y][x + 1]) * 1.6;
                int alpha = (int) Math.round(Math.ceil(density * 5) / 5 * 225);
                image.setRGB(x, y, shade(f, lx, ly, hash(x, y, variant + 40), alpha));
            }
        }
        return image;
    }

    private static BufferedImage dirt(int variant) {
        int[] palette = {0x4a3221, 0x5e4029, 0x75553a, 0x8a6a4a};
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(300 + variant);
        int specks = 14 + random.nextInt(7);
        for (int i = 0; i < specks; i++) {
            int x = 1 + random.nextInt(14);
            int y = 1 + random.nextInt(14);
            int rgb = palette[random.nextInt(palette.length)];
            image.setRGB(x, y, argb(255, rgb));
            if (random.nextInt(4) == 0) image.setRGB(x + (random.nextBoolean() ? 1 : -1), y, argb(255, palette[Math.max(0, random.nextInt(palette.length) - 1)]));
            if (random.nextInt(5) == 0) image.setRGB(x, y + 1, argb(255, palette[0]));
        }
        return image;
    }

    private static BufferedImage dust(int variant) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(500 + variant);
        double cx = 7.5 + random.nextDouble() - .5;
        double cy = 7.5 + random.nextDouble() - .5;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - cx, y - cy) / 6.5 + .35 * (fbm(x * .45, y * .45, 70 + variant) - .5);
                if (d >= 1) continue;
                int alpha = (int) Math.round(Math.ceil((1 - d) * (1 - d) * 6) / 6 * 110);
                if (alpha < 12) continue;
                image.setRGB(x, y, argb(alpha, 0xd9c9a0));
            }
        }
        for (int i = 0; i < 9; i++) {
            int x = 2 + random.nextInt(12);
            int y = 2 + random.nextInt(12);
            image.setRGB(x, y, argb(210, random.nextBoolean() ? 0xf0e6c4 : 0xe2d2a2));
        }
        return image;
    }

    private static BufferedImage snow(int variant) {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(700 + variant);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x - 15.5, y - 15.5) / 15.5;
                int haze = (int) Math.round(Math.ceil((1 - d) * (1 - d) * 3) / 3 * 26);
                if (d < 1 && haze > 0) image.setRGB(x, y, argb(haze, 0xeef4ff));
            }
        }
        for (int i = 0; i < 46 + variant * 8; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * 14;
            int x = (int) Math.round(15.5 + Math.cos(a) * r);
            int y = (int) Math.round(15.5 + Math.sin(a) * r);
            image.setRGB(x, y, argb(random.nextInt(3) == 0 ? 190 : 255, random.nextInt(4) == 0 ? 0xdde8f8 : 0xffffff));
            if (random.nextInt(6) == 0 && x + 1 < size) image.setRGB(x + 1, y, argb(200, 0xffffff));
        }
        return image;
    }
}
