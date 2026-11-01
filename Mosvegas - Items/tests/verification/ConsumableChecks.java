import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.mosvegas.items.ConsumableDefinition;
import ru.mosvegas.items.ConsumptionTimeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Configuration regression checks; deliberately excluded from the release JAR. */
public final class ConsumableChecks {
    private static final Path RESOURCES = Path.of(System.getProperty("mosvegas.projectDir", ""), "src/main/resources");
    private static int checks;

    public static void main(String[] args) throws Exception {
        var food = parse("{\"id\":\"food\",\"category\":\"food\"}");
        require(food.useAction().equals("eat") && !food.alwaysEdible(), "Food defaults");
        var smoke = parse("{\"id\":\"smoke\",\"category\":\"tobacco\"}");
        require(smoke.useAction().equals("smoke") && smoke.alwaysEdible(), "Tobacco defaults");
        require(smoke.particles().id().equals("mosvegas_items:exhaled_smoke"), "Default exhaled smoke");
        require(parse("{\"id\":\"smoke\",\"category\":\"tobacco\",\"particles\":false}")
                .particles().id().isEmpty(), "Disabled particles");
        require(food.openTime() == 20, "Default opening time");
        require(parse("{\"id\":\"can\",\"open_time\":30}").openTime() == 30, "Custom opening time");
        String model = ",\"model\":\"geo/custom.geo.json\"";
        require(parse("{\"id\":\"can\",\"category\":\"alcohol\"" + model + "}").discard().equals("throw"), "A 3D can is thrown away");
        require(parse("{\"id\":\"cig\",\"category\":\"tobacco\"" + model + "}").discard().equals("drop"), "A 3D butt is dropped");
        require(parse("{\"id\":\"bottle\",\"category\":\"drink\",\"remainder\":\"glass_bottle\"" + model + "}")
                .discard().equals("none"), "A returned container is not thrown away");
        require(parse("{\"id\":\"flat\",\"category\":\"drink\"}").discard().equals("none"), "Flat items leave no litter");
        require(food.discard().equals("none"), "Food leaves no litter");
        require(parse("{\"id\":\"flat\",\"discard\":\"DROP\"}").discard().equals("drop"), "Explicit discard");
        require(parse("{\"id\":\"custom/drink\",\"category\":\"ALCOHOL\"}")
                .useAction().equals("drink"), "Nested IDs and category case");
        var resources = parse("{\"id\":\"custom\",\"model\":\"geo/custom.geo.json\","
                + "\"texture\":\"another:textures/item/custom.png\",\"effects\":[{\"id\":\"speed\"}]}");
        require(resources.model().equals("mosvegas_items:geo/custom.geo.json"), "Default model namespace");
        require(resources.texture().equals("another:textures/item/custom.png"), "Explicit texture namespace");
        require(resources.effects().get(0).id().equals("minecraft:speed"), "Default effect namespace");
        require(food.startSound().isEmpty(), "Existing definitions do not play an opening sound");
        require(!food.consumeInCreative(), "Existing definitions retain creative consumption behavior");
        require(parse("{\"id\":\"roleplay\",\"consume_in_creative\":true}").consumeInCreative(),
                "Creative depletion can be enabled per item");
        require(parse("{\"id\":\"can\",\"start_sound\":\"mosvegas_items:item.can.open\"}")
                .startSound().equals("mosvegas_items:item.can.open"), "Custom opening sound namespace");
        require(parse("{\"id\":\"can\",\"start_sound\":\"entity.player.burp\"}")
                .startSound().equals("minecraft:entity.player.burp"), "Default opening sound namespace");
        expectInvalid("{\"id\":\"Invalid\"}");
        expectInvalid("{\"id\":\"../escape\"}");
        expectInvalid("{\"id\":\"item\",\"model\":\"geo/../escape.json\"}");
        expectInvalid("{\"id\":\"item\",\"uses\":0}");
        expectInvalid("{\"id\":\"item\",\"use_time\":1.5}");
        expectInvalid("{\"id\":\"item\",\"stack_size\":65}");
        expectInvalid("{\"id\":\"item\",\"always_edible\":\"true\"}");
        expectInvalid("{\"id\":\"item\",\"effects\":[{\"id\":\"speed\",\"chance\":1.01}]}");
        expectInvalid("{\"id\":\"item\",\"particles\":{\"interval\":0}}");
        expectInvalid("{\"id\":\"item\",\"start_sound\":false}");
        expectInvalid("{\"id\":\"item\",\"consume_in_creative\":\"true\"}");
        expectInvalid("{\"id\":\"item\",\"start_sound\":\"Invalid:Sound\"}");
        expectInvalid("{\"id\":\"item\",\"discard\":\"bin\"}");
        expectInvalid("{\"id\":\"item\",\"discard\":true}");
        expectInvalid("{\"id\":\"item\",\"open_time\":0}");
        for (String id : List.of("beer", "cigarette", "energy_drink", "snack")) {
            var definition = parse(Files.readString(RESOURCES.resolve("data/mosvegas_items/consumables/" + id + ".json")));
            require(definition.id().equals(id), "Built-in item " + id);
        }
        require(parse("{\"id\":\"pizza\",\"group\":\"pizza\",\"order\":100}").group().equals("pizza"), "Group and order");
        require(parse("{\"id\":\"plain\"}").group().isEmpty() && parse("{\"id\":\"plain\"}").order() == 0, "Ungrouped by default");
        expectInvalid("{\"id\":\"item\",\"group\":\"Pizza\"}");
        expectInvalid("{\"id\":\"item\",\"order\":1.5}");
        bundledItems();
        vanillaFoodPack();
        timeline();
        try {
            resources.effects().clear();
            throw new AssertionError("Effect list must be immutable");
        } catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("PASS: " + checks + " consumable configuration checks");
    }

