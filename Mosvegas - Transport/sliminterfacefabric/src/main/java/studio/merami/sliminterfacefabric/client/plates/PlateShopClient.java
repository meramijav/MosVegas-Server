package studio.merami.sliminterfacefabric.client.plates;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import studio.merami.slimcore.SlimCore;
import studio.merami.sliminterfacefabric.network.PacketPlateMenu;
import studio.merami.sliminterfacefabric.network.PacketPlateRequest;

public final class PlateShopClient {
    private static final String[] SPINS = {"full", "letters", "digits"};

    public record Menu(String result, boolean ok, boolean hasCar, String style, String plate, boolean free, String balance, double balanceValue, long[] prices,
                       String[] priceTexts) {
        public boolean numbered() {
            return !plate.isEmpty();
        }
    }

    private PlateShopClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketPlateMenu.TYPE, (packet, player, sender) -> accept(packet.json()));
    }

    public static void request(int action, String region) {
        ClientPlayNetworking.send(new PacketPlateRequest(action, region));
    }

    private static void accept(String json) {
        Menu menu;
        try {
            menu = parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            SlimCore.LOGGER.error("Bad number plate menu from the server: {}", e.getMessage());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlateShopScreen screen) screen.accept(menu);
        else if (menu.hasCar) mc.setScreen(new PlateShopScreen(menu));
        else if (mc.player != null && !menu.result.isEmpty()) mc.player.displayClientMessage(Component.translatable(menu.result), true);
    }

    private static Menu parse(JsonObject json) {
        long[] prices = new long[SPINS.length];
        String[] texts = new String[SPINS.length];
        JsonObject priceJson = json.has("prices") ? json.getAsJsonObject("prices") : new JsonObject();
        for (int i = 0; i < SPINS.length; i++) {
            JsonObject entry = priceJson.has(SPINS[i]) ? priceJson.getAsJsonObject(SPINS[i]) : new JsonObject();
            prices[i] = entry.has("amount") ? entry.get("amount").getAsLong() : 0;
            texts[i] = entry.has("text") ? entry.get("text").getAsString() : Long.toString(prices[i]);
        }
        return new Menu(string(json, "result"), json.has("ok") && json.get("ok").getAsBoolean(), json.has("vehicle"), json.has("style") ? string(json, "style") : "standard",
                string(json, "plate"), json.has("free") && json.get("free").getAsBoolean(), json.has("balance_text") ? string(json, "balance_text") : null,
                json.has("balance") ? json.get("balance").getAsDouble() : Double.NaN, prices, texts);
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }
}
