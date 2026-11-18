package studio.merami.slimcore.systems;

import studio.merami.slimcore.baseclasses.BlockPosition;
import studio.merami.slimcore.baseclasses.Point3D;

import java.util.ArrayList;
import java.util.List;

public final class TestArena {
    public static final int WATER_DEPTH = 4;
    public static final int BASIN_LENGTH = 96;
    public static final int BASIN_HALF_WIDTH = 40;
    public static final int RAMP_START = 22;
    public static final int RAMP_STEP_LENGTH = 2;
    public static final int RAMP_HALF_WIDTH = 2;
    public static final int[][] RAMPS = {{-20, 3}, {0, 6}, {20, 9}};
    public static final int[][] RINGS = {{40, 30, 14, 7}, {64, -5, 20, 7}, {88, 20, 28, 7}};
    public static final String[] RING_COLORS = {"minecraft:yellow_concrete", "minecraft:orange_concrete", "minecraft:red_concrete"};

    private static final String AIR = "minecraft:air";
    private static final String WATER = "minecraft:water";
    private static final String SAND = "minecraft:sand";
    private static final String GROUND = "minecraft:smooth_stone";
    private static final String WALL = "minecraft:stone_bricks";
    private static final String DECK = "minecraft:spruce_planks";
    private static final String POST = "minecraft:spruce_log";
    private static final String PAD = "minecraft:gray_concrete";
    private static final String PAD_EDGE = "minecraft:yellow_concrete";
    private static final String PAD_MARK = "minecraft:white_concrete";
    private static final String LIGHT = "minecraft:sea_lantern";
    private static final String RAMP = "minecraft:smooth_quartz";
    private static final String RAMP_SLAB = "minecraft:smooth_quartz_slab[type=bottom]";
    private static final String RAMP_EDGE = "minecraft:red_nether_bricks";
    private static final String RAMP_EDGE_SLAB = "minecraft:red_nether_brick_slab[type=bottom]";
    private static final String BUOY = "minecraft:red_wool";
    private static final int CLEAR_HEIGHT = 24;

    private TestArena() {}

