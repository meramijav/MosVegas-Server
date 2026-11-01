package studio.merami.sliminterfacefabric.gametest;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.items.ItemKey;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.systems.SeatedInteractions;
import studio.merami.slimcore.systems.VehicleSpawner;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;
import studio.merami.sliminterfacefabric.wrapper.WrapperItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class InteractionGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void seatedPlayersOnlyUseTheirOwnCar(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        EntityVehicleF_Physics other = create(h);
        other.setPosition(car.position().add(2, 0, 0));
        spawn(h, car);
        spawn(h, other);
        Player driver = h.makeMockPlayer();
        Player passenger = h.makeMockPlayer();
        ride(driver, car);
        ride(passenger, car);
        for (Player p : new Player[]{driver, passenger}) {
            InteractionHand hand = InteractionHand.MAIN_HAND;
            BlockPos pos = entity(car).blockPosition();
            BlockHitResult blockHit = new BlockHitResult(v(car.position()), Direction.UP, pos, false);
            h.assertTrue(UseBlockCallback.EVENT.invoker().interact(p, h.getLevel(), hand, blockHit) == InteractionResult.FAIL, "Seated block use must be denied on server for every seat");
            h.assertTrue(AttackBlockCallback.EVENT.invoker().interact(p, h.getLevel(), hand, pos, Direction.UP) == InteractionResult.FAIL, "Seated mining must be denied");
            h.assertTrue(UseItemCallback.EVENT.invoker().interact(p, h.getLevel(), InteractionHand.OFF_HAND).getResult() == InteractionResult.FAIL, "Off-hand items must also be blocked");
            h.assertFalse(SeatedInteractions.canUseEntity(w(p), other), "Cannot interact with another car from a seat");
            h.assertTrue(SeatedInteractions.canUseEntity(w(p), car), "Own car controls must remain usable");
            own(other, p);
            other.action(w(p), VehicleAction.LOCK);
            h.assertFalse(other.flag(EntityVehicleF_Physics.LOCKED), "Custom packet must not bypass seated interaction policy");
        }
        passenger.stopRiding();
        h.assertFalse(SeatedInteractions.seated(w(passenger)), "Restrictions must end on dismount");
        car.remove();
        other.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gloveboxLidClosesWithoutOpeningInventory(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        PartOpening glovebox = car.opening("glovebox");
        open(car, glovebox);
        List<InteractionZone> zones = car.interactionZones();
        InteractionZone lid = zones.stream().filter(z -> z.part() == glovebox && !z.storage()).findFirst().orElseThrow();
        InteractionZone contents = zones.stream().filter(z -> z.part() == glovebox && z.storage()).findFirst().orElseThrow();
        Point3D from = car.localToWorld(new Point3D(-.44, 1.25, .2));
        var target = car.targetAlong(from, lid.box().center);
        h.assertTrue(target.isPresent() && target.get().zone().part() == glovebox && !target.get().zone().storage(), "Visible open lid must not be intercepted by storage");
        h.assertTrue(lid.box().center.distanceTo(contents.box().center) > .1, "Lid and storage need distinct targets");
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, from.add(0, -player.getEyeHeight(), 0));
        lookAt(player, lid.box().center);
        car.interactPart(w(player), false);
        h.assertFalse(glovebox.isOpen(), "Normal click on open lid must close it without sneaking");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void driverExitLeavesEngineIdlingThenSwitchesOff(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        car.setDefinition("police");
        spawn(h, car);
        Player driver = h.makeMockPlayer();
        Player passenger = h.makeMockPlayer();
        ride(driver, car);
        ride(passenger, car);
        int[] equipment = {EntityVehicleF_Physics.ENGINE, EntityVehicleF_Physics.LIGHTS, EntityVehicleF_Physics.SIREN, EntityVehicleF_Physics.RADIO};
        for (int flag : equipment) car.setFlag(flag, true);
        passenger.stopRiding();
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE) && car.flag(EntityVehicleF_Physics.LIGHTS), "Passenger exit must not stop the driver's car");
        driver.stopRiding();
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE) && car.flag(EntityVehicleF_Physics.LIGHTS) && car.flag(EntityVehicleF_Physics.RADIO),
                "The engine keeps idling after the driver leaves, with the lights and the radio");
        h.assertFalse(car.flag(EntityVehicleF_Physics.SIREN), "The siren needs a driver");
        h.assertTrue(car.gear() == 0, "Driver exit must select neutral");
        int keep = (int) Math.round(car.definition().engine.keepRunning * 20);
        h.assertTrue(keep == 2400, "Test setup: the police car keeps its engine running for two minutes: " + keep);
        for (int tick = 0; tick < keep - 20; tick++) step(car);
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE) && car.rpm() > 400 && Math.abs(car.speed()) < .01, "It idles on the spot for engine.keep_running: " + car.rpm() + " rpm");
        ride(driver, car);
        for (int tick = 0; tick < 40; tick++) step(car);
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE), "A driver who comes back in time finds the engine running");
        driver.stopRiding();
        for (int tick = 0; tick < keep - 20; tick++) step(car);
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE), "Each exit starts the time again");
        for (int tick = 0; tick < 30; tick++) step(car);
        for (int flag : equipment) h.assertFalse(car.flag(flag), "When the time runs out the car switches off: flag " + flag);
        for (int flag : equipment) car.setFlag(flag, true);
        ride(driver, car);
        double kept = car.definition().engine.keepRunning;
        car.definition().engine.keepRunning = 0;
        driver.stopRiding();
        car.definition().engine.keepRunning = kept;
        for (int flag : equipment) h.assertFalse(car.flag(flag), "With keep_running 0 the driver's exit switches everything off at once: flag " + flag);
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void trunkLidAndStorageAreSeparateTargets(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        PartOpening trunk = car.opening("trunk");
        car.setOpen(trunk, true);
        for (int tick = 0; tick < 9; tick++) tick(car);
        List<InteractionZone> zones = car.interactionZones();
        InteractionZone lid = zones.stream().filter(z -> z.part() == trunk && !z.storage()).findFirst().orElseThrow();
        InteractionZone storage = zones.stream().filter(z -> z.part() == trunk && z.storage()).findFirst().orElseThrow();
        h.assertTrue(lid.box().center.distanceTo(storage.box().center) > .4, "Open lid must have its own target away from storage");
        Point3D from = car.localToWorld(new Point3D(0, 2.4, -2.85));
        Point3D to = car.localToWorld(new Point3D(0, 1.0, -2.85));
        h.assertTrue(car.targetAlong(from, to).map(hit -> hit.zone().storage()).orElse(false), "A ray into the open compartment must select storage");
        Point3D lidFrom = lid.box().center.add(lid.box().axes[1].scale(2));
        h.assertTrue(car.targetAlong(lidFrom, lid.box().center).map(hit -> hit.zone().part() == trunk && !hit.zone().storage()).orElse(false), "Clicking lid must not select inventory");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void boardingControlsAndExit(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        car.board(w(player), 0);
        h.assertFalse(player.isPassenger(), "Closed door must block boarding");
        openDoor(car, 0);
        car.board(w(player), 0);
        h.assertTrue(car.driver(w(player)), "Open driver's door permits boarding");
        car.action(w(player), VehicleAction.RADIO);
        h.assertFalse(car.flag(EntityVehicleF_Physics.RADIO), "Radio must remain off while engine is off");
        car.action(w(player), VehicleAction.ENGINE);
        car.action(w(player), VehicleAction.RADIO);
        h.assertTrue(car.flag(EntityVehicleF_Physics.RADIO), "Running engine permits radio");
        car.action(w(player), VehicleAction.LIGHTS);
        h.assertTrue(car.flag(EntityVehicleF_Physics.LIGHTS), "Driver must be able to toggle headlights");
        car.action(w(player), VehicleAction.WINDOW);
        entity(car).lerpTo(car.x() + 2, car.y(), car.z() + 1, 45, 0, 3);
        h.assertTrue(car.lerpTargetX() == car.x() + 2 && car.lerpTargetZ() == car.z() + 1, "Rotation-only packets must retain pending interpolation target");
        PartOpening door = car.opening("door_fl");
        h.runAtTickTime(15, () -> {
            h.assertTrue(car.seat(0).windowProgress > .95, "Driver window must lower fully");
            car.action(w(player), VehicleAction.ENGINE);
            h.assertFalse(car.flag(EntityVehicleF_Physics.ENGINE) || car.flag(EntityVehicleF_Physics.RADIO), "Stopping engine shuts radio off too");
            Vec3 exit = entity(car).getDismountLocationForPassenger(player);
            AABB standing = player.getDimensions(Pose.STANDING).makeBoundingBox(exit);
            h.assertFalse(car.hull().intersects(box(standing)), "Exit must be outside car");
            h.assertTrue(h.getLevel().noCollision(player, standing), "Exit must not intersect terrain");
            car.setOpen(door, false);
            door.progress = 0;
            car.action(w(player), VehicleAction.EXIT);
            h.assertTrue(player.isPassenger(), "Exit must wait for door animation");
            h.assertTrue(door.isOpen(), "Exit must open the correct door first");
        });
        h.runAtTickTime(20, () -> h.assertTrue(player.isPassenger(), "Rider must remain seated while door opens"));
        h.runAtTickTime(45, () -> {
            h.assertFalse(player.isPassenger(), "Open door must release the rider after the animation");
            h.assertFalse(car.hull().intersects(box(player.getBoundingBox())), "Rider must exit outside the hull");
            car.remove();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void seatedRiderKeepsTheSeatOnShiftAndHandlesNoItems(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        openDoor(car, 0);
        car.board(w(player), 0);
        h.assertTrue(car.driver(w(player)), "Driver boards through the open door");
        player.setShiftKeyDown(true);
        player.rideTick();
        h.assertTrue(player.isPassenger(), "Shift does not take a rider out of the car");
        player.setShiftKeyDown(false);
        ItemEntity loot = new ItemEntity(h.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.APPLE));
        loot.setNoPickUpDelay();
        h.getLevel().addFreshEntity(loot);
        player.aiStep();
        h.assertTrue(loot.isAlive() && player.getInventory().countItem(Items.APPLE) == 0, "A seated rider picks nothing up");
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
        player.inventoryMenu.clicked(36, 0, ClickType.THROW, player);
        player.inventoryMenu.setCarried(new ItemStack(Items.STICK));
        player.inventoryMenu.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, ClickType.PICKUP, player);
        h.assertTrue(player.getInventory().getItem(0).getCount() == 3 && player.inventoryMenu.getCarried().is(Items.STICK),
                "A seated rider throws nothing out of an inventory window");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.stopRiding();
        player.setPos(loot.position());
        player.aiStep();
        h.assertTrue(player.getInventory().countItem(Items.APPLE) == 1, "Out of the car the same player picks the item up");
        player.inventoryMenu.clicked(36, 0, ClickType.THROW, player);
        h.assertTrue(player.getInventory().getItem(0).getCount() == 2, "Out of the car the same player throws items again");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void driverOpensGloveboxFromSeat(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        Player rear = h.makeMockPlayer();
        place(rear, car.position());
        openDoor(car, 0);
        openDoor(car, 2);
        car.board(w(player), 0);
        entity(car).positionRider(player);
        car.board(w(rear), 2);
        entity(car).positionRider(rear);
        h.assertTrue(car.driver(w(player)), "Driver must be seated");
        double eye = car.worldToLocal(p(player.getEyePosition())).y;
        h.assertTrue(eye > 1.2 && eye < 1.6, "Seated eye must be inside the cabin: " + eye);
        PartOpening glovebox = car.opening("glovebox");
        PartOpening driverDoor = car.opening("door_fl");
        for (InteractionZone zone : car.interactionZones()) {
            if (zone.part() == glovebox || zone.part() == driverDoor) continue;
            Point3D from = p(player.getEyePosition());
            h.assertTrue(car.seatedTarget(0, from, zone.box().center.subtract(from).normalize(), zone.box().center).map(hit -> hit.zone().part() == glovebox).orElse(true),
                    "A seated driver must not target " + zone.label());
        }
        InteractionZone lid = car.interactionZones().stream().filter(z -> z.part() == glovebox).findFirst().orElseThrow();
        lookAt(player, lid.box().center);
        h.assertTrue(seatedTarget(car, player).map(hit -> hit.zone().part() == glovebox).orElse(false), "Aiming at the glovebox selects it");
        float towards = player.getYRot();
        float down = player.getXRot();
        for (int pitch = -30; pitch <= down - 15; pitch += 10) {
            player.setYRot(towards);
            player.setXRot(pitch);
            h.assertFalse(seatedTarget(car, player).map(hit -> hit.zone().part() == glovebox).orElse(false), "Turned towards the glovebox but looking past it (pitch " + pitch + ") selects nothing");
        }
        for (int off : new int[]{-35, 35}) {
            player.setYRot(towards + off);
            player.setXRot(down);
            h.assertFalse(seatedTarget(car, player).map(hit -> hit.zone().part() == glovebox).orElse(false), "Looking " + off + " degrees beside the glovebox selects nothing");
        }
        for (int pitch = -20; pitch <= 25; pitch += 5) {
            player.setYRot(car.yaw());
            player.setXRot(pitch);
            Point3D from = p(player.getEyePosition());
            Point3D look = p(player.getLookAngle());
            h.assertTrue(car.seatedTarget(0, from, look, from.add(look.scale(5))).isEmpty(), "Looking ahead must not select anything at pitch " + pitch);
        }
        lookAt(player, lid.box().center);
        car.action(w(player), VehicleAction.INTERACT);
        h.assertTrue(glovebox.isOpen(), "Driver must open the glovebox from the seat");
        h.assertFalse(car.opening("door_fr").isOpen(), "Opening the glovebox must not open the passenger door");
        glovebox.progress = 1;
        car.refreshHull();
        InteractionZone contents = car.interactionZones().stream().filter(z -> z.part() == glovebox && z.storage()).findFirst().orElseThrow();
        lookAt(player, contents.box().center);
        h.assertTrue(seatedTarget(car, player).map(hit -> hit.zone().storage()).orElse(false), "Aiming into the open glovebox targets its contents");
        Vec3 back = v(lid.box().center).subtract(rear.getEyePosition());
        rear.setYRot((float) Math.toDegrees(Math.atan2(-back.x, back.z)));
        rear.setXRot((float) -Math.toDegrees(Math.atan2(back.y, Math.hypot(back.x, back.z))));
        Point3D rearEye = p(rear.getEyePosition());
        Point3D rearLook = p(rear.getLookAngle());
        h.assertTrue(car.seatedTarget(car.seatOf(w(rear)), rearEye, rearLook, rearEye.add(rearLook.scale(5))).isEmpty(),
                "A rear passenger must not target the glovebox");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void seatedRiderClosesOnlyTheirOwnDoor(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        Player rear = h.makeMockPlayer();
        place(rear, car.position());
        for (int seat = 0; seat < 4; seat++) openDoor(car, seat);
        car.board(w(player), 0);
        entity(car).positionRider(player);
        car.board(w(rear), 3);
        entity(car).positionRider(rear);
        h.assertTrue(car.driver(w(player)) && car.seatOf(w(rear)) == 3, "Driver and rear passenger must be seated");
        Map<PartOpening, Hull.Box> doors = new HashMap<>();
        for (InteractionZone zone : car.interactionZones()) {
            if (zone.part() instanceof PartOpening opening && opening.isDoor() && !zone.storage()) doors.put(opening, zone.box());
        }
        PartOpening own = car.opening("door_fl");
        lookAt(player, doors.get(own).center);
        var hit = seatedTarget(car, player);
        h.assertTrue(hit.map(x -> x.zone().part() == own && x.zone().label().equals("hud.slimvehicle.close_door")).orElse(false),
                "Looking at the open driver's door from the seat targets it: " + hit.map(x -> x.zone().label()).orElse("nothing"));
        for (String other : new String[]{"door_fr", "door_rl", "door_rr"}) {
            PartOpening door = car.opening(other);
            lookAt(player, doors.get(door).center);
            h.assertFalse(seatedTarget(car, player).map(x -> x.zone().part() == door).orElse(false), "The driver must not target " + other);
        }
        lookAt(player, doors.get(own).center);
        car.action(w(player), VehicleAction.INTERACT);
        h.assertFalse(own.isOpen(), "The driver closes their own door from the seat");
        h.assertTrue(car.driver(w(player)) && car.opening("door_fr").isOpen() && car.opening("door_rr").isOpen(), "Closing it keeps the driver seated and the other doors as they were");
        car.setFlag(EntityVehicleF_Physics.LOCKED, true);
        entity(car).tickCount += 5;
        PartOpening rearDoor = car.opening("door_rr");
        lookAt(rear, doors.get(rearDoor).center);
        car.action(w(rear), VehicleAction.INTERACT);
        h.assertFalse(rearDoor.isOpen(), "A passenger closes their own door, even in a car they have no key to");
        h.assertTrue(car.seatOf(w(rear)) == 3, "The passenger stays seated");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "glovebox_drive", timeoutTicks = 60)
    public void gloveboxWorksWhileDriving(GameTestHelper h) {
        floor(h, 1, 10, 0, 30);
        EntityVehicleF_Physics car = car(h, 5, 6, 0);
        spawn(h, car);
        Player player = drive(car, h);
        car.push(new Point3D(0, 0, .3), 0);
        for (int tick = 0; tick < 3; tick++) step(car);
        h.assertTrue(Math.abs(car.speed()) > .1, "Test setup: the car is moving: " + car.speed());
        entity(car).positionRider(player);
        PartOpening glovebox = car.opening("glovebox");
        InteractionZone lid = car.interactionZones().stream().filter(z -> z.part() == glovebox && !z.storage()).findFirst().orElseThrow();
        lookAt(player, lid.box().center);
        car.action(w(player), VehicleAction.INTERACT);
        h.assertTrue(glovebox.isOpen(), "The glovebox opens while driving");
        for (int tick = 0; tick < 5; tick++) step(car);
        h.assertTrue(glovebox.isOpen(), "Driving on does not shut the glovebox");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void keyLocksItsCarFromAfar(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player owner = h.makeMockPlayer();
        Player stranger = h.makeMockPlayer();
        car.setOwner(owner.getUUID());
        ItemStack key = new ItemStack(ItemRegistry.item(SlimItems.KEY));
        key.getOrCreateTag().putUUID(ItemKey.VEHICLE, car.uuid());
        WrapperItemStack wrapped = new WrapperItemStack(key);
        h.assertTrue(car.uuid().equals(ItemKey.vehicleOf(wrapped)), "Test setup: the key belongs to the car");
        Point3D side = car.position().add(car.directionToWorld(new Point3D(1, 0, 0)).scale(20));
        place(stranger, side);
        stranger.getInventory().add(key.copy());
        place(owner, side);
        long now = h.getLevel().getGameTime();
        h.assertFalse(car.lockFlash(now), "Test setup: the indicators are dark before the key is used");
        SlimItems.KEY.useInAir(w(stranger), wrapped);
        h.assertTrue(car.flag(EntityVehicleF_Physics.LOCKED), "Pressing the key 20 blocks away locks its car");
        h.assertTrue(car.lockFlash(now) && car.lockFlash(now + 9) && !car.lockFlash(now + 12), "Locking flashes the indicators once");
        SlimItems.KEY.useInAir(w(stranger), wrapped);
        h.assertFalse(car.flag(EntityVehicleF_Physics.LOCKED), "Pressing it again unlocks the car");
        h.assertTrue(car.lockFlash(now) && !car.lockFlash(now + 6) && car.lockFlash(now + 9) && !car.lockFlash(now + 14), "Unlocking flashes them twice");
        Player thief = h.makeMockPlayer();
        place(thief, car.position().add(0, 0, 3));
        car.action(w(thief), VehicleAction.LOCK);
        h.assertFalse(car.flag(EntityVehicleF_Physics.LOCKED), "Without a key the lock key does nothing");
        car.action(w(stranger), VehicleAction.LOCK);
        h.assertTrue(car.flag(EntityVehicleF_Physics.LOCKED), "The lock key works from a distance for anyone holding the car's key");
        openDoor(car, 1);
        car.board(w(thief), 1);
        h.assertFalse(thief.isPassenger(), "A locked car keeps people without a key out");
        Point3D far = car.position().add(car.directionToWorld(new Point3D(1, 0, 0)).scale(ItemKey.REACH + 6));
        place(stranger, far);
        SlimItems.KEY.useInAir(w(stranger), wrapped);
        car.action(w(stranger), VehicleAction.LOCK);
        h.assertTrue(car.flag(EntityVehicleF_Physics.LOCKED), "Beyond " + ItemKey.REACH + " blocks the key does not reach the car");
        ItemStack blank = new ItemStack(ItemRegistry.item(SlimItems.KEY));
        h.assertTrue(ItemKey.vehicleOf(new WrapperItemStack(blank)) == null, "A blank key fits no car");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void onlyTheKeyStartsAndLocksAndTheHornSoundsWhileHeld(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player owner = h.makeMockPlayer();
        car.setOwner(owner.getUUID());
        place(owner, car.position());
        openDoor(car, 0);
        car.board(w(owner), 0);
        h.assertTrue(car.driver(w(owner)), "Anyone may take the wheel of an unlocked car");
        car.action(w(owner), VehicleAction.ENGINE);
        h.assertFalse(car.flag(EntityVehicleF_Physics.ENGINE), "Without its key not even the owner starts the engine");
        car.action(w(owner), VehicleAction.LOCK);
        h.assertFalse(car.flag(EntityVehicleF_Physics.LOCKED), "Without its key not even the owner locks the car");
        VehicleSpawner.giveKey(w(owner), car);
        car.action(w(owner), VehicleAction.ENGINE);
        h.assertTrue(car.flag(EntityVehicleF_Physics.ENGINE), "The key starts the engine");
        car.hornInput(w(owner), true);
        h.assertTrue(car.flag(EntityVehicleF_Physics.HORN), "The horn sounds while its key is held");
        Player passenger = h.makeMockPlayer();
        car.hornInput(w(passenger), false);
        h.assertTrue(car.flag(EntityVehicleF_Physics.HORN), "Only the driver works the horn");
        h.succeedWhen(() -> {
            h.assertFalse(car.flag(EntityVehicleF_Physics.HORN), "The horn stops once the driver's input stops coming");
            car.remove();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wipersRunOnlyWithEngine(GameTestHelper h) {
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        openDoor(car, 0);
        car.board(w(player), 0);
        car.action(w(player), VehicleAction.WIPERS);
        h.assertFalse(car.flag(EntityVehicleF_Physics.WIPERS), "Wipers must not run with the engine off");
        car.action(w(player), VehicleAction.ENGINE);
        car.action(w(player), VehicleAction.WIPERS);
        h.assertTrue(car.flag(EntityVehicleF_Physics.WIPERS), "Wipers switch on with their own key");
        car.action(w(player), VehicleAction.WIPERS);
        h.assertFalse(car.flag(EntityVehicleF_Physics.WIPERS), "Wipers switch off with their own key");
        car.action(w(player), VehicleAction.WIPERS);
        car.action(w(player), VehicleAction.ENGINE);
        h.assertFalse(car.flag(EntityVehicleF_Physics.WIPERS), "Stopping the engine switches the wipers off");
        car.action(w(player), VehicleAction.ENGINE);
        h.assertFalse(car.flag(EntityVehicleF_Physics.WIPERS), "Restarting the engine must not restart the wipers");
        car.remove();
        h.succeed();
    }
}
