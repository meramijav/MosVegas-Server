import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DownmixSounds {
    static final int SFM_READ = 0x10;
    static final int SFM_WRITE = 0x20;
    static final int OGG_VORBIS = 0x200000 | 0x0060;
    static final int CHUNK = 4096;

    static MethodHandle open;
    static MethodHandle read;
    static MethodHandle write;
    static MethodHandle close;
    static MethodHandle error;

    record Audio(float[] samples, int channels, int rate) {
        long frames() {
            return samples.length / channels;
        }
    }

    public static void main(String[] args) throws Throwable {
        if (args.length < 4) {
            System.err.println("usage: DownmixSounds <libsndfile.dll> <sounds dir> <manifest.json> <originals dir>");
            System.exit(2);
        }
        Path sounds = Path.of(args[1]);
        Path manifest = Path.of(args[2]);
        Path originals = Path.of(args[3]);
        Files.createDirectories(originals);
        try (Arena library = Arena.ofShared()) {
            bind(SymbolLookup.libraryLookup(Path.of(args[0]), library));
            String text = Files.readString(manifest, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);
            JsonArray records = JsonParser.parseString(text).getAsJsonArray();
            int converted = 0;
            for (JsonElement element : records) {
                JsonObject entry = element.getAsJsonObject();
                Path path = sounds.resolve(entry.get("event").getAsString() + ".ogg");
                Path original = originals.resolve(path.getFileName());
                Audio audio = decode(path);
                if (audio.channels() > 1) {
                    Files.copy(path, original, StandardCopyOption.REPLACE_EXISTING);
                    entry.addProperty("originalSha256", sha256(path));
                    encode(path, mono(audio), audio.rate());
                    entry.addProperty("processing", "Stereo downmixed to mono for positional playback");
                    audio = decode(path);
                    converted++;
                }
                if (audio.channels() != 1) throw new IllegalStateException(path + " is still not mono");
                entry.addProperty("sha256", sha256(path));
                entry.addProperty("durationSeconds", new BigDecimal((double) audio.frames() / audio.rate()).setScale(3, RoundingMode.HALF_EVEN).doubleValue());
            }
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(records);
            Files.writeString(manifest, json + "\n", StandardCharsets.UTF_8);
            System.out.println("Downmixed " + converted + ", validated " + records.size() + " mono Vorbis sounds");
        }
    }

    static void bind(SymbolLookup lib) {
        Linker linker = Linker.nativeLinker();
        open = linker.downcallHandle(lib.find("sf_wchar_open").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        read = linker.downcallHandle(lib.find("sf_readf_float").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        write = linker.downcallHandle(lib.find("sf_writef_float").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        close = linker.downcallHandle(lib.find("sf_close").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        error = linker.downcallHandle(lib.find("sf_strerror").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    static MemorySegment openFile(Arena arena, Path path, int mode, MemorySegment info) throws Throwable {
        byte[] wide = (path.toAbsolutePath() + "\0").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment name = arena.allocate(wide.length, 2);
        name.copyFrom(MemorySegment.ofArray(wide));
        MemorySegment file = (MemorySegment) open.invokeExact(name, mode, info);
        if (file.address() == 0) {
            MemorySegment message = ((MemorySegment) error.invokeExact(MemorySegment.NULL)).reinterpret(512);
            throw new IOException(path + ": " + message.getUtf8String(0));
        }
        return file;
    }

    static Audio decode(Path path) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(32, 8);
            MemorySegment file = openFile(arena, path, SFM_READ, info);
            int rate = info.get(ValueLayout.JAVA_INT, 8);
            int channels = info.get(ValueLayout.JAVA_INT, 12);
            long frames = info.get(ValueLayout.JAVA_LONG, 0);
            MemorySegment buffer = arena.allocateArray(ValueLayout.JAVA_FLOAT, frames * channels);
            long got = (long) read.invokeExact(file, buffer, frames);
            int closed = (int) close.invokeExact(file);
            float[] samples = new float[(int) (got * channels)];
            MemorySegment.copy(buffer, ValueLayout.JAVA_FLOAT, 0, samples, 0, samples.length);
            return new Audio(samples, channels, rate);
        }
    }

    static float[] mono(Audio audio) {
        float[] out = new float[(int) audio.frames()];
        for (int i = 0; i < out.length; i++) {
            float sum = 0;
            for (int c = 0; c < audio.channels(); c++) sum += audio.samples()[i * audio.channels() + c];
            out[i] = sum / audio.channels();
        }
        return out;
    }

    static void encode(Path path, float[] samples, int rate) throws Throwable {
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(32, 8);
            info.set(ValueLayout.JAVA_INT, 8, rate);
            info.set(ValueLayout.JAVA_INT, 12, 1);
            info.set(ValueLayout.JAVA_INT, 16, OGG_VORBIS);
            MemorySegment file = openFile(arena, temp, SFM_WRITE, info);
            MemorySegment buffer = arena.allocateArray(ValueLayout.JAVA_FLOAT, CHUNK);
            for (int offset = 0; offset < samples.length; offset += CHUNK) {
                int count = Math.min(CHUNK, samples.length - offset);
                MemorySegment.copy(samples, offset, buffer, ValueLayout.JAVA_FLOAT, 0, count);
                long written = (long) write.invokeExact(file, buffer, (long) count);
                if (written != count) throw new IOException("Short write to " + temp);
            }
            int closed = (int) close.invokeExact(file);
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
