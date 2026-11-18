package studio.merami.sliminterfacefabric.shop;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.slimcore.plates.PlatePrices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ShopFiles {
    private static final String FOLDER = "slimvehicle";
    private static final String CONFIG = "slimvehicle-shops.json";

    record Prices(PlatePrices plates, PaintShop.Prices paint) {}

    private ShopFiles() {}

    static Path file(MinecraftServer server, String name) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(name);
    }

    static void write(Path target, String text, String what) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            SlimCore.LOGGER.error("{} were not saved to {}", what, target, e);
        }
    }

    static Prices prices() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
        PlatePrices plates = PlatePrices.DEFAULT;
        PaintShop.Prices paint = PaintShop.Prices.DEFAULT;
        if (!Files.exists(file)) {
            write(file, defaults(plates, paint), "Shop prices");
            return new Prices(plates, paint);
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject plate = root.getAsJsonObject("plates");
            JsonObject colour = root.getAsJsonObject("paint");
            return new Prices(new PlatePrices(amount(plate, "full"), amount(plate, "letters"), amount(plate, "digits"), plate.get("first_free").getAsBoolean()),
                    new PaintShop.Prices(amount(colour, "body"), amount(colour, "part"), amount(colour, "interior")));
        } catch (IOException | RuntimeException e) {
            SlimCore.LOGGER.error("Shop prices were not read from {}; the default prices are used", file, e);
            return new Prices(plates, paint);
        }
    }

    private static long amount(JsonObject json, String key) {
        return Math.max(0, json.get(key).getAsLong());
    }

    private static String defaults(PlatePrices plates, PaintShop.Prices paint) {
        JsonObject plate = new JsonObject();
        plate.addProperty("full", plates.full());
        plate.addProperty("letters", plates.letters());
        plate.addProperty("digits", plates.digits());
        plate.addProperty("first_free", plates.firstFree());
        JsonObject colour = new JsonObject();
        colour.addProperty("body", paint.body());
        colour.addProperty("part", paint.part());
        colour.addProperty("interior", paint.interior());
        JsonObject root = new JsonObject();
        root.add("plates", plate);
        root.add("paint", colour);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
