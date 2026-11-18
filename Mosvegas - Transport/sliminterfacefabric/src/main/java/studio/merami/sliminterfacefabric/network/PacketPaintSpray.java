package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPaintSpray(int vehicle) implements FabricPacket {
    public static final PacketType<PacketPaintSpray> TYPE = PacketType.create(SlimVehicle.id("paint_spray"), PacketPaintSpray::new);

    private PacketPaintSpray(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(vehicle);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
