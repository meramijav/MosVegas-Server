package ru.mosvegas.casino.client.voice;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.dealer.DealerEntity;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Plays the dealer's voice lines from {@code assets/mosvegas_casino/voice/<set>/<key>.ogg|.wav}: the first key
 * of the server's list that exists (e.g. {@code dealer_total_19}, then {@code dealer_total}). While the line
 * plays, the dealer's lips move.
 */
public final class VoicePlayer {
    private VoicePlayer() {}

    public static void play(int entityId, String set, float pitch, List<String> keys) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Entity speaker = mc.world.getEntityById(entityId);
        if (speaker == null) return;
        for (String key : keys) {
            for (String ext : new String[]{".ogg", ".wav"}) {
                Identifier id = Identifier.tryParse(MosVegasCasino.MOD_ID + ":voice/" + set + "/" + key + ext);
                if (id == null) continue;
                Optional<Resource> resource = mc.getResourceManager().getResource(id);
                if (resource.isEmpty()) continue;
                try (InputStream in = resource.get().getInputStream()) {
                    VoiceClip clip = VoiceClip.read(ext, in);
                    mc.getSoundManager().play(new VoiceSound(speaker, clip, pitch));
                    if (speaker instanceof DealerEntity dealer) {
                        dealer.talkUntil(mc.world.getTime() + clip.seconds() * 20f / pitch);
                    }
                } catch (Exception e) {
                    MosVegasCasino.LOGGER.warn("Voice line {} could not be played: {}", id, e.toString());
                }
                return;
            }
        }
    }
}
