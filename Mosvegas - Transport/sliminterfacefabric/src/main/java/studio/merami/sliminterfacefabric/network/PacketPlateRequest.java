package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPlateRequest(int action, int slot) implements FabricPacket {
    public static final PacketType<PacketPlateRequest> TYPE = PacketType.create(SlimVehicle.id("plate_request"), PacketPlateRequest::new);

    private PacketPlateRequest(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readByte());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action).writeByte(slot);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
