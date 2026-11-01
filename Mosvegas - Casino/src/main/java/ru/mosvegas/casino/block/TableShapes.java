package ru.mosvegas.casino.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableSize;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Collision of each footprint block, cut from the half-disc of the felt in quarter-block steps. */
public final class TableShapes {
    private static final Map<String, VoxelShape> CACHE = new ConcurrentHashMap<>();
    private static final double HEIGHT = 0.9375;

    private TableShapes() {}

    public static VoxelShape get(TableSize size, int part, Direction facing) {
        return CACHE.computeIfAbsent(size.id + "/" + part + "/" + facing.getName(), k -> build(size, part, facing));
    }

    private static VoxelShape build(TableSize size, int part, Direction facing) {
        TableGeometry g = new TableGeometry(size, 1);
        BlockPos origin = BlockPos.ORIGIN;
        BlockPos block = TableGeometry.partPos(origin, facing, part);
        VoxelShape shape = VoxelShapes.empty();
        int n = 4;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double u0 = i / (double) n, v0 = j / (double) n;
                Vec3d center = new Vec3d(block.getX() + u0 + 0.5 / n, 0, block.getZ() + v0 + 0.5 / n);
                TableGeometry.Local local = TableGeometry.toLocal(origin, facing, center);
                if (g.insideFelt(local.x(), local.z(), TableGeometry.RIM_WIDTH * 0.4)) {
                    shape = VoxelShapes.union(shape, VoxelShapes.cuboid(u0, 0, v0, u0 + 1.0 / n, HEIGHT, v0 + 1.0 / n));
                }
            }
        }
        return shape.isEmpty() ? VoxelShapes.cuboid(0.375, 0, 0.375, 0.625, 0.5, 0.625) : shape.simplify();
    }
}
