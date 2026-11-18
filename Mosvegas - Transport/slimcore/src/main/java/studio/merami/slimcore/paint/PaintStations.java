package studio.merami.slimcore.paint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class PaintStations {
    public static final int MIN_ZONE_SIDE = 3;
    public static final double STAND_REACH = 16;
    public static final double GUN_RANGE = 24;
    public static final PaintStations EMPTY = new PaintStations(List.of());

    public record Zone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }

        public boolean containsBlock(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public boolean overlaps(Zone other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        public double distanceSqr(double x, double y, double z) {
            double dx = Math.max(Math.max(minX - x, 0), x - (maxX + 1));
            double dy = Math.max(Math.max(minY - y, 0), y - (maxY + 1));
            double dz = Math.max(Math.max(minZ - z, 0), z - (maxZ + 1));
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Stand(int x, int y, int z, int facing) {
        public boolean at(int x, int y, int z) {
            return this.x == x && this.y == y && this.z == z;
        }

        public double distanceSqr(double x, double y, double z) {
            double dx = this.x + .5 - x;
            double dy = this.y + .5 - y;
            double dz = this.z + .5 - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Station(int id, String world, String region, Zone zone, List<Stand> stands) {
        public Station {
            stands = List.copyOf(stands);
        }
    }

    public static final class Rejected extends RuntimeException {
        public final String key;

        public Rejected(String key) {
            super(key, null, false, false);
            this.key = key;
        }
    }

    public final List<Station> stations;

    private PaintStations(List<Station> stations) {
        this.stations = List.copyOf(stations);
    }

    public PaintStations addRegion(String world, String region, Zone zone) {
        if (regionStation(world, region) != null) throw new Rejected("message.slimvehicle.station.region_taken");
        if (zone.sizeX() < MIN_ZONE_SIDE || zone.sizeZ() < MIN_ZONE_SIDE) throw new Rejected("message.slimvehicle.station.zone_small");
        for (Station station : stations) {
            if (station.world.equals(world) && station.zone.overlaps(zone)) throw new Rejected("message.slimvehicle.station.zone_overlap");
            if (!station.world.equals(world)) continue;
            for (Stand stand : station.stands) {
                if (zone.containsBlock(stand.x, stand.y, stand.z)) throw new Rejected("message.slimvehicle.station.zone_overlap");
            }
        }
        int id = 1;
        for (Station station : stations) id = Math.max(id, station.id + 1);
        List<Station> next = new ArrayList<>(stations);
        next.add(new Station(id, world, region, zone, List.of()));
        return new PaintStations(next);
    }

    public Station regionStation(String world, String region) {
        for (Station station : stations) {
            if (station.world.equals(world) && station.region.equals(region)) return station;
        }
        return null;
    }

    public PaintStations addStand(String world, Stand stand) {
        Station target = null;
        double best = STAND_REACH * STAND_REACH;
        for (Station station : stations) {
            if (!station.world.equals(world)) continue;
            if (station.zone.containsBlock(stand.x, stand.y, stand.z)) throw new Rejected("message.slimvehicle.station.stand_in_zone");
            if (standAt(world, stand.x, stand.y, stand.z) != null) throw new Rejected("message.slimvehicle.station.stand_taken");
            double distance = station.zone.distanceSqr(stand.x + .5, stand.y + .5, stand.z + .5);
            if (distance <= best) {
                best = distance;
                target = station;
            }
        }
        if (target == null) throw new Rejected("message.slimvehicle.station.no_zone");
        List<Stand> stands = new ArrayList<>(target.stands);
        stands.add(stand);
        return replace(target, new Station(target.id, target.world, target.region, target.zone, stands));
    }

    public PaintStations removeStand(String world, int x, int y, int z) {
        for (Station station : stations) {
            if (!station.world.equals(world)) continue;
            List<Stand> stands = new ArrayList<>(station.stands);
            if (stands.removeIf(stand -> stand.at(x, y, z))) return replace(station, new Station(station.id, station.world, station.region, station.zone, stands));
        }
        return this;
    }

    public PaintStations removeStation(int id) {
        List<Station> next = new ArrayList<>(stations);
        next.removeIf(station -> station.id == id);
        return next.size() == stations.size() ? this : new PaintStations(next);
    }

    private PaintStations replace(Station old, Station updated) {
        List<Station> next = new ArrayList<>(stations);
        next.set(next.indexOf(old), updated);
        return new PaintStations(next);
    }

    public Station station(int id) {
        for (Station station : stations) {
            if (station.id == id) return station;
        }
        return null;
    }

    public Station zoneAt(String world, double x, double y, double z) {
        for (Station station : stations) {
            if (station.world.equals(world) && station.zone.contains(x, y, z)) return station;
        }
        return null;
    }

    public Station zoneAtBlock(String world, int x, int y, int z) {
        for (Station station : stations) {
            if (station.world.equals(world) && station.zone.containsBlock(x, y, z)) return station;
        }
        return null;
    }

    public Stand standAt(String world, int x, int y, int z) {
        for (Station station : stations) {
            if (!station.world.equals(world)) continue;
            for (Stand stand : station.stands) {
                if (stand.at(x, y, z)) return stand;
            }
        }
        return null;
    }

    public double nearestStandSqr(String world, double x, double y, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (Station station : stations) {
            if (!station.world.equals(world)) continue;
            for (Stand stand : station.stands) best = Math.min(best, stand.distanceSqr(x, y, z));
        }
        return best;
    }

    public PaintStations inWorld(String world) {
        List<Station> found = new ArrayList<>();
        for (Station station : stations) {
            if (station.world.equals(world)) found.add(station);
        }
        return new PaintStations(found);
    }

    public static int facingTowards(float playerYaw) {
        return Math.floorMod(Math.round((playerYaw + 180) / 90f), 4) * 90;
    }

    public String toJson() {
        JsonArray all = new JsonArray();
        for (Station station : stations) {
            JsonObject object = new JsonObject();
            object.addProperty("id", station.id);
            object.addProperty("world", station.world);
            object.addProperty("region", station.region);
            Zone zone = station.zone;
            object.add("zone", ints(zone.minX, zone.minY, zone.minZ, zone.maxX, zone.maxY, zone.maxZ));
            JsonArray stands = new JsonArray();
            for (Stand stand : station.stands) stands.add(ints(stand.x, stand.y, stand.z, stand.facing));
            object.add("stands", stands);
            all.add(object);
        }
        JsonObject root = new JsonObject();
        root.add("stations", all);
        return root.toString();
    }

    public static PaintStations fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Station> stations = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("stations")) {
            JsonObject object = element.getAsJsonObject();
            JsonArray zone = object.getAsJsonArray("zone");
            List<Stand> stands = new ArrayList<>();
            for (JsonElement raw : object.getAsJsonArray("stands")) {
                JsonArray stand = raw.getAsJsonArray();
                stands.add(new Stand(stand.get(0).getAsInt(), stand.get(1).getAsInt(), stand.get(2).getAsInt(), stand.get(3).getAsInt()));
            }
            String region = object.has("region") ? object.get("region").getAsString() : "";
            stations.add(new Station(object.get("id").getAsInt(), object.get("world").getAsString(), region, new Zone(zone.get(0).getAsInt(), zone.get(1).getAsInt(),
                    zone.get(2).getAsInt(), zone.get(3).getAsInt(), zone.get(4).getAsInt(), zone.get(5).getAsInt()), stands));
        }
        return new PaintStations(stations);
    }

    private static JsonArray ints(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }
}
