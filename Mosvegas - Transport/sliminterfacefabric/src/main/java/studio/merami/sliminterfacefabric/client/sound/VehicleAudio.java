package studio.merami.sliminterfacefabric.client.sound;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONSounds;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.registry.SoundRegistry;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class VehicleAudio extends AbstractTickableSoundInstance implements FabricSoundInstance {
    public enum Kind {
        ENGINE(EntityVehicleF_Physics.ENGINE),
        ENGINE_INSIDE(EntityVehicleF_Physics.ENGINE),
        SIREN(EntityVehicleF_Physics.SIREN),
        RADIO(EntityVehicleF_Physics.RADIO),
        HORN(EntityVehicleF_Physics.HORN),
        ROAD(0),
        SKID(0);

        final int flag;

        Kind(int flag) {
            this.flag = flag;
        }

        public int flag() {
            return flag;
        }

        boolean engine() {
            return this == ENGINE || this == ENGINE_INSIDE;
        }
    }

    private static final float RADIO = .8f;
    private static final float RADIO_SHUT = .12f;
    private static final float HORN = 2f;
    private static final String STATION_SOUND = "slimvehicle:radio_station";
    private static final float KMH = 72;
    private static final float FULL_PULL = .006f;
    private static final float FULL_REGEN = .012f;
    private static final float LOAD_EASE = .15f;
    private final EntityVehicleF_Physics vehicle;
    private final JSONVehicle definition;
    private final Kind kind;
    private final JSONSounds.EngineLayer layer;
    private int silentTicks;
    private int offTicks;
    private float lastRpm;
    private float lastSpeed = -1;
    private float load;
    private volatile float shut;
    private final RadioStations.Tuned tuned;
    private final int station;
    private int generation;

    public VehicleAudio(EntityVehicleF_Physics vehicle, SoundEvent event, Kind kind, JSONSounds.EngineLayer layer) {
        this(vehicle, event, kind, layer, null);
    }

    private VehicleAudio(EntityVehicleF_Physics vehicle, SoundEvent event, Kind kind, JSONSounds.EngineLayer layer, RadioStations.Tuned tuned) {
        super(event, SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        this.tuned = tuned;
        this.station = vehicle.radioStation();
        this.generation = RadioStations.generation();
        this.definition = vehicle.definition();
        this.kind = kind;
        this.layer = layer;
        looping = true;
        delay = 0;
        volume = 0;
        if (kind == Kind.ENGINE_INSIDE) {
            relative = true;
            attenuation = Attenuation.NONE;
        }
        tick();
    }

    public static VehicleAudio radio(EntityVehicleF_Physics vehicle, String sound) {
        RadioStations.Tuned tuned = playable(vehicle.radioStation());
        return new VehicleAudio(vehicle, SoundRegistry.event(tuned == null ? sound : STATION_SOUND), Kind.RADIO, null, tuned);
    }

    private static RadioStations.Tuned playable(int station) {
        Minecraft mc = Minecraft.getInstance();
        RadioStations.Tuned tuned = mc.level == null ? null : RadioStations.tune(station, mc.level.getGameTime() / 20.0);
        return tuned != null && tuned.ready() ? tuned : null;
    }

    private boolean retuned() {
        if (RadioStations.generation() == generation) return false;
        generation = RadioStations.generation();
        RadioStations.Tuned now = playable(station);
        return !Objects.equals(now == null ? null : now.station(), tuned == null ? null : tuned.station());
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly) {
        if (tuned == null) return loader.getStream(id, repeatInstantly);
        RadioStations.Tuned start = tuned;
        return CompletableFuture.supplyAsync(() -> new RadioStream(start), Util.backgroundExecutor());
    }

    public Kind kind() {
        return kind;
    }

    public boolean muffled(EntityVehicleF_Physics cabin) {
        return kind != Kind.ENGINE_INSIDE && !(kind == Kind.RADIO && vehicle == cabin);
    }

    public float shut() {
        return shut;
    }

    private float load() {
        if (layer.load <= 0) return 1;
        float speed = Math.abs(vehicle.speed());
        float change = lastSpeed < 0 ? 0 : speed - lastSpeed;
        float pull = change > 0 ? change / FULL_PULL : -change / FULL_REGEN;
        load = Mth.lerp(LOAD_EASE, load, Mth.clamp(pull, 0, 1));
        return load;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (vehicle.isRemoved() || mc.level != BuilderVehicle.of(vehicle).level() || mc.player == null || vehicle.distanceToSqr(Convert.point(mc.player.position())) > 2500
                || vehicle.definition() != definition || kind == Kind.RADIO && (vehicle.radioStation() != station || retuned())) {
            stop();
            return;
        }
        boolean on = kind.flag == 0 || vehicle.flag(kind.flag);
        int spin = (int) Math.round(definition.sounds.spinDown * 20);
        if (on) {
            offTicks = 0;
        } else if (!kind.engine() || ++offTicks > spin) {
            stop();
            return;
        }
        if (kind != Kind.ENGINE_INSIDE) {
            Point3D at = kind.engine() || kind == Kind.HORN ? vehicle.enginePosition() : vehicle.position().add(0, .5, 0);
            x = at.x;
            y = at.y;
            z = at.z;
        }
        float target;
        switch (kind) {
            case SKID -> {
                target = Mth.clamp((vehicle.slip() - .025f) * 2.5f, 0, .7f);
                pitch = .85f + Math.min(.4f, vehicle.slip());
            }
            case ROAD -> {
                target = Math.min(.28f, Math.abs(vehicle.speed()) * .3f);
                pitch = .8f + Math.min(.6f, Math.abs(vehicle.speed()) * .6f);
            }
            case ENGINE, ENGINE_INSIDE -> {
                float fall = on ? 0 : offTicks / (float) Math.max(1, spin);
                if (on) lastRpm = layer.followsSpeed() ? Math.abs(vehicle.speed()) * KMH : vehicle.rpm();
                float rpm = on ? lastRpm : lastRpm * (1 - fall);
                float inside = layer.inside == null ? 0 : CabinAcoustics.inside(vehicle);
                target = (float) (definition.sounds.engineVolume * layer.volume) * layer.blend(rpm) * layer.loadShare(load())
                        * definition.sounds.startFade(vehicle.ticks() - vehicle.startedAt) * (kind == Kind.ENGINE ? 1 - inside : inside) * (1 - fall * fall);
                pitch = layer.pitch(rpm);
            }
            case RADIO -> {
                pitch = 1;
                shut = CabinAcoustics.listensInside(vehicle) ? 0 : 1 - Math.min(1, CabinAcoustics.openness(vehicle));
                target = RADIO * (1 - (1 - RADIO_SHUT) * shut);
            }
            case HORN -> {
                pitch = 1;
                target = HORN;
            }
            default -> {
                pitch = 1;
                target = 2.5f;
            }
        }
        volume = kind == Kind.HORN ? target : Mth.lerp(.25f, volume, target);
        lastSpeed = Math.abs(vehicle.speed());
        if ((kind.flag == 0 || kind == Kind.ENGINE_INSIDE) && target < .001f) {
            if (++silentTicks > 25) stop();
        } else {
            silentTicks = 0;
        }
    }
}
