package studio.merami.sliminterfacefabric.client.paint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.paint.PaintAim;
import studio.merami.slimcore.paint.PaintJob;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.PacketPaintSpray;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;

import java.util.Optional;

public final class PaintSprayClient {
    private static final int SEND_TICKS = 4;
    private static final double SEARCH = 6;
    private static final int HISS_TICKS = 5;
    private static int ticks;
    private static int hiss;
    private static boolean spraying;
    private static PaintAim.Aim aim;
    private static int colour = -1;

    private PaintSprayClient() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PaintSprayClient::tick);
    }

    public static PaintAim.Aim aim() {
        return aim;
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        aim = null;
        if (mc.level != null) SprayStream.tick(mc.level);
        if (player == null || mc.level == null || mc.screen != null || !ClientPlayNetworking.canSend(PacketPaintSpray.TYPE) || !player.getMainHandItem().is(ItemRegistry.item(SlimItems.SPRAY_GUN))) {
            release();
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Point3D from = new Point3D(eye.x, eye.y, eye.z);
        Point3D direction = new Point3D(look.x, look.y, look.z);
        EntityVehicleF_Physics target = null;
        PaintAim.Aim hit = null;
        for (BuilderVehicle entity : mc.level.getEntitiesOfClass(BuilderVehicle.class, player.getBoundingBox().inflate(SEARCH))) {
            EntityVehicleF_Physics vehicle = entity.vehicle();
            if (vehicle == null || !vehicle.painting()) continue;
            Optional<PaintAim.Aim> found = PaintAim.find(vehicle, vehicle.paintCoats(), from, direction);
            if (found.isPresent() && (hit == null || found.get().distance() < hit.distance())) {
                target = vehicle;
                hit = found.get();
            }
        }
        aim = hit;
        if (!mc.options.keyAttack.isDown()) {
            release();
            return;
        }
        if (!spraying) {
            spraying = true;
            sound(mc, player, SoundEvents.TRIPWIRE_CLICK_ON, .3f, 1.6f);
        }
        if (hiss++ % HISS_TICKS == 0) sound(mc, player, SoundEvents.FIRE_EXTINGUISH, .12f, 1.9f);
        if (target != null) colour = hit.area() == PaintJob.Area.INTERIOR ? target.paintInterior() : target.paintBody();
        if (target != null && hit.inReach()) PaintReveal.aim(target, hit.area(), hit.point());
        SprayStream.spray(mc.level, from, direction, target == null ? null : hit.point(), colour);
        if (target != null && ticks++ % SEND_TICKS == 0) ClientPlayNetworking.send(new PacketPaintSpray(target.id()));
    }

    private static void release() {
        ticks = 0;
        hiss = 0;
        spraying = false;
    }

    private static void sound(Minecraft mc, LocalPlayer player, SoundEvent sound, float volume, float pitch) {
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
