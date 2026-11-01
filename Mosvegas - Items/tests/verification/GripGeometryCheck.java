import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.mosvegas.items.ConsumptionPose;
import ru.mosvegas.items.HeldConsumableRenderer;
import ru.mosvegas.items.Pose;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;
import software.bernie.geckolib.util.RenderUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks the held poses against the real meshes and display transforms without opening a game window. */
public class GripGeometryCheck {
    private static final Path ASSETS = Path.of(System.getProperty("mosvegas.projectDir", ""), "src/main/resources/assets/mosvegas_items");
    // Vertical half field of view of first-person hands (fixed 70 degrees) and a 16:9 window.
    private static final float VIEW_Y = (float) Math.tan(Math.toRadians(35)), VIEW_X = VIEW_Y * 16 / 9;
    private static int checks;
    private static float worstMiss, worstShift, minVisible = 1;

    public static void main(String[] args) throws Exception {
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        BakedGeoModel cigarette = loadGeometry("cigarette"), beer = loadGeometry("beer");
        JsonUnbakedModel smokeDisplay = loadDisplay("consumable_smoke"), canDisplay = loadDisplay("consumable");
        for (boolean smoke : new boolean[]{false, true}) {
            BakedGeoModel model = smoke ? cigarette : beer;
            JsonUnbakedModel display = smoke ? smokeDisplay : canDisplay;
            for (boolean opening : smoke ? new boolean[]{false} : new boolean[]{false, true})
                for (int side : new int[]{-1, 1}) {
                    for (boolean slim : new boolean[]{false, true})
                        for (float pitch : new float[]{-0.9f, 0, 0.9f}) for (float yaw : new float[]{-0.6f, 0, 0.6f})
                            for (int step = 0; step <= 8; step++)
                                thirdPerson(model, display, smoke, opening, slim, side, pitch, yaw, step / 8f);
                    for (float equip : new float[]{0, 0.5f}) for (int step = 0; step <= 8; step++)
                        firstPerson(model, display, smoke, opening, side, equip, step / 8f);
                }
        }
        cigaretteBurn(cigarette);
        System.out.printf("PASS: %d grip/mesh checks; worst arm reach miss %.2f px, worst shoulder shift %.2f px, "
                + "least visible first-person item %.0f%%%n", checks, worstMiss, worstShift, minVisible * 100);
    }

    private static void thirdPerson(BakedGeoModel model, JsonUnbakedModel display, boolean smoke, boolean opening,
                                    boolean slim, int side, float pitch, float yaw, float reach) {
        String context = (smoke ? "cigarette" : opening ? "opening can" : "can") + " third person slim=" + slim
                + " side=" + side + " head=" + pitch + "/" + yaw + " reach=" + reach;
        ModelTransformationMode mode = side == 1 ? ModelTransformationMode.THIRD_PERSON_RIGHT_HAND : ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
        float scale = display.getTransformations().getTransformation(mode).scale.y();
        ModelPart head = part();
        head.pitch = pitch;
        head.yaw = yaw;
        // Vanilla's arm holding an item.
        ModelPart arm = part();
        arm.pivotX = -side * 5;
        arm.pivotY = slim ? 2.5f : 2;
        arm.pitch = -(float) Math.PI / 10;
        Vector3f shoulder = new Vector3f(arm.pivotX, arm.pivotY, arm.pivotZ);
        Vector3f grip = HeldConsumableRenderer.scaled(smoke ? HeldConsumableRenderer.SMOKE_GRIP : HeldConsumableRenderer.CAN_GRIP, scale);
        Pose idle = Pose.of(arm).then(HeldConsumableRenderer.idleInHand(smoke, slim, side, scale));
        Pose target = opening ? HeldConsumableRenderer.chest(side, scale)
                : Pose.of(head).then(HeldConsumableRenderer.atLips(smoke, side, reach, scale));
        Pose wanted = Pose.blend(idle, target, reach, grip);
        Vector3f handPoint = HeldConsumableRenderer.handPoint(smoke, slim, side);
        Pose item = HeldConsumableRenderer.hold(arm, handPoint, wanted, grip);

        require(Pose.of(arm).transform(handPoint).distance(item.transform(grip)) < 1e-5f, "The item stays in the fist " + context);
        // How far the item had to give way because the arm could not reach its choreographed place.
        float miss = item.position().distance(wanted.position()) * 16;
        float shift = new Vector3f(arm.pivotX, arm.pivotY, arm.pivotZ).distance(shoulder);
        worstMiss = Math.max(worstMiss, miss);
        worstShift = Math.max(worstShift, shift);
        // On the way the item may give way a little; at the lips and in front of the chest it must be in place.
        if (reach == 0 || reach == 1) require(miss < 1, "The arm must reach the item's place: miss=" + miss + " px " + context);
        require(shift <= HeldConsumableRenderer.MAX_SHOULDER_SHIFT * 16 + 1e-3f, "The shoulder may only shift slightly: " + shift + " px " + context);
        Matrix4f rendered = renderTransform(item, display, mode, side);
        Vector3f gripPixels = new Vector3f(smoke ? HeldConsumableRenderer.SMOKE_GRIP : HeldConsumableRenderer.CAN_GRIP).div(16);
        require(rendered.transformPosition(gripPixels).distance(item.transform(grip)) < 1e-5f,
                "Display transforms must keep the gripped point of the real mesh in the hand " + context);
        Vector3f up = item.rotation().transform(new Vector3f(0, 1, 0));
        if (reach == 0) {
            // Model space: -Y is up, -Z is forward.
            if (smoke) require(up.z < -0.3f, "A held cigarette points its lit end forward: " + up + " " + context);
            else require(up.y < -0.98f, "A held can stands upright: " + up + " " + context);
        }
        if (reach < 1 || opening) return;
        Matrix4f toHead = new Matrix4f().translation(Pose.of(head).position()).rotate(Pose.of(head).rotation()).invert();
        List<Vector3f> local = mesh(model, rendered, 1).stream().flatMap(face -> face.vertices.stream())
                .map(point -> toHead.transformPosition(new Vector3f(point)).mul(16)).toList();
        long inside = local.stream().filter(p -> Math.abs(p.x) < 3.95f && p.y > -7.95f && p.y < -0.05f && Math.abs(p.z) < 3.95f).count();
        require(inside == 0, "The item must not sink into the head: " + inside + " vertices " + context);
        float nearest = (float) local.stream().mapToDouble(p -> p.z).min().orElseThrow();
        float touching = (float) local.stream().mapToDouble(p -> p.z).max().orElseThrow();
        require(touching > -4.6f, "The filter or rim must touch the lips: " + touching + " px " + context);
        Vector3f headUp = new Matrix4f(toHead).transformDirection(new Vector3f(up));
        if (smoke) require(headUp.z < -0.8f && nearest < -6,
                "The cigarette points away from the face, filter in the mouth: " + headUp + " " + context);
        else require(headUp.z > 0.5f, "The can's top faces the mouth: " + headUp + " " + context);
    }

