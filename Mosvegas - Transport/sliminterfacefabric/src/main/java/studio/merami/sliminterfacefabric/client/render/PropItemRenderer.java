package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.sliminterfacefabric.client.paint.SprayStream;
import studio.merami.sliminterfacefabric.registry.BlockRegistry;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;

public record PropItemRenderer(PropModel model, float scale, float centerX, float centerY, float centerZ) implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    public static void register() {
        BuiltinItemRendererRegistry.INSTANCE.register(ItemRegistry.item(SlimItems.SPRAY_GUN), new PropItemRenderer(PropModel.SPRAY_GUN, 2.6f, 0, 2.4f, 1.2f));
        BuiltinItemRendererRegistry.INSTANCE.register(ItemRegistry.item(SlimItems.ZONE_MARKER), new PropItemRenderer(PropModel.ZONE_MARKER, 6.5f, 0, .6f, 0));
        BuiltinItemRendererRegistry.INSTANCE.register(ItemRegistry.item(SlimItems.PAINT_STAND), new PropItemRenderer(PropModel.PAINT_STATION, .62f, 0, 8, 0));
        BuiltinItemRendererRegistry.INSTANCE.register(BlockRegistry.PLATE_TERMINAL_ITEM, new PropItemRenderer(PropModel.PLATE_TERMINAL, .42f, 0, 16, 0));
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5f, .5f, .5f);
        pose.scale(scale, scale, scale);
        pose.translate(-centerX / 16, -centerY / 16, -centerZ / 16);
        if (model == PropModel.SPRAY_GUN && (mode.firstPerson() || mode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) SprayStream.capture(pose, mode);
        model.draw(pose, buffers, light, overlay);
        pose.popPose();
    }
}
