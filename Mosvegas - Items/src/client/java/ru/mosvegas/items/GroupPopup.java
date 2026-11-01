package ru.mosvegas.items;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The red column of variants that opens over a grouped slot of the creative tab. Up to seven
 * variants form one column; it lines its cells up with the slots underneath while it fits the
 * 9 x 5 item grid and otherwise also covers the hotbar row. Coordinates are relative to the
 * inventory screen, like {@link Slot#x}.
 */
public final class GroupPopup {
    // The creative item grid: 9 x 5 slots starting at (9, 18) with an 18 px pitch.
    private static final int GRID_X = 9, GRID_Y = 18, GRID_COLUMNS = 9, GRID_ROWS = 5, PITCH = 18;
    // Lower edge of the inventory background, below the hotbar row.
    private static final int BOTTOM = 136;
    private static final int BORDER = 3, MAX_COLUMN = 7;
    // Markers sit above grid items (z 150) and their bars (z 200); the list itself is drawn last, below its tooltip (z 400).
    private static final int PANEL_Z = 250, ITEM_Z = 112;
    private static final int OUTLINE = 0xFF1C0404, FRAME = 0xFFD8353A, BODY = 0xFF8E1F24;
    private static final int SLOT_DARK = 0xFF3B0709, SLOT_LIGHT = 0xFFE87A7E, SLOT_FILL = 0xFF5E1216;
    private final Item representative;
    private final List<ItemStack> variants;
    private final int left, top, rows;

    public GroupPopup(Item representative, List<? extends Item> items, int slotX, int slotY) {
        this.representative = representative;
        this.variants = items.stream().map(ItemStack::new).toList();
        int columns = (variants.size() + MAX_COLUMN - 1) / MAX_COLUMN;
        this.rows = (variants.size() + columns - 1) / columns;
        int column = Math.min((slotX - GRID_X) / PITCH, GRID_COLUMNS - columns);
        this.left = GRID_X + Math.max(column, 0) * PITCH;
        if (rows <= GRID_ROWS) {
            int row = Math.min((slotY - GRID_Y) / PITCH, GRID_ROWS - rows);
            this.top = GRID_Y + Math.max(row, 0) * PITCH;
        } else {
            this.top = Math.max(BORDER + 1, Math.min(slotY, BOTTOM - BORDER - 1 - rows * PITCH + 2));
        }
    }

    public Item representative() {
        return representative;
    }

    public ItemStack variant(int cell) {
        return variants.get(cell);
    }

    private int cellX(int cell) {
        return left + cell / rows * PITCH;
    }

    private int cellY(int cell) {
        return top + cell % rows * PITCH;
    }

    private int columnsRight() {
        int columns = (variants.size() + rows - 1) / rows;
        return left + columns * PITCH - 2;
    }

    /** Index of the cell under the point, or -1. */
    public int cellAt(double x, double y) {
        for (int cell = 0; cell < variants.size(); cell++) {
            int cx = cellX(cell), cy = cellY(cell);
            if (x >= cx - 1 && x < cx + PITCH - 1 && y >= cy - 1 && y < cy + PITCH - 1) return cell;
        }
        return -1;
    }

    public boolean contains(double x, double y) {
        return x >= left - BORDER - 1 && x < columnsRight() + BORDER + 1
                && y >= top - BORDER - 1 && y < top + rows * PITCH - 2 + BORDER + 1;
    }

    public void render(DrawContext context, TextRenderer textRenderer, double mouseX, double mouseY) {
        int x1 = left - BORDER, y1 = top - BORDER, x2 = columnsRight() + BORDER, y2 = top + rows * PITCH - 2 + BORDER;
        context.fill(x1 - 1, y1, x2 + 1, y2, PANEL_Z, OUTLINE);
        context.fill(x1, y1 - 1, x2, y2 + 1, PANEL_Z, OUTLINE);
        context.fill(x1, y1, x2, y2, PANEL_Z, FRAME);
        context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, PANEL_Z, BODY);
        for (int cell = 0; cell < variants.size(); cell++) {
            int x = cellX(cell), y = cellY(cell);
            context.fill(x - 1, y - 1, x + 17, y + 17, PANEL_Z, SLOT_DARK);
            context.fill(x, y, x + 17, y + 17, PANEL_Z, SLOT_LIGHT);
            context.fill(x, y, x + 16, y + 16, PANEL_Z, SLOT_FILL);
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, ITEM_Z);
        for (int cell = 0; cell < variants.size(); cell++) {
            ItemStack stack = variants.get(cell);
            context.drawItem(stack, cellX(cell), cellY(cell));
            context.drawItemInSlot(textRenderer, stack, cellX(cell), cellY(cell));
        }
        context.getMatrices().pop();
        int hovered = cellAt(mouseX, mouseY);
        if (hovered >= 0) HandledScreen.drawSlotHighlight(context, cellX(hovered), cellY(hovered), PANEL_Z + 100);
    }

    /** A small red corner on slots that open a group. */
    public static void drawMarker(DrawContext context, int slotX, int slotY) {
        for (int i = 0; i < 4; i++) {
            context.fill(slotX + 11 + i, slotY - 1 + i, slotX + 12 + i, slotY + i, PANEL_Z, OUTLINE);
            context.fill(slotX + 12 + i, slotY - 1 + i, slotX + 17, slotY + i, PANEL_Z, FRAME);
        }
    }

    /** Tooltip of a group slot: the group's name and how to open it. */
    public static List<Text> tooltip(Item representative) {
        var variants = ConsumableGroups.variants(representative);
        String group = ConsumableGroups.group(representative);
        return List.of(
                Text.translatableWithFallback("group." + MosVegasItems.MOD_ID + "." + group, group),
                Text.translatable("tooltip.mosvegas_items.group_variants", variants.size()).formatted(Formatting.GRAY),
                Text.translatable("tooltip.mosvegas_items.group_open").formatted(Formatting.DARK_GRAY));
    }

    /**
     * A creative list slot holding one variant. Vanilla's creative click handling only reads the
     * slot, so picking, shift-picking, cloning, dropping and hotbar keys behave as in any other tab.
     */
    public static Slot slot(Inventory creativeList, ItemStack stack) {
        return new Slot(creativeList, 0, 0, 0) {
            @Override
            public ItemStack getStack() {
                return stack;
            }

            @Override
            public boolean hasStack() {
                return true;
            }
        };
    }
}
