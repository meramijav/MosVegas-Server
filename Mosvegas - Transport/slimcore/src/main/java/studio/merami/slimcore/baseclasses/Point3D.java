package studio.merami.slimcore.baseclasses;

public final class Point3D {
    public static final Point3D ZERO = new Point3D(0, 0, 0);

    public final double x;
    public final double y;
    public final double z;

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point3D add(Point3D other) {
        return add(other.x, other.y, other.z);
    }

    public Point3D add(double dx, double dy, double dz) {
        return new Point3D(x + dx, y + dy, z + dz);
    }

    public Point3D subtract(Point3D other) {
        return subtract(other.x, other.y, other.z);
    }

    public Point3D subtract(double dx, double dy, double dz) {
        return add(-dx, -dy, -dz);
    }

    public Point3D scale(double factor) {
        return multiply(factor, factor, factor);
    }

    public Point3D multiply(double fx, double fy, double fz) {
        return new Point3D(x * fx, y * fy, z * fz);
    }

    public Point3D reverse() {
        return scale(-1);
    }

    public double dot(Point3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Point3D cross(Point3D other) {
        return new Point3D(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double horizontalDistance() {
        return Math.sqrt(x * x + z * z);
    }

    public double horizontalDistanceSqr() {
        return x * x + z * z;
    }

    public double distanceTo(Point3D other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceToSqr(Point3D other) {
        return distanceToSqr(other.x, other.y, other.z);
    }

    public double distanceToSqr(double ox, double oy, double oz) {
        double dx = ox - x;
        double dy = oy - y;
        double dz = oz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Point3D normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1e-4 ? ZERO : new Point3D(x / length, y / length, z / length);
    }

    public Point3D lerp(Point3D other, double t) {
        return new Point3D(SlimMath.lerp(t, x, other.x), SlimMath.lerp(t, y, other.y), SlimMath.lerp(t, z, other.z));
    }

    public Point3D xRot(float angle) {
        float c = SlimMath.cos(angle);
        float s = SlimMath.sin(angle);
        return new Point3D(x, y * c + z * s, z * c - y * s);
    }

    public Point3D yRot(float angle) {
        float c = SlimMath.cos(angle);
        float s = SlimMath.sin(angle);
        return new Point3D(x * c + z * s, y, z * c - x * s);
    }

    public Point3D zRot(float angle) {
        float c = SlimMath.cos(angle);
        float s = SlimMath.sin(angle);
        return new Point3D(x * c + y * s, y * c - x * s, z);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Point3D other)) return false;
        return Double.compare(other.x, x) == 0 && Double.compare(other.y, y) == 0 && Double.compare(other.z, z) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(x);
        int hash = (int) (bits ^ bits >>> 32);
        bits = Double.doubleToLongBits(y);
        hash = 31 * hash + (int) (bits ^ bits >>> 32);
        bits = Double.doubleToLongBits(z);
        return 31 * hash + (int) (bits ^ bits >>> 32);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
