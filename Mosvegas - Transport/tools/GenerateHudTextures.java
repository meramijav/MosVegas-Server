import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GenerateHudTextures {
    static final int CELL = 9;

    static final String[][] DIGITS = {
            {".##.", "#..#", "#.##", "##.#", "#..#", ".##."},
            {".#.", "##.", ".#.", ".#.", ".#.", "###"},
            {".##.", "#..#", "..#.", ".#..", "#...", "####"},
            {".##.", "#..#", "..#.", "...#", "#..#", ".##."},
            {"..##", ".#.#", "#..#", "####", "...#", "...#"},
            {"####", "#...", "###.", "...#", "#..#", ".##."},
            {".##.", "#...", "###.", "#..#", "#..#", ".##."},
            {"####", "...#", "..#.", "..#.", ".#..", ".#.."},
            {".##.", "#..#", ".##.", "#..#", "#..#", ".##."},
            {".##.", "#..#", "#..#", ".###", "...#", ".##."}
    };

    static final String[][] ICONS = {
            {
                    "....###..",
                    "##.#...#.",
                    "...#....#",
                    "##.#....#",
                    "...#....#",
                    "##.#...#.",
                    "....###.."
            },
            {
                    "####...",
                    "#..#...",
                    "#..##..",
                    "####.#.",
                    "####.#.",
                    "####.#.",
                    "####.##",
                    "####..."
            },
            {
                    "..####...",
                    ".#...##..",
                    "#########",
                    "#########",
                    "#########",
                    ".##...##."
            },
            {
                    "..#####..",
                    ".#.....#.",
                    "#..###..#",
                    "#..#..#.#",
                    "#..###..#",
                    "#..#....#",
                    "#..#....#",
                    ".#.....#.",
                    "..#####.."
            },
            {
                    "..###..",
                    ".#...#.",
                    ".#...#.",
                    "#######",
                    "#######",
                    "###.###",
                    "###.###",
                    "#######",
                    "#######"
            },
            {
                    "...##..",
                    "...#.#.",
                    "...#..#",
                    "...#...",
                    "...#...",
                    ".###...",
                    "####...",
                    "####...",
                    ".##...."
            },
            {
                    "..#####..",
                    ".#.....#.",
                    "#.....#.#",
                    "#....#..#",
                    "#...#...#",
                    "#..#....#",
                    "#########"
            },
            {
                    "..####...",
                    "...##....",
                    ".#######.",
                    "##.....#.",
                    "#......##",
                    "##.....#.",
                    ".#######."
            },
            {
                    "#...#...#",
                    ".#.....#.",
                    "...###...",
                    "..#####..",
                    "..#####..",
                    "..#####..",
                    ".#######.",
                    ".#######."
            },
            {
                    "...#...",
                    "..##...",
                    ".######",
                    "#######",
                    ".######",
                    "..##...",
                    "...#..."
            },
            {
                    "...#...",
                    "...##..",
                    "######.",
                    "#######",
                    "######.",
                    "...##..",
                    "...#..."
            },
            {
                    ".###.....",
                    "#...#....",
                    "#...#####",
                    "#...#.#.#",
                    ".###..#.#"
            }
    };

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "sliminterfacefabric/src/main/resources/assets/slimvehicle/textures/gui");
        Files.createDirectories(out);
        ImageIO.write(digits(), "png", out.resolve("dial_digits.png").toFile());
        ImageIO.write(icons(), "png", out.resolve("hud_icons.png").toFile());
    }

    static BufferedImage digits() {
        BufferedImage image = new BufferedImage(DIGITS.length * 5, 6, BufferedImage.TYPE_INT_ARGB);
        for (int digit = 0; digit < DIGITS.length; digit++) stamp(image, DIGITS[digit], digit * 5, 0);
        return image;
    }

    static BufferedImage icons() {
        BufferedImage image = new BufferedImage(ICONS.length * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int icon = 0; icon < ICONS.length; icon++) {
            String[] rows = ICONS[icon];
            int left = icon * CELL + (CELL - rows[0].length()) / 2;
            int top = (CELL - rows.length) / 2;
            stamp(image, rows, left, top);
        }
        return image;
    }

    static void stamp(BufferedImage image, String[] rows, int left, int top) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '#') image.setRGB(left + x, top + y, 0xffffffff);
            }
        }
    }
}
