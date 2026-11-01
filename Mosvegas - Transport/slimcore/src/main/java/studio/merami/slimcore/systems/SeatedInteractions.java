package studio.merami.slimcore.systems;

import studio.merami.slimcore.entities.components.AEntityA_Base;
import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;

public final class SeatedInteractions {
    private SeatedInteractions() {}

    public static boolean seated(IWrapperPlayer player) {
        return player.vehicle() instanceof AEntityF_Multipart<?>;
    }

    public static boolean canUseEntity(IWrapperPlayer player, AEntityA_Base target) {
        return !seated(player) || player.vehicle() == target;
    }
}
