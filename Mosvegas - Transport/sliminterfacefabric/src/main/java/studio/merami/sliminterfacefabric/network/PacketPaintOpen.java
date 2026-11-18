package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPaintOpen(int vehicle) implements FabricPacket {
    public static final PacketType<PacketPaintOpen> TYPE = PacketType.create(SlimVehicle.id("paint_open"), PacketPaintOpen::new);

    private PacketPaintOpen(FriendlyByteBuf buf) {
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
