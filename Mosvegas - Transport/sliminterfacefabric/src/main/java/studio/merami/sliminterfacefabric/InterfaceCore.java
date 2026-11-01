package studio.merami.sliminterfacefabric;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import studio.merami.slimcore.items.AItemBase;
import studio.merami.slimcore.sliminterface.IInterfaceCore;
import studio.merami.slimcore.sliminterface.IWrapperInventory;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;
import studio.merami.sliminterfacefabric.wrapper.WrapperInventory;
import studio.merami.sliminterfacefabric.wrapper.WrapperItemStack;
import studio.merami.sliminterfacefabric.wrapper.WrapperNBT;

public final class InterfaceCore implements IInterfaceCore {
    @Override
    public IWrapperNBT createNBT() {
        return new WrapperNBT(new CompoundTag());
    }

    @Override
    public IWrapperInventory createInventory(int size) {
        return new WrapperInventory(new SimpleContainer(size));
    }

    @Override
    public IWrapperItemStack createStack(AItemBase item) {
        return new WrapperItemStack(new ItemStack(ItemRegistry.item(item)));
    }
}
