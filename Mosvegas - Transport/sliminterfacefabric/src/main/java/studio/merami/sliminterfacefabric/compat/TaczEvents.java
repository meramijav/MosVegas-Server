package studio.merami.sliminterfacefabric.compat;

import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;

final class TaczEvents {
    private TaczEvents() {}

    static void register() {
        GunShootEvent.CALLBACK.register(event -> {
            if (TaczCompat.driving(event.getShooter())) event.setCanceled(true);
        });
        GunReloadEvent.CALLBACK.register(event -> {
            if (TaczCompat.driving(event.getEntity())) event.setCanceled(true);
        });
        GunMeleeEvent.CALLBACK.register(event -> {
            if (TaczCompat.driving(event.getShooter())) event.setCanceled(true);
        });
        GunFireSelectEvent.CALLBACK.register(event -> {
            if (TaczCompat.driving(event.getShooter())) event.setCanceled(true);
        });
    }

    static boolean holdsGun(Player player) {
        return IGun.mainHandHoldGun(player);
    }
}
