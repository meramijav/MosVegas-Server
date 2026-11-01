package studio.merami.slimcore.baseclasses;

public record BlockPosition(int x, int y, int z) {
    public static BlockPosition containing(Point3D point) {
        return new BlockPosition(SlimMath.floor(point.x), SlimMath.floor(point.y), SlimMath.floor(point.z));
    }
}
