package studio.merami.sliminterfacefabric.client.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector4d;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.client.ClientConfig;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VehicleLights {
    static final int MAX_LAMPS = 32;
    static final int MAX_OCCLUDERS = 16;
    private static final int MAX_FIGURES = 8;
    private static final double FIGURE_SHRINK = .1;
    private static final float CLEAR_HAZE = .008f;
    static final int MAX_SHADOWED = 8;
    static final int MAP_WIDTH = 256;
    static final int MAP_HEIGHT = 128;
    private static final int ROWS = MAX_LAMPS + MAX_OCCLUDERS;
    private static final int MAX_VEHICLES = 8;
    private static final int HAZE_LAMPS = 4;
    private static final double REACH = 64;
    private static final Point3D FORWARD = new Point3D(0, 0, 1);
    private static final Point3D BACK = new Point3D(0, 0, -1);
    private static final Point3D RIGHT = new Point3D(-1, 0, 0);
    private static final Point3D LEFT = new Point3D(1, 0, 0);
    private static final Point3D UP = new Point3D(0, 1, 0);
    private static final float TAIL_RANGE = 8;
    private static final float TAIL_GLOW = .45f;
    private static final float INDICATOR_RANGE = 5;
    private static final float POSITION_RANGE = 4;
    private static final float POSITION_GLOW = .35f;
    private static final float REVERSE_RANGE = 8;
    private static final float FIRE_RANGE = 11;
    private static final int OMNI = -2;
    private static final float DARK = .3f;
    private static final float[] HALOGEN = {1, .87f, .68f};
    private static final float[] LED = {.86f, .93f, 1};
    private static final FloatBuffer DATA = ByteBuffer.allocateDirect(ROWS * 16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final Map<Integer, Levels> LEVELS = new HashMap<>();
    private static ShaderInstance light;
    private static ShaderInstance shadow;
    private static TextureTarget scene;
    private static TextureTarget shadowMaps;
    private static int lampTexture = -1;
    private static int lamps;
    private static long lastFrame;
    private static Object world;
    private static boolean enabled;

    private static final class Levels {
        float head;
        float tail;
        float brake;
        float left;
        float right;
        float reverse;
    }

    private VehicleLights() {}

    public static boolean register() {
        enabled = ClientConfig.lights == ClientConfig.Lights.FULL;
        if (enabled) LightGL.registerShaders(shader -> light = shader, shader -> shadow = shader);
        return enabled;
    }

    public static boolean active() {
        return enabled && !IrisCompat.shaderPackInUse();
    }

    public static void tick(Minecraft mc) {
        if (!enabled) return;
        if (world != mc.level) {
            world = mc.level;
            LEVELS.clear();
            if (mc.level == null) release();
        }
        if (mc.level == null || mc.player == null) return;
        if (!active()) {
            LightVolume.tick(mc, false);
            return;
        }
        boolean lit = false;
        float daylight = Mth.clamp((mc.level.getSkyDarken(1) - .2f) / .8f, 0, 1);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof BuilderVehicle builder && builder.distanceToSqr(mc.player) < REACH * REACH && glows(mc.level, builder.vehicle(), daylight)) {
                lit = true;
                break;
            }
        }
        LightVolume.tick(mc, lit);
        LEVELS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || light == null || shadow == null || !active()) return;
        long now = Util.getNanos();
        float dt = lastFrame == 0 ? 0 : Mth.clamp((now - lastFrame) / 1e9f, 0, .1f);
        lastFrame = now;
        float partial = context.tickDelta();
        Vec3 view = context.camera().getPosition();
        Point3D camera = Convert.point(view);
        List<EntityVehicleF_Physics> vehicles = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            EntityVehicleF_Physics vehicle = BuilderVehicle.core(entity);
            if (vehicle != null && vehicle.isAlive() && (vehicle.definition().lamps != null || vehicle.fire() > 0)
                    && vehicle.distanceToSqr(camera) < REACH * REACH) vehicles.add(vehicle);
        }
        if (vehicles.isEmpty()) return;
        vehicles.sort(Comparator.comparingDouble(vehicle -> vehicle.distanceToSqr(camera)));
        if (vehicles.size() > MAX_VEHICLES) vehicles = vehicles.subList(0, MAX_VEHICLES);
        float daylight = Mth.clamp((level.getSkyDarken(partial) - .2f) / .8f, 0, 1);
        Levels[] state = new Levels[vehicles.size()];
        float[] night = new float[vehicles.size()];
        int[] packed = new int[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++) {
            EntityVehicleF_Physics vehicle = vehicles.get(i);
            state[i] = update(vehicle, dt);
            BlockPos cabin = cabin(vehicle);
            int sky = level.getBrightness(LightLayer.SKY, cabin);
            int block = level.getBrightness(LightLayer.BLOCK, cabin);
            night[i] = night(daylight, sky, block);
            packed[i] = block * 16 + sky;
        }
        lamps = 0;
        int shadowed = 0;
        boolean[] traced = new boolean[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++) {
            if (state[i].head > .004f && shadowed + 2 <= MAX_SHADOWED) {
                shadowed += headlights(vehicles.get(i), state[i].head * night[i], true, packed[i], partial, camera);
                traced[i] = true;
            }
        }
        for (int i = 0; i < vehicles.size(); i++) {
            if (state[i].head > .004f && !traced[i]) headlights(vehicles.get(i), state[i].head * night[i], false, packed[i], partial, camera);
        }
        for (int i = 0; i < vehicles.size(); i++) smallLamps(vehicles.get(i), state[i], night[i], packed[i], partial, camera);
        for (int i = 0; i < vehicles.size(); i++) fire(vehicles.get(i), night[i], packed[i], partial, camera);
        if (lamps == 0) return;
        int occluders = 0;
        for (EntityVehicleF_Physics vehicle : vehicles) {
            AJSONInteractable.Box box = vehicle.definition().lightOccluder;
            if (box == null) continue;
            int offset = (MAX_LAMPS + occluders++) * 16;
            put(offset, vehicle.chassisToWorld(box.center, partial).subtract(camera), (float) Math.toRadians(Mth.rotLerp(partial, vehicle.prevYaw(), vehicle.yaw())));
            put(offset + 4, box.half.scale(vehicle.scale()), vehicle.id());
        }
        if (shadowed > 0) occluders = figures(level, context.camera(), partial, occluders);
        upload();

        RenderTarget main = LightGL.mainTarget();
        LightGL.beginPass();
        boolean volume = LightVolume.ready();
        BlockPos origin = LightVolume.origin();
        int volumeTexture = volume ? LightVolume.texture() : lampTexture;
        Vec3 volumeOrigin = volume ? new Vec3(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z) : Vec3.ZERO;
        if (shadowed > 0) {
            shadowMaps = LightGL.shadowTarget(shadowMaps, MAP_WIDTH, MAP_HEIGHT * MAX_SHADOWED);
            LightGL.drawInto(shadowMaps);
            shadow.setSampler("Lamps", lampTexture);
            shadow.setSampler("Volume", volumeTexture);
            uniform(shadow, "VolumeOrigin", volumeOrigin);
            shadow.safeGetUniform("VolumeReady").set(volume ? 1 : 0);
            shadow.safeGetUniform("ShadowCount").set(shadowed);
            shadow.safeGetUniform("OccluderCount").set(occluders);
            LightGL.drawFullscreen(shadow);
        }
        scene = LightGL.copyOf(scene, main);
        LightGL.drawInto(main);
        Matrix4f viewProjection = new Matrix4f(context.projectionMatrix()).mul(context.matrixStack().last().pose());
        Vector4d eye = new Matrix4d(viewProjection).invert().transform(new Vector4d(0, 0, 1, 0));
        light.safeGetUniform("ViewProjection").set(viewProjection);
        light.safeGetUniform("InverseViewProjection").set(new Matrix4f(viewProjection).invert());
        light.safeGetUniform("Eye").set((float) (eye.x / eye.w), (float) (eye.y / eye.w), (float) (eye.z / eye.w));
        light.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        light.safeGetUniform("LampCount").set(lamps);
        light.safeGetUniform("HazeCount").set(Math.min(HAZE_LAMPS, shadowed));
        light.safeGetUniform("Haze").set(haze(context, level, partial));
        light.setSampler("Lightmap", LightGL.lightmapOn(context.lightmapTextureManager()));
        light.setSampler("SceneColor", scene.getColorTextureId());
        light.setSampler("SceneDepth", scene.getDepthTextureId());
        light.setSampler("Lamps", lampTexture);
        light.setSampler("Shadows", shadowMaps != null ? shadowMaps.getColorTextureId() : lampTexture);
        light.setSampler("Volume", volumeTexture);
        uniform(light, "VolumeOrigin", volumeOrigin);
        light.safeGetUniform("VolumeReady").set(volume ? 1 : 0);
        LightGL.drawFullscreen(light);
        LightGL.lightmapOff(context.lightmapTextureManager());
        LightGL.endPass();
    }

    private static int figures(ClientLevel level, Camera view, float partial, int occluders) {
        Vec3 camera = view.getPosition();
        Entity self = view.isDetached() ? null : view.getEntity();
        List<LivingEntity> figures = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && living != self && !living.isSpectator() && !living.isInvisible() && !(living.getVehicle() instanceof BuilderVehicle)
                    && living.distanceToSqr(camera) < REACH * REACH) figures.add(living);
        }
        figures.sort(Comparator.comparingDouble(figure -> figure.distanceToSqr(camera)));
        for (int i = 0; i < figures.size() && i < MAX_FIGURES && occluders < MAX_OCCLUDERS; i++) {
            LivingEntity figure = figures.get(i);
            AABB box = figure.getBoundingBox().move(figure.getPosition(partial).subtract(figure.position()));
            int offset = (MAX_LAMPS + occluders++) * 16;
            put(offset, Convert.point(box.getCenter().subtract(camera)), 0);
            put(offset + 4, new Point3D(Math.max(.05, box.getXsize() / 2 - FIGURE_SHRINK), box.getYsize() / 2, Math.max(.05, box.getZsize() / 2 - FIGURE_SHRINK)), -1);
        }
        return occluders;
    }

    private static Levels update(EntityVehicleF_Physics vehicle, float dt) {
        Levels levels = LEVELS.computeIfAbsent(vehicle.id(), id -> new Levels());
        boolean blink = (vehicle.world().gameTime() % 16) < 8;
        boolean flash = vehicle.lockFlash(vehicle.world().gameTime());
        levels.head = approach(levels.head, vehicle.flag(EntityVehicleF_Physics.LIGHTS), dt, .12f, .25f);
        levels.tail = approach(levels.tail, vehicle.flag(EntityVehicleF_Physics.LIGHTS), dt, .12f, .25f);
        levels.brake = approach(levels.brake, vehicle.flag(EntityVehicleF_Physics.BRAKING), dt, .07f, .18f);
        levels.left = approach(levels.left, vehicle.flag(EntityVehicleF_Physics.TURN_LEFT) && blink || flash, dt, .05f, .1f);
        levels.right = approach(levels.right, vehicle.flag(EntityVehicleF_Physics.TURN_RIGHT) && blink || flash, dt, .05f, .1f);
        levels.reverse = approach(levels.reverse, vehicle.gear() < 0 && vehicle.flag(EntityVehicleF_Physics.ENGINE), dt, .08f, .15f);
        return levels;
    }

    private static float approach(float value, boolean on, float dt, float rise, float fall) {
        return Mth.lerp(1 - (float) Math.exp(-dt / (on ? rise : fall)), value, on ? 1 : 0);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static BlockPos cabin(EntityVehicleF_Physics vehicle) {
        return BlockPos.containing(Convert.vec(vehicle.localToWorld(new Point3D(0, 1.25, 0))));
    }

    private static float night(float daylight, int sky, int block) {
        float ambient = Math.max(daylight * sky / 15f, block / 15f * .8f);
        return 1 - .93f * smooth(Mth.clamp((ambient - .1f) / .65f, 0, 1));
    }

    private static boolean glows(ClientLevel level, EntityVehicleF_Physics vehicle, float daylight) {
        if (vehicle.fire() > 0) return true;
        if (vehicle.definition().lamps == null) return false;
        if (vehicle.flag(EntityVehicleF_Physics.LIGHTS)) return true;
        if (!vehicle.flag(EntityVehicleF_Physics.BRAKING) && !vehicle.flag(EntityVehicleF_Physics.TURN_LEFT) && !vehicle.flag(EntityVehicleF_Physics.TURN_RIGHT)
                && !(vehicle.gear() < 0 && vehicle.flag(EntityVehicleF_Physics.ENGINE)) && !vehicle.lockFlash(level.getGameTime())) return false;
        BlockPos cabin = cabin(vehicle);
        return night(daylight, level.getBrightness(LightLayer.SKY, cabin), level.getBrightness(LightLayer.BLOCK, cabin)) > DARK;
    }

    private static float haze(WorldRenderContext context, ClientLevel level, float partial) {
        if (context.camera().getFluidInCamera() != FogType.NONE) return 0;
        return CLEAR_HAZE + .012f * level.getRainLevel(partial) + .006f * level.getThunderLevel(partial);
    }

    private static int headlights(EntityVehicleF_Physics vehicle, float k, boolean shadow, int light, float partial, Point3D camera) {
        JSONVehicle.Lamps lamps = vehicle.definition().lamps;
        Point3D lens = lamps == null ? null : lamps.headlight;
        if (lens == null) return 0;
        int shadowed = 0;
        float[] tint = ClientConfig.led(lamps) ? LED : HALOGEN;
        for (int side = 0; side < 2; side++) {
            if (vehicle.lampBroken(side == 0 ? EntityVehicleF_Physics.HEADLIGHT_RIGHT : EntityVehicleF_Physics.HEADLIGHT_LEFT) || VehicleLights.lamps >= MAX_LAMPS) continue;
            Point3D at = vehicle.chassisToWorld(new Point3D(side == 0 ? -lens.x : lens.x, lens.y, lens.z), partial);
            emit(vehicle, at, vehicle.levelledDirection(FORWARD, partial), vehicle.levelledDirection(RIGHT, partial), 0,
                    shadow ? VehicleLights.lamps : -1, tint[0] * k, tint[1] * k, tint[2] * k, light, camera);
            if (shadow) shadowed++;
        }
        return shadowed;
    }

    private static void smallLamps(EntityVehicleF_Physics vehicle, Levels levels, float night, int light, float partial, Point3D camera) {
        JSONVehicle.Lamps lamps = vehicle.definition().lamps;
        if (lamps == null) return;
        float tail = Math.max(TAIL_GLOW * levels.tail, levels.brake) * night;
        float amber = .8f * night;
        float reverse = .55f * levels.reverse * night;
        float[] tint = ClientConfig.led(lamps) ? LED : HALOGEN;
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? 1 : -1;
            float indicator = (side == 0 ? levels.left : levels.right) * amber;
            float position = POSITION_GLOW * levels.tail * night * (1 - Math.min(1, indicator / amber * 2));
            if (lamps.frontIndicator != null && position > .004f) {
                add(vehicle, mirror(lamps.frontIndicator, x), FORWARD, RIGHT, POSITION_RANGE, -1, tint[0] * position, tint[1] * position, tint[2] * position, light, partial, camera);
            }
            if (lamps.taillight != null && tail > .004f && !vehicle.lampBroken(side == 0 ? EntityVehicleF_Physics.TAILLIGHT_LEFT : EntityVehicleF_Physics.TAILLIGHT_RIGHT)) {
                add(vehicle, mirror(lamps.taillight, x), BACK, LEFT, TAIL_RANGE, -1, tail, .06f * tail, .03f * tail, light, partial, camera);
            }
            if (indicator > .004f) {
                if (lamps.frontIndicator != null) add(vehicle, mirror(lamps.frontIndicator, x), FORWARD, RIGHT, INDICATOR_RANGE, -1, indicator, .42f * indicator, .04f * indicator, light, partial, camera);
                if (lamps.rearIndicator != null) add(vehicle, mirror(lamps.rearIndicator, x), BACK, LEFT, INDICATOR_RANGE, -1, indicator, .42f * indicator, .04f * indicator, light, partial, camera);
            }
            if (lamps.reverse != null && reverse > .004f) add(vehicle, mirror(lamps.reverse, x), BACK, LEFT, REVERSE_RANGE, -1, reverse, .95f * reverse, .86f * reverse, light, partial, camera);
        }
    }

    private static Point3D mirror(Point3D left, float side) {
        return new Point3D(side * left.x, left.y, left.z);
    }

    private static void fire(EntityVehicleF_Physics vehicle, float night, int light, float partial, Point3D camera) {
        float strength = vehicle.fire();
        JSONVehicle definition = vehicle.definition();
        if (strength <= 0 || !definition.hasEngineBay()) return;
        float t = (vehicle.world().gameTime() + partial) * .35f;
        float seed = vehicle.id() * 1.7f;
        float flicker = .8f + .12f * Mth.sin(t * 2.3f + seed) + .08f * Mth.sin(t * 5.7f + seed * 2.1f);
        float k = Math.max(night, .2f) * strength * flicker * 1.6f;
        add(vehicle, definition.engine.pos.add(0, .45, 0), UP, RIGHT, FIRE_RANGE, OMNI, k, .44f * k, .12f * k, light, partial, camera);
        if (strength >= 1 && !definition.fire.isEmpty()) {
            Point3D middle = Point3D.ZERO;
            for (JSONVehicle.Emitter spot : definition.fire) middle = middle.add(spot.pos);
            middle = middle.scale(1.0 / definition.fire.size()).add(0, .55, 0);
            float m = k * (.9f + .1f * Mth.sin(t * 3.1f + seed * .7f));
            add(vehicle, middle, UP, RIGHT, FIRE_RANGE, OMNI, m, .42f * m, .11f * m, light, partial, camera);
        }
    }

    private static void add(EntityVehicleF_Physics vehicle, Point3D local, Point3D forward, Point3D right, float type, int slot, float r, float g, float b, int light, float partial, Point3D camera) {
        if (lamps >= MAX_LAMPS) return;
        Point3D at = vehicle.chassisToWorld(local, partial);
        Point3D f = vehicle.chassisToWorld(local.add(forward), partial).subtract(at).normalize();
        Point3D side = vehicle.chassisToWorld(local.add(right), partial).subtract(at);
        emit(vehicle, at, f, side, type, slot, r, g, b, light, camera);
    }

    private static void emit(EntityVehicleF_Physics vehicle, Point3D at, Point3D f, Point3D side, float type, int slot, float r, float g, float b, int light, Point3D camera) {
        if (lamps >= MAX_LAMPS) return;
        side = side.subtract(f.scale(side.dot(f))).normalize();
        int offset = lamps++ * 16;
        put(offset, at.subtract(camera), type);
        put(offset + 4, f, slot);
        DATA.put(offset + 8, r).put(offset + 9, g).put(offset + 10, b).put(offset + 11, vehicle.id());
        put(offset + 12, side, light);
    }

    private static void put(int offset, Point3D v, float w) {
        DATA.put(offset, (float) v.x).put(offset + 1, (float) v.y).put(offset + 2, (float) v.z).put(offset + 3, w);
    }

    private static void upload() {
        if (lampTexture < 0) lampTexture = LightGL.floatTexture(4, ROWS);
        LightGL.upload(lampTexture, 4, ROWS, DATA);
    }

    private static void uniform(ShaderInstance shader, String name, Vec3 v) {
        shader.safeGetUniform(name).set((float) v.x, (float) v.y, (float) v.z);
    }

    private static void release() {
        LightGL.releaseTarget(scene);
        scene = null;
        LightGL.releaseTarget(shadowMaps);
        shadowMaps = null;
        LightGL.releaseTexture(lampTexture);
        lampTexture = -1;
    }
}
