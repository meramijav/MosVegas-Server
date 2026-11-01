package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketVehicleInput(int vehicle, float throttle, float brake, float steer, boolean handbrake, boolean horn) implements FabricPacket {
    public static final PacketType<PacketVehicleInput> TYPE = PacketType.create(SlimVehicle.id("input"), PacketVehicleInput::new);

    private PacketVehicleInput(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(vehicle).writeFloat(throttle).writeFloat(brake).writeFloat(steer).writeBoolean(handbrake).writeBoolean(horn);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
