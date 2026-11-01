package ru.mosvegas.casino.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Low-level quads for cards, chips and floating labels, in coordinates relative to the current matrix. */
public final class CasinoDraw {
    private static final int CHIP_SEGMENTS = 14;

    private CasinoDraw() {}

    /**
     * A flat card. {@code top} points to the card's head (towards the dealer), {@code left} to its left edge,
     * {@code flip} 0..1 turns it over around the long axis (0 = shows {@code front}).
     */
    public static void card(MatrixStack ms, VertexConsumerProvider vcp, Identifier front, Identifier back, Vec3d center,
                            Vec3d top, Vec3d left, float width, float height, float flip, int light) {
        double angle = Math.PI * flip;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        boolean showBack = cos < 0;
        Identifier texture = showBack ? back : front;
        double scale = Math.abs(cos);
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d halfTop = top.multiply(height / 2.0);
        Vec3d halfLeft = left.multiply(width / 2.0 * scale);
        Vec3d lift = up.multiply(sin * width / 2.0);
        Vec3d tl = center.add(halfTop).add(halfLeft).add(lift);
        Vec3d tr = center.add(halfTop).subtract(halfLeft).subtract(lift);
        Vec3d br = center.subtract(halfTop).subtract(halfLeft).subtract(lift);
        Vec3d bl = center.subtract(halfTop).add(halfLeft).add(lift);
        if (showBack) {
            // mirrored so the back image is not flipped while turning
            Vec3d t1 = tl;
            tl = tr;
            tr = t1;
            Vec3d b1 = bl;
            bl = br;
            br = b1;
        }
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        vertex(vc, m, n, tl, 0, 0, light);
        vertex(vc, m, n, bl, 0, 1, light);
        vertex(vc, m, n, br, 1, 1, light);
        vertex(vc, m, n, tr, 1, 0, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3d p, float u, float v, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
    }

    /** A glowing decal lying on the felt (translucent, full bright), tinted with {@code argb}. */
    public static void decal(MatrixStack ms, VertexConsumerProvider vcp, Identifier texture, Vec3d center, float radius, int argb) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float x = (float) center.x, y = (float) center.y, z = (float) center.z;
        // both windings: visible from above whatever the layer's culling
        float[][] corners = {{-radius, -radius, 0, 0}, {-radius, radius, 0, 1}, {radius, radius, 1, 1}, {radius, -radius, 1, 0},
                {radius, -radius, 1, 0}, {radius, radius, 1, 1}, {-radius, radius, 0, 1}, {-radius, -radius, 0, 0}};
        for (float[] k : corners) {
            vc.vertex(m, x + k[0], y, z + k[1]).color(r, g, b, a).texture(k[2], k[3]).overlay(OverlayTexture.DEFAULT_UV)
                    .light(0xF000F0).normal(n, 0, 1, 0).next();
        }
    }

    /** A poker chip: textured top/bottom discs and a ribbed side, {@code base} is the bottom centre. */
    public static void chip(MatrixStack ms, VertexConsumerProvider vcp, Identifier texture, Vec3d base, float radius, float thickness, int light) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        float x = (float) base.x, y = (float) base.y, z = (float) base.z;
        float top = y + thickness;
        for (int i = 0; i < CHIP_SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / CHIP_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / CHIP_SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float u0 = 0.5f + 0.5f * c0, v0 = 0.5f + 0.5f * s0, u1 = 0.5f + 0.5f * c1, v1 = 0.5f + 0.5f * s1;
            // top
            put(vc, m, n, x, top, z, 0.5f, 0.5f, 0, 1, 0, light);
            put(vc, m, n, x + c1 * radius, top, z + s1 * radius, u1, v1, 0, 1, 0, light);
            put(vc, m, n, x + c0 * radius, top, z + s0 * radius, u0, v0, 0, 1, 0, light);
            put(vc, m, n, x + c0 * radius, top, z + s0 * radius, u0, v0, 0, 1, 0, light);
            // bottom
            put(vc, m, n, x, y, z, 0.5f, 0.5f, 0, -1, 0, light);
            put(vc, m, n, x + c0 * radius, y, z + s0 * radius, u0, v0, 0, -1, 0, light);
            put(vc, m, n, x + c1 * radius, y, z + s1 * radius, u1, v1, 0, -1, 0, light);
            put(vc, m, n, x + c1 * radius, y, z + s1 * radius, u1, v1, 0, -1, 0, light);
            // side: sample the outer ring of the face texture (the edge stripes)
            float r0u = 0.5f + 0.47f * c0, r0v = 0.5f + 0.47f * s0, r1u = 0.5f + 0.47f * c1, r1v = 0.5f + 0.47f * s1;
            float i0u = 0.5f + 0.40f * c0, i0v = 0.5f + 0.40f * s0, i1u = 0.5f + 0.40f * c1, i1v = 0.5f + 0.40f * s1;
            float nx = (c0 + c1) / 2, nz = (s0 + s1) / 2;
            put(vc, m, n, x + c0 * radius, y, z + s0 * radius, i0u, i0v, nx, 0, nz, light);
            put(vc, m, n, x + c0 * radius, top, z + s0 * radius, r0u, r0v, nx, 0, nz, light);
            put(vc, m, n, x + c1 * radius, top, z + s1 * radius, r1u, r1v, nx, 0, nz, light);
            put(vc, m, n, x + c1 * radius, y, z + s1 * radius, i1u, i1v, nx, 0, nz, light);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                            float nx, float ny, float nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
    }

    /** A camera-facing text line, centred on {@code pos}. */
    public static void label(MatrixStack ms, VertexConsumerProvider vcp, Vec3d pos, Text text, int color, int background, float scale, int light) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(mc.getEntityRenderDispatcher().getRotation());
        ms.scale(-scale, -scale, scale);
        float x = -tr.getWidth(text) / 2f;
        tr.draw(text, x, 0, color, false, ms.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.NORMAL, background, light);
        ms.pop();
    }

    /** Multi-line label: first line on top. */
    public static void labels(MatrixStack ms, VertexConsumerProvider vcp, Vec3d pos, Text[] lines, int[] colors, int background, float scale, int light) {
        double lineHeight = 10 * scale;
        for (int i = 0; i < lines.length; i++) {
            label(ms, vcp, pos.add(0, (lines.length - 1 - i) * lineHeight, 0), lines[i], colors[Math.min(i, colors.length - 1)], background, scale, light);
        }
    }

    public static Vector3f up() { return new Vector3f(0, 1, 0); }
}
