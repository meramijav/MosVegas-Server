package ru.mosvegas.casino;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mosvegas.casino.command.CasinoCommands;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.dealer.DealerRoster;
import ru.mosvegas.casino.dealer.SkinResolver;
import ru.mosvegas.casino.economy.Economy;
import ru.mosvegas.casino.net.CasinoNet;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.BlackjackTable;
import ru.mosvegas.casino.table.TableRegistry;

public final class MosVegasCasino implements ModInitializer {
    public static final String MOD_ID = "mosvegas_casino";
    public static final Logger LOGGER = LoggerFactory.getLogger("MosVegas Casino");

    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    @Override
    public void onInitialize() {
        ConfigManager.load();
        CasinoRegistry.register();
        CasinoNet.registerServer();
        CasinoCommands.register();
        ru.mosvegas.casino.dev.Showcase.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Economy.configure(ConfigManager.get());
            DealerRoster.reset();
            SkinResolver.init(server, () -> server.getPlayerManager().getPlayerList().forEach(CasinoNet::sendProfiles));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TableRegistry.disposeAll();
            DealerRoster.reset();
            SkinResolver.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CasinoNet.sendProfiles(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            BlackjackTable table = TableRegistry.tableOf(handler.player.getUuid());
            if (table != null) table.leave(handler.player.getUuid(), false);
        });
        LOGGER.info("MosVegas Casino загружен, конфиг: {}", ConfigManager.path());
    }
}
