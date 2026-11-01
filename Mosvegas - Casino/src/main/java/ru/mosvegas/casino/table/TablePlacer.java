package ru.mosvegas.casino.table;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.block.TablePartBlock;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.config.TableStyle;
import ru.mosvegas.casino.registry.CasinoRegistry;

/** Places a complete multi-block table from code (admin command, showcase world). */
public final class TablePlacer {
    private TablePlacer() {}

    /** @param facing direction towards the players; returns null on success, otherwise the reason. */
    public static String place(ServerWorld world, BlockPos master, Direction facing, String styleId) {
        TableStyle style = ConfigManager.get().styles.get(styleId);
        if (style == null) return "Нет стиля " + styleId;
        TableSize size = style.size();
        for (int part : TableGeometry.footprint(size)) {
            BlockPos p = TableGeometry.partPos(master, facing, part);
            if (!world.isInBuildLimit(p) || !world.getBlockState(p).isReplaceable()) return "Мешает блок " + p.toShortString();
        }
        BlockState state = CasinoRegistry.BLACKJACK_TABLE.getDefaultState().with(BlackjackTableBlock.FACING, facing).with(BlackjackTableBlock.SIZE, size);
        world.setBlockState(master, state, Block.NOTIFY_ALL);
        if (world.getBlockEntity(master) instanceof BlackjackTableBlockEntity be) be.setStyle(styleId);
        for (int part : TableGeometry.footprint(size)) {
            if (part == TableGeometry.MASTER_PART) continue;
            world.setBlockState(TableGeometry.partPos(master, facing, part), CasinoRegistry.TABLE_PART.getDefaultState()
                    .with(TablePartBlock.FACING, facing).with(TablePartBlock.SIZE, size).with(TablePartBlock.PART, part), Block.NOTIFY_ALL);
        }
        return null;
    }
}
