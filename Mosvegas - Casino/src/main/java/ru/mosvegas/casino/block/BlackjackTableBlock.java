package ru.mosvegas.casino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import ru.mosvegas.casino.item.TableItem;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableSize;

/**
 * Master block of a multi-block table (bottom centre of the dealer side). FACING points towards the
 * players. It carries the block entity; {@link TablePartBlock}s fill the rest of the footprint.
 */
public final class BlackjackTableBlock extends BlockWithEntity {
    public static final MapCodec<BlackjackTableBlock> CODEC = createCodec(BlackjackTableBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<TableSize> SIZE = EnumProperty.of("size", TableSize.class);

    public BlackjackTableBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SIZE, TableSize.LARGE));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIZE);
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        TableSize size = TableItem.sizeOf(ctx.getStack());
        World world = ctx.getWorld();
        BlockPos master = ctx.getBlockPos();
        for (int part : TableGeometry.footprint(size)) {
            BlockPos p = TableGeometry.partPos(master, facing, part);
            if (!world.isInBuildLimit(p) || !world.getBlockState(p).isReplaceable()) return null;
        }
        return getDefaultState().with(FACING, facing).with(SIZE, size);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient) return;
        Direction facing = state.get(FACING);
        TableSize size = state.get(SIZE);
        for (int part : TableGeometry.footprint(size)) {
            if (part == TableGeometry.MASTER_PART) continue;
            BlockPos p = TableGeometry.partPos(pos, facing, part);
            world.setBlockState(p, CasinoRegistry.TABLE_PART.getDefaultState()
                    .with(TablePartBlock.FACING, facing).with(TablePartBlock.SIZE, size).with(TablePartBlock.PART, part), Block.NOTIFY_ALL);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            Direction facing = state.get(FACING);
            for (int part : TableGeometry.footprint(state.get(SIZE))) {
                if (part == TableGeometry.MASTER_PART) continue;
                BlockPos p = TableGeometry.partPos(pos, facing, part);
                BlockState there = world.getBlockState(p);
                if (there.isOf(CasinoRegistry.TABLE_PART) && there.get(TablePartBlock.PART) == part) {
                    world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return TableShapes.get(state.get(SIZE), TableGeometry.MASTER_PART, state.get(FACING));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return use(world, pos, player, hand, hit.getPos());
    }

    static ActionResult use(World world, BlockPos master, PlayerEntity player, Hand hand, Vec3d hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.CONSUME;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(master) instanceof BlackjackTableBlockEntity be && be.runtime() != null) {
            be.runtime().interact(sp, hit);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof BlackjackTableBlockEntity be) return TableItem.forStyle(be.style(), state.get(SIZE));
        return TableItem.forStyle(null, state.get(SIZE));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new BlackjackTableBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CasinoRegistry.BLACKJACK_TABLE_ENTITY, BlackjackTableBlockEntity::serverTick);
    }
}
