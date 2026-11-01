package ru.mosvegas.casino.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import ru.mosvegas.casino.item.TableItem;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableSize;

/** Invisible filler of a table footprint: collision and clicks, forwarded to the master block. */
public final class TablePartBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<TableSize> SIZE = BlackjackTableBlock.SIZE;
    public static final IntProperty PART = IntProperty.of("part", 0, TableGeometry.GRID * 3 - 1);

    public TablePartBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SIZE, TableSize.LARGE).with(PART, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIZE, PART);
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

    public static BlockPos master(BlockState state, BlockPos pos) {
        return TableGeometry.masterPos(pos, state.get(FACING), state.get(PART));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return TableShapes.get(state.get(SIZE), state.get(PART), state.get(FACING));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return BlackjackTableBlock.use(world, master(state, pos), player, hand, hit.getPos());
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos master = master(state, pos);
        if (!world.isClient && world.getBlockState(master).isOf(CasinoRegistry.BLACKJACK_TABLE)) {
            world.breakBlock(master, !player.isCreative(), player);
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient) {
            BlockPos master = master(state, pos);
            if (world.getBlockState(master).isOf(CasinoRegistry.BLACKJACK_TABLE)) world.breakBlock(master, true);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        BlockPos master = master(state, pos);
        if (world.getBlockEntity(master) instanceof BlackjackTableBlockEntity be) return TableItem.forStyle(be.style(), state.get(SIZE));
        return TableItem.forStyle(null, state.get(SIZE));
    }
}
