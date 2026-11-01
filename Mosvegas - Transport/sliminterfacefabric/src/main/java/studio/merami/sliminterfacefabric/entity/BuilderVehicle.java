package studio.merami.sliminterfacefabric.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.SyncedValue;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperDamage;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;
import studio.merami.sliminterfacefabric.wrapper.WrapperNBT;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class BuilderVehicle extends Entity {
    private static final List<SyncedValue<?>> VALUES = SyncedValue.of(EntityVehicleF_Physics.class);
    private static final EntityDataAccessor<?>[] BY_INDEX;
    private static final SyncedValue<?>[] BY_ID;
    private static final ThreadLocal<Boolean> IGNORE_VEHICLES = ThreadLocal.withInitial(() -> false);

    static {
        int indices = 0;
        for (SyncedValue<?> value : VALUES) indices = Math.max(indices, value.index + 1);
        BY_INDEX = new EntityDataAccessor<?>[indices];
        List<EntityDataAccessor<?>> accessors = new ArrayList<>();
        for (SyncedValue<?> value : VALUES) {
            EntityDataAccessor<?> accessor = SynchedEntityData.defineId(BuilderVehicle.class, serializer(value.kind));
            BY_INDEX[value.index] = accessor;
            accessors.add(accessor);
        }
        int ids = 0;
        for (EntityDataAccessor<?> accessor : accessors) ids = Math.max(ids, accessor.getId() + 1);
        BY_ID = new SyncedValue<?>[ids];
        for (int i = 0; i < VALUES.size(); i++) BY_ID[accessors.get(i).getId()] = VALUES.get(i);
    }

    private final IEntityData data = new IEntityData() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(SyncedValue<T> key) {
            Object value = entityData.get(BY_INDEX[key.index]);
            return (T) (key.kind == SyncedValue.Kind.NBT ? new WrapperNBT((CompoundTag) value) : value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void set(SyncedValue<T> key, T value) {
            Object raw = key.kind == SyncedValue.Kind.NBT ? ((WrapperNBT) value).tag : value;
            entityData.set((EntityDataAccessor<Object>) BY_INDEX[key.index], raw);
        }
    };
    private final EntityVehicleF_Physics vehicle;

    public BuilderVehicle(EntityType<? extends BuilderVehicle> type, Level level) {
        super(type, level);
        blocksBuilding = true;
        vehicle = new EntityVehicleF_Physics(WrapperEntity.of(this), data);
    }

    private static EntityDataSerializer<?> serializer(SyncedValue.Kind kind) {
        return switch (kind) {
            case INT -> EntityDataSerializers.INT;
            case FLOAT -> EntityDataSerializers.FLOAT;
            case STRING -> EntityDataSerializers.STRING;
            case NBT -> EntityDataSerializers.COMPOUND_TAG;
        };
    }

    @SuppressWarnings("unchecked")
    private static Object initial(SyncedValue<?> value) {
        return value.kind == SyncedValue.Kind.NBT ? new CompoundTag() : value.initial;
    }

    public static BuilderVehicle of(EntityVehicleF_Physics vehicle) {
        return (BuilderVehicle) WrapperEntity.unwrap(vehicle);
    }

    public static EntityVehicleF_Physics core(Entity entity) {
        return entity instanceof BuilderVehicle builder ? builder.vehicle : null;
    }

    public static EntityVehicleF_Physics ridden(Entity entity) {
        return core(entity.getVehicle());
    }

    public static boolean ignoringVehicles() {
        return IGNORE_VEHICLES.get();
    }

    public static boolean ignoringVehicles(BooleanSupplier check) {
        IGNORE_VEHICLES.set(true);
        try {
            return check.getAsBoolean();
        } finally {
            IGNORE_VEHICLES.set(false);
        }
    }

    public EntityVehicleF_Physics vehicle() {
        return vehicle;
    }

    private static IWrapperEntity wrap(Entity entity) {
        return WrapperEntity.of(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void defineSynchedData() {
        for (SyncedValue<?> value : VALUES) entityData.define((EntityDataAccessor<Object>) BY_INDEX[value.index], initial(value));
    }

    @Override
    public void tick() {
        super.tick();
        vehicle.tick();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        vehicle.load(new WrapperNBT(tag));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        vehicle.save(new WrapperNBT(tag));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (vehicle == null) return;
        int id = accessor.getId();
        if (id < BY_ID.length && BY_ID[id] != null) vehicle.onSyncedDataUpdated(BY_ID[id]);
    }

    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values) {
        super.onSyncedDataUpdated(values);
        if (vehicle == null) return;
        List<SyncedValue<?>> keys = new ArrayList<>(values.size());
        for (SynchedEntityData.DataValue<?> value : values) {
            if (value.id() < BY_ID.length && BY_ID[value.id()] != null) keys.add(BY_ID[value.id()]);
        }
        vehicle.onSyncedDataUpdated(keys);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        super.moveTo(x, y, z, yaw, pitch);
        if (vehicle != null) vehicle.onMovedTo(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        vehicle.lerpTo(x, y, z);
    }

    @Override
    public double lerpTargetX() {
        return vehicle.lerpTargetX();
    }

    @Override
    public double lerpTargetY() {
        return vehicle.lerpTargetY();
    }

    @Override
    public double lerpTargetZ() {
        return vehicle.lerpTargetZ();
    }

    @Override
    protected AABB makeBoundingBox() {
        if (vehicle == null || !vehicle.isConstructed()) return super.makeBoundingBox();
        return Convert.aabb(vehicle.makeBounds());
    }

    public void refreshBounds() {
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide) vehicle.interactPart(WrapperPlayer.of(player), false);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        return interact(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return vehicle.attack(new WrapperDamage(source), amount);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        IWrapperEntity driver = vehicle.controllingPassenger();
        return driver == null ? null : (LivingEntity) WrapperEntity.unwrap(driver);
    }

    @Override
    protected boolean canAddPassenger(Entity entity) {
        return vehicle.canAddRider(wrap(entity));
    }

    @Override
    protected void addPassenger(Entity entity) {
        vehicle.addRider(wrap(entity), () -> super.addPassenger(entity));
    }

    @Override
    protected void removePassenger(Entity entity) {
        vehicle.removeRider(wrap(entity), () -> super.removePassenger(entity));
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        IWrapperEntity rider = wrap(passenger);
        Point3D at = vehicle.riderPosition(rider);
        move.accept(passenger, at.x, at.y, at.z);
        vehicle.alignRider(rider);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return Convert.vec(vehicle.dismountLocation(wrap(passenger)));
    }
}
