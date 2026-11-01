package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import studio.merami.slimcore.sliminterface.IWrapperInventory;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperNBT;

import java.util.ArrayList;
import java.util.List;

public final class WrapperInventory implements IWrapperInventory {
    public final SimpleContainer container;

    public WrapperInventory(SimpleContainer container) {
        this.container = container;
    }

    @Override
    public int size() {
        return container.getContainerSize();
    }

    @Override
    public List<IWrapperItemStack> moveTo(IWrapperInventory target) {
        SimpleContainer into = ((WrapperInventory) target).container;
        List<IWrapperItemStack> left = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = into.addItem(stack);
            if (!rest.isEmpty()) left.add(new WrapperItemStack(rest));
        }
        return left;
    }

    @Override
    public void load(IWrapperNBT tag, String key) {
        container.fromTag(((WrapperNBT) tag).tag.getList(key, Tag.TAG_COMPOUND));
    }

    @Override
    public void save(IWrapperNBT tag, String key) {
        ((WrapperNBT) tag).tag.put(key, container.createTag());
    }
}
