package ru.mosvegas.casino.client.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.client.state.ClientTables;
import ru.mosvegas.casino.table.TableSize;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** GeckoLib table model plus the live cards, chips and labels of the round. */
public final class TableRenderer implements BlockEntityRenderer<BlackjackTableBlockEntity> {
    private final BlockEntityRenderer<BlackjackTableBlockEntity> model = new GeoBlockRenderer<>(new TableGeoModel());

    public TableRenderer(BlockEntityRendererFactory.Context context) {}

    @Override
    public void render(BlackjackTableBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        model.render(be, tickDelta, matrices, vertexConsumers, light, overlay);
        BlockState state = be.getCachedState();
        if (!state.contains(BlackjackTableBlock.FACING)) return;
        Direction facing = state.get(BlackjackTableBlock.FACING);
        TableSize size = state.get(BlackjackTableBlock.SIZE);
        matrices.push();
        TableOverlay.render(facing, size, ClientTables.get(be.getPos()), tickDelta, matrices, vertexConsumers, light);
        matrices.pop();
    }

    @Override public boolean rendersOutsideBoundingBox(BlackjackTableBlockEntity be) { return true; }
    @Override public int getRenderDistance() { return 96; }
}
