package studio.merami.slimcore.baseclasses;

import java.util.Optional;

public final class BoundingBox {
    private static final double EPSILON = 1.0E-7;

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public BoundingBox(double x0, double y0, double z0, double x1, double y1, double z1) {
        minX = Math.min(x0, x1);
        minY = Math.min(y0, y1);
        minZ = Math.min(z0, z1);
        maxX = Math.max(x0, x1);
        maxY = Math.max(y0, y1);
        maxZ = Math.max(z0, z1);
    }

    public BoundingBox(Point3D a, Point3D b) {
        this(a.x, a.y, a.z, b.x, b.y, b.z);
    }

    public BoundingBox inflate(double amount) {
        return inflate(amount, amount, amount);
    }

    public BoundingBox inflate(double dx, double dy, double dz) {
        return new BoundingBox(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public BoundingBox deflate(double amount) {
        return inflate(-amount);
    }

    public BoundingBox move(Point3D offset) {
        return move(offset.x, offset.y, offset.z);
    }

    public BoundingBox move(double dx, double dy, double dz) {
        return new BoundingBox(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public BoundingBox expandTowards(Point3D direction) {
        return expandTowards(direction.x, direction.y, direction.z);
    }

    public BoundingBox expandTowards(double dx, double dy, double dz) {
        double x0 = minX;
        double y0 = minY;
        double z0 = minZ;
        double x1 = maxX;
        double y1 = maxY;
        double z1 = maxZ;
        if (dx < 0) x0 += dx;
        else if (dx > 0) x1 += dx;
        if (dy < 0) y0 += dy;
        else if (dy > 0) y1 += dy;
        if (dz < 0) z0 += dz;
        else if (dz > 0) z1 += dz;
        return new BoundingBox(x0, y0, z0, x1, y1, z1);
    }

    public BoundingBox minmax(BoundingBox other) {
        return new BoundingBox(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    public boolean intersects(BoundingBox other) {
        return intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(double x0, double y0, double z0, double x1, double y1, double z1) {
        return minX < x1 && maxX > x0 && minY < y1 && maxY > y0 && minZ < z1 && maxZ > z0;
    }

    public boolean contains(Point3D point) {
        return point.x >= minX && point.x < maxX && point.y >= minY && point.y < maxY && point.z >= minZ && point.z < maxZ;
    }

    public double sizeX() {
        return maxX - minX;
    }

    public double sizeY() {
        return maxY - minY;
    }

    public double sizeZ() {
        return maxZ - minZ;
    }

    public Point3D center() {
        return new Point3D(SlimMath.lerp(.5, minX, maxX), SlimMath.lerp(.5, minY, maxY), SlimMath.lerp(.5, minZ, maxZ));
    }

    public Optional<Point3D> clip(Point3D start, Point3D end) {
        double[] nearest = {1.0};
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        boolean hit = false;
        if (dx > EPSILON) hit = clipFace(nearest, dx, dy, dz, minX, minY, maxY, minZ, maxZ, start.x, start.y, start.z);
        else if (dx < -EPSILON) hit = clipFace(nearest, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, start.x, start.y, start.z);
        if (dy > EPSILON) hit |= clipFace(nearest, dy, dz, dx, minY, minZ, maxZ, minX, maxX, start.y, start.z, start.x);
        else if (dy < -EPSILON) hit |= clipFace(nearest, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, start.y, start.z, start.x);
        if (dz > EPSILON) hit |= clipFace(nearest, dz, dx, dy, minZ, minX, maxX, minY, maxY, start.z, start.x, start.y);
        else if (dz < -EPSILON) hit |= clipFace(nearest, dz, dx, dy, maxZ, minX, maxX, minY, maxY, start.z, start.x, start.y);
        if (!hit) return Optional.empty();
        double t = nearest[0];
        return Optional.of(start.add(t * dx, t * dy, t * dz));
    }

    private static boolean clipFace(double[] nearest, double d, double e, double f, double plane, double minE, double maxE, double minF, double maxF,
                                    double startD, double startE, double startF) {
        double t = (plane - startD) / d;
        double atE = startE + t * e;
        double atF = startF + t * f;
        if (0.0 < t && t < nearest[0] && minE - EPSILON < atE && atE < maxE + EPSILON && minF - EPSILON < atF && atF < maxF + EPSILON) {
            nearest[0] = t;
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof BoundingBox other)) return false;
        return Double.compare(other.minX, minX) == 0 && Double.compare(other.minY, minY) == 0 && Double.compare(other.minZ, minZ) == 0
                && Double.compare(other.maxX, maxX) == 0 && Double.compare(other.maxY, maxY) == 0 && Double.compare(other.maxZ, maxZ) == 0;
    }

    @Override
    public int hashCode() {
        int hash = Double.hashCode(minX);
        hash = 31 * hash + Double.hashCode(minY);
        hash = 31 * hash + Double.hashCode(minZ);
        hash = 31 * hash + Double.hashCode(maxX);
        hash = 31 * hash + Double.hashCode(maxY);
        return 31 * hash + Double.hashCode(maxZ);
    }

    @Override
    public String toString() {
        return "BoundingBox[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
