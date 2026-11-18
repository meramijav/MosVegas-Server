package studio.merami.slimcore.physics;

import studio.merami.slimcore.jsondefs.JSONCraft;

public final class CraftPhysics {
    public static final double GRAVITY = .08;
    public static final double TERMINAL = -1.2;
    public static final double HOVER_ROTOR = .92;
    public static final double HOP = .42;
    public static final double HOP_SPEED = .32;
    private static final double AIR_GRAVITY = .065;
    private static final double HULL_SLIDE = .985;
    private static final double SKID_FRICTION = .8;
    private static final double YAW_RESPONSE = .18;
    private static final double TRANSLATIONAL_LIFT = .35;
    public static final double CUSHION_HEIGHT = 3;
    private static final double CUSHION_SINK = .25;
    private static final double AIR_DRAG = .995;
    private static final double VERTICAL_RESPONSE = .05;
    private static final double BUOYANCY = .3;
    private static final double WATER_DAMPING = .62;
    private static final double MAX_RISE = .45;
    private static final double MIN_STEER_SHARE = .2;
    private static final double SPLASH_LOSS = .88;
    private static final double MAX_PITCH = .55;
    private static final double MAX_ROLL = .7;

    private CraftPhysics() {}

    public record Input(double throttle, double brake, double steer, double lift) {
        public static final Input IDLE = new Input(0, 0, 0, 0);
    }

    public record State(double forward, double lateral, double vertical, double rotor, double turn) {
        public State(double forward, double lateral, double vertical, double rotor) {
            this(forward, lateral, vertical, rotor, 0);
        }
    }

    public record Result(double forward, double lateral, double vertical, double yawDelta, double rotor, double pitch, double roll, double load) {}

    public static double spool(JSONCraft craft, double rotor, boolean running) {
        double rate = 1 / (craft.spool * 20);
        return running ? Math.min(1, rotor + rate) : Math.max(0, rotor - rate * .5);
    }

    public static Result flight(JSONCraft craft, State state, Input input, boolean running, boolean grounded, double power) {
        return flight(craft, state, input, running, grounded, power, Double.NaN);
    }

    public static Result flight(JSONCraft craft, State state, Input input, boolean running, boolean grounded, double power, double clearance) {
        double rotor = spool(craft, state.rotor(), running);
        double flying = rotor >= HOVER_ROTOR ? 1 : 0;
        double lift = clamp(input.lift(), -1, 1) * flying;
        double vertical = state.vertical();
        double sink = 1 - rotor * rotor;
        double speedShare = Math.min(1, Math.abs(state.forward()) / craft.maxSpeed());
        double climb = craft.climb * Math.max(.35, power) * (lift > 0 ? 1 + TRANSLATIONAL_LIFT * speedShare : 1);
        double target = flying > 0 ? lift * climb - (1 - power) * .06 : -sink * -TERMINAL;
        if (flying > 0 && target < 0 && !Double.isNaN(clearance) && clearance < CUSHION_HEIGHT) {
            target = Math.max(target, -craft.climb * (CUSHION_SINK + (1 - CUSHION_SINK) * Math.max(0, clearance) / CUSHION_HEIGHT));
        }
        double response = VERTICAL_RESPONSE + GRAVITY * sink;
        vertical = approach(vertical, target, response);
        vertical = Math.max(TERMINAL, vertical);
        if (grounded && vertical < 0) vertical = 0;
        double forward = state.forward();
        double lateral = state.lateral();
        double before = forward;
        boolean airborne = !grounded || vertical > 1e-3;
        double throttle = clamp(input.throttle(), 0, 1);
        double brake = clamp(input.brake(), 0, 1);
        double steer = clamp(input.steer(), -1, 1);
        double wantedTurn = steer * craft.yawRate * rotor * rotor * (airborne ? 1 : .4) * flying;
        double turn = approach(state.turn(), wantedTurn, craft.yawRate * YAW_RESPONSE);
        if (airborne && flying > 0) {
            double max = craft.maxSpeed() * Math.max(.3, power);
            if (throttle > 0) {
                double wanted = throttle * max;
                forward = forward < wanted ? Math.min(wanted, forward + craft.acceleration) : approach(forward, wanted, craft.coast);
            } else if (brake > 0) {
                forward = forward > .02 ? approach(forward, 0, craft.brake * brake) : approach(forward, -craft.reverseSpeed(), craft.acceleration * .6);
            } else {
                forward = approach(forward, 0, craft.coast);
            }
            double[] kept = keepHeading(forward, lateral, -turn);
            forward = kept[0];
            lateral = approach(kept[1] * craft.slip, 0, brake > 0 ? craft.brake * .5 : craft.coast * .3);
        } else if (grounded) {
            forward *= SKID_FRICTION;
            lateral *= SKID_FRICTION;
        } else {
            forward *= craft.drag;
            lateral *= craft.drag;
        }
        double share = clamp(forward / craft.maxSpeed(), -1, 1);
        double delta = forward - before;
        double change = clamp(delta / (delta > 0 ? craft.acceleration : craft.brake), -1, 1);
        double pitch = flying > 0 && airborne ? clamp(share * craft.tilt * .6 + change * craft.tilt * 1.2, -MAX_PITCH, MAX_PITCH) : 0;
        double slide = clamp(lateral / Math.max(.2, craft.maxSpeed() * .3), -1, 1);
        double roll = flying > 0 && airborne ? clamp(-steer * craft.bank * (.4 + .6 * Math.abs(share)) - slide * craft.bank * .5, -MAX_ROLL, MAX_ROLL) : 0;
        double load = rotor * (.55 + .25 * Math.abs(lift) + .2 * Math.abs(share));
        return new Result(forward, lateral, vertical, -turn, rotor, pitch, roll, load);
    }

