package studio.merami.sliminterfacefabric.client.packs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;
import studio.merami.sliminterfacefabric.packs.PackDiscovery;

import java.util.List;
import java.util.function.Consumer;

public final class FolderPackSource implements RepositorySource {
    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        for (PackDiscovery.FolderPack folder : PackDiscovery.folderPacks()) {
            Pack.ResourcesSupplier resources = folder.zip()
                    ? new FilePackResources.FileResourcesSupplier(folder.file(), false)
                    : new PathPackResources.PathResourcesSupplier(folder.root(), false);
            Component title = Component.literal("Slim Vehicle: " + folder.name());
            Pack.Info info = new Pack.Info(title, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
            consumer.accept(Pack.create("slimvehicle/" + folder.name(), title, true, resources, info, Pack.Position.TOP, false, PackSource.BUILT_IN));
        }
    }
}
