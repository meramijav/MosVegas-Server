package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketRadioRequest(String hash, int offset) implements FabricPacket {
    public static final PacketType<PacketRadioRequest> TYPE = PacketType.create(SlimVehicle.id("radio_request"), PacketRadioRequest::new);
    static final int HASH = 40;

    private PacketRadioRequest(FriendlyByteBuf buf) {
        this(buf.readUtf(HASH), buf.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(hash, HASH).writeVarInt(offset);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
