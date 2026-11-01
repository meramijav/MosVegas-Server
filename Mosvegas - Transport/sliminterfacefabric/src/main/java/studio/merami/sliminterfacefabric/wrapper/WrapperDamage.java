package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.sliminterface.IWrapperDamage;
import studio.merami.slimcore.sliminterface.IWrapperEntity;

public final class WrapperDamage implements IWrapperDamage {
    public final DamageSource source;

    public WrapperDamage(DamageSource source) {
        this.source = source;
    }

    @Override
    public IWrapperEntity projectile() {
        return source.getDirectEntity() instanceof Projectile projectile ? WrapperEntity.of(projectile) : null;
    }

    @Override
    public boolean isExplosion() {
        return source.is(DamageTypeTags.IS_EXPLOSION);
    }

    @Override
    public Point3D sourcePosition() {
        Vec3 position = source.getSourcePosition();
        return position == null ? null : Convert.point(position);
    }

    @Override
    public IWrapperEntity attacker() {
        return WrapperEntity.of(source.getEntity());
    }
}
