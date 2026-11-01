package studio.merami.sliminterfacefabric.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.ModelSkeleton;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperInventory;

import java.util.ArrayList;
import java.util.List;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class DamageGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void shotsFromTheCabinStopAtClosedGlassAndDoors(GameTestHelper h) {
        floor(h, 1, 8, 0, 8);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        car.setDefinition("g63");
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        openDoor(car, 0);
        car.board(w(player), 0);
        PartOpening door = car.opening("door_fl");
        car.setOpen(door, false);
        door.progress = 0;
        car.invalidateHull();
        entity(car).positionRider(player);
        Point3D eye = Convert.point(player.getEyePosition());
        int left = pane(car, "glass_fl");
        int right = pane(car, "glass_fr");
        Point3D throughLeft = beyond(eye, glassBox(car, left).center);
        Point3D throughRight = beyond(eye, glassBox(car, right).center);
        Point3D lowLeft = beyond(eye, glassBox(car, left).center.subtract(0, .6, 0));
        Point3D ahead = eye.add(car.directionToWorld(new Point3D(0, 0, 1)).scale(6));
        h.assertTrue(car.cabinHit(eye, throughLeft).map(hit -> hit.box().glass == left).orElse(false), "A shot at the closed side window meets its glass");
        h.assertTrue(car.cabinHit(eye, ahead).map(hit -> hit.box().glass == pane(car, "glass_windshield")).orElse(false), "A shot ahead meets the windscreen");
        h.assertTrue(car.cabinHit(eye, lowLeft).map(hit -> hit.box().part == door && hit.box().glass < 0).orElse(false), "The door below the window stops a shot");
        car.cabinShot(eye, throughLeft, 5);
        h.assertTrue(car.brokenGlass(left), "The shot breaks the window it meets from inside");
        h.assertTrue(car.cabinHit(eye, throughLeft).isEmpty(), "Through the broken window the next shot flies on");
        car.toggleWindow(1);
        h.runAtTickTime(25, () -> {
            h.assertTrue(car.seat(1).windowProgress > .95, "The passenger window is down");
            h.assertTrue(car.cabinHit(eye, throughRight).isEmpty(), "Through a lowered window a shot flies on");
            h.assertFalse(car.brokenGlass(right), "A lowered window stays whole");
            car.remove();
            h.succeed();
        });
    }

    private static int pane(EntityVehicleF_Physics car, String bone) {
        for (int i = 0; i < car.definition().glass.size(); i++) if (car.definition().glass.get(i).bone.equals(bone)) return i;
        throw new IllegalStateException("No pane " + bone);
    }

    private static Hull.Box glassBox(EntityVehicleF_Physics car, int pane) {
        return car.hull().boxes().stream().filter(box -> box.glass == pane).findFirst().orElseThrow();
    }

    private static Point3D beyond(Point3D eye, Point3D target) {
        return eye.add(target.subtract(eye).scale(3));
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_damage")
    public void headOnCrashDamagesAndGentleBumpDoesNot(GameTestHelper h) {
        floor(h, 1, 22, 0, 24);
        for (int x = 1; x < 22; x++) for (int y = 1; y < 4; y++) h.setBlock(x, y, 16, Blocks.STONE);
        double wall = h.absoluteVec(new Vec3(0, 0, 16)).z;
        EntityVehicleF_Physics fast = car(h, 5.5, 8, 0);
        EntityVehicleF_Physics slow = car(h, 15.5, 8, 0);
        noseTo(fast, wall, 1.2);
        noseTo(slow, wall, .05);
        Player occupant = h.makeMockPlayer();
        place(occupant, fast.position());
        ride(occupant, fast);
        fast.push(new Point3D(0, 0, .7), 0);
        slow.push(new Point3D(0, 0, .1), 0);
        for (int tick = 0; tick < 30; tick++) {
            tick(fast);
            tick(slow);
        }
        h.assertTrue(fast.health() > 45 && fast.health() < 80, "50 km/h into a wall costs about a third of the condition: " + fast.health());
        h.assertTrue(fast.engineHealth() <= fast.health() + .01f, "A nose hit reaches the engine: " + fast.engineHealth());
        h.assertTrue(fast.lampBroken(EntityVehicleF_Physics.HEADLIGHT_LEFT) && fast.lampBroken(EntityVehicleF_Physics.HEADLIGHT_RIGHT), "Both headlights break in a centred hit");
        h.assertTrue(fast.opening("bonnet").isJammed() && fast.brokenGlass(4), "A hard nose hit jams the bonnet and breaks the windshield");
        h.assertFalse(fast.lampBroken(EntityVehicleF_Physics.TAILLIGHT_LEFT) || fast.opening("trunk").isJammed() || fast.opening("door_fl").isJammed(), "The rear and doors stay intact");
        h.assertTrue(occupant.getHealth() < occupant.getMaxHealth(), "The occupant is hurt in a hard crash");
        h.assertTrue(fast.canSpawnAtCurrentPosition() && fast.hull().bounds().maxZ <= wall + .001, "The car stops at the wall, never inside it");
        h.assertTrue(slow.health() == DamageModel.FULL && !slow.lampBroken(EntityVehicleF_Physics.HEADLIGHT_LEFT), "A walking-pace bump is harmless: " + slow.health());
        fast.remove();
        slow.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wreckedCarStaysDeadUntilRepaired(GameTestHelper h) {
        floor(h, 1, 8, 0, 8);
        EntityVehicleF_Physics car = car(h, 4, 4, 0);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        openDoor(car, 0);
        car.board(w(player), 0);
        car.setCondition(0);
        h.assertTrue(car.wrecked(), "Zero condition wrecks the car");
        car.action(w(player), VehicleAction.ENGINE);
        h.assertFalse(car.flag(EntityVehicleF_Physics.ENGINE), "A wrecked car must not start");
        CompoundTag tag = new CompoundTag();
        entity(car).saveWithoutId(tag);
        EntityVehicleF_Physics copy = create(h);
        entity(copy).load(tag);
        h.assertTrue(copy.wrecked() && copy.engineHealth() == 0, "Damage must survive saving");
        player.stopRiding();
        place(player, car.localToWorld(new Point3D(3, 0, 0)));
        car.puncture(1);
        car.breakGlass(4);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.item(SlimItems.REPAIR_KIT)));
        car.interactPart(w(player), false);
        h.assertTrue(car.health() == DamageModel.FULL && car.engineHealth() == DamageModel.FULL && !car.punctured(1) && !car.brokenGlass(4), "The repair kit restores the car");
        h.assertTrue(player.getMainHandItem().isEmpty(), "The kit is used up");
        car.remove();
        copy.remove();
        h.succeed();
    }


    @GameTest(template = EMPTY_STRUCTURE)
    public void sideImpactDentsAndJamsOnlyTheDoorItHits(GameTestHelper h) {
        floor(h, 1, 10, 0, 12);
        EntityVehicleF_Physics car = car(h, 5, 6, 30);
        Point3D door = car.localToWorld(new Point3D(.97, .8, .5));
        car.crash(door, null, .55, worldDirection(car, new Point3D(-1, 0, 0)));
        h.assertTrue(car.opening("door_fl").isJammed() && car.brokenGlass(0), "The door that was hit jams and loses its window");
        h.assertFalse(car.opening("door_rl").isJammed() || car.opening("bonnet").isJammed() || car.opening("trunk").isJammed() || car.brokenGlass(4), "Nothing else breaks");
        int front = car.definition().damage.zones.stream().filter(z -> z.id.equals("door_fl")).findFirst().orElseThrow().index;
        h.assertTrue(car.zoneDamage(front) > 10, "The door zone takes the damage: " + car.zoneDamage(front));
        h.assertTrue(car.engineHealth() > car.health(), "A side hit barely reaches the engine");
        h.assertTrue(car.dents().size() == 1, "The hit leaves a dent");
        DamageModel.Dent dent = car.dents().get(0);
        h.assertTrue(dent.pos().x > .8 && dent.dir().x < -.99 && dent.depth() > .1, "The dent is in the left side, pushed inwards: " + dent);
        car.crash(car.localToWorld(new Point3D(.97, .8, .3)), null, .4, worldDirection(car, new Point3D(-1, 0, 0)));
        h.assertTrue(car.dents().size() == 1 && car.dents().get(0).depth() > dent.depth(), "Close hits deepen one dent");
        car.crash(car.localToWorld(new Point3D(0, .6, -3.05)), null, .13, worldDirection(car, new Point3D(0, 0, 1)));
        h.assertTrue(car.dents().size() == 1, "A gentle bump does not dent");
        CompoundTag tag = new CompoundTag();
        entity(car).saveWithoutId(tag);
        EntityVehicleF_Physics copy = create(h);
        entity(copy).load(tag);
        h.assertTrue(copy.dents().size() == 1 && Math.abs(copy.zoneDamage(front) - car.zoneDamage(front)) < 1e-4, "Dents and zone damage are saved");
        car.repair();
        h.assertTrue(car.dents().isEmpty() && car.zoneDamage(front) == 0 && !car.opening("door_fl").isJammed() && !car.brokenGlass(0), "A repair takes out dents and zone damage");
        car.remove();
        copy.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "crash_wing")
    public void bentFrontWingPullsTheCarToItsSide(GameTestHelper h) {
        floor(h, 1, 40, 0, 60);
        EntityVehicleF_Physics straight = car(h, 10, 5, 0);
        EntityVehicleF_Physics bent = car(h, 28, 5, 0);
        bent.crash(bent.localToWorld(new Point3D(.99, .7, 2.9)), null, .9, worldDirection(bent, new Point3D(-1, 0, 0)));
        h.assertTrue(bent.steeringPull() > .01 && !bent.punctured(0), "A crushed left wing pulls left: " + bent.steeringPull());
        Player a = drive(straight, h);
        Player b = drive(bent, h);
        for (int tick = 0; tick < 90; tick++) {
            straight.acceptInput(w(a), 1, 0, 0, false);
            tick(straight);
            bent.acceptInput(w(b), 1, 0, 0, false);
            tick(bent);
        }
        h.assertTrue(Math.abs(straight.yaw()) < .01, "An intact car runs straight");
        h.assertTrue(bent.yaw() < -1.5, "Without steering the damaged car drifts to the left: yaw=" + bent.yaw());
        straight.remove();
        bent.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void crushedTailLeaksFuel(GameTestHelper h) {
        floor(h, 1, 10, 0, 12);
        EntityVehicleF_Physics car = car(h, 5, 6, 0);
        car.crash(car.localToWorld(new Point3D(0, .6, -3.05)), null, .85, worldDirection(car, new Point3D(0, 0, 1)));
        h.assertTrue(car.opening("trunk").isJammed() && car.lampBroken(EntityVehicleF_Physics.TAILLIGHT_LEFT) && car.lampBroken(EntityVehicleF_Physics.TAILLIGHT_RIGHT),
                "A hard hit from behind jams the trunk and breaks both tail lamps");
        h.assertTrue(car.fuelLeak() > 0, "...and splits the tank");
        float before = car.fuel();
        for (int tick = 0; tick < 100; tick++) tick(car);
        h.assertTrue(car.fuel() < before - .05f, "The tank empties slowly while it stands: " + before + " -> " + car.fuel());
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void burningStartsAtTheEngineAndRepairPutsItOut(GameTestHelper h) {
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 2, 4));
        h.assertTrue(car.fire() == 0, "An intact car does not burn");
        car.setCondition(40);
        h.assertTrue(car.fire() == 0, "A damaged engine only smokes");
        car.setCondition(5);
        h.assertTrue(car.fire() > 0 && car.fire() < .5f, "An engine about to die burns a little: " + car.fire());
        car.setCondition(0);
        h.assertTrue(car.wrecked() && car.fire() == 1, "A wreck burns all over");
        car.repair();
        h.assertTrue(car.fire() == 0, "A repair puts the fire out");
        h.assertFalse(vehicle("scooter").hasEngineBay(), "A scooter has no engine bay to burn");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dentedPanelsStayOnTheirHinges(GameTestHelper h) {
        floor(h, 1, 10, 0, 12);
        EntityVehicleF_Physics car = car(h, 5, 6, 0);
        spawn(h, car);
        ModelSkeleton.Bone bone = ModelSkeleton.bones(car.definition().modelLocation).stream().filter(b -> b.name().equals("fuel_flap")).findFirst().orElseThrow();
        Point3D pivot = new Point3D(-bone.pivot().x, bone.pivot().y, -bone.pivot().z);
        Hull.Box closed = control(car, "fuel");
        double attached = distance(closed, car.localToWorld(pivot));
        Point3D flap = car.worldToLocal(closed.center);
        car.crash(car.localToWorld(new Point3D(-.97, flap.y, flap.z)), null, .6, worldDirection(car, new Point3D(1, 0, 0)));
        Point3D push = DamageModel.displacement(car.dents(), pivot);
        h.assertTrue(push.length() > .08, "Test setup: the hit dents the body at the flap's hinge: " + push);
        Hull.Box dented = control(car, "fuel");
        h.assertTrue(car.worldToLocal(dented.center).x - flap.x > .05, "The closed flap sinks in with the dented body");
        PartOpening fuel = car.opening("fuel");
        open(car, fuel);
        Hull.Box opened = control(car, "fuel");
        double hinge = distance(opened, car.localToWorld(pivot.add(push)));
        h.assertTrue(Math.abs(hinge - attached) < .02, "The open flap turns about its hinge on the dented body, not about the undented spot in the air: " + hinge + " vs " + attached);
        car.remove();
        h.succeed();
    }

    private static List<String> state(EntityVehicleF_Physics car) {
        List<String> state = new ArrayList<>();
        state.add("definition=" + car.definition().id);
        state.add("owner=" + car.owner());
        state.add("health=" + car.health());
        for (int i = 0; i < car.definition().damage.zones.size(); i++) state.add("zone" + i + "=" + car.zoneDamage(i));
        state.add("dents=" + car.dents());
        state.add("flags=" + (car.flag(EntityVehicleF_Physics.LOCKED) ? "L" : "") + (car.flag(EntityVehicleF_Physics.LIGHTS) ? "H" : ""));
        for (PartOpening opening : car.openings()) state.add("opening." + opening.id() + "=" + opening.isOpen() + "/" + opening.isJammed());
        for (int i = 0; i < car.seats().size(); i++) state.add("window" + i + "=" + car.windowDown(i));
        for (int i = 0; i < car.glassPanes().size(); i++) state.add("glass" + i + "=" + car.brokenGlass(i));
        for (int i = 0; i < car.wheels().size(); i++) state.add("tyre" + i + "=" + car.punctured(i));
        for (int lamp : new int[]{1, 2, 4, 8}) state.add("lamp" + lamp + "=" + car.lampBroken(lamp));
        state.add("fuel=" + car.fuel());
        state.add("engine=" + car.engineHealth());
        state.add("tint=" + car.tint());
        state.add("color=" + car.color());
        state.add("styles=" + car.rimStyle + "," + car.bumperStyle + "," + car.spoilerStyle + "," + car.skin);
        for (PartOpening opening : car.openings()) {
            if (!opening.hasStorage()) continue;
            var container = ((WrapperInventory) car.storage(opening)).container;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) state.add("storage." + opening.id() + "." + i + "=" + stack.getCount() + " " + stack.getItem());
            }
        }
        return state;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void worldsSavedBeforeTheCoreSplitLoadUnchanged(GameTestHelper h) {
        for (String[] save : new String[][]{{SaveSnapshots.G63_SNBT, SaveSnapshots.G63_STATE}, {SaveSnapshots.VOLGA_SNBT, SaveSnapshots.VOLGA_STATE}}) {
            CompoundTag tag;
            try {
                tag = TagParser.parseTag(save[0].strip());
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException(e);
            }
            EntityVehicleF_Physics car = create(h);
            entity(car).load(tag);
            List<String> expected = save[1].strip().lines().toList();
            List<String> actual = state(car);
            for (int i = 0; i < Math.max(expected.size(), actual.size()); i++) {
                String want = i < expected.size() ? expected.get(i) : "(nothing)";
                String got = i < actual.size() ? actual.get(i) : "(nothing)";
                h.assertTrue(want.equals(got), "A vehicle saved by 0.2.3 loads the same: expected " + want + ", got " + got);
            }
            CompoundTag again = new CompoundTag();
            entity(car).saveWithoutId(again);
            h.assertTrue(again.equals(tag), "Saving it again writes the same NBT as 0.2.3: " + again);
            car.remove();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void damageZonesComeFromTheDefinition(GameTestHelper h) {
        AJSONInteractable.Damage layout = vehicle("volga").damage;
        h.assertTrue(layout.zones.size() == 11, "The Volga has eleven body zones: " + layout.zones.size());
        AJSONInteractable.Zone rear = layout.zone(new Point3D(0, .6, -3.05));
        h.assertTrue(rear != null && rear.id.equals("rear") && rear.leak && rear.jamIndex == vehicle("volga").opening("trunk").index, "The rear zone jams the trunk and leaks");
        h.succeed();
    }
}
