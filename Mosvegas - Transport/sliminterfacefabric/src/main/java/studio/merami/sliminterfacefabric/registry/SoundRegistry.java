package studio.merami.sliminterfacefabric.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import studio.merami.sliminterfacefabric.SlimVehicle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundRegistry {
    private static final List<String> BUILT_IN = List.of(
            "engine_loop", "engine_start", "engine_stop", "engine_low", "engine_mid", "engine_high", "exhaust_pop",
            "horn", "bell", "siren", "radio", "tyre_squeal", "road_roll", "tyre_blowout", "suspension_bump", "collision",
            "door_open", "door_close", "hood_open", "hood_close", "hatch_open", "hatch_close",
            "gear_shift", "lock", "handbrake_on", "handbrake_off");
    private static final Map<String, SoundEvent> EVENTS = new ConcurrentHashMap<>();

    private SoundRegistry() {}

    public static void register() {
        for (String name : BUILT_IN) {
            ResourceLocation id = SlimVehicle.id(name);
            Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
        }
    }

    public static SoundEvent event(String id) {
        return EVENTS.computeIfAbsent(id, key -> {
            ResourceLocation location = new ResourceLocation(key);
            return BuiltInRegistries.SOUND_EVENT.getOptional(location).orElseGet(() -> SoundEvent.createVariableRangeEvent(location));
        });
    }
}
