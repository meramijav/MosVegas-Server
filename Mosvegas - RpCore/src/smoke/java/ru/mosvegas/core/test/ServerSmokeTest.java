package ru.mosvegas.core.test;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import ru.mosvegas.core.api.*;
import ru.mosvegas.core.config.*;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.shop.*;
import ru.mosvegas.core.util.Permissions;
import ru.mosvegas.core.zone.*;
import java.nio.file.*;
import java.util.*;

public final class ServerSmokeTest implements ModInitializer {
    private static int assertions;
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                test(server);
                String result = "ServerSmokeTest: " + assertions + " assertions passed";
                System.out.println(result);
                Files.writeString(Path.of("smoke-result.txt"), result);
            } catch (Throwable error) {
                error.printStackTrace();
                try { Files.writeString(Path.of("smoke-result.txt"), "FAILED: " + error); } catch (Exception ignored) {}
            } finally { server.stop(false); }
        });
    }
    private static void test(MinecraftServer server) throws Exception {
        DataStore.load(server);
        ServerWorld world = server.getOverworld();
        var player = player(server, "ZoneTester");
        var victim = player(server, "ZoneVictim");
        // World data is plain JSON: the release JAR carries no SQLite driver.
        DataStore.player(player.getUuid()).serverLevelTicks = 424242;
        DataStore.save();
        DataStore.flush();
        yes(Files.readString(server.getSavePath(WorldSavePath.ROOT).resolve("mosvegas-core-data.json")).contains("424242"), "world data saved as JSON");
        player.setPosition(1, 100, 1); victim.setPosition(2, 100, 1);
        ZoneStore.update(s -> { s.zones.clear(); s.protectedWorlds.add("minecraft:overworld"); });
        ShopStore.update(s -> s.shops.clear());
        BlockPos pos = new BlockPos(1, 100, 1);
        no(ZoneService.can(player, world, pos, ZoneAction.BUILD), "global city build protection");
        yes(ZoneService.can(player, world, pos, ZoneAction.DOORS), "global doors open by default");
        testBlockUseGuard(server, world);
        Permissions.grant(player.getUuid(), "mosvegas.admin.zones");
        command(server, player, "mosvegas zone world minecraft:overworld false");
        yes(ZoneService.can(player, world, pos, ZoneAction.BUILD), "namespaced world command disables protection");
        command(server, player, "mosvegas zone world minecraft:overworld true");
        no(ZoneService.can(player, world, pos, ZoneAction.BUILD), "namespaced world command restores protection");
        command(server, player, "mosvegas zone world custom:test_dimension true");
        yes(ZoneStore.get().protectedWorlds.contains("custom:test_dimension"), "custom dimension identifier accepted");
        command(server, player, "mosvegas zone world custom:test_dimension false");
        no(ZoneStore.get().protectedWorlds.contains("custom:test_dimension"), "custom dimension protection removed");
        command(server, player, "mosvegas zone pos1 0 90 0");
        command(server, player, "mosvegas zone pos2 10 110 10");
        command(server, player, "mosvegas zone create police 10");
        command(server, player, "mosvegas zone flag police doors members");
        command(server, player, "mosvegas zone flag police containers members");
        command(server, player, "mosvegas zone flag police pvp deny");
        command(server, player, "mosvegas zone flag police shoot deny");
        command(server, player, "mosvegas zone grant police faction police doors");
        no(ZoneService.can(player, world, pos, ZoneAction.DOORS), "civilian doors denied");
        DataStore.player(player.getUuid()).faction = "police";
        yes(ZoneService.can(player, world, pos, ZoneAction.DOORS), "police doors allowed");
        no(ZoneService.can(player, world, pos, ZoneAction.CONTAINERS), "door grant does not leak to containers");
        no(ZoneService.shoot(player), "faction cannot bypass no-shoot");
        testTaczEvents(player, victim);
        no(victim.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.POISON, 200), player), "hostile splash potion blocked");
        no(ZoneService.damage(victim, world.getDamageSources().playerAttack(player)), "PVP damage blocked");
        no(ZoneService.gunHit(player, victim), "gun hit blocked");
        player.setPosition(30, 100, 30);
        no(ZoneService.gunHit(player, victim), "outside shot into safe zone blocked");
        no(ZoneService.damage(victim, world.getDamageSources().playerAttack(player)), "outside melee into safe zone blocked");
        player.setPosition(1, 100, 1);
        command(server, player, "mosvegas zone grant police player " + player.getUuidAsString() + " containers");
        yes(ZoneService.can(player, world, pos, ZoneAction.CONTAINERS), "individual container grant");
        command(server, player, "mosvegas zone revoke police player " + player.getUuidAsString() + " containers");
        no(ZoneService.can(player, world, pos, ZoneAction.CONTAINERS), "revocation immediate");
        ZoneStore.reload();
        yes(ZoneService.can(player, world, pos, ZoneAction.DOORS), "zone persisted");
        world.setBlockState(pos, Blocks.CHEST.getDefaultState());
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        yes(UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, hit) == ActionResult.FAIL, "actual chest event denied");
        no(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, world.getBlockState(pos), world.getBlockEntity(pos)), "actual break event denied");
        no(world.breakBlock(pos, false), "environment cannot break city");
        ItemStack blocks = new ItemStack(Items.STONE, 8);
        player.setStackInHand(Hand.MAIN_HAND, blocks);
        yes(((BlockItem)Items.STONE).place(new ItemPlacementContext(player, Hand.MAIN_HAND, blocks, hit)) == ActionResult.FAIL, "actual placement mixin denied");
        yes(blocks.getCount() == 8 && world.getBlockState(pos.up()).isAir(), "denied placement consumes nothing");
        world.createExplosion(null, 1.5, 100.5, 1.5, 4, World.ExplosionSourceType.TNT);
        yes(world.getBlockState(pos).isOf(Blocks.CHEST), "actual explosion preserves chest");
        no(world.setBlockState(pos.up(), Blocks.FIRE.getDefaultState()), "fire cannot be placed in city");
        new net.minecraft.entity.mob.EndermanEntity(net.minecraft.entity.EntityType.ENDERMAN, world);
        world.setBlockState(pos.up(2), Blocks.FARMLAND.getDefaultState());
        FarmlandBlock.setToDirt(player, world.getBlockState(pos.up(2)), world, pos.up(2));
        yes(world.getBlockState(pos.up(2)).isOf(Blocks.FARMLAND), "trampling cannot destroy protected farmland");
        BlockPos sand = new BlockPos(5, 105, 5);
        world.setBlockState(sand, Blocks.SAND.getDefaultState());
        ((FallingBlock)Blocks.SAND).scheduledTick(world.getBlockState(sand), world, sand, world.random);
        yes(world.getBlockState(sand).isOf(Blocks.SAND), "protected sand cannot begin falling");
        var falling = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(world, sand, Blocks.SAND.getDefaultState());
        world.setBlockState(new BlockPos(5, 99, 5), Blocks.STONE.getDefaultState());
        falling.setPosition(5.5, 102, 5.5);
        for (int tick = 0; tick < 80 && !falling.isRemoved(); tick++) falling.tick();
        yes(world.getBlockState(new BlockPos(5, 100, 5)).isAir(), "incoming falling block cannot be placed in city");
        command(server, player, "mosvegas zone flag police vehicle_place deny");
        no(TransportAccess.canPlace(player, world, new Box(9.5, 100, 1, 12, 102, 3)), "vehicle footprint crossing boundary denied");
        yes(TransportAccess.canPlace(player, world, new Box(20, 100, 20, 23, 102, 23)), "vehicle placement outside zone allowed");

        Permissions.grant(player.getUuid(), "mosvegas.admin.shops");
        command(server, player, "mosvegas shopadmin create test Test Shop");
        ItemStack item = new ItemStack(Items.DIAMOND, 3);
        item.setCustomName(Text.literal("Custom NBT")); item.getOrCreateNbt().putString("gun_id", "fixture:gun");
        player.setStackInHand(Hand.MAIN_HAND, item);
        command(server, player, "mosvegas shopadmin add test gems 125");
        yes(player.getMainHandStack().getCount() == 3, "catalog copies template");
        player.getInventory().clear();
        DataStore.player(player.getUuid()).bankBalance = 500;
        long revision = ShopStore.revision();
        yes(ShopService.buy(player, "test", "gems", revision), "buy succeeds");
        yes(DataStore.player(player.getUuid()).bankBalance == 375, "charged exact amount");
        ItemStack bought = player.getInventory().getStack(0);
        yes(bought.getCount() == 3 && "fixture:gun".equals(bought.getNbt().getString("gun_id")), "quantity and NBT preserved");
        no(bought.getOrCreateSubNbt("display").contains("Lore"), "price preview not copied to purchased item");
        DataStore.player(player.getUuid()).bankBalance = 5;
        no(ShopService.buy(player, "test", "gems", revision), "insufficient balance refused");
        yes(bought.getCount() == 3 && DataStore.player(player.getUuid()).bankBalance == 5, "failed buy unchanged");
        DataStore.player(player.getUuid()).bankBalance = 1000;
        for (int i = 0; i < 36; i++) player.getInventory().setStack(i, new ItemStack(Items.COBBLESTONE, 64));
        no(ShopService.buy(player, "test", "gems", revision), "full inventory refused");
        yes(DataStore.player(player.getUuid()).bankBalance == 1000, "full inventory not charged");
        command(server, player, "mosvegas shopadmin price test gems 200");
        player.getInventory().clear();
        no(ShopService.buy(player, "test", "gems", revision), "stale confirmation refused");
        command(server, player, "mosvegas shopadmin enabled test false");
        no(ShopService.buy(player, "test", "gems", ShopStore.revision()), "closed shop refuses purchase");
        command(server, player, "mosvegas shopadmin enabled test true");
        ShopStore.reload();
        yes(ShopStore.offer(ShopStore.find(ShopStore.get(), "test"), "gems").price == 200, "catalog persisted");
        yes(ShopService.buy(player, "test", "gems", ShopStore.revision()), "purchase after reload");
        command(server, player, "mosvegas shop test");
        yes(player.currentScreenHandler.slots.size() == 90, "shop menu opens");
        int before = player.getInventory().count(Items.DIAMOND);
        player.currentScreenHandler.onSlotClick(0, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, player);
        yes(player.getInventory().count(Items.DIAMOND) == before, "shift-click cannot steal display items");
        player.closeHandledScreen();
        Path zones = server.getSavePath(WorldSavePath.ROOT).resolve("mosvegas-zones.json");
        String valid = Files.readString(zones);
        try {
            Files.writeString(zones, "{broken");
            try { ZoneStore.reload(); throw new AssertionError("bad JSON accepted"); } catch (com.google.gson.JsonParseException expected) { assertions++; }
            yes(ZoneService.can(player, world, pos, ZoneAction.DOORS), "invalid reload keeps protection");
        } finally { Files.writeString(zones, valid); }
        testMoney(server, player);
        testProperties(server, world, player, victim);
    }

    private static void testMoney(MinecraftServer server, ServerPlayerEntity player) throws Exception {
        var dispatcher = server.getCommandManager().getDispatcher();
        // Level 2 only lets "@s" parse: the fake player is not in the player list, and the mod's check reads the player, not the source level.
        var source = player.getCommandSource().withLevel(2);
        DataStore.player(player.getUuid()).bankBalance = 1000;
        try { dispatcher.execute("mosvegas money give @s 500", source); throw new AssertionError("money command without permission"); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { assertions++; }
        yes(DataStore.player(player.getUuid()).bankBalance == 1000, "money unchanged without permission");
        Permissions.grant(player.getUuid(), "mosvegas.admin.money");
        yes(dispatcher.execute("mosvegas money give @s 500", source) == 1, "money give accepted");
        yes(DataStore.player(player.getUuid()).bankBalance == 1500, "money give adds to bank balance");
        yes(dispatcher.execute("mosvegas money take @s 300", source) == 1, "money take accepted");
        yes(DataStore.player(player.getUuid()).bankBalance == 1200, "money take subtracts from bank balance");
        yes(dispatcher.execute("mosvegas money take @s 5000", source) == 0, "money take above balance refused");
        yes(DataStore.player(player.getUuid()).bankBalance == 1200, "refused take leaves balance unchanged");
        yes(dispatcher.execute("mosvegas money set @s " + Long.MAX_VALUE, source) == 1, "money set accepted");
        yes(dispatcher.execute("mosvegas money give @s 1", source) == 0, "money give overflow refused");
        yes(dispatcher.execute("mosvegas money set @s 0", source) == 1, "money set to zero accepted");
        yes(DataStore.player(player.getUuid()).bankBalance == 0, "money set replaces bank balance");
        Permissions.revoke(player.getUuid(), "mosvegas.admin.money");
    }
    private static void testProperties(MinecraftServer server, ServerWorld world, ServerPlayerEntity outsider, ServerPlayerEntity buyer) throws Exception {
        ConfigManager.get().properties.removeIf(p -> p.id.startsWith("smoke_"));
        deleteSmokeRecords();
        // П-shape: two arms joined by a base; the courtyard between the arms is not part of the apartment.
        var apartment = new MosvegasConfig.Property("smoke_u", "Smoke П", "APARTMENT", 1000, 100, new MosvegasConfig.Point("minecraft:overworld", 204, 100, 206, 0));
        apartment.areas.add(new MosvegasConfig.Area(200, 100, 200, 202, 103, 212));
        apartment.areas.add(new MosvegasConfig.Area(208, 100, 200, 206, 103, 212));
        apartment.areas.add(new MosvegasConfig.Area(203, 100, 210, 205, 103, 212));
        ConfigManager.get().properties.add(apartment);
        BlockPos leftArm = new BlockPos(201, 100, 201), rightArm = new BlockPos(207, 101, 205), base = new BlockPos(204, 100, 211), courtyard = new BlockPos(204, 100, 202);
        for (ZoneAction action : List.of(ZoneAction.DOORS, ZoneAction.CONTAINERS, ZoneAction.INTERACT, ZoneAction.ENTITIES, ZoneAction.BUILD))
            for (BlockPos at : List.of(leftArm, rightArm, base)) no(ZoneService.can(buyer, world, at, action), "free apartment closed: " + action.id() + " at " + at.toShortString());
        yes(ZoneService.can(buyer, world, courtyard, ZoneAction.CONTAINERS), "courtyard of П is outside the apartment");
        yes(ZoneService.can(buyer, world, leftArm, ZoneAction.PVP), "property does not change PvP rules");

        command(server, buyer, "property");
        yes(slot(buyer, 22).isOf(Items.BARRIER) && slot(buyer, 9).isOf(Items.GRAY_STAINED_GLASS_PANE), "free objects are not listed in /property");
        buyer.closeHandledScreen();

        BlockPos door = new BlockPos(207, 100, 200);
        world.setBlockState(door, Blocks.OAK_DOOR.getDefaultState());
        world.setBlockState(door.up(), Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
        var doorHit = new BlockHitResult(Vec3d.ofCenter(door), Direction.NORTH, door, false);
        DataStore.player(buyer.getUuid()).bankBalance = 5000;
        yes(UseBlockCallback.EVENT.invoker().interact(buyer, world, Hand.MAIN_HAND, doorHit) == ActionResult.FAIL, "free door stays shut");
        yes(slot(buyer, 29).isOf(Items.EMERALD), "door offers the free apartment");
        buyer.currentScreenHandler.onSlotClick(29, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, buyer);
        yes(buyer.getUuidAsString().equals(DataStore.property("smoke_u").owner) && DataStore.player(buyer.getUuid()).bankBalance == 4000, "bought through the door offer");
        buyer.closeHandledScreen();

        yes(ZoneService.can(buyer, world, base, ZoneAction.CONTAINERS) && ZoneService.can(buyer, world, rightArm, ZoneAction.INTERACT), "owner uses every part");
        no(ZoneService.can(outsider, world, base, ZoneAction.CONTAINERS), "outsider cannot open owner's containers");
        world.setBlockState(base, Blocks.CHEST.getDefaultState());
        var chestHit = new BlockHitResult(Vec3d.ofCenter(base), Direction.UP, base, false);
        yes(UseBlockCallback.EVENT.invoker().interact(outsider, world, Hand.MAIN_HAND, chestHit) == ActionResult.FAIL, "outsider chest event denied");
        yes(UseBlockCallback.EVENT.invoker().interact(buyer, world, Hand.MAIN_HAND, chestHit) == ActionResult.PASS, "owner chest event passes");
        outsider.closeHandledScreen();
        yes(UseBlockCallback.EVENT.invoker().interact(outsider, world, Hand.MAIN_HAND, doorHit) == ActionResult.FAIL, "owned door denied");
        yes(outsider.currentScreenHandler == outsider.playerScreenHandler, "owned apartment is not offered");
        DataStore.property("smoke_u").forRent = true;
        UseBlockCallback.EVENT.invoker().interact(outsider, world, Hand.MAIN_HAND, doorHit);
        yes(slot(outsider, 29).getName().getString().startsWith("Арендовать"), "apartment for rent is offered at the door");
        outsider.closeHandledScreen();
        DataStore.property("smoke_u").forRent = false;
        DataStore.property("smoke_u").members.add(outsider.getUuidAsString());
        yes(ZoneService.can(outsider, world, base, ZoneAction.CONTAINERS), "member opens containers");
        DataStore.property("smoke_u").members.clear();

        command(server, buyer, "property");
        yes(slot(buyer, 9).getName().getString().startsWith("Smoke П"), "own object listed");
        yes(slot(buyer, 10).isOf(Items.GRAY_STAINED_GLASS_PANE), "other free objects hidden");
        buyer.currentScreenHandler.onSlotClick(9, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, buyer);
        boolean teleport = false;
        for (int i = 0; i < 54; i++) teleport |= slot(buyer, i).isOf(Items.ENDER_PEARL);
        no(teleport, "player menu has no teleport");
        yes(slot(buyer, 29).isOf(Items.GOLD_INGOT), "owner menu keeps selling");
        buyer.closeHandledScreen();

        // The admin list pages past 36 objects; editing an object keeps who owns and shares it.
        var admin = player(server, "PropertyAdmin");
        Permissions.grant(admin.getUuid(), "mosvegas.admin.properties");
        for (int i = 0; i < 40; i++) ConfigManager.get().properties.add(new MosvegasConfig.Property(String.format("smoke_page_%02d", i), "Page " + i, "APARTMENT", 1, 0, new MosvegasConfig.Point("minecraft:overworld", 400 + i * 10, 100, 400, 1)));
        var properties = ConfigManager.get().properties;
        command(server, admin, "property admin");
        yes(!slot(admin, 44).isOf(Items.GRAY_STAINED_GLASS_PANE) && slot(admin, 53).isOf(Items.ARROW) && !slot(admin, 45).isOf(Items.ARROW), "first admin page is full and turns forward");
        admin.currentScreenHandler.onSlotClick(53, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, admin);
        yes(slot(admin, 45).isOf(Items.ARROW) && slot(admin, 9).getName().getString().equals(properties.get(36).id + ": " + properties.get(36).name), "37th object is on the second page");
        admin.closeHandledScreen();

        DataStore.property("smoke_u").members.add(outsider.getUuidAsString());
        command(server, admin, "property admin");
        int index = properties.indexOf(apartment);
        for (int page = 0; page < index / 36; page++) admin.currentScreenHandler.onSlotClick(53, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, admin);
        admin.currentScreenHandler.onSlotClick(9 + index % 36, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, admin);
        yes(slot(admin, 20).getName().getString().startsWith("ID: smoke_u"), "admin detail offers ID editing");
        anvil(admin, 20, "smoke_page_00");
        yes(apartment.id.equals("smoke_u"), "taken ID is refused");
        anvil(admin, 20, "Smoke_Renamed");
        yes(apartment.id.equals("smoke_renamed") && !DataStore.properties().containsKey("smoke_u"), "ID renamed and sanitized");
        var moved = DataStore.property("smoke_renamed");
        yes(buyer.getUuidAsString().equals(moved.owner) && moved.members.contains(outsider.getUuidAsString()), "owner and members follow the new ID");
        yes(ZoneService.can(buyer, world, base, ZoneAction.CONTAINERS) && ZoneService.can(outsider, world, base, ZoneAction.CONTAINERS), "renamed apartment keeps access");
        anvil(admin, 19, "Smoke edited");
        anvil(admin, 24, "1 500");
        anvil(admin, 25, "-5");
        yes(apartment.name.equals("Smoke edited") && apartment.price == 1500 && apartment.rentPrice == 100, "name and price edited, negative rent refused");
        yes(slot(admin, 24).getName().getString().contains("1500"), "detail shows the new price");
        admin.closeHandledScreen();

        // A broken file on reload must not drop the objects already marked.
        Path configFile = ConfigManager.path();
        String saved = Files.readString(configFile);
        yes(Files.exists(configFile.resolveSibling(configFile.getFileName() + ".bak")), "config save keeps a .bak");
        try {
            Files.writeString(configFile, "{broken");
            ConfigManager.load();
            yes(ConfigManager.get().properties.contains(apartment), "unreadable reload keeps the objects in memory");
        } finally { Files.writeString(configFile, saved); }

        // Older configs stored a single min/max box; it must keep protecting as the first part.
        var legacy = new MosvegasConfig.Property("smoke_legacy", "Legacy", "PARKING", 10, 0, new MosvegasConfig.Point("minecraft:overworld", 300, 100, 300, 0));
        legacy.min = new MosvegasConfig.Point("minecraft:overworld", 302, 100, 302, 0);
        legacy.max = new MosvegasConfig.Point("minecraft:overworld", 300, 101, 300, 0);
        legacy.areas = null;
        ConfigManager.get().properties.add(legacy);
        ConfigManager.save();
        ConfigManager.load();
        var migrated = ConfigManager.get().properties.stream().filter(p -> p.id.equals("smoke_legacy")).findFirst().orElseThrow();
        yes(migrated.min == null && migrated.areas.size() == 1 && migrated.areas.get(0).contains(301, 101, 301), "legacy min/max migrated to a part");
        no(ZoneService.can(outsider, world, new BlockPos(301, 100, 301), ZoneAction.INTERACT), "migrated part still protects");
        ConfigManager.get().properties.removeIf(p -> p.id.startsWith("smoke_"));
        ConfigManager.save();
        deleteSmokeRecords();
    }
    private static void deleteSmokeRecords() { new ArrayList<>(DataStore.properties().keySet()).stream().filter(id -> id.startsWith("smoke_")).forEach(DataStore::deleteProperty); }
    /** Clicks an edit button, types into the anvil prompt and confirms it. */
    private static void anvil(ServerPlayerEntity player, int button, String text) {
        player.currentScreenHandler.onSlotClick(button, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
        if (!(player.currentScreenHandler instanceof net.minecraft.screen.AnvilScreenHandler input)) throw new AssertionError("slot " + button + " opens text input");
        input.setNewItemName(text);
        input.onSlotClick(2, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
    }
    private static void testBlockUseGuard(MinecraftServer server, ServerWorld world) {
        var guest = player(server, "GuardGuest");
        BlockPos trapdoor = new BlockPos(5, 100, 5), sign = new BlockPos(6, 100, 5), lever = new BlockPos(7, 100, 5);
        world.setBlockState(trapdoor.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
        world.setBlockState(trapdoor, net.minecraft.block.Blocks.OAK_TRAPDOOR.getDefaultState());
        world.setBlockState(sign, net.minecraft.block.Blocks.OAK_SIGN.getDefaultState());
        world.setBlockState(lever, net.minecraft.block.Blocks.LEVER.getDefaultState());
        guest.setPosition(5, 100, 4);
        no(useBlock(guest, world, trapdoor).isAccepted(), "player cannot use trapdoor");
        no(world.getBlockState(trapdoor).get(net.minecraft.block.TrapdoorBlock.OPEN), "trapdoor stays closed for player");
        yes(ru.mosvegas.core.util.BlockUseGuard.isBlocked(guest, net.minecraft.util.Hand.MAIN_HAND, sign), "player cannot use sign");
        no(ru.mosvegas.core.util.BlockUseGuard.isBlocked(guest, net.minecraft.util.Hand.MAIN_HAND, lever), "lever stays usable");
        Permissions.grant(guest.getUuid(), "mosvegas.admin.interact");
        yes(useBlock(guest, world, trapdoor).isAccepted(), "admin uses trapdoor");
        yes(world.getBlockState(trapdoor).get(net.minecraft.block.TrapdoorBlock.OPEN), "trapdoor opened by admin");
        no(ru.mosvegas.core.util.BlockUseGuard.isBlocked(guest, net.minecraft.util.Hand.MAIN_HAND, sign), "admin uses sign");
        Permissions.revoke(guest.getUuid(), "mosvegas.admin.interact");
        for (BlockPos at : List.of(trapdoor.down(), trapdoor, sign, lever)) world.setBlockState(at, net.minecraft.block.Blocks.AIR.getDefaultState());
    }
    private static net.minecraft.util.ActionResult useBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        return player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, net.minecraft.util.Hand.MAIN_HAND, hit);
    }
    private static ItemStack slot(ServerPlayerEntity player, int index) { return player.currentScreenHandler.getSlot(index).getStack(); }
    private static ServerPlayerEntity player(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayerEntity(server, server.getOverworld(), profile, SyncedClientOptions.createDefault());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void setPacketListener(net.minecraft.network.listener.PacketListener listener) {}
        };
        player.networkHandler = new ServerPlayNetworkHandler(server, connection, player, ConnectedClientData.createDefault(profile));
        return player;
    }
    private static void testTaczEvents(ServerPlayerEntity player, ServerPlayerEntity victim) throws Exception {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tacz")) return;
        Class<?> sideClass = Class.forName("cn.sh1rocu.tacz.api.LogicalSide");
        Object side = sideClass.getField("SERVER").get(null);
        for (String name : List.of("GunShootEvent", "GunFireEvent")) {
            Class<?> type = Class.forName("com.tacz.guns.api.event.common." + name);
            var ctor = type.getConstructor(net.minecraft.entity.LivingEntity.class, ItemStack.class, sideClass);
            var bus = (net.fabricmc.fabric.api.event.Event<?>)type.getField("CALLBACK").get(null);
            var post = Class.forName(type.getName() + "$Callback").getMethod("post", type);
            Object blocked = ctor.newInstance(player, new ItemStack(Items.DIAMOND), side);
            post.invoke(bus.invoker(), blocked);
            yes((boolean)type.getMethod("isCanceled").invoke(blocked), "TaCZ " + name + " canceled inside zone");
            player.setPosition(30, 100, 30);
            Object allowed = ctor.newInstance(player, new ItemStack(Items.DIAMOND), side);
            post.invoke(bus.invoker(), allowed);
            no((boolean)type.getMethod("isCanceled").invoke(allowed), "TaCZ " + name + " allowed outside zone");
            player.setPosition(1, 100, 1);
        }
        Class<?> base = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent");
        Class<?> pre = Class.forName(base.getName() + "$Pre");
        var bus = (net.fabricmc.fabric.api.event.Event<?>)base.getField("PRE").get(null);
        var post = Class.forName(base.getName() + "$PreCallBack").getMethod("post", pre);
        player.setPosition(30, 100, 30);
        Object blocked = pre.getConstructors()[0].newInstance(null, victim, player, new Identifier("tacz:test"), new Identifier("tacz:test"), 10f, null, false, 1f, side);
        post.invoke(bus.invoker(), blocked);
        yes((boolean)pre.getMethod("isCanceled").invoke(blocked), "TaCZ actual Pre callback blocks outside hit into zone");
        victim.setPosition(31, 100, 30);
        Object allowed = pre.getConstructors()[0].newInstance(null, victim, player, new Identifier("tacz:test"), new Identifier("tacz:test"), 10f, null, false, 1f, side);
        post.invoke(bus.invoker(), allowed);
        no((boolean)pre.getMethod("isCanceled").invoke(allowed), "TaCZ actual Pre callback allows outside hit");
        player.setPosition(1, 100, 1); victim.setPosition(2, 100, 1);
    }
    private static void command(MinecraftServer server, ServerPlayerEntity player, String command) throws Exception {
        int result = server.getCommandManager().getDispatcher().execute(command, player.getCommandSource());
        yes(result == 1, "command: " + command);
    }
    private static void yes(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void no(boolean value, String message) { yes(!value, message); }
}
