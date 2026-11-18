package studio.merami.slimcore.plates;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlateRegistry {
    public record Car(UUID owner, String style, String plate, boolean pending) {
        public Car {
            plate = PlateNumber.valid(plate) ? plate : PlateNumber.NONE;
        }

        public boolean numbered() {
            return !plate.isEmpty();
        }
    }

    private final Map<UUID, Car> cars = new LinkedHashMap<>();
    private final Map<UUID, UUID> drivers = new LinkedHashMap<>();
    private final Map<String, UUID> plates = new HashMap<>();

    public synchronized Car car(UUID vehicle) {
        return cars.get(vehicle);
    }

    public synchronized UUID lastDriven(UUID player) {
        return drivers.get(player);
    }

    public synchronized UUID holder(String plate) {
        return plates.get(plate);
    }

    public synchronized boolean taken(String plate) {
        return plates.containsKey(plate);
    }

    public synchronized boolean drove(UUID player, UUID vehicle, UUID owner, String style, String plate) {
        boolean changed = !vehicle.equals(drivers.put(player, vehicle));
        return seen(vehicle, owner, style, plate) || changed;
    }

    public synchronized boolean seen(UUID vehicle, UUID owner, String style, String plate) {
        Car old = cars.get(vehicle);
        if (old != null && old.pending) {
            Car next = new Car(owner, style, old.plate, true);
            if (next.equals(old)) return false;
            cars.put(vehicle, next);
            return true;
        }
        Car next = new Car(owner, style, plate, false);
        if (next.numbered() && !vehicle.equals(plates.getOrDefault(next.plate, vehicle))) next = new Car(owner, style, PlateNumber.NONE, false);
        if (next.equals(old)) return false;
        if (old != null && old.numbered()) plates.remove(old.plate, vehicle);
        cars.put(vehicle, next);
        if (next.numbered()) plates.put(next.plate, vehicle);
        return true;
    }

    public synchronized void assign(UUID vehicle, String plate) {
        Car old = cars.get(vehicle);
        if (old == null) throw new IllegalStateException("Vehicle " + vehicle + " has never been seen");
        if (old.numbered()) plates.remove(old.plate, vehicle);
        cars.put(vehicle, new Car(old.owner, old.style, plate, true));
        plates.put(plate, vehicle);
    }

    public synchronized boolean applied(UUID vehicle, String plate) {
        Car old = cars.get(vehicle);
        if (old == null || !old.pending || !old.plate.equals(plate)) return false;
        cars.put(vehicle, new Car(old.owner, old.style, old.plate, false));
        return true;
    }

    public synchronized String pending(UUID vehicle) {
        Car car = cars.get(vehicle);
        return car != null && car.pending ? car.plate : null;
    }

    public synchronized boolean forget(UUID vehicle) {
        Car old = cars.remove(vehicle);
        if (old == null) return false;
        if (old.numbered()) plates.remove(old.plate, vehicle);
        drivers.values().removeIf(vehicle::equals);
        return true;
    }

    public synchronized String toJson() {
        JsonObject root = new JsonObject();
        JsonObject carsJson = new JsonObject();
        cars.forEach((vehicle, car) -> {
            JsonObject json = new JsonObject();
            json.addProperty("owner", car.owner == null ? "" : car.owner.toString());
            json.addProperty("style", car.style);
            json.addProperty("plate", car.plate);
            if (car.pending) json.addProperty("pending", true);
            carsJson.add(vehicle.toString(), json);
        });
        JsonObject driversJson = new JsonObject();
        drivers.forEach((player, vehicle) -> driversJson.addProperty(player.toString(), vehicle.toString()));
        root.add("cars", carsJson);
        root.add("drivers", driversJson);
        return root.toString();
    }

    public static PlateRegistry fromJson(String text) {
        PlateRegistry registry = new PlateRegistry();
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        if (root.has("cars")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("cars").entrySet()) {
                JsonObject json = entry.getValue().getAsJsonObject();
                String owner = json.has("owner") ? json.get("owner").getAsString() : "";
                Car car = new Car(owner.isEmpty() ? null : UUID.fromString(owner), json.get("style").getAsString(), json.get("plate").getAsString(),
                        json.has("pending") && json.get("pending").getAsBoolean());
                UUID vehicle = UUID.fromString(entry.getKey());
                if (car.numbered() && registry.plates.containsKey(car.plate)) car = new Car(car.owner, car.style, PlateNumber.NONE, false);
                registry.cars.put(vehicle, car);
                if (car.numbered()) registry.plates.put(car.plate, vehicle);
            }
        }
        if (root.has("drivers")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("drivers").entrySet()) {
                registry.drivers.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue().getAsString()));
            }
        }
        return registry;
    }
}
