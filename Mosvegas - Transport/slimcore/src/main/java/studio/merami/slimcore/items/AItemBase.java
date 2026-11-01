package studio.merami.slimcore.items;

import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;

public class AItemBase {
    public final ResourceId id;
    public final int maxStack;

    public AItemBase(ResourceId id, int maxStack) {
        this.id = id;
        this.maxStack = maxStack;
    }

    public Text displayName() {
        return null;
    }

    public boolean usedOnBlock() {
        return false;
    }

    public boolean useOnBlock(IWrapperPlayer player, IWrapperItemStack stack, Point3D at) {
        return false;
    }

    public boolean usedInAir() {
        return false;
    }

    public boolean useInAir(IWrapperPlayer player, IWrapperItemStack stack) {
        return false;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
