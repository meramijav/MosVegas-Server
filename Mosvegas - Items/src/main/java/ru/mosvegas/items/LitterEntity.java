package ru.mosvegas.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * An empty can thrown with the drop key or a cigarette butt flicked to the floor. Purely
 * decorative: it cannot be picked up, is not saved with the world and disappears after a minute.
 */
public final class LitterEntity extends Entity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(LitterEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    public static final int LIFETIME = 20 * 60;
    /** A dropped butt keeps glowing and smoking for a few seconds. */
    public static final int SMOULDER = 20 * 5;
    // Snappier than a dropped item: a can thrown at 45 degrees lands about a dozen blocks away.
    private static final double GRAVITY = 0.06, DRAG = 0.99, THROW_SPEED = 0.85;

    public LitterEntity(EntityType<? extends LitterEntity> type, World world) {
        super(type, world);
    }

    /** Thrown with the drop key: flies where the player looks, so it can go high or far. */
    public static void toss(ServerWorld world, LivingEntity user, ItemStack empties) {
        Vec3d look = user.getRotationVector();
        Vec3d carried = new Vec3d(user.getVelocity().x, user.isOnGround() ? 0 : user.getVelocity().y, user.getVelocity().z);
        for (int i = 0; i < empties.getCount(); i++) {
            // Several cans dropped at once scatter a little.
            Vec3d spread = i == 0 ? Vec3d.ZERO : new Vec3d(world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextGaussian()).multiply(0.06);
            Vec3d velocity = look.multiply(THROW_SPEED).add(0, 0.08, 0).add(carried).add(spread);
            spawn(world, user, Hand.MAIN_HAND, look.multiply(0.3).add(0, -0.25, 0), 0.15, velocity, empties);
        }
    }

    /** A finished cigarette is flicked down near the feet. */
    public static void flick(ServerWorld world, LivingEntity user, Hand hand, ItemStack butt) {
        Vec3d forward = forward(user);
        Vec3d velocity = forward.multiply(0.08).add(0, 0.05, 0).add(side(user, hand).multiply(0.03));
        spawn(world, user, hand, forward.multiply(0.3).add(0, -0.75, 0), 0.35, velocity, butt);
    }

    private static void spawn(ServerWorld world, LivingEntity user, Hand hand, Vec3d offset, double sideways, Vec3d velocity, ItemStack used) {
        Vec3d eye = user.getEyePos();
        Vec3d start = eye.add(offset).add(side(user, hand).multiply(sideways));
        // Never start inside a wall the player is facing.
        var hit = world.raycast(new RaycastContext(eye, start, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        if (hit.getType() != HitResult.Type.MISS) start = hit.getPos().add(eye.subtract(hit.getPos()).normalize().multiply(0.15));
        LitterEntity litter = new LitterEntity(MosVegasItems.LITTER, world);
        ItemStack visual = used.copyWithCount(1);
        MosVegasConsumableItem.markSpent(visual);
        litter.setStack(visual);
        litter.refreshPositionAndAngles(start.x, start.y, start.z, user.getYaw() + (world.random.nextFloat() - 0.5f) * 90, 0);
        litter.setVelocity(velocity);
        world.spawnEntity(litter);
    }

    private static Vec3d forward(LivingEntity user) {
        float yaw = user.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    private static Vec3d side(LivingEntity user, Hand hand) {
        Arm arm = hand == Hand.MAIN_HAND ? user.getMainArm() : user.getMainArm().getOpposite();
        float yaw = user.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw)).multiply(arm == Arm.RIGHT ? 1 : -1);
    }

    public ItemStack getStack() {
        return dataTracker.get(STACK);
    }

    private void setStack(ItemStack stack) {
        dataTracker.set(STACK, stack);
    }

    public boolean isButt() {
        return getStack().getItem() instanceof MosVegasConsumableItem item && item.definition().useAction().equals("smoke");
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(STACK, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        // Same simple ballistics on both sides; the tracker corrects small differences.
        Vec3d before = getVelocity().add(0, -GRAVITY, 0);
        setVelocity(before);
        move(MovementType.SELF, before);
        // Collisions zero the blocked components; bounce them back instead.
        Vec3d after = getVelocity();
        double x = after.x, y = after.y, z = after.z;
        boolean knock = false;
        if (before.x != 0 && after.x == 0) { x = -before.x * 0.35; knock = Math.abs(before.x) > 0.12; }
        if (before.z != 0 && after.z == 0) { z = -before.z * 0.35; knock |= Math.abs(before.z) > 0.12; }
        if (isOnGround()) {
            if (before.y < -0.15) {
                y = -before.y * 0.3;
                knock = true;
            }
            // A can rolls a little further than a butt.
            double friction = isButt() ? 0.5 : 0.7;
            x *= friction;
            z *= friction;
        }
        if (knock && !getWorld().isClient && !isButt()) playSound(SoundEvents.BLOCK_CHAIN_HIT, 0.35f, 1.5f + random.nextFloat() * 0.3f);
        setVelocity(new Vec3d(x, y, z).multiply(DRAG));
        if (!getWorld().isClient && age >= LIFETIME) discard();
    }

    @Override
    public boolean shouldRender(double distance) {
        double range = 48 * getRenderDistanceMultiplier();
        return distance < range * range;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Not saved: the entity type disables saving.
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
