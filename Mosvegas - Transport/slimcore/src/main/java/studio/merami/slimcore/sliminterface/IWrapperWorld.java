package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.baseclasses.BlockPosition;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;

import java.util.List;

public interface IWrapperWorld {
    enum Particle {
        GLASS,
        SMOKE,
        LARGE_SMOKE,
        SPARK
    }

    enum DamageKind {
        RUN_OVER,
        CRASH,
        FALL
    }

    enum Access {
        ENTER,
        USE,
        PLACE,
        STORAGE,
        DAMAGE
    }

    boolean isClient();

    boolean allows(IWrapperPlayer player, Access access, AEntityA_Base vehicle);

    long gameTime();

    boolean rule(IInterfaceCore.Rule rule);

    List<BoundingBox> blockCollisions(IWrapperEntity entity, BoundingBox query);

    boolean blockCollides(IWrapperEntity entity, BoundingBox query);

    boolean noCollision(IWrapperEntity entity, BoundingBox box);

    BlockHit raycastBlocks(Point3D from, Point3D to, IWrapperEntity entity);

    List<IWrapperEntity> entities(IWrapperEntity except, BoundingBox box);

    List<AEntityA_Base> coreEntities(BoundingBox box);

    float breakableHardness(BlockPosition pos);

    void destroyBlock(BlockPosition pos, IWrapperEntity breaker);

    void playSound(Point3D at, String sound, float volume, float pitch);

    void spawnParticles(Particle particle, Point3D at, int count, double spreadX, double spreadY, double spreadZ, double speed);

    void spawnBlockParticles(BlockPosition block, Point3D at, int count, double spread, double speed);

    IWrapperDamage damage(DamageKind kind, IWrapperEntity direct, IWrapperEntity causing);

    EntityVehicleF_Physics createVehicle();

    boolean addVehicle(EntityVehicleF_Physics vehicle);
}
