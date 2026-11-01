package studio.merami.slimcore.baseclasses;

import java.util.Arrays;

public final class Text {
    private final String key;
    private final String fallback;
    private final Object[] args;

    private Text(String key, String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
    }

    public static Text translatable(String key, Object... args) {
        return new Text(key, null, args.clone());
    }

    public static Text withFallback(String key, String fallback, Object... args) {
        return new Text(key, fallback, args.clone());
    }

    public String key() {
        return key;
    }

    public String fallback() {
        return fallback;
    }

    public Object[] args() {
        return args.clone();
    }

    @Override
    public String toString() {
        return "Text[" + key + (fallback == null ? "" : " | " + fallback) + (args.length == 0 ? "" : " " + Arrays.toString(args)) + "]";
    }
}
