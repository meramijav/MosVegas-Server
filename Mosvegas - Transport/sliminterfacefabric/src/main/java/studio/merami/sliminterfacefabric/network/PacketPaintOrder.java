package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.util.LinkedHashMap;
import java.util.Map;

public record PacketPaintOrder(int vehicle, PaintShop.Order order) implements FabricPacket {
    private static final int MAX_BONE = 64;
    public static final PacketType<PacketPaintOrder> TYPE = PacketType.create(SlimVehicle.id("paint_order"), PacketPaintOrder::new);

    private PacketPaintOrder(FriendlyByteBuf buf) {
        this(buf.readInt(), read(buf));
    }

    private static PaintShop.Order read(FriendlyByteBuf buf) {
        int body = buf.readInt();
        int interior = buf.readInt();
        int count = Math.min(buf.readVarInt(), PaintShop.MAX_PARTS + 1);
        Map<String, Integer> parts = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) parts.put(buf.readUtf(MAX_BONE), buf.readInt());
        return new PaintShop.Order(body, parts, interior);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(vehicle).writeInt(order.body()).writeInt(order.interior());
        buf.writeVarInt(order.parts().size());
        order.parts().forEach((bone, rgb) -> buf.writeUtf(bone, MAX_BONE).writeInt(rgb));
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
