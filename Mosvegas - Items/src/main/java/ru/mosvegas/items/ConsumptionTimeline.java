package ru.mosvegas.items;

import net.minecraft.util.math.MathHelper;

/**
 * One use is a fixed choreography expressed as fractions of its duration. The server
 * (sounds, exhaled smoke) and every client (poses of any player) read the same phases,
 * so remote players see the motion the user performs.
 */
public final class ConsumptionTimeline {
    // Smoking: raise to the lips, inhale, lower the hand, exhale.
    public static final float SMOKE_RAISED = 0.17f, SMOKE_LOWER = 0.58f, SMOKE_LOWERED = 0.75f;
    public static final float INHALE_START = 0.16f, INHALE_FULL = 0.26f, INHALE_END = 0.5f, INHALE_STOP = 0.6f;
    public static final float EXHALE_START = 0.68f, EXHALE_END = 0.92f;
    // Drinking or eating: raise, tip further while drinking, lower before the use ends.
    public static final float DRINK_RAISED = 0.22f, DRINK_LOWER = 0.8f;
    // Opening: lift the can in front of the chest, the other hand pulls the tab.
    public static final float TAB_POP = 0.55f, OPENED_TAB_DEGREES = 28;

    private ConsumptionTimeline() {}

    public static float reach(String action, boolean opening, float elapsed, float total) {
        float f = fraction(elapsed, total);
        if (opening) return ramp(f, 0, 0.3f) * (1 - ramp(f, 0.78f, 1));
        return switch (action) {
            case "smoke" -> ramp(f, 0, SMOKE_RAISED) * (1 - ramp(f, SMOKE_LOWER, SMOKE_LOWERED));
            case "drink", "eat" -> ramp(f, 0, DRINK_RAISED) * (1 - ramp(f, DRINK_LOWER, 1));
            default -> 0;
        };
    }

    /** 0..1 how far the drink is tipped while at the mouth. */
    public static float tilt(float elapsed, float total) {
        return ramp(fraction(elapsed, total), DRINK_RAISED * 0.8f, DRINK_LOWER);
    }

    /** 0..1 strength of the draw; the ember glows and the paper burns faster. */
    public static float inhale(float elapsed, float total) {
        float f = fraction(elapsed, total);
        return ramp(f, INHALE_START, INHALE_FULL) * (1 - ramp(f, INHALE_END, INHALE_STOP));
    }

    public static boolean exhaling(int elapsed, int total) {
        float f = fraction(elapsed, total);
        return f >= EXHALE_START && f <= EXHALE_END;
    }

    /** The free hand reaching for the pull tab while opening. */
    public static float otherHandReach(float elapsed, float total) {
        float f = fraction(elapsed, total);
        return ramp(f, 0.18f, 0.45f) * (1 - ramp(f, 0.62f, 0.86f));
    }

    public static float tabDegrees(boolean opened, boolean opening, float elapsed, float total) {
        if (!opening) return opened ? OPENED_TAB_DEGREES : 0;
        float f = fraction(elapsed, total);
        return MathHelper.lerp(ramp(f, TAB_POP - 0.05f, TAB_POP + 0.05f), 0, 80)
                + MathHelper.lerp(ramp(f, TAB_POP + 0.08f, 0.8f), 0, OPENED_TAB_DEGREES - 80);
    }

    public static int tick(float fraction, int total) {
        return Math.round(fraction * total);
    }

    public static float ramp(float value, float from, float to) {
        float t = MathHelper.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float fraction(float elapsed, float total) {
        return MathHelper.clamp(elapsed / Math.max(1, total), 0, 1);
    }
}
