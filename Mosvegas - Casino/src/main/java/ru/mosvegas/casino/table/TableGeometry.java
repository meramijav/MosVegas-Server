package ru.mosvegas.casino.table;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Table-local coordinates shared by server (seats, dealer path, collision) and client (cards, chips, labels).
 * <ul>
 *   <li>origin: bottom centre of the master block;</li>
 *   <li>{@code lz}: towards the players (block FACING), {@code lx}: to the dealer's right;</li>
 *   <li>the felt is a half disc of {@code radius} around {@code (0, CENTER_Z)}, the dealer sits behind it.</li>
 * </ul>
 * Keep in sync with {@code tools/AssetGen.java}, which builds the default models from the same numbers.
 */
public final class TableGeometry {
    public static final double CENTER_Z = -0.45;
    public static final double FELT_Y = 0.875;
    public static final double RIM_WIDTH = 0.12;
    public static final int GRID = 5;

    public final TableSize size;
    public final double radius;
    public final int boxes;

    public record Local(double x, double z) {}

    public TableGeometry(TableSize size, int boxes) {
        this.size = size;
        this.radius = size.radius;
        this.boxes = Math.max(1, boxes);
    }

    /** Angle of a box in degrees around the felt centre; box 0 is on the dealer's left, like "first base". */
    public double boxAngle(int box) {
        if (boxes == 1) return 90;
        double span = boxes <= 3 ? 110 : 140;
        double start = 90 + span / 2;
        return start - box * (span / (boxes - 1));
    }

    public Local polar(double angleDeg, double distance) {
        double a = Math.toRadians(angleDeg);
        return new Local(Math.cos(a) * distance, CENTER_Z + Math.sin(a) * distance);
    }

    public Local betSpot(int box) { return polar(boxAngle(box), radius * 0.70); }
    public Local insuranceSpot(int box) { return polar(boxAngle(box) - 9 * (2.4 / radius), radius * 0.50); }
    public Local cardAnchor(int box) { return polar(boxAngle(box), radius * 0.56); }
    public Local seat(int box) { return polar(boxAngle(box), radius + 0.48); }
    /** Where people betting behind a box stand. */
    public Local behindSpot(int box, int index) {
        double spread = (index % 2 == 0 ? -1 : 1) * (8 + 6 * (index / 2));
        return polar(boxAngle(box) + spread, radius + 1.25 + 0.15 * (index / 2));
    }
    public Local sideSpot(int box, int index) {
        double angle = boxAngle(box) + (index % 2 == 0 ? 8 : -8) * (2.4 / radius);
        return polar(angle, radius * 0.86);
    }

    public Local dealerSeat() { return new Local(0, -0.80); }
    public Local dealerStand() { return new Local(0, -1.55); }
    public Local defaultEntrance() { return new Local(0, -4.5); }
    /** Card shoe and discard holder sit within reach of the seated dealer's hands (see AssetGen "seated dealer reach"). */
    public Local shoe() { return new Local(-0.66, -0.40); }
    public Local discard() { return new Local(0.66, -0.40); }
    /** Where a card leaves the shoe. */
    public Local shoeMouth() { return new Local(-0.56, -0.27); }
    /** Where the right hand lets the collected cards fall into the discard holder. */
    public Local discardDrop() { return new Local(0.60, -0.36); }
    public Local chipTray() { return new Local(0, -0.32); }

    /** Number of hand-animation buckets: directions from the dealer's left (160 deg) to the right (20 deg). */
    public static final int BUCKETS = 7;
    /** Buckets 0..2 are served by the dealer's left hand, the rest by the right hand. */
    public static final int LAST_LEFT_BUCKET = 2;

    /** Animation bucket of a direction around the felt centre (degrees, 0 = dealer's right, 180 = dealer's left). */
    public static int bucket(double angleDeg) {
        int k = (int) Math.round((160 - angleDeg) / (140.0 / (BUCKETS - 1)));
        return Math.max(0, Math.min(BUCKETS - 1, k));
    }

    /** Angle around the felt centre of a table-local point. */
    public static double angleOf(double lx, double lz) {
        return Math.toDegrees(Math.atan2(lz - CENTER_Z, lx));
    }

    public int boxBucket(int box) { return bucket(boxAngle(box)); }
    public Local commonCards() { return new Local(0, CENTER_Z + radius * 0.36); }

    public Local dealerCard(int index) {
        return new Local(-0.22 + index * 0.15, -0.02);
    }

    /** Unit tangent of the arc at a box, pointing from the dealer's left to the dealer's right. */
    public Local tangent(int box) {
        double a = Math.toRadians(boxAngle(box));
        return new Local(Math.sin(a), -Math.cos(a));
    }

