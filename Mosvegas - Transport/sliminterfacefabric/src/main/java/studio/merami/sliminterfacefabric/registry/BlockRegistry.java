package studio.merami.sliminterfacefabric.registry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.shop.PlateTerminalBlock;
import studio.merami.sliminterfacefabric.shop.PlateTerminalBlockEntity;

public final class BlockRegistry {
    public static final Block PLATE_TERMINAL = new PlateTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(-1, 3600000).noOcclusion().noLootTable());
    public static final Item PLATE_TERMINAL_ITEM = new BlockItem(PLATE_TERMINAL, new Item.Properties().stacksTo(1));
    public static BlockEntityType<PlateTerminalBlockEntity> PLATE_TERMINAL_ENTITY;

    private BlockRegistry() {}

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, SlimVehicle.id("plate_terminal"), PLATE_TERMINAL);
        Registry.register(BuiltInRegistries.ITEM, SlimVehicle.id("plate_terminal"), PLATE_TERMINAL_ITEM);
        PLATE_TERMINAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SlimVehicle.id("plate_terminal"),
                FabricBlockEntityTypeBuilder.create(PlateTerminalBlockEntity::new, PLATE_TERMINAL).build());
    }
}
