package ru.mosvegas.core.util;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.*;

public final class Ui {
    private static final String CENTERED_TITLE_MARKER = "mosvegas_core:centered";
    private Ui() {}

    public static ItemStack item(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        return stack;
    }

    public static void menu(ServerPlayerEntity player, String title, Consumer<MenuBuilder> layout) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, Runnable> actions = new HashMap<>();
        MenuBuilder builder = new MenuBuilder(inventory, actions);
        layout.accept(builder);
        ItemStack filler = item(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i=0; i<54; i++) if (inventory.getStack(i).isEmpty()) inventory.setStack(i, filler.copy());
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> new ClickMenu(syncId, playerInventory, inventory, actions),
                centeredTitle(title)));
    }

    public static void input(ServerPlayerEntity player, String title, String initial, Consumer<String> callback) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new TextInput(syncId, inventory, initial, callback),
                centeredTitle(title)));
    }

    private static Text centeredTitle(String value) {
        return Text.literal(value).styled(style -> style.withInsertion(CENTERED_TITLE_MARKER));
    }

    public static final class MenuBuilder {
        private final SimpleInventory inventory;
        private final Map<Integer, Runnable> actions;
        private MenuBuilder(SimpleInventory inventory, Map<Integer, Runnable> actions) { this.inventory=inventory; this.actions=actions; }
        public void button(int slot, ItemStack stack, Runnable action) { inventory.setStack(slot, stack); actions.put(slot, action); }
        public void label(int slot, ItemStack stack) { inventory.setStack(slot, stack); }
    }

    private static final class ClickMenu extends GenericContainerScreenHandler {
        private final Map<Integer, Runnable> actions;
        private boolean actionStarted;
        private ClickMenu(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, Map<Integer, Runnable> actions) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
            this.actions = actions;
        }
        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!actionStarted && actionType == SlotActionType.PICKUP && button == 0 && slotIndex >= 0 && slotIndex < 54) {
                Runnable action = actions.remove(slotIndex);
                if (action != null) {
                    actionStarted = true;
                    action.run();
                }
            }
        }
        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }

    private static final class TextInput extends AnvilScreenHandler {
        private final Consumer<String> callback;
        private String value;
        private boolean completed;
        private TextInput(int syncId, PlayerInventory inventory, String initial, Consumer<String> callback) {
            super(syncId, inventory, ScreenHandlerContext.EMPTY);
            this.callback = callback;
            ItemStack paper = item(Items.PAPER, " ");
            getSlot(0).setStack(paper);
            value = initial == null || initial.isBlank() ? "" : initial;
            setNewItemName(value);
        }
        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public boolean setNewItemName(String newItemName) {
            value = newItemName;
            return super.setNewItemName(newItemName);
        }
        @Override public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (slotIndex == 2 && !completed && value != null && !value.isBlank()) {
                completed = true;
                String result = value.trim();
                if (player instanceof ServerPlayerEntity serverPlayer) serverPlayer.closeHandledScreen();
                callback.accept(result);
                return;
            }
            // GUI paper is a control, not a real item: input slots cannot be picked up or moved.
        }
        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }
}
