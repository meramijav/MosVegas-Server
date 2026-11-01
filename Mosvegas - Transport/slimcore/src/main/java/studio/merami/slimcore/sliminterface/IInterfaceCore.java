package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.items.AItemBase;

public interface IInterfaceCore {
    enum Rule {
        VEHICLE_DAMAGE,
        VEHICLE_HURTS_PEOPLE,
        VEHICLE_BREAKS_BLOCKS
    }

    IWrapperNBT createNBT();

    IWrapperInventory createInventory(int size);

    IWrapperItemStack createStack(AItemBase item);
}
