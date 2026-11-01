package studio.merami.slimcore.jsondefs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class JSONSounds {
    public double engineVolume = .55;
    public List<EngineLayer> engine = defaultEngine();
    public String start = "slimvehicle:engine_start";
    public String startInside;
    public double startCatch = .2;
    public double startSettle = .8;
    public String stop = "slimvehicle:engine_stop";
    public double spinDown;
    public String horn = "slimvehicle:horn";
    public String siren = "slimvehicle:siren";
    public String radio = "slimvehicle:radio";
    public String gearShift = "slimvehicle:gear_shift";
    public String gearShiftDown;
    public String lock = "slimvehicle:lock";
    public String handbrakeOn = "slimvehicle:handbrake_on";
    public String handbrakeOff = "slimvehicle:handbrake_off";
    public String bump = "slimvehicle:suspension_bump";
    public String collision = "slimvehicle:collision";
    public String blowout = "slimvehicle:tyre_blowout";
    public String exhaustPop = "slimvehicle:exhaust_pop";
    public double exhaustPopVolume = .45;
    public List<Integer> popTicks = new ArrayList<>(List.of(1, 3, 5, 6, 12));
    public String road = "slimvehicle:road_roll";
    public String skid = "slimvehicle:tyre_squeal";
    public String doorOpen = "slimvehicle:door_open";
    public String doorClose = "slimvehicle:door_close";
    public String hoodOpen = "slimvehicle:hood_open";
    public String hoodClose = "slimvehicle:hood_close";
    public String trunkOpen;
    public String trunkClose;
    public String hatchOpen = "slimvehicle:hatch_open";
    public String hatchClose = "slimvehicle:hatch_close";

    public static class EngineLayer {
        public String sound;
        public String inside;
        public double[] fadeIn;
        public double[] fadeOut;
        public double pitchRpm = 3300;
        public double pitchFollow = Double.NaN;

        static EngineLayer of(String sound, double[] fadeIn, double[] fadeOut, double pitchRpm) {
            EngineLayer layer = new EngineLayer();
            layer.sound = sound;
            layer.fadeIn = fadeIn;
            layer.fadeOut = fadeOut;
            layer.pitchRpm = pitchRpm;
            return layer;
        }

        public float blend(float rpm) {
            float in = fadeIn == null ? 1 : ramp(rpm, fadeIn);
            float out = fadeOut == null ? 1 : 1 - ramp(rpm, fadeOut);
            return in * out;
        }

        public float pitch(float rpm) {
            if (Double.isNaN(pitchFollow)) return (float) Math.max(.85, Math.min(1.5, .8 + rpm / pitchRpm * .25));
            return (float) Math.max(.5, Math.min(2, 1 + pitchFollow * (rpm / pitchRpm - 1)));
        }

        private static float ramp(float rpm, double[] range) {
            return (float) Math.max(0, Math.min(1, (rpm - range[0]) / (range[1] - range[0])));
        }
    }

    private static List<EngineLayer> defaultEngine() {
        List<EngineLayer> layers = new ArrayList<>();
        layers.add(EngineLayer.of("slimvehicle:engine_low", null, new double[]{1500, 3300}, 1600));
        layers.add(EngineLayer.of("slimvehicle:engine_mid", new double[]{1500, 3300}, new double[]{3600, 5700}, 3300));
        layers.add(EngineLayer.of("slimvehicle:engine_high", new double[]{3600, 5700}, null, 5400));
        return layers;
    }

    public int catchTicks() {
        return (int) Math.round(startCatch * 20);
    }

    public float startFade(int ticks) {
        double t = ticks / 20.0;
        if (t >= startSettle) return 1;
        double x = Math.max(0, Math.min(1, (t - startCatch) / Math.max(.05, startSettle - startCatch)));
        return (float) (x * x * (3 - 2 * x));
    }

    public static boolean silent(String sound) {
        return sound == null || sound.isEmpty();
    }

    private static String name(UnaryOperator<String> qualify, String sound) {
        return silent(sound) ? sound : qualify.apply(sound);
    }

    void qualify(UnaryOperator<String> qualify) {
        if (!(startCatch >= 0 && startSettle >= startCatch)) throw new IllegalArgumentException("sounds need 0 <= start_catch <= start_settle");
        if (spinDown < 0) throw new IllegalArgumentException("sounds \"spin_down\" can not be negative");
        for (EngineLayer layer : engine) {
            if (silent(layer.sound)) throw new IllegalArgumentException("an engine sound layer has no \"sound\"");
            layer.sound = qualify.apply(layer.sound);
            layer.inside = name(qualify, layer.inside);
        }
        start = name(qualify, start);
        startInside = name(qualify, startInside);
        stop = name(qualify, stop);
        horn = name(qualify, horn);
        siren = name(qualify, siren);
        radio = name(qualify, radio);
        gearShift = name(qualify, gearShift);
        gearShiftDown = gearShiftDown == null ? gearShift : name(qualify, gearShiftDown);
        lock = name(qualify, lock);
        handbrakeOn = name(qualify, handbrakeOn);
        handbrakeOff = name(qualify, handbrakeOff);
        bump = name(qualify, bump);
        collision = name(qualify, collision);
        blowout = name(qualify, blowout);
        exhaustPop = name(qualify, exhaustPop);
        road = name(qualify, road);
        skid = name(qualify, skid);
        doorOpen = name(qualify, doorOpen);
        doorClose = name(qualify, doorClose);
        hoodOpen = name(qualify, hoodOpen);
        hoodClose = name(qualify, hoodClose);
        trunkOpen = trunkOpen == null ? hoodOpen : name(qualify, trunkOpen);
        trunkClose = trunkClose == null ? hoodClose : name(qualify, trunkClose);
        hatchOpen = name(qualify, hatchOpen);
        hatchClose = name(qualify, hatchClose);
    }
}
