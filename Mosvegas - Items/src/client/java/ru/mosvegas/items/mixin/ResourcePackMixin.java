package ru.mosvegas.items.mixin;

import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.VanillaResourcePackProvider;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.items.ConsumableDefinitionLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(VanillaResourcePackProvider.class)
public abstract class ResourcePackMixin {
    @Inject(method = "register", at = @At("TAIL"))
    private void mosvegas$packs(Consumer<ResourcePackProfile> consumer, CallbackInfo ci) {
        if (!((Object) this instanceof DefaultClientResourcePackProvider)) return;
        add(consumer, "generated", ResourcePackProfile.InsertionPosition.BOTTOM);
        add(consumer, "resources", ResourcePackProfile.InsertionPosition.TOP);
    }

    private static void add(Consumer<ResourcePackProfile> consumer, String folder, ResourcePackProfile.InsertionPosition position) {
        Path path = ConsumableDefinitionLoader.ROOT.resolve(folder);
        if (!Files.exists(path.resolve("pack.mcmeta"))) return;
        ResourcePackProfile profile = ResourcePackProfile.create("mosvegas_" + folder, Text.literal("MosVegas " + folder),
                true, new DirectoryResourcePack.DirectoryBackedFactory(path, false),
                ResourceType.CLIENT_RESOURCES, position, ResourcePackSource.BUILTIN);
        if (profile != null) consumer.accept(profile);
    }
}
