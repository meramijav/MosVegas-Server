package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.baseclasses.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Convert {
    private static final Map<ResourceId, ResourceLocation> LOCATIONS = new ConcurrentHashMap<>();

    private Convert() {}

    public static Vec3 vec(Point3D point) {
        return new Vec3(point.x, point.y, point.z);
    }

    public static Point3D point(Vec3 vec) {
        return new Point3D(vec.x, vec.y, vec.z);
    }

    public static AABB aabb(BoundingBox box) {
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static BoundingBox box(AABB box) {
        return new BoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static ResourceLocation location(ResourceId id) {
        return id == null ? null : LOCATIONS.computeIfAbsent(id, key -> new ResourceLocation(key.namespace(), key.path()));
    }

    public static ResourceId id(ResourceLocation location) {
        return new ResourceId(location.getNamespace(), location.getPath());
    }

    public static Component text(Text text) {
        Object[] args = text.args();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Text inner) args[i] = text(inner);
        }
        return text.fallback() == null ? Component.translatable(text.key(), args) : Component.translatableWithFallback(text.key(), text.fallback(), args);
    }
}