    /** Unit vector from a box towards the dealer (felt centre). */
    public Local inward(int box) {
        double a = Math.toRadians(boxAngle(box));
        return new Local(-Math.cos(a), -Math.sin(a));
    }

    /** Nearest box to a table-local point, by angle around the felt centre. */
    public int boxAt(double lx, double lz) {
        double angle = Math.toDegrees(Math.atan2(lz - CENTER_Z, lx));
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int b = 0; b < boxes; b++) {
            double d = Math.abs(MathHelper.wrapDegrees(angle - boxAngle(b)));
            if (d < bestDiff) {
                bestDiff = d;
                best = b;
            }
        }
        return best;
    }

    public boolean insideFelt(double lx, double lz, double margin) {
        double dz = lz - CENTER_Z;
        return dz >= -0.06 && lx * lx + dz * dz <= (radius + margin) * (radius + margin);
    }

    // ------------------------------------------------------------------ world transforms

    public static Vec3d right(Direction facing) {
        Direction r = facing.rotateYClockwise();
        return new Vec3d(r.getOffsetX(), 0, r.getOffsetZ());
    }

    public static Vec3d forward(Direction facing) {
        return new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ());
    }

    public static Vec3d toWorld(BlockPos master, Direction facing, double lx, double ly, double lz) {
        Vec3d r = right(facing), f = forward(facing);
        return new Vec3d(master.getX() + 0.5 + r.x * lx + f.x * lz, master.getY() + ly, master.getZ() + 0.5 + r.z * lx + f.z * lz);
    }

    public static Vec3d toWorld(BlockPos master, Direction facing, Local local, double ly) {
        return toWorld(master, facing, local.x(), ly, local.z());
    }

    /** Offset relative to the master block origin (for renderers, which already sit at the block). */
    public static Vec3d toBlockOffset(Direction facing, double lx, double ly, double lz) {
        Vec3d r = right(facing), f = forward(facing);
        return new Vec3d(0.5 + r.x * lx + f.x * lz, ly, 0.5 + r.z * lx + f.z * lz);
    }

    public static Local toLocal(BlockPos master, Direction facing, Vec3d world) {
        Vec3d r = right(facing), f = forward(facing);
        double dx = world.x - (master.getX() + 0.5), dz = world.z - (master.getZ() + 0.5);
        return new Local(dx * r.x + dz * r.z, dx * f.x + dz * f.z);
    }

    /** Minecraft yaw that looks along table-local direction (dx, dz). */
    public static float yawOf(Direction facing, double dx, double dz) {
        Vec3d r = right(facing), f = forward(facing);
        return worldYaw(r.x * dx + f.x * dz, r.z * dx + f.z * dz);
    }

    /** Minecraft yaw that looks along world direction (wx, wz). */
    public static float worldYaw(double wx, double wz) {
        return (float) (MathHelper.atan2(wz, wx) * (180 / Math.PI)) - 90f;
    }

    // ------------------------------------------------------------------ footprint

    public static int partIndex(int lx, int lz) { return lz * GRID + (lx + 2); }
    public static int partX(int index) { return index % GRID - 2; }
    public static int partZ(int index) { return index / GRID; }
    public static final int MASTER_PART = partIndex(0, 0);

    public static BlockPos partPos(BlockPos master, Direction facing, int index) {
        Direction r = facing.rotateYClockwise();
        int lx = partX(index), lz = partZ(index);
        return master.add(r.getOffsetX() * lx + facing.getOffsetX() * lz, 0, r.getOffsetZ() * lx + facing.getOffsetZ() * lz);
    }

    public static BlockPos masterPos(BlockPos part, Direction facing, int index) {
        Direction r = facing.rotateYClockwise();
        int lx = partX(index), lz = partZ(index);
        return part.add(-(r.getOffsetX() * lx + facing.getOffsetX() * lz), 0, -(r.getOffsetZ() * lx + facing.getOffsetZ() * lz));
    }

    /** Footprint cells (as part indices) whose area touches the felt. */
    public static List<Integer> footprint(TableSize size) {
        List<Integer> list = new ArrayList<>();
        TableGeometry g = new TableGeometry(size, 1);
        for (int lz = 0; lz < size.depth; lz++) {
            for (int lx = -size.halfWidth; lx <= size.halfWidth; lx++) {
                boolean any = false;
                for (int i = 0; i < 4 && !any; i++) for (int j = 0; j < 4 && !any; j++) {
                    double px = lx - 0.5 + (i + 0.5) / 4, pz = lz - 0.5 + (j + 0.5) / 4;
                    if (g.insideFelt(px, pz, RIM_WIDTH * 0.5)) any = true;
                }
                if (any) list.add(partIndex(lx, lz));
            }
        }
        return list;
    }
}
