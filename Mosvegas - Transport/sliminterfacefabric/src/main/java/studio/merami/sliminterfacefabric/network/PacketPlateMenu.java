package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.sliminterfacefabric.SlimVehicle;

public record PacketPlateMenu(String json) implements FabricPacket {
    private static final int MAX_LENGTH = 1 << 16;
    public static final PacketType<PacketPlateMenu> TYPE = PacketType.create(SlimVehicle.id("plate_menu"), PacketPlateMenu::new);

    private PacketPlateMenu(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_LENGTH));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(json, MAX_LENGTH);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
