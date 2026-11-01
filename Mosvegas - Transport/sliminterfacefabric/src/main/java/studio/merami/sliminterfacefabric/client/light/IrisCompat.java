package studio.merami.sliminterfacefabric.client.light;

import net.fabricmc.loader.api.FabricLoader;
import studio.merami.slimcore.SlimCore;

import java.lang.reflect.Method;

final class IrisCompat {
    private static final Object API;
    private static final Method IN_USE;

    static {
        Object api = null;
        Method inUse = null;
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                SlimCore.LOGGER.warn("Iris is installed but its API is not available; vehicle lights stay on: {}", e.toString());
                api = null;
                inUse = null;
            }
        }
        API = api;
        IN_USE = inUse;
    }

    private IrisCompat() {}

    static boolean shaderPackInUse() {
        if (IN_USE == null) return false;
        try {
            return (Boolean) IN_USE.invoke(API);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
