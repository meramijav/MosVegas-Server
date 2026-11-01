package studio.merami.sliminterfacefabric.client.sound;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import studio.merami.slimcore.SlimCore;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.List;

public final class RadioStream implements AudioStream {
    static final int RATE = 44100;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
    private static final int FRAMES = 2048;
    private static final int POLL = RATE / 10;

    private final List<RadioStations.Track> tracks;
    private int index;
    private ByteBuffer data;
    private long handle;
    private ShortBuffer decoded;
    private int channels = 1;
    private int available;
    private int position;
    private double step = 1;
    private double phase = 1;
    private float previous;
    private float current;
    private int failures;
    private boolean played;
    private long waiting = -1;

    public RadioStream(RadioStations.Tuned tuned) {
        tracks = tuned.station().tracks();
        index = tuned.track();
        if (!open(tuned.offset())) advance();
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int size) {
        if (failures >= tracks.size()) return null;
        int samples = Math.max(1, size / 2);
        ByteBuffer out = BufferUtils.createByteBuffer(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            while (phase >= 1) {
                previous = current;
                current = next();
                phase -= 1;
            }
            float value = previous + (current - previous) * (float) phase;
            phase += step;
            out.putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value))));
        }
        out.flip();
        return out;
    }

    private float next() {
        while (position >= available) {
            if (waiting >= 0) {
                RadioStations.Track track = tracks.get(index);
                if (waiting >= track.seconds() * RATE) {
                    if (!advance()) return 0;
                    continue;
                }
                if (waiting % POLL != 0 || !track.ready()) {
                    waiting++;
                    return 0;
                }
                if (!open(waiting / (double) RATE)) {
                    failures++;
                    if (!advance()) return 0;
                }
                continue;
            }
            if (handle != 0) {
                decoded.clear();
                int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, decoded);
                if (frames > 0) {
                    available = frames;
                    position = 0;
                    failures = 0;
                    played = true;
                    break;
                }
                if (!played) failures++;
            }
            if (!advance()) return 0;
        }
        int base = position++ * channels;
        float sum = 0;
        for (int c = 0; c < channels; c++) sum += decoded.get(base + c);
        return sum / channels;
    }

    private boolean advance() {
        while (failures < tracks.size()) {
            index = (index + 1) % tracks.size();
            if (open(0)) return true;
            failures++;
        }
        release();
        return false;
    }

    private boolean open(double offset) {
        release();
        RadioStations.Track track = tracks.get(index);
        available = 0;
        position = 0;
        played = false;
        if (!track.ready()) {
            waiting = (long) (offset * RATE);
            step = 1;
            return true;
        }
        waiting = -1;
        try {
            data = track.load();
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Radio: could not read {}: {}", track.title(), e.getMessage());
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
            if (handle == 0) {
                release();
                return false;
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            channels = Math.max(1, info.channels());
            step = info.sample_rate() / (double) RATE;
            if (offset > 0) STBVorbis.stb_vorbis_seek(handle, (int) (offset * info.sample_rate()));
        }
        if (decoded == null || decoded.capacity() < FRAMES * channels) {
            if (decoded != null) MemoryUtil.memFree(decoded);
            decoded = MemoryUtil.memAllocShort(FRAMES * channels);
        }
        return true;
    }

    private void release() {
        if (handle != 0) {
            STBVorbis.stb_vorbis_close(handle);
            handle = 0;
        }
        if (data != null) {
            MemoryUtil.memFree(data);
            data = null;
        }
    }

    @Override
    public void close() {
        release();
        if (decoded != null) {
            MemoryUtil.memFree(decoded);
            decoded = null;
        }
    }
}
