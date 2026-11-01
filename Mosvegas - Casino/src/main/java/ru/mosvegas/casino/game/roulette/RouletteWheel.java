package ru.mosvegas.casino.game.roulette;

import java.util.Random;

/** European single-zero wheel (the layout of Evolution / live roulette tables). */
public final class RouletteWheel {
    /** Pocket order clockwise starting at zero; used by the wheel model and the ball animation. */
    public static final int[] EUROPEAN_ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };
    private static final boolean[] RED = new boolean[37];

    static {
        int[] reds = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
        for (int r : reds) RED[r] = true;
    }

    private RouletteWheel() {}

    public static boolean isRed(int number) { return number > 0 && number <= 36 && RED[number]; }
    public static boolean isBlack(int number) { return number > 0 && number <= 36 && !RED[number]; }

    /** Index of the pocket on the wheel, for rotating the model to the winning number. */
    public static int pocketIndex(int number) {
        for (int i = 0; i < EUROPEAN_ORDER.length; i++) if (EUROPEAN_ORDER[i] == number) return i;
        return -1;
    }

    public static int spin(Random random) { return random.nextInt(37); }
}