    /** Every bundled item parses, has a translated name, and its model's textures exist. */
    private static void bundledItems() throws Exception {
        Path folder = RESOURCES.resolve("data/mosvegas_items/consumables");
        JsonObject english = JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/mosvegas_items/lang/en_us.json"))).getAsJsonObject();
        JsonObject russian = JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/mosvegas_items/lang/ru_ru.json"))).getAsJsonObject();
        Map<String, Integer> groups = new HashMap<>();
        Set<String> ids = new HashSet<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(folder)) { files = walk.filter(p -> p.toString().endsWith(".json")).toList(); }
        for (Path file : files) {
            var definition = parse(Files.readString(file));
            require(ids.add(definition.id()), "Unique bundled ID " + definition.id());
            if (!definition.group().isEmpty()) {
                groups.merge(definition.group(), 1, Integer::sum);
                String key = "group.mosvegas_items." + definition.group();
                require(english.has(key) && russian.has(key), "Translated group " + key);
            }
            String name = definition.name();
            require(english.has(name) && russian.has(name), "Translated name " + name);
            if (!file.getParent().getFileName().toString().equals("food")) continue;
            Path model = RESOURCES.resolve("assets/mosvegas_items/models/item/" + definition.id() + ".json");
            require(Files.exists(model), "Item model of " + definition.id());
            modelTextures(model, RESOURCES.resolve("assets"));
        }
        require(files.size() == 63, "4 examples and 59 food items are bundled, found " + files.size());
        groups.forEach((group, size) -> require(size >= 2, "Group " + group + " has at least two variants"));
    }

    /** The built-in 3D pack replaces vanilla food models and keeps vanilla textures untouched. */
    private static void vanillaFoodPack() throws Exception {
        Path pack = RESOURCES.resolve("resourcepacks/food_3d");
        require(JsonParser.parseString(Files.readString(pack.resolve("pack.mcmeta"))).getAsJsonObject()
                .getAsJsonObject("pack").get("pack_format").getAsInt() == 22, "3D food pack format 22");
        require(Files.notExists(pack.resolve("assets/minecraft/gpu_warnlist.json"))
                && Files.notExists(pack.resolve("assets/minecraft/regional_compliancies.json")), "Only food assets are overridden");
        try (Stream<Path> textures = Files.list(pack.resolve("assets/minecraft/textures/item"))) {
            require(textures.allMatch(Files::isDirectory), "Vanilla item textures are not replaced");
        }
        List<Path> models;
        try (Stream<Path> walk = Files.list(pack.resolve("assets/minecraft/models/item"))) { models = walk.toList(); }
        for (Path model : models) modelTextures(model, pack.resolve("assets"));
        require(models.size() == 42, "42 vanilla food models");
    }

    private static void modelTextures(Path model, Path assets) throws Exception {
        JsonObject json = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
        for (var texture : json.getAsJsonObject("textures").entrySet()) {
            String id = texture.getValue().getAsString();
            if (id.startsWith("#")) continue;
            String[] parts = id.contains(":") ? id.split(":", 2) : new String[]{"minecraft", id};
            Path png = assets.resolve(parts[0] + "/textures/" + parts[1] + ".png");
            require(Files.exists(png), model.getFileName() + " texture " + id);
            // Atlas sprites with sides that are not multiples of 16 cut the mipmaps of every block texture.
            var header = java.nio.ByteBuffer.wrap(Files.readAllBytes(png), 16, 8);
            require(header.getInt() % 16 == 0 && header.getInt() % 16 == 0, id + " keeps the block atlas mipmaps");
        }
        for (var element : json.getAsJsonArray("elements")) {
            JsonObject rotation = element.getAsJsonObject().getAsJsonObject("rotation");
            if (rotation == null) continue;
            float angle = Math.abs(rotation.get("angle").getAsFloat());
            require(angle == 0 || angle == 22.5f || angle == 45, model.getFileName() + " uses a rotation Minecraft accepts");
        }
    }

    /** The shared choreography the server (sound, breath) and clients (poses) both follow. */
    private static void timeline() {
        int total = 48;
        require(ConsumptionTimeline.reach("smoke", false, 0, total) == 0
                && ConsumptionTimeline.reach("smoke", false, total, total) == 0, "A puff starts and ends with the hand down");
        float inhale = (ConsumptionTimeline.INHALE_FULL + ConsumptionTimeline.INHALE_END) / 2 * total;
        require(ConsumptionTimeline.reach("smoke", false, inhale, total) == 1
                && ConsumptionTimeline.inhale(inhale, total) == 1, "The draw happens with the cigarette at the lips");
        require(ConsumptionTimeline.INHALE_STOP < ConsumptionTimeline.EXHALE_START
                && ConsumptionTimeline.SMOKE_LOWERED <= ConsumptionTimeline.EXHALE_START + 0.1f,
                "Smoke is breathed out after the draw, once the hand comes down");
        boolean exhales = false;
        for (int elapsed = 0; elapsed <= total; elapsed++) {
            if (!ConsumptionTimeline.exhaling(elapsed, total)) continue;
            exhales = true;
            require(ConsumptionTimeline.inhale(elapsed, total) == 0, "No exhaling while drawing at tick " + elapsed);
        }
        require(exhales, "Every puff has an exhale");
        require(ConsumptionTimeline.reach("drink", false, total * 0.5f, total) == 1
                && ConsumptionTimeline.tilt(total, total) == 1, "A sip tips the can at the mouth");
        int open = 20;
        require(ConsumptionTimeline.tabDegrees(false, true, 0, open) == 0
                && ConsumptionTimeline.tabDegrees(false, true, ConsumptionTimeline.TAB_POP * open + 1, open) > 45
                && Math.abs(ConsumptionTimeline.tabDegrees(false, true, open, open) - ConsumptionTimeline.OPENED_TAB_DEGREES) < 1e-3
                && ConsumptionTimeline.tabDegrees(true, false, 0, 1) == ConsumptionTimeline.OPENED_TAB_DEGREES,
                "The tab is pulled up, pops, then rests where an opened can keeps it");
        require(ConsumptionTimeline.otherHandReach(ConsumptionTimeline.TAB_POP * open, open) == 1
                && ConsumptionTimeline.otherHandReach(open, open) == 0, "The free hand is on the tab when it pops");
    }

    private static ConsumableDefinition parse(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return ConsumableDefinition.fromJson(object);
    }

    private static void expectInvalid(String json) {
        try { parse(json); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Invalid configuration accepted: " + json);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
