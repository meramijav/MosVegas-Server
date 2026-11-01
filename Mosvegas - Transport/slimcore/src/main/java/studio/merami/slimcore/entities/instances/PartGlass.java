package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.entities.components.AEntityF_Multipart;
import studio.merami.slimcore.jsondefs.AJSONMultipart;

public final class PartGlass extends APart {
    public final AJSONMultipart.Glass definition;

    public PartGlass(AEntityF_Multipart<?> entity, AJSONMultipart.Glass definition) {
        super(entity, definition.index);
        this.definition = definition;
    }

    public boolean isBroken() {
        return entity.brokenGlass(index);
    }

    public int window() {
        return definition.window;
    }

    @Override
    public String label() {
        return definition.bone;
    }

    @Override
    public int highlightColor() {
        return 0x62dded;
    }
}
