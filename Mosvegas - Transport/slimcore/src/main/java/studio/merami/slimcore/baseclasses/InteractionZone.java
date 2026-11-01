package studio.merami.slimcore.baseclasses;

import studio.merami.slimcore.entities.instances.APart;
import studio.merami.slimcore.jsondefs.AJSONItem;

public record InteractionZone(APart part, int seat, Hull.Box box, boolean storage, String label) {
    public record Hit(InteractionZone zone, Point3D point, double distance) {}

    public static final int SEAT_COLOR = 0xffdf58;

    public int color() {
        if (seat >= 0) return SEAT_COLOR;
        return part == null ? 0xef7272 : part.highlightColor();
    }

    public Text text() {
        return AJSONItem.text(label);
    }
}
