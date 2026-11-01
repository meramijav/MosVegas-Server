package ru.mosvegas.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable startup configuration. Durations are ticks (20 = 1 second). */
public record ConsumableDefinition(
        String id, String name, String description, String category, String useAction,
        String model, String texture, String animationFile, String idleAnimation, String useAnimation,
        String sound, String startSound, String finishSound, float volume, float pitch, int soundInterval,
        int useTime, int hunger, float saturation, int uses, int stackSize, int cooldown,
        boolean alwaysEdible, boolean consumeInCreative, String remainder, String discard, int openTime,
        ParticleDefinition particles, List<EffectDefinition> effects, String group, int order) {

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_/-]+");
    private static final Pattern GROUP_ID = Pattern.compile("[a-z0-9_]*");
    private static final Set<String> USE_ACTIONS = Set.of("eat", "drink", "smoke", "none");
    private static final Set<String> DISCARDS = Set.of("throw", "drop", "none");
    private static final ParticleDefinition NO_PARTICLES = new ParticleDefinition("", 2, 6, 0.07f, 0.01f);
    // For smoking the particles are the breath let out after each draw.
    private static final ParticleDefinition SMOKE_PARTICLES = new ParticleDefinition("mosvegas_items:exhaled_smoke", 3, 2, 0.04f, 0.08f);

    public ConsumableDefinition {
        effects = List.copyOf(effects);
    }

    public static ConsumableDefinition fromJson(JsonObject json) {
        String id = string(json, "id", "");
        require(ITEM_ID.matcher(id).matches(), "id must use lowercase a-z, 0-9, _ / -");
        String category = string(json, "category", "other").toLowerCase(Locale.ROOT);
        String defaultAction = switch (category) {
            case "food" -> "eat";
            case "drink", "alcohol" -> "drink";
            case "tobacco" -> "smoke";
            default -> "none";
        };
        String action = string(json, "use_action", defaultAction).toLowerCase(Locale.ROOT);
        require(USE_ACTIONS.contains(action), "use_action must be eat, drink, smoke or none");
        String defaultSound = switch (action) {
            case "eat" -> "minecraft:entity.generic.eat";
            case "drink" -> "minecraft:entity.generic.drink";
            case "smoke" -> "minecraft:block.fire.ambient";
            default -> "";
        };
        String model = resource(string(json, "model", ""), MosVegasItems.MOD_ID, true);
        String remainder = resource(string(json, "remainder", ""), "minecraft", true);
        // A 3D can is thrown away and a cigarette butt dropped, unless a container is returned.
        String defaultDiscard = model.isEmpty() || !remainder.isEmpty() ? "none"
                : action.equals("smoke") ? "drop" : action.equals("drink") ? "throw" : "none";
        String discard = string(json, "discard", defaultDiscard).toLowerCase(Locale.ROOT);
        require(DISCARDS.contains(discard), "discard must be throw, drop or none");
        // Items sharing a group fill one creative slot that opens a list of the variants.
        String group = string(json, "group", "");
        require(GROUP_ID.matcher(group).matches(), "group must use lowercase a-z, 0-9, _");
        return new ConsumableDefinition(id, string(json, "name", "item.mosvegas_items." + id),
                string(json, "description", ""), category, action, model,
                resource(string(json, "texture", "minecraft:item/apple"), MosVegasItems.MOD_ID, false),
                resource(string(json, "animation_file", "animations/consumable.animation.json"), MosVegasItems.MOD_ID, false),
                string(json, "idle_animation", ""), string(json, "use_animation", ""),
                resource(string(json, "sound", defaultSound), "minecraft", true),
                resource(string(json, "start_sound", ""), "minecraft", true),
                resource(string(json, "finish_sound", ""), "minecraft", true),
                decimal(json, "sound_volume", 0.6f, 0, 4), decimal(json, "sound_pitch", 1, 0.1f, 2),
                integer(json, "sound_interval", 8, 1, 72000), integer(json, "use_time", 32, 1, 72000),
                integer(json, "hunger", 0, 0, 20), decimal(json, "saturation", 0, 0, 20),
                integer(json, "uses", 1, 1, 10000), integer(json, "stack_size", 16, 1, 64),
                integer(json, "cooldown", 0, 0, 72000), bool(json, "always_edible", !action.equals("eat")),
                bool(json, "consume_in_creative", false), remainder, discard,
                integer(json, "open_time", 20, 1, 72000),
                parseParticles(json.get("particles"), action), parseEffects(json.get("effects")),
                group, integer(json, "order", 0, -1000000, 1000000));
    }

    static boolean enabled(JsonObject json) {
        return bool(json, "enabled", true);
    }

    private static List<EffectDefinition> parseEffects(JsonElement element) {
        if (element == null) return List.of();
        require(element.isJsonArray(), "effects must be an array");
        JsonArray array = element.getAsJsonArray();
        List<EffectDefinition> effects = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonElement entry = array.get(index);
            require(entry.isJsonObject(), "effects[" + index + "] must be an object");
            JsonObject effect = entry.getAsJsonObject();
            try {
                effects.add(new EffectDefinition(resource(string(effect, "id", ""), "minecraft", false),
                        integer(effect, "duration", 200, 1, 72000000), integer(effect, "amplifier", 0, 0, 255),
                        decimal(effect, "chance", 1, 0, 1), bool(effect, "ambient", false),
                        bool(effect, "show_particles", true), bool(effect, "show_icon", true)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("effects[" + index + "]: " + exception.getMessage(), exception);
            }
        }
        return effects;
    }

    private static ParticleDefinition parseParticles(JsonElement element, String action) {
        ParticleDefinition fallback = action.equals("smoke") ? SMOKE_PARTICLES : NO_PARTICLES;
        if (element == null) return fallback;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean() ? fallback : NO_PARTICLES;
        }
        require(element.isJsonObject(), "particles must be true, false or an object");
        JsonObject p = element.getAsJsonObject();
        return new ParticleDefinition(resource(string(p, "id", fallback.id()), "minecraft", true),
                integer(p, "count", 2, 0, 64), integer(p, "interval", 6, 1, 72000),
                decimal(p, "spread", 0.07f, 0, 2), decimal(p, "speed", 0.01f, 0, 2));
    }

    private static String string(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), key + " must be a string");
        return value.getAsString();
    }

    private static int integer(JsonObject json, String key, int fallback, int min, int max) {
        double value = number(json, key, fallback);
        require(value == Math.rint(value) && value >= min && value <= max, key + " must be an integer in " + min + ".." + max);
        return (int) value;
    }

    private static float decimal(JsonObject json, String key, float fallback, float min, float max) {
        double value = number(json, key, fallback);
        require(Double.isFinite(value) && value >= min && value <= max, key + " must be in " + min + ".." + max);
        return (float) value;
    }

    private static double number(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), key + " must be a number");
        return value.getAsDouble();
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(), key + " must be true or false");
        return value.getAsBoolean();
    }

    private static String resource(String value, String namespace, boolean optional) {
        if (optional && value.isEmpty()) return "";
        Identifier id = Identifier.tryParse(value.contains(":") ? value : namespace + ":" + value);
        require(id != null && !id.getPath().isEmpty() && !id.getPath().contains(".."), "Invalid resource id: " + value);
        return id.toString();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    public record EffectDefinition(String id, int duration, int amplifier, float chance,
                                   boolean ambient, boolean showParticles, boolean showIcon) {}
    public record ParticleDefinition(String id, int count, int interval, float spread, float speed) {}
}
