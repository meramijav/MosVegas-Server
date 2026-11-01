package studio.merami.slimcore.physics;

public final class Suspension {
    private static final double SWAY_WEIGHT = .35;
    private static final double SPRING_WEIGHT = -.35;
    private static final double REFERENCE_HEIGHT = 1.95;
    public double pitch;
    public double roll;
    public double sway = 1;
    public double spring = .24;
    private double pitchVelocity;
    private double rollVelocity;

    public void step(double acceleration, double lateralAcceleration, boolean grounded) {
        double p = grounded ? clamp(-acceleration * 1.2 * sway, -.025 * sway, .025 * sway) : 0;
        double r = grounded ? clamp(lateralAcceleration * 1.8 * sway, -.045 * sway, .045 * sway) : 0;
        pitchVelocity = (pitchVelocity + (p - pitch) * spring) * .48;
        rollVelocity = (rollVelocity + (r - roll) * spring) * .48;
        pitch = clamp(pitch + pitchVelocity, -.03 * sway, .03 * sway);
        roll = clamp(roll + rollVelocity, -.05 * sway, .05 * sway);
    }

    public void carry(double mass, double height) {
        sway = DrivePhysics.weight(mass, SWAY_WEIGHT) * Math.max(.8, Math.min(1.3, Math.sqrt(height / REFERENCE_HEIGHT)));
        spring = .24 * DrivePhysics.weight(mass, SPRING_WEIGHT);
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
