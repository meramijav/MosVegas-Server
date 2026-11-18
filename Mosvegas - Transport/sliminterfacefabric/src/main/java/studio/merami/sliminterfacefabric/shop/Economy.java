package studio.merami.sliminterfacefabric.shop;

import net.minecraft.server.level.ServerPlayer;
import studio.merami.sliminterfacefabric.compat.MosvegasCompat;
import studio.merami.sliminterfacefabric.compat.MosvegasMoney;

public interface Economy {
    double balance(ServerPlayer player);

    boolean withdraw(ServerPlayer player, long amount);

    String format(double amount);

    static Economy find() {
        return MosvegasCompat.LOADED ? MosvegasMoney.INSTANCE : null;
    }
}
