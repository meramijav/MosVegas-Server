package ru.mosvegas.casino.dealer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.config.DealerProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a dealer {@code skin} setting into something every client can load: a resource id or an http(s)
 * PNG link. {@code player:<nick>} is resolved once through the public Mojang API (works on offline-mode
 * servers too); until then the default skin of the dealer's gender is used.
 */
public final class SkinResolver {
    public record Resolved(String reference, boolean slim) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Map<String, Resolved> PLAYERS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static Runnable onResolved = () -> {};

    private SkinResolver() {}

    public static void init(MinecraftServer srv, Runnable resolvedCallback) {
        server = srv;
        onResolved = resolvedCallback;
    }

    public static String defaultSkin(boolean slim) {
        return MosVegasCasino.MOD_ID + ":textures/entity/dealer/" + (slim ? "anna" : "maxim") + ".png";
    }

    public static Resolved resolve(DealerProfile profile) {
        String skin = profile.skin == null ? "" : profile.skin.trim();
        boolean slim = profile.slim();
        if (skin.isEmpty()) return new Resolved(defaultSkin(slim), slim);
        if (skin.regionMatches(true, 0, "player:", 0, 7)) {
            String name = skin.substring(7).trim();
            Resolved cached = PLAYERS.get(name.toLowerCase());
            if (cached != null) return new Resolved(cached.reference(), slim);
            fetch(name);
            return new Resolved(defaultSkin(slim), slim);
        }
        return new Resolved(skin, slim);
    }

    private static void fetch(String name) {
        String key = name.toLowerCase();
        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) return;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject idJson = get("https://api.mojang.com/users/profiles/minecraft/" + name);
                String uuid = idJson.get("id").getAsString();
                JsonObject profile = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                for (var element : profile.getAsJsonArray("properties")) {
                    JsonObject property = element.getAsJsonObject();
                    if (!"textures".equals(property.get("name").getAsString())) continue;
                    String decoded = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
                    JsonObject skin = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                    String url = skin.get("url").getAsString().replace("http://", "https://");
                    boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                    PLAYERS.put(key, new Resolved(url, slim));
                    MosVegasCasino.LOGGER.info("Скин крупье {} загружен: {}", name, url);
                }
            } catch (Exception e) {
                MosVegasCasino.LOGGER.warn("Не удалось получить скин игрока {}: {}", name, e.toString());
            } finally {
                PENDING.remove(key);
                MinecraftServer srv = server;
                if (srv != null && PLAYERS.containsKey(key)) srv.execute(onResolved);
            }
        });
    }

    private static JsonObject get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " " + url);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public static void clear() {
        PLAYERS.clear();
        server = null;
    }
}
