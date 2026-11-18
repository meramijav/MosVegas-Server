package studio.merami.slimcore.paint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import studio.merami.slimcore.baseclasses.DamageModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PaintJob(UUID owner, String ownerName, UUID vehicle, PaintShop.Order order, long price, long lastProgress, int coats) {
    public enum Area {
        FRONT,
        REAR,
        LEFT,
        RIGHT,
        TOP,
        INTERIOR;

        public static Area of(DamageModel.Side side) {
            return values()[side.ordinal()];
        }
    }

    public static final long TIMEOUT_MS = 120_000;
    public static final int FULL = 30;
    public static final int STEP = 2;
    public static final long SPRAY_INTERVAL_MS = 150;
    public static final double MIN_REACH = .6;
    public static final double MAX_REACH = 4;
    public static final double INTERIOR_MIN_REACH = .3;
    public static final double INTERIOR_MAX_REACH = 4;
    private static final int BITS = 5;

    public static int initialCoats(PaintShop.Order order) {
        return initialCoats(order, List.of());
    }

    public static int initialCoats(PaintShop.Order order, Collection<Area> sides) {
        boolean body = order.body() != PaintShop.NONE || !order.parts().isEmpty();
        boolean interior = order.interior() != PaintShop.NONE;
        int coats = 0;
        for (Area area : Area.values()) {
            boolean needed = area == Area.INTERIOR ? interior : body && (sides.isEmpty() || sides.contains(area));
            if (!needed) coats |= FULL << area.ordinal() * BITS;
        }
        return coats;
    }

    public boolean stale(long now) {
        return now - lastProgress >= TIMEOUT_MS;
    }

    public int secondsLeft(long now) {
        return (int) Math.max(0, (TIMEOUT_MS - (now - lastProgress) + 999) / 1000);
    }

    public PaintJob sprayed(Area area, long now) {
        int current = coat(coats, area);
        if (current >= FULL) return this;
        int next = Math.min(FULL, current + STEP);
        int shift = area.ordinal() * BITS;
        int packed = coats & ~(((1 << BITS) - 1) << shift) | next << shift;
        return new PaintJob(owner, ownerName, vehicle, order, price, now, packed);
    }

    public boolean done() {
        for (Area area : Area.values()) {
            if (coat(coats, area) < FULL) return false;
        }
        return true;
    }

    public static int coat(int coats, Area area) {
        return coats >>> area.ordinal() * BITS & (1 << BITS) - 1;
    }

    public static float share(int coats, Area area) {
        return coats < 0 ? 0 : Math.min(1, coat(coats, area) / (float) FULL);
    }

    public static float progress(int coats, boolean body, boolean interior) {
        if (coats < 0) return 0;
        float sum = 0;
        int count = 0;
        for (Area area : Area.values()) {
            if (area == Area.INTERIOR ? !interior : !body) continue;
            sum += share(coats, area);
            count++;
        }
        return count == 0 ? 1 : sum / count;
    }

    public static String toJson(Collection<PaintJob> jobs) {
        JsonArray all = new JsonArray();
        for (PaintJob job : jobs) {
            JsonObject object = new JsonObject();
            object.addProperty("owner", job.owner.toString());
            object.addProperty("owner_name", job.ownerName);
            object.addProperty("vehicle", job.vehicle.toString());
            object.addProperty("price", job.price);
            object.addProperty("body", job.order.body());
            object.addProperty("interior", job.order.interior());
            JsonObject parts = new JsonObject();
            job.order.parts().forEach(parts::addProperty);
            object.add("parts", parts);
            all.add(object);
        }
        JsonObject root = new JsonObject();
        root.add("jobs", all);
        return root.toString();
    }

    public static List<PaintJob> fromJson(String json, long now) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<PaintJob> jobs = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("jobs")) {
            JsonObject object = element.getAsJsonObject();
            Map<String, Integer> parts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> part : object.getAsJsonObject("parts").entrySet()) parts.put(part.getKey(), part.getValue().getAsInt());
            PaintShop.Order order = new PaintShop.Order(object.get("body").getAsInt(), parts, object.get("interior").getAsInt());
            jobs.add(new PaintJob(UUID.fromString(object.get("owner").getAsString()), object.get("owner_name").getAsString(),
                    UUID.fromString(object.get("vehicle").getAsString()), order, object.get("price").getAsLong(), now, 0));
        }
        return jobs;
    }
}
