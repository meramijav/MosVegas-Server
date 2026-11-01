package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.jsondefs.AJSONItem;

public abstract class APart {
    public final AEntityF_Multipart<?> entity;
    public final int index;

    protected APart(AEntityF_Multipart<?> entity, int index) {
        this.entity = entity;
        this.index = index;
    }

    public abstract String label();

    public abstract int highlightColor();

    public Text text() {
        return AJSONItem.text(label());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + index + "(" + label() + ")";
    }
}
