package studio.merami.sliminterfacefabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.mosvegas.core.zone.ZoneAction;
import ru.mosvegas.core.zone.ZoneDefinition;
import ru.mosvegas.core.zone.ZoneStore;
import studio.merami.slimcore.baseclasses.BlockPosition;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.systems.VehicleSpawner;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperWorld;

import java.util.function.Consumer;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class ZoneGameTests implements FabricGameTest {
    private static void zones(Consumer<ZoneStore.State> edit) {
        try {
            ZoneStore.update(edit);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void zone(GameTestHelper h, String id, BlockPos from, BlockPos to, ZoneDefinition.Rule rule, ZoneAction... actions) {
        BlockPos a = h.absolutePos(from);
        BlockPos b = h.absolutePos(to);
        zones(state -> {
            state.zones.removeIf(z -> z.id.equals(id));
            ZoneDefinition zone = new ZoneDefinition();
            zone.id = id;
            zone.dimension = h.getLevel().dimension().location().toString();
            zone.minX = Math.min(a.getX(), b.getX());
            zone.minY = Math.min(a.getY(), b.getY());
            zone.minZ = Math.min(a.getZ(), b.getZ());
            zone.maxX = Math.max(a.getX(), b.getX());
            zone.maxY = Math.max(a.getY(), b.getY());
            zone.maxZ = Math.max(a.getZ(), b.getZ());
            zone.priority = 10;
            for (ZoneAction action : actions) zone.flags.put(action, rule);
            state.zones.add(zone);
        });
    }

    private static void remove(String id) {
        zones(state -> state.zones.removeIf(z -> z.id.equals(id)));
    }

    private static ServerPlayer player(GameTestHelper h, Point3D at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        place(player, at);
        return player;
    }

    private static void leave(GameTestHelper h, ServerPlayer player) {
        player.stopRiding();
        h.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void seat(EntityVehicleF_Physics car, ServerPlayer player) {
        own(car, player);
        openDoor(car, 0);
        place(player, car.position());
        car.board(w(player), 0);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "zone_enter")
    public void boardingFollowsTheZone(GameTestHelper h) {
        floor(h, 1, 18, 0, 9);
        EntityVehicleF_Physics inside = car(h, 4, 4, 0);
        EntityVehicleF_Physics outside = car(h, 13, 4, 0);
        spawn(h, inside);
        spawn(h, outside);
        zone(h, "test_enter", new BlockPos(0, 0, 0), new BlockPos(8, 4, 8), ZoneDefinition.Rule.DENY, ZoneAction.VEHICLE_ENTER);
        ServerPlayer player = player(h, inside.position());
        h.assertFalse(player.hasPermissions(3), "Test setup: the mock player is no operator, so the zone applies to him");
        seat(inside, player);
        h.assertTrue(player.getVehicle() == null, "vehicle_enter deny: nobody boards a car standing in the zone");
        seat(outside, player);
        h.assertTrue(player.getVehicle() == entity(outside), "A car outside the zone still takes the player");
        leave(h, player);
        remove("test_enter");
        inside.remove();
        outside.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "zone_place")
    public void placingFollowsTheZone(GameTestHelper h) {
        floor(h, 1, 20, 0, 12);
        zone(h, "test_place", new BlockPos(0, 0, 0), new BlockPos(8, 4, 11), ZoneDefinition.Rule.DENY, ZoneAction.VEHICLE_PLACE);
        ServerPlayer player = player(h, abs(h, 4.5, 1, 1));
        AABB area = new AABB(h.absoluteVec(Vec3.ZERO), h.absoluteVec(new Vec3(20, 5, 12)));
        int before = h.getLevel().getEntitiesOfClass(BuilderVehicle.class, area).size();
        EntityVehicleF_Physics denied = VehicleSpawner.spawn(w(player), vehicle("volga"), abs(h, 4.5, 1, 5));
        h.assertTrue(denied == null, "vehicle_place deny: no car appears in the zone");
        h.assertTrue(h.getLevel().getEntitiesOfClass(BuilderVehicle.class, area).size() == before, "The refused car leaves no entity behind");
        place(player, abs(h, 15.5, 1, 1));
        EntityVehicleF_Physics allowed = VehicleSpawner.spawn(w(player), vehicle("volga"), abs(h, 15.5, 1, 5));
        h.assertTrue(allowed != null, "Outside the zone the car is placed as before");
        leave(h, player);
        remove("test_place");
        if (allowed != null) allowed.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "zone_drive", timeoutTicks = 100)
    public void drivingStopsAtTheZoneAndBacksOut(GameTestHelper h) {
        floor(h, 1, 10, 0, 36);
        zone(h, "test_drive", new BlockPos(0, 0, 18), new BlockPos(9, 4, 35), ZoneDefinition.Rule.DENY, ZoneAction.VEHICLE_USE);
        double wall = h.absolutePos(new BlockPos(0, 0, 18)).getZ();
        EntityVehicleF_Physics car = createAt(h, new Vec3(4.5, 1.03, 4));
        spawn(h, car);
        ServerPlayer player = player(h, car.position());
        own(car, player);
        ride(player, car);
        car.action(w(player), VehicleAction.ENGINE);
        car.shift(1, w(player));
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE), "Outside the zone the engine starts");
        double start = car.z();
        double nose = car.bounds().maxZ;
        for (int tick = 0; tick < 200; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            step(car);
            nose = Math.max(nose, car.bounds().maxZ);
        }
        h.assertTrue(car.z() > start + 4, "The car drives up to the zone: moved " + (car.z() - start));
        h.assertTrue(nose <= wall + 1e-6, "vehicle_use deny: the car never enters the zone, nose " + nose + " border " + wall);
        h.assertTrue(Math.abs(car.speed()) < .01, "The car stands at the border: speed " + car.speed());
        double stopped = car.z();
        place(player, car.position());
        for (int step = 0; step < 2; step++) {
            for (int tick = 0; tick < 10; tick++) {
                car.acceptInput(w(player), 0, 1, 0, false);
                step(car);
            }
            car.action(w(player), VehicleAction.GEAR_DOWN);
        }
        h.assertTrue(car.gear() == -1, "Reverse engages at the border: gear " + car.gear());
        for (int tick = 0; tick < 60; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            step(car);
        }
        h.assertTrue(car.z() < stopped - 1, "The driver backs away from the zone instead of being stuck: moved " + (stopped - car.z()));
        leave(h, player);
        remove("test_drive");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "zone_revoke", timeoutTicks = 100)
    public void closingTheZoneStopsACarInside(GameTestHelper h) {
        floor(h, 1, 10, 0, 40);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4.5, 1.03, 4));
        spawn(h, car);
        ServerPlayer player = player(h, car.position());
        own(car, player);
        ride(player, car);
        car.action(w(player), VehicleAction.ENGINE);
        car.shift(1, w(player));
        double start = car.z();
        for (int tick = 0; tick < 20; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            step(car);
        }
        h.assertTrue(car.z() > start + .5, "Test setup: the car is moving before the zone closes");
        zone(h, "test_revoke", new BlockPos(0, 0, 0), new BlockPos(9, 4, 39), ZoneDefinition.Rule.DENY, ZoneAction.VEHICLE_USE);
        for (int tick = 0; tick < 60; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            step(car);
        }
        double held = car.z();
        for (int tick = 0; tick < 40; tick++) {
            car.acceptInput(w(player), 1, 0, 0, false);
            step(car);
        }
        h.assertTrue(Math.abs(car.z() - held) < .05 && Math.abs(car.speed()) < .01, "The driver loses control once the zone closes: moved " + (car.z() - held));
        place(player, car.position());
        car.action(w(player), VehicleAction.ENGINE);
        h.assertFalse(car.flag(EntityVehicleF_Physics.ENGINE), "The engine can still be switched off");
        car.action(w(player), VehicleAction.ENGINE);
        h.assertFalse(car.flag(EntityVehicleF_Physics.ENGINE), "but not started again inside the zone");
        leave(h, player);
        remove("test_revoke");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "zone_storage")
    public void trunkAndDamageFollowTheZone(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        spawn(h, car);
        PartOpening glovebox = car.opening("glovebox");
        open(car, glovebox);
        InteractionZone contents = car.interactionZones().stream().filter(z -> z.part() == glovebox && z.storage()).findFirst().orElseThrow();
        Point3D from = car.localToWorld(new Point3D(-.44, 1.25, .2));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        own(car, player);
        place(player, from.add(0, -player.getEyeHeight(), 0));
        lookAt(player, contents.box().center);
        zone(h, "test_storage", new BlockPos(0, 0, 0), new BlockPos(8, 6, 8), ZoneDefinition.Rule.DENY, ZoneAction.CONTAINERS, ZoneAction.VEHICLE_DAMAGE);
        car.interactPart(w(player), false);
        h.assertTrue(player.containerMenu == player.inventoryMenu, "containers deny: the glovebox does not open in the zone");
        float health = car.health();
        h.assertFalse(entity(car).hurt(h.getLevel().damageSources().explosion(null, null), 10), "vehicle_damage deny: an explosion does not hurt the car");
        h.assertTrue(car.health() == health, "Health is unchanged: " + car.health());
        remove("test_storage");
        car.interactPart(w(player), false);
        h.assertTrue(player.containerMenu != player.inventoryMenu, "Without the zone the glovebox opens as before");
        player.closeContainer();
        h.assertTrue(entity(car).hurt(h.getLevel().damageSources().explosion(null, null), 10) && car.health() < health, "Without the zone the explosion damages the car");
        leave(h, player);
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "zone_terrain")
    public void rammingFollowsTerrainProtection(GameTestHelper h) {
        zones(state -> state.protectedWorlds.add(h.getLevel().dimension().location().toString()));
        h.setBlock(4, 1, 4, Blocks.GLASS_PANE);
        BlockPos pane = h.absolutePos(new BlockPos(4, 1, 4));
        BlockPosition at = new BlockPosition(pane.getX(), pane.getY(), pane.getZ());
        WrapperWorld world = WrapperWorld.of(h.getLevel());
        h.assertTrue(world.breakableHardness(at) < 0, "A protected world keeps its glass panes from cars");
        zone(h, "test_terrain", new BlockPos(0, 0, 0), new BlockPos(8, 4, 8), ZoneDefinition.Rule.ALLOW, ZoneAction.TERRAIN);
        h.assertTrue(world.breakableHardness(at) >= 0, "A zone with terrain allow lets cars knock the pane down");
        remove("test_terrain");
        h.succeed();
    }
}