    private static void firstPerson(BakedGeoModel model, JsonUnbakedModel display, boolean smoke, boolean opening,
                                    int side, float equip, float reach) {
        String context = (smoke ? "cigarette" : opening ? "opening can" : "can") + " first person side=" + side
                + " equip=" + equip + " reach=" + reach;
        ModelTransformationMode mode = side == 1 ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        float scale = display.getTransformations().getTransformation(mode).scale.y();
        Vector3f grip = HeldConsumableRenderer.scaled(smoke ? HeldConsumableRenderer.SMOKE_GRIP : HeldConsumableRenderer.CAN_GRIP, scale);
        Pose rig = HeldConsumableRenderer.firstPersonRig(side, equip, 0);
        Pose target = opening ? HeldConsumableRenderer.firstPersonChest(side)
                : HeldConsumableRenderer.firstPersonLips(smoke, side, reach, scale);
        Pose item = rig.then(Pose.blend(HeldConsumableRenderer.firstPersonIdle(smoke, side, scale), target, reach, grip));
        List<Vector3f> points = mesh(model, renderTransform(item, display, mode, side), 1).stream()
                .flatMap(face -> face.vertices.stream()).toList();
        require(points.stream().allMatch(p -> p.z < -0.12f), "The held item stays in front of the camera " + context);
        if (equip == 0) {
            // At the mouth the item sits at the bottom edge of the view, but most of it must stay visible.
            float onScreen = points.stream().filter(p -> Math.abs(p.x / p.z) < VIEW_X && Math.abs(p.y / p.z) < VIEW_Y).count()
                    / (float) points.size();
            minVisible = Math.min(minVisible, onScreen);
            require(onScreen > 0.4f, "The held item is on screen: " + onScreen + " " + context);
        }
        boolean slim = false;
        Vector3f handPoint = HeldConsumableRenderer.handPoint(smoke, slim, side);
        Vector3f facing = item.rotation().transform(smoke ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, -1));
        Pose arm = HeldConsumableRenderer.firstPersonArm(rig.transform(HeldConsumableRenderer.shoulder(side)),
                item.transform(grip), facing, handPoint);
        require(arm.transform(handPoint).distance(item.transform(grip)) < 1e-4f, "The first-person fist holds the item " + context);
        // The arm enters the view from its edge instead of ending in mid-air.
        Vector3f top = arm.transform(new Vector3f(-side, -2, 0).div(16));
        require(top.z > -0.05f || top.y / -top.z < -VIEW_Y || Math.abs(top.x / top.z) > VIEW_X,
                "The upper end of the arm must be off screen: " + top + " " + context);
    }

    private static void cigaretteBurn(BakedGeoModel cigarette) {
        List<Face> full = mesh(cigarette, new Matrix4f(), 1);
        for (float fraction : new float[]{0.04f, 0.2f, 0.6f}) {
            List<Face> burned = mesh(cigarette, new Matrix4f(), fraction);
            require(centre(full, "filter").distance(centre(burned, "filter")) < 1e-5f, "Smoking keeps the filter fixed");
            require(Math.abs((maximumY(burned, "paper") - minimumY(burned, "paper"))
                    / (maximumY(full, "paper") - minimumY(full, "paper")) - fraction) < 1e-4f,
                    "The remaining paper matches the remaining fraction " + fraction);
            require(Math.abs(minimumY(burned, "ember") - maximumY(burned, "paper")) < 1e-5f,
                    "The ember stays attached to the burned paper " + fraction);
            require(Math.abs(maximumY(burned, "ember") * 16 - ConsumptionPose.emberTop(fraction)) < 1e-3f,
                    "Smoke rises from the actual ember " + fraction);
        }
        for (int remaining = 1; remaining <= 5; remaining++) {
            require(Math.abs(ConsumptionPose.remainingFraction(remaining, 5, 0) - remaining / 5f) < 1e-5f,
                    "Cigarette length follows the stored puffs");
            require(Math.abs(ConsumptionPose.remainingFraction(remaining, 5, 1)
                    - ConsumptionPose.remainingFraction(remaining - 1, 5, 0)) < 1e-5f,
                    "Burning is continuous when a puff is consumed");
        }
    }

    private static BakedGeoModel loadGeometry(String name) throws Exception {
        Model model = JsonUtil.GEO_GSON.fromJson(Files.readString(ASSETS.resolve("geo/" + name + ".geo.json")), Model.class);
        return BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
    }

    private static JsonUnbakedModel loadDisplay(String name) throws Exception {
        // Both models define every hand transform themselves, so the parent is not needed.
        return JsonUnbakedModel.deserialize(Files.readString(ASSETS.resolve("models/item/" + name + ".json")));
    }

    /** The item pose, then the display transform, ItemRenderer's origin and GeoItemRenderer.preRender. */
    private static Matrix4f renderTransform(Pose item, JsonUnbakedModel display, ModelTransformationMode mode, int side) {
        MatrixStack matrices = new MatrixStack();
        item.apply(matrices);
        display.getTransformations().getTransformation(mode).apply(side == -1, matrices);
        matrices.translate(-0.5f, -0.5f, -0.5f);
        matrices.translate(0.5f, 0.51f, 0.5f);
        return new Matrix4f(matrices.peek().getPositionMatrix());
    }

    private static List<Face> mesh(BakedGeoModel model, Matrix4f rendered, float fraction) {
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(rendered);
        List<Face> faces = new ArrayList<>();
        for (GeoBone bone : model.topLevelBones()) appendBone(bone, matrices, fraction, faces);
        return faces;
    }

    private static void appendBone(GeoBone bone, MatrixStack matrices, float fraction, List<Face> faces) {
        float oldScale = bone.getScaleY(), oldPosition = bone.getPosY();
        ConsumptionPose.applyCigaretteBurn(bone, fraction);
        matrices.push();
        try {
            RenderUtils.prepMatrixForBone(matrices, bone);
            for (GeoCube cube : bone.getCubes()) {
                matrices.push();
                RenderUtils.translateToPivotPoint(matrices, cube);
                RenderUtils.rotateMatrixAroundCube(matrices, cube);
                RenderUtils.translateAwayFromPivotPoint(matrices, cube);
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    List<Vector3f> points = new ArrayList<>();
                    for (var vertex : quad.vertices()) {
                        var pos = vertex.position();
                        points.add(matrices.peek().getPositionMatrix().transformPosition(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z)));
                    }
                    faces.add(new Face(bone.getName(), points));
                }
                matrices.pop();
            }
            for (GeoBone child : bone.getChildBones()) appendBone(child, matrices, fraction, faces);
        } finally {
            matrices.pop();
            bone.setScaleY(oldScale);
            bone.setPosY(oldPosition);
        }
    }

    private static float minimumY(List<Face> faces, String bone) {
        return (float) faces.stream().filter(face -> face.bone.equals(bone))
                .flatMap(face -> face.vertices.stream()).mapToDouble(vertex -> vertex.y).min().orElseThrow();
    }

    private static float maximumY(List<Face> faces, String bone) {
        return (float) faces.stream().filter(face -> face.bone.equals(bone))
                .flatMap(face -> face.vertices.stream()).mapToDouble(vertex -> vertex.y).max().orElseThrow();
    }

    private static Vector3f centre(List<Face> faces, String bone) {
        List<Vector3f> points = faces.stream().filter(face -> face.bone.equals(bone)).flatMap(face -> face.vertices.stream()).toList();
        Vector3f mean = new Vector3f();
        for (Vector3f point : points) mean.add(point);
        return mean.div(points.size());
    }

    private static ModelPart part() { return new ModelPart(List.of(), Map.of()); }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private record Face(String bone, List<Vector3f> vertices) {}
}
