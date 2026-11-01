package studio.merami.sliminterfacefabric.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import studio.merami.sliminterfacefabric.compat.TaczCompat;

public final class TaczClientCompat {
    private TaczClientCompat() {}

    static void tick(Minecraft mc) {
        if (TaczCompat.LOADED && mc.player != null && TaczCompat.driving(mc.player)) Aim.release(mc.player);
    }

    private static final class Aim {
        static void release(LocalPlayer player) {
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (operator.isAim()) operator.aim(false);
        }
    }
}
