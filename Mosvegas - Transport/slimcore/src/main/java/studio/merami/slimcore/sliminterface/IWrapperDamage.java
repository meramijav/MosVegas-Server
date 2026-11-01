package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.baseclasses.Point3D;

public interface IWrapperDamage {
    IWrapperEntity projectile();

    boolean isExplosion();

    Point3D sourcePosition();

    IWrapperEntity attacker();
}
