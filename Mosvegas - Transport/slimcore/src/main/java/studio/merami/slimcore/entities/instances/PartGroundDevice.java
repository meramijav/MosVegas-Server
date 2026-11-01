package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.jsondefs.AJSONMultipart;

public final class PartGroundDevice extends APart {
    public final AJSONMultipart.Wheel definition;
    public float travel;
    public float oldTravel;

    public PartGroundDevice(AEntityF_Multipart<?> entity, AJSONMultipart.Wheel definition) {
        super(entity, definition.index);
        this.definition = definition;
    }

    public Point3D center() {
        return definition.pos;
    }

    public boolean isPunctured() {
        return entity.punctured(index);
    }

    @Override
    public String label() {
        return definition.label;
    }

    @Override
    public int highlightColor() {
        return 0xef7272;
    }
}
