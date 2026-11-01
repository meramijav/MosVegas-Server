package studio.merami.sliminterfacefabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.sliminterface.InterfaceManager;
import studio.merami.sliminterfacefabric.command.SlimVehicleCommand;
import studio.merami.sliminterfacefabric.compat.TaczCompat;
import studio.merami.sliminterfacefabric.event.SeatedInteractionEvents;
import studio.merami.sliminterfacefabric.network.NetworkHandler;
import studio.merami.sliminterfacefabric.network.RadioServer;
import studio.merami.sliminterfacefabric.packs.PackDiscovery;
import studio.merami.sliminterfacefabric.registry.EntityRegistry;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;
import studio.merami.sliminterfacefabric.registry.ParticleRegistry;
import studio.merami.sliminterfacefabric.registry.RuleRegistry;
import studio.merami.sliminterfacefabric.registry.SoundRegistry;

public class SlimVehicle implements ModInitializer {
    public static ResourceLocation id(String path) {
        return new ResourceLocation(SlimCore.MODID, path);
    }

    @Override
    public void onInitialize() {
        InterfaceManager.coreInterface = new InterfaceCore();
        PackParser.load(PackDiscovery.sources(), PackDiscovery.CONFIGS, PackDiscovery.OVERRIDES);
        SoundRegistry.register();
        ParticleRegistry.register();
        RuleRegistry.register();
        EntityRegistry.register();
        ItemRegistry.register();
        NetworkHandler.registerServer();
        RadioServer.register();
        SeatedInteractionEvents.register();
        TaczCompat.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> SlimVehicleCommand.register(dispatcher));
    }
}
