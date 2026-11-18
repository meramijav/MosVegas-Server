package studio.merami.sliminterfacefabric.client.paint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class SprayStream {
    private static final ResourceLocation MIST = SlimVehicle.id("textures/effect/spray_mist.png");
    private static final ResourceLocation DROPLET = SlimVehicle.id("textures/effect/spray_drop.png");
    private static final ResourceLocation FAN = SlimVehicle.id("textures/effect/spray_fan.png");
    private static final ResourceLocation SPLAT = SlimVehicle.id("textures/effect/spray_splat.png");
    private static final Set<Kind> SOFT = EnumSet.of(Kind.CLOUD, Kind.PUFF);
    private static final Set<Kind> WET_SPOT = EnumSet.of(Kind.WET);
    private static final Set<Kind> SHARP = EnumSet.of(Kind.STREAM, Kind.SPLASH);
    private static final Vector4f TIP = new Vector4f(0, 2.375f / 16, -.125f / 16, 1);
    private static final int MAX_DROPS = 2400;
    private static final int STREAM_PER_TICK = 60;
    private static final int CLOUD_PER_TICK = 5;
    private static final int PUFF_PER_TICK = 2;
    private static final int MIST_VARIANTS = 4;
    private static final double SPEED = .8;
    private static final double STREAM_DRAG = .9;
    private static final double FAN_WIDE = .16;
    private static final double FAN_THIN = .03;
    private static final double RANGE = 4.4;
    private static final double MAX_TRAVEL = 5.5;
    private static final int NO_COLOUR = 0xdadde0;
    private static final int FAN_STEPS = 12;
    private static final long FAN_HOLD_MS = 120;
    private static final double FAN_RISE_MS = 40;
    private static final double FAN_FALL_MS = 90;
    private static final float FAN_NOZZLE = .012f;
    private static final float FAN_TILE = .55f;
    private static final float FAN_WHITE = .3f;

    private enum Kind { STREAM, SPLASH, CLOUD, PUFF, WET }

    private static final class Drop {
        Kind kind;
        double x, y, z, ox, oy, oz, vx, vy, vz;
        double travel, stop, drag;
        int age, life, color, variant;
        float shade, size, grow, alpha, spin;
        boolean lands;
        Vector3f faceA, faceB;
    }

    private record Capture(float x, float y, float depth, long frame) {}

    private static final List<Drop> DROPS = new ArrayList<>();
    private static Capture capture;
    private static long frame;
    private static Point3D tip;
    private static ClientLevel level;
    private static Point3D fanFrom;
    private static Point3D fanTo;
    private static boolean fanLands;
    private static int fanColor;
    private static float fanShade = 1;
    private static float fanPower;
    private static long fanSprayedAt = Long.MIN_VALUE;
    private static long fanFrameAt;

    private SprayStream() {}

    public static void capture(PoseStack pose, ItemDisplayContext mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mode.firstPerson()) {
            LivingEntity holder = GunHolder.get();
            if (holder != mc.player) return;
        }
        Matrix4f clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Vector4f at = clip.transform(new Vector4f(TIP));
        if (at.w <= .01f) return;
        capture = new Capture(at.x / at.w, at.y / at.w, at.w, frame);
    }

    public static void render(WorldRenderContext context) {
        frame++;
        Vec3 camera = context.camera().getPosition();
        Matrix4f projection = new Matrix4f(context.projectionMatrix());
        Matrix4f view = new Matrix4f(context.matrixStack().last().pose());
        Capture current = capture;
        tip = current != null && frame - current.frame <= 2 ? unproject(projection, view, camera, current.x, current.y, current.depth) : null;
        boolean fan = updateFan();
        if (DROPS.isEmpty() && !fan) return;
        float partial = context.tickDelta();
        List<Drop> sorted = new ArrayList<>(DROPS);
        sorted.sort(Comparator.comparingDouble((Drop drop) -> -distanceSqr(drop, camera)));
        Quaternionf facing = context.camera().rotation();
        Matrix4f matrix = context.matrixStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        batch(sorted, SOFT, MIST, matrix, camera, facing, partial);
        if (fan) drawFan(matrix, camera);
        batch(sorted, WET_SPOT, SPLAT, matrix, camera, facing, partial);
        batch(sorted, SHARP, DROPLET, matrix, camera, facing, partial);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static Point3D unproject(Matrix4f projection, Matrix4f view, Vec3 camera, float x, float y, float depth) {
        Matrix4f inverse = new Matrix4f(projection).invert();
        Vector4f near = inverse.transform(new Vector4f(x, y, -1, 1));
        Vector4f far = inverse.transform(new Vector4f(x, y, 1, 1));
        Vector3f a = new Vector3f(near.x / near.w, near.y / near.w, near.z / near.w);
        Vector3f b = new Vector3f(far.x / far.w, far.y / far.w, far.z / far.w);
        float t = Math.abs(b.z - a.z) < 1e-6f ? 0 : (-depth - a.z) / (b.z - a.z);
        Vector3f local = a.lerp(b, t);
        Vector3f world = new Matrix4f(view).invert().transformPosition(local);
        return new Point3D(world.x + camera.x, world.y + camera.y, world.z + camera.z);
    }

    public static void spray(ClientLevel world, Point3D eye, Point3D look, Point3D hit, int rgb) {
        if (level != world) {
            DROPS.clear();
            level = world;
        }
        Point3D from = tip != null ? tip : eye.add(look.scale(.55)).add(0, -.22, 0);
        Point3D target = hit != null ? hit : eye.add(look.scale(RANGE));
        Point3D direction = target.subtract(from).normalize();
        double stop = hit != null ? Math.min(target.subtract(from).length(), MAX_TRAVEL) : Double.MAX_VALUE;
        int color = rgb < 0 ? NO_COLOUR : rgb & 0xffffff;
        RandomSource random = world.random;
        Point3D side = direction.cross(new Point3D(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Point3D(1, 0, 0);
        side = side.normalize();
        Point3D up = side.cross(direction).normalize();
        float shade = brightness(world, from);
        double span = hit != null ? stop : RANGE;
        fanFrom = from;
        fanTo = from.add(direction.scale(span));
        fanLands = hit != null;
        fanColor = color;
        fanShade = shade;
        fanSprayedAt = Util.getMillis();
        for (int i = 0; i < STREAM_PER_TICK && DROPS.size() < MAX_DROPS; i++) {
            double fan = (random.nextDouble() * 2 - 1) * FAN_WIDE;
            double thin = random.nextGaussian() * FAN_THIN;
            Point3D spread = direction.add(up.scale(fan)).add(side.scale(thin)).normalize();
            double speed = SPEED * (.82 + random.nextDouble() * .3);
            double lead = random.nextDouble() * speed;
            Drop drop = drop(Kind.STREAM, from.add(spread.scale(lead)), 16, shade);
            drop.vx = spread.x * speed;
            drop.vy = spread.y * speed;
            drop.vz = spread.z * speed;
            drop.travel = lead;
            drop.stop = stop;
            drop.lands = hit != null;
            drop.drag = STREAM_DRAG;
            drop.size = .004f + random.nextFloat() * .007f;
            drop.grow = .0012f;
            drop.alpha = .7f;
            drop.color = shade(color, random.nextDouble() * .1, random);
            drop.spin = random.nextFloat() * 6.28f;
            DROPS.add(drop);
        }
        for (int i = 0; i < PUFF_PER_TICK && DROPS.size() < MAX_DROPS; i++) {
            Drop puff = drop(Kind.PUFF, from.add(direction.scale(.05 + random.nextDouble() * .06)), 6 + random.nextInt(4), shade);
            Point3D drift = direction.scale(.05).add(side.scale((random.nextDouble() - .5) * .02)).add(up.scale((random.nextDouble() - .5) * .02));
            puff.vx = drift.x;
            puff.vy = drift.y;
            puff.vz = drift.z;
            puff.drag = .86;
            puff.size = .04f;
            puff.grow = .03f;
            puff.alpha = .32f;
            puff.color = shade(color, .25, random);
            soften(puff, random);
            DROPS.add(puff);
        }
        if (DROPS.size() < MAX_DROPS) {
            double along = random.nextDouble() * Math.min(span, MAX_TRAVEL);
            double reach = along * .1;
            Drop halo = drop(Kind.CLOUD, from.add(direction.scale(along)).add(up.scale(random.nextGaussian() * reach)).add(side.scale(random.nextGaussian() * reach)), 18 + random.nextInt(13), shade);
            halo.vy = -.002;
            halo.drag = .92;
            halo.size = .11f;
            halo.grow = .008f;
            halo.alpha = .15f;
            halo.color = shade(color, .08, random);
            soften(halo, random);
            DROPS.add(halo);
        }
        if (hit != null && stop < MAX_TRAVEL) {
            Point3D landing = from.add(direction.scale(stop - .03));
            float landed = brightness(world, landing);
            for (int i = 0; i < CLOUD_PER_TICK && DROPS.size() < MAX_DROPS; i++) {
                Drop cloud = drop(Kind.CLOUD, landing.add(direction.scale(-.05 - random.nextDouble() * .12)), 50 + random.nextInt(41), landed);
                double angle = random.nextDouble() * Math.PI * 2;
                Point3D drift = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(.01 + random.nextDouble() * .016).add(direction.scale(-.012));
                cloud.vx = drift.x;
                cloud.vy = drift.y - .004;
                cloud.vz = drift.z;
                cloud.drag = .95;
                cloud.size = .14f;
                cloud.grow = .01f;
                cloud.alpha = .2f;
                cloud.color = shade(color, random.nextDouble() * .1, random);
                soften(cloud, random);
                DROPS.add(cloud);
            }
            if (DROPS.size() < MAX_DROPS) {
                Drop wet = drop(Kind.WET, landing.subtract(direction.scale(.01)), 4, landed);
                float angle = random.nextFloat() * 6.28f;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                wet.faceA = new Vector3f((float) (side.x * cos + up.x * sin), (float) (side.y * cos + up.y * sin), (float) (side.z * cos + up.z * sin));
                wet.faceB = new Vector3f((float) (up.x * cos - side.x * sin), (float) (up.y * cos - side.y * sin), (float) (up.z * cos - side.z * sin));
                wet.drag = 1;
                wet.size = .17f + random.nextFloat() * .08f;
                wet.alpha = .6f;
                wet.color = shade(color, .06, random);
                DROPS.add(wet);
            }
        }
    }

    private static Drop drop(Kind kind, Point3D at, int life, float shade) {
        Drop drop = new Drop();
        drop.kind = kind;
        drop.x = drop.ox = at.x;
        drop.y = drop.oy = at.y;
        drop.z = drop.oz = at.z;
        drop.life = life;
        drop.shade = shade;
        drop.stop = Double.MAX_VALUE;
        return drop;
    }

    private static void soften(Drop drop, RandomSource random) {
        drop.spin = random.nextFloat() * 6.28f;
        drop.variant = random.nextInt(MIST_VARIANTS);
    }

    private static float brightness(ClientLevel world, Point3D at) {
        int light = LevelRenderer.getLightColor(world, BlockPos.containing(at.x, at.y, at.z));
        float value = Math.max(LightTexture.block(light), LightTexture.sky(light) * world.getSkyDarken(1)) / 15f;
        return .22f + .78f * value;
    }

    public static void tick(ClientLevel world) {
        if (world != level) {
            DROPS.clear();
            level = world;
            return;
        }
        RandomSource random = world.random;
        List<Drop> splashes = new ArrayList<>();
        DROPS.removeIf(drop -> {
            drop.ox = drop.x;
            drop.oy = drop.y;
            drop.oz = drop.z;
            if (++drop.age >= drop.life) return true;
            double step = Math.sqrt(drop.vx * drop.vx + drop.vy * drop.vy + drop.vz * drop.vz);
            if (drop.kind == Kind.STREAM && drop.lands && drop.travel + step >= drop.stop) {
                double share = step <= 0 ? 0 : (drop.stop - drop.travel) / step;
                double x = drop.x + drop.vx * share;
                double y = drop.y + drop.vy * share;
                double z = drop.z + drop.vz * share;
                if (random.nextFloat() < .25f) splashes.add(splash(drop, x, y, z, random));
                return true;
            }
            drop.x += drop.vx;
            drop.y += drop.vy;
            drop.z += drop.vz;
            drop.travel += step;
            drop.vx *= drop.drag;
            drop.vy = drop.vy * drop.drag - gravity(drop.kind);
            drop.vz *= drop.drag;
            drop.size += drop.grow;
            return drop.kind == Kind.STREAM && drop.travel > MAX_TRAVEL;
        });
        for (Drop splash : splashes) if (DROPS.size() < MAX_DROPS) DROPS.add(splash);
    }

    private static double gravity(Kind kind) {
        return switch (kind) {
            case STREAM -> .004;
            case SPLASH -> .012;
            case CLOUD -> .0004;
            case PUFF, WET -> 0;
        };
    }

    private static Drop splash(Drop from, double x, double y, double z, RandomSource random) {
        Point3D along = new Point3D(from.vx, from.vy, from.vz).normalize();
        Point3D side = along.cross(new Point3D(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Point3D(1, 0, 0);
        side = side.normalize();
        Point3D up = side.cross(along).normalize();
        double angle = random.nextDouble() * Math.PI * 2;
        Point3D out = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(.02 + random.nextDouble() * .03).add(along.scale(-.012));
        Drop drop = drop(Kind.SPLASH, new Point3D(x, y, z).subtract(along.scale(.02)), 12 + random.nextInt(8), from.shade);
        drop.vx = out.x;
        drop.vy = out.y;
        drop.vz = out.z;
        drop.drag = .92;
        drop.size = .006f + random.nextFloat() * .008f;
        drop.alpha = .8f;
        drop.color = from.color;
        drop.spin = random.nextFloat() * 6.28f;
        return drop;
    }

    public static void clear() {
        DROPS.clear();
        capture = null;
        tip = null;
        level = null;
        fanFrom = null;
        fanTo = null;
        fanPower = 0;
    }

    private static double distanceSqr(Drop drop, Vec3 camera) {
        double dx = drop.x - camera.x;
        double dy = drop.y - camera.y;
        double dz = drop.z - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int shade(int rgb, double white, RandomSource random) {
        double tint = 1 + (random.nextDouble() - .5) * .12;
        int r = channel((rgb >> 16 & 255) * tint, white);
        int g = channel((rgb >> 8 & 255) * tint, white);
        int b = channel((rgb & 255) * tint, white);
        return r << 16 | g << 8 | b;
    }

    private static int channel(double value, double white) {
        return (int) Math.max(0, Math.min(255, value + (255 - value) * white));
    }

    private static void batch(List<Drop> drops, Set<Kind> kinds, ResourceLocation texture, Matrix4f matrix, Vec3 camera, Quaternionf facing, float partial) {
        BufferBuilder builder = null;
        for (Drop drop : drops) {
            if (!kinds.contains(drop.kind)) continue;
            if (builder == null) {
                builder = Tesselator.getInstance().getBuilder();
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            draw(builder, matrix, camera, facing, drop, partial);
        }
        if (builder == null) return;
        RenderSystem.setShaderTexture(0, texture);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void draw(BufferBuilder builder, Matrix4f matrix, Vec3 camera, Quaternionf facing, Drop drop, float partial) {
        float progress = (drop.age + partial) / drop.life;
        float alpha = Math.max(0, drop.alpha * fade(drop, progress));
        float x = (float) (drop.ox + (drop.x - drop.ox) * partial - camera.x);
        float y = (float) (drop.oy + (drop.y - drop.oy) * partial - camera.y);
        float z = (float) (drop.oz + (drop.z - drop.oz) * partial - camera.z);
        float size = drop.size + drop.grow * partial;
        Vector3f a;
        Vector3f b;
        if (drop.kind == Kind.STREAM) {
            Vector3f velocity = new Vector3f((float) drop.vx, (float) drop.vy, (float) drop.vz);
            float speed = velocity.length();
            Vector3f toCamera = new Vector3f(-x, -y, -z);
            Vector3f along = speed < 1e-4f ? facing.transform(new Vector3f(0, 1, 0)) : velocity.normalize(new Vector3f());
            Vector3f across = along.cross(toCamera, new Vector3f());
            if (across.lengthSquared() < 1e-8f) across = facing.transform(new Vector3f(1, 0, 0));
            across.normalize(size);
            a = along.mul(size + speed * .05f);
            b = across;
        } else if (drop.kind == Kind.WET) {
            a = new Vector3f(drop.faceA).mul(size);
            b = new Vector3f(drop.faceB).mul(size);
        } else {
            float cos = (float) Math.cos(drop.spin);
            float sin = (float) Math.sin(drop.spin);
            a = facing.transform(new Vector3f(cos * size, sin * size, 0));
            b = facing.transform(new Vector3f(-sin * size, cos * size, 0));
        }
        float u0 = SOFT.contains(drop.kind) ? drop.variant / (float) MIST_VARIANTS : 0;
        float u1 = SOFT.contains(drop.kind) ? (drop.variant + 1) / (float) MIST_VARIANTS : 1;
        float red = (drop.color >> 16 & 255) / 255f * drop.shade;
        float green = (drop.color >> 8 & 255) / 255f * drop.shade;
        float blue = (drop.color & 255) / 255f * drop.shade;
        builder.vertex(matrix, x - a.x - b.x, y - a.y - b.y, z - a.z - b.z).uv(u0, 1).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, x - a.x + b.x, y - a.y + b.y, z - a.z + b.z).uv(u1, 1).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, x + a.x + b.x, y + a.y + b.y, z + a.z + b.z).uv(u1, 0).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, x + a.x - b.x, y + a.y - b.y, z + a.z - b.z).uv(u0, 0).color(red, green, blue, alpha).endVertex();
    }

    private static float fade(Drop drop, float progress) {
        return switch (drop.kind) {
            case STREAM -> {
                float in = Math.min(1, (float) drop.travel / .12f);
                float out = drop.lands ? 1 : (float) Math.max(0, Math.min(1, (RANGE + .6 - drop.travel) / 1.6));
                yield Math.max(.25f, in) * out * (1 - progress * progress);
            }
            case SPLASH -> 1 - progress;
            case CLOUD -> Math.min(1, progress * 5) * (float) Math.pow(1 - progress, 1.5);
            case PUFF -> (1 - progress) * (1 - progress);
            case WET -> 1 - progress * .5f;
        };
    }

    private static boolean updateFan() {
        long now = Util.getMillis();
        float target = now - fanSprayedAt < FAN_HOLD_MS ? 1 : 0;
        float step = 1 - (float) Math.exp(-(now - fanFrameAt) / (target > fanPower ? FAN_RISE_MS : FAN_FALL_MS));
        fanFrameAt = now;
        fanPower += (target - fanPower) * step;
        return fanPower > .01f && fanFrom != null;
    }

    private static void drawFan(Matrix4f matrix, Vec3 camera) {
        Point3D from = tip != null ? tip : fanFrom;
        Vector3f start = new Vector3f((float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z));
        Vector3f axis = new Vector3f((float) (fanTo.x - camera.x), (float) (fanTo.y - camera.y), (float) (fanTo.z - camera.z)).sub(start);
        float length = axis.length();
        if (length < .05f) return;
        axis.div(length);
        float time = Util.getMillis() / 1000f;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        fanLayer(builder, matrix, start, axis, length, (float) FAN_WIDE, .42f, time * 2.6f, 0);
        fanLayer(builder, matrix, start, axis, length, (float) FAN_WIDE * .46f, .7f, time * 4.1f, .37f);
        RenderSystem.setShaderTexture(0, FAN);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void fanLayer(BufferBuilder builder, Matrix4f matrix, Vector3f start, Vector3f axis, float length, float spread, float strength, float scroll, float offset) {
        for (int i = 0; i < FAN_STEPS; i++) {
            float t0 = i / (float) FAN_STEPS;
            float t1 = (i + 1) / (float) FAN_STEPS;
            Vector3f p0 = new Vector3f(axis).mul(t0 * length).add(start);
            Vector3f p1 = new Vector3f(axis).mul(t1 * length).add(start);
            Vector3f w0 = fanAcross(axis, p0, FAN_NOZZLE + spread * t0 * length);
            Vector3f w1 = fanAcross(axis, p1, FAN_NOZZLE + spread * t1 * length);
            float a0 = fanAlpha(t0) * strength;
            float a1 = fanAlpha(t1) * strength;
            float v0 = t0 * length * FAN_TILE - scroll;
            float v1 = t1 * length * FAN_TILE - scroll;
            fanQuad(builder, matrix, p0, w0, p1, w1, -1, 0, a0, a1, offset, v0, v1);
            fanQuad(builder, matrix, p0, w0, p1, w1, 0, 1, a0, a1, offset, v0, v1);
        }
    }

    private static Vector3f fanAcross(Vector3f axis, Vector3f at, float width) {
        Vector3f across = axis.cross(-at.x, -at.y, -at.z, new Vector3f());
        if (across.lengthSquared() < 1e-8f) across.set(1, 0, 0);
        return across.normalize(width);
    }

    private static float fanAlpha(float t) {
        float in = Math.min(1, t * 12);
        float out = fanLands ? 1 - .45f * t : (float) Math.pow(1 - t, 1.2);
        return fanPower * in * out;
    }

    private static float mist(int channel) {
        return (channel / 255f + (1 - channel / 255f) * FAN_WHITE) * fanShade;
    }

    private static void fanQuad(BufferBuilder builder, Matrix4f matrix, Vector3f p0, Vector3f w0, Vector3f p1, Vector3f w1, int left, int right, float a0, float a1, float offset, float v0, float v1) {
        float red = mist(fanColor >> 16 & 255);
        float green = mist(fanColor >> 8 & 255);
        float blue = mist(fanColor & 255);
        float uLeft = offset + (left + 1) * .5f;
        float uRight = offset + (right + 1) * .5f;
        builder.vertex(matrix, p0.x + w0.x * left, p0.y + w0.y * left, p0.z + w0.z * left).uv(uLeft, v0).color(red, green, blue, left == 0 ? a0 : 0).endVertex();
        builder.vertex(matrix, p0.x + w0.x * right, p0.y + w0.y * right, p0.z + w0.z * right).uv(uRight, v0).color(red, green, blue, right == 0 ? a0 : 0).endVertex();
        builder.vertex(matrix, p1.x + w1.x * right, p1.y + w1.y * right, p1.z + w1.z * right).uv(uRight, v1).color(red, green, blue, right == 0 ? a1 : 0).endVertex();
        builder.vertex(matrix, p1.x + w1.x * left, p1.y + w1.y * left, p1.z + w1.z * left).uv(uLeft, v1).color(red, green, blue, left == 0 ? a1 : 0).endVertex();
    }
}
