package studio.merami.sliminterfacefabric.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.ModelSkeleton;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONSounds;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.JSONParser;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.rendering.AnimationFile;
import studio.merami.slimcore.rendering.Animator;
import studio.merami.slimcore.rendering.MoLang;
import studio.merami.slimcore.rendering.ModelGeometry;
import studio.merami.sliminterfacefabric.wrapper.WrapperDamage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class PackGameTests implements FabricGameTest {
    private static JsonObject builtIn(String name) {
        try {
            return JSONParser.read(PackParser.findAsset(SlimCore.id("vehicles/" + name + ".json")).orElseThrow());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JSONVehicle define(String name, JsonObject json, List<String> unknown) {
        JSONParser.applyWheelTemplate(json);
        JSONVehicle vehicle = JSONParser.parse(json, JSONVehicle.class, unknown);
        vehicle.id = "test:" + name;
        vehicle.namespace = "test";
        vehicle.name = name;
        vehicle.bake();
        return vehicle;
    }

    private static JSONVehicle define(String name, JsonObject json) {
        return define(name, json, new ArrayList<>());
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyVehicleComesFromItsDefinition(GameTestHelper h) {
        JSONVehicle volga = vehicle("volga");
        JSONVehicle police = vehicle("police");
        JSONVehicle scooter = vehicle("scooter");
        h.assertTrue(PackParser.vehicles().size() >= 4 && PackParser.vehicle("no-such-car") == volga, "All definitions load, an unknown id falls back to the first");
        h.assertTrue(volga.id.equals("slimvehicle:volga") && PackParser.vehicle("slimvehicle:police") == police, "Vehicles are known by namespaced id and by name");
        h.assertTrue(police.emergency && !volga.emergency && police.wheels.size() == volga.wheels.size() && police.seats.size() == volga.seats.size()
                && police.wheels.get(2).pos.equals(volga.wheels.get(2).pos), "Police reuses the Volga body and adds a siren");
        h.assertTrue(police.tune.acceleration() != volga.tune.acceleration() && Math.abs(volga.tune.wheelbase() - 3.65 * 1.08) < 1e-9, "Police keeps its own tune; the wheelbase follows the wheels");
        h.assertTrue(scooter.wheels.size() == 2 && scooter.seats.get(0).door == null && scooter.seats.get(0).stand && scooter.boardOnClick && scooter.driver == null,
                "The scooter is a two-wheeler without doors");
        for (JSONVehicle definition : PackParser.vehicles()) {
            Set<String> bones = new HashSet<>();
            for (ModelSkeleton.Bone bone : ModelSkeleton.bones(definition.modelLocation)) bones.add(bone.name());
            for (AJSONMultipart.Opening opening : definition.openingList) h.assertTrue(bones.contains(opening.bone), definition + ": no bone " + opening.bone);
            for (AJSONMultipart.Glass pane : definition.glass) h.assertTrue(bones.contains(pane.bone), definition + ": no glass bone " + pane.bone);
            for (String bone : definition.dentBones) h.assertTrue(bones.contains(bone), definition + ": no dent bone " + bone);
            for (String bone : definition.paintBones) h.assertTrue(bones.contains(bone), definition + ": no paint bone " + bone);
            for (AJSONMultipart.Wheel wheel : definition.wheels) h.assertTrue(bones.contains("steer_" + wheel.bone) || bones.contains("roll_" + wheel.bone), definition + ": no wheel bones " + wheel.bone);
            if (definition.driver != null) h.assertTrue(bones.contains(definition.driver.steering.bone), definition + ": no steering bone");
            EntityVehicleF_Physics car = create(h);
            car.setDefinition(definition.id);
            car.setPosition(p(h.absoluteVec(new Vec3(4, 2, 4))));
            h.assertTrue(car.definition() == definition && !car.hull().boxes().isEmpty(), definition + ": builds its hull");
            car.remove();
        }
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics ride = create(h);
        ride.setDefinition("scooter");
        ride.setPosition(p(h.absoluteVec(new Vec3(4, 1, 4))));
        spawn(h, ride);
        Player player = h.makeMockPlayer();
        own(ride, player);
        place(player, ride.position().add(1, 0, 0));
        ride.interactPart(w(player), false);
        h.assertTrue(ride.driver(w(player)), "A click gets the rider on the scooter");
        ride.action(w(player), VehicleAction.EXIT);
        h.assertFalse(player.isPassenger(), "...and off it at once");
        ride.remove();
        h.succeed();
    }



    @GameTest(template = EMPTY_STRUCTURE)
    public void g63SeatsFiveAndBoardsTheMiddleThroughTheRearDoor(GameTestHelper h) {
        JSONVehicle g63 = vehicle("g63");
        h.assertTrue(g63.seats.size() == 5 && g63.seats.get(3).doorOpening == g63.opening("door_rl") && g63.driver.steering.axis.z == 1
                && g63.glass.size() == 9 && g63.tailpipes.size() == 4, "Five seats, the middle one behind the rear left door, a wheel spun on its own axis, nine panes, four side pipes");
        for (int x = 1; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        car.setDefinition("g63");
        spawn(h, car);
        Player owner = h.makeMockPlayer();
        own(car, owner);
        place(owner, car.position());
        open(car, car.opening("door_rl"));
        car.board(w(owner), 2);
        h.assertTrue(car.seatOf(w(owner)) == 2, "The owner takes the rear left seat");
        Player guest = h.makeMockPlayer();
        Point3D door = control(car, "door_rl").center;
        Point3D eye = car.localToWorld(new Point3D(2.4, 1.6, -0.6));
        place(guest, eye.add(0, -guest.getEyeHeight(), 0));
        lookAt(guest, door);
        car.interactPart(w(guest), true);
        h.assertTrue(car.seatOf(w(guest)) == 3, "Getting in through the rear left door with its seat taken lands in the middle: seat " + car.seatOf(w(guest)));
        h.assertTrue(car.animationActive("!locked") && !car.animationActive("locked"), "A negated condition plays while the car is open");
        car.remove();
        h.succeed();
    }

    private static Point3D outward(EntityVehicleF_Physics car, Hull.Box box) {
        double[] half = {box.half.x, box.half.y, box.half.z};
        int thin = 0;
        for (int i = 1; i < 3; i++) if (half[i] < half[thin]) thin = i;
        Point3D axis = box.axes[thin];
        if (Math.abs(axis.y) > .7) return axis.y > 0 ? axis : axis.scale(-1);
        Point3D away = box.center.subtract(car.position());
        return axis.dot(new Point3D(away.x, 0, away.z)) > 0 ? axis : axis.scale(-1);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void panesBreakAndOpeningsAnswerFromOutside(GameTestHelper h) {
        for (int x = 1; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);
        for (String id : new String[]{"volga", "g63"}) {
            EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
            car.setDefinition(id);
            spawn(h, car);
            for (PartOpening opening : car.openings()) {
                if (opening.definition.type.equals("glovebox")) continue;
                Hull.Box box = control(car, opening.id());
                Point3D out = outward(car, box);
                int normal = 0;
                for (int i = 1; i < 3; i++) if (Math.abs(box.axes[i].dot(out)) > Math.abs(box.axes[normal].dot(out))) normal = i;
                int[] across = {(normal + 1) % 3, (normal + 2) % 3};
                double[] half = {box.half.x, box.half.y, box.half.z};
                double depth = half[normal];
                for (double a : new double[]{-.6, 0, .6}) {
                    for (double b : new double[]{-.6, 0, .6}) {
                        Point3D point = box.center.add(out.scale(depth)).add(box.axes[across[0]].scale(a * half[across[0]])).add(box.axes[across[1]].scale(b * half[across[1]]));
                        Point3D eye = point.add(out.scale(1.5));
                        Optional<InteractionZone.Hit> hit = car.targetAlong(eye, eye.add(point.subtract(eye).normalize().scale(5)));
                        h.assertTrue(hit.isPresent() && hit.get().zone().part() == opening,
                                id + ": " + opening.id() + " answers a click at " + a + "," + b + " of its face, got " + hit.map(found -> found.zone().label()).orElse("nothing"));
                    }
                }
            }
            Player player = h.makeMockPlayer();
            List<Hull.Box> panes = car.hull().boxes().stream().filter(b -> b.glass >= 0).toList();
            h.assertTrue(panes.size() == car.definition().glass.size(), id + ": every pane is in the hull");
            for (Hull.Box pane : panes) {
                Point3D eye = pane.center.add(outward(car, pane).scale(1.5));
                place(player, eye.subtract(0, player.getEyeHeight(), 0));
                lookAt(player, pane.center);
                entity(car).hurt(player.damageSources().playerAttack(player), 1);
                h.assertFalse(car.brokenGlass(pane.glass), id + ": a punch or a swing from outside leaves " + car.definition().glass.get(pane.glass).bone + " whole");
                car.projectileHit(eye, eye.add(pane.center.subtract(eye).scale(2)), new WrapperDamage(player.damageSources().playerAttack(player)), 4);
                h.assertTrue(car.brokenGlass(pane.glass), id + ": a bullet from outside breaks " + car.definition().glass.get(pane.glass).bone);
            }
            car.remove();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sidePipesBlowClearOfTheWheels(GameTestHelper h) {
        JSONVehicle g63 = vehicle("g63");
        h.assertTrue(vehicle("volga").tailpipes.size() == 1 && vehicle("volga").tailpipes.get(0).direction().equals(new Point3D(0, 0, -1)), "One tailpipe blows backwards by default");
        for (JSONVehicle.Tailpipe pipe : g63.tailpipes) {
            h.assertTrue(Math.signum(pipe.direction().x) == Math.signum(pipe.pos().x), "Side pipe at " + pipe.pos() + " blows outwards");
            for (double d = 0; d <= 1.5; d += .05) {
                Point3D smoke = pipe.pos().add(pipe.direction().scale(d));
                for (AJSONMultipart.Wheel wheel : g63.wheels) {
                    boolean inside = Math.abs(smoke.x - wheel.pos.x) < wheel.halfWidth + .05 && Math.abs(smoke.y - wheel.pos.y) < wheel.radius
                            && Math.abs(smoke.z - wheel.pos.z) < wheel.radius;
                    h.assertFalse(inside, "Smoke from " + pipe.pos() + " runs into the " + wheel.bone + " tyre " + d + " out");
                }
            }
        }
        h.succeed();
    }



    @GameTest(template = EMPTY_STRUCTURE)
    public void closedCabinsMuffleTheOutside(GameTestHelper h) {
        JSONVehicle g63 = vehicle("g63");
        JSONVehicle volga = vehicle("volga");
        JSONVehicle scooter = vehicle("scooter");
        h.assertTrue(g63.cabinMuffle == JSONVehicle.DEFAULT_MUFFLE && volga.cabinMuffle == JSONVehicle.DEFAULT_MUFFLE && scooter.cabinMuffle == 0,
                "Cars with doors muffle the outside, a scooter does not");
        for (JSONVehicle car : List.of(g63, volga)) {
            for (AJSONMultipart.Seat seat : car.seats) h.assertTrue(car.inCabin(seat.pos.add(0, 1, 0)), car + ": a rider's head is in the cabin");
            h.assertFalse(car.inCabin(car.engine.pos), car + ": the engine is outside the cabin");
            for (JSONVehicle.Tailpipe pipe : car.tailpipes) h.assertFalse(car.inCabin(pipe.pos()), car + ": the tailpipes are outside the cabin");
        }
        h.assertFalse(scooter.inCabin(scooter.seats.get(0).pos), "An open vehicle has no cabin");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void eachCarHasItsOwnInteriorSounds(GameTestHelper h) {
        JSONSounds volga = vehicle("volga").sounds;
        JSONSounds g63 = vehicle("g63").sounds;
        for (JSONSounds.EngineLayer layer : g63.engine) h.assertTrue(layer.inside != null && layer.inside.endsWith("_inside"), "Every V8 layer has its cabin recording");
        h.assertTrue("slimvehicle:v8_start_inside".equals(g63.startInside) && JSONSounds.silent(g63.stop) && g63.spinDown > 0,
                "The V8 starts with its own cabin recording and winds down instead of playing the I4 stop");
        h.assertTrue(!g63.doorOpen.equals(volga.doorOpen) && !g63.doorClose.equals(volga.doorClose) && !g63.hoodClose.equals(volga.hoodClose)
                && !g63.gearShift.equals(volga.gearShift) && !g63.lock.equals(volga.lock) && !g63.handbrakeOn.equals(volga.handbrakeOn),
                "The G 63 does not borrow the Volga's doors, bonnet, lever, lock or handbrake");
        h.assertTrue(!g63.trunkClose.equals(g63.hoodClose) && !g63.gearShiftDown.equals(g63.gearShift),
                "A side-hinged tailgate and the lever going down have their own sounds");
        h.assertTrue(volga.trunkClose.equals(volga.hoodClose) && volga.gearShiftDown.equals(volga.gearShift), "Unset, the trunk and the lever going down fall back");
        h.assertTrue(g63.startFade(0) == 0 && g63.startFade(g63.catchTicks()) == 0 && g63.startFade(40) == 1 && volga.startFade(20) == 1,
                "Engine loops come in after the start sound catches and are full once it settles");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void seatsAndWheelsComeFromTheDefinition(GameTestHelper h) {
        JsonObject json = builtIn("volga");
        JsonObject wheel = json.getAsJsonArray("wheels").get(2).getAsJsonObject();
        for (int i = 0; i < 2; i++) {
            JsonObject extra = wheel.deepCopy();
            extra.addProperty("bone", "extra" + i);
            json.getAsJsonArray("wheels").add(extra);
        }
        for (int i = 0; i < 4; i++) {
            JsonObject seat = json.getAsJsonArray("seats").get(3).getAsJsonObject().deepCopy();
            seat.remove("door");
            json.getAsJsonArray("seats").add(seat);
        }
        JSONVehicle bus = define("bus", json);
        h.assertTrue(bus.wheels.size() == 6 && bus.seats.size() == 8 && bus.seats.get(7).doorOpening == null && bus.seats.get(0).doorOpening == bus.opening("door_fl"),
                "Six wheels and eight seats from the definition");
        JsonObject crowded = builtIn("volga");
        for (int i = 0; i < AJSONMultipart.MAX_SEATS; i++) crowded.getAsJsonArray("seats").add(crowded.getAsJsonArray("seats").get(1).deepCopy());
        boolean refused = false;
        try {
            define("crowded", crowded);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        h.assertTrue(refused, "More than " + AJSONMultipart.MAX_SEATS + " seats is refused with a clear error");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void soundsAndAnimationsComeFromTheDefinition(GameTestHelper h) {
        JSONVehicle volga = vehicle("volga");
        h.assertTrue(volga.sounds.horn.equals("slimvehicle:horn") && vehicle("scooter").sounds.horn.equals("slimvehicle:bell"), "Horns are named by the definitions");
        h.assertTrue(volga.sounds.engine.size() == 3 && volga.sounds.engine.get(1).blend(2400) == .5f && volga.sounds.engine.get(0).blend(900) == 1,
                "Engine layers cross-fade by rpm: " + volga.sounds.engine.get(1).blend(2400));
        h.assertTrue(volga.openingSound(volga.opening("bonnet"), true).equals("slimvehicle:hood_open"), "A bonnet sounds like a hood");
        JSONVehicle g63 = vehicle("g63");
        JSONSounds.EngineLayer low = g63.sounds.engine.get(1);
        h.assertTrue(g63.sounds.engine.size() == 4 && g63.sounds.start.equals("slimvehicle:v8_start") && Math.abs(low.pitch(2230) - 1) < 1e-6
                && Math.abs(low.pitch(3345) - 1.5) < 1e-6 && Math.abs(volga.sounds.engine.get(0).pitch(1600) - 1.05f) < 1e-6,
                "A layer that follows the revs plays at the pitch it was recorded at; the default curve stays gentle");
        JsonObject events;
        try {
            events = JSONParser.read(PackParser.findAsset(SlimCore.id("sounds.json")).orElseThrow());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (JSONVehicle definition : PackParser.vehicles()) {
            JSONSounds s = definition.sounds;
            List<String> names = new ArrayList<>(Arrays.asList(s.start, s.startInside, s.stop, s.horn, s.siren, s.radio, s.gearShift, s.gearShiftDown, s.lock,
                    s.handbrakeOn, s.handbrakeOff, s.bump, s.collision, s.blowout, s.exhaustPop, s.road, s.skid, s.doorOpen, s.doorClose, s.hoodOpen, s.hoodClose,
                    s.trunkOpen, s.trunkClose, s.hatchOpen, s.hatchClose));
            for (JSONSounds.EngineLayer layer : s.engine) {
                names.add(layer.sound);
                names.add(layer.inside);
            }
            for (String name : names) {
                if (JSONSounds.silent(name) || !name.startsWith("slimvehicle:")) continue;
                String event = name.substring("slimvehicle:".length());
                h.assertTrue(events.has(event) && PackParser.findAsset(SlimCore.id("sounds/" + event + ".ogg")).isPresent(), definition + ": no sound " + name);
            }
        }
        h.assertTrue(volga.animations.size() == 1 && volga.animationLocation.equals(SlimCore.id("animations/volga3102.animation.json")), "Animations are attached by the definition");
        for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        EntityVehicleF_Physics car = createAt(h, new Vec3(4, 1.02, 4));
        spawn(h, car);
        Player player = h.makeMockPlayer();
        own(car, player);
        place(player, car.position());
        openDoor(car, 0);
        car.board(w(player), 0);
        h.assertFalse(car.animationActive("engine"), "The idle animation waits for the engine");
        car.action(w(player), VehicleAction.ENGINE);
        h.assertTrue(car.animationActive("engine") && car.animationActive("open:door_fl") && !car.animationActive("open:trunk") && car.animationActive("seat:0"),
                "Animation conditions follow the vehicle's state");
        car.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void modelsAndAnimationsPlayWithoutGeckoLib(GameTestHelper h) {
        for (JSONVehicle definition : PackParser.vehicles()) {
            ModelGeometry model = ModelGeometry.load(definition.modelLocation);
            h.assertTrue(!model.roots.isEmpty() && model.textureWidth > 0, definition + ": the model is read");
            for (ModelSkeleton.Bone bone : ModelSkeleton.bones(definition.modelLocation)) h.assertTrue(model.bone(bone.name()) != null, definition + ": drawn model has " + bone.name());
        }
        JSONVehicle g63 = vehicle("g63");
        Animator animator = new Animator(ModelGeometry.load(g63.modelLocation), AnimationFile.load(g63.animationLocation), g63.animations);
        int mirror = animator.model.bone("mirror_fl").index;
        float rest = animator.pose().bind[mirror][Animator.ROTATION + 1];
        float folded = rest + (float) Math.toRadians(65);
        Set<String> state = new HashSet<>(Set.of("locked"));
        Predicate<String> when = condition -> condition.startsWith("!") != state.contains(condition.replace("!", ""));
        animator.update(100, 5, when);
        h.assertTrue(Math.abs(animator.pose().values[mirror][Animator.ROTATION + 1] - rest) < 1e-6, "Locking starts folding the mirrors from rest");
        animator.update(106, 5, when);
        float half = animator.pose().values[mirror][Animator.ROTATION + 1];
        h.assertTrue(half > rest + .1 && half < folded - .1, "Halfway through the fold the mirror is between rest and folded: " + half);
        animator.update(112, 5, when);
        h.assertTrue(Math.abs(animator.pose().values[mirror][Animator.ROTATION + 1] - folded) < 1e-5, "After 0.6 s the mirror is folded");
        animator.update(130, 5, when);
        h.assertTrue(Math.abs(animator.pose().values[mirror][Animator.ROTATION + 1] - folded) < 1e-5, "A play-and-hold animation keeps its last frame");
        state.clear();
        animator.update(140, 5, when);
        h.assertTrue(Math.abs(animator.pose().values[mirror][Animator.ROTATION + 1] - folded) < 1e-5, "Unlocking unfolds from the folded position, without a jump");
        animator.update(152.5, 5, when);
        h.assertTrue(Math.abs(animator.pose().values[mirror][Animator.ROTATION + 1] - rest) < 1e-6, "...and ends at rest");
        state.add("locked");
        animator.update(200, 5, when);
        animator.update(212, 5, when);
        animator.update(212.5, 5, condition -> false);
        h.assertTrue(Math.abs(animator.pose().values[mirror][Animator.ROTATION + 1] - folded) < 1e-5, "A bone left by its animation starts returning from where it was");
        animator.update(213, 5, condition -> false);
        float returning = animator.pose().values[mirror][Animator.ROTATION + 1];
        h.assertTrue(Math.abs(returning - (rest + folded) / 2) < 1e-4, "...reaching half way after half a tick: " + returning);
        animator.update(213.5, 5, condition -> false);
        h.assertTrue(animator.pose().values[mirror][Animator.ROTATION + 1] == rest, "...and rest after one tick");
        MoLang.Context second = new MoLang.Context() {
            @Override
            public double animTime() {
                return 1;
            }

            @Override
            public double lifeTime() {
                return 7;
            }
        };
        MoLang.Value wave = MoLang.parse("math.sin(query.anim_time * 90) * 10 + math.clamp(q.life_time, 0, 2) - -math.lerp(0, 4, 0.5) / 2");
        h.assertTrue(!wave.constant() && Math.abs(wave.get(second) - 13) < 1e-9, "Simple MoLang is evaluated in degrees, with queries: " + wave.get(second));
        h.assertTrue(MoLang.parse(" 2.5 ").constant() && MoLang.parse("2.5").get(second) == 2.5, "Plain numbers stay constants");
        boolean refused = false;
        try {
            MoLang.parse("query.is_on_fire * 3");
        } catch (IllegalArgumentException e) {
            refused = e.getMessage().contains("query.is_on_fire");
        }
        h.assertTrue(refused, "Unknown MoLang names are refused with the name in the message");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unknownKeysAreReported(GameTestHelper h) {
        JsonObject json = builtIn("volga");
        json.addProperty("colour", 1);
        json.getAsJsonObject("physics").addProperty("max_sped_kmh", 90);
        List<String> unknown = new ArrayList<>();
        define("typo", json, unknown);
        h.assertTrue(unknown.contains("colour") && unknown.contains("physics.max_sped_kmh") && unknown.size() == 2, "Typos in a definition are reported: " + unknown);
        JsonObject override = JsonParser.parseString("{\"physics\": {\"max_speed_kmh\": 60}, \"fuel\": {\"tank\": 80}}").getAsJsonObject();
        JSONVehicle tuned = define("tuned", JSONParser.merge(builtIn("volga"), override));
        h.assertTrue(tuned.physics.maxSpeedKmh == 60 && tuned.physics.brake == .02 && tuned.fuel.tank == 80 && tuned.fuel.perTick == .0007, "An override changes only what it lists");
        h.succeed();
    }
}
