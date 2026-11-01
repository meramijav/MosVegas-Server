package ru.mosvegas.casino.client.voice;

import net.minecraft.client.sound.OggAudioStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A decoded voice line: 16-bit mono PCM. Reads WAV (PCM 16-bit or IMA ADPCM, as written by tools/VoicePack.java)
 * and Ogg Vorbis, so a resource pack can replace any line with a real recording.
 */
public record VoiceClip(short[] pcm, int sampleRate) {
    public float seconds() { return pcm.length / (float) sampleRate; }

    public static VoiceClip read(String fileName, InputStream in) throws IOException {
        if (fileName.endsWith(".ogg")) return ogg(in);
        return wav(in.readAllBytes());
    }

    static VoiceClip ogg(InputStream in) throws IOException {
        try (OggAudioStream stream = new OggAudioStream(in)) {
            ByteBuffer all = stream.getBuffer().order(ByteOrder.LITTLE_ENDIAN);
            int channels = stream.getFormat().getChannels();
            int frames = all.remaining() / 2 / Math.max(1, channels);
            short[] pcm = new short[frames];
            for (int i = 0; i < frames; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) sum += all.getShort((i * channels + c) * 2);
                pcm[i] = (short) (sum / Math.max(1, channels));
            }
            return new VoiceClip(pcm, (int) stream.getFormat().getSampleRate());
        }
    }

    public static VoiceClip wav(byte[] wav) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 12 || !"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))) throw new IOException("not a WAV file");
        int format = 1, channels = 1, rate = 16000, blockAlign = 0, bits = 16, samplesPerBlock = 0, total = -1;
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(pos + 4);
            int body = pos + 8;
            switch (id) {
                case "fmt " -> {
                    format = b.getShort(body) & 0xFFFF;
                    channels = b.getShort(body + 2);
                    rate = b.getInt(body + 4);
                    blockAlign = b.getShort(body + 12) & 0xFFFF;
                    bits = b.getShort(body + 14);
                    if (format == 0x11 && size >= 20) samplesPerBlock = b.getShort(body + 18) & 0xFFFF;
                }
                case "fact" -> total = b.getInt(body);
                case "data" -> {
                    int length = Math.min(size, wav.length - body);
                    if (format == 1 && bits == 16) return new VoiceClip(pcm16(b, body, length, channels), rate);
                    if (format == 0x11 && channels == 1) {
                        if (samplesPerBlock == 0) samplesPerBlock = (blockAlign - 4) * 2 + 1;
                        return new VoiceClip(imaAdpcm(wav, body, length, blockAlign, samplesPerBlock, total), rate);
                    }
                    throw new IOException("unsupported WAV format " + format + " (" + bits + " bit, " + channels + " ch)");
                }
                default -> { }
            }
            pos = body + size + (size & 1);
        }
        throw new IOException("WAV without data");
    }

    private static short[] pcm16(ByteBuffer b, int start, int length, int channels) {
        int frames = length / 2 / Math.max(1, channels);
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += b.getShort(start + (i * channels + c) * 2);
            out[i] = (short) (sum / Math.max(1, channels));
        }
        return out;
    }

    private static final int[] INDEX = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
    private static final int[] STEP = {7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
            107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060,
            1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845,
            8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};

    private static short[] imaAdpcm(byte[] data, int start, int length, int blockAlign, int samplesPerBlock, int total) {
        int blocks = (length + blockAlign - 1) / blockAlign;
        int capacity = blocks * samplesPerBlock;
        short[] out = new short[total > 0 ? Math.min(total, capacity) : capacity];
        int n = 0;
        for (int block = 0; block < blocks && n < out.length; block++) {
            int p = start + block * blockAlign;
            int end = Math.min(start + length, p + blockAlign);
            if (p + 4 > end) break;
            int predictor = (short) ((data[p] & 0xFF) | (data[p + 1] << 8));
            int index = Math.max(0, Math.min(88, data[p + 2] & 0xFF));
            out[n++] = (short) predictor;
            for (int i = p + 4; i < end && n < out.length; i++) {
                for (int half = 0; half < 2 && n < out.length; half++) {
                    int nibble = half == 0 ? data[i] & 0x0F : (data[i] >> 4) & 0x0F;
                    int step = STEP[index];
                    int delta = step >> 3;
                    if ((nibble & 4) != 0) delta += step;
                    if ((nibble & 2) != 0) delta += step >> 1;
                    if ((nibble & 1) != 0) delta += step >> 2;
                    predictor = (nibble & 8) != 0 ? predictor - delta : predictor + delta;
                    predictor = Math.max(-32768, Math.min(32767, predictor));
                    index = Math.max(0, Math.min(88, index + INDEX[nibble]));
                    out[n++] = (short) predictor;
                }
            }
        }
        if (n == out.length) return out;
        short[] trimmed = new short[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }
}
