package studio.merami.sliminterfacefabric.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import studio.merami.sliminterfacefabric.registry.BlockRegistry;

public final class PlateTerminalBlockEntity extends BlockEntity {
    public PlateTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.PLATE_TERMINAL_ENTITY, pos, state);
    }
}
