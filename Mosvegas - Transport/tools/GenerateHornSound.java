import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class GenerateHornSound {
    static final int SFM_WRITE = 0x20;
    static final int OGG_VORBIS = 0x200000 | 0x0060;
    static final int CHUNK = 4096;
    static final int RATE = 44100;
    static final double LOOP = .5;
    static final double LOW = 410;
    static final double HIGH = 512;
    static final double[][] FORMANTS = {{1850, 650, 1}, {3300, 900, .55}, {5200, 1400, .2}};

    public static void main(String[] args) throws Throwable {
        if (args.length < 2) {
            System.err.println("usage: GenerateHornSound <libsndfile.dll> <out.ogg> [low Hz] [high Hz]");
            System.exit(2);
        }
        double low = args.length > 3 ? Double.parseDouble(args[2]) : LOW;
        double high = args.length > 3 ? Double.parseDouble(args[3]) : HIGH;
        for (double tone : new double[]{low, high}) {
            if (Math.abs(tone * LOOP - Math.rint(tone * LOOP)) > 1e-9) throw new IllegalArgumentException(tone + " Hz does not fit a whole number of cycles in the loop");
        }
        float[] samples = horn(low, high);
        try (Arena library = Arena.ofShared()) {
            SymbolLookup lib = SymbolLookup.libraryLookup(Path.of(args[0]), library);
            encode(lib, Path.of(args[1]), samples);
        }
        System.out.println("Horn loop: " + samples.length + " samples at " + RATE + " Hz");
    }

    static float[] horn(double low, double high) {
        int length = (int) Math.round(RATE * LOOP);
        double[] mix = new double[length];
        double peak = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) RATE;
            double x = .55 * tone(low, t, 11) + .45 * tone(high, t, 29);
            x = Math.tanh(1.8 * x);
            mix[i] = x;
            peak = Math.max(peak, Math.abs(x));
        }
        float[] out = new float[length];
        for (int i = 0; i < length; i++) out[i] = (float) (mix[i] / peak * .85);
        return out;
    }

    static double tone(double frequency, double t, int seed) {
        double sum = 0;
        double norm = 0;
        for (int k = 1; k * frequency < 9000; k++) {
            double f = k * frequency;
            double gain = Math.pow(k, -.85) * (.35 + resonance(f));
            double phase = fract(Math.sin(k * 12.9898 + seed * 78.233) * 43758.5453) * Math.PI * 2;
            sum += gain * Math.sin(2 * Math.PI * f * t + phase);
            norm += gain;
        }
        return sum / norm * 2.2;
    }

    static double resonance(double f) {
        double r = 0;
        for (double[] formant : FORMANTS) {
            double d = (f - formant[0]) / formant[1];
            r += formant[2] * Math.exp(-d * d);
        }
        return r;
    }

    static double fract(double v) {
        return v - Math.floor(v);
    }

    static void encode(SymbolLookup lib, Path path, float[] samples) throws Throwable {
        Linker linker = Linker.nativeLinker();
        MethodHandle open = linker.downcallHandle(lib.find("sf_wchar_open").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        MethodHandle write = linker.downcallHandle(lib.find("sf_writef_float").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        MethodHandle close = linker.downcallHandle(lib.find("sf_close").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(32, 8);
            info.set(ValueLayout.JAVA_INT, 8, RATE);
            info.set(ValueLayout.JAVA_INT, 12, 1);
            info.set(ValueLayout.JAVA_INT, 16, OGG_VORBIS);
            byte[] wide = (temp.toAbsolutePath() + "\0").getBytes(StandardCharsets.UTF_16LE);
            MemorySegment name = arena.allocate(wide.length, 2);
            name.copyFrom(MemorySegment.ofArray(wide));
            MemorySegment file = (MemorySegment) open.invokeExact(name, SFM_WRITE, info);
            if (file.address() == 0) throw new IOException("Cannot write " + temp);
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
}
