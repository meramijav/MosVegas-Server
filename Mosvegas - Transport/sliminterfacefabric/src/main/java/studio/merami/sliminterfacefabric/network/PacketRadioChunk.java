package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketRadioChunk(String hash, int offset, byte[] data) implements FabricPacket {
    public static final PacketType<PacketRadioChunk> TYPE = PacketType.create(SlimVehicle.id("radio_chunk"), PacketRadioChunk::new);
    public static final int SIZE = 32 << 10;

    private PacketRadioChunk(FriendlyByteBuf buf) {
        this(buf.readUtf(PacketRadioRequest.HASH), buf.readVarInt(), buf.readByteArray(SIZE));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(hash, PacketRadioRequest.HASH).writeVarInt(offset).writeByteArray(data);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
