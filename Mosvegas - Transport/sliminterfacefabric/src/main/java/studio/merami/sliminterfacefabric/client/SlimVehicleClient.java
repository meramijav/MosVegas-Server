package studio.merami.sliminterfacefabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.sliminterfacefabric.client.effects.VehicleEffects;
import studio.merami.sliminterfacefabric.client.effects.VehicleParticles;
import studio.merami.sliminterfacefabric.client.hud.InteractionOverlay;
import studio.merami.sliminterfacefabric.client.hud.VehicleHud;
import studio.merami.sliminterfacefabric.client.light.VehicleLights;
import studio.merami.sliminterfacefabric.client.render.VehicleRenderer;
import studio.merami.sliminterfacefabric.client.render.VehicleShadow;
import studio.merami.sliminterfacefabric.client.sound.CabinAcoustics;
import studio.merami.sliminterfacefabric.client.sound.RadioDownloads;
import studio.merami.sliminterfacefabric.client.sound.RadioStations;
import studio.merami.sliminterfacefabric.network.PacketVehicleConfigs;
import studio.merami.sliminterfacefabric.registry.EntityRegistry;

public class SlimVehicleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(EntityRegistry.VEHICLE, VehicleRenderer::new);
        VehicleRenderer.registerReload();
        ClientConfig.load();
        boolean lights = VehicleLights.register();
        VehicleParticles.register();
        VehicleControls.register();
        RadioStations.register();
        RadioDownloads.register();
        ClientTickEvents.END_CLIENT_TICK.register(SlimVehicleClient::tick);
        WorldRenderEvents.START.register(CabinAcoustics::observe);
        WorldRenderEvents.AFTER_ENTITIES.register(VehicleShadow::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VehicleEffects::render);
        WorldRenderEvents.AFTER_ENTITIES.register(DriverPose::renderFirstPerson);
        if (lights) WorldRenderEvents.LAST.register(VehicleLights::render);
        VehicleHud.registerReload();
        HudRenderCallback.EVENT.register(VehicleHud::render);
        ClientPlayNetworking.registerGlobalReceiver(PacketVehicleConfigs.TYPE, (packet, player, sender) -> PackParser.useRemoteConfigs(packet.configs()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> PackParser.useRemoteConfigs(null)));
    }

    private static void tick(Minecraft mc) {
        VehicleControls.tickSettings(mc);
        VehicleLights.tick(mc);
        VehicleShadow.tick(mc);
        CabinAcoustics.tick(mc);
        RadioStations.tick(mc);
        RadioDownloads.tick(mc);
        VehicleEffects.tick(mc);
        TaczClientCompat.tick(mc);
        InteractionOverlay.tick(mc);
        VehicleControls.tick(mc);
    }
}
