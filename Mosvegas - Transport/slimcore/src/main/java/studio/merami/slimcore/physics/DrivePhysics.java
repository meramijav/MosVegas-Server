package studio.merami.slimcore.physics;

public final class DrivePhysics {
    public record Gearbox(double[] fractions, double[] ratios) {
        public int gears() {
            return fractions.length - 2;
        }

        public int clamp(int gear) {
            return Math.max(-1, Math.min(gears(), gear));
        }

        public double fraction(int gear) {
            return fractions[clamp(gear) + 1];
        }

        public double ratio(int gear) {
            return ratios[clamp(gear) + 1];
        }

        public double firstRatio() {
            return ratios[2];
        }
    }

    public record Revs(double idle, double atLimit, double max, double free) {}

    public record Tune(double maxSpeed, double acceleration, double brake, double grip, double wheelbase, double fuelPerTick,
                       Gearbox gearbox, Revs revs, double mass, boolean allWheel, boolean automatic) {}

    public record Input(double throttle, double brake, double steer, boolean handbrake) {}

    public record Result(double forward, double lateral, double yawDelta, double rpm, double slip, double fuel) {}

    public record Drift(double yawRate, double loss) {}

    public static final Input IDLE = new Input(0, 0, 0, false);
    public static final double GRAVITY = 9.81 / 400;
    private static final double SPECIFIC_POWER = 44.93 / .0135 / 8000;
    private static final double WEIGHT_MIN = .7;
    private static final double WEIGHT_MAX = 1.4;
    private static final double BRAKE_WEIGHT = -.3;
    private static final double GRIP_WEIGHT = -.12;
    private static final double YAW_WEIGHT = -.3;
    private static final double ROLLING = .013 * GRAVITY;
    private static final double HEADROOM = 1.06;
    private static final double LAUNCH = .3;
    private static final double STALL = .35;
    private static final double CONVERTER = .7;
    private static final double ENGINE_BRAKE = .12;
    private static final double CONVERTER_BRAKE = .05;
    private static final double REAR_SHARE = .55;
    private static final double SCRUB = .2;
    private static final double CORNER_MARGIN = .95;
    private static final double BRAKE_GRIP = 1.15;
    private static final double CORNER_GRIP = 3.4;
    private static final double NEUTRAL_REVS = .75;
    private static final double STALL_REVS = .4;
    private static final double CLUTCH_REVS = .3;
    private static final double REV_RISE = 260;
    private static final double REV_FALL = 380;

    private DrivePhysics() {}

