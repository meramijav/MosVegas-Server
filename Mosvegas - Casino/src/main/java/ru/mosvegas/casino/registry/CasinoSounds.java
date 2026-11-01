package ru.mosvegas.casino.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import ru.mosvegas.casino.MosVegasCasino;

import java.util.HashMap;
import java.util.Map;

/**
 * Sound events. {@code assets/mosvegas_casino/sounds.json} maps them to vanilla sounds by default;
 * drop your own .ogg files into a resource pack and point sounds.json at them to replace.
 */
public final class CasinoSounds {
    private static final Map<String, SoundEvent> BY_NAME = new HashMap<>();
    public static final String[] NAMES = {
            "card_deal", "card_flip", "chip_place", "chip_stack", "shuffle", "knock", "cards_collect", "win", "blackjack", "dealer_bell"
    };

    private CasinoSounds() {}

    public static void register() {
        for (String name : NAMES) {
            Identifier id = MosVegasCasino.id(name);
            BY_NAME.put(name, Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id)));
        }
    }

    public static SoundEvent get(String name) { return BY_NAME.get(name); }
}
