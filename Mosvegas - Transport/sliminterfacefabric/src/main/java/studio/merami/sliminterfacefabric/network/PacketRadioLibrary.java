package studio.merami.sliminterfacefabric.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import studio.merami.slimcore.systems.RadioLibrary;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.util.List;

public record PacketRadioLibrary(List<RadioLibrary.Station> stations) implements FabricPacket {
    public static final PacketType<PacketRadioLibrary> TYPE = PacketType.create(SlimVehicle.id("radio_library"), PacketRadioLibrary::new);

    private PacketRadioLibrary(FriendlyByteBuf buf) {
        this(buf.readList(in -> new RadioLibrary.Station(in.readUtf(), in.readList(PacketRadioLibrary::readTrack))));
    }

    private static RadioLibrary.Track readTrack(FriendlyByteBuf in) {
        return new RadioLibrary.Track(in.readUtf(), in.readUtf(PacketRadioRequest.HASH), in.readVarLong(), in.readDouble(), null);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(stations, (out, station) -> {
            out.writeUtf(station.name());
            out.writeCollection(station.tracks(), (into, track) -> {
                into.writeUtf(track.title());
                into.writeUtf(track.hash(), PacketRadioRequest.HASH);
                into.writeVarLong(track.size());
                into.writeDouble(track.seconds());
            });
        });
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
