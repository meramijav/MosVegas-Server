package studio.merami.sliminterfacefabric.compat;

import net.minecraft.server.level.ServerPlayer;
import ru.mosvegas.core.data.DataStore;
import ru.mosvegas.core.service.EconomyService;
import studio.merami.sliminterfacefabric.shop.Economy;

public final class MosvegasMoney implements Economy {
    public static final MosvegasMoney INSTANCE = new MosvegasMoney();

    private MosvegasMoney() {}

    @Override
    public double balance(ServerPlayer player) {
        return DataStore.player(player.getUUID()).bankBalance;
    }

    @Override
    public boolean withdraw(ServerPlayer player, long amount) {
        return EconomyService.debit(player, amount);
    }

    @Override
    public String format(double amount) {
        return EconomyService.money((long) Math.floor(amount));
    }
}
