package studio.merami.sliminterfacefabric.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.sliminterfacefabric.compat.TaczCompat;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;

import java.util.Optional;

public final class InteractionOverlay {
    public static EntityVehicleF_Physics target;
    public static InteractionZone.Hit hit;

    private InteractionOverlay() {}

    public static void tick(Minecraft mc) {
        target = null;
        hit = null;
        if (mc.player == null || mc.level == null || mc.screen != null || TaczCompat.holdsGun(mc.player)) return;
        EntityVehicleF_Physics ridden = BuilderVehicle.ridden(mc.player);
        if (ridden != null) {
            Vec3 from = mc.player.getEyePosition();
            Vec3 to = from.add(mc.player.getLookAngle().scale(3));
            to = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getLocation();
            Optional<InteractionZone.Hit> found = ridden.seatedTarget(ridden.seatOf(WrapperEntity.of(mc.player)), Convert.point(from), Convert.point(mc.player.getLookAngle()), Convert.point(to));
            if (found.isPresent()) {
                target = ridden;
                hit = found.get();
            }
            return;
        }
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = from.add(mc.player.getLookAngle().scale(5));
        to = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getLocation();
        double nearest = 5;
        Point3D start = Convert.point(from);
        Point3D end = Convert.point(to);
        for (BuilderVehicle builder : mc.level.getEntitiesOfClass(BuilderVehicle.class, new AABB(from, to).inflate(1))) {
            EntityVehicleF_Physics vehicle = builder.vehicle();
            Optional<InteractionZone.Hit> found = vehicle.targetAlong(start, end);
            if (found.isPresent() && found.get().distance() < nearest) {
                hit = found.get();
                target = vehicle;
                nearest = hit.distance();
            }
        }
    }

    public static void draw(PoseStack pose, VertexConsumer buffer, Hull.Box box, Point3D origin, int color, float alpha) {
        float r = ((color >> 16) & 255) / 255f;
        float g = ((color >> 8) & 255) / 255f;
        float b = (color & 255) / 255f;
        Point3D[] corners = new Point3D[8];
        for (int i = 0; i < 8; i++) corners[i] = box.corner(i).subtract(origin);
        for (int i = 0; i < 8; i++) {
            for (int axis = 0; axis < 3; axis++) {
                int j = i ^ (1 << axis);
                if (j < i) continue;
                Point3D a = corners[i];
                Point3D end = corners[j];
                Point3D normal = end.subtract(a).normalize();
                buffer.vertex(pose.last().pose(), (float) a.x, (float) a.y, (float) a.z).color(r, g, b, alpha)
                        .normal(pose.last().normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
                buffer.vertex(pose.last().pose(), (float) end.x, (float) end.y, (float) end.z).color(r, g, b, alpha)
                        .normal(pose.last().normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
            }
        }
    }
}
