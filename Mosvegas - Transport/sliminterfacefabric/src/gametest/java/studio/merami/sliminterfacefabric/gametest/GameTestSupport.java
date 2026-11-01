package studio.merami.sliminterfacefabric.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.systems.VehicleSpawner;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.registry.EntityRegistry;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.util.List;
import java.util.Optional;

final class GameTestSupport {
    private GameTestSupport() {}

    static JSONVehicle vehicle(String name) {
        return PackParser.vehicle(name);
    }

    static Point3D p(Vec3 vec) {
        return Convert.point(vec);
    }

    static Vec3 v(Point3D point) {
        return Convert.vec(point);
    }

    static AABB aabb(BoundingBox box) {
        return Convert.aabb(box);
    }

    static BoundingBox box(AABB box) {
        return Convert.box(box);
    }

    static Point3D abs(GameTestHelper h, double x, double y, double z) {
        return p(h.absoluteVec(new Vec3(x, y, z)));
    }

    static IWrapperPlayer w(Player player) {
        return WrapperPlayer.of(player);
    }

    static IWrapperEntity w(Entity entity) {
        return WrapperEntity.of(entity);
    }

    static void own(EntityVehicleF_Physics car, Player player) {
        car.setOwner(player.getUUID());
        VehicleSpawner.giveKey(w(player), car);
    }

    static BuilderVehicle entity(EntityVehicleF_Physics car) {
        return BuilderVehicle.of(car);
    }

    static void tick(EntityVehicleF_Physics car) {
        entity(car).tick();
    }

    static void step(EntityVehicleF_Physics car) {
        BuilderVehicle entity = entity(car);
        entity.tickCount++;
        entity.tick();
    }

    static void spawn(GameTestHelper h, EntityVehicleF_Physics car) {
        h.getLevel().addFreshEntity(entity(car));
    }

    static void ride(Entity rider, EntityVehicleF_Physics car) {
        rider.startRiding(entity(car), true);
    }

    static void place(Entity entity, Point3D at) {
        entity.setPos(at.x, at.y, at.z);
    }

    static EntityVehicleF_Physics create(GameTestHelper h) {
        return EntityRegistry.VEHICLE.create(h.getLevel()).vehicle();
    }

    static EntityVehicleF_Physics createAt(GameTestHelper h, Vec3 relative) {
        EntityVehicleF_Physics car = create(h);
        car.setPosition(p(h.absoluteVec(relative)));
        return car;
    }

