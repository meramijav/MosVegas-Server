package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import studio.merami.sliminterfacefabric.shop.PlateTerminalBlockEntity;

public final class PlateTerminalRenderer implements BlockEntityRenderer<PlateTerminalBlockEntity> {
    @Override
    public void render(PlateTerminalBlockEntity terminal, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - terminal.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot()));
        PropModel.PLATE_TERMINAL.draw(pose, buffers, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PlateTerminalBlockEntity terminal) {
        return true;
    }
}
