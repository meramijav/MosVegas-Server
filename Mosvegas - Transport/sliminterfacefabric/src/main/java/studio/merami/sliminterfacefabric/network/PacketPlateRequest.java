package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPlateRequest(int action, String region, int slot) implements FabricPacket {
    private static final int MAX_REGION = 3;
    public static final PacketType<PacketPlateRequest> TYPE = PacketType.create(SlimVehicle.id("plate_request"), PacketPlateRequest::new);

    private PacketPlateRequest(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readUtf(MAX_REGION), buf.readByte());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action).writeUtf(region, MAX_REGION).writeByte(slot);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
