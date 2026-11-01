package studio.merami.slimcore.jsondefs;

import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Point3D;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AJSONInteractable extends AJSONItem {
    public static final double REFERENCE_MASS = 1400;
    public static final int MAX_ZONES = 16;

    public Size size = new Size();
    public double mass = REFERENCE_MASS;
    public Hull hull = new Hull();
    public Damage damage = new Damage();

    public transient double massRatio;
    public transient double bodyHalfWidth;
    public transient double bodyHalfLength;

    public static class Size {
        public double halfWidth = 1;
        public double halfLength = 2;
        public double height = 1.6;
    }

    public static class Box {
        public Point3D center = Point3D.ZERO;
        public Point3D half = Point3D.ZERO;
    }

    public static class Hull {
        public List<Box> boxes = new ArrayList<>();
        public Map<String, List<Integer>> bones = new LinkedHashMap<>();
    }

    public static class Damage {
        public double halfWidth = Double.NaN;
        public double halfLength = Double.NaN;
        public double roof = 99;
        public Point3D fuelTank;
        public List<Zone> zones = new ArrayList<>();

        public DamageModel.Side side(Point3D local) {
            if (local.y >= roof && Math.abs(local.x) <= halfWidth && Math.abs(local.z) <= halfLength) return DamageModel.Side.TOP;
            if (Math.abs(local.z) / halfLength >= Math.abs(local.x) / halfWidth) return local.z >= 0 ? DamageModel.Side.FRONT : DamageModel.Side.REAR;
            return local.x >= 0 ? DamageModel.Side.LEFT : DamageModel.Side.RIGHT;
        }

        public Zone zone(Point3D local) {
            DamageModel.Side side = side(local);
            double along = side == DamageModel.Side.LEFT || side == DamageModel.Side.RIGHT ? local.z : local.x;
            Zone best = null;
            double gap = Double.MAX_VALUE;
            for (Zone zone : zones) {
                if (zone.sideValue != side) continue;
                double distance = along < zone.from ? zone.from - along : along > zone.to ? along - zone.to : 0;
                if (distance < gap) {
                    gap = distance;
                    best = zone;
                }
            }
            return best;
        }
    }

    public static class Zone {
        public String id;
        public String label;
        public String side;
        public double from = -99;
        public double to = 99;
        public float engine;
        public String jam;
        public float jamAt = 12;
        public String glass;
        public float glassAt = 12;
        public float pull;
        public boolean leak;

        public transient int index;
        public transient DamageModel.Side sideValue;
        public transient int jamIndex = -1;
        public transient int glassIndex = -1;
    }

    @Override
    public void bake() {
        super.bake();
        massRatio = mass / REFERENCE_MASS;
        double width = 0;
        double length = 0;
        for (Box box : hull.boxes) {
            width = Math.max(width, Math.abs(box.center.x) + box.half.x);
            length = Math.max(length, Math.abs(box.center.z) + box.half.z);
        }
        bodyHalfWidth = width > 0 ? width : size.halfWidth;
        bodyHalfLength = width > 0 ? length : size.halfLength;
        if (Double.isNaN(damage.halfWidth)) damage.halfWidth = size.halfWidth;
        if (Double.isNaN(damage.halfLength)) damage.halfLength = size.halfLength;
        if (damage.zones.size() > MAX_ZONES) throw new IllegalArgumentException("at most " + MAX_ZONES + " damage zones");
        for (int i = 0; i < damage.zones.size(); i++) {
            Zone zone = damage.zones.get(i);
            if (zone.id == null || zone.side == null) throw new IllegalArgumentException("damage zone " + i + " needs \"id\" and \"side\"");
            zone.index = i;
            zone.sideValue = DamageModel.Side.valueOf(zone.side.toUpperCase(Locale.ROOT));
            if (zone.label == null) zone.label = zone.id;
        }
    }
}
