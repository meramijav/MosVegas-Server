package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.jsondefs.AJSONMultipart;

public final class PartSeat extends APart {
    public final AJSONMultipart.Seat definition;
    public float windowProgress;
    public float previousWindowProgress;

    public PartSeat(AEntityF_Multipart<?> entity, AJSONMultipart.Seat definition) {
        super(entity, definition.index);
        this.definition = definition;
    }

    public PartOpening door() {
        return definition.doorOpening == null ? null : entity.openings().get(definition.doorOpening.index);
    }

    public boolean standing() {
        return definition.stand;
    }

    public Point3D pos() {
        return definition.pos;
    }

    public boolean windowDown() {
        return entity.windowDown(index);
    }

    @Override
    public String label() {
        return definition.label;
    }

    @Override
    public int highlightColor() {
        return InteractionZone.SEAT_COLOR;
    }
}
