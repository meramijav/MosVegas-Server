package ru.mosvegas.items.mixin;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.mosvegas.items.ConsumableGroups;
import ru.mosvegas.items.GroupPopup;
import ru.mosvegas.items.MosVegasItems;

import java.util.List;

/** Grouped items in the MosVegas tab: one slot per group that opens a red list of variants. */
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeGroupsMixin extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler> {
    private static final int GRID_SLOTS = 45;
    @Shadow private static ItemGroup selectedTab;
    @Shadow @Final private static SimpleInventory INVENTORY;
    @Unique private GroupPopup mosvegas$popup;
    @Unique private boolean mosvegas$swallowRelease;

    private CreativeGroupsMixin(CreativeInventoryScreen.CreativeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Unique
    private boolean mosvegas$groupsShown() {
        return selectedTab == MosVegasItems.ITEM_GROUP;
    }

    /** Mouse position in screen-local coordinates; render arguments may be hidden while over the list. */
    @Unique
    private double mosvegas$mouseX() {
        return client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth() - x;
    }

    @Unique
    private double mosvegas$mouseY() {
        return client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight() - y;
    }

    @Unique
    private Slot mosvegas$gridSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < GRID_SLOTS; i++) {
            Slot slot = handler.slots.get(i);
            if (isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) return slot;
        }
        return null;
    }

    // While the list is open, slots must not highlight or show tooltips that it would cover.
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int mosvegas$hideMouseX(int mouseX) {
        return mosvegas$popup != null ? -10000 : mouseX;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int mosvegas$hideMouseY(int mouseY) {
        return mosvegas$popup != null ? -10000 : mouseY;
    }

    // Runs inside the slot pass, after the grid items and before the dragged stack and tooltips.
    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void mosvegas$drawMarkers(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!mosvegas$groupsShown()) return;
        for (int i = 0; i < GRID_SLOTS; i++) {
            Slot slot = handler.slots.get(i);
            if (!ConsumableGroups.variants(slot.getStack().getItem()).isEmpty()) GroupPopup.drawMarker(context, slot.x, slot.y);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mosvegas$drawPopup(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (mosvegas$popup == null) return;
        // Drawn last over a cleared depth buffer: grid items rendered with depth must not show through the panel.
        context.draw();
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        mosvegas$popup.render(context, textRenderer, mosvegas$mouseX(), mosvegas$mouseY());
        context.getMatrices().pop();
        if (!handler.getCursorStack().isEmpty()) return;
        int cell = mosvegas$popup.cellAt(mosvegas$mouseX(), mosvegas$mouseY());
        if (cell < 0) return;
        ItemStack stack = mosvegas$popup.variant(cell);
        context.drawTooltip(textRenderer, Screen.getTooltipFromItem(client, stack), stack.getTooltipData(),
                (int) mosvegas$mouseX() + x, (int) mosvegas$mouseY() + y);
    }

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"), cancellable = true)
    private void mosvegas$groupTooltip(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        if (mosvegas$groupsShown() && focusedSlot != null && focusedSlot.inventory == INVENTORY
                && !ConsumableGroups.variants(stack.getItem()).isEmpty()) {
            cir.setReturnValue(GroupPopup.tooltip(stack.getItem()));
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mosvegas$clickGroup(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        double localX = mouseX - x, localY = mouseY - y;
        if (mosvegas$popup != null) {
            GroupPopup popup = mosvegas$popup;
            int cell = popup.cellAt(localX, localY);
            if (cell >= 0) {
                mosvegas$pick(popup.variant(cell), button);
                mosvegas$consume(cir);
                return;
            }
            mosvegas$popup = null;
            Slot slot = mosvegas$gridSlotAt(mouseX, mouseY);
            // A click on the panel border or on the open group's own slot only closes the list.
            if (popup.contains(localX, localY) || slot != null && slot.getStack().isOf(popup.representative())) {
                mosvegas$consume(cir);
                return;
            }
        }
        if (!mosvegas$groupsShown() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || client.options.pickItemKey.matchesMouse(button) || !handler.getCursorStack().isEmpty()) return;
        Slot slot = mosvegas$gridSlotAt(mouseX, mouseY);
        if (slot == null) return;
        var variants = ConsumableGroups.variants(slot.getStack().getItem());
        if (variants.isEmpty()) return;
        mosvegas$popup = new GroupPopup(slot.getStack().getItem(), variants, slot.x, slot.y);
        mosvegas$consume(cir);
    }

    @Unique
    private void mosvegas$consume(CallbackInfoReturnable<Boolean> cir) {
        // Vanilla would otherwise act on the slot under the list when the button is released.
        mosvegas$swallowRelease = true;
        cir.setReturnValue(true);
    }

    @Unique
    private void mosvegas$pick(ItemStack stack, int button) {
        SlotActionType type;
        if (client.options.pickItemKey.matchesMouse(button)) type = SlotActionType.CLONE;
        else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            type = hasShiftDown() ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP;
        else return;
        onMouseClick(GroupPopup.slot(INVENTORY, stack), -1, button, type);
        if (!handler.getCursorStack().isEmpty()) mosvegas$popup = null;
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void mosvegas$swallowRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!mosvegas$swallowRelease) return;
        mosvegas$swallowRelease = false;
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mosvegas$groupKeys(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (mosvegas$popup == null) return;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            mosvegas$popup = null;
            cir.setReturnValue(true);
            return;
        }
        int cell = mosvegas$popup.cellAt(mosvegas$mouseX(), mosvegas$mouseY());
        if (cell < 0) return;
        Slot slot = GroupPopup.slot(INVENTORY, mosvegas$popup.variant(cell));
        if (handler.getCursorStack().isEmpty()) {
            if (client.options.swapHandsKey.matchesKey(keyCode, scanCode)) {
                onMouseClick(slot, -1, 40, SlotActionType.SWAP);
                cir.setReturnValue(true);
                return;
            }
            for (int i = 0; i < 9; i++) {
                if (client.options.hotbarKeys[i].matchesKey(keyCode, scanCode)) {
                    onMouseClick(slot, -1, i, SlotActionType.SWAP);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        if (client.options.pickItemKey.matchesKey(keyCode, scanCode)) {
            onMouseClick(slot, -1, 0, SlotActionType.CLONE);
            mosvegas$popup = null;
            cir.setReturnValue(true);
        } else if (client.options.dropKey.matchesKey(keyCode, scanCode)) {
            onMouseClick(slot, -1, hasControlDown() ? 1 : 0, SlotActionType.THROW);
            cir.setReturnValue(true);
        }
    }

    // Scrolling, switching tabs and resizing move the slots, so the list closes.
    @Inject(method = "mouseScrolled", at = @At("HEAD"))
    private void mosvegas$closeOnScroll(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        mosvegas$popup = null;
    }

    @Inject(method = "setSelectedTab", at = @At("HEAD"))
    private void mosvegas$closeOnTab(ItemGroup group, CallbackInfo ci) {
        mosvegas$popup = null;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void mosvegas$closeOnInit(CallbackInfo ci) {
        mosvegas$popup = null;
    }
}
