package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.items.AItemBase;

public interface IWrapperItemStack {
    boolean isEmpty();

    AItemBase item();

    boolean hasData();

    IWrapperNBT data();

    IWrapperNBT dataOrCreate();

    void shrink(int amount);

    void setName(Text name);
}
