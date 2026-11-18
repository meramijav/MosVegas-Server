package studio.merami.slimcore.jsondefs;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.paint.PaintJob;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.rendering.ModelGeometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class JSONVehicle extends AJSONMultipart {
    public static final int MAX_GEARS = 9;
    public static final double DEFAULT_MUFFLE = .85;
    public static final String TAIL_GLOW = "tail";
    public static final String BRAKE_GLOW = "brake";
    public static final double DEFAULT_TAIL_LEVEL = .45;
    public static final String LENS_RED = "red";
    public static final String LENS_WHITE = "white";
    public static final String DRIVE_REAR = "rear";
    public static final String DRIVE_FRONT = "front";
    public static final String DRIVE_ALL = "all";
    public static final String HEADLIGHT_HALOGEN = "halogen";
    public static final String HEADLIGHT_LED = "led";
    public static final List<String> PLATE_STYLES = List.of("standard", "police");
    private static final String ALL_FACES = "all";
    private static final double CABIN_SIDE = .55;
    private static final double CABIN_LENGTH = .6;
    private static final double CABIN_HEAD = 1.3;

    public boolean emergency;
    public boolean tyreMarks = true;
    public double maxStep = .625;
    public double bodyPivot = .6;
    public double windowDrop = .575;
    public Physics physics = new Physics();
    public Transmission transmission = new Transmission();
    public Fuel fuel = new Fuel();
    public Engine engine = new Engine();
    public List<Point3D> underbody = new ArrayList<>();
    public Lamps lamps;
    public Box lightOccluder;
    public Point3D exhaust;
    public List<Point3D> exhaustPipes = new ArrayList<>();
    public Point3D exhaustDirection = new Point3D(0, 0, -1);
    public boolean exhaustMirror;
    public ExhaustEffects exhaustEffects = new ExhaustEffects();
    public Cabin cabin = new Cabin();
    public List<Emitter> fire = new ArrayList<>();
    public Driver driver;
    public Wipers wipers;
    public Render render = new Render();
    public Plates plates = new Plates();
    public JSONCraft craft;

    public transient DrivePhysics.Tune tune;
    public transient Set<String> paintBones;
    public transient Set<String> interiorPaintBones;
    public transient Set<String> paintKeepBones;
    public transient Map<String, int[]> paintKeepFaces;
    public transient Map<String, int[]> paintFaces;
    public transient Map<String, PaintJob.Area> paintAreaBones;
    public transient Map<String, ModelGeometry.Face> plateFaces;
    public transient int interiorColor;
    public transient int bodyColor;
    public transient Set<String> interiorBones;
    public transient Set<String> dentBones;
    public transient double shadowHalfWidth;
    public transient double shadowHalfLength;
    public transient List<Tailpipe> tailpipes;
    public transient Point3D cabinCenter;
    public transient Point3D cabinHalf;
    public transient double cabinMuffle;

    public record Tailpipe(Point3D pos, Point3D direction) {}

    public static class Plates {
        public String style = PLATE_STYLES.get(0);
        public Map<String, String> bones = new LinkedHashMap<>();
    }

    public static class ExhaustEffects {
        public double smoke = 1;
        public double startSmoke = 1;
        public double startSeconds = 2.5;
        public boolean startFlame = true;
        public boolean overrun = true;
        public boolean shift;
        public boolean flames = true;
    }

    public static class Cabin {
        public Point3D center;
        public Point3D half;
        public double muffle = Double.NaN;
    }

    public static class Physics {
        public double maxSpeedKmh = 100;
        public double acceleration = Double.NaN;
        public double brake = .02;
        public double grip = .28;
        public double steerLock = .6;
        public double steerFalloff = 1.5;
        public double wheelbase = Double.NaN;
        public String drive = DRIVE_REAR;
    }

    public static class Transmission {
        public int gears = 5;
        public List<Double> speedsKmh;
        public double reverseKmh = Double.NaN;
        public List<Double> ratios;
        public double reverseRatio = 3.4;
        public boolean automatic;
        public double shiftUp = .92;
        public double shiftDown = .55;
    }

    public static class Fuel {
        public double tank = 50;
        public double perTick = .0007;
    }

    public static class Engine {
        public Point3D pos;
        public Point3D spread = Point3D.ZERO;
        public String bone;
        public double powerKw = Double.NaN;
        public double idleRpm = 850;
        public double limitRpm = 5600;
        public double maxRpm = 6800;
        public double revRpm = 600;
        public double redlineRpm = 6000;
        public double keepRunning = 120;
    }

    public static class Lamps {
        public Point3D headlight;
        public Point3D taillight;
        public Point3D frontIndicator;
        public Point3D rearIndicator;
        public Point3D reverse;
        public LampBone headlightBone;
        public LampBone taillightBone;
        public List<Glow> glow = new ArrayList<>();
        public List<Sequential> sequential = new ArrayList<>();
        public String headlightType = HEADLIGHT_HALOGEN;

        public String headlightBoneName() {
            return headlightBone == null ? null : headlightBone.bone;
        }

        public String taillightBoneName() {
            return taillightBone == null ? null : taillightBone.bone;
        }
    }

    public static class Glow {
        public String bone;
        public String when = TAIL_GLOW;
        public double tail = DEFAULT_TAIL_LEVEL;

        public boolean brakeOnly() {
            return BRAKE_GLOW.equals(when);
        }
    }

    public static class Sequential {
        public List<String> bones = new ArrayList<>();
        public String lens = LENS_RED;
        public Point3D min;
        public Point3D max;
        public int segments = 7;

        public boolean white() {
            return LENS_WHITE.equals(lens);
        }

        public boolean rear() {
            return min.z + max.z < 0;
        }

        public int segment(double x, double y, double z) {
            double side = Math.abs(x);
            if (side < min.x || side > max.x || y < min.y || y > max.y || z < min.z || z > max.z) return -1;
            return Math.min(segments - 1, (int) ((side - min.x) / (max.x - min.x) * segments));
        }
    }

    public static class LampBone {
        public String bone;
        public List<String> cubes = new ArrayList<>();

        public transient boolean[] left;
    }

    public static class Emitter {
        public Point3D pos = Point3D.ZERO;
        public Point3D spread = Point3D.ZERO;
    }

    public static class Driver {
        public Steering steering;
        public Lever gearLever;

        public Point3D lever() {
            return gearLever == null ? null : gearLever.pos;
        }

        public String leverBone() {
            return gearLever == null ? null : gearLever.bone;
        }
    }

    public static class Lever {
        public Point3D pos;
        public String bone;
    }

    public static class Steering {
        public Point3D hub = Point3D.ZERO;
        public double tilt;
        public double[] offset = {0, 0};
        public double radius = .19;
        public double ratio = 7;
        public String bone;
        public Point3D axis = new Point3D(-1, 0, 0);

        public float angle(float steer) {
            return steer * (float) ratio;
        }

        public Point3D grip(boolean left, float steer) {
            double gripAngle = .95 * Math.tanh(angle(steer) / .95);
            Vector3d point = new Vector3d(0, 0, left ? -radius : radius);
            new Matrix4d().rotateZ(-Math.PI / 2).rotateY(Math.toRadians(tilt))
                    .rotateX(-Math.PI / 2).translate(offset[0], offset[1], 0).rotateX(-gripAngle).transformPosition(point);
            return new Point3D(hub.x - point.x, hub.y + point.y, hub.z - point.z);
        }
    }

    public static class Wipers {
        public List<String> bones = new ArrayList<>();
        public double glassAngle = 45;
        public double sweep = 1.3;
        public float cycle = 28;
    }

    public static class Render {
        public String chassis = "chassis";
        public List<String> paint = new ArrayList<>();
        public List<String> dent = new ArrayList<>();
        public List<String> interior = new ArrayList<>();
        public List<String> interiorPaint = new ArrayList<>();
        public List<String> paintKeep = new ArrayList<>();
        public Map<String, Map<String, List<Integer>>> paintKeepFaces = new LinkedHashMap<>();
        public Map<String, Map<String, List<Integer>>> paintFaces = new LinkedHashMap<>();
        public Map<String, List<String>> paintAreas = new LinkedHashMap<>();
        public double paintDark = .3;
        public double paintChroma = .25;
        public String interiorColor = "ffffff";
        public String bodyColor = "ffffff";
        public double[] shadow;
        public double speedometer = Double.NaN;
    }

    @Override
    public void bake() {
        super.bake();
        if (driver != null && driver.steering == null) driver = null;
        if (lamps != null) {
            bakeLampBone(lamps.headlightBone);
        if (craft != null) craft.bake();
            bakeLampBone(lamps.taillightBone);
            if (!HEADLIGHT_HALOGEN.equals(lamps.headlightType) && !HEADLIGHT_LED.equals(lamps.headlightType)) {
                throw new IllegalArgumentException("lamps \"headlight_type\" is \"" + HEADLIGHT_HALOGEN + "\" or \"" + HEADLIGHT_LED + "\", not \"" + lamps.headlightType + "\"");
            }
            for (Glow glow : lamps.glow) {
                if (glow.bone == null) throw new IllegalArgumentException("lamps \"glow\" needs a \"bone\"");
                if (!(glow.tail >= 0 && glow.tail <= 1)) throw new IllegalArgumentException("lamps \"glow\" \"tail\" is a brightness from 0 to 1, not " + glow.tail);
                if (!TAIL_GLOW.equals(glow.when) && !BRAKE_GLOW.equals(glow.when)) {
                    throw new IllegalArgumentException("lamps \"glow\" \"when\" is \"" + TAIL_GLOW + "\" or \"" + BRAKE_GLOW + "\", not \"" + glow.when + "\"");
                }
            }
            for (Sequential strip : lamps.sequential) bakeSequential(strip);
        }
        paintBones = Set.copyOf(render.paint);
        interiorPaintBones = Set.copyOf(render.interiorPaint);
        paintKeepBones = Set.copyOf(render.paintKeep);
        paintKeepFaces = faceMasks(render.paintKeepFaces, "paint_keep_faces");
        paintFaces = faceMasks(render.paintFaces, "paint_faces");
        paintAreaBones = paintAreas();
        if (render.paintDark < 0 || render.paintDark > 1) throw new IllegalArgumentException("render \"paint_dark\" is a brightness from 0 to 1, not " + render.paintDark);
        if (render.paintChroma < 0 || render.paintChroma > 1) throw new IllegalArgumentException("render \"paint_chroma\" is a saturation from 0 to 1, not " + render.paintChroma);
        interiorColor = hexColor(render.interiorColor, "interior_color");
        bodyColor = hexColor(render.bodyColor, "body_color");
        plateFaces = plateFaces();
        interiorBones = Set.copyOf(render.interior);
        dentBones = Set.copyOf(render.dent);
        shadowHalfWidth = render.shadow == null ? size.halfWidth : render.shadow[0];
        shadowHalfLength = render.shadow == null ? size.halfLength : render.shadow[1];
        if (Double.isNaN(render.speedometer)) render.speedometer = Math.ceil(physics.maxSpeedKmh * 1.2 / 20) * 20;
        tailpipes = tailpipes();
        bakeCabin();
        if (!DRIVE_REAR.equals(physics.drive) && !DRIVE_FRONT.equals(physics.drive) && !DRIVE_ALL.equals(physics.drive)) {
            throw new IllegalArgumentException("physics \"drive\" is \"" + DRIVE_REAR + "\", \"" + DRIVE_FRONT + "\" or \"" + DRIVE_ALL + "\", not \"" + physics.drive + "\"");
        }
        tune = new DrivePhysics.Tune(physics.maxSpeedKmh / 72, acceleration(), physics.brake, physics.grip,
                (Double.isNaN(physics.wheelbase) ? axleSpan : physics.wheelbase) * scale, fuel.perTick, gearbox(),
                new DrivePhysics.Revs(engine.idleRpm, engine.limitRpm, engine.maxRpm, engine.revRpm), massRatio, DRIVE_ALL.equals(physics.drive), transmission.automatic);
    }

    private Map<String, ModelGeometry.Face> plateFaces() {
        if (!PLATE_STYLES.contains(plates.style)) throw new IllegalArgumentException("plates \"style\" is one of " + PLATE_STYLES + ", not \"" + plates.style + "\"");
        Map<String, ModelGeometry.Face> faces = new LinkedHashMap<>();
        for (Map.Entry<String, String> bone : plates.bones.entrySet()) {
            ModelGeometry.Face found = null;
            for (ModelGeometry.Face face : ModelGeometry.Face.values()) {
                if (face.name().toLowerCase(Locale.ROOT).equals(bone.getValue())) found = face;
            }
            if (found == null) throw new IllegalArgumentException("plates \"bones\" face of \"" + bone.getKey() + "\" is north, south, east, west, up or down, not \"" + bone.getValue() + "\"");
            faces.put(bone.getKey(), found);
        }
        return Map.copyOf(faces);
    }

    private static int hexColor(String value, String key) {
        try {
            return Integer.parseInt(value.replace("#", ""), 16) & 0xffffff;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("render \"" + key + "\" is a hex colour like \"ffffff\", not \"" + value + "\"");
        }
    }

    private Map<String, PaintJob.Area> paintAreas() {
        Map<String, PaintJob.Area> areas = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : render.paintAreas.entrySet()) {
            PaintJob.Area area = null;
            for (PaintJob.Area side : PaintJob.Area.values()) {
                if (side != PaintJob.Area.INTERIOR && side.name().toLowerCase(Locale.ROOT).equals(entry.getKey())) area = side;
            }
            if (area == null) throw new IllegalArgumentException("render \"paint_areas\" are \"front\", \"rear\", \"left\", \"right\" or \"top\", not \"" + entry.getKey() + "\"");
            for (String bone : entry.getValue()) {
                if (!paintBones.contains(bone)) throw new IllegalArgumentException("render \"paint_areas\" bone \"" + bone + "\" is not in \"paint\"");
                areas.put(bone, area);
            }
        }
        return Map.copyOf(areas);
    }

    private static Map<String, int[]> faceMasks(Map<String, Map<String, List<Integer>>> faces, String key) {
        Map<String, int[]> masks = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Integer>>> bone : faces.entrySet()) {
            int count = 0;
            for (List<Integer> cubes : bone.getValue().values()) {
                for (int cube : cubes) {
                    if (cube < 0) throw new IllegalArgumentException("render \"" + key + "\" cube " + cube + " of \"" + bone.getKey() + "\" is not an index");
                    count = Math.max(count, cube + 1);
                }
            }
            int[] mask = new int[count];
            for (Map.Entry<String, List<Integer>> face : bone.getValue().entrySet()) {
                int bits = faceBits(face.getKey(), key);
                for (int cube : face.getValue()) mask[cube] |= bits;
            }
            masks.put(bone.getKey(), mask);
        }
        return Map.copyOf(masks);
    }

    private static int faceBits(String face, String key) {
        if (ALL_FACES.equals(face)) return (1 << ModelGeometry.Face.values().length) - 1;
        for (ModelGeometry.Face value : ModelGeometry.Face.values()) {
            if (value.name().toLowerCase(Locale.ROOT).equals(face)) return 1 << value.ordinal();
        }
        throw new IllegalArgumentException("render \"" + key + "\" face is north, south, east, west, up, down or all, not \"" + face + "\"");
    }

    private void bakeCabin() {
        if (cabin.center != null && cabin.half != null) {
            cabinCenter = cabin.center;
            cabinHalf = cabin.half;
        } else {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (Seat seat : seats) {
                minX = Math.min(minX, seat.pos.x);
                minY = Math.min(minY, seat.pos.y);
                minZ = Math.min(minZ, seat.pos.z);
                maxX = Math.max(maxX, seat.pos.x);
                maxY = Math.max(maxY, seat.pos.y);
                maxZ = Math.max(maxZ, seat.pos.z);
            }
            cabinCenter = new Point3D((minX + maxX) / 2, (minY - .3 + maxY + CABIN_HEAD) / 2, (minZ + maxZ) / 2);
            cabinHalf = new Point3D((maxX - minX) / 2 + CABIN_SIDE, (maxY + CABIN_HEAD - minY + .3) / 2, (maxZ - minZ) / 2 + CABIN_LENGTH);
        }
        boolean doors = false;
        for (Opening opening : openingList) doors |= opening.kind == OpeningType.DOOR;
        cabinMuffle = Double.isNaN(cabin.muffle) ? (doors ? DEFAULT_MUFFLE : 0) : Math.max(0, Math.min(1, cabin.muffle));
    }

    public boolean inCabin(Point3D local) {
        return cabinMuffle > 0 && Math.abs(local.x - cabinCenter.x) <= cabinHalf.x && Math.abs(local.y - cabinCenter.y) <= cabinHalf.y
                && Math.abs(local.z - cabinCenter.z) <= cabinHalf.z;
    }

    private List<Tailpipe> tailpipes() {
        if (exhaust == null) {
            if (!exhaustPipes.isEmpty()) throw new IllegalArgumentException("\"exhaust_pipes\" needs \"exhaust\"");
            return List.of();
        }
        if (exhaustDirection.lengthSqr() < 1e-6) throw new IllegalArgumentException("\"exhaust_direction\" needs a length");
        Point3D direction = exhaustDirection.normalize();
        List<Point3D> positions = new ArrayList<>();
        positions.add(exhaust);
        positions.addAll(exhaustPipes);
        List<Tailpipe> pipes = new ArrayList<>();
        for (Point3D pos : positions) {
            pipes.add(new Tailpipe(pos, direction));
            if (exhaustMirror) pipes.add(new Tailpipe(new Point3D(-pos.x, pos.y, pos.z), new Point3D(-direction.x, direction.y, direction.z)));
        }
        return List.copyOf(pipes);
    }

    private static void bakeSequential(Sequential strip) {
        if (strip.bones.isEmpty()) throw new IllegalArgumentException("lamps \"sequential\" needs \"bones\"");
        if (!LENS_RED.equals(strip.lens) && !LENS_WHITE.equals(strip.lens)) {
            throw new IllegalArgumentException("lamps \"sequential\" \"lens\" is \"" + LENS_RED + "\" or \"" + LENS_WHITE + "\", not \"" + strip.lens + "\"");
        }
        if (strip.min == null || strip.max == null || strip.min.x < 0 || strip.min.x >= strip.max.x || strip.min.y > strip.max.y || strip.min.z > strip.max.z) {
            throw new IllegalArgumentException("lamps \"sequential\" needs \"min\" below \"max\" on the left side (x >= 0)");
        }
        if (strip.segments < 1) throw new IllegalArgumentException("lamps \"sequential\" \"segments\" is at least 1, not " + strip.segments);
    }

    private static void bakeLampBone(LampBone bone) {
        if (bone == null) return;
        bone.left = new boolean[bone.cubes.size()];
        for (int i = 0; i < bone.cubes.size(); i++) bone.left[i] = bone.cubes.get(i).equals("left");
    }

    private double acceleration() {
        if (!Double.isNaN(physics.acceleration)) return physics.acceleration;
        double power = Double.isNaN(engine.powerKw) ? 1 : engine.powerKw / 74;
        return .0135 * power / massRatio;
    }

    private DrivePhysics.Gearbox gearbox() {
        int gears = transmission.speedsKmh != null ? transmission.speedsKmh.size() : transmission.gears;
        if (gears < 1 || gears > MAX_GEARS) throw new IllegalArgumentException("1.." + MAX_GEARS + " gears");
        if (transmission.ratios != null && transmission.ratios.size() != gears) {
            throw new IllegalArgumentException("transmission \"ratios\" needs one value per gear");
        }
        if (!(transmission.shiftDown > 0 && transmission.shiftDown < transmission.shiftUp && transmission.shiftUp <= 1)) {
            throw new IllegalArgumentException("transmission needs 0 < shift_down < shift_up <= 1");
        }
        double[] fractions = new double[gears + 2];
        double[] ratios = new double[gears + 2];
        double first = Math.max(.1, Math.min(1, .23 * 5 / gears));
        for (int gear = 1; gear <= gears; gear++) {
            double fraction = transmission.speedsKmh != null ? transmission.speedsKmh.get(gear - 1) / physics.maxSpeedKmh
                    : gears == 1 ? 1 : first + (1 - first) * (gear - 1) / (gears - 1);
            fractions[gear + 1] = Math.max(.01, Math.min(1, fraction));
        }
        for (int gear = 1; gear <= gears; gear++) {
            ratios[gear + 1] = transmission.ratios != null ? transmission.ratios.get(gear - 1)
                    : 3.5 * Math.pow(fractions[2] / fractions[gear + 1], .9);
        }
        fractions[0] = Double.isNaN(transmission.reverseKmh) ? .18 : transmission.reverseKmh / physics.maxSpeedKmh;
        fractions[1] = 1;
        ratios[0] = -transmission.reverseRatio;
        ratios[1] = 0;
        return new DrivePhysics.Gearbox(fractions, ratios);
    }

    public int gears() {
        return tune.gearbox().gears();
    }

    public boolean hasEngineBay() {
        return engine.pos != null;
    }
}
