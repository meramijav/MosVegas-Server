package studio.merami.sliminterfacefabric.packs;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.packloading.PackParser;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class PackDiscovery {
    public static final Path FOLDER = FabricLoader.getInstance().getGameDir().resolve("slimvehicle").resolve("packs");
    public static final Path CONFIGS = FabricLoader.getInstance().getConfigDir().resolve("slimvehicle").resolve("vehicles");
    public static final Path OVERRIDES = FabricLoader.getInstance().getConfigDir().resolve("slimvehicle").resolve("overrides");

    public record FolderPack(String name, Path file, Path root, boolean zip) {}

    private static List<FolderPack> folderPacks;

    private PackDiscovery() {}

    public static List<PackParser.Source> sources() {
        List<PackParser.Source> sources = new ArrayList<>();
        ModContainer self = FabricLoader.getInstance().getModContainer(SlimCore.MODID).orElseThrow();
        sources.add(new PackParser.Source(self.getMetadata().getName(), self.getRootPaths(), true));
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getId().equals(SlimCore.MODID)) continue;
            if (mod.findPath(PackParser.PACK_FILE).isPresent()) sources.add(new PackParser.Source(mod.getMetadata().getName(), mod.getRootPaths(), false));
        }
        for (FolderPack pack : folderPacks()) sources.add(new PackParser.Source(pack.name(), List.of(pack.root()), false));
        return sources;
    }

    public static synchronized List<FolderPack> folderPacks() {
        if (folderPacks != null) return folderPacks;
        List<FolderPack> found = new ArrayList<>();
        try {
            Files.createDirectories(FOLDER);
            try (Stream<Path> entries = Files.list(FOLDER)) {
                for (Path entry : entries.sorted().toList()) {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        found.add(new FolderPack(name, entry, entry, false));
                    } else if (name.endsWith(".zip")) {
                        FileSystem zip = FileSystems.newFileSystem(entry);
                        found.add(new FolderPack(name.substring(0, name.length() - 4), entry, zip.getPath("/"), true));
                    }
                }
            }
        } catch (IOException e) {
            SlimCore.LOGGER.error("Could not read vehicle packs in {}: {}", FOLDER, e.getMessage());
        }
        folderPacks = List.copyOf(found);
        return folderPacks;
    }
}
