package ru.mosvegas.items.rp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RpAnim {
    public final String name;
    public final float length;
    final Map<String, Track[]> bones = new HashMap<>();

    static final int ROTATION = 0, POSITION = 1, SCALE = 2;

    record Track(float[] times, float[][] values) {
        float[] sample(float t, float[] out) {
            int n = times.length;
            if (n == 0) return null;
            if (t <= times[0]) return copy(values[0], out);
            if (t >= times[n - 1]) return copy(values[n - 1], out);
            int lo = 0, hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= t) lo = mid; else hi = mid;
            }
            float a = (t - times[lo]) / (times[hi] - times[lo]);
            for (int k = 0; k < 3; k++) out[k] = values[lo][k] + (values[hi][k] - values[lo][k]) * a;
            return out;
        }

        private static float[] copy(float[] v, float[] out) {
            out[0] = v[0];
            out[1] = v[1];
            out[2] = v[2];
            return out;
        }
    }

    RpAnim(String name, JsonObject json) {
        this.name = name;
        this.length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : 0;
        JsonObject bonesJson = json.has("bones") ? json.getAsJsonObject("bones") : new JsonObject();
        for (var entry : bonesJson.entrySet()) {
            JsonObject channels = entry.getValue().getAsJsonObject();
            Track[] tracks = new Track[3];
            tracks[ROTATION] = track(channels.get("rotation"), 0);
            tracks[POSITION] = track(channels.get("position"), 0);
            tracks[SCALE] = track(channels.get("scale"), 1);
            bones.put(entry.getKey(), tracks);
        }
    }

    private static Track track(JsonElement element, float fallback) {
        if (element == null) return null;
        TreeMap<Float, float[]> keys = new TreeMap<>();
        if (element.isJsonArray() || element.isJsonPrimitive()) {
            keys.put(0f, vector(element, fallback));
        } else {
            for (var key : element.getAsJsonObject().entrySet()) {
                JsonElement value = key.getValue();
                if (value.isJsonObject()) {
                    JsonObject keyframe = value.getAsJsonObject();
                    value = keyframe.has("post") ? keyframe.get("post") : keyframe.get("pre");
                }
                keys.put(Float.parseFloat(key.getKey()), vector(value, fallback));
            }
        }
        float[] times = new float[keys.size()];
        float[][] values = new float[keys.size()][];
        int i = 0;
        for (var key : keys.entrySet()) {
            times[i] = key.getKey();
            values[i++] = key.getValue();
        }
        return new Track(times, values);
    }

    private static float[] vector(JsonElement element, float fallback) {
        if (element == null) return new float[]{fallback, fallback, fallback};
        if (element.isJsonPrimitive()) {
            float v = element.getAsFloat();
            return new float[]{v, v, v};
        }
        JsonArray array = element.getAsJsonArray();
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) out[k] = k < array.size() ? array.get(k).getAsFloat() : fallback;
        return out;
    }

    float[] sample(String bone, int channel, float t, float[] out) {
        Track[] tracks = bones.get(bone);
        if (tracks == null || tracks[channel] == null) return null;
        return tracks[channel].sample(t, out);
    }

    boolean has(String bone) {
        return bones.containsKey(bone);
    }

    public static List<RpAnim> parseFile(JsonObject root) {
        List<RpAnim> out = new ArrayList<>();
        JsonObject animations = root.getAsJsonObject("animations");
        if (animations == null) return out;
        for (var entry : animations.entrySet()) out.add(new RpAnim(entry.getKey(), entry.getValue().getAsJsonObject()));
        return out;
    }
}
