package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.sliminterface.IWrapperInventory;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class WrapperPlayer extends WrapperEntity implements IWrapperPlayer {
    private static final MenuType<?>[] CHEST_ROWS = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

    public final Player player;

    WrapperPlayer(Player player) {
        super(player);
        this.player = player;
    }

    public static WrapperPlayer of(Player player) {
        return (WrapperPlayer) WrapperEntity.of(player);
    }

    @Override
    public void message(Text text) {
        player.displayClientMessage(Convert.text(text), true);
    }

    @Override
    public boolean hasPermission(int level) {
        return player.hasPermissions(level);
    }

    @Override
    public boolean isCreative() {
        return player.isCreative();
    }

    @Override
    public boolean instabuild() {
        return player.getAbilities().instabuild;
    }

    @Override
    public boolean isSneaking() {
        return player.isSecondaryUseActive();
    }

    @Override
    public IWrapperItemStack mainHand() {
        return new WrapperItemStack(player.getMainHandItem());
    }

    @Override
    public List<IWrapperItemStack> inventoryItems() {
        List<IWrapperItemStack> items = new ArrayList<>(player.getInventory().items.size() + 1);
        for (ItemStack stack : player.getInventory().items) items.add(new WrapperItemStack(stack));
        for (ItemStack stack : player.getInventory().offhand) items.add(new WrapperItemStack(stack));
        return items;
    }

    @Override
    public void give(IWrapperItemStack stack) {
        ItemStack item = ((WrapperItemStack) stack).stack;
        if (!player.getInventory().add(item)) player.drop(item, false);
    }

    @Override
    public void openStorage(IWrapperInventory inventory, int rows, Text title, Predicate<IWrapperPlayer> stillValid) {
        WrapperInventory storage = (WrapperInventory) inventory;
        player.openMenu(new SimpleMenuProvider((id, playerInventory, user) -> new ChestMenu(CHEST_ROWS[rows - 1], id, playerInventory, storage.container, rows) {
            @Override
            public boolean stillValid(Player viewer) {
                return stillValid.test(WrapperPlayer.of(viewer));
            }
        }, Convert.text(title)));
    }

    @Override
    public IWrapperPlayer asPlayer() {
        return this;
    }
}
