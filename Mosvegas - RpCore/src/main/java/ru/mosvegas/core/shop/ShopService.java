package ru.mosvegas.core.shop;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.mosvegas.core.data.DataStore;
import ru.mosvegas.core.service.EconomyService;
import ru.mosvegas.core.util.*;
import java.util.*;

public final class ShopService {
    private ShopService() {}
    public static boolean admin(ServerPlayerEntity player) {
        return Permissions.hasAny(player, 3, "mosvegas.admin.shops", "mosvegas.admin");
    }
    public static void list(ServerPlayerEntity player, int page) {
        List<ShopStore.Shop> shops = ShopStore.get().shops.stream().filter(s -> s.enabled).toList();
        int current = Math.max(0, Math.min(page, Math.max(0, (shops.size() - 1) / 45)));
        Ui.menu(player, "Магазины • " + (current + 1), menu -> {
            for (int i = current * 45; i < Math.min(shops.size(), current * 45 + 45); i++) {
                ShopStore.Shop shop = shops.get(i);
                menu.button(i % 45, Ui.item(Items.CHEST, shop.title), () -> open(player, shop.id, 0));
            }
            if (current > 0) menu.button(45, Ui.item(Items.ARROW, "Предыдущая"), () -> list(player, current - 1));
            if ((current + 1) * 45 < shops.size()) menu.button(53, Ui.item(Items.ARROW, "Следующая"), () -> list(player, current + 1));
        });
    }

    public static void open(ServerPlayerEntity player, String id, int page) {
        try {
            ShopStore.Shop shop = ShopStore.find(ShopStore.get(), id);
            if (!shop.enabled) throw new IllegalArgumentException("Магазин временно закрыт.");
            int current = Math.max(0, Math.min(page, Math.max(0, (shop.offers.size() - 1) / 45)));
            long revision = ShopStore.revision();
            Ui.menu(player, shop.title + " • " + (current + 1), menu -> {
                for (int i = current * 45; i < Math.min(shop.offers.size(), current * 45 + 45); i++) {
                    ShopStore.Offer offer = shop.offers.get(i);
                    menu.button(i % 45, preview(id, offer), () -> confirm(player, id, offer.id, current, revision));
                }
                menu.label(49, Ui.item(Items.GOLD_INGOT, "Банк: " + EconomyService.money(DataStore.player(player.getUuid()).bankBalance)));
                if (current > 0) menu.button(45, Ui.item(Items.ARROW, "Предыдущая"), () -> open(player, id, current - 1));
                if ((current + 1) * 45 < shop.offers.size()) menu.button(53, Ui.item(Items.ARROW, "Следующая"), () -> open(player, id, current + 1));
            });
        } catch (IllegalArgumentException e) { fail(player, e); }
    }

