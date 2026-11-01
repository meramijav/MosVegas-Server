package studio.merami.slimcore.baseclasses;


public final class PoseReplay {
    public record Pose(double x, double y, double z, float yaw) {}

    public static final double DELAY = 2;
    private static final double MAX_DELAY = 8;
    private static final double TELEPORT = 64;
    private static final double SMOOTH = .1;
    private static final double GAIN = .08;
    private static final double DRIFT = .004;
    private static final int QUIET = 2;
    private static final int SIZE = 16;

    private final int[] ticks = new int[SIZE];
    private final Pose[] poses = new Pose[SIZE];
    private int first;
    private int count;
    private int sinceAdd;
    private double time;
    private double behind;
    private double pace;
    private double base;
    private boolean playing;

    public void clear() {
        first = 0;
        count = 0;
        playing = false;
    }

    public void add(int tick, Pose pose, Pose shown) {
        if (count > 0) {
            int newest = tickAt(count - 1);
            Pose last = poseAt(count - 1);
            if (tick <= newest || distance(last, pose) > TELEPORT) clear();
            else if (tick - newest > 1) push(tick - 1, last);
        }
        if (count == 0) push(tick - 1, distance(shown, pose) > TELEPORT ? pose : shown);
        push(tick, pose);
        sinceAdd = 0;
    }

    public Pose next() {
        if (count == 0) return null;
        int newest = tickAt(count - 1);
        boolean flowing = sinceAdd <= QUIET;
        sinceAdd++;
        if (!playing) {
            time = newest - DELAY;
            behind = DELAY;
            pace = base = 1;
            playing = true;
        } else if (!flowing) {
            time += 1;
        } else {
            time += pace;
            double lag = newest - time;
            if (lag > MAX_DELAY) {
                time = newest - DELAY;
                lag = behind = DELAY;
            }
            if (lag < 0) {
                time = newest;
                lag = 0;
            }
            behind += (lag - behind) * SMOOTH;
            double error = behind - DELAY;
            base = SlimMath.clamp(base + error * DRIFT, .5, 1.5);
            pace = SlimMath.clamp(base + error * GAIN, .25, 2);
        }
        while (count > 2 && tickAt(1) <= time) {
            first = (first + 1) % SIZE;
            count--;
        }
        return sample(time);
    }

    private Pose sample(double at) {
        if (at <= tickAt(0)) return poseAt(0);
        for (int i = 0; i + 1 < count; i++) {
            int from = tickAt(i);
            int to = tickAt(i + 1);
            if (at >= to) continue;
            double f = (at - from) / (to - from);
            Pose a = poseAt(i);
            Pose b = poseAt(i + 1);
            return new Pose(SlimMath.lerp(f, a.x(), b.x()), SlimMath.lerp(f, a.y(), b.y()), SlimMath.lerp(f, a.z(), b.z()),
                    a.yaw() + SlimMath.wrapDegrees(b.yaw() - a.yaw()) * (float) f);
        }
        return poseAt(count - 1);
    }

    private void push(int tick, Pose pose) {
        if (count == SIZE) {
            first = (first + 1) % SIZE;
            count--;
        }
        int i = (first + count) % SIZE;
        ticks[i] = tick;
        poses[i] = pose;
        count++;
    }

    private int tickAt(int i) {
        return ticks[(first + i) % SIZE];
    }

    private Pose poseAt(int i) {
        return poses[(first + i) % SIZE];
    }

    private static double distance(Pose a, Pose b) {
        return Math.sqrt(SlimMath.square(a.x() - b.x()) + SlimMath.square(a.y() - b.y()) + SlimMath.square(a.z() - b.z()));
    }
}
