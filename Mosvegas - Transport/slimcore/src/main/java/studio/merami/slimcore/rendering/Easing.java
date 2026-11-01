package studio.merami.slimcore.rendering;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

public final class Easing {
    private static final Map<String, Easing> BY_NAME = new HashMap<>();
    public static final Easing LINEAR = register(new Easing("linear", arg -> t -> t), "none");

    static {
        register(new Easing("step", Easing::step));
        register(new Easing("easeinsine", arg -> Easing::sine));
        register(new Easing("easeoutsine", arg -> out(Easing::sine)));
        register(new Easing("easeinoutsine", arg -> inOut(Easing::sine)));
        register(new Easing("easeinquad", arg -> t -> t * t));
        register(new Easing("easeoutquad", arg -> out(t -> t * t)));
        register(new Easing("easeinoutquad", arg -> inOut(t -> t * t)));
        register(new Easing("easeincubic", arg -> t -> t * t * t));
        register(new Easing("easeoutcubic", arg -> out(t -> t * t * t)));
        register(new Easing("easeinoutcubic", arg -> inOut(t -> t * t * t)));
        register(new Easing("easeinquart", arg -> pow(4)));
        register(new Easing("easeoutquart", arg -> out(pow(4))));
        register(new Easing("easeinoutquart", arg -> inOut(pow(4))));
        register(new Easing("easeinquint", arg -> pow(4)));
        register(new Easing("easeoutquint", arg -> out(pow(5))));
        register(new Easing("easeinoutquint", arg -> inOut(pow(5))));
        register(new Easing("easeinexpo", arg -> Easing::exp));
        register(new Easing("easeoutexpo", arg -> out(Easing::exp)));
        register(new Easing("easeinoutexpo", arg -> inOut(Easing::exp)));
        register(new Easing("easeincirc", arg -> Easing::circle));
        register(new Easing("easeoutcirc", arg -> out(Easing::circle)));
        register(new Easing("easeinoutcirc", arg -> inOut(Easing::circle)));
        register(new Easing("easeinback", Easing::back));
        register(new Easing("easeoutback", arg -> out(back(arg))));
        register(new Easing("easeinoutback", arg -> inOut(back(arg))));
        register(new Easing("easeinelastic", Easing::elastic));
        register(new Easing("easeoutelastic", arg -> out(elastic(arg))));
        register(new Easing("easeinoutelastic", arg -> inOut(elastic(arg))));
        register(new Easing("easeinbounce", Easing::bounce));
        register(new Easing("easeoutbounce", arg -> out(bounce(arg))));
        register(new Easing("easeinoutbounce", arg -> inOut(bounce(arg))));
        register(new Easing("catmullrom", arg -> inOut(Easing::catmullRom)));
    }

    public final String name;
    private final Function<Double, DoubleUnaryOperator> curve;

    private Easing(String name, Function<Double, DoubleUnaryOperator> curve) {
        this.name = name;
        this.curve = curve;
    }

    private static Easing register(Easing easing, String... aliases) {
        BY_NAME.put(easing.name, easing);
        for (String alias : aliases) BY_NAME.put(alias, easing);
        return easing;
    }

    public static Easing byName(String name) {
        return name == null ? LINEAR : BY_NAME.getOrDefault(name.toLowerCase(Locale.ROOT), LINEAR);
    }

    public double apply(Double argument, double t) {
        return curve.apply(argument).applyAsDouble(t);
    }

    public double interpolate(double tick, double length, double from, double to, Double argument) {
        if (tick >= length) return (float) to;
        return from + (to - from) * apply(argument, tick / length);
    }

    private static DoubleUnaryOperator out(DoubleUnaryOperator curve) {
        return t -> 1 - curve.applyAsDouble(1 - t);
    }

    private static DoubleUnaryOperator inOut(DoubleUnaryOperator curve) {
        return t -> t < .5 ? curve.applyAsDouble(t * 2) / 2 : 1 - curve.applyAsDouble((1 - t) * 2) / 2;
    }

    private static DoubleUnaryOperator pow(double power) {
        return t -> Math.pow(t, power);
    }

    private static double sine(double t) {
        return 1 - Math.cos(t * Math.PI / 2);
    }

    private static double circle(double t) {
        return 1 - Math.sqrt(1 - t * t);
    }

    private static double exp(double t) {
        return Math.pow(2, 10 * (t - 1));
    }

    private static double catmullRom(double n) {
        return .5 * (2 * (n + 1) + (n + 2 - n) + (2 * n - 5 * (n + 1) + 4 * (n + 2) - (n + 3)) + (3 * (n + 1) - n - 3 * (n + 2) + (n + 3)));
    }

    private static DoubleUnaryOperator elastic(Double argument) {
        double bounces = argument == null ? 1 : argument;
        return t -> 1 - Math.pow(Math.cos(t * Math.PI / 2), 3) * Math.cos(t * bounces * Math.PI);
    }

    private static DoubleUnaryOperator back(Double argument) {
        double overshoot = argument == null ? 1.70158 : argument * 1.70158;
        return t -> t * t * ((overshoot + 1) * t - overshoot);
    }

    private static DoubleUnaryOperator bounce(Double argument) {
        double k = argument == null ? .5 : argument;
        return t -> Math.min(Math.min(7.5625 * t * t, 30.25 * k * Math.pow(t - .54545456F, 2) + 1 - k),
                Math.min(121 * k * k * Math.pow(t - .8181818F, 2) + 1 - k * k, 484 * k * k * k * Math.pow(t - .95454544F, 2) + 1 - k * k * k));
    }

    private static DoubleUnaryOperator step(Double argument) {
        double count = argument == null ? 2 : argument;
        if (count < 2) throw new IllegalArgumentException("Steps must be >= 2, got: " + count);
        int steps = (int) count;
        double length = 1.0 / steps;
        return t -> {
            if (t < 0) return 0;
            double last = (steps - 1) * length;
            if (t > last) return last;
            int left = 0;
            int right = steps - 1;
            while (right - left != 1) {
                int middle = left + (right - left) / 2;
                if (t >= middle * length) left = middle;
                else right = middle;
            }
            return left * length;
        };
    }
}
