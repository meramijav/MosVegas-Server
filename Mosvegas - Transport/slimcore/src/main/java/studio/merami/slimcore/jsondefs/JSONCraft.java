package studio.merami.slimcore.jsondefs;

import studio.merami.slimcore.baseclasses.Point3D;

import java.util.ArrayList;
import java.util.List;

public class JSONCraft {
    public static final String HELICOPTER = "helicopter";
    public static final String WATERCRAFT = "watercraft";
    public static final String AXIS_X = "x";
    public static final String AXIS_Y = "y";
    public static final String AXIS_Z = "z";
    public static final String INPUT_STEER = "steer";
    public static final String INPUT_CYCLIC = "cyclic";
    public static final String LIGHT_LIGHTS = "lights";
    public static final String LIGHT_ENGINE = "engine";
    public static final String LIGHT_BEACON = "beacon";
    public static final String LIGHT_STROBE = "strobe";
    public static final int BEACON_PERIOD = 24;
    public static final int BEACON_ON = 4;
    public static final int STROBE_PERIOD = 32;

    public String type;
    public double maxSpeedKmh = 150;
    public double reverseKmh = 12;
    public double acceleration = .006;
    public double brake = .1;
    public double coast = .03;
    public double climb = .3;
    public double yawRate = 3.5;
    public double tilt = .2;
    public double bank = .3;
    public double spool = 6;
    public double draft = .3;
    public double drag = .985;
    public double slip = .88;
    public List<Rotor> rotors = new ArrayList<>();
    public List<Steer> steer = new ArrayList<>();
    public List<Light> lights = new ArrayList<>();
    public Riding riding;

    public static class Rotor {
        public String bone;
        public String axis = AXIS_Y;
        public double rate = 1.4;
    }

    public static class Steer {
        public String bone;
        public String axis = AXIS_Y;
        public double angle = .45;
        public String input = INPUT_STEER;
    }

    public static class Light {
        public String bone;
        public String when = LIGHT_LIGHTS;
    }

    public static class Riding {
        public Point3D grip;
        public Point3D bar;
        public double turn = .45;
        public Point3D foot;
        public Point3D rest;
        public double lean = .2;

        public Point3D grip(boolean left, double share) {
            Point3D arm = (left ? grip : new Point3D(-grip.x, grip.y, grip.z)).subtract(bar);
            double c = Math.cos(share * turn);
            double s = Math.sin(share * turn);
            return new Point3D(arm.x * c + arm.z * s, arm.y, arm.z * c - arm.x * s).add(bar);
        }

        public Point3D foot(boolean left) {
            return left ? foot : new Point3D(-foot.x, foot.y, foot.z);
        }
    }

    public static boolean lit(Light light, boolean lights, boolean engine, long time) {
        return switch (light.when) {
            case LIGHT_ENGINE -> engine;
            case LIGHT_BEACON -> engine && Math.floorMod(time, BEACON_PERIOD) < BEACON_ON;
            case LIGHT_STROBE -> {
                long phase = Math.floorMod(time, STROBE_PERIOD);
                yield lights && (phase < 2 || phase >= 4 && phase < 6);
            }
            default -> lights;
        };
    }

    public boolean helicopter() {
        return HELICOPTER.equals(type);
    }

    public boolean watercraft() {
        return WATERCRAFT.equals(type);
    }

    public double maxSpeed() {
        return maxSpeedKmh / 72;
    }

    public double reverseSpeed() {
        return reverseKmh / 72;
    }

    public void bake() {
        if (!helicopter() && !watercraft()) {
            throw new IllegalArgumentException("craft \"type\" is \"" + HELICOPTER + "\" or \"" + WATERCRAFT + "\", not \"" + type + "\"");
        }
        if (!(maxSpeedKmh > 0) || !(reverseKmh >= 0)) throw new IllegalArgumentException("craft speeds must be positive");
        if (!(acceleration > 0) || !(brake > 0) || !(coast > 0) || !(climb > 0) || !(yawRate > 0) || !(spool > 0)) {
            throw new IllegalArgumentException("craft \"acceleration\", \"brake\", \"coast\", \"climb\", \"yaw_rate\" and \"spool\" must be positive");
        }
        if (!(drag > 0 && drag <= 1) || !(slip >= 0 && slip <= 1)) throw new IllegalArgumentException("craft \"drag\" and \"slip\" are shares from 0 to 1");
        for (Rotor rotor : rotors) {
            if (rotor.bone == null) throw new IllegalArgumentException("craft rotor needs a \"bone\"");
            axis(rotor.axis);
        }
        if (riding != null && (riding.grip == null || riding.bar == null || riding.foot == null || riding.rest == null)) {
            throw new IllegalArgumentException("craft \"riding\" needs \"grip\", \"bar\", \"foot\" and \"rest\"");
        }
        for (Light light : lights) {
            if (light.bone == null) throw new IllegalArgumentException("craft light needs a \"bone\"");
            if (!LIGHT_LIGHTS.equals(light.when) && !LIGHT_ENGINE.equals(light.when) && !LIGHT_BEACON.equals(light.when) && !LIGHT_STROBE.equals(light.when)) {
                throw new IllegalArgumentException("craft light \"when\" is lights, engine, beacon or strobe, not \"" + light.when + "\"");
            }
        }
        for (Steer part : steer) {
            if (part.bone == null) throw new IllegalArgumentException("craft steer part needs a \"bone\"");
            if (!AXIS_X.equals(part.axis) && !AXIS_Y.equals(part.axis) && !AXIS_Z.equals(part.axis)) throw new IllegalArgumentException("craft steer axis is \"x\", \"y\" or \"z\", not \"" + part.axis + "\"");
            if (!INPUT_STEER.equals(part.input) && !INPUT_CYCLIC.equals(part.input)) throw new IllegalArgumentException("craft steer \"input\" is steer or cyclic, not \"" + part.input + "\"");
        }
    }

    private static void axis(String axis) {
        if (!AXIS_X.equals(axis) && !AXIS_Y.equals(axis)) throw new IllegalArgumentException("craft axis is \"x\" or \"y\", not \"" + axis + "\"");
    }
}
