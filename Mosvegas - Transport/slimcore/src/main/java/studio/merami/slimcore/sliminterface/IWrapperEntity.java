package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.components.AEntityA_Base;

import java.util.List;
import java.util.UUID;

public interface IWrapperEntity {
    int id();

    UUID uuid();

    IWrapperWorld world();

    Point3D position();

    Point3D position(float partial);

    double x();

    double y();

    double z();

    void setPosition(double x, double y, double z);

    void moveTo(double x, double y, double z, float yaw, float pitch);

    void setOldPosAndRot();

    float yaw();

    float prevYaw();

    void setYaw(float yaw);

    void setHeadYaw(float yaw);

    void setBodyYaw(float yaw, float previous);

    float pitch();

    Point3D eyePosition();

    Point3D lookAngle();

    Point3D velocity();

    void setVelocity(Point3D velocity);

    void markHurt();

    void markImpulse();

    void resetFallDistance();

    BoundingBox bounds();

    void refreshBounds();

    BoundingBox standingBounds(Point3D feet);

    float width();

    boolean onGround();

    void setOnGround(boolean onGround);

    boolean inWater();

    int tickCount();

    boolean isLiving();

    boolean isSpectator();

    boolean noPhysics();

    boolean isAlive();

    boolean isRemoved();

    void discard();

    double distanceToSqr(Point3D point);

    double distanceToSqr(IWrapperEntity other);

    AEntityA_Base vehicle();

    AEntityA_Base rootVehicle();

    List<IWrapperEntity> passengers();

    boolean hasPassenger(IWrapperEntity passenger);

    boolean startRiding(AEntityA_Base vehicle);

    void stopRiding();

    void teleport(Point3D position);

    boolean hurt(IWrapperDamage damage, float amount);

    void dropItem(IWrapperItemStack stack);

    IWrapperPlayer asPlayer();
}
