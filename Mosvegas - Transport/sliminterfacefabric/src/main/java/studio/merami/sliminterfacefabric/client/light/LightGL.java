package studio.merami.sliminterfacefabric.client.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.function.Consumer;

final class LightGL {
    private LightGL() {}

    static void registerShaders(Consumer<ShaderInstance> light, Consumer<ShaderInstance> shadow) {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(SlimVehicle.id("vehicle_light"), DefaultVertexFormat.POSITION, light::accept);
            context.register(SlimVehicle.id("vehicle_light_shadow"), DefaultVertexFormat.POSITION, shadow::accept);
        });
    }

    static int floatTexture(int width, int height) {
        int texture = TextureUtil.generateTextureId();
        RenderSystem.bindTexture(texture);
        nearest();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
        return texture;
    }

    static int byteTexture(int width, int height) {
        int texture = TextureUtil.generateTextureId();
        RenderSystem.bindTexture(texture);
        nearest();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return texture;
    }

    static void upload(int texture, int width, int height, FloatBuffer data) {
        RenderSystem.bindTexture(texture);
        unpack();
        data.rewind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_FLOAT, data);
    }

    static void upload(int texture, int width, int height, ByteBuffer data) {
        RenderSystem.bindTexture(texture);
        unpack();
        data.rewind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    }

    static void releaseTexture(int texture) {
        if (texture >= 0) TextureUtil.releaseTextureId(texture);
    }

    private static void nearest() {
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void unpack() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
    }

    static TextureTarget shadowTarget(TextureTarget current, int width, int height) {
        if (current != null) return current;
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_NEAREST);
        return target;
    }

    static TextureTarget copyOf(TextureTarget current, RenderTarget main) {
        if (current == null) current = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (current.width != main.width || current.height != main.height) current.resize(main.width, main.height, Minecraft.ON_OSX);
        int depth = depthFormat(main);
        if (depthFormat(current) != depth) reformatDepth(current, depth);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, current.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, current.width, current.height, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        return current;
    }

    private static int depthFormat(RenderTarget target) {
        RenderSystem.bindTexture(target.getDepthTextureId());
        return GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
    }

    private static void reformatDepth(RenderTarget target, int format) {
        boolean stencil = format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8;
        int type = format == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : stencil ? GL30.GL_UNSIGNED_INT_24_8 : GL11.GL_FLOAT;
        RenderSystem.bindTexture(target.getDepthTextureId());
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, format, target.width, target.height, 0, stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT, type, null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, stencil ? target.getDepthTextureId() : 0, 0);
    }

    static void releaseTarget(RenderTarget target) {
        if (target != null) target.destroyBuffers();
    }

    static RenderTarget mainTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    static void drawInto(RenderTarget target) {
        target.bindWrite(true);
    }

    static void beginPass() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
    }

    static void endPass() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    static int lightmapOn(LightTexture lightmap) {
        lightmap.turnOnLightLayer();
        return RenderSystem.getShaderTexture(2);
    }

    static void lightmapOff(LightTexture lightmap) {
        lightmap.turnOffLightLayer();
    }

    static void drawFullscreen(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.vertex(-1, -1, 0).endVertex();
        buffer.vertex(1, -1, 0).endVertex();
        buffer.vertex(1, 1, 0).endVertex();
        buffer.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
}
