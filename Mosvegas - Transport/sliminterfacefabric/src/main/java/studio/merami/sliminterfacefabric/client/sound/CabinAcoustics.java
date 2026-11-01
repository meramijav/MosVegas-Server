package studio.merami.sliminterfacefabric.client.sound;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.util.Mth;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.PartGlass;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.PartSeat;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.mixin.client.ChannelAccessor;
import studio.merami.sliminterfacefabric.mixin.client.SoundEngineAccessor;
import studio.merami.sliminterfacefabric.mixin.client.SoundManagerAccessor;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.Map;

public final class CabinAcoustics {
    private static final float FOLLOW = .3f;
    private static final float DOOR = .6f;
    private static final float LID = .2f;
    private static final float WINDOW = .3f;
    private static final float PANE = .25f;
    private static final float GAIN = .3f;
    private static final float HIGHS = .85f;
    private static final float SHUT_HIGHS = .92f;
    private static EntityVehicleF_Physics cabin;
    private static EntityVehicleF_Physics viewed;
    private static boolean cameraInside;
    private static float closure;
    private static boolean filtering;
    private static int filter = -1;
    private static long filterContext;
    private static volatile boolean unsupported;

    private CabinAcoustics() {}

    public static float inside(EntityVehicleF_Physics vehicle) {
        return vehicle == cabin ? closure : 0;
    }

    public static boolean listensInside(EntityVehicleF_Physics vehicle) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && BuilderVehicle.ridden(mc.player) == vehicle;
    }

    public static void observe(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        EntityVehicleF_Physics riding = mc.player != null ? BuilderVehicle.ridden(mc.player) : null;
        viewed = riding;
        cameraInside = riding != null && riding.definition().inCabin(riding.worldToLocal(Convert.point(context.camera().getPosition()), context.tickDelta()));
    }

    public static void tick(Minecraft mc) {
        EntityVehicleF_Physics riding = mc.player != null ? BuilderVehicle.ridden(mc.player) : null;
        float target = 0;
        if (riding != null && riding == viewed && cameraInside) target = 1 - Math.min(1, openness(riding));
        if (riding != null && riding != cabin && target > 0) {
            cabin = riding;
            closure = 0;
        }
        closure = cabin == null ? 0 : Mth.lerp(FOLLOW, closure, cabin == riding ? target : 0);
        if (cabin != null && cabin != riding && closure < .01f) cabin = null;
        apply(mc);
    }

    public static float openness(EntityVehicleF_Physics vehicle) {
        float open = 0;
        for (PartOpening opening : vehicle.openings()) {
            AJSONMultipart.OpeningType kind = opening.kind();
            if (kind == AJSONMultipart.OpeningType.DOOR || kind == AJSONMultipart.OpeningType.TRUNK) open += DOOR * opening.progress;
            else if (kind == AJSONMultipart.OpeningType.BONNET || kind == AJSONMultipart.OpeningType.HATCH) open += LID * opening.progress;
        }
        for (PartSeat seat : vehicle.seats()) open += WINDOW * seat.windowProgress;
        for (PartGlass pane : vehicle.glassPanes()) if (pane.isBroken()) open += PANE;
        return open;
    }

    private static void apply(Minecraft mc) {
        if (unsupported) return;
        EntityVehicleF_Physics vehicle = cabin;
        float muffle = vehicle == null ? 0 : (float) (vehicle.definition().cabinMuffle * closure);
        boolean on = muffle >= .01f;
        SoundEngineAccessor engine = (SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).slimvehicle$engine();
        if (!engine.slimvehicle$loaded()) return;
        Map<SoundInstance, ChannelAccess.ChannelHandle> channels = engine.slimvehicle$channels();
        boolean shutRadio = false;
        for (SoundInstance sound : channels.keySet()) shutRadio |= sound instanceof VehicleAudio audio && audio.shut() >= .01f;
        if (!on && !shutRadio && !filtering) return;
        filtering = on || shutRadio;
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : channels.entrySet()) {
            float cabinMuffle = on && muffles(entry.getKey(), vehicle) ? muffle : 0;
            float shut = entry.getKey() instanceof VehicleAudio audio ? audio.shut() : 0;
            boolean muffled = cabinMuffle >= .01f || shut >= .01f;
            float gain = 1 - GAIN * cabinMuffle;
            float highs = (1 - HIGHS * cabinMuffle) * (1 - SHUT_HIGHS * shut);
            entry.getValue().execute(channel -> attach(((ChannelAccessor) channel).slimvehicle$source(), muffled, gain, highs));
        }
    }

    private static boolean muffles(SoundInstance sound, EntityVehicleF_Physics vehicle) {
        if (sound instanceof VehicleAudio audio) return audio.muffled(vehicle);
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return false;
        JSONVehicle definition = vehicle.definition();
        return !definition.inCabin(vehicle.worldToLocal(new Point3D(sound.getX(), sound.getY(), sound.getZ())));
    }

    private static void attach(int source, boolean muffled, float gain, float highs) {
        if (unsupported) return;
        long context = ALC10.alcGetCurrentContext();
        if (filter < 0 || context != filterContext) {
            long device = context == 0 ? 0 : ALC10.alcGetContextsDevice(context);
            if (device == 0 || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
                unsupported = true;
                return;
            }
            int made = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(made, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            filter = made;
            filterContext = context;
        }
        if (muffled) {
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, highs);
        }
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, muffled ? filter : EXTEfx.AL_FILTER_NULL);
    }
}