    public record Fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String block) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public static Point3D pier(BlockPosition origin) {
        return new Point3D(origin.x() + 4.5, origin.y() + WATER_DEPTH, origin.z() + .5);
    }

    public static Fill bounds(BlockPosition origin) {
        int top = 0;
        for (int[] ring : RINGS) top = Math.max(top, ring[2] + ring[3] + 1);
        return new Fill(origin.x() - 20, origin.y() - 1, origin.z() - BASIN_HALF_WIDTH - 4,
                origin.x() + BASIN_LENGTH + 2, origin.y() + top, origin.z() + BASIN_HALF_WIDTH + 4, AIR);
    }

    public static List<Fill> layout(BlockPosition origin) {
        Builder b = new Builder(origin);
        Fill all = bounds(origin);
        int west = all.minX() - origin.x();
        int east = all.maxX() - origin.x();
        int north = all.minZ() - origin.z();
        int south = all.maxZ() - origin.z();
        int top = WATER_DEPTH - 1;
        b.fill(west, 0, north, east, CLEAR_HEIGHT, south, AIR);
        b.fill(west, -1, north, east, -1, south, GROUND);
        b.fill(0, -1, -BASIN_HALF_WIDTH, BASIN_LENGTH - 1, -1, BASIN_HALF_WIDTH, SAND);
        b.fill(-1, 0, -BASIN_HALF_WIDTH - 1, BASIN_LENGTH, WATER_DEPTH, -BASIN_HALF_WIDTH - 1, WALL);
        b.fill(-1, 0, BASIN_HALF_WIDTH + 1, BASIN_LENGTH, WATER_DEPTH, BASIN_HALF_WIDTH + 1, WALL);
        b.fill(-1, 0, -BASIN_HALF_WIDTH, -1, WATER_DEPTH, BASIN_HALF_WIDTH, WALL);
        b.fill(BASIN_LENGTH, 0, -BASIN_HALF_WIDTH, BASIN_LENGTH, WATER_DEPTH, BASIN_HALF_WIDTH, WALL);
        b.fill(0, 0, -BASIN_HALF_WIDTH, BASIN_LENGTH - 1, top, BASIN_HALF_WIDTH, WATER);
        quay(b, top);
        pier(b, top);
        for (int[] ramp : RAMPS) ramp(b, RAMP_START, ramp[0], ramp[1]);
        ramp(b, 70, 0, 2);
        slalom(b, top);
        tower(b, 76, -28, 16);
        for (int i = 0; i < RINGS.length; i++) ring(b, RINGS[i], RING_COLORS[i]);
        return b.fills;
    }

    private static void quay(Builder b, int top) {
        b.fill(-18, 0, -10, -1, top, 10, GROUND);
        b.fill(-1, WATER_DEPTH, -10, -1, WATER_DEPTH, 10, AIR);
        helipad(b, -10, 0, top, 6);
    }

    private static void helipad(Builder b, int x, int z, int y, int half) {
        b.fill(x - half, y, z - half, x + half, y, z + half, PAD_EDGE);
        b.fill(x - half + 1, y, z - half + 1, x + half - 1, y, z + half - 1, PAD);
        int arm = half - 3;
        b.fill(x - arm, y, z - arm, x + arm, y, z - arm, PAD_MARK);
        b.fill(x - arm, y, z + arm, x + arm, y, z + arm, PAD_MARK);
        b.fill(x, y, z - arm, x, y, z + arm, PAD_MARK);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) b.fill(x + sx * half, y, z + sz * half, x + sx * half, y, z + sz * half, LIGHT);
        }
    }

    private static void pier(Builder b, int top) {
        b.fill(0, top, -2, 8, top, 2, DECK);
        for (int x = 0; x <= 8; x += 4) {
            for (int z = -2; z <= 2; z += 4) b.fill(x, 0, z, x, top - 1, z, POST);
        }
    }

    private static void ramp(Builder b, int startX, int centerZ, int steps) {
        for (int i = 0; i < steps; i++) {
            int x = startX + i * RAMP_STEP_LENGTH;
            int halves = 2 * WATER_DEPTH + i;
            int full = halves / 2;
            boolean slab = halves % 2 == 1;
            for (int z = centerZ - RAMP_HALF_WIDTH; z <= centerZ + RAMP_HALF_WIDTH; z++) {
                boolean edge = Math.abs(z - centerZ) == RAMP_HALF_WIDTH;
                b.fill(x, 0, z, x + RAMP_STEP_LENGTH - 1, full - 1, z, edge ? RAMP_EDGE : RAMP);
                if (slab) b.fill(x, full, z, x + RAMP_STEP_LENGTH - 1, full, z, edge ? RAMP_EDGE_SLAB : RAMP_SLAB);
            }
        }
    }

    private static void slalom(Builder b, int top) {
        for (int i = 0; i * 8 + 10 < BASIN_LENGTH - 6; i++) {
            int x = 10 + i * 8;
            int z = -34 + (i % 2 == 0 ? -2 : 2);
            b.fill(x, 0, z, x, top, z, LIGHT);
            b.fill(x, top + 1, z, x, top + 1, z, BUOY);
        }
    }

    private static void tower(Builder b, int x, int z, int height) {
        b.fill(x - 1, 0, z - 1, x + 1, height - 1, z + 1, WALL);
        helipad(b, x, z, height, 5);
    }

    private static void ring(Builder b, int[] ring, String block) {
        int x = ring[0];
        int r = ring[3];
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dy = -r - 1; dy <= r + 1; dy++) {
                if (Math.abs(Math.sqrt(dz * dz + dy * dy) - r) < .55) b.fill(x, ring[2] + dy, ring[1] + dz, x, ring[2] + dy, ring[1] + dz, block);
            }
        }
    }

    private static final class Builder {
        private final BlockPosition origin;
        private final List<Fill> fills = new ArrayList<>();

        private Builder(BlockPosition origin) {
            this.origin = origin;
        }

        private void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
            fills.add(new Fill(origin.x() + Math.min(x0, x1), origin.y() + Math.min(y0, y1), origin.z() + Math.min(z0, z1),
                    origin.x() + Math.max(x0, x1), origin.y() + Math.max(y0, y1), origin.z() + Math.max(z0, z1), block));
        }
    }
}
