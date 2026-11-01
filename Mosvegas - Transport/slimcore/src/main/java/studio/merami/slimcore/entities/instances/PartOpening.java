package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.sliminterface.IWrapperInventory;

public final class PartOpening extends APart {
    public final AJSONMultipart.Opening definition;
    public float progress;
    public float previousProgress;
    public IWrapperInventory storage;

    public PartOpening(AEntityF_Multipart<?> entity, AJSONMultipart.Opening definition) {
        super(entity, definition.index);
        this.definition = definition;
    }

    public String id() {
        return definition.id;
    }

    public AJSONMultipart.OpeningType kind() {
        return definition.kind;
    }

    public boolean isDoor() {
        return definition.kind == AJSONMultipart.OpeningType.DOOR;
    }

    public boolean isOpen() {
        return entity.isOpen(this);
    }

    public boolean isJammed() {
        return entity.isJammed(this);
    }

    public boolean hasStorage() {
        return definition.storage != null;
    }

    @Override
    public String label() {
        return definition.label;
    }

    @Override
    public int highlightColor() {
        return switch (definition.kind) {
            case DOOR -> 0x46b4ff;
            case BONNET -> 0xff9245;
            case TRUNK -> 0xce80ff;
            case FUEL -> 0x55e599;
            case GLOVEBOX -> 0xff83bb;
            case HATCH -> 0xef7272;
        };
    }
}
