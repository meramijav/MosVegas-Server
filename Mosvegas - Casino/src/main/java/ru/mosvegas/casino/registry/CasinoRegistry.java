package ru.mosvegas.casino.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.block.TablePartBlock;
import ru.mosvegas.casino.config.BuiltinStyles;
import ru.mosvegas.casino.dealer.DealerEntity;
import ru.mosvegas.casino.item.TableItem;
import ru.mosvegas.casino.seat.SeatEntity;

public final class CasinoRegistry {
    public static final BlackjackTableBlock BLACKJACK_TABLE = new BlackjackTableBlock(tableSettings());
    public static final TablePartBlock TABLE_PART = new TablePartBlock(tableSettings().dropsNothing());
    public static final TableItem BLACKJACK_TABLE_ITEM = new TableItem(BLACKJACK_TABLE, new Item.Settings().maxCount(1));

    public static final BlockEntityType<BlackjackTableBlockEntity> BLACKJACK_TABLE_ENTITY =
            FabricBlockEntityTypeBuilder.create(BlackjackTableBlockEntity::new, BLACKJACK_TABLE).build();

    public static final EntityType<DealerEntity> DEALER = EntityType.Builder.create(DealerEntity::new, SpawnGroup.MISC)
            .setDimensions(0.6f, 1.8f).maxTrackingRange(10).disableSaving().disableSummon().makeFireImmune().build("dealer");

    public static final EntityType<SeatEntity> SEAT = EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC)
            .setDimensions(0.25f, 0.25f).maxTrackingRange(10).disableSaving().disableSummon().makeFireImmune().build("seat");

    public static final ItemGroup GROUP = FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.mosvegas_casino"))
            .icon(() -> TableItem.forStyle(BuiltinStyles.DEFAULT))
            .entries((context, entries) -> {
                for (BuiltinStyles.Entry e : BuiltinStyles.ALL) entries.add(TableItem.forStyle(e.id()));
            })
            .build();

    private CasinoRegistry() {}

    private static AbstractBlock.Settings tableSettings() {
        return AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(-1.0f, 3_600_000f)
                .nonOpaque().sounds(BlockSoundGroup.WOOD).pistonBehavior(PistonBehavior.BLOCK).dynamicBounds();
    }

    public static void register() {
        Registry.register(Registries.BLOCK, MosVegasCasino.id("blackjack_table"), BLACKJACK_TABLE);
        Registry.register(Registries.BLOCK, MosVegasCasino.id("table_part"), TABLE_PART);
        Registry.register(Registries.ITEM, MosVegasCasino.id("blackjack_table"), BLACKJACK_TABLE_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, MosVegasCasino.id("blackjack_table"), BLACKJACK_TABLE_ENTITY);
        Registry.register(Registries.ENTITY_TYPE, MosVegasCasino.id("dealer"), DEALER);
        Registry.register(Registries.ENTITY_TYPE, MosVegasCasino.id("seat"), SEAT);
        Registry.register(Registries.ITEM_GROUP, MosVegasCasino.id("casino"), GROUP);
        FabricDefaultAttributeRegistry.register(DEALER, DealerEntity.createDealerAttributes());
        CasinoSounds.register();
    }
}