    public static Result water(JSONCraft craft, State state, Input input, boolean running, double depth, boolean grounded, boolean hop, double power) {
        boolean afloat = !Double.isNaN(depth) && depth > -.05;
        boolean airborne = !afloat && !grounded;
        double vertical = state.vertical();
        double forward = state.forward();
        double lateral = state.lateral();
        if (afloat) {
            if (vertical < -.3) {
                forward *= SPLASH_LOSS;
                lateral *= SPLASH_LOSS;
            }
            vertical = Math.min(MAX_RISE, (vertical + depth * BUOYANCY) * WATER_DAMPING);
            if (hop && running) vertical = HOP + HOP_SPEED * Math.min(1, Math.abs(forward) / craft.maxSpeed());
        } else {
            vertical = Math.max(TERMINAL, vertical - (grounded ? GRAVITY : AIR_GRAVITY));
        }
        double throttle = running ? clamp(input.throttle(), 0, 1) : 0;
        double brake = clamp(input.brake(), 0, 1);
        double steer = clamp(input.steer(), -1, 1);
        double turn = 0;
        if (afloat) {
            double max = craft.maxSpeed() * Math.max(.3, power);
            if (throttle > 0) {
                double wanted = throttle * max;
                forward = forward < wanted ? Math.min(wanted, forward * craft.drag + craft.acceleration) : approach(forward, wanted, craft.coast);
            } else if (brake > 0) {
                forward = forward > .02 ? approach(forward, 0, craft.brake * brake) : approach(forward, running ? -craft.reverseSpeed() : 0, craft.acceleration * .5);
            } else {
                forward = approach(forward * craft.drag, 0, craft.coast);
            }
            double share = Math.abs(forward) / craft.maxSpeed();
            double grip = MIN_STEER_SHARE + (1 - MIN_STEER_SHARE) * Math.min(1, share * 3 + throttle * .4);
            turn = steer * craft.yawRate * grip * Math.signum(forward == 0 ? 1 : forward);
            double[] kept = keepHeading(forward, lateral, -turn);
            forward = kept[0];
            lateral = kept[1] * craft.slip;
        } else if (grounded) {
            forward *= HULL_SLIDE;
            lateral *= HULL_SLIDE * .9;
        } else {
            forward *= AIR_DRAG;
            lateral *= AIR_DRAG;
            turn = steer * craft.yawRate * .25;
        }
        double share = clamp(forward / craft.maxSpeed(), -1, 1);
        double pitch;
        if (airborne) pitch = clamp(-Math.atan2(vertical, Math.max(.3, Math.abs(forward))) * .8, -MAX_PITCH, MAX_PITCH);
        else pitch = afloat ? -Math.abs(share) * craft.tilt - (throttle - brake) * craft.tilt * .25 : 0;
        double roll = afloat ? -steer * craft.bank * Math.min(1, Math.abs(share) * 2) : airborne ? -steer * craft.bank * .5 : 0;
        double load = running ? (airborne ? .25 + .75 * throttle : .25 + .75 * Math.max(throttle, Math.abs(share))) : 0;
        return new Result(forward, lateral, vertical, -turn, 0, pitch, roll, load);
    }

    static double[] keepHeading(double forward, double lateral, double yawDelta) {
        double angle = Math.toRadians(yawDelta);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{forward * c - lateral * s, lateral * c + forward * s};
    }

    public static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