    static void floor(GameTestHelper h, int x0, int x1, int z0, int z1) {
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                for (int y = 1; y < 4; y++) h.setBlock(x, y, z, Blocks.AIR);
            }
        }
    }

    static EntityVehicleF_Physics car(GameTestHelper h, double x, double z, float yaw) {
        return car(h, x, z, yaw, null);
    }

    static EntityVehicleF_Physics car(GameTestHelper h, double x, double z, float yaw, String definition) {
        EntityVehicleF_Physics car = create(h);
        if (definition != null) car.setDefinition(definition);
        Vec3 at = h.absoluteVec(new Vec3(x, 1, z));
        car.moveTo(at.x, at.y + .02, at.z, yaw, 0);
        car.settleOnGround(1);
        return car;
    }

    static Player drive(EntityVehicleF_Physics car, GameTestHelper h) {
        Player player = h.makeMockPlayer();
        place(player, car.position());
        own(car, player);
        ride(player, car);
        car.action(w(player), VehicleAction.ENGINE);
        car.shift(1, w(player));
        return player;
    }

    static void noseTo(EntityVehicleF_Physics car, double z, double gap) {
        double nose = car.hull().bounds().maxZ - car.z();
        car.setPosition(car.x(), car.y(), z - gap - nose);
    }

    static Point3D worldDirection(EntityVehicleF_Physics car, Point3D local) {
        return car.directionToWorld(local).normalize();
    }

    static void open(EntityVehicleF_Physics car, PartOpening part) {
        car.setOpen(part, true);
        part.progress = 1;
        car.refreshHull();
    }

    static void openDoor(EntityVehicleF_Physics car, int seat) {
        open(car, car.seat(seat).door());
    }

    static void lookAt(Player player, Point3D target) {
        Vec3 aim = v(target).subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))));
    }

    static Optional<InteractionZone.Hit> seatedTarget(EntityVehicleF_Physics car, Player player) {
        Point3D eye = p(player.getEyePosition());
        Point3D look = p(player.getLookAngle());
        return car.seatedTarget(car.seatOf(w(player)), eye, look, eye.add(look.scale(5)));
    }

    static void walk(EntityVehicleF_Physics car, Player player, Vec3 input) {
        player.setDeltaMovement(player.getDeltaMovement().add(input));
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.setDeltaMovement(player.getDeltaMovement().multiply(.546, 0, .546));
        car.pushOut(w(player));
    }

    static void stroll(List<EntityVehicleF_Physics> cars, Player player, Vec3 input) {
        player.setDeltaMovement(player.getDeltaMovement().add(input));
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.setDeltaMovement(player.getDeltaMovement().multiply(.546, 0, .546));
        for (EntityVehicleF_Physics car : cars) {
            if (car.pushOut(w(player))) break;
        }
    }

    static void fall(EntityVehicleF_Physics car, Player player, Vec3 input, boolean jump) {
        Vec3 push = player.getDeltaMovement().add(input);
        if (jump && player.onGround()) push = new Vec3(push.x, .42, push.z);
        player.setDeltaMovement(push);
        player.move(MoverType.SELF, player.getDeltaMovement());
        Vec3 after = player.getDeltaMovement();
        double drag = player.onGround() ? .546 : .91;
        player.setDeltaMovement(after.x * drag, player.onGround() ? 0 : (after.y - .08) * .98, after.z * drag);
        car.pushOut(w(player));
    }

    static boolean near(List<EntityVehicleF_Physics> cars, AABB box) {
        BoundingBox area = box(box);
        for (EntityVehicleF_Physics car : cars) {
            if (car.hull().intersects(area)) return true;
        }
        return false;
    }

    static double distance(Hull.Box box, Point3D point) {
        Point3D d = point.subtract(box.center);
        Point3D closest = box.center;
        double[] half = {box.half.x, box.half.y, box.half.z};
        for (int i = 0; i < 3; i++) closest = closest.add(box.axes[i].scale(Math.max(-half[i], Math.min(half[i], d.dot(box.axes[i])))));
        return point.distanceTo(closest);
    }

    static Hull.Box openDoorBox(EntityVehicleF_Physics car) {
        PartOpening door = car.opening("door_fl");
        return car.hull().boxes().stream().filter(b -> b.part == door && b.glass < 0).findFirst().orElseThrow();
    }

    static Hull.Box control(EntityVehicleF_Physics car, String opening) {
        PartOpening part = car.opening(opening);
        return car.hull().controls().stream().filter(b -> b.part == part).findFirst().orElseThrow();
    }

    static void assertWheelsAttached(GameTestHelper h, EntityVehicleF_Physics car, String where) {
        ChassisGeometry.Pose body = new ChassisGeometry.Pose(car.bodyPitch, car.bodyRoll, car.bodyHeave);
        for (int i = 0; i < car.wheels().size(); i++) {
            double arch = ChassisGeometry.bodyAtWheel(car.definition(), body, i);
            h.assertTrue(car.wheel(i).travel >= arch - .0401, "Wheel " + i + " hangs below the body: " + where + " gap=" + (arch - car.wheel(i).travel));
        }
    }

    static void assertBodyAboveGround(GameTestHelper h, EntityVehicleF_Physics car, String where) {
        for (double x : new double[]{-.9, .9}) {
            for (double z : new double[]{-2.9, -1.5, 0, 1.5, 2.9}) {
                Point3D under = car.chassisToWorld(new Point3D(x, .30, z));
                BlockHitResult hit = h.getLevel().clip(new ClipContext(v(under.add(0, 1.2, 0)), v(under.add(0, -1.5, 0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity(car)));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y < under.y + 1.15) {
                    h.assertTrue(under.y >= hit.getLocation().y - .02, "Body sinks into the ground: " + where + " at " + x + "," + z + " depth=" + (hit.getLocation().y - under.y));
                }
            }
        }
    }
}
