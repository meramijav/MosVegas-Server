package ru.mosvegas.items;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemGroups;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ConfigurationGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void definitionsLoadAgainWithoutChanges(TestContext context) {
        List<ConsumableDefinition> first = ConsumableDefinitionLoader.load();
        List<ConsumableDefinition> second = ConsumableDefinitionLoader.load();
        context.assertTrue(!first.isEmpty(), "First-run configuration must include the bundled items");
        context.assertTrue(first.equals(second), "Repeated configuration loading must preserve every definition");
        List<String> ids = first.stream().map(ConsumableDefinition::id).toList();
        context.assertTrue(ids.equals(first.stream().sorted(ConsumableDefinitionLoader.ORDER).map(ConsumableDefinition::id).toList()),
                "Definitions must load in stable tab order");
        context.assertTrue(ids.equals(new ArrayList<>(MosVegasItems.items().keySet())),
                "Loaded configuration IDs must match the registered items");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bundledFoodReachesExistingConfigurations(TestContext context) throws IOException {
        // Whether the configuration was created by 1.0.x (its marker only says "1") or by this version,
        // every bundled file ends up listed in the marker and copied once.
        List<String> seeded = Files.readAllLines(ConsumableDefinitionLoader.ROOT.resolve(".initialized"));
        context.assertTrue(seeded.contains("beer.json") && seeded.contains("food/pizza_pepperoni.json"),
                "The marker must list the original and the newly bundled files: " + seeded);
        context.assertTrue(Files.exists(ConsumableDefinitionLoader.ROOT.resolve("consumables/food/pizza_pepperoni.json")),
                "Bundled items added after the first run must be copied into the configuration");
        context.assertTrue(MosVegasItems.items().containsKey("pizza_pepperoni") && MosVegasItems.items().containsKey("caipirinha"),
                "Bundled food must be registered");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void groupsShareOneCreativeSlot(TestContext context) {
        var items = MosVegasItems.items();
        var pizza = ConsumableGroups.variants(items.get("pizza_pepperoni"));
        context.assertTrue(pizza.size() == 5 && pizza.get(0) == items.get("pizza_pepperoni") && pizza.contains(items.get("pizza_box")),
                "The first pizza in tab order represents all five pizzas");
        context.assertTrue(ConsumableGroups.hiddenInTab(items.get("pizza_box")) && !ConsumableGroups.hiddenInTab(items.get("pizza_pepperoni")),
                "Only the representative is shown in the tab");
        context.assertTrue(ConsumableGroups.variants(items.get("pizza_box")).isEmpty(), "Other variants do not open the list");
        context.assertTrue(!ConsumableGroups.hiddenInTab(items.get("cigarette")) && ConsumableGroups.group(items.get("cigarette")).isEmpty(),
                "Items without a group keep their own slot");
        // Tabs are filled when a creative player first opens them; a server has to ask for it.
        ItemGroups.updateDisplayContext(context.getWorld().getEnabledFeatures(), false, context.getWorld().getRegistryManager());
        var stacks = MosVegasItems.ITEM_GROUP.getDisplayStacks();
        context.assertTrue(stacks.stream().noneMatch(stack -> stack.isOf(items.get("pizza_box")))
                        && stacks.stream().anyMatch(stack -> stack.isOf(items.get("pizza_pepperoni"))),
                "The creative tab lists one slot per group");
        context.assertTrue(MosVegasItems.ITEM_GROUP.getSearchTabStacks().stream().anyMatch(stack -> stack.isOf(items.get("pizza_box"))),
                "Creative search still finds every variant");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void identicalGenerationPreservesModificationTimes(TestContext context) throws IOException {
        List<ConsumableDefinition> definitions = ConsumableDefinitionLoader.load();
        ConsumableDefinitionLoader.generateItemModels(definitions);
        Path generated = ConsumableDefinitionLoader.ROOT.resolve("generated");
        List<Path> files = new ArrayList<>();
        files.add(generated.resolve("pack.mcmeta"));
        files.add(ConsumableDefinitionLoader.ROOT.resolve("resources/pack.mcmeta"));
        for (ConsumableDefinition definition : definitions) {
            files.add(generated.resolve("assets/mosvegas_items/models/item/" + definition.id() + ".json"));
        }
        Map<Path, FileTime> originalTimes = new LinkedHashMap<>();
        FileTime sentinel = FileTime.fromMillis(1_234_567_890_000L);
        try {
            for (Path file : files) {
                originalTimes.put(file, Files.getLastModifiedTime(file));
                Files.setLastModifiedTime(file, sentinel);
            }
            ConsumableDefinitionLoader.load();
            ConsumableDefinitionLoader.generateItemModels(definitions);
            for (Path file : files) {
                context.assertTrue(Files.getLastModifiedTime(file).equals(sentinel),
                        "Unchanged generated files must not be rewritten: " + file.getFileName());
            }
        } finally {
            for (Map.Entry<Path, FileTime> entry : originalTimes.entrySet()) {
                Files.setLastModifiedTime(entry.getKey(), entry.getValue());
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void packMetadataEscapesDescription(TestContext context) throws IOException {
        Path root = ConsumableDefinitionLoader.ROOT.resolve("verification/metadata");
        String description = "MosVegas: \"quoted text\"\ncustom \\ resources";
        ConsumableDefinitionLoader.writePackMeta(root, description);
        JsonObject pack = JsonParser.parseString(Files.readString(root.resolve("pack.mcmeta")))
                .getAsJsonObject().getAsJsonObject("pack");
        context.assertTrue(pack.get("description").getAsString().equals(description),
                "Pack description must round-trip quotes, backslashes and line breaks");
        context.assertTrue(pack.get("pack_format").getAsInt() == 22, "Minecraft 1.20.4 pack format must stay 22");
        Files.write(root.resolve("pack.mcmeta"), new byte[] {(byte) 0xff});
        ConsumableDefinitionLoader.writePackMeta(root, description);
        context.assertTrue(JsonParser.parseString(Files.readString(root.resolve("pack.mcmeta"))).isJsonObject(),
                "Generation must repair corrupt metadata rather than fail while comparing its old content");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void definitionSchemaIsStrict(TestContext context) {
        context.assertTrue(ConsumableDefinition.enabled(json("{}")), "Items are enabled by default");
        context.assertFalse(ConsumableDefinition.enabled(json("{\"enabled\":false}")),
                "A boolean false must disable an item");
        rejects(context, () -> ConsumableDefinition.enabled(json("{\"enabled\":\"false\"}")),
                "enabled must be true or false");
        rejects(context, () -> ConsumableDefinition.enabled(json("{\"enabled\":null}")),
                "enabled must be true or false");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"effects\":null}", "effects must be an array");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"effects\":[null]}", "effects[0] must be an object");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"effects\":[{\"id\":\"speed\",\"duration\":0}]}",
                "effects[0]: duration");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"particles\":null}", "particles must be");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"particles\":\"smoke\"}", "particles must be");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"group\":\"Pizza Group\"}", "group must use");
        rejectsDefinition(context, "{\"id\":\"invalid\",\"order\":0.5}", "order must be");
        context.complete();
    }

    private static JsonObject json(String source) {
        return JsonParser.parseString(source).getAsJsonObject();
    }

    private static void rejectsDefinition(TestContext context, String source, String expectedMessage) {
        rejects(context, () -> ConsumableDefinition.fromJson(json(source)), expectedMessage);
    }

    private static void rejects(TestContext context, Supplier<?> parser, String expectedMessage) {
        try {
            parser.get();
        } catch (IllegalArgumentException exception) {
            context.assertTrue(exception.getMessage().contains(expectedMessage),
                    "Validation must identify the invalid field: " + exception.getMessage());
            return;
        }
        context.throwGameTestException("Invalid configuration was accepted: " + expectedMessage);
    }
}
