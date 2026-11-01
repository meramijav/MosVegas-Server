package ru.mosvegas.casino.seat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Invisible stool a seated player rides. Owned by a table; disappears when empty or unclaimed. */
public final class SeatEntity extends Entity {
    /** Player feet relative to the floor: hips end up on the stool cushion. */
    public static final double PASSENGER_Y = -0.27;

    private BlockPos tablePos;
    private int box = -1;
    private Vec3d dismountTo;
    private long claimedAt = Long.MIN_VALUE;

    public SeatEntity(EntityType<? extends SeatEntity> type, World world) {
        super(type, world);
        noClip = true;
        setNoGravity(true);
        setInvisible(true);
    }

    public void setup(BlockPos table, int box, Vec3d dismountTo) {
        this.tablePos = table;
        this.box = box;
        this.dismountTo = dismountTo;
    }

    public void claim(long worldTime) { claimedAt = worldTime; }
    public BlockPos tablePos() { return tablePos; }
    public int box() { return box; }

    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if ((!hasPassengers() && age > 10) || getWorld().getTime() - claimedAt > 40) discard();
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, getX(), getY() + PASSENGER_Y, getZ());
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return dismountTo != null ? dismountTo : super.updatePassengerForDismount(passenger);
    }

    @Override public boolean isCollidable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldSave() { return false; }
}
