package studio.merami.sliminterfacefabric.client.paint;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.paint.PaintJob;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PaintReveal {
    private static final double EASE_MS = 160;
    private static final float SOFT = .55f;
    private static final float WOBBLE = .14f;
    private static final float DONE = .995f;
    private static final Map<PaintJob.Area, Front> FRONTS = new EnumMap<>(PaintJob.Area.class);
    private static UUID vehicleId;

    public static final class Front {
        private final float x;
        private final float y;
        private final float z;
        private float shown;
        private float far;
        private float edge = -SOFT;
        private long easedAt = Util.getMillis();

        private Front(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private void advance(float share, long now) {
            shown = share < shown ? share : shown + (share - shown) * (1 - (float) Math.exp(-(now - easedAt) / EASE_MS));
            easedAt = now;
            edge = -SOFT + shown * (far + 2 * SOFT);
        }

        public float at(float px, float py, float pz) {
            float dx = px - x;
            float dy = py - y;
            float dz = pz - z;
            float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz) + WOBBLE * (float) (Math.sin(px * 5.3 + pz * 3.1) * Math.sin(py * 6.7 + pz * 4.3));
            far = Math.max(far, distance);
            float u = Mth.clamp((edge + SOFT - distance) / (2 * SOFT), 0, 1);
            return u * u * (3 - 2 * u);
        }
    }

    private PaintReveal() {}

    public static void aim(EntityVehicleF_Physics vehicle, PaintJob.Area area, Point3D point) {
        if (!vehicle.uuid().equals(vehicleId)) {
            vehicleId = vehicle.uuid();
            FRONTS.clear();
        }
        Front known = FRONTS.get(area);
        if (known != null && known.shown > 0) return;
        double scale = vehicle.scale();
        Point3D local = vehicle.worldToLocal(point);
        FRONTS.put(area, new Front((float) (-local.x / scale), (float) (local.y / scale), (float) (-local.z / scale)));
    }

    public static Front front(EntityVehicleF_Physics vehicle, PaintJob.Area area, float share) {
        if (!vehicle.uuid().equals(vehicleId)) return null;
        Front front = FRONTS.get(area);
        if (front == null) return null;
        front.advance(share, Util.getMillis());
        if (share <= 0 && front.shown <= 0) return null;
        return share >= 1 && front.shown >= DONE ? null : front;
    }

    public static void clear() {
        FRONTS.clear();
        vehicleId = null;
    }
}
