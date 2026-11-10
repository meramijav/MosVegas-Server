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
import java.util.Random;

public class GenerateElectricSounds {
    static final int SFM_WRITE = 0x20;
    static final int OGG_VORBIS = 0x200000 | 0x0060;
    static final int CHUNK = 4096;
    static final int RATE = 44100;
    static final double[] BELL = {1, 2.0, 3.0, 4.2};
    static final double[] BELL_GAIN = {1, .32, .12, .05};
    static final double[] BELL_DECAY = {.55, .3, .18, .1};

    public static void main(String[] args) throws Throwable {
        if (args.length < 2) {
            System.err.println("usage: GenerateElectricSounds <libsndfile.dll> <sounds dir> [prefix]");
            System.exit(2);
        }
        Path dir = Path.of(args[1]);
        String prefix = args.length > 2 ? args[2] : "l9";
        try (Arena library = Arena.ofShared()) {
            SymbolLookup lib = SymbolLookup.libraryLookup(Path.of(args[0]), library);
            write(lib, dir, prefix + "_motor_hum", hum());
            write(lib, dir, prefix + "_motor_low", motor(400, 1100, .12, .55, 7));
            write(lib, dir, prefix + "_motor_high", motor(930, 2560, .22, .65, 13));
            write(lib, dir, prefix + "_start", start());
            write(lib, dir, prefix + "_stop", stop());
            write(lib, dir, prefix + "_charge", charge());
        }
    }

    static void write(SymbolLookup lib, Path dir, String name, float[] samples) throws Throwable {
        encode(lib, dir.resolve(name + ".ogg"), samples);
        System.out.printf("%s: %.2f s%n", name, samples.length / (double) RATE);
    }

    static float[] hum() {
        double[] mix = new double[RATE];
        tone(mix, 0, 1, 120, .5, 0, 0);
        tone(mix, 0, 1, 240, .22, 0, 0);
        tone(mix, 0, 1, 360, .08, 0, 0);
        tone(mix, 0, 1, 500, .3, 2, .5);
        tone(mix, 0, 1, 750, .18, 2, .5);
        tone(mix, 0, 1, 1003, .06, 3, .4);
        hiss(mix, 80, 900, .1, 3);
        return normalize(mix, .3);
    }

    static float[] motor(int base, int gear, double hiss, double peak, int seed) {
        double[] mix = new double[RATE];
        double[] harmonics = {1, .4, .18, .08};
        for (int k = 0; k < harmonics.length; k++) tone(mix, 0, 1, base * (k + 1), harmonics[k], 6, .08);
        tone(mix, 0, 1, gear, .45, 0, 0);
        tone(mix, 0, 1, gear + 3, .12, 0, 0);
        tone(mix, 0, 1, gear * 2, .12, 0, 0);
        hiss(mix, 200, 6000, hiss, seed);
        return normalize(mix, peak);
    }

    static float[] start() {
        double[] mix = new double[(int) (RATE * 1.5)];
        clunk(mix, 0);
        sweep(mix, .05, .9, 220, 660, .1);
        bell(mix, .25, 1047, .5);
        bell(mix, .45, 1568, .45);
        return normalize(mix, .8);
    }

    static float[] stop() {
        double[] mix = new double[(int) (RATE * 1.3)];
        bell(mix, 0, 1568, .45);
        bell(mix, .2, 1047, .5);
        sweep(mix, .05, .85, 600, 150, .1);
        clunk(mix, .9);
        return normalize(mix, .75);
    }

    static float[] charge() {
        double[] mix = new double[(int) (RATE * 1.1)];
        click(mix, 0, .6);
        click(mix, .06, .4);
        beep(mix, .25, .08, 1319, .35);
        beep(mix, .4, .08, 1319, .35);
        beep(mix, .55, .15, 1760, .35);
        clunk(mix, .8);
        return normalize(mix, .75);
    }

