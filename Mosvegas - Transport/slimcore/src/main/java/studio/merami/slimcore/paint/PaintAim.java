package studio.merami.slimcore.paint;

import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PaintAim {
    public static final double RAY = PaintJob.INTERIOR_MAX_REACH + .6;
    private static final double PART_MARGIN = .08;

    public record Aim(PaintJob.Area area, Point3D point, double distance) {
        public boolean tooClose() {
            return distance < min(area);
        }

        public boolean tooFar() {
            return distance > max(area);
        }

        public boolean inReach() {
            return !tooClose() && !tooFar();
        }
    }

    private PaintAim() {}

    public static double min(PaintJob.Area area) {
        return area == PaintJob.Area.INTERIOR ? PaintJob.INTERIOR_MIN_REACH : PaintJob.MIN_REACH;
    }

    public static double max(PaintJob.Area area) {
        return area == PaintJob.Area.INTERIOR ? PaintJob.INTERIOR_MAX_REACH : PaintJob.MAX_REACH;
    }

    public static Optional<Aim> find(EntityVehicleF_Physics vehicle, int coats, Point3D eye, Point3D look) {
        Point3D end = eye.add(look.x * RAY, look.y * RAY, look.z * RAY);
        if (PaintJob.coat(coats, PaintJob.Area.INTERIOR) < PaintJob.FULL) {
            Aim seat = null;
            for (InteractionZone zone : vehicle.interactionZones()) {
                if (zone.seat() < 0) continue;
                Optional<Point3D> point = zone.box().clip(eye, end);
                if (point.isEmpty()) continue;
                double distance = eye.distanceTo(point.get());
                if (seat == null || distance < seat.distance()) seat = new Aim(PaintJob.Area.INTERIOR, point.get(), distance);
            }
            if (seat != null) return Optional.of(seat);
        }
        if (!bodyLeft(coats)) return Optional.empty();
        Map<String, PaintJob.Area> parts = vehicle.definition().paintAreaBones;
        if (!parts.isEmpty()) return part(vehicle, parts, eye, end);
        Optional<Hull.Hit> hit = vehicle.hull().ray(eye, end);
        if (hit.isEmpty()) return Optional.empty();
        PaintJob.Area area = PaintJob.Area.of(vehicle.definition().damage.side(vehicle.worldToLocal(hit.get().point())));
        return Optional.of(new Aim(area, hit.get().point(), hit.get().distance()));
    }

    private static Optional<Aim> part(EntityVehicleF_Physics vehicle, Map<String, PaintJob.Area> parts, Point3D eye, Point3D end) {
        Aim nearest = null;
        for (Map.Entry<String, List<Hull.Box>> bone : vehicle.boneBoxes(parts.keySet()).entrySet()) {
            for (Hull.Box box : bone.getValue()) {
                Optional<Point3D> point = box.grow(PART_MARGIN).clip(eye, end);
                if (point.isEmpty()) continue;
                double distance = eye.distanceTo(point.get());
                if (nearest == null || distance < nearest.distance()) nearest = new Aim(parts.get(bone.getKey()), point.get(), distance);
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static boolean bodyLeft(int coats) {
        for (PaintJob.Area area : PaintJob.Area.values()) {
            if (area != PaintJob.Area.INTERIOR && PaintJob.coat(coats, area) < PaintJob.FULL) return true;
        }
        return false;
    }
}
