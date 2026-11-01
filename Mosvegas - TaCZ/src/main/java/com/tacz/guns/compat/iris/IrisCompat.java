package com.tacz.guns.compat.iris;

import net.minecraft.client.renderer.MultiBufferSource;

public final class IrisCompat {
    private IrisCompat() {
    }

    public static void initCompat() {
    }

    public static boolean isRenderShadow() {
        return false;
    }

    public static boolean isUsingRenderPack() {
        return false;
    }

    public static boolean endBatch(MultiBufferSource.BufferSource bufferSource) {
        return false;
    }
}
