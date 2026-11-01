package studio.merami.sliminterfacefabric.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.util.LinkedHashMap;
import java.util.Map;

public record PacketVehicleConfigs(String json) implements FabricPacket {
    public static final PacketType<PacketVehicleConfigs> TYPE = PacketType.create(SlimVehicle.id("configs"), PacketVehicleConfigs::new);
    private static final int MAX_LENGTH = 1 << 20;

    private PacketVehicleConfigs(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_LENGTH));
    }

    public static PacketVehicleConfigs of(Map<String, JsonObject> configs) {
        JsonObject all = new JsonObject();
        configs.forEach(all::add);
        return new PacketVehicleConfigs(all.toString());
    }

    public Map<String, JsonObject> configs() {
        Map<String, JsonObject> configs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonObject()) configs.put(entry.getKey(), entry.getValue().getAsJsonObject());
        }
        return configs;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(json, MAX_LENGTH);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