    private static ItemStack preview(String id, ShopStore.Offer offer) {
        ItemStack preview = ShopStore.stack(id, offer.id);
        NbtCompound display = preview.getOrCreateSubNbt("display");
        NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE).copy();
        lore.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("Цена за " + preview.getCount() + " шт.: " + EconomyService.money(offer.price)))));
        lore.add(NbtString.of(Text.Serialization.toJsonString(Text.literal("Оплата с банковского счёта"))));
        display.put("Lore", lore);
        return preview;
    }

    private static void confirm(ServerPlayerEntity player, String shopId, String offerId, int page, long revision) {
        if (revision != ShopStore.revision()) { open(player, shopId, page); return; }
        ShopStore.Offer offer = ShopStore.offer(ShopStore.find(ShopStore.get(), shopId), offerId);
        Ui.menu(player, "Подтвердить покупку", menu -> {
            menu.label(13, preview(shopId, offer));
            menu.button(30, Ui.item(Items.EMERALD, "Купить за " + EconomyService.money(offer.price)), () -> {
                buy(player, shopId, offerId, revision);
                open(player, shopId, page);
            });
            menu.button(32, Ui.item(Items.BARRIER, "Отмена"), () -> open(player, shopId, page));
        });
    }

    public static boolean buy(ServerPlayerEntity player, String shopId, String offerId, long revision) {
        if (revision != ShopStore.revision()) { player.sendMessage(Text.literal("§eКаталог изменён. Выберите товар заново.")); return false; }
        ShopStore.Shop shop = ShopStore.find(ShopStore.get(), shopId);
        if (!shop.enabled || player.isSpectator()) return false;
        ShopStore.Offer offer = ShopStore.offer(shop, offerId);
        ItemStack purchase = ShopStore.stack(shopId, offerId);
        if (!hasSpace(player.getInventory(), purchase)) { player.sendMessage(Text.literal("§cОсвободите место в инвентаре. Деньги не списаны.")); return false; }
        if (offer.price > 0 && !EconomyService.debit(player, offer.price)) { player.sendMessage(Text.literal("§cНедостаточно денег на банковском счёте.")); return false; }
        // All checks and insertion run in the same server tick. Never drop a paid item onto the ground.
        insert(player.getInventory(), purchase);
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
        player.sendMessage(Text.literal("§aКуплено: " + purchase.getName().getString() + " ×" + purchase.getCount() + " за " + EconomyService.money(offer.price)));
        return true;
    }

    private static boolean hasSpace(PlayerInventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = 0; i < 36; i++) {
            ItemStack slot = inventory.getStack(i);
            if (slot.isEmpty()) remaining -= stack.getMaxCount();
            else if (ItemStack.canCombine(slot, stack)) remaining -= Math.max(0, slot.getMaxCount() - slot.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static void insert(PlayerInventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = inventory.getStack(i);
            if (pass == 0 && !slot.isEmpty() && ItemStack.canCombine(slot, stack)) {
                int count = Math.min(remaining, Math.max(0, slot.getMaxCount() - slot.getCount()));
                slot.increment(count);
                remaining -= count;
            } else if (pass == 1 && slot.isEmpty()) {
                int count = Math.min(remaining, stack.getMaxCount());
                inventory.setStack(i, stack.copyWithCount(count));
                remaining -= count;
            }
        }
    }

    public static void add(String shopId, String offerId, long price, ItemStack stack) throws Exception {
        if (stack.isEmpty()) throw new IllegalArgumentException("Возьмите товар в основную руку.");
        ShopStore.update(state -> {
            ShopStore.Shop shop = ShopStore.find(state, shopId);
            if (shop.offers.stream().anyMatch(o -> o.id.equals(offerId))) throw new IllegalArgumentException("Этот ID товара уже занят.");
            ShopStore.Offer offer = new ShopStore.Offer();
            offer.id = offerId;
            offer.price = price;
            offer.stack = stack.writeNbt(new NbtCompound()).toString();
            shop.offers.add(offer);
        });
    }

    public static void edit(ServerPlayerEntity player, String id, int page) {
        if (!admin(player)) return;
        ShopStore.Shop shop = ShopStore.find(ShopStore.get(), id);
        int current = Math.max(0, Math.min(page, Math.max(0, (shop.offers.size() - 1) / 45)));
        SimpleInventory icons = new SimpleInventory(54);
        Map<Integer, String> offers = new HashMap<>();
        for (int i = current * 45; i < Math.min(shop.offers.size(), current * 45 + 45); i++) {
            ShopStore.Offer offer = shop.offers.get(i);
            icons.setStack(i % 45, preview(id, offer));
            offers.put(i % 45, offer.id);
        }
        icons.setStack(45, Ui.item(Items.ARROW, "Предыдущая"));
        icons.setStack(49, Ui.item(Items.PAPER, "Нажмите предмет в своём инвентаре, чтобы добавить его"));
        icons.setStack(53, Ui.item(Items.ARROW, "Следующая"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync, inventory, ignored) ->
                new Editor(sync, inventory, icons, player, id, current, offers, ShopStore.revision()), Text.literal("Редактор: " + shop.title)));
    }

    private static final class Editor extends GenericContainerScreenHandler {
        private final ServerPlayerEntity owner;
        private final String shopId;
        private final int page;
        private final Map<Integer, String> offers;
        private final long revision;
        private boolean acted;
        Editor(int sync, PlayerInventory inventory, SimpleInventory icons, ServerPlayerEntity owner,
               String shopId, int page, Map<Integer, String> offers, long revision) {
            super(ScreenHandlerType.GENERIC_9X6, sync, inventory, icons, 6);
            this.owner = owner; this.shopId = shopId; this.page = page; this.offers = offers; this.revision = revision;
        }
        @Override public boolean canUse(PlayerEntity player) { return player == owner && admin(owner); }
        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
        @Override public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity player) {
            if (acted || !canUse(player) || type != SlotActionType.PICKUP || button != 0) return;
            acted = true;
            try {
                if (ShopStore.revision() != revision) { edit(owner, shopId, page); return; }
                if (slot == 45 || slot == 53) { edit(owner, shopId, page + (slot == 45 ? -1 : 1)); return; }
                if (slot >= 54 && slot < slots.size() && !getSlot(slot).getStack().isEmpty()) {
                    ItemStack template = getSlot(slot).getStack().copy();
                    Ui.input(owner, "Цена за всю пачку", "100", value -> {
                        if (!admin(owner)) return;
                        try { add(shopId, "item_" + UUID.randomUUID().toString().substring(0, 8), Long.parseLong(value), template); }
                        catch (Exception e) { fail(owner, e); }
                        edit(owner, shopId, page);
                    });
                } else if (offers.containsKey(slot)) {
                    String offerId = offers.get(slot);
                    Ui.menu(owner, "Товар: " + offerId, menu -> {
                        menu.button(30, Ui.item(Items.GOLD_INGOT, "Изменить цену"), () -> Ui.input(owner, "Новая цена", "100", value -> {
                            if (!admin(owner)) return;
                            try { ShopStore.update(state -> ShopStore.offer(ShopStore.find(state, shopId), offerId).price = Long.parseLong(value)); }
                            catch (Exception e) { fail(owner, e); }
                            edit(owner, shopId, page);
                        }));
                        menu.button(32, Ui.item(Items.BARRIER, "Удалить товар"), () -> {
                            if (!admin(owner)) return;
                            try { ShopStore.update(state -> ShopStore.find(state, shopId).offers.removeIf(o -> o.id.equals(offerId))); }
                            catch (Exception e) { fail(owner, e); }
                            edit(owner, shopId, page);
                        });
                        menu.button(49, Ui.item(Items.ARROW, "Назад"), () -> edit(owner, shopId, page));
                    });
                } else acted = false;
            } catch (Exception e) { fail(owner, e); owner.closeHandledScreen(); }
        }
    }
    private static void fail(ServerPlayerEntity player, Exception e) { player.sendMessage(Text.literal("§c" + e.getMessage())); }
}
