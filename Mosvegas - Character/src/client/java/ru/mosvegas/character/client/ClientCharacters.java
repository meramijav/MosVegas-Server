package ru.mosvegas.character.client;

import ru.mosvegas.character.data.Appearance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientCharacters {
    private static final Map<UUID, Appearance> APPEARANCES = new ConcurrentHashMap<>();
    private static boolean editorRequired;
    private static Appearance draft;
    private static List<String> outfits = List.of();

    private ClientCharacters() {}

    public static Appearance get(UUID id) {
        return APPEARANCES.get(id);
    }

    public static Appearance effective(UUID id) {
        if (editorRequired && draft != null) {
            var player = net.minecraft.client.MinecraftClient.getInstance().player;
            if (player != null && player.getUuid().equals(id)) return draft;
        }
        return APPEARANCES.get(id);
    }

    public static void set(UUID id, Appearance appearance) {
        if (appearance == null) APPEARANCES.remove(id);
        else APPEARANCES.put(id, appearance);
    }

    public static boolean editorRequired() {
        return editorRequired;
    }

    public static void requireEditor(Appearance current, List<String> outfitList) {
        editorRequired = true;
        outfits = List.copyOf(outfitList);
        draft = current != null ? current.copy() : new Appearance();
        if (!outfits.isEmpty() && !outfits.contains(draft.outfit)) draft.outfit = outfits.get(0);
    }

    public static void releaseEditor() {
        editorRequired = false;
    }

    public static Appearance draft() {
        if (draft == null) draft = new Appearance();
        return draft;
    }

    public static List<String> outfits() {
        return outfits;
    }

    public static void clear() {
        APPEARANCES.clear();
        editorRequired = false;
        draft = null;
        outfits = List.of();
    }
}
