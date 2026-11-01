package studio.merami.slimcore.rendering;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.packloading.PackParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimationFile {
    public enum Axis {
        X,
        Y,
        Z
    }

    public record Keyframe(double length, MoLang.Value start, MoLang.Value end, Easing easing, Double argument) {}

    public static final class Channel {
        public final boolean rotation;
        public final List<List<Keyframe>> axes;

        Channel(boolean rotation, List<Keyframe> x, List<Keyframe> y, List<Keyframe> z) {
            this.rotation = rotation;
            this.axes = List.of(x, y, z);
        }

        public boolean isEmpty() {
            return axes.get(0).isEmpty();
        }

        double length() {
            double longest = 0;
            for (List<Keyframe> frames : axes) {
                double total = 0;
                for (Keyframe frame : frames) total += frame.length();
                longest = Math.max(longest, total);
            }
            return longest;
        }

        public double value(Axis axis, double tick, MoLang.Context context) {
            List<Keyframe> frames = axes.get(axis.ordinal());
            double total = 0;
            Keyframe frame = frames.get(frames.size() - 1);
            double local = tick;
            for (Keyframe candidate : frames) {
                total += candidate.length();
                if (total > tick) {
                    frame = candidate;
                    local = tick - (total - candidate.length());
                    break;
                }
            }
            return frame.easing().interpolate(local, frame.length(), convert(axis, frame.start(), context), convert(axis, frame.end(), context), frame.argument());
        }

        public double first(Axis axis, MoLang.Context context) {
            List<Keyframe> frames = axes.get(axis.ordinal());
            double total = 0;
            for (Keyframe frame : frames) {
                total += frame.length();
                if (total > 0) return convert(axis, frame.start(), context);
            }
            return convert(axis, frames.get(frames.size() - 1).start(), context);
        }

        private double convert(Axis axis, MoLang.Value value, MoLang.Context context) {
            double raw = value.get(context);
            if (!rotation || value.constant()) return raw;
            double radians = Math.toRadians(raw);
            return axis == Axis.Z ? radians : -radians;
        }
    }

    public record Track(String bone, Channel rotation, Channel position, Channel scale) {}

    public record Clip(String name, double length, List<Track> tracks) {}

    private static final Map<ResourceId, AnimationFile> FILES = new HashMap<>();

    public final Map<String, Clip> clips;

    private AnimationFile(Map<String, Clip> clips) {
        this.clips = Collections.unmodifiableMap(clips);
    }

    public static synchronized AnimationFile load(ResourceId location) {
        AnimationFile cached = FILES.get(location);
        if (cached != null) return cached;
        try (InputStream in = PackParser.openAsset(location); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            AnimationFile file = parse(JsonParser.parseReader(reader).getAsJsonObject());
            FILES.put(location, file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("animation file " + location + ": " + e.getMessage(), e);
        }
    }

    public static synchronized void clearCache() {
        FILES.clear();
    }

    public Clip clip(String name) {
        return clips.get(name);
    }

    public static AnimationFile parse(JsonObject root) {
        Map<String, Clip> clips = new LinkedHashMap<>();
        JsonObject animations = root.has("animations") ? root.getAsJsonObject("animations") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            try {
                clips.put(entry.getKey(), clip(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(entry.getKey() + ": " + e.getMessage(), e);
            }
        }
        return new AnimationFile(clips);
    }

    private static Clip clip(String name, JsonObject raw) {
        List<Track> tracks = new ArrayList<>();
        JsonObject bones = raw.has("bones") ? raw.getAsJsonObject("bones") : new JsonObject();
        double length = 0;
        for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            JsonObject bone = entry.getValue().getAsJsonObject();
            Track track = new Track(entry.getKey(), channel(bone.get("rotation"), true), channel(bone.get("position"), false), channel(bone.get("scale"), false));
            tracks.add(track);
            length = Math.max(length, Math.max(track.rotation().length(), Math.max(track.position().length(), track.scale().length())));
        }
        if (raw.has("animation_length")) length = raw.get("animation_length").getAsDouble() * 20;
        else if (length == 0) length = Double.MAX_VALUE;
        return new Clip(name, length, List.copyOf(tracks));
    }

    private record Entry(String time, JsonElement value) {}

    private static List<Entry> entries(JsonElement element) {
        List<Entry> entries = new ArrayList<>();
        if (element == null) return entries;
        if (element instanceof JsonPrimitive primitive) {
            JsonArray array = new JsonArray(3);
            array.add(primitive);
            array.add(primitive);
            array.add(primitive);
            element = array;
        }
        if (element instanceof JsonArray array) {
            entries.add(new Entry("0", array));
            return entries;
        }
        if (!(element instanceof JsonObject object)) throw new IllegalArgumentException("a keyframe channel must be a number, an array or an object: " + element);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue() instanceof JsonObject frame && !frame.has("vector")) {
                double time = number(entry.getKey()) ? Double.parseDouble(entry.getKey()) : 0;
                if (frame.has("pre")) entries.add(new Entry(entry.getKey(), vector(frame.get("pre"))));
                if (frame.has("post")) entries.add(new Entry(String.valueOf(time + 1e-7), vector(frame.get("post"))));
            } else {
                entries.add(new Entry(entry.getKey(), entry.getValue()));
            }
        }
        return entries;
    }

    private static JsonArray vector(JsonElement element) {
        return element.isJsonArray() ? element.getAsJsonArray() : element.getAsJsonObject().getAsJsonArray("vector");
    }

    private static boolean number(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static MoLang.Value value(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return new MoLang.Constant(element.getAsDouble());
        return MoLang.parse(element.getAsString());
    }

    private static Channel channel(JsonElement element, boolean rotation) {
        List<List<Keyframe>> axes = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        MoLang.Value[] previous = new MoLang.Value[3];
        Entry last = null;
        for (Entry entry : entries(element)) {
            String key = entry.time();
            if (key.equals("easing") || key.equals("easingArgs") || key.equals("lerp_mode")) continue;
            double previousTime = last == null ? 0 : Double.parseDouble(last.time());
            double time = number(key) ? Double.parseDouble(key) : 0;
            JsonArray vector = entry.value() instanceof JsonArray array ? array : entry.value().getAsJsonObject().getAsJsonArray("vector");
            JsonObject frame = entry.value() instanceof JsonObject object ? object : null;
            Easing easing = frame != null && frame.has("easing") && frame.get("easing").isJsonPrimitive() ? Easing.byName(frame.get("easing").getAsString()) : Easing.LINEAR;
            Double argument = frame != null && frame.has("easingArgs") && frame.getAsJsonArray("easingArgs").size() > 0 ? frame.getAsJsonArray("easingArgs").get(0).getAsDouble() : null;
            for (int axis = 0; axis < 3; axis++) {
                MoLang.Value value = value(vector.get(axis));
                if (rotation && value.constant()) value = new MoLang.Constant(axis == 2 ? Math.toRadians(value.get(null)) : Math.toRadians(-value.get(null)));
                axes.get(axis).add(new Keyframe((time - previousTime) * 20, last == null ? value : previous[axis], value, easing, argument));
                previous[axis] = value;
            }
            last = entry;
        }
        return new Channel(rotation, List.copyOf(axes.get(0)), List.copyOf(axes.get(1)), List.copyOf(axes.get(2)));
    }
}
