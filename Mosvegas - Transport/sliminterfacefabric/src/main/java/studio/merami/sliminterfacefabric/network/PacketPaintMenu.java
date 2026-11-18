package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPaintMenu(int vehicle, double balance, String balanceText, PaintShop.Prices prices, boolean free) implements FabricPacket {
    public static final PacketType<PacketPaintMenu> TYPE = PacketType.create(SlimVehicle.id("paint_menu"), PacketPaintMenu::new);

    private PacketPaintMenu(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readDouble(), buf.readUtf(), new PaintShop.Prices(buf.readLong(), buf.readLong(), buf.readLong()), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(vehicle).writeDouble(balance).writeUtf(balanceText);
        buf.writeLong(prices.body()).writeLong(prices.part()).writeLong(prices.interior()).writeBoolean(free);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
