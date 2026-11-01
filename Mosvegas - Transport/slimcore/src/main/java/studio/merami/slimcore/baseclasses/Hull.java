package studio.merami.slimcore.baseclasses;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import studio.merami.slimcore.entities.instances.APart;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Hull {
    public record Hit(Box box, Point3D point, double distance) {}

    public record Frame(Point3D center, Point3D ex, Point3D ez, double width, double length) {
        Frame move(Point3D offset) {
            return new Frame(center.add(offset), ex, ez, width, length);
        }
    }

    public static final class Box {
        private static final double SLICE = .45;
        private static final double FINE_SLICE = .1;
        private static final double THIN = .3;
        private static final Point3D[] WORLD_AXES = {new Point3D(1, 0, 0), new Point3D(0, 1, 0), new Point3D(0, 0, 1)};

        public final Point3D center;
        public final Point3D half;
        public final Point3D[] axes;
        public final APart part;
        public final int glass;
        public final BoundingBox bounds;
        private List<BoundingBox> slices;

        public Box(Point3D center, Point3D half, Point3D[] axes, APart part, int glass) {
            this.center = center;
            this.half = half;
            this.axes = axes;
            this.part = part;
            this.glass = glass;
            double x = radius(new Point3D(1, 0, 0));
            double y = radius(new Point3D(0, 1, 0));
            double z = radius(new Point3D(0, 0, 1));
            bounds = new BoundingBox(center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
        }

        public static Box of(BoundingBox box) {
            return new Box(box.center(), new Point3D(box.sizeX() / 2, box.sizeY() / 2, box.sizeZ() / 2), WORLD_AXES, null, -1);
        }

        public double radius(Point3D axis) {
            return Math.abs(axis.dot(axes[0])) * half.x + Math.abs(axis.dot(axes[1])) * half.y + Math.abs(axis.dot(axes[2])) * half.z;
        }

        public Box move(Point3D delta) {
            return new Box(center.add(delta), half, axes, part, glass);
        }

        public Box grow(double amount) {
            return new Box(center, half.add(amount, amount, amount), axes, part, glass);
        }

        public Point3D corner(int i) {
            return center.add(axes[0].scale((i & 1) == 0 ? -half.x : half.x))
                    .add(axes[1].scale((i & 2) == 0 ? -half.y : half.y))
                    .add(axes[2].scale((i & 4) == 0 ? -half.z : half.z));
        }

        public Optional<Point3D> clip(Point3D start, Point3D end) {
            Point3D a = local(start);
            Point3D b = local(end);
            BoundingBox box = new BoundingBox(-half.x, -half.y, -half.z, half.x, half.y, half.z);
            if (box.contains(a)) return Optional.of(start);
            return box.clip(a, b).map(p -> start.lerp(end, a.distanceTo(p) / Math.max(1e-9, a.distanceTo(b))));
        }

        private Point3D local(Point3D point) {
            Point3D d = point.subtract(center);
            return new Point3D(d.dot(axes[0]), d.dot(axes[1]), d.dot(axes[2]));
        }

        public boolean intersects(BoundingBox other) {
            return bounds.intersects(other) && intersects(of(other));
        }

        public boolean intersects(Box other) {
            if (!bounds.intersects(other.bounds)) return false;
            Point3D d = other.center.subtract(center);
            for (Point3D a : axes) if (separated(other, d, a)) return false;
            for (Point3D a : other.axes) if (separated(other, d, a)) return false;
            for (Point3D a : axes) {
                for (Point3D b : other.axes) if (separated(other, d, a.cross(b))) return false;
            }
            return true;
        }

        private boolean separated(Box other, Point3D delta, Point3D axis) {
            double length = axis.length();
            return length > 1e-8 && Math.abs(delta.dot(axis)) >= radius(axis) + other.radius(axis) - 1e-5 * length;
        }

        public Point3D escape(BoundingBox other) {
            Point3D h = new Point3D(other.sizeX() / 2, other.sizeY() / 2, other.sizeZ() / 2);
            Point3D d = other.center().subtract(center);
            List<Point3D> ways = new ArrayList<>(List.of(new Point3D(1, 0, 0), new Point3D(0, 0, 1)));
            for (int i = 0; i < 3; i += 2) {
                Point3D flat = new Point3D(axes[i].x, 0, axes[i].z);
                if (flat.lengthSqr() > .01) ways.add(flat.normalize());
            }
            Point3D best = null;
            double least = Double.MAX_VALUE;
            for (Point3D way : ways) {
                double side = d.dot(way);
                double depth = radius(way) + Math.abs(way.x) * h.x + Math.abs(way.z) * h.z - Math.abs(side);
                if (depth < least) {
                    least = depth;
                    best = way.scale(side < 0 ? -depth : depth);
                }
            }
            double up = center.y + radius(new Point3D(0, 1, 0)) - other.minY;
            return up < least ? new Point3D(0, up, 0) : best;
        }

        public void collisionSlices(BoundingBox query, List<BoundingBox> out) {
            if (!bounds.intersects(query)) return;
            if (slices != null) {
                for (BoundingBox slice : slices) if (slice.intersects(query)) out.add(slice);
                return;
            }
            slices = new ArrayList<>();
            double[] sizes = {half.x * 2, half.y * 2, half.z * 2};
            double[] aligned = new double[3];
            for (int i = 0; i < 3; i++) {
                Point3D a = axes[i];
                aligned[i] = Math.max(Math.abs(a.x), Math.max(Math.abs(a.y), Math.abs(a.z)));
            }
            int[] count = {1, 1, 1};
            for (int i = 0; i < 3; i++) {
                if (aligned[i] >= .9999) continue;
                double step = SLICE;
                if (aligned[i] < .98) {
                    for (int j = 0; j < 3; j++) {
                        if (j != i && aligned[j] < .9999 && sizes[j] < THIN) step = Math.min(step, Math.max(FINE_SLICE, sizes[j] * .75));
                    }
                }
                count[i] = Math.max(1, (int) Math.ceil(sizes[i] / step));
            }
            Point3D h = new Point3D(half.x / count[0], half.y / count[1], half.z / count[2]);
            for (int x = 0; x < count[0]; x++) {
                for (int y = 0; y < count[1]; y++) {
                    for (int z = 0; z < count[2]; z++) {
                        Point3D c = center.add(axes[0].scale(-half.x + h.x * (2 * x + 1)))
                                .add(axes[1].scale(-half.y + h.y * (2 * y + 1)))
                                .add(axes[2].scale(-half.z + h.z * (2 * z + 1)));
                        BoundingBox slice = new Box(c, h, axes, part, glass).bounds;
                        slices.add(slice);
                        if (slice.intersects(query)) out.add(slice);
                    }
                }
            }
        }
    }

    private final List<Box> boxes;
    private final List<Box> controls;
    private final Frame frame;
    private final BoundingBox bounds;
    private List<Box> entityBoxes;

    public Hull(List<Box> boxes, List<Box> controls, Frame frame) {
        this.boxes = List.copyOf(boxes);
        this.controls = List.copyOf(controls);
        this.frame = frame;
        BoundingBox total = boxes.get(0).bounds;
        for (Box box : boxes) total = total.minmax(box.bounds);
        bounds = total;
    }

    public List<Box> boxes() {
        return boxes;
    }

    public List<Box> controls() {
        return controls;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public Hull move(Point3D offset) {
        return new Hull(boxes.stream().map(b -> b.move(offset)).toList(), controls.stream().map(b -> b.move(offset)).toList(), frame.move(offset));
    }

    public List<Box> entityBoxes() {
        if (entityBoxes == null) {
            List<Box> out = new ArrayList<>();
            for (Box box : boxes) {
                Box flush = flush(box, frame);
                if (flush != null) out.add(flush);
            }
            entityBoxes = List.copyOf(out);
        }
        return entityBoxes;
    }

    public boolean intersectsEntity(BoundingBox box) {
        return bounds.intersects(box) && entityBoxes().stream().anyMatch(b -> b.intersects(box));
    }

    private static Box flush(Box box, Frame frame) {
        Point3D[] frameAxes = {frame.ex(), new Point3D(0, 1, 0), frame.ez()};
        double[] limit = {frame.width(), 0, frame.length()};
        int[] along = new int[3];
        for (int i = 0; i < 3; i++) {
            along[i] = -1;
            for (int j = 0; j < 3; j++) if (Math.abs(box.axes[i].dot(frameAxes[j])) > .9999) along[i] = j;
            if (along[i] < 0) return box;
        }
        Point3D d = box.center.subtract(frame.center());
        Point3D center = box.center;
        double[] half = {box.half.x, box.half.y, box.half.z};
        for (int i = 0; i < 3; i++) {
            int j = along[i];
            if (limit[j] <= 0) continue;
            double c = d.dot(frameAxes[j]);
            double lo = Math.max(-limit[j], c - half[i]);
            double hi = Math.min(limit[j], c + half[i]);
            if (hi - lo < 1e-4) return null;
            center = center.add(frameAxes[j].scale((lo + hi) / 2 - c));
            half[i] = (hi - lo) / 2;
        }
        return new Box(center, new Point3D(half[0], half[1], half[2]), box.axes, box.part, box.glass);
    }

    public boolean intersects(BoundingBox box) {
        return bounds.intersects(box) && boxes.stream().anyMatch(b -> b.intersects(box));
    }

    public boolean intersects(Hull other, Point3D offset) {
        if (!bounds.move(offset).intersects(other.bounds)) return false;
        for (Box a : boxes) {
            Box moved = a.move(offset);
            for (Box b : other.boxes) if (moved.intersects(b)) return true;
        }
        return false;
    }

    public Optional<Hit> ray(Point3D start, Point3D end) {
        Hit nearest = null;
        for (Box box : boxes) {
            Optional<Point3D> point = box.clip(start, end);
            if (point.isEmpty()) continue;
            double distance = start.distanceTo(point.get());
            if (nearest == null || distance < nearest.distance()) nearest = new Hit(box, point.get(), distance);
        }
        return Optional.ofNullable(nearest);
    }

    public int glassRay(Point3D start, Point3D end) {
        Hit pane = null;
        for (Box box : boxes) {
            if (box.glass < 0) continue;
            Optional<Point3D> point = box.clip(start, end);
            if (point.isEmpty()) continue;
            double distance = start.distanceTo(point.get());
            if (pane == null || distance < pane.distance()) pane = new Hit(box, point.get(), distance);
        }
        if (pane == null) return -1;
        APart holder = pane.box().part;
        for (Box box : boxes) {
            if (box.glass >= 0 || holder != null && box.part == holder) continue;
            Optional<Point3D> point = box.clip(start, end);
            if (point.isPresent() && start.distanceTo(point.get()) < pane.distance()) return -1;
        }
        return pane.box().glass;
    }

    public static Box bake(Matrix4d matrix, ModelSkeleton.Cube cube, APart part, int glass) {
        Matrix4d transform = new Matrix4d(matrix);
        rotateAt(transform, cube.pivot(), cube.rotation());
        return new Box(position(transform, cube.center()), cube.half().scale(transform.getScale(new Vector3d()).x),
                new Point3D[]{direction(transform, new Point3D(1, 0, 0)), direction(transform, new Point3D(0, 1, 0)), direction(transform, new Point3D(0, 0, 1))},
                part, glass);
    }

    public static Box chassisBox(Matrix4d root, Point3D local, Point3D half, APart part, int glass) {
        Point3D center = position(root, new Point3D(-local.x, local.y, -local.z));
        Point3D[] axes = {direction(root, new Point3D(1, 0, 0)), direction(root, new Point3D(0, 1, 0)), direction(root, new Point3D(0, 0, 1))};
        return new Box(center, half.scale(root.getScale(new Vector3d()).x), axes, part, glass);
    }

    public static void rotateAt(Matrix4d m, Point3D pivot, Point3D rotation) {
        m.translate(pivot.x, pivot.y, pivot.z).rotateZ(rotation.z).rotateY(rotation.y).rotateX(rotation.x).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    public static Point3D position(Matrix4d m, Point3D p) {
        Vector3d v = m.transformPosition(new Vector3d(p.x, p.y, p.z));
        return new Point3D(v.x, v.y, v.z);
    }

    public static Point3D direction(Matrix4d m, Point3D p) {
        Vector3d v = m.transformDirection(new Vector3d(p.x, p.y, p.z)).normalize();
        return new Point3D(v.x, v.y, v.z);
    }
}
