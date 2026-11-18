package studio.merami.sliminterfacefabric.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.paint.PaintAim;
import studio.merami.slimcore.paint.PaintJob;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.item.BuilderItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PaintJobs {
    private static final String FILE = "paint_jobs.json";
    private static final int CHECK_TICKS = 20;
    private static final Map<UUID, PaintJob> JOBS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SPRAY = new HashMap<>();
    private static MinecraftServer owner;
    private static Path file;

    private PaintJobs() {}

    static void load(MinecraftServer server) {
        owner = server;
        file = ShopFiles.file(server, FILE);
        JOBS.clear();
        LAST_SPRAY.clear();
        if (!Files.exists(file)) return;
        try {
            for (PaintJob job : PaintJob.fromJson(Files.readString(file, StandardCharsets.UTF_8), System.currentTimeMillis())) JOBS.put(job.vehicle(), job);
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Unfinished paint jobs were not read from {}; those cars keep their old colour", file, e);
        }
    }

    static void clear() {
        JOBS.clear();
        LAST_SPRAY.clear();
        owner = null;
        file = null;
    }

    public static void loaded(EntityVehicleF_Physics vehicle) {
        PaintJob job = JOBS.get(vehicle.uuid());
        if (job != null) finish(vehicle, job, "the vehicle was loaded again");
    }

    static PaintJob of(UUID vehicle) {
        return JOBS.get(vehicle);
    }

    static PaintJob ownedBy(UUID player) {
        for (PaintJob job : JOBS.values()) {
            if (job.owner().equals(player)) return job;
        }
        return null;
    }

    static void start(ServerPlayer player, EntityVehicleF_Physics vehicle, PaintShop.Order order, long price) {
        PaintJob job = new PaintJob(player.getUUID(), player.getGameProfile().getName(), vehicle.uuid(), order, price, System.currentTimeMillis(),
                PaintJob.initialCoats(order, vehicle.definition().paintAreaBones.values()));
        JOBS.put(vehicle.uuid(), job);
        vehicle.startPainting(player.getUUID(), order.body(), order.interior(), job.coats());
        save();
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0 || JOBS.isEmpty()) return;
        for (PaintJob job : List.copyOf(JOBS.values())) {
            EntityVehicleF_Physics vehicle = find(server, job.vehicle());
            if (vehicle == null) continue;
            String reason = vehicle.wrecked() ? "the vehicle was wrecked"
                    : vehicle.flag(EntityVehicleF_Physics.ENGINE) ? "the owner drove off"
                    : server.getPlayerList().getPlayer(job.owner()) == null ? "the owner left the game"
                    : job.stale(System.currentTimeMillis()) ? "no progress" : null;
            if (reason != null) finish(vehicle, job, reason);
        }
    }

    private static EntityVehicleF_Physics find(MinecraftServer server, UUID vehicle) {
        for (ServerLevel level : server.getAllLevels()) {
            EntityVehicleF_Physics found = BuilderVehicle.core(level.getEntity(vehicle));
            if (found != null) return found;
        }
        return null;
    }

    static void spray(ServerPlayer player, EntityVehicleF_Physics vehicle) {
        PaintJob job = JOBS.get(vehicle.uuid());
        if (job == null || !job.owner().equals(player.getUUID()) || !gun(player.getMainHandItem())) return;
        long now = System.currentTimeMillis();
        Long last = LAST_SPRAY.get(player.getUUID());
        if (last != null && now - last < PaintJob.SPRAY_INTERVAL_MS) return;
        LAST_SPRAY.put(player.getUUID(), now);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Optional<PaintAim.Aim> aim = PaintAim.find(vehicle, job.coats(), new Point3D(eye.x, eye.y, eye.z), new Point3D(look.x, look.y, look.z));
        if (aim.isEmpty() || !aim.get().inReach()) return;
        PaintJob next = job.sprayed(aim.get().area(), now);
        if (next == job) return;
        JOBS.put(vehicle.uuid(), next);
        vehicle.setPaintCoats(next.coats());
        if (next.done()) finish(vehicle, next, null);
    }

    static boolean gun(ItemStack stack) {
        return stack.getItem() instanceof BuilderItem builder && builder.item == SlimItems.SPRAY_GUN;
    }

    private static void finish(EntityVehicleF_Physics vehicle, PaintJob job, String early) {
        if (!JOBS.remove(vehicle.uuid(), job)) return;
        PaintShop.apply(vehicle, job.order());
        vehicle.stopPainting();
        save();
        if (early == null) {
            SlimCore.LOGGER.info("{} finished painting vehicle {}", job.ownerName(), job.vehicle());
            return;
        }
        SlimCore.LOGGER.info("The paid order of {} was applied to vehicle {} before the painting was done: {}", job.ownerName(), job.vehicle(), early);
        ServerPlayer player = owner.getPlayerList().getPlayer(job.owner());
        if (player != null) player.sendSystemMessage(Component.translatable("message.slimvehicle.paint.applied"));
    }

    public static void vanished(UUID vehicle, Entity.RemovalReason reason) {
        if (file == null || !reason.shouldDestroy()) return;
        PaintJob job = JOBS.remove(vehicle);
        if (job == null) return;
        save();
        SlimCore.LOGGER.warn("The paid paint order of {} was dropped: vehicle {} was destroyed ({})", job.ownerName(), job.vehicle(), reason);
    }

    private static void save() {
        ShopFiles.write(file, PaintJob.toJson(JOBS.values()), "Paint jobs");
    }
}
