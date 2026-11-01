package ru.mosvegas.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Write first, publish second. Bad JSON is never replaced with empty defaults. */
public final class JsonFile {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private JsonFile() {}

    public static <T> T read(Path path, Class<T> type) throws IOException {
        T value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        if (value == null) throw new IOException("Пустой файл: " + path);
        return value;
    }

    public static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
        if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static <T> T copy(T value, Class<T> type) { return GSON.fromJson(GSON.toJson(value), type); }
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    public static void validId(String id) {
        require(id != null && id.matches("[a-z0-9_-]{1,64}"), "ID: 1–64 символа a-z, 0-9, _ или -.");
    }
}
