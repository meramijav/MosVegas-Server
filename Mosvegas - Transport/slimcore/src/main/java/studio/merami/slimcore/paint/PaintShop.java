package studio.merami.slimcore.paint;

import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PaintShop {
    public static final String INTERIOR = "interior";
    public static final int NONE = -1;
    public static final double MAX_SPEED = .035;
    public static final int MAX_PARTS = 32;
    public static final double FLOOR_TOLERANCE = .5;

    private PaintShop() {}

    public record Prices(long body, long part, long interior) {
        public static final Prices DEFAULT = new Prices(25000, 4000, 8000);
    }

    public record Order(int body, Map<String, Integer> parts, int interior) {
        public static final Order EMPTY = new Order(NONE, Map.of(), NONE);

        public Order {
            parts = Map.copyOf(parts);
        }

        public boolean isEmpty() {
            return body == NONE && parts.isEmpty() && interior == NONE;
        }

        public long price(Prices prices) {
            return (body == NONE ? 0 : prices.body) + parts.size() * prices.part + (interior == NONE ? 0 : prices.interior);
        }

        public Order withBody(int rgb) {
            return new Order(rgb, rgb == NONE ? parts : Map.of(), interior);
        }

        public Order withPart(String bone, int rgb) {
            Map<String, Integer> next = new LinkedHashMap<>(parts);
            if (rgb == NONE) next.remove(bone);
            else next.put(bone, rgb & 0xffffff);
            return new Order(body, next, interior);
        }

        public Order withInterior(int rgb) {
            return new Order(body, parts, rgb);
        }
        public int colorOf(EntityVehicleF_Physics vehicle, String bone, int fallback) {
            if (vehicle.definition().interiorPaintBones.contains(bone)) return interior == NONE ? fallback : interior;
            Integer own = parts.get(bone);
            if (own != null) return own;
            return body == NONE ? fallback : body;
        }
    }
    public static boolean parked(EntityVehicleF_Physics vehicle, PaintStations stations, String world) {
        boolean running = vehicle.flag(EntityVehicleF_Physics.ENGINE);
        if (vehicle.wrecked() || running || Math.abs(vehicle.speed()) > MAX_SPEED) return false;
        var position = vehicle.position();
        double y = position.y + FLOOR_TOLERANCE;
        return world == null ? zoneAt(stations, position.x, y, position.z) : stations.zoneAt(world, position.x, y, position.z) != null;
    }

    private static boolean zoneAt(PaintStations stations, double x, double y, double z) {
        for (PaintStations.Station station : stations.stations) {
            if (station.zone().contains(x, y, z)) return true;
        }
        return false;
    }
    public static boolean customer(EntityVehicleF_Physics vehicle, IWrapperPlayer player) {
        return vehicle.driver(player) && vehicle.isOwner(player);
    }
    public static Set<String> brokenParts(EntityVehicleF_Physics vehicle) {
        JSONVehicle definition = vehicle.definition();
        Set<String> named = new HashSet<>();
        Set<String> broken = new HashSet<>();
        boolean body = false;
        for (AJSONInteractable.Zone zone : definition.damage.zones) {
            String bone = paintBone(definition, zone.id) ? zone.id : paintBone(definition, zone.jam) ? zone.jam : null;
            if (bone != null) named.add(bone);
            if (vehicle.zoneDamage(zone.index) <= 0) continue;
            if (bone != null) broken.add(bone);
            else body = true;
        }
        if (body) {
            for (String bone : definition.paintBones) {
                if (!named.contains(bone)) broken.add(bone);
            }
        }
        return broken;
    }
    private static boolean paintBone(JSONVehicle definition, String bone) {
        return bone != null && definition.paintBones.contains(bone);
    }

    public static String check(EntityVehicleF_Physics vehicle, Order order) {
        if (order.isEmpty()) return "message.slimvehicle.paint.empty";
        if (order.parts.size() > MAX_PARTS) return "message.slimvehicle.paint.empty";
        Set<String> broken = brokenParts(vehicle);
        if (order.body != NONE && !broken.isEmpty()) return "message.slimvehicle.paint.repair_first";
        for (String bone : order.parts.keySet()) {
            if (!vehicle.definition().paintBones.contains(bone)) return "message.slimvehicle.paint.empty";
            if (broken.contains(bone)) return "message.slimvehicle.paint.repair_first";
        }
        if (order.interior != NONE && vehicle.definition().interiorPaintBones.isEmpty()) return "message.slimvehicle.paint.empty";
        return null;
    }

    public static void apply(EntityVehicleF_Physics vehicle, Order order) {
        if (order.body != NONE) vehicle.setColor(order.body);
        for (Map.Entry<String, Integer> part : order.parts.entrySet()) vehicle.setPartColor(part.getKey(), part.getValue());
        if (order.interior != NONE) vehicle.setInteriorColor(order.interior);
    }
}
