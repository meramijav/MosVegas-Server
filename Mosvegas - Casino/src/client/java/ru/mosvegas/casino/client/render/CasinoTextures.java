package ru.mosvegas.casino.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.game.cards.Cards;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Texture lookup with fallbacks, so a resource pack can replace or add any card/chip/table texture:
 * {@code textures/card/<suit>_<rank>.png}, {@code textures/card/back_<style>.png} (else {@code back.png}),
 * {@code textures/chip/chip_<value>.png} (else a colour slot {@code chip_slot<N>.png}).
 */
public final class CasinoTextures {
    private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();
    private static final Identifier BACK = MosVegasCasino.id("textures/card/back.png");
    private static final Map<Integer, Identifier> FACES = new HashMap<>();

    private CasinoTextures() {}

    public static void clearCache() { EXISTS.clear(); }

    public static boolean exists(Identifier id) {
        return EXISTS.computeIfAbsent(id, k -> MinecraftClient.getInstance().getResourceManager().getResource(k).isPresent());
    }

    public static Identifier face(int card) {
        return FACES.computeIfAbsent(card, c -> MosVegasCasino.id("textures/card/" + Cards.textureName(c) + ".png"));
    }

    public static Identifier back(String style) {
        Identifier styled = MosVegasCasino.id("textures/card/back_" + style + ".png");
        return exists(styled) ? styled : BACK;
    }

    public static Identifier chip(long value, List<Long> chips) {
        Identifier exact = MosVegasCasino.id("textures/chip/chip_" + value + ".png");
        if (exists(exact)) return exact;
        int slot = Math.max(0, chips.indexOf(value));
        return MosVegasCasino.id("textures/chip/chip_slot" + Math.min(slot, 7) + ".png");
    }
}
