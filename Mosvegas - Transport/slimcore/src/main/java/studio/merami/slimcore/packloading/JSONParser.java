package studio.merami.slimcore.packloading;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import studio.merami.slimcore.baseclasses.Point3D;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JSONParser {
    private static final FieldNamingPolicy NAMING = FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
    public static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(NAMING)
            .registerTypeAdapter(Point3D.class, new Point3DAdapter())
            .create();

    private JSONParser() {}

    public static JsonObject read(Path path) throws IOException {
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public static JsonObject merge(JsonObject base, JsonObject over) {
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            if (entry.getKey().equals("parent")) continue;
            JsonElement old = base.get(entry.getKey());
            if (old != null && old.isJsonObject() && entry.getValue().isJsonObject()) {
                merge(old.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                base.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return base;
    }

    public static void applyWheelTemplate(JsonObject vehicle) {
        JsonElement template = vehicle.remove("wheel");
        if (template == null || !template.isJsonObject() || !vehicle.has("wheels")) return;
        for (JsonElement wheel : vehicle.getAsJsonArray("wheels")) {
            if (!wheel.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> entry : template.getAsJsonObject().entrySet()) {
                if (!wheel.getAsJsonObject().has(entry.getKey())) wheel.getAsJsonObject().add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    public static <T> T parse(JsonObject json, Class<T> type, List<String> unknownKeys) {
        checkKeys(json, type, "", unknownKeys);
        return GSON.fromJson(json, type);
    }

    private static void checkKeys(JsonElement element, Type type, String path, List<String> out) {
        Class<?> raw = raw(type);
        if (raw == null) return;
        if (element.isJsonArray() && List.class.isAssignableFrom(raw)) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) checkKeys(array.get(i), argument(type, 0), path + "[" + i + "]", out);
            return;
        }
        if (!element.isJsonObject()) return;
        if (Map.class.isAssignableFrom(raw)) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                checkKeys(entry.getValue(), argument(type, 1), path + "." + entry.getKey(), out);
            }
            return;
        }
        if (raw.getName().startsWith("java.") || raw == Point3D.class) return;
        Map<String, Field> fields = fields(raw);
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Field field = fields.get(entry.getKey());
            String child = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            if (field == null) {
                if (!entry.getKey().equals("parent")) out.add(child);
            } else {
                checkKeys(entry.getValue(), field.getGenericType(), child, out);
            }
        }
    }

    private static Map<String, Field> fields(Class<?> type) {
        Map<String, Field> fields = new HashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
                fields.putIfAbsent(NAMING.translateName(field), field);
            }
        }
        return fields;
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    private static Type argument(Type type, int index) {
        return type instanceof ParameterizedType p ? p.getActualTypeArguments()[index] : Object.class;
    }

    private static final class Point3DAdapter extends TypeAdapter<Point3D> {
        @Override
        public void write(JsonWriter out, Point3D value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginArray().value(value.x).value(value.y).value(value.z).endArray();
        }

        @Override
        public Point3D read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            in.beginArray();
            Point3D value = new Point3D(in.nextDouble(), in.nextDouble(), in.nextDouble());
            in.endArray();
            return value;
        }
    }
}
