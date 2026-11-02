package ru.mosvegas.menu;

import net.fabricmc.api.ClientModInitializer;

public final class MosVegasMenuClient implements ClientModInitializer {
    public static final String MOD_ID = "mosvegas_menu";

    @Override
    public void onInitializeClient() {
        VideoBackground.initialize();
    }
}
