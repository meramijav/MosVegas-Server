package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.sliminterface.IWrapperDamage;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WrapperEntity implements IWrapperEntity {
    public final Entity entity;
    private Vec3 lastPosition;
    private Point3D lastPoint;
    private AABB lastBounds;
    private BoundingBox lastBox;

    protected WrapperEntity(Entity entity) {
        this.entity = entity;
    }

    public static WrapperEntity of(Entity entity) {
        if (entity == null) return null;
        IWrapperHolder holder = (IWrapperHolder) entity;
        if (holder.slimvehicle$wrapper() instanceof WrapperEntity wrapper) return wrapper;
        WrapperEntity wrapper = entity instanceof Player player ? new WrapperPlayer(player) : new WrapperEntity(entity);
        holder.slimvehicle$setWrapper(wrapper);
        return wrapper;
    }

    public static Entity unwrap(IWrapperEntity wrapper) {
        return wrapper == null ? null : ((WrapperEntity) wrapper).entity;
    }

    public static Entity unwrap(AEntityA_Base core) {
        return unwrap(core.host);
    }

    private static AEntityA_Base core(Entity entity) {
        return entity instanceof BuilderVehicle builder ? builder.vehicle() : null;
    }

    @Override
    public int id() {
        return entity.getId();
    }

    @Override
    public UUID uuid() {
        return entity.getUUID();
    }

    @Override
    public IWrapperWorld world() {
        return WrapperWorld.of(entity.level());
    }

    @Override
    public Point3D position() {
        Vec3 position = entity.position();
        if (position != lastPosition) {
            lastPoint = Convert.point(position);
            lastPosition = position;
        }
        return lastPoint;
    }

    @Override
    public Point3D position(float partial) {
        return Convert.point(entity.getPosition(partial));
    }

    @Override
    public double x() {
        return entity.getX();
    }

    @Override
    public double y() {
        return entity.getY();
    }

    @Override
    public double z() {
        return entity.getZ();
    }

    @Override
    public void setPosition(double x, double y, double z) {
        entity.setPos(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        entity.moveTo(x, y, z, yaw, pitch);
    }

    @Override
    public void setOldPosAndRot() {
        entity.setOldPosAndRot();
    }

    @Override
    public float yaw() {
        return entity.getYRot();
    }

    @Override
    public float prevYaw() {
        return entity.yRotO;
    }

    @Override
    public void setYaw(float yaw) {
        entity.setYRot(yaw);
    }

    @Override
    public void setHeadYaw(float yaw) {
        entity.setYHeadRot(yaw);
    }

    @Override
    public void setBodyYaw(float yaw, float previous) {
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = previous;
        }
    }

    @Override
    public float pitch() {
        return entity.getXRot();
    }

    @Override
    public Point3D eyePosition() {
        return Convert.point(entity.getEyePosition());
    }

    @Override
    public Point3D lookAngle() {
        return Convert.point(entity.getLookAngle());
    }

    @Override
    public Point3D velocity() {
        return Convert.point(entity.getDeltaMovement());
    }

    @Override
    public void setVelocity(Point3D velocity) {
        entity.setDeltaMovement(velocity.x, velocity.y, velocity.z);
    }

    @Override
    public void markHurt() {
        entity.hurtMarked = true;
    }

    @Override
    public void markImpulse() {
        entity.hasImpulse = true;
    }

    @Override
    public void resetFallDistance() {
        entity.fallDistance = 0;
    }

    @Override
    public BoundingBox bounds() {
        AABB bounds = entity.getBoundingBox();
        if (bounds != lastBounds) {
            lastBox = Convert.box(bounds);
            lastBounds = bounds;
        }
        return lastBox;
    }

    @Override
    public void refreshBounds() {
        if (entity instanceof BuilderVehicle builder) builder.refreshBounds();
    }

    @Override
    public BoundingBox standingBounds(Point3D feet) {
        return Convert.box(entity.getDimensions(Pose.STANDING).makeBoundingBox(Convert.vec(feet)));
    }

    @Override
    public float width() {
        return entity.getBbWidth();
    }

    @Override
    public boolean onGround() {
        return entity.onGround();
    }

    @Override
    public void setOnGround(boolean onGround) {
        entity.setOnGround(onGround);
    }

    @Override
    public boolean inWater() {
        return entity.isInWater();
    }

    @Override
    public int tickCount() {
        return entity.tickCount;
    }

    @Override
    public boolean isLiving() {
        return entity instanceof LivingEntity;
    }

    @Override
    public boolean isSpectator() {
        return entity.isSpectator();
    }

    @Override
    public boolean noPhysics() {
        return entity.noPhysics;
    }

    @Override
    public boolean isAlive() {
        return entity.isAlive();
    }

    @Override
    public boolean isRemoved() {
        return entity.isRemoved();
    }

    @Override
    public void discard() {
        entity.discard();
    }

    @Override
    public double distanceToSqr(Point3D point) {
        return entity.distanceToSqr(point.x, point.y, point.z);
    }

    @Override
    public double distanceToSqr(IWrapperEntity other) {
        return entity.distanceToSqr(unwrap(other));
    }

    @Override
    public AEntityA_Base vehicle() {
        return core(entity.getVehicle());
    }

    @Override
    public AEntityA_Base rootVehicle() {
        return core(entity.getRootVehicle());
    }

    @Override
    public List<IWrapperEntity> passengers() {
        List<Entity> riders = entity.getPassengers();
        List<IWrapperEntity> passengers = new ArrayList<>(riders.size());
        for (Entity rider : riders) passengers.add(of(rider));
        return passengers;
    }

    @Override
    public boolean hasPassenger(IWrapperEntity passenger) {
        return entity.hasPassenger(unwrap(passenger));
    }

    @Override
    public boolean startRiding(AEntityA_Base vehicle) {
        return entity.startRiding(unwrap(vehicle), true);
    }

    @Override
    public void stopRiding() {
        entity.stopRiding();
    }

    @Override
    public void teleport(Point3D position) {
        if (entity instanceof ServerPlayer player) player.connection.teleport(position.x, position.y, position.z, player.getYRot(), player.getXRot());
        else entity.setPos(Convert.vec(position));
    }

    @Override
    public boolean hurt(IWrapperDamage damage, float amount) {
        return entity.hurt(((WrapperDamage) damage).source, amount);
    }

    @Override
    public void dropItem(IWrapperItemStack stack) {
        entity.spawnAtLocation(((WrapperItemStack) stack).stack);
    }

    @Override
    public IWrapperPlayer asPlayer() {
        return null;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof WrapperEntity other && other.entity == entity;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(entity);
    }

    @Override
    public String toString() {
        return "Wrapper[" + entity + "]";
    }
}
