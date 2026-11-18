package ru.mosvegas.character;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mosvegas.character.command.CharacterCommand;
import ru.mosvegas.character.data.CharacterOptions;
import ru.mosvegas.character.data.CharacterStore;
import ru.mosvegas.character.net.CharacterNet;
import ru.mosvegas.character.server.CharacterGate;

public final class MosVegasCharacter implements ModInitializer {
    public static final String MOD_ID = "mosvegas_character";
    public static final Logger LOGGER = LoggerFactory.getLogger("MosVegas Character");
    public static final CharacterStore STORE = new CharacterStore();

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CharacterOptions.load();
        CharacterNet.registerServer();
        CharacterGate.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> CharacterCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CharacterOptions.load();
            STORE.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STORE.save();
            STORE.unload();
            CharacterGate.clear();
        });
    }
}