    static void tone(double[] mix, double from, double length, double frequency, double gain, double wobble, double depth) {
        int start = (int) (from * RATE);
        int end = Math.min(mix.length, start + (int) (length * RATE));
        for (int i = start; i < end; i++) {
            double t = (i - start) / (double) RATE;
            double am = 1 + depth * Math.sin(2 * Math.PI * wobble * t);
            mix[i] += gain * am * Math.sin(2 * Math.PI * frequency * t);
        }
    }

    static void hiss(double[] mix, int low, int high, double gain, int seed) {
        Random random = new Random(seed);
        double norm = 0;
        double[] noise = new double[mix.length];
        for (int f = low; f <= high; f += 3) {
            double shape = Math.exp(-Math.pow(Math.log(f / Math.sqrt(low * (double) high)) / 1.1, 2));
            double phase = random.nextDouble() * 2 * Math.PI;
            double step = 2 * Math.PI * f / RATE;
            for (int i = 0; i < noise.length; i++) noise[i] += shape * Math.sin(step * i + phase);
            norm += shape * shape;
        }
        double scale = gain / Math.sqrt(Math.max(1e-9, norm / 2));
        for (int i = 0; i < mix.length; i++) mix[i] += noise[i] * scale;
    }

    static void bell(double[] mix, double at, double frequency, double gain) {
        int start = (int) (at * RATE);
        for (int i = start; i < mix.length; i++) {
            double t = (i - start) / (double) RATE;
            double attack = Math.min(1, t / .005);
            double sum = 0;
            for (int k = 0; k < BELL.length; k++) sum += BELL_GAIN[k] * Math.exp(-t / BELL_DECAY[k]) * Math.sin(2 * Math.PI * frequency * BELL[k] * t);
            mix[i] += gain * attack * sum;
        }
    }

    static void beep(double[] mix, double at, double length, double frequency, double gain) {
        int start = (int) (at * RATE);
        int end = Math.min(mix.length, start + (int) (length * RATE));
        for (int i = start; i < end; i++) {
            double t = (i - start) / (double) RATE;
            double edge = Math.min(1, Math.min(t, length - t) / .006);
            mix[i] += gain * edge * (Math.sin(2 * Math.PI * frequency * t) + .2 * Math.sin(4 * Math.PI * frequency * t));
        }
    }

    static void sweep(double[] mix, double from, double to, double startFrequency, double endFrequency, double gain) {
        int start = (int) (from * RATE);
        int end = Math.min(mix.length, (int) (to * RATE));
        double phase = 0;
        for (int i = start; i < end; i++) {
            double x = (i - start) / (double) (end - start);
            double frequency = startFrequency * Math.pow(endFrequency / startFrequency, x);
            phase += 2 * Math.PI * frequency / RATE;
            double envelope = Math.sin(Math.PI * x);
            mix[i] += gain * envelope * (Math.sin(phase) + .3 * Math.sin(2.75 * phase));
        }
    }

    static void clunk(double[] mix, double at) {
        int start = (int) (at * RATE);
        Random random = new Random(start);
        for (int i = start; i < mix.length; i++) {
            double t = (i - start) / (double) RATE;
            mix[i] += .7 * Math.exp(-t / .03) * Math.sin(2 * Math.PI * 70 * t) + .35 * Math.exp(-t / .003) * (random.nextDouble() * 2 - 1);
        }
    }

    static void click(double[] mix, double at, double gain) {
        int start = (int) (at * RATE);
        Random random = new Random(start + 1);
        for (int i = start; i < Math.min(mix.length, start + RATE / 20); i++) {
            double t = (i - start) / (double) RATE;
            mix[i] += gain * Math.exp(-t / .0025) * ((random.nextDouble() * 2 - 1) + Math.sin(2 * Math.PI * 2400 * t));
        }
    }

    static float[] normalize(double[] mix, double peak) {
        double max = 1e-9;
        for (double v : mix) max = Math.max(max, Math.abs(v));
        float[] out = new float[mix.length];
        for (int i = 0; i < mix.length; i++) out[i] = (float) (mix[i] / max * peak);
        return out;
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
