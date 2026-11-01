package ru.mosvegas.items;

import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

/** Regression checks in a real Fabric server; excluded from the release mod. */
public final class ConsumableGameTests implements FabricGameTest, ModInitializer {
    private static MosVegasConsumableItem legacyDrink;

    @Override
    public void onInitialize() {
        ConsumableDefinition definition = ConsumableDefinition.fromJson(JsonParser.parseString("""
                {"id":"legacy_drink","category":"drink","uses":5,"cooldown":40,"sound":"",
                 "effects":[{"id":"minecraft:slowness","duration":120}]}
                """).getAsJsonObject());
        legacyDrink = Registry.register(Registries.ITEM, new Identifier("mosvegas_items_tests", "legacy_drink"),
                new MosVegasConsumableItem(new Item.Settings().maxCount(16), definition));
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void chargesAndMetadata(TestContext context) {
        MosVegasConsumableItem cigarette = item(context, "cigarette");
        ItemStack stack = new ItemStack(cigarette, 3);
        context.assertTrue(cigarette.remainingUses(stack) == 5, "Untouched cigarettes have five portions");
        context.assertFalse(stack.hasNbt(), "Reading portions must not add NBT");
        context.assertFalse(cigarette.isItemBarVisible(stack), "Untouched items show no bar");
        stack.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, 5);
        context.assertFalse(cigarette.isItemBarVisible(stack), "A full item shows no bar even with stored portions");

        stack.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, Integer.MIN_VALUE);
        context.assertTrue(cigarette.remainingUses(stack) == 1, "Negative stored portions clamp to one");
        context.assertTrue(cigarette.isItemBarVisible(stack), "Partial items show the bar");
        context.assertTrue(cigarette.getItemBarStep(stack) == 3, "One of five portions rounds to three bar pixels");
        stack.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, Integer.MAX_VALUE);
        context.assertTrue(cigarette.remainingUses(stack) == 5, "Stored portions cannot exceed the configured maximum");
        stack.getOrCreateNbt().putString(MosVegasConsumableItem.USES_TAG, "2");
        context.assertTrue(cigarette.remainingUses(stack) == 5, "Non-numeric NBT uses the configured default");
        stack.getOrCreateNbt().putByte(MosVegasConsumableItem.USES_TAG, (byte) 2);
        context.assertTrue(cigarette.remainingUses(stack) == 2, "Numeric NBT types retain their portions");
        context.assertTrue(stack.getCount() == 3, "Reading the bar and portions does not change stack size");

        context.assertTrue(cigarette.getUseAction(stack) == UseAction.NONE, "Smoking has no vanilla eating animation");
        context.assertTrue(cigarette.getMaxUseTime(stack) == 48, "Configured smoking duration is preserved");
        MosVegasConsumableItem beer = item(context, "beer");
        context.assertTrue(beer.getUseAction(new ItemStack(beer)) == UseAction.NONE, "A closed can is opened first");
        context.assertTrue(beer.getUseAction(lastPortion(beer)) == UseAction.DRINK, "Opened beer uses the drinking action");
        MosVegasConsumableItem snack = item(context, "snack");
        context.assertTrue(snack.getUseAction(new ItemStack(snack)) == UseAction.EAT, "Flat-model food uses the eating action");
        context.assertTrue(snack.definition().model().isEmpty(), "An ordinary item needs no GeckoLib model");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void splitsUntouchedUnits(TestContext context) {
        MosVegasConsumableItem cigarette = item(context, "cigarette");
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(cigarette, 3));
        ItemStack partial = consume(context, player, cigarette);

        context.assertTrue(partial.getCount() == 1, "Only the active unit becomes partially consumed");
        context.assertTrue(cigarette.remainingUses(partial) == 4, "One portion is consumed");
        context.assertTrue(partial.getNbt() != null && partial.getNbt().containsUuid("MosVegasUnit"),
                "A partial unit has an identity to prevent merging with other partial units");
        context.assertTrue(totalUnits(player, cigarette) == 3, "Splitting preserves all three units");
        context.assertTrue(totalPortions(player, cigarette) == 14, "Splitting consumes exactly one of fifteen portions");
        int untouched = player.getInventory().main.stream()
                .filter(stack -> stack.isOf(cigarette) && !stack.hasNbt()).mapToInt(ItemStack::getCount).sum();
        context.assertTrue(untouched == 2, "The other two units retain their full portions and clean NBT");
        context.assertTrue(player.getItemCooldownManager().isCoolingDown(cigarette), "Consumption starts the configured cooldown");
        var slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
        context.assertTrue(slowness != null && slowness.getDuration() == 120 && slowness.getAmplifier() == 0,
                "The guaranteed configured effect is applied with its original duration");
        // Exercise the cached sound and exhaled-smoke paths over a whole puff.
        for (int remaining = cigarette.getMaxUseTime(partial); remaining > 0; remaining--)
            cigarette.usageTick(context.getWorld(), player, partial, remaining);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void consumesFinalPortion(TestContext context) {
        MosVegasConsumableItem cigarette = item(context, "cigarette");
        PlayerEntity player = survivalPlayer(context);
        ItemStack stack = new ItemStack(cigarette);
        stack.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, 1);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        ItemStack result = consume(context, player, cigarette);
        context.assertTrue(result.isEmpty(), "The final portion consumes the unit");
        context.assertTrue(totalUnits(player, cigarette) == 0, "Finishing a unit does not duplicate it");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeKeepsPortions(TestContext context) {
        context.assertTrue(legacyDrink != null, "The test-only legacy drink is registered");
        MosVegasConsumableItem cigarette = legacyDrink;
        PlayerEntity player = context.createMockCreativePlayer();
        // Vanilla mock players override isCreative(), but do not set the ability used by consumption.
        player.getAbilities().creativeMode = true;
        ItemStack stack = new ItemStack(cigarette, 3);
        UUID identity = UUID.randomUUID();
        stack.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, 2);
        stack.getOrCreateNbt().putUuid("MosVegasUnit", identity);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        ItemStack result = consume(context, player, cigarette);
        context.assertTrue(result == stack && result.getCount() == 3, "Creative consumption retains the original stack");
        context.assertTrue(cigarette.remainingUses(result) == 2, "Creative consumption retains all portions");
        context.assertTrue(identity.equals(result.getNbt().getUuid("MosVegasUnit")), "Creative consumption retains unit identity");
        context.assertTrue(player.hasStatusEffect(StatusEffects.SLOWNESS), "Creative consumption still applies effects");
        context.assertTrue(player.getItemCooldownManager().isCoolingDown(cigarette), "Creative consumption still applies cooldown");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void fullInventoryDropsUntouchedUnits(TestContext context) {
        fullInventoryDrops(context, false);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeFullInventoryDropsUntouchedUnits(TestContext context) {
        fullInventoryDrops(context, true);
    }

    private static void fullInventoryDrops(TestContext context, boolean creative) {
        MosVegasConsumableItem cigarette = item(context, "cigarette");
        // Plain PlayerEntity mocks only construct dropped entities. ServerPlayerEntity
        // also spawns them into the world, matching the production inventory path.
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(creative ? GameMode.CREATIVE : GameMode.SURVIVAL);
        player.getAbilities().creativeMode = creative;
        player.setPosition(context.getAbsolute(new Vec3d(2, 1, 2)));
        for (int slot = 0; slot < player.getInventory().main.size(); slot++) {
            player.getInventory().main.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(cigarette, 3));
        ItemStack partial = consume(context, player, cigarette);
        context.assertTrue(partial.getCount() == 1 && cigarette.remainingUses(partial) == 4,
                "A full inventory retains the consumed partial unit in the active hand");
        List<ItemEntity> drops = context.getEntitiesAround(EntityType.ITEM, new BlockPos(2, 1, 2), 3)
                .stream().filter(entity -> entity.getStack().isOf(cigarette)).toList();
        int droppedUnits = drops.stream().mapToInt(entity -> entity.getStack().getCount()).sum();
        int droppedPortions = drops.stream()
                .mapToInt(entity -> entity.getStack().getCount() * cigarette.remainingUses(entity.getStack())).sum();
        context.assertTrue(droppedUnits == 2, "The two untouched units are dropped when no inventory slot is free");
        context.assertTrue(totalPortions(player, cigarette) + droppedPortions == 14,
                "Dropping excess units consumes exactly one portion without loss or duplication");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void canOpeningBelongsToOneUnit(TestContext context) {
        canOpening(context, false);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeCanOpeningBelongsToOneUnit(TestContext context) {
        canOpening(context, true);
    }

    private static void canOpening(TestContext context, boolean creative) {
        MosVegasConsumableItem beer = item(context, "beer");
        PlayerEntity player = creative ? context.createMockCreativePlayer() : survivalPlayer(context);
        player.getAbilities().creativeMode = creative;
        int hunger = player.getHungerManager().getFoodLevel();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(beer, 3));
        ItemStack closed = player.getStackInHand(Hand.MAIN_HAND);
        context.assertTrue(beer.isOpening(closed) && beer.getMaxUseTime(closed) == beer.definition().openTime()
                && beer.getUseAction(closed) == UseAction.NONE, "A closed can is first opened by its own short use");
        context.assertTrue(beer.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.CONSUME,
                "A fresh can can be opened");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND) == closed && closed.getCount() == 3 && !closed.hasNbt(),
                "Starting to open leaves the stack untouched until the tab pops");
        ItemStack opened = beer.finishUsing(closed, context.getWorld(), player);
        player.setStackInHand(Hand.MAIN_HAND, opened);
        player.stopUsingItem();
        context.assertTrue(opened.getCount() == 1 && beer.isOpened(opened), "Opening isolates and marks exactly one can");
        context.assertTrue(beer.remainingUses(opened) == beer.definition().uses(), "Opening does not consume a sip");
        context.assertFalse(player.getItemCooldownManager().isCoolingDown(beer), "Opening does not start the cooldown");
        context.assertTrue(player.getHungerManager().getFoodLevel() == hunger && !player.hasStatusEffect(StatusEffects.NAUSEA)
                && !player.hasStatusEffect(StatusEffects.STRENGTH), "Opening neither feeds nor applies effects");
        context.assertTrue(beer.getMaxUseTime(opened) == beer.definition().useTime() && beer.getUseAction(opened) == UseAction.DRINK,
                "An opened can is drunk with the configured duration");
        UUID identity = opened.getNbt().getUuid("MosVegasUnit");
        int unopened = player.getInventory().main.stream()
                .filter(stack -> stack.isOf(beer) && !beer.isOpened(stack)).mapToInt(ItemStack::getCount).sum();
        context.assertTrue(unopened == 2, "The rest of the original stack stays unopened");
        context.assertTrue(beer.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.CONSUME,
                "An opened can can be drunk");
        player.stopUsingItem();
        context.assertTrue(beer.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.CONSUME,
                "An interrupted can can still be consumed");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND) == opened
                        && identity.equals(opened.getNbt().getUuid("MosVegasUnit")),
                "Restarting retains the same opened unit instead of opening another can");
        player.stopUsingItem();
        ItemStack partial = consume(context, player, beer);
        context.assertTrue(beer.isOpened(partial) && identity.equals(partial.getNbt().getUuid("MosVegasUnit")),
                "The opening marker and unit identity survive consumption");
        context.assertTrue(beer.remainingUses(partial) == beer.definition().uses() - 1,
                "A completed sip reduces durability by one");
        context.assertTrue(totalPortions(player, beer) == 3 * beer.definition().uses() - 1,
                "Can opening and stack partitioning neither create nor lose drink portions");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blockedUseLeavesCanUnopened(TestContext context) {
        MosVegasConsumableItem beer = item(context, "beer");
        PlayerEntity player = survivalPlayer(context);
        ItemStack cans = new ItemStack(beer, 2);
        player.setStackInHand(Hand.MAIN_HAND, cans);
        player.getItemCooldownManager().set(beer, 20);
        context.assertTrue(beer.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.FAIL,
                "Cooldown blocks can opening");
        context.assertFalse(beer.isOpened(cans), "A failed use does not trigger the opening state");
        context.assertTrue(cans.getCount() == 2 && !cans.hasNbt(), "A failed use leaves the entire stack untouched");
        player.getItemCooldownManager().remove(beer);
        context.assertTrue(beer.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.CONSUME,
                "Without a cooldown the can starts opening");
        player.stopUsingItem();
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND) == cans && cans.getCount() == 2 && !cans.hasNbt(),
                "Letting go before the tab pops leaves the cans closed");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void finishedCigaretteDropsButt(TestContext context) {
        // Relative layer 0 is the ground the test structure stands on (it also holds the structure block).
        for (String id : List.of("cigarette", "energy_drink", "snack")) {
            MosVegasConsumableItem item = item(context, id);
            PlayerEntity player = survivalPlayer(context);
            player.getHungerManager().setFoodLevel(10);
            player.setStackInHand(Hand.MAIN_HAND, lastPortion(item));
            clearDrops(context);
            ItemStack result = consume(context, player, item);
            context.assertFalse(result.isOf(item), "The final portion uses up the unit: " + id);
            List<LitterEntity> litter = litter(context);
            if (!item.definition().discard().equals("drop")) {
                context.assertTrue(litter.isEmpty(), "Only items configured to drop leave litter by themselves: " + id);
                continue;
            }
            context.assertTrue(litter.size() == 1 && litter.get(0).isButt(), "The smoked cigarette leaves one butt");
            ItemStack shown = litter.get(0).getStack();
            context.assertTrue(shown.isOf(item) && shown.getCount() == 1 && MosVegasConsumableItem.isSpent(shown),
                    "The butt shows one spent cigarette");
            context.assertTrue(litter.get(0).getVelocity().horizontalLength() < 0.15, "The butt falls near the feet");
            for (int tick = 0; tick < 40 && !litter.get(0).isOnGround(); tick++) litter.get(0).tick();
            context.assertTrue(litter.get(0).isOnGround(), "The butt reaches the floor within two seconds");
            // The mock player hovers one block above the ground, so the butt falls further than usual.
            context.assertTrue(litter.get(0).getPos().subtract(player.getPos()).horizontalLength() < 1.5,
                    "The butt lies near the smoker: " + context.getRelative(litter.get(0).getPos()) + " player " + context.getRelative(player.getPos()));
        }
        clearDrops(context);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void emptyCanIsThrownWithDropKey(TestContext context) {
        MosVegasConsumableItem beer = item(context, "beer");
        context.assertTrue(beer.definition().discard().equals("throw"), "Beer cans are thrown away by the player");
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.getAbilities().creativeMode = false;
        player.setPosition(context.getAbsolute(new Vec3d(2, 1, 2)));
        player.setStackInHand(Hand.MAIN_HAND, lastPortion(beer));
        clearDrops(context);
        ItemStack empty = consume(context, player, beer);
        context.assertTrue(empty.isOf(beer) && empty.getCount() == 1 && MosVegasConsumableItem.isSpent(empty),
                "The last sip leaves the empty can in the hand");
        context.assertTrue(litter(context).isEmpty(), "The can is not thrown by itself");
        context.assertTrue(beer.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.PASS,
                "An empty can cannot be drunk again");
        context.assertTrue(ItemStack.canCombine(empty, beer.empty()), "Empty cans stack with each other");

        player.setPitch(-40);
        player.dropSelectedItem(false);
        context.assertTrue(player.getMainHandStack().isEmpty(), "The drop key throws the empty can out of the hand");
        List<LitterEntity> litter = litter(context);
        context.assertTrue(litter.size() == 1 && !litter.get(0).isButt(), "The thrown can becomes litter");
        context.assertTrue(context.getEntitiesAround(EntityType.ITEM, new BlockPos(2, 1, 2), 4).isEmpty(),
                "No item entity is dropped instead");
        Vec3d velocity = litter.get(0).getVelocity();
        context.assertTrue(velocity.y > 0.4 && velocity.z > 0.5, "Looking up throws the can high and far");
        LitterEntity can = litter.get(0);
        double startY = can.getY();
        int rising = 0;
        while (can.getVelocity().y > 0 && rising < 40) {
            can.tick();
            rising++;
        }
        double peak = can.getY();
        for (int tick = 0; tick < 8; tick++) can.tick();
        context.assertTrue(peak - startY > 1.5 && rising < 15 && can.getY() < peak - 1,
                "A thrown can rises quickly, then falls back down instead of floating");
        litter.forEach(LitterEntity::discard);

        // Death drops and full-inventory spills keep the vanilla item entity.
        ItemStack dropped = beer.empty();
        context.assertTrue(player.dropItem(dropped, true, false) != null, "Random drops keep vanilla behavior");
        clearDrops(context);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void drinksHaveFiniteDurability(TestContext context) {
        for (String id : List.of("beer", "energy_drink")) {
            MosVegasConsumableItem drink = item(context, id);
            PlayerEntity player = survivalPlayer(context);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(drink, 2));
            context.assertTrue(drink.definition().uses() > 1, "Built-in drinks contain several sips");
            // A can is opened first; opening is not a sip.
            if (drink.isOpening(player.getStackInHand(Hand.MAIN_HAND))) consume(context, player, drink);
            for (int sip = 0; sip < drink.definition().uses(); sip++) {
                player.getItemCooldownManager().remove(drink);
                consume(context, player, drink);
            }
            ItemStack result = player.getStackInHand(Hand.MAIN_HAND);
            if (drink.definition().discard().equals("throw")) {
                context.assertTrue(result.isOf(drink) && result.getCount() == 1 && MosVegasConsumableItem.isSpent(result),
                        "The fully consumed drink leaves its empty can in the hand: " + id);
            } else if (drink.definition().remainder().isEmpty()) {
                context.assertTrue(result.isEmpty(), "The fully consumed drink disappears: " + id);
            } else {
                Item remainder = Registries.ITEM.get(new Identifier(drink.definition().remainder()));
                context.assertTrue(result.isOf(remainder) && result.getCount() == 1,
                        "The fully consumed drink leaves exactly one configured container: " + id);
            }
            context.assertTrue(totalUnits(player, drink) == 1 && totalPortions(player, drink) == drink.definition().uses(),
                    "The other drink retains its full durability: " + id);
            context.assertTrue(player.getInventory().main.stream().filter(stack -> stack.isOf(drink) && !MosVegasConsumableItem.isSpent(stack))
                    .noneMatch(drink::isOpened), "The spare drink remains unopened: " + id);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void roleplayItemsWearInCreative(TestContext context) {
        MosVegasConsumableItem cigarette = item(context, "cigarette");
        context.assertTrue(cigarette.definition().consumeInCreative(), "Roleplay cigarettes enable creative depletion");
        PlayerEntity player = context.createMockCreativePlayer();
        player.getAbilities().creativeMode = true;
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(cigarette, 3));
        ItemStack partial = consume(context, player, cigarette);
        context.assertTrue(partial.getCount() == 1 && cigarette.remainingUses(partial) == 4,
                "Creative roleplay consumption reduces one unit's durability");
        context.assertTrue(totalUnits(player, cigarette) == 3 && totalPortions(player, cigarette) == 14,
                "Creative splitting preserves untouched units and consumes exactly one portion");
        for (int portion = 1; portion < cigarette.definition().uses(); portion++) {
            player.getItemCooldownManager().remove(cigarette);
            consume(context, player, cigarette);
        }
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).isEmpty(), "The smoked cigarette disappears in creative too");
        context.assertTrue(totalUnits(player, cigarette) == 2 && totalPortions(player, cigarette) == 10,
                "Only the active cigarette is consumed in creative");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeRightClickPreservesFiniteUnits(TestContext context) {
        for (String id : List.of("beer", "cigarette")) {
            MosVegasConsumableItem item = item(context, id);
            var player = context.createMockCreativeServerPlayerInWorld();
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().creativeMode = true;
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(item, 3));
            if (item.isOpening(player.getStackInHand(Hand.MAIN_HAND))) {
                context.assertTrue(player.interactionManager.interactItem(player, context.getWorld(),
                        player.getStackInHand(Hand.MAIN_HAND), Hand.MAIN_HAND) == ActionResult.CONSUME,
                        "The vanilla interaction path starts opening in creative: " + id);
                ItemStack opened = item.finishUsing(player.getStackInHand(Hand.MAIN_HAND), context.getWorld(), player);
                player.setStackInHand(Hand.MAIN_HAND, opened);
                player.stopUsingItem();
                context.assertTrue(opened.getCount() == 1 && item.isOpened(opened) && totalUnits(player, item) == 3
                                && totalPortions(player, item) == 3 * item.definition().uses(),
                        "Creative opening isolates one unit without spending a portion: " + id);
            }
            ItemStack original = player.getStackInHand(Hand.MAIN_HAND);
            context.assertTrue(player.interactionManager.interactItem(player, context.getWorld(), original, Hand.MAIN_HAND)
                    == ActionResult.CONSUME, "The vanilla interaction path starts creative consumption: " + id);
            context.assertTrue(totalUnits(player, item) == 3 && totalPortions(player, item) == 3 * item.definition().uses(),
                    "Vanilla creative count restoration must not duplicate isolated units: " + id);
            ItemStack partial = item.finishUsing(player.getStackInHand(Hand.MAIN_HAND), context.getWorld(), player);
            player.setStackInHand(Hand.MAIN_HAND, partial);
            player.stopUsingItem();
            context.assertTrue(partial.getCount() == 1 && totalUnits(player, item) == 3
                            && totalPortions(player, item) == 3 * item.definition().uses() - 1,
                    "Completing normal creative consumption spends exactly one portion: " + id);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void legacyCreativeRightClickKeepsPortions(TestContext context) {
        context.assertTrue(legacyDrink != null, "The test-only legacy drink is registered");
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.CREATIVE);
        player.getAbilities().creativeMode = true;
        ItemStack stack = new ItemStack(legacyDrink, 3);
        stack.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, 2);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        context.assertTrue(player.interactionManager.interactItem(player, context.getWorld(), stack, Hand.MAIN_HAND)
                == ActionResult.CONSUME, "Legacy items still use the vanilla creative interaction path");
        ItemStack result = legacyDrink.finishUsing(player.getStackInHand(Hand.MAIN_HAND), context.getWorld(), player);
        player.setStackInHand(Hand.MAIN_HAND, result);
        player.stopUsingItem();
        context.assertTrue(result == stack && result.getCount() == 3 && legacyDrink.remainingUses(result) == 2,
                "A disabled creative-depletion flag preserves vanilla stack and portion behavior");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void hungerAndCooldownGate(TestContext context) {
        MosVegasConsumableItem snack = item(context, "snack");
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(snack));
        context.assertTrue(snack.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.FAIL,
                "Ordinary food cannot be consumed at full hunger");
        player.getHungerManager().setFoodLevel(10);
        ItemStack result = consume(context, player, snack);
        context.assertTrue(result.isEmpty() && player.getHungerManager().getFoodLevel() == 16,
                "Eating consumes the snack and restores its configured hunger");
        MosVegasConsumableItem cigarette = item(context, "cigarette");
        player.getHungerManager().setFoodLevel(20);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(cigarette));
        context.assertTrue(cigarette.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.CONSUME,
                "Always-edible items work at full hunger");
        player.stopUsingItem();
        player.getItemCooldownManager().set(cigarette, 40);
        context.assertTrue(cigarette.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.FAIL,
                "An active cooldown blocks use");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void bundledFoodIsEatenInEveryMode(TestContext context) {
        MosVegasConsumableItem pizza = item(context, "pizza_pepperoni");
        context.assertFalse(pizza.isItemBarVisible(new ItemStack(pizza)), "A whole pizza shows no bar");
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(pizza));
        ItemStack bitten = consume(context, player, pizza);
        context.assertTrue(pizza.remainingUses(bitten) == 3 && pizza.isItemBarVisible(bitten),
                "A player at full hunger eats a slice; the bar appears after the first one");
        MosVegasConsumableItem burger = item(context, "cheeseburger_small");
        var creative = context.createMockCreativeServerPlayerInWorld();
        creative.changeGameMode(GameMode.CREATIVE);
        creative.getAbilities().creativeMode = true;
        creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(burger, 2));
        ItemStack rest = consume(context, creative, burger);
        context.assertTrue(rest.getCount() == 1, "Bundled food is eaten in creative as well");
        context.complete();
    }

    private static MosVegasConsumableItem item(TestContext context, String id) {
        MosVegasConsumableItem item = MosVegasItems.items().get(id);
        context.assertTrue(item != null, "The built-in test item must be registered: " + id);
        return item;
    }

    private static PlayerEntity survivalPlayer(TestContext context) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.setPosition(context.getAbsolute(new Vec3d(2, 1, 2)));
        return player;
    }

    private static ItemStack consume(TestContext context, PlayerEntity player, MosVegasConsumableItem item) {
        context.assertTrue(item.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == ActionResult.CONSUME,
                "The test player must be able to start consuming the item");
        ItemStack result = item.finishUsing(player.getStackInHand(Hand.MAIN_HAND), context.getWorld(), player);
        player.setStackInHand(Hand.MAIN_HAND, result);
        player.stopUsingItem();
        return result;
    }

    private static ItemStack lastPortion(MosVegasConsumableItem item) {
        ItemStack last = new ItemStack(item);
        last.getOrCreateNbt().putInt(MosVegasConsumableItem.USES_TAG, 1);
        last.getOrCreateNbt().putBoolean(MosVegasConsumableItem.OPENED_TAG, true);
        return last;
    }

    private static List<LitterEntity> litter(TestContext context) {
        return context.getEntitiesAround(MosVegasItems.LITTER, new BlockPos(2, 1, 2), 4);
    }

    private static void clearDrops(TestContext context) {
        litter(context).forEach(Entity::discard);
        context.getEntitiesAround(EntityType.ITEM, new BlockPos(2, 1, 2), 4).forEach(Entity::discard);
    }

    // Empty cans are containers, not units with portions.
    private static int totalUnits(PlayerEntity player, MosVegasConsumableItem item) {
        return player.getInventory().main.stream().filter(stack -> stack.isOf(item) && !MosVegasConsumableItem.isSpent(stack))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int totalPortions(PlayerEntity player, MosVegasConsumableItem item) {
        return player.getInventory().main.stream().filter(stack -> stack.isOf(item) && !MosVegasConsumableItem.isSpent(stack))
                .mapToInt(stack -> stack.getCount() * item.remainingUses(stack)).sum();
    }
}
