package studio.merami.sliminterfacefabric.client.effects;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartGroundDevice;
import studio.merami.slimcore.jsondefs.JSONSounds;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.effects.VehicleParticles.Kind;
import studio.merami.sliminterfacefabric.client.sound.CabinAcoustics;
import studio.merami.sliminterfacefabric.client.sound.VehicleAudio;
import studio.merami.sliminterfacefabric.client.sound.VehicleCue;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.registry.SoundRegistry;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VehicleEffects {
    public static final int TRACK_LIFETIME = 100;
    public static final int TRACK_FADE = 50;
    public static final int MAX_TRACKS = 1024;
    private static final ResourceLocation TRACKS = SlimVehicle.id("textures/effect/tyre_tracks.png");
    private static final double TRACK_HALF_WIDTH = 5 / 32.0;
    private static final double TRACK_VIEW_DISTANCE = 48;
    private static final double FIRE_DETAIL = 16;
    private static final double CRACKLE_RANGE = 24;
    private static final float FIRE_LEAST = .15f;
    private static final Deque<Mark> MARKS = new ArrayDeque<>();
    private static final Map<Integer, Contact[]> LAST_TYRES = new HashMap<>();
    private static final Map<Integer, Engine> ENGINES = new HashMap<>();
    private static final Map<Integer, Fire> FIRES = new HashMap<>();
    private static final Map<String, VehicleAudio> SOUNDS = new HashMap<>();
    private static final Map<String, Integer> RETRIES = new HashMap<>();
    private static final int RETRY = 20;
    private static int clock;
    private static Object world;
    private static VertexBuffer mesh;
    private static boolean meshFilled;
    private static int meshClock = -1;
    private static Vec3 meshOrigin = Vec3.ZERO;

    private enum Surface {
        ROAD(.34f, .34f, .36f),
        GRASS(.62f, .66f, .50f),
        DIRT(.58f, .52f, .47f),
        SAND(.74f, .68f, .58f),
        SNOW(.70f, .74f, .84f);

        final float r;
        final float g;
        final float b;

        Surface(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private record Contact(Vec3 center, Vec3 side, Surface surface, double distance) {}

    private record Mark(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float v0, float v1, int born, float strength, Surface surface, boolean skid) {}

    private static final class Engine {
        int cued = -1000;
        int caught = -1000;
        float speed;
        float rpm;
    }

    private static final class Fire {
        int since = -1;
        int crackle;
    }

    private VehicleEffects() {}

    public static void tick(Minecraft mc) {
        if (mc.level != world) {
            MARKS.clear();
            meshClock = -1;
            meshFilled = false;
            LAST_TYRES.clear();
            ENGINES.clear();
            FIRES.clear();
            SOUNDS.values().forEach(sound -> mc.getSoundManager().stop(sound));
            SOUNDS.clear();
            RETRIES.clear();
            world = mc.level;
        }
        if (mc.level == null || mc.player == null) return;
        clock++;
        while (!MARKS.isEmpty() && clock - MARKS.peekFirst().born() >= TRACK_LIFETIME) MARKS.removeFirst();
        Set<Integer> active = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof BuilderVehicle builder) || builder.distanceToSqr(mc.player) > 2304) continue;
            EntityVehicleF_Physics vehicle = builder.vehicle();
            active.add(vehicle.id());
            JSONSounds sounds = vehicle.definition().sounds;
            List<JSONSounds.EngineLayer> layers = sounds.engine;
            for (int i = 0; i < layers.size(); i++) {
                JSONSounds.EngineLayer layer = layers.get(i);
                loop(mc, vehicle, VehicleAudio.Kind.ENGINE, i, layer.sound, layer);
                if (layer.inside != null && CabinAcoustics.inside(vehicle) > .01f) loop(mc, vehicle, VehicleAudio.Kind.ENGINE_INSIDE, i, layer.inside, layer);
            }
            start(mc, vehicle);
            loop(mc, vehicle, VehicleAudio.Kind.SIREN, 0, sounds.siren, null);
            loop(mc, vehicle, VehicleAudio.Kind.RADIO, 0, sounds.radio, null);
            loop(mc, vehicle, VehicleAudio.Kind.HORN, 0, sounds.horn, null);
            if (Math.abs(vehicle.speed()) > .02) {
                loop(mc, vehicle, VehicleAudio.Kind.ROAD, 0, sounds.road, null);
                loop(mc, vehicle, VehicleAudio.Kind.SKID, 0, sounds.skid, null);
            }
            exhaust(mc, vehicle);
            engineSmoke(mc, vehicle);
            fire(mc, vehicle);
            leak(mc, vehicle);
            wheels(mc, vehicle);
        }
        LAST_TYRES.keySet().retainAll(active);
        ENGINES.keySet().retainAll(active);
        FIRES.keySet().retainAll(active);
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        RETRIES.keySet().retainAll(SOUNDS.keySet());
    }

    private static Point3D motion(EntityVehicleF_Physics vehicle) {
        BuilderVehicle builder = BuilderVehicle.of(vehicle);
        return new Point3D(builder.getX(), builder.getY(), builder.getZ()).subtract(builder.xo, builder.yo, builder.zo);
    }

    private static float density(Minecraft mc) {
        ParticleStatus status = mc.options.particles().get();
        return status == ParticleStatus.ALL ? 1 : status == ParticleStatus.DECREASED ? .5f : .2f;
    }

    private static int count(RandomSource random, float count) {
        int whole = (int) count;
        return whole + (random.nextFloat() < count - whole ? 1 : 0);
    }

    private static void exhaust(Minecraft mc, EntityVehicleF_Physics vehicle) {
        Engine engine = ENGINES.computeIfAbsent(vehicle.id(), id -> new Engine());
        JSONVehicle definition = vehicle.definition();
        List<JSONVehicle.Tailpipe> tailpipes = definition.tailpipes;
        float speed = vehicle.speed();
        float rpm = vehicle.rpm();
        float accel = Math.abs(speed) - Math.abs(engine.speed);
        float rise = rpm - engine.rpm;
        engine.speed = speed;
        engine.rpm = rpm;
        if (tailpipes.isEmpty()) return;
        JSONVehicle.ExhaustEffects fx = definition.exhaustEffects;
        boolean running = vehicle.flag(EntityVehicleF_Physics.ENGINE);
        boolean pop = fx.flames && definition.sounds.popTicks.contains(vehicle.ticks() - vehicle.popAt);
        if (!running && !pop) return;
        int sinceStart = vehicle.ticks() - vehicle.startedAt;
        int catchTicks = definition.sounds.catchTicks();
        int startTicks = Math.max(1, (int) Math.round(fx.startSeconds * 20));
        boolean cranking = sinceStart < catchTicks;
        boolean caught = running && !cranking && sinceStart < catchTicks + 5 && engine.caught != vehicle.startedAt;
        if (caught) engine.caught = vehicle.startedAt;
        float starting = running && !cranking && sinceStart < catchTicks + startTicks
                ? (float) (fx.startSmoke * Math.pow(1 - (sinceStart - catchTicks) / (float) startTicks, 1.5)) : 0;
        RandomSource random = mc.level.random;
        float density = density(mc);
        Point3D motion = motion(vehicle);
        JSONVehicle.Engine revs = definition.engine;
        float span = (float) Math.max(1, revs.maxRpm - revs.idleRpm);
        float rev = Mth.clamp((rpm - (float) revs.idleRpm) / span, 0, 1);
        float load = Mth.clamp(accel * 90 + rev * .45f + Math.max(0, rise) / span * 6, 0, 1);
        int interval = load > .7f ? 1 : load > .35f ? 2 : rev > .25f ? 3 : 4;
        float smoke = (float) fx.smoke;
        for (JSONVehicle.Tailpipe tailpipe : tailpipes) {
            Point3D pipe = vehicle.chassisToWorld(tailpipe.pos());
            Point3D back = vehicle.directionToWorld(tailpipe.direction());
            if (pop) {
                jet(random, pipe, back, motion, 4, 1);
                VehicleParticles.spawn(Kind.EXHAUST, pipe.add(back.scale(.32)), back.scale(.06).add(motion.scale(.5)).add(0, .015, 0), 24, .18f, .7f, .5f, .32f);
            }
            if (!running || cranking) continue;
            if (caught) {
                if (fx.startFlame) jet(random, pipe, back, motion, 3, .8f);
                for (int i = count(random, (5 + 4 * (float) fx.startSmoke) * density); i > 0; i--) {
                    Point3D spread = new Point3D(random.nextGaussian() * .025, random.nextFloat() * .008, random.nextGaussian() * .025);
                    VehicleParticles.spawn(Kind.EXHAUST, pipe.add(back.scale(.1)), back.scale(.07 + random.nextFloat() * .07).add(spread),
                            70 + random.nextInt(30), .3f, 1.6f + random.nextFloat() * .8f, .62f, .32f + random.nextFloat() * .1f);
                }
            }
            if (starting > .02f) {
                for (int i = count(random, (.6f + 1.4f * starting) * density); i > 0; i--) {
                    Point3D spread = new Point3D(random.nextGaussian() * .018, .003 + random.nextFloat() * .006, random.nextGaussian() * .018);
                    VehicleParticles.spawn(Kind.EXHAUST, pipe.add(back.scale(.08)), back.scale(.04 + .05 * starting).add(motion.scale(.5)).add(spread),
                            55 + random.nextInt(30), .2f + .1f * starting, .9f + 1.1f * starting, .3f + .35f * Math.min(1, starting), Mth.lerp(Math.min(1, starting), .7f, .42f));
                }
                continue;
            }
            if (clock % interval != 0) continue;
            float strength = load;
            Point3D jitter = new Point3D(random.nextGaussian() * .006, random.nextGaussian() * .004, random.nextGaussian() * .006);
            Point3D velocity = back.scale(.035 + .09 * rev + .05 * load).add(motion.scale(.55)).add(0, .012 + .01 * strength, 0).add(jitter);
            VehicleParticles.spawn(Kind.EXHAUST, pipe.add(back.scale(.06)), velocity, (int) (28 + 22 * strength + random.nextInt(8)),
                    (.12f + .06f * strength) * (float) Math.sqrt(smoke), (.55f + .65f * strength) * (float) Math.sqrt(smoke),
                    Math.min(.8f, (.26f + .3f * load) * smoke), Mth.lerp(load, .78f, .42f));
        }
    }

    private static void jet(RandomSource random, Point3D pipe, Point3D back, Point3D motion, int puffs, float scale) {
        float flare = scale * (.85f + random.nextFloat() * .3f);
        for (int i = 0; i < puffs; i++) {
            Point3D at = pipe.add(back.scale(.03 + i * .09)).add(random.nextGaussian() * .01, random.nextGaussian() * .01, random.nextGaussian() * .01);
            VehicleParticles.spawn(Kind.BACKFIRE, at, back.scale(.05 + .025 * i).add(motion), 3 + i / 2 + random.nextInt(2),
                    (.07f + .04f * i) * flare, (.13f + .065f * i) * flare, 1, 1);
        }
    }

    private static void engineSmoke(Minecraft mc, EntityVehicleF_Physics vehicle) {
        JSONVehicle definition = vehicle.definition();
        if (!definition.hasEngineBay() || vehicle.fire() > 0) return;
        float engine = vehicle.engineHealth();
        if (engine >= 50) return;
        int interval = engine < 25 ? 3 : 7;
        if (clock % interval != 0) return;
        RandomSource random = mc.level.random;
        Point3D spread = definition.engine.spread;
        Point3D at = vehicle.chassisToWorld(definition.engine.pos.add(random.nextGaussian() * spread.x, random.nextGaussian() * spread.y, random.nextGaussian() * spread.z));
        Point3D drift = motion(vehicle).scale(.4)
                .add(random.nextGaussian() * .006, .035 + random.nextFloat() * .02, random.nextGaussian() * .006);
        float heavy = Mth.clamp((50 - engine) / 50f, 0, 1);
        VehicleParticles.spawn(Kind.EXHAUST, at, drift, (int) (30 + 30 * heavy + random.nextInt(10)), .2f + .15f * heavy, .8f + .9f * heavy,
                .25f + .35f * heavy, Mth.lerp(heavy, .8f, .18f));
    }

    private static void fire(Minecraft mc, EntityVehicleF_Physics vehicle) {
        Fire state = FIRES.computeIfAbsent(vehicle.id(), id -> new Fire());
        float strength = vehicle.fire();
        JSONVehicle definition = vehicle.definition();
        if (strength <= 0 || !definition.hasEngineBay()) {
            state.since = -1;
            return;
        }
        if (state.since < 0) {
            state.since = clock;
            state.crackle = clock;
        }
        int burning = clock - state.since;
        RandomSource random = mc.level.random;
        double distance = Convert.point(mc.gameRenderer.getMainCamera().getPosition()).distanceToSqr(vehicle.position());
        float density = density(mc) * Mth.clamp((float) (FIRE_DETAIL * FIRE_DETAIL / Math.max(1, distance)), FIRE_LEAST, 1);
        Point3D motion = motion(vehicle);
        Point3D wind = wind(mc);
        int spots = 1;
        if (strength >= 1) {
            for (int i = 0; i < definition.fire.size(); i++) if (burning > i * 22) spots++;
        }
        float smoke = 1f / spots;
        JSONVehicle.Emitter bay = new JSONVehicle.Emitter();
        bay.pos = definition.engine.pos;
        bay.spread = definition.engine.spread;
        burn(mc, vehicle, bay, strength * Mth.clamp(.35f + burning / 60f, 0, 1), motion, wind, density, smoke);
        if (strength >= 1) {
            for (int i = 0; i < definition.fire.size(); i++) {
                float spread = Mth.clamp((burning - i * 22) / 50f, 0, 1);
                if (spread > 0) burn(mc, vehicle, definition.fire.get(i), .9f * spread, motion, wind, density, smoke);
            }
        }
        if (clock >= state.crackle) {
            state.crackle = clock + 8 + random.nextInt(10) + (int) ((1 - strength) * 14);
            if (distance > CRACKLE_RANGE * CRACKLE_RANGE) return;
            Point3D at = vehicle.localToWorld(definition.engine.pos);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, .7f + strength, .75f + random.nextFloat() * .35f, false);
        }
    }

    private static void burn(Minecraft mc, EntityVehicleF_Physics vehicle, JSONVehicle.Emitter spot, float strength, Point3D motion, Point3D wind, float density, float smoke) {
        RandomSource random = mc.level.random;
        Point3D spread = spot.spread;
        float area = (float) Mth.clamp((spread.x + .1) * (spread.z + .1) * 4, .12, 1.6);
        for (int i = count(random, (.6f + 2.4f * strength) * area * density + .3f); i > 0; i--) {
            Point3D at = vehicle.chassisToWorld(spot.pos.add((random.nextFloat() * 2 - 1) * spread.x, (random.nextFloat() * 2 - 1) * spread.y, (random.nextFloat() * 2 - 1) * spread.z));
            Point3D velocity = new Point3D((random.nextFloat() - .5f) * .02, .025 + .035 * strength + random.nextFloat() * .02, (random.nextFloat() - .5f) * .02)
                    .add(motion.scale(.7)).add(wind.scale(.25));
            float size = (.24f + .36f * strength) * (.75f + .5f * random.nextFloat());
            VehicleParticles.spawn(Kind.FLAME, at, velocity, 9 + random.nextInt(7) + (int) (5 * strength), size * .45f, size, .95f, 1);
        }
        if (strength > .4f && random.nextFloat() < strength * density) {
            Point3D at = vehicle.chassisToWorld(spot.pos.add((random.nextFloat() * 2 - 1) * spread.x * .6, -.05, (random.nextFloat() * 2 - 1) * spread.z * .6));
            VehicleParticles.spawn(Kind.FLAME, at, new Point3D(0, .02, 0).add(motion.scale(.8)), 6 + random.nextInt(4), .15f, .3f + .15f * strength, 1, 1.15f);
        }
        if (random.nextFloat() < .35f * strength * density) {
            Point3D at = vehicle.chassisToWorld(spot.pos.add((random.nextFloat() * 2 - 1) * spread.x, .1, (random.nextFloat() * 2 - 1) * spread.z));
            Point3D velocity = new Point3D((random.nextFloat() - .5f) * .05, .07 + random.nextFloat() * .06, (random.nextFloat() - .5f) * .05).add(wind.scale(.5)).add(motion.scale(.5));
            VehicleParticles.spawn(Kind.EMBER, at, velocity, 22 + random.nextInt(24), .035f + random.nextFloat() * .025f, .03f, 1, 1);
        }
        if (random.nextFloat() < (.35f + .6f * strength) * density * smoke) {
            Point3D at = vehicle.chassisToWorld(spot.pos.add((random.nextFloat() * 2 - 1) * spread.x * .7, .45 + .35 * strength, (random.nextFloat() * 2 - 1) * spread.z * .7));
            Point3D velocity = new Point3D((random.nextFloat() - .5f) * .012, .045 + .035 * strength + random.nextFloat() * .015, (random.nextFloat() - .5f) * .012)
                    .add(wind.scale(.8)).add(motion.scale(.35));
            float shade = .07f + random.nextFloat() * .09f;
            VehicleParticles.spawn(Kind.FIRE_SMOKE, at, velocity, 50 + random.nextInt(30) + (int) (25 * strength), .45f + .25f * strength, 1.5f + 1.6f * strength,
                    Math.min(.9f, (.5f + .25f * strength) * (1.3f - .3f * smoke)), shade);
        }
    }

    private static Point3D wind(Minecraft mc) {
        double angle = mc.level.getGameTime() * .0004;
        return new Point3D(Math.cos(angle), 0, Math.sin(angle)).scale(.01 * (1 + mc.level.getRainLevel(1) * 1.5));
    }

    private static void leak(Minecraft mc, EntityVehicleF_Physics vehicle) {
        Point3D tank = vehicle.definition().damage.fuelTank;
        float leak = vehicle.fuelLeak();
        if (tank == null || leak <= 0 || vehicle.fuel() <= 0) return;
        RandomSource random = mc.level.random;
        if (random.nextFloat() < Math.min(.6f, leak * 150) * density(mc)) {
            Point3D at = vehicle.chassisToWorld(tank.add((random.nextFloat() - .5f) * .3, 0, (random.nextFloat() - .5f) * .3));
            mc.level.addParticle(ParticleTypes.FALLING_HONEY, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    private static void wheels(Minecraft mc, EntityVehicleF_Physics vehicle) {
        JSONVehicle definition = vehicle.definition();
        Contact[] previous = LAST_TYRES.computeIfAbsent(vehicle.id(), id -> new Contact[JSONVehicle.MAX_WHEELS]);
        if (Math.abs(vehicle.speed()) < .02 || !definition.tyreMarks) {
            Arrays.fill(previous, null);
            return;
        }
        RandomSource random = mc.level.random;
        boolean skid = vehicle.slip() > .035;
        boolean fast = Math.abs(vehicle.speed()) > .1;
        Point3D back = new Point3D(0, 0, -Math.signum(vehicle.speed())).yRot((float) -Math.toRadians(vehicle.yaw()));
        for (PartGroundDevice wheel : vehicle.wheels()) {
            int i = wheel.index;
            Vec3 at = Convert.vec(vehicle.localToWorld(wheel.center().add(0, wheel.travel, 0)).add(0, .2, 0));
            BlockHitResult ground = mc.level.clip(new ClipContext(at, at.add(0, -1.1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, BuilderVehicle.of(vehicle)));
            if (ground.getType() != HitResult.Type.BLOCK || ground.getDirection() != Direction.UP) {
                previous[i] = null;
                continue;
            }
            Vec3 center = ground.getLocation().add(0, .004, 0);
            Surface surface = surface(mc.level.getBlockState(ground.getBlockPos()));
            boolean soft = surface != Surface.ROAD;
            Point3D kick = back.scale(.03 + Math.abs(vehicle.speed()) * .12).add(0, .06 + Math.abs(vehicle.speed()) * .08, 0);
            if (soft && fast && random.nextFloat() < Math.min(.85f, Math.abs(vehicle.speed()) * 1.4f)) {
                Point3D spray = new Point3D(random.nextGaussian() * .02, random.nextFloat() * .03, random.nextGaussian() * .02);
                switch (surface) {
                    case DIRT, GRASS -> VehicleParticles.spawn(Kind.DIRT, Convert.point(center).add(0, .06, 0), kick.add(spray), 14 + random.nextInt(8), .07f, .05f, 1, 1);
                    case SAND -> VehicleParticles.spawn(Kind.DUST, Convert.point(center).add(0, .1, 0), kick.scale(.6).add(spray), 24 + random.nextInt(10), .18f, .5f, .6f, 1);
                    case SNOW -> VehicleParticles.spawn(Kind.SNOW, Convert.point(center).add(0, .08, 0), kick.add(spray), 16 + random.nextInt(8), .1f, .14f, .9f, 1);
                    default -> {
                    }
                }
                if (surface == Surface.DIRT && random.nextFloat() < .35f) {
                    VehicleParticles.spawn(Kind.DUST, Convert.point(center).add(0, .12, 0), kick.scale(.4).add(spray), 26 + random.nextInt(10), .2f, .6f, .32f, .75f);
                }
            }
            boolean driven = !wheel.definition.steer;
            if (skid && (driven || vehicle.flag(EntityVehicleF_Physics.BRAKING)) && random.nextFloat() < Math.min(.9f, vehicle.slip() * 4)) {
                VehicleParticles.spawn(Kind.TYRE_SMOKE, Convert.point(center).add(0, .15, 0), kick.scale(.35).add(random.nextGaussian() * .01, .012, random.nextGaussian() * .01),
                        32 + random.nextInt(14), .35f, 1.25f, soft ? .3f : .42f, soft ? .62f : .86f);
            }
            boolean sliding = skid && (driven || vehicle.flag(EntityVehicleF_Physics.BRAKING) || vehicle.slip() > .12);
            if (!soft && !sliding) {
                previous[i] = null;
                continue;
            }
            Vec3 side = new Vec3(TRACK_HALF_WIDTH, 0, 0).yRot((float) -Math.toRadians(vehicle.yaw()) + (wheel.definition.steer ? vehicle.visualSteer : 0));
            Contact old = previous[i];
            Contact next = new Contact(center, side, surface, old == null ? random.nextInt(16) / 16.0 : old.distance() + old.center().distanceTo(center));
            if (old != null && old.surface() == surface) {
                double distance = old.center().distanceTo(center);
                if (distance < .1) continue;
                if (distance < 3 && Math.abs(old.center().y - center.y) < .5) stamp(old, next, sliding ? Math.min(1, .55f + vehicle.slip() * 2) : .85f, sliding);
            }
            previous[i] = next;
        }
    }

    private static Surface surface(BlockState state) {
        if (state.is(BlockTags.SAND)) return Surface.SAND;
        if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) return Surface.SNOW;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK)) return Surface.GRASS;
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(Blocks.CLAY)) return Surface.DIRT;
        return Surface.ROAD;
    }

    private static void loop(Minecraft mc, EntityVehicleF_Physics vehicle, VehicleAudio.Kind kind, int index, String sound, JSONSounds.EngineLayer layer) {
        if (JSONSounds.silent(sound) || kind.flag() != 0 && !vehicle.flag(kind.flag())) return;
        String key = vehicle.id() + ":" + kind + ":" + index;
        VehicleAudio playing = SOUNDS.get(key);
        if (playing != null && !playing.isStopped()) {
            if (mc.getSoundManager().isActive(playing) || clock - RETRIES.getOrDefault(key, -RETRY) < RETRY) return;
            mc.getSoundManager().stop(playing);
        }
        RETRIES.put(key, clock);
        VehicleAudio audio = kind == VehicleAudio.Kind.RADIO ? VehicleAudio.radio(vehicle, sound) : new VehicleAudio(vehicle, SoundRegistry.event(sound), kind, layer);
        SOUNDS.put(key, audio);
        mc.getSoundManager().play(audio);
    }

    private static void start(Minecraft mc, EntityVehicleF_Physics vehicle) {
        Engine engine = ENGINES.computeIfAbsent(vehicle.id(), id -> new Engine());
        if (engine.cued == vehicle.startedAt || vehicle.ticks() - vehicle.startedAt > 3) return;
        engine.cued = vehicle.startedAt;
        JSONSounds sounds = vehicle.definition().sounds;
        boolean inside = sounds.startInside != null && CabinAcoustics.inside(vehicle) > .5f;
        String sound = inside ? sounds.startInside : sounds.start;
        if (!JSONSounds.silent(sound)) mc.getSoundManager().play(new VehicleCue(vehicle, SoundRegistry.event(sound), inside));
    }

    public static void render(WorldRenderContext context) {
        if (MARKS.isEmpty()) return;
        Vec3 camera = context.camera().getPosition();
        if (meshClock != clock || meshOrigin.distanceToSqr(camera) > 256) rebuildTracks(camera);
        if (!meshFilled) return;
        PoseStack pose = context.matrixStack();
        pose.pushPose();
        pose.translate(meshOrigin.x - camera.x, meshOrigin.y - camera.y, meshOrigin.z - camera.z);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1, -10);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, TRACKS);
        mesh.bind();
        mesh.drawWithShader(pose.last().pose(), context.projectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        RenderSystem.polygonOffset(0, 0);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void rebuildTracks(Vec3 camera) {
        meshClock = clock;
        meshOrigin = new Vec3(Math.floor(camera.x), Math.floor(camera.y), Math.floor(camera.z));
        Matrix4f matrix = new Matrix4f().translation((float) -meshOrigin.x, (float) -meshOrigin.y, (float) -meshOrigin.z);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        int count = 0;
        for (Mark mark : MARKS) {
            if (mark.a().distanceToSqr(camera) > TRACK_VIEW_DISTANCE * TRACK_VIEW_DISTANCE) continue;
            float a = mark.strength() * Math.min(1, (TRACK_LIFETIME - (clock - mark.born())) / (float) TRACK_FADE);
            if (a < .01f) continue;
            float u0 = mark.skid() ? .5f : 0;
            float u1 = u0 + .5f;
            float r = mark.surface().r * a;
            float g = mark.surface().g * a;
            float b = mark.surface().b * a;
            track(buffer, matrix, mark.a(), u0, mark.v0(), r, g, b, a);
            track(buffer, matrix, mark.b(), u1, mark.v0(), r, g, b, a);
            track(buffer, matrix, mark.c(), u1, mark.v1(), r, g, b, a);
            track(buffer, matrix, mark.d(), u0, mark.v1(), r, g, b, a);
            count++;
        }
        BufferBuilder.RenderedBuffer rendered = buffer.end();
        meshFilled = count > 0;
        if (count == 0) {
            rendered.release();
            return;
        }
        if (mesh == null) mesh = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        mesh.bind();
        mesh.upload(rendered);
        VertexBuffer.unbind();
    }

    private static void track(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float u, float v, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(u, v).endVertex();
    }

    private static void stamp(Contact from, Contact to, float strength, boolean skid) {
        Vec3 l0 = from.center().add(from.side());
        Vec3 r0 = from.center().subtract(from.side());
        Vec3 l1 = to.center().add(to.side());
        Vec3 r1 = to.center().subtract(to.side());
        if (Math.abs(l1.y - l0.y) >= .12 || Math.abs(r1.y - r0.y) >= .12) return;
        double base = Math.floor(from.distance());
        while (MARKS.size() >= MAX_TRACKS) MARKS.removeFirst();
        MARKS.addLast(new Mark(l0, r0, r1, l1, (float) (from.distance() - base), (float) (to.distance() - base), clock, strength, from.surface(), skid));
    }

    public static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float u, float v, int color, float a) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).uv(u, v)
                .color(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, a).endVertex();
    }
}
