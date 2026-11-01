package ru.mosvegas.casino.client.dealer;

import net.minecraft.network.PacketByteBuf;

import java.util.HashMap;
import java.util.Map;

/** Dealer looks sent by the server (config may change without a client update). */
public final class DealerProfiles {
    public record Profile(String id, String name, boolean slim, String skin, String model, String animations) {}

    private static final Map<String, Profile> PROFILES = new HashMap<>();

    private DealerProfiles() {}

    public static void read(PacketByteBuf buf) {
        Map<String, Profile> next = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Profile p = new Profile(buf.readString(), buf.readString(), buf.readBoolean(), buf.readString(), buf.readString(), buf.readString());
            next.put(p.id(), p);
        }
        PROFILES.clear();
        PROFILES.putAll(next);
    }

    public static Profile get(String id) { return PROFILES.get(id); }

    public static void clear() { PROFILES.clear(); }
}
