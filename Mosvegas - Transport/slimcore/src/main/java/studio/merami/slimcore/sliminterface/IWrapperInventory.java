package studio.merami.slimcore.sliminterface;

import java.util.List;

public interface IWrapperInventory {
    int size();

    List<IWrapperItemStack> moveTo(IWrapperInventory target);

    void load(IWrapperNBT tag, String key);

    void save(IWrapperNBT tag, String key);
}
