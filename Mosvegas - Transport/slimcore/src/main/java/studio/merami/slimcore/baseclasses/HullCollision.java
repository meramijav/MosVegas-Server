package studio.merami.slimcore.baseclasses;

import studio.merami.slimcore.entities.instances.AEntityVehicleC_Colliding;

import java.util.ArrayList;
import java.util.List;

public final class HullCollision {
    public record Other(AEntityVehicleC_Colliding vehicle, Hull hull) {}

    public record World(List<BoundingBox> blocks, List<Other> vehicles) {}

    public record Touch(int index, Hull.Box obstacle, BoundingBox block, Other other) {}

    public record Contact(double time, Point3D normal, Point3D point, Hull.Box box, Hull.Box obstacle, BoundingBox block, Other other) {}

    private static final Point3D X = new Point3D(1, 0, 0);
    private static final Point3D Y = new Point3D(0, 1, 0);
    private static final Point3D Z = new Point3D(0, 0, 1);
    private static final double SAME_TIME = 1e-4;
    private static final double DEEPER = 1e-4;

    private HullCollision() {}

    public static boolean blocked(Hull before, Point3D from, Hull after, Point3D to, World world) {
        return scan(before, from, after, to, world, null);
    }

    public static List<Touch> touches(Hull before, Point3D from, Hull after, Point3D to, World world) {
        List<Touch> out = new ArrayList<>();
        scan(before, from, after, to, world, out);
        return out;
    }

    private static boolean scan(Hull before, Point3D from, Hull after, Point3D to, World world, List<Touch> out) {
        BoundingBox reach = after.bounds().move(to);
        Point3D back = to.reverse();
        for (BoundingBox block : world.blocks()) {
            if (!reach.intersects(block)) continue;
            BoundingBox local = block.move(back);
            Hull.Box obstacle = null;
            for (int i = 0; i < after.boxes().size(); i++) {
                if (!after.boxes().get(i).intersects(local)) continue;
                if (obstacle == null) obstacle = Hull.Box.of(block);
                if (!deepens(before, i, from, after.boxes().get(i), to, obstacle)) continue;
                if (out == null) return true;
                out.add(new Touch(i, obstacle, block, null));
            }
        }
        for (Other other : world.vehicles()) {
            if (!reach.intersects(other.hull().bounds())) continue;
            for (Hull.Box obstacle : other.hull().boxes()) {
                if (!reach.intersects(obstacle.bounds)) continue;
                Hull.Box local = obstacle.move(back);
                for (int i = 0; i < after.boxes().size(); i++) {
                    if (!after.boxes().get(i).intersects(local)) continue;
                    if (!deepens(before, i, from, after.boxes().get(i), to, obstacle)) continue;
                    if (out == null) return true;
                    out.add(new Touch(i, obstacle, null, other));
                }
            }
        }
        return out != null && !out.isEmpty();
    }

    private static boolean deepens(Hull before, int index, Point3D from, Hull.Box after, Point3D to, Hull.Box obstacle) {
        if (index >= before.boxes().size()) return true;
        Hull.Box previous = before.boxes().get(index);
        if (!previous.move(from).intersects(obstacle)) return true;
        return depth(after, to, obstacle) > depth(previous, from, obstacle) + DEEPER;
    }

    private static double depth(Hull.Box box, Point3D offset, Hull.Box obstacle) {
        double depth = -gap(box, offset, obstacle, Y);
        for (Point3D axis : axes(box, obstacle)) depth = Math.min(depth, -gap(box, offset, obstacle, axis));
        return depth;
    }

