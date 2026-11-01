package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.effects.VehicleEffects;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VehicleShadow {
    private static final ResourceLocation TEXTURE = SlimVehicle.id("textures/effect/vehicle_shadow.png");
    private static final Map<Integer, Ground> CACHE = new HashMap<>();
    private static List<BuilderVehicle> vehicles = List.of();
    private static Object world;
    private static int tick;

    private record Ground(Vec3 position, List<AABB> tops, int tick) {}

    private VehicleShadow() {}

    public static void tick(Minecraft mc) {
        if (world != mc.level) {
            world = mc.level;
            CACHE.clear();
            vehicles = List.of();
        }
        if (mc.level == null || mc.player == null) return;
        tick++;
        List<BuilderVehicle> nearby = mc.level.getEntitiesOfClass(BuilderVehicle.class, mc.player.getBoundingBox().inflate(40));
        nearby.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
        vehicles = List.copyOf(nearby.subList(0, Math.min(12, nearby.size())));
        Set<Integer> active = new HashSet<>();
        for (BuilderVehicle vehicle : vehicles) {
            active.add(vehicle.getId());
            Ground old = CACHE.get(vehicle.getId());
            if (old != null && tick - old.tick() < 5 && old.position().distanceToSqr(vehicle.position()) < .04) continue;
            JSONVehicle definition = vehicle.vehicle().definition();
            double radius = Math.hypot(definition.shadowHalfWidth, definition.shadowHalfLength) * definition.scale + .4;
            Vec3 p = vehicle.position();
            List<AABB> tops = new ArrayList<>();
            for (int x = Mth.floor(p.x - radius); x <= Mth.floor(p.x + radius); x++) {
                for (int z = Mth.floor(p.z - radius); z <= Mth.floor(p.z + radius); z++) {
                    for (int y = Mth.floor(p.y + .15); y >= Mth.floor(p.y - 1.8); y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = mc.level.getBlockState(pos);
                        VoxelShape shape = state.getCollisionShape(mc.level, pos);
                        if (shape.isEmpty()) continue;
                        for (AABB box : shape.toAabbs()) if (y + box.maxY <= p.y + .18) tops.add(box.move(x, y, z));
                        break;
                    }
                }
            }
            CACHE.put(vehicle.getId(), new Ground(p, List.copyOf(tops), tick));
        }
        CACHE.keySet().retainAll(active);
    }

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.entityShadows().get() || vehicles.isEmpty()) return;
        Vec3 camera = context.camera().getPosition();
        PoseStack pose = context.matrixStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = pose.last().pose();
        for (BuilderVehicle vehicle : vehicles) {
            Ground ground = CACHE.get(vehicle.getId());
            if (ground == null || !vehicle.isAlive()) continue;
            Vec3 p = vehicle.getPosition(context.tickDelta());
            double yaw = Math.toRadians(Mth.rotLerp(context.tickDelta(), vehicle.yRotO, vehicle.getYRot()));
            JSONVehicle definition = vehicle.vehicle().definition();
            double width = definition.shadowHalfWidth * definition.scale;
            double length = definition.shadowHalfLength * definition.scale;
            for (AABB top : ground.tops()) {
                float alpha = (float) (.48 * Math.max(0, 1 - (p.y - top.maxY) / 1.8) * Math.max(0, 1 - p.distanceToSqr(camera) / 1600));
                if (alpha <= .005) continue;
                List<Vec3> polygon = new ArrayList<>(List.of(new Vec3(top.minX, top.maxY + .008, top.minZ), new Vec3(top.maxX, top.maxY + .008, top.minZ),
                        new Vec3(top.maxX, top.maxY + .008, top.maxZ), new Vec3(top.minX, top.maxY + .008, top.maxZ)));
                for (int edge = 0; edge < 4; edge++) polygon = clip(polygon, p, yaw, width, length, edge);
                for (int i = 1; i < polygon.size() - 1; i++) {
                    vertex(buffer, matrix, polygon.get(0), p, yaw, width, length, alpha);
                    vertex(buffer, matrix, polygon.get(i), p, yaw, width, length, alpha);
                    vertex(buffer, matrix, polygon.get(i + 1), p, yaw, width, length, alpha);
                    vertex(buffer, matrix, polygon.get(i + 1), p, yaw, width, length, alpha);
                }
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static double boundary(Vec3 v, Vec3 p, double yaw, double width, double length, int edge) {
        Vec3 local = v.subtract(p).yRot((float) yaw);
        return switch (edge) {
            case 0 -> width + local.x;
            case 1 -> width - local.x;
            case 2 -> length + local.z;
            default -> length - local.z;
        };
    }

    private static List<Vec3> clip(List<Vec3> polygon, Vec3 p, double yaw, double width, double length, int edge) {
        if (polygon.isEmpty()) return polygon;
        List<Vec3> out = new ArrayList<>();
        Vec3 previous = polygon.get(polygon.size() - 1);
        double a = boundary(previous, p, yaw, width, length, edge);
        for (Vec3 current : polygon) {
            double c = boundary(current, p, yaw, width, length, edge);
            if ((a >= 0) != (c >= 0)) out.add(previous.lerp(current, a / (a - c)));
            if (c >= 0) out.add(current);
            previous = current;
            a = c;
        }
        return out;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 v, Vec3 p, double yaw, double width, double length, float alpha) {
        Vec3 local = v.subtract(p).yRot((float) yaw);
        VehicleEffects.vertex(buffer, matrix, v, (float) (local.x / (2 * width) + .5), (float) (local.z / (2 * length) + .5), 0, alpha);
    }
}
