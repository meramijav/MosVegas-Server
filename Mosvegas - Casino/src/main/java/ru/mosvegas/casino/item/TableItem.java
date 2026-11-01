package ru.mosvegas.casino.item;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import ru.mosvegas.casino.config.BuiltinStyles;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.TableSize;

import java.util.List;

/** Table item: the style goes to the block entity through BlockEntityTag, the size picks the footprint. */
public final class TableItem extends BlockItem {
    public static final String SIZE_KEY = "casino_size";
    public static final String NAME_KEY = "casino_style_name";

    public TableItem(Block block, Settings settings) { super(block, settings); }

    public static ItemStack forStyle(String style) {
        BuiltinStyles.Entry e = BuiltinStyles.find(style);
        return forStyle(style, e != null ? e.size() : TableSize.LARGE);
    }

    public static ItemStack forStyle(String style, TableSize size) {
        if (style == null) style = BuiltinStyles.DEFAULT;
        ItemStack stack = new ItemStack(CasinoRegistry.BLACKJACK_TABLE_ITEM);
        NbtCompound be = new NbtCompound();
        be.putString("style", style);
        stack.setSubNbt("BlockEntityTag", be);
        stack.getOrCreateNbt().putString(SIZE_KEY, size.id);
        BuiltinStyles.Entry e = BuiltinStyles.find(style);
        stack.getOrCreateNbt().putString(NAME_KEY, e != null ? e.name() : style);
        return stack;
    }

    public static ItemStack forStyle(String style, TableSize size, String displayName) {
        ItemStack stack = forStyle(style, size);
        stack.getOrCreateNbt().putString(NAME_KEY, displayName);
        return stack;
    }

    public static TableSize sizeOf(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(SIZE_KEY)) return TableSize.parse(nbt.getString(SIZE_KEY), TableSize.LARGE);
        BuiltinStyles.Entry e = BuiltinStyles.find(styleOf(stack));
        return e != null ? e.size() : TableSize.LARGE;
    }

    public static String styleOf(ItemStack stack) {
        NbtCompound be = stack.getSubNbt("BlockEntityTag");
        return be != null && be.contains("style") ? be.getString("style") : BuiltinStyles.DEFAULT;
    }

    @Override
    public Text getName(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        String name = nbt != null && nbt.contains(NAME_KEY) ? nbt.getString(NAME_KEY) : styleOf(stack);
        return Text.translatable(getTranslationKey()).append(Text.literal(" — " + name));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        BuiltinStyles.Entry e = BuiltinStyles.find(styleOf(stack));
        TableSize size = sizeOf(stack);
        tooltip.add(Text.translatable("tooltip.mosvegas_casino.size", size == TableSize.LARGE ? "5x3" : "3x2").formatted(net.minecraft.util.Formatting.GRAY));
        if (e != null) {
            tooltip.add(Text.literal(e.variant().title + " • " + e.boxes()).append(Text.translatable("tooltip.mosvegas_casino.boxes")).formatted(net.minecraft.util.Formatting.GOLD));
        }
        tooltip.add(Text.translatable("tooltip.mosvegas_casino.place_hint").formatted(net.minecraft.util.Formatting.DARK_GRAY));
    }
}