    public static Contact contact(Hull hull, Point3D from, Point3D to, World world) {
        Contact first = null;
        Point3D normals = Point3D.ZERO;
        Point3D points = Point3D.ZERO;
        int same = 0;
        for (Touch touch : touches(hull, from, hull, to, world)) {
            Hull.Box box = hull.boxes().get(touch.index());
            Hull.Box obstacle = touch.obstacle();
            boolean inside = box.move(from).intersects(obstacle);
            double time = 0;
            double least = Double.NEGATIVE_INFINITY;
            Point3D axis = null;
            boolean apart = gap(box, to, obstacle, Y) >= 0;
            for (Point3D a : axes(box, obstacle)) {
                double g0 = gap(box, from, obstacle, a);
                double g1 = gap(box, to, obstacle, a);
                if (g1 >= 0) {
                    apart = true;
                    break;
                }
                if (inside) {
                    if (g1 > least) {
                        least = g1;
                        axis = a;
                    }
                } else if (g0 > 0) {
                    double t = g0 / (g0 - g1);
                    if (axis == null || t > time) {
                        time = t;
                        axis = a;
                    }
                }
            }
            if (apart || axis == null) continue;
            Point3D at = from.add(to.subtract(from).scale(time));
            Point3D normal = box.center.add(at).subtract(obstacle.center).dot(axis) < 0 ? axis.reverse() : axis;
            Point3D point = closest(box, at, obstacle.center);
            if (first == null || time < first.time() - SAME_TIME) {
                first = new Contact(time, normal, point, box, obstacle, touch.block(), touch.other());
                normals = normal;
                points = point;
                same = 1;
            } else if (time <= first.time() + SAME_TIME) {
                normals = normals.add(normal);
                points = points.add(point);
                same++;
            }
        }
        if (first == null || same == 1) return first;
        Point3D normal = normals.lengthSqr() > 1e-6 ? normals.normalize() : first.normal();
        return new Contact(first.time(), normal, points.scale(1.0 / same), first.box(), first.obstacle(), first.block(), first.other());
    }

    public static Point3D separate(Hull before, Hull after, World world, double max) {
        Point3D push = Point3D.ZERO;
        for (int round = 0; round < 4; round++) {
            List<Touch> touches = touches(before, Point3D.ZERO, after, push, world);
            if (touches.isEmpty()) return push;
            Point3D move = null;
            double deepest = 0;
            for (Touch touch : touches) {
                Hull.Box box = after.boxes().get(touch.index());
                Hull.Box obstacle = touch.obstacle();
                double least = Double.MAX_VALUE;
                Point3D out = null;
                for (Point3D a : axes(box, obstacle)) {
                    double overlap = -gap(box, push, obstacle, a);
                    if (overlap < least) {
                        least = overlap;
                        out = a;
                    }
                }
                if (out == null || least <= 0 || least <= deepest) continue;
                deepest = least;
                move = (box.center.add(push).subtract(obstacle.center).dot(out) < 0 ? out.reverse() : out).scale(least + .002);
            }
            if (move == null) return null;
            push = push.add(move);
            if (push.length() > max) return null;
        }
        return blocked(before, Point3D.ZERO, after, push, world) ? null : push;
    }

    private static double gap(Hull.Box box, Point3D offset, Hull.Box obstacle, Point3D axis) {
        Point3D d = obstacle.center.subtract(box.center).subtract(offset);
        return Math.abs(d.dot(axis)) - box.radius(axis) - obstacle.radius(axis);
    }

    private static List<Point3D> axes(Hull.Box box, Hull.Box obstacle) {
        List<Point3D> axes = new ArrayList<>(8);
        axes.add(X);
        axes.add(Z);
        for (Point3D a : box.axes) flat(a, axes);
        for (Point3D a : obstacle.axes) flat(a, axes);
        return axes;
    }

    private static void flat(Point3D axis, List<Point3D> out) {
        double length = Math.hypot(axis.x, axis.z);
        if (length < .5) return;
        Point3D flat = new Point3D(axis.x / length, 0, axis.z / length);
        for (Point3D a : out) if (Math.abs(a.dot(flat)) > .9999) return;
        out.add(flat);
    }

    private static Point3D closest(Hull.Box box, Point3D offset, Point3D target) {
        Point3D center = box.center.add(offset);
        Point3D d = target.subtract(center);
        Point3D p = center;
        double[] half = {box.half.x, box.half.y, box.half.z};
        for (int i = 0; i < 3; i++) p = p.add(box.axes[i].scale(SlimMath.clamp(d.dot(box.axes[i]), -half[i], half[i])));
        return p;
    }
}
