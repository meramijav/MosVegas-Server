package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

final class VehicleRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> GLASS = Util.memoize(texture -> create("slimvehicle_glass",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)));

    private VehicleRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static RenderType glass(ResourceLocation texture) {
        return GLASS.apply(texture);
    }
}
