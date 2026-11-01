package studio.merami.slimcore.jsondefs;

import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.Point3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AJSONMultipart extends AJSONInteractable {
    public static final int MAX_SEATS = 16;
    public static final int MAX_WHEELS = 8;
    public static final int MAX_OPENINGS = 16;
    public static final int MAX_GLASS = 32;

    public boolean boardOnClick;
    public Rider rider = new Rider();
    public List<Seat> seats = new ArrayList<>();
    public Map<String, Opening> openings = new LinkedHashMap<>();
    public List<Glass> glass = new ArrayList<>();
    public List<Wheel> wheels = new ArrayList<>();
    public JSONSounds sounds = new JSONSounds();

    public transient List<Opening> openingList;
    public transient Map<String, Opening> openingsByBone;
    public transient Map<String, Glass> glassByBone;
    public transient double meanWheelZ;
    public transient double frontAxle;
    public transient double rearAxle;
    public transient double axleSpan;
    public transient double track;

    public enum OpeningType {
        DOOR,
        BONNET,
        TRUNK,
        FUEL,
        GLOVEBOX,
        HATCH
    }

    public static class Rider {
        public double scale = ChassisGeometry.RIDER_SCALE;
    }

    public static class Seat {
        public Point3D pos = Point3D.ZERO;
        public boolean stand;
        public String door;
        public String label;
        public Click click = new Click();

        public transient int index;
        public transient Opening doorOpening;
    }

    public static class Click {
        public Point3D center;
        public Point3D half = new Point3D(.32, .19, .37);
    }

    public static class Opening {
        public String type;
        public String bone;
        public Point3D rotate = Point3D.ZERO;
        public double buckle = .06;
        public boolean solid = true;
        public double maxHalfWidth;
        public String label;
        public Boolean shutOnMove;
        public Boolean usableWhileMoving;
        public String soundOpen;
        public String soundClose;
        public Storage storage;

        public transient String id;
        public transient int index;
        public transient OpeningType kind;
    }

    public static class Storage {
        public Point3D center = Point3D.ZERO;
        public Point3D half = Point3D.ZERO;
        public int rows = 3;
        public String title;
        public String label;
        public List<Integer> seats = new ArrayList<>();
    }

    public static class Glass {
        public String bone;
        public int window = -1;

        public transient int index;
    }

    public static class Wheel {
        public String bone;
        public Point3D pos = Point3D.ZERO;
        public double radius = .35;
        public double halfWidth = .145;
        public double rollRadius = Double.NaN;
        public double contact = Double.NaN;
        public boolean steer;
        public boolean solid = true;
        public String label;

        public transient int index;
    }

    @Override
    public void bake() {
        super.bake();
        if (wheels.isEmpty() || wheels.size() > MAX_WHEELS) throw new IllegalArgumentException("1.." + MAX_WHEELS + " wheels");
        if (seats.isEmpty() || seats.size() > MAX_SEATS) throw new IllegalArgumentException("1.." + MAX_SEATS + " seats");
        if (openings.size() > MAX_OPENINGS) throw new IllegalArgumentException("at most " + MAX_OPENINGS + " openings");
        if (glass.size() > MAX_GLASS) throw new IllegalArgumentException("at most " + MAX_GLASS + " panes");
        bakeWheels();
        bakeOpenings();
        bakeGlass();
        bakeSeats();
        sounds.qualify(sound -> qualify(sound).toString());
        for (Opening opening : openingList) {
            if (!JSONSounds.silent(opening.soundOpen)) opening.soundOpen = qualify(opening.soundOpen).toString();
            if (!JSONSounds.silent(opening.soundClose)) opening.soundClose = qualify(opening.soundClose).toString();
        }
        for (Zone zone : damage.zones) {
            if (zone.jam != null) {
                Opening jammed = openings.get(zone.jam);
                if (jammed == null) throw new IllegalArgumentException("damage zone " + zone.id + " jams unknown opening " + zone.jam);
                zone.jamIndex = jammed.index;
            }
            if (zone.glass != null) {
                Glass pane = glassByBone.get(zone.glass);
                if (pane == null) throw new IllegalArgumentException("damage zone glass " + zone.glass + " is not in \"glass\"");
                zone.glassIndex = pane.index;
            }
        }
    }

    private void bakeWheels() {
        for (int i = 0; i < wheels.size(); i++) {
            Wheel wheel = wheels.get(i);
            wheel.index = i;
            if (wheel.bone == null) wheel.bone = "w" + i;
            if (Double.isNaN(wheel.rollRadius)) wheel.rollRadius = wheel.radius;
            if (Double.isNaN(wheel.contact)) wheel.contact = wheel.radius;
            if (wheel.label == null) wheel.label = "part." + namespace + ".tyre_" + wheel.bone;
        }
        double z = 0;
        for (Wheel wheel : wheels) z += wheel.pos.z;
        z /= wheels.size();
        meanWheelZ = z;
        double front = 0;
        double rear = 0;
        double left = 0;
        double right = 0;
        int frontCount = 0;
        int rearCount = 0;
        int leftCount = 0;
        int rightCount = 0;
        for (Wheel wheel : wheels) {
            if (wheel.pos.z > z) {
                front += wheel.pos.z;
                frontCount++;
            } else {
                rear += wheel.pos.z;
                rearCount++;
            }
            if (wheel.pos.x > 1e-6) {
                left += wheel.pos.x;
                leftCount++;
            } else if (wheel.pos.x < -1e-6) {
                right += wheel.pos.x;
                rightCount++;
            }
        }
        frontAxle = frontCount > 0 ? front / frontCount : z;
        rearAxle = rearCount > 0 ? rear / rearCount : z;
        axleSpan = frontAxle - rearAxle;
        track = leftCount > 0 && rightCount > 0 ? left / leftCount - right / rightCount : 0;
    }

    private void bakeOpenings() {
        List<Opening> list = new ArrayList<>();
        Map<String, Opening> byBone = new HashMap<>();
        for (Map.Entry<String, Opening> entry : openings.entrySet()) {
            Opening opening = entry.getValue();
            opening.id = entry.getKey();
            opening.index = list.size();
            opening.kind = opening.type != null ? OpeningType.valueOf(opening.type.toUpperCase(Locale.ROOT)) : inferType(opening.id);
            if (opening.bone == null) opening.bone = opening.id;
            if (opening.label == null) opening.label = "part." + namespace + "." + opening.id;
            if (opening.shutOnMove == null) opening.shutOnMove = opening.kind != OpeningType.GLOVEBOX;
            if (opening.usableWhileMoving == null) opening.usableWhileMoving = opening.kind == OpeningType.GLOVEBOX;
            if (opening.storage != null) {
                if (opening.storage.title == null) opening.storage.title = "container." + namespace + "." + opening.id;
                if (opening.storage.label == null) opening.storage.label = "part." + namespace + "." + opening.id + ".contents";
            }
            list.add(opening);
            byBone.put(opening.bone, opening);
        }
        openingList = Collections.unmodifiableList(list);
        openingsByBone = Collections.unmodifiableMap(byBone);
    }

    private static OpeningType inferType(String id) {
        if (id.startsWith("door")) return OpeningType.DOOR;
        if (id.equals("bonnet") || id.equals("hood")) return OpeningType.BONNET;
        if (id.startsWith("trunk") || id.startsWith("boot")) return OpeningType.TRUNK;
        if (id.startsWith("fuel")) return OpeningType.FUEL;
        if (id.startsWith("glovebox")) return OpeningType.GLOVEBOX;
        return OpeningType.HATCH;
    }

    private void bakeGlass() {
        Map<String, Glass> byBone = new HashMap<>();
        for (int i = 0; i < glass.size(); i++) {
            Glass pane = glass.get(i);
            if (pane.bone == null) throw new IllegalArgumentException("pane " + i + " has no \"bone\"");
            pane.index = i;
            byBone.put(pane.bone, pane);
        }
        glassByBone = Collections.unmodifiableMap(byBone);
    }

    private void bakeSeats() {
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            seat.index = i;
            if (seat.door != null) {
                seat.doorOpening = openings.get(seat.door);
                if (seat.doorOpening == null) throw new IllegalArgumentException("seat " + i + " uses unknown door " + seat.door);
            }
            if (seat.label == null) seat.label = i == 0 ? "seat.slimvehicle.driver" : "seat.slimvehicle.passenger";
            if (seat.click.center == null) seat.click.center = seat.pos;
        }
    }

    public Opening opening(String id) {
        return openings.get(id);
    }

    public String openingSound(Opening opening, boolean opens) {
        if (opens && opening.soundOpen != null) return opening.soundOpen;
        if (!opens && opening.soundClose != null) return opening.soundClose;
        return switch (opening.kind) {
            case BONNET -> opens ? sounds.hoodOpen : sounds.hoodClose;
            case TRUNK -> opens ? sounds.trunkOpen : sounds.trunkClose;
            case GLOVEBOX, FUEL, HATCH -> opens ? sounds.hatchOpen : sounds.hatchClose;
            default -> opens ? sounds.doorOpen : sounds.doorClose;
        };
    }

    public boolean hasWindow(int seat) {
        for (Glass pane : glass) {
            if (pane.window == seat) return true;
        }
        return false;
    }
}
