package studio.merami.sliminterfacefabric.client;

import net.fabricmc.loader.api.FabricLoader;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.jsondefs.JSONVehicle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class ClientConfig {
    public enum Lights {
        FULL,
        OFF
    }

    public enum Headlights {
        PACK,
        HALOGEN,
        LED
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("slimvehicle-client.properties");
    private static final String[] KEYS = {"lights", "headlights", "hints"};
    public static Lights lights = Lights.FULL;
    public static Headlights headlights = Headlights.PACK;
    public static boolean hints = true;

    private ClientConfig() {}

    public static void load() {
        boolean complete = false;
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                Properties properties = new Properties();
                properties.load(reader);
                lights = "off".equals(value(properties, "lights", "full")) ? Lights.OFF : Lights.FULL;
                headlights = switch (value(properties, "headlights", "pack")) {
                    case JSONVehicle.HEADLIGHT_HALOGEN -> Headlights.HALOGEN;
                    case JSONVehicle.HEADLIGHT_LED -> Headlights.LED;
                    default -> Headlights.PACK;
                };
                hints = Boolean.parseBoolean(properties.getProperty("hints", "true").trim());
                complete = true;
                for (String key : KEYS) complete &= properties.containsKey(key);
            } catch (IOException e) {
                SlimCore.LOGGER.warn("Could not read vehicle preferences", e);
                return;
            }
        }
        if (!complete) save();
    }

    private static String value(Properties properties, String key, String fallback) {
        return properties.getProperty(key, fallback).trim().toLowerCase(Locale.ROOT);
    }

    public static boolean led(JSONVehicle.Lamps lamps) {
        return headlights == Headlights.PACK ? lamps != null && JSONVehicle.HEADLIGHT_LED.equals(lamps.headlightType) : headlights == Headlights.LED;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                Properties properties = new Properties();
                properties.setProperty("lights", lights.name().toLowerCase(Locale.ROOT));
                properties.setProperty("headlights", headlights.name().toLowerCase(Locale.ROOT));
                properties.setProperty("hints", Boolean.toString(hints));
                properties.store(writer, "lights = full | off, the screen-space lamp pass, read at start; "
                        + "headlights = pack | halogen | led, pack keeps each vehicle's lamps.headlight_type, halogen is warm yellowish, led is cold white; "
                        + "hints = true | false, the key hints beside the dashboard");
            }
        } catch (IOException e) {
            SlimCore.LOGGER.warn("Could not save vehicle preferences", e);
        }
    }
}
