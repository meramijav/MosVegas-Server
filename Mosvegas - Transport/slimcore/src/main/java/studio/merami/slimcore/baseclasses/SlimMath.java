package studio.merami.slimcore.baseclasses;

public final class SlimMath {
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = (float) (Math.PI * 2);
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
    }

    private SlimMath() {}

    public static float sin(float angle) {
        return SIN[(int) (angle * 10430.378F) & 65535];
    }

    public static float cos(float angle) {
        return SIN[(int) (angle * 10430.378F + 16384.0F) & 65535];
    }

    public static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float lerp(float t, float from, float to) {
        return from + t * (to - from);
    }

    public static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    public static float approach(float value, float target, float step) {
        step = Math.abs(step);
        return value < target ? clamp(value + step, value, target) : clamp(value - step, target, value);
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public static float rotLerp(float t, float from, float to) {
        return from + t * wrapDegrees(to - from);
    }

    public static double square(double value) {
        return value * value;
    }
}
