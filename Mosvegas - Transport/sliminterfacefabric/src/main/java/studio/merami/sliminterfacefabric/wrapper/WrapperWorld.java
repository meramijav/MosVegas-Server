package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import studio.merami.slimcore.baseclasses.BlockPosition;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.sliminterface.BlockHit;
import studio.merami.slimcore.sliminterface.IInterfaceCore;
import studio.merami.slimcore.sliminterface.IWrapperDamage;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.compat.MosvegasCompat;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.registry.EntityRegistry;
import studio.merami.sliminterfacefabric.registry.RuleRegistry;
import studio.merami.sliminterfacefabric.registry.SoundRegistry;

import java.util.ArrayList;
import java.util.List;

public final class WrapperWorld implements IWrapperWorld {
    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK, SlimVehicle.id("car_breakable"));
    public static final ResourceKey<DamageType> RUN_OVER = ResourceKey.create(Registries.DAMAGE_TYPE, SlimVehicle.id("vehicle"));
    private static final ParticleOptions GLASS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());

    public final Level level;

    private WrapperWorld(Level level) {
        this.level = level;
    }

    public static WrapperWorld of(Level level) {
        if (level == null) return null;
        IWrapperHolder holder = (IWrapperHolder) level;
        if (holder.slimvehicle$wrapper() instanceof WrapperWorld wrapper) return wrapper;
        WrapperWorld wrapper = new WrapperWorld(level);
        holder.slimvehicle$setWrapper(wrapper);
        return wrapper;
    }

    private static BlockPos pos(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    @Override
    public boolean allows(IWrapperPlayer player, Access access, AEntityA_Base vehicle) {
        return MosvegasCompat.allows(level, player == null ? null : WrapperEntity.unwrap(player), access, WrapperEntity.unwrap(vehicle));
    }

    @Override
    public boolean isClient() {
        return level.isClientSide;
    }

    @Override
    public long gameTime() {
        return level.getGameTime();
    }

    @Override
    public boolean rule(IInterfaceCore.Rule rule) {
        return level.getGameRules().getBoolean(RuleRegistry.key(rule));
    }

    @Override
    public List<BoundingBox> blockCollisions(IWrapperEntity entity, BoundingBox query) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (VoxelShape shape : level.getBlockCollisions(WrapperEntity.unwrap(entity), Convert.aabb(query))) {
            for (AABB box : shape.toAabbs()) boxes.add(Convert.box(box));
        }
        return boxes;
    }

    @Override
    public boolean blockCollides(IWrapperEntity entity, BoundingBox query) {
        return level.getBlockCollisions(WrapperEntity.unwrap(entity), Convert.aabb(query)).iterator().hasNext();
    }

    @Override
    public boolean noCollision(IWrapperEntity entity, BoundingBox box) {
        return level.noCollision(WrapperEntity.unwrap(entity), Convert.aabb(box));
    }

    @Override
    public BlockHit raycastBlocks(Point3D from, Point3D to, IWrapperEntity entity) {
        BlockHitResult hit = level.clip(new ClipContext(Convert.vec(from), Convert.vec(to), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, WrapperEntity.unwrap(entity)));
        return new BlockHit(hit.getType() == HitResult.Type.BLOCK, hit.getDirection() == Direction.UP, Convert.point(hit.getLocation()));
    }

    @Override
    public List<IWrapperEntity> entities(IWrapperEntity except, BoundingBox box) {
        List<IWrapperEntity> entities = new ArrayList<>();
        for (Entity entity : level.getEntities(WrapperEntity.unwrap(except), Convert.aabb(box), entity -> true)) entities.add(WrapperEntity.of(entity));
        return entities;
    }

    @Override
    public List<AEntityA_Base> coreEntities(BoundingBox box) {
        List<AEntityA_Base> entities = new ArrayList<>();
        for (BuilderVehicle builder : level.getEntitiesOfClass(BuilderVehicle.class, Convert.aabb(box))) entities.add(builder.vehicle());
        return entities;
    }

    @Override
    public float breakableHardness(BlockPosition position) {
        BlockPos pos = pos(position);
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        return state.is(BREAKABLE) && hardness >= 0 && MosvegasCompat.terrain(level, pos) ? hardness : -1;
    }

    @Override
    public void destroyBlock(BlockPosition position, IWrapperEntity breaker) {
        level.destroyBlock(pos(position), true, WrapperEntity.unwrap(breaker));
    }

    @Override
    public void playSound(Point3D at, String sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, SoundRegistry.event(sound), SoundSource.NEUTRAL, volume, pitch);
    }

    @Override
    public void spawnParticles(Particle particle, Point3D at, int count, double spreadX, double spreadY, double spreadZ, double speed) {
        if (!(level instanceof ServerLevel server)) return;
        ParticleOptions options = switch (particle) {
            case GLASS -> GLASS;
            case SMOKE -> ParticleTypes.SMOKE;
            case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
            case SPARK -> ParticleTypes.ELECTRIC_SPARK;
        };
        server.sendParticles(options, at.x, at.y, at.z, count, spreadX, spreadY, spreadZ, speed);
    }

    @Override
    public void spawnBlockParticles(BlockPosition block, Point3D at, int count, double spread, double speed) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState state = level.getBlockState(pos(block));
        if (!state.isAir()) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    @Override
    public IWrapperDamage damage(DamageKind kind, IWrapperEntity direct, IWrapperEntity causing) {
        DamageSource source = switch (kind) {
            case RUN_OVER -> level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(RUN_OVER)
                    .map(type -> new DamageSource(type, WrapperEntity.unwrap(direct), WrapperEntity.unwrap(causing))).orElseGet(() -> level.damageSources().flyIntoWall());
            case CRASH -> level.damageSources().flyIntoWall();
            case FALL -> level.damageSources().fall();
        };
        return new WrapperDamage(source);
    }

    @Override
    public EntityVehicleF_Physics createVehicle() {
        BuilderVehicle builder = EntityRegistry.VEHICLE.create(level);
        return builder == null ? null : builder.vehicle();
    }

    @Override
    public boolean addVehicle(EntityVehicleF_Physics vehicle) {
        return level.addFreshEntity(WrapperEntity.unwrap(vehicle));
    }
}
