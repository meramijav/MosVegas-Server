package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketVehicleAction(int vehicle, VehicleAction action) implements FabricPacket {
    public static final PacketType<PacketVehicleAction> TYPE = PacketType.create(SlimVehicle.id("action"), PacketVehicleAction::new);

    private PacketVehicleAction(FriendlyByteBuf buf) {
        this(buf.readInt(), VehicleAction.byId(buf.readByte()));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(vehicle).writeByte(action.ordinal());
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
