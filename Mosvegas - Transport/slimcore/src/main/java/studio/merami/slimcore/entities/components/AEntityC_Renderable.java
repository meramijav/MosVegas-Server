package studio.merami.slimcore.entities.components;

import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;

public abstract class AEntityC_Renderable extends AEntityB_Existing {
    protected AEntityC_Renderable(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    public boolean animationActive(String condition) {
        return "always".equals(condition);
    }
}
