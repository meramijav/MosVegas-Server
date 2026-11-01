import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Helper of tools/VoiceGen.ps1 (JDK 21, run from the project root):
 * <pre>
 *   java tools/VoicePack.java lines tools/voice/phrases.txt build/voice-raw/lines.txt   expands the phrase list
 *   java tools/VoicePack.java pack build/voice-raw src/main/resources/assets/mosvegas_casino/voice
 * </pre>
 * "pack" trims the silence of the synthesized 16-bit mono WAV files, normalizes them and stores them as
 * IMA ADPCM WAV (4 bits per sample), which the mod decodes itself (ru.mosvegas.casino.client.voice.VoiceClip).
 */
public class VoicePack {
    static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("anna", "Анна");
        NAMES.put("maria", "Мария");
        NAMES.put("elena", "Елена");
        NAMES.put("maxim", "Максим");
        NAMES.put("artem", "Артём");
        NAMES.put("david", "Давид");
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("lines")) lines(Paths.get(args[1]), Paths.get(args[2]));
        else if (args.length == 3 && args[0].equals("pack")) pack(Paths.get(args[1]), Paths.get(args[2]));
        else System.err.println("usage: lines <phrases.txt> <out.txt> | pack <rawDir> <outDir>");
    }

    // ------------------------------------------------------------------ phrase list

    static final Pattern RANGE = Pattern.compile("^(.*)\\[(\\d+)\\.\\.(\\d+)]$");

    static void lines(Path in, Path out) throws IOException {
        List<String> result = new ArrayList<>();
        for (String raw : Files.readAllLines(in, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int bar = line.indexOf('|');
            String key = line.substring(0, bar).strip(), text = line.substring(bar + 1).strip();
            String[] sets = {"female", "male"};
            int colon = key.indexOf(':');
            if (colon > 0) {
                sets = new String[]{key.substring(0, colon)};
                key = key.substring(colon + 1);
            }
            Matcher m = RANGE.matcher(key);
            List<String[]> expanded = new ArrayList<>();
            if (m.matches()) {
                for (int n = Integer.parseInt(m.group(2)); n <= Integer.parseInt(m.group(3)); n++) {
                    String lo = Integer.toString(n - 10);
                    expanded.add(new String[]{m.group(1).replace("{n}", "" + n).replace("{lo}", lo), text.replace("{n}", "" + n).replace("{lo}", lo)});
                }
            } else if (key.endsWith("[names]")) {
                String base = key.substring(0, key.length() - "[names]".length());
                for (var e : NAMES.entrySet()) expanded.add(new String[]{base.replace("{id}", e.getKey()), text.replace("{name}", e.getValue())});
            } else expanded.add(new String[]{key, text});
            for (String set : sets) for (String[] kv : expanded) result.add(set + "|" + kv[0] + "|" + kv[1]);
        }
        Files.createDirectories(out.getParent());
        Files.write(out, result, StandardCharsets.UTF_8);
        System.out.println(result.size() + " voice lines -> " + out);
    }

    // ------------------------------------------------------------------ packing

    static void pack(Path rawDir, Path outDir) throws IOException {
        int count = 0;
        long bytes = 0;
        List<Path> files;
        try (Stream<Path> s = Files.walk(rawDir)) {
            files = s.filter(p -> p.toString().endsWith(".wav")).toList();
        }
        for (Path wav : files) {
            Path rel = rawDir.relativize(wav);
            short[] pcm = readPcm16(Files.readAllBytes(wav));
            int rate = sampleRate(Files.readAllBytes(wav));
            pcm = normalize(trim(pcm, rate));
            byte[] out = adpcmWav(pcm, rate);
            Path target = outDir.resolve(rel.toString());
            Files.createDirectories(target.getParent());
            Files.write(target, out);
            count++;
            bytes += out.length;
        }
        System.out.printf("%d voice files, %.1f KB -> %s%n", count, bytes / 1024.0, outDir);
    }

    static int sampleRate(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(pos + 4);
            if (id.equals("fmt ")) return b.getInt(pos + 12);
            pos += 8 + size + (size & 1);
        }
        return 16000;
    }

    static short[] readPcm16(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(pos + 4);
            if (id.equals("data")) {
                size = Math.min(size, wav.length - pos - 8);
                short[] out = new short[size / 2];
                for (int i = 0; i < out.length; i++) out[i] = b.getShort(pos + 8 + i * 2);
                return out;
            }
            pos += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("no data chunk");
    }

    /** Cuts silence at both ends, keeps 40 ms of air. */
    static short[] trim(short[] pcm, int rate) {
        int threshold = 500, pad = rate / 25;
        int start = 0, end = pcm.length - 1;
        while (start < pcm.length && Math.abs(pcm[start]) < threshold) start++;
        while (end > start && Math.abs(pcm[end]) < threshold) end--;
        start = Math.max(0, start - pad);
        end = Math.min(pcm.length - 1, end + pad);
        short[] out = new short[Math.max(1, end - start + 1)];
        System.arraycopy(pcm, start, out, 0, out.length);
        return out;
    }

    static short[] normalize(short[] pcm) {
        int peak = 1;
        for (short s : pcm) peak = Math.max(peak, Math.abs(s));
        double gain = Math.min(4.0, 29000.0 / peak);
        short[] out = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++) out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(pcm[i] * gain)));
        return out;
    }

    // ------------------------------------------------------------------ IMA ADPCM

    static final int[] INDEX = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
    static final int[] STEP = {7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
            107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060,
            1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845,
            8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
    static final int BLOCK_ALIGN = 256;
    static final int SAMPLES_PER_BLOCK = (BLOCK_ALIGN - 4) * 2 + 1;

    static byte[] adpcmWav(short[] pcm, int rate) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int index = 0;
        for (int start = 0; start < pcm.length; start += SAMPLES_PER_BLOCK) {
            byte[] block = new byte[BLOCK_ALIGN];
            int predictor = pcm[start];
            block[0] = (byte) predictor;
            block[1] = (byte) (predictor >> 8);
            block[2] = (byte) index;
            for (int i = 0; i < (BLOCK_ALIGN - 4) * 2; i++) {
                int s = start + 1 + i;
                int sample = s < pcm.length ? pcm[s] : predictor;
                // encode one nibble and update the decoder state exactly like the decoder will
                int step = STEP[index];
                int diff = sample - predictor;
                int nibble = 0;
                if (diff < 0) {
                    nibble = 8;
                    diff = -diff;
                }
                if (diff >= step) {
                    nibble |= 4;
                    diff -= step;
                }
                if (diff >= step >> 1) {
                    nibble |= 2;
                    diff -= step >> 1;
                }
                if (diff >= step >> 2) nibble |= 1;
                int delta = step >> 3;
                if ((nibble & 4) != 0) delta += step;
                if ((nibble & 2) != 0) delta += step >> 1;
                if ((nibble & 1) != 0) delta += step >> 2;
                predictor = (nibble & 8) != 0 ? predictor - delta : predictor + delta;
                predictor = Math.max(-32768, Math.min(32767, predictor));
                index = Math.max(0, Math.min(88, index + INDEX[nibble]));
                int b = 4 + i / 2;
                if ((i & 1) == 0) block[b] = (byte) nibble;
                else block[b] |= (byte) (nibble << 4);
            }
            data.writeBytes(block);
        }
        byte[] payload = data.toByteArray();
        ByteBuffer b = ByteBuffer.allocate(12 + 28 + 12 + 8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(b.capacity() - 8).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(20)
                .putShort((short) 0x11).putShort((short) 1).putInt(rate)
                .putInt(rate * BLOCK_ALIGN / SAMPLES_PER_BLOCK).putShort((short) BLOCK_ALIGN).putShort((short) 4)
                .putShort((short) 2).putShort((short) SAMPLES_PER_BLOCK);
        b.put("fact".getBytes(StandardCharsets.US_ASCII)).putInt(4).putInt(pcm.length);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).put(payload);
        return b.array();
    }
}
