package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.baseclasses.Text;

import java.util.List;
import java.util.function.Predicate;

public interface IWrapperPlayer extends IWrapperEntity {
    void message(Text text);

    boolean hasPermission(int level);

    boolean isCreative();

    boolean instabuild();

    boolean isSneaking();

    IWrapperItemStack mainHand();

    List<IWrapperItemStack> inventoryItems();

    void give(IWrapperItemStack stack);

    void openStorage(IWrapperInventory inventory, int rows, Text title, Predicate<IWrapperPlayer> stillValid);
}
