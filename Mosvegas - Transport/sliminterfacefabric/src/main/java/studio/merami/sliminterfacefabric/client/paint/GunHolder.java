package studio.merami.sliminterfacefabric.client.paint;

import net.minecraft.world.entity.LivingEntity;

public final class GunHolder {
    private static LivingEntity holder;

    private GunHolder() {}

    public static void set(LivingEntity entity) {
        holder = entity;
    }

    public static LivingEntity get() {
        return holder;
    }
}
