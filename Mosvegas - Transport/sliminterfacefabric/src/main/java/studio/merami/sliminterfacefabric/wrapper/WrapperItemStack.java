package studio.merami.sliminterfacefabric.wrapper;

import net.minecraft.world.item.ItemStack;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.items.AItemBase;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.sliminterfacefabric.item.BuilderItem;

public final class WrapperItemStack implements IWrapperItemStack {
    public final ItemStack stack;

    public WrapperItemStack(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public AItemBase item() {
        return stack.getItem() instanceof BuilderItem builder ? builder.item : null;
    }

    @Override
    public boolean hasData() {
        return stack.hasTag();
    }

    @Override
    public IWrapperNBT data() {
        return stack.getTag() == null ? null : new WrapperNBT(stack.getTag());
    }

    @Override
    public IWrapperNBT dataOrCreate() {
        return new WrapperNBT(stack.getOrCreateTag());
    }

    @Override
    public void shrink(int amount) {
        stack.shrink(amount);
    }

    @Override
    public void setName(Text name) {
        stack.setHoverName(Convert.text(name));
    }
}