    public static double weight(double mass, double exponent) {
        return Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, Math.pow(Math.max(1e-3, mass), exponent)));
    }

    public static double speedLimit(Tune tune, int gear) {
        return tune.maxSpeed() * tune.gearbox().fraction(gear);
    }

    public static double reachableSpeed(Tune tune, int gear, double power, int punctures) {
        return speedLimit(tune, gear) * (.6 + .4 * Math.max(0, Math.min(1, power))) * Math.pow(.62, Math.max(0, Math.min(4, punctures)));
    }

    public static double friction(Tune tune) {
        return (.55 + tune.grip()) * GRAVITY;
    }

    public static double cornering(Tune tune) {
        return friction(tune) * CORNER_GRIP;
    }

    public static double traction(Tune tune) {
        return friction(tune) * (tune.allWheel() ? 1 : REAR_SHARE);
    }

    public static double torque(double revs) {
        if (revs < .4) return 1.25 * (.72 + .28 * revs / .4);
        if (revs < .65) return 1.25;
        if (revs < 1) return 1.25 - .25 * (revs - .65) / .35;
        return Math.max(0, 1 - (revs - 1) / .2);
    }

    public static double engineForce(Tune tune, int gear, double speed) {
        double top = Math.max(.01, speedLimit(tune, gear));
        double revs = Math.abs(speed) / top;
        double boost = tune.automatic() ? 1 + CONVERTER * Math.max(0, 1 - revs / STALL) : 1;
        return SPECIFIC_POWER * tune.acceleration() * torque(Math.max(revs, LAUNCH)) * boost / top;
    }

    public static double drag(Tune tune) {
        double equilibrium = tune.maxSpeed() * HEADROOM;
        return Math.max(0, (SPECIFIC_POWER * tune.acceleration() / equilibrium - ROLLING) / (equilibrium * equilibrium));
    }

    public static int automaticGear(Tune tune, int gear, double speed, double throttle, double power, int punctures, double up, double down) {
        if (gear < 1) return gear;
        double v = Math.abs(speed);
        if (gear < tune.gearbox().gears() && v >= reachableSpeed(tune, gear, power, punctures) * (throttle > .05 ? up : .99)) return gear + 1;
        double hold = throttle > .9 ? Math.max(down, up - .12) : down;
        if (gear > 1 && v < reachableSpeed(tune, gear - 1, power, punctures) * hold) return gear - 1;
        return gear;
    }

    public static Result step(Tune t, double forward, double lateral, double steerAngle, int gear, boolean running, double fuel, int punctures, Input input) {
        return step(t, forward, lateral, steerAngle, gear, running, fuel, punctures, input, drift(t, forward, steerAngle, input, new Drift(0, 0)));
    }

    public static Drift drift(Tune t, double forward, double steerAngle, Input input, Drift old) {
        double speed = Math.abs(forward);
        double corner = Math.min(1, Math.abs(steerAngle) / .20);
        double target = input.handbrake() && speed > .12 ? .86 * corner : 0;
        double loss = old.loss() + (target - old.loss()) * (target > old.loss() ? .28 : .075);
        if (speed < .04) loss = 0;
        double cap = cornering(t) * weight(t.mass(), GRIP_WEIGHT) * CORNER_MARGIN / Math.max(speed, .05) * (1 + loss * 1.5);
        double desired = Math.max(-cap, Math.min(cap, -Math.tan(steerAngle) * forward / t.wheelbase() * (1 + loss * .42)));
        double yaw = old.yawRate() + (desired - old.yawRate()) * (.34 - .10 * loss) * weight(t.mass(), YAW_WEIGHT);
        return new Drift(Math.max(-.075, Math.min(.075, yaw)), loss);
    }

    public static Result step(Tune t, double forward, double lateral, double steerAngle, int gear, boolean running, double fuel, int punctures, Input input, Drift drift) {
        return step(t, forward, lateral, steerAngle, gear, running, fuel, punctures, input, drift, 1);
    }

    public static Result step(Tune t, double forward, double lateral, double steerAngle, int gear, boolean running, double fuel, int punctures,
                              Input input, Drift drift, double power) {
        gear = t.gearbox().clamp(gear);
        power = Math.max(0, Math.min(1, power));
        punctures = Math.max(0, Math.min(4, punctures));
        double limit = reachableSpeed(t, gear, power, punctures);
        double tyreEfficiency = Math.pow(.62, punctures);
        double throttle = Math.max(0, Math.min(1, input.throttle()));
        boolean live = running && fuel > 0;
        double speed = Math.abs(forward);
        double rpm = engineRpm(t, gear, speed, throttle, live);
        double grip = friction(t) * (1 - .12 * punctures);
        double demand = 0;
        double drive = 0;
        if (live && gear != 0 && (speed > .02 || gear <= 1)) {
            demand = engineForce(t, gear, speed) * throttle;
            drive = Math.min(demand, traction(t) * (1 - .12 * punctures)) * power * tyreEfficiency * Math.signum(gear);
        }
        double brake = Math.min(t.brake() * weight(t.mass(), BRAKE_WEIGHT), grip * BRAKE_GRIP);
        double braking = brake * Math.max(0, Math.min(1, input.brake())) + (input.handbrake() ? brake * .22 : 0);
        if (input.brake() > .05) drive *= 1 - Math.min(1, input.brake());
        double engineBraking = live && gear != 0 && throttle < .05
                ? (t.automatic() ? CONVERTER_BRAKE : ENGINE_BRAKE) * engineForce(t, gear, speed) * Math.min(1, speed / Math.max(.01, speedLimit(t, gear)) / LAUNCH) : 0;
        double resistance = ROLLING + drag(t) * forward * forward + engineBraking + punctures * (.0012 + speed * .013);
        if (Math.signum(forward) == Math.signum(gear)) {
            drive = Math.copySign(Math.min(Math.abs(drive), Math.max(0, (limit - speed) * .45 + resistance)), drive);
        }
        forward = approachZero(forward + drive, resistance + braking);
        double turn = drift.yawRate();
        forward = approachZero(forward, Math.abs(forward * turn) * Math.abs(Math.sin(steerAngle)) * SCRUB);
        double c = Math.cos(turn);
        double s = Math.sin(turn);
        double rotatedForward = forward * c - lateral * s;
        lateral = lateral * c + forward * s;
        forward = rotatedForward;
        double hold = grip * weight(t.mass(), GRIP_WEIGHT) * (1 + (CORNER_GRIP - 1) * (1 - drift.loss())) * Math.max(.1, 1 - drift.loss() * .9);
        lateral = approachZero(lateral, Math.min(Math.abs(lateral) * .7, hold));
        double slip = Math.abs(lateral) + (input.handbrake() ? Math.abs(forward) * .35 : 0) + (input.brake() > .6 ? Math.abs(forward) * .18 : 0);
        double traction = traction(t) * (1 - .12 * punctures);
        if (demand * power > traction * 1.15) slip += Math.min(.25, (demand * power / traction - 1) * .15) * (t.allWheel() ? .3 : 1);
        if (throttle > .8 && Math.abs(forward) < .12 && live && gear != 0 && input.brake() > .5) slip += .18;
        double consumption = running ? t.fuelPerTick() * (.2 + throttle * .8 + rpm / 10000) : 0;
        return new Result(forward, lateral, Math.toDegrees(turn), rpm, slip, Math.max(0, fuel - consumption));
    }

    public static double engineRpm(Tune t, int gear, double speed, double throttle, boolean live) {
        if (!live) return 0;
        Revs revs = t.revs();
        throttle = Math.max(0, Math.min(1, throttle));
        double rpm;
        if (gear == 0) {
            rpm = revs.idle() + throttle * (revs.atLimit() * NEUTRAL_REVS - revs.idle());
        } else {
            double coupled = Math.abs(speed) / Math.max(.01, speedLimit(t, gear)) * revs.atLimit() + (t.automatic() ? throttle * revs.free() : 0);
            double slipping = revs.idle() + throttle * Math.max(0, revs.atLimit() * (t.automatic() ? STALL_REVS : CLUTCH_REVS) - revs.idle());
            rpm = Math.max(coupled, slipping);
        }
        return Math.min(revs.max(), Math.max(revs.idle(), rpm));
    }

    public static double revsToward(double current, double target) {
        if (target <= 0 || current <= 0) return target;
        return current + Math.max(-REV_FALL, Math.min(REV_RISE, target - current));
    }

    private static double approachZero(double x, double amount) {
        return Math.copySign(Math.max(0, Math.abs(x) - amount), x);
    }

    public static float brakePressure(float current, float requested) {
        float target = Math.max(0, Math.min(1, requested));
        return current + Math.max(-.22f, Math.min(.09f, target - current));
    }

    public static boolean canShift(int from, int to, double speed, int gears) {
        if (to < -1 || to > gears) return false;
        if (to > from && to > 1 && Math.abs(speed) < .06) return false;
        return !(to < 0 && speed > .025) && !(to > 0 && speed < -.025);
    }
}
