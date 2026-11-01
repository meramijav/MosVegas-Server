package ru.mosvegas.casino.client.voice;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import ru.mosvegas.casino.MosVegasCasino;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * A voice line coming from the dealer's mouth: follows the entity, uses the "Voice/Speech" volume slider and
 * streams our own decoded PCM through Fabric's custom audio stream hook ({@code mosvegas_casino:voice} in
 * sounds.json is a streamed placeholder).
 */
final class VoiceSound extends MovingSoundInstance implements FabricSoundInstance {
    static final Identifier EVENT = MosVegasCasino.id("voice");

    private final Entity speaker;
    private final VoiceClip clip;

    VoiceSound(Entity speaker, VoiceClip clip, float pitch) {
        super(SoundEvent.of(EVENT), SoundCategory.VOICE, SoundInstance.createRandom());
        this.speaker = speaker;
        this.clip = clip;
        this.pitch = pitch;
        this.volume = 1.0f;
        this.repeat = false;
        this.attenuationType = AttenuationType.LINEAR;
        follow();
    }

    private void follow() {
        x = speaker.getX();
        y = speaker.getEyeY();
        z = speaker.getZ();
    }

    @Override
    public void tick() {
        if (speaker.isRemoved()) setDone();
        else follow();
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
        if (Boolean.getBoolean("mosvegas.casino.autotest")) MosVegasCasino.LOGGER.info("[voice] streaming {} s", clip.seconds());
        return CompletableFuture.completedFuture(new PcmStream(clip));
    }

    /** Serves the decoded samples in chunks to the sound engine; null marks the end. */
    private static final class PcmStream implements AudioStream {
        private final VoiceClip clip;
        private final AudioFormat format;
        private int position;

        PcmStream(VoiceClip clip) {
            this.clip = clip;
            this.format = new AudioFormat(clip.sampleRate(), 16, 1, true, false);
        }

        @Override public AudioFormat getFormat() { return format; }

        @Override
        public ByteBuffer getBuffer(int size) {
            short[] pcm = clip.pcm();
            if (position >= pcm.length) return null;
            int samples = Math.min(pcm.length - position, Math.max(1, size / 2));
            ByteBuffer out = BufferUtils.createByteBuffer(samples * 2);
            for (int i = 0; i < samples; i++) out.putShort(pcm[position + i]);
            position += samples;
            out.flip();
            return out;
        }

        @Override public void close() {}
    }
}
