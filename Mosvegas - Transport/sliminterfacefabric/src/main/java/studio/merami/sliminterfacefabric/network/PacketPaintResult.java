package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPaintResult(int vehicle, boolean ok, String key, double balance, String balanceText) implements FabricPacket {
    public static final PacketType<PacketPaintResult> TYPE = PacketType.create(SlimVehicle.id("paint_result"), PacketPaintResult::new);

    private PacketPaintResult(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readUtf(), buf.readDouble(), buf.readUtf());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(vehicle).writeBoolean(ok).writeUtf(key).writeDouble(balance).writeUtf(balanceText);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
