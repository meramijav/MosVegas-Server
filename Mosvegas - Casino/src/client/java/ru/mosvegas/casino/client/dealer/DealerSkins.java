package ru.mosvegas.casino.client.dealer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import ru.mosvegas.casino.MosVegasCasino;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Skin textures of dealers. Resource ids are used as they are; http(s) skins are downloaded once,
 * cached in {@code .minecraft/mosvegas_casino/skins} and registered as dynamic textures.
 */
public final class DealerSkins {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, Identifier> LOADED = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private DealerSkins() {}

    public static Identifier fallback(boolean slim) {
        return MosVegasCasino.id("textures/entity/dealer/" + (slim ? "anna" : "maxim") + ".png");
    }

    public static Identifier texture(String reference, boolean slim) {
        if (reference == null || reference.isBlank()) return fallback(slim);
        if (reference.startsWith("http://") || reference.startsWith("https://")) {
            Identifier id = LOADED.get(reference);
            if (id != null) return id;
            if (!FAILED.contains(reference) && LOADING.add(reference)) download(reference);
            return fallback(slim);
        }
        Identifier id = Identifier.tryParse(reference);
        return id != null ? id : fallback(slim);
    }

    private static void download(String url) {
        Path cache = MinecraftClient.getInstance().runDirectory.toPath().resolve("mosvegas_casino").resolve("skins").resolve(hash(url) + ".png");
        CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.isRegularFile(cache)) return Files.readAllBytes(cache);
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = response.body();
                if (response.statusCode() != 200 || body.length > MAX_BYTES) throw new IllegalStateException("HTTP " + response.statusCode());
                Files.createDirectories(cache.getParent());
                Files.write(cache, body);
                return body;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, Util.getDownloadWorkerExecutor()).whenCompleteAsync((bytes, error) -> {
            LOADING.remove(url);
            if (error != null || bytes == null) {
                FAILED.add(url);
                MosVegasCasino.LOGGER.warn("Скин крупье не загружен: {} ({})", url, error == null ? "empty" : error.getMessage());
                return;
            }
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                image = normalize(image);
                Identifier id = MosVegasCasino.id("dealer_skin/" + hash(url));
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                LOADED.put(url, id);
            } catch (Exception e) {
                FAILED.add(url);
                MosVegasCasino.LOGGER.warn("Неверный PNG скина {}: {}", url, e.toString());
            }
        }, MinecraftClient.getInstance());
    }

    /** Converts legacy 64x32 skins to the 64x64 layout like vanilla does. */
    private static NativeImage normalize(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        if (w != h * 2) return image;
        int s = w / 64;
        NativeImage out = new NativeImage(w, w, true);
        out.copyFrom(image);
        out.fillRect(0, 32 * s, 64 * s, 32 * s, 0);
        int[][] rects = {
                {4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
                {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4},
                {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}
        };
        for (int[] r : rects) out.copyRect(r[0] * s, r[1] * s, r[2] * s, r[3] * s, r[4] * s, r[5] * s, true, false);
        image.close();
        return out;
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 20);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public static void clear() {
        FAILED.clear();
    }
}
