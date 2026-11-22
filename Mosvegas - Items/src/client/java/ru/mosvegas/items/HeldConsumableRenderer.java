package ru.mosvegas.items;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Places 3D consumables between the fingers. Each use phase defines where the item is
 * (in the hand, at the lips, in front of the chest); the arm is then turned so that its
 * fist reaches the item, so the item never leaves the hand. Model units are blocks;
 * geometry constants are written in model pixels.
 */
public final class HeldConsumableRenderer {
    /** Item-local pixels: the side of the can facing the palm, the filter next to the paper. */
    public static final Vector3f CAN_GRIP = new Vector3f(0, 0, 1.92f), SMOKE_GRIP = new Vector3f(0, 0.3f, 0);
    public static final Vector3f FILTER_END = new Vector3f(0, -1, 0);
    public static final float CAN_TOP = 4.21f, CAN_RIM = 1.5f;
    // The rigid arm has no elbow: looking down brings the mouth close to the shoulder, which slides back a little.
    public static final float MAX_SHOULDER_SHIFT = 6.5f / 16;
    private static final ThreadLocal<Boolean> FIRST_PERSON_ARM = ThreadLocal.withInitial(() -> false);
    // Item placement computed while posing the model, used by the held item feature of the same entity.
    private static LivingEntity posedEntity;
    private static Arm posedArm;
    private static Pose posedItem;

    private HeldConsumableRenderer() {}

    public static boolean renderingFirstPersonArm() {
        return FIRST_PERSON_ARM.get();
    }

    public static boolean handles(ItemStack stack) {
        return stack.getItem() instanceof MosVegasConsumableItem item && !item.definition().model().isEmpty();
    }

    /** Current phase of an active use, or null while the stack is only held. */
    public record Motion(String action, boolean opening, float reach, float tilt, float otherHand) {}

    public static Motion motion(LivingEntity entity, ItemStack stack, float tickDelta) {
        if (!(stack.getItem() instanceof MosVegasConsumableItem item) || !entity.isUsingItem()
                || entity.getActiveItem() != stack) return null;
        String action = item.definition().useAction();
        boolean opening = item.isOpening(stack);
        float total = item.getMaxUseTime(stack);
        float elapsed = entity.getItemUseTime() + tickDelta;
        return new Motion(action, opening, ConsumptionTimeline.reach(action, opening, elapsed, total),
                ConsumptionTimeline.tilt(elapsed, total),
                opening ? ConsumptionTimeline.otherHandReach(elapsed, total) : 0);
    }

    // ---------------------------------------------------------------- third person

    /** Runs after vanilla posed the model; turns the arms and records where the item goes. */
    public static void poseModel(BipedEntityModel<?> model, LivingEntity entity, float tickDelta) {
        posedEntity = null;
        ItemStack stack = entity.getActiveItem();
        Motion motion = motion(entity, stack, tickDelta);
        if (motion == null || motion.action.equals("none") || motion.reach <= 0 && motion.otherHand <= 0) return;
        Arm arm = activeArm(entity);
        int side = arm == Arm.RIGHT ? 1 : -1;
        boolean slim = isSlim(entity), smoke = motion.action.equals("smoke");
        ModelPart hand = side == 1 ? model.rightArm : model.leftArm;
        if (!motion.opening && !smoke) {
            // Tip the head back slightly while drinking.
            model.head.pitch -= MathHelper.RADIANS_PER_DEGREE * 16 * motion.reach * (0.4f + 0.6f * motion.tilt);
            model.hat.copyTransform(model.head);
        }
        Pose head = Pose.of(model.head);
        if (!handles(stack)) {
            // Flat items are drawn by vanilla at the fist; bring the fist in front of the mouth.
            Vector3f fist = new Vector3f(-side * centre(slim), 10, -2).div(16);
            Vector3f mouth = head.transform(px(-side * 0.5f, -1.5f, -7));
            reach(hand, fist, Pose.of(hand).transform(fist).lerp(mouth, motion.reach));
            return;
        }
        float scale = displayScale(stack, entity, arm, false);
        Vector3f grip = scaled(smoke ? SMOKE_GRIP : CAN_GRIP, scale);
        Pose idle = Pose.of(hand).then(idleInHand(smoke, slim, side, scale));
        Pose target = motion.opening ? chest(side, scale) : head.then(atLips(smoke, side, motion.tilt, scale));
        Pose item = hold(hand, handPoint(smoke, slim, side), Pose.blend(idle, target, motion.reach, grip), grip);
        if (motion.otherHand > 0 && entity.getStackInHand(otherHand(entity)).isEmpty()) {
            ModelPart other = side == 1 ? model.leftArm : model.rightArm;
            Vector3f tip = new Vector3f(side * centre(slim), 10.3f, 0).div(16);
            Vector3f tab = item.transform(scaled(new Vector3f(0, CAN_TOP + 0.4f, 0), scale));
            reach(other, tip, Pose.of(other).transform(tip).lerp(tab, motion.otherHand));
        }
        posedEntity = entity;
        posedArm = arm;
        posedItem = item;
    }

    public static void thirdPerson(LivingEntity entity, ItemStack stack, Arm arm, BipedEntityModel<?> model,
                                   MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        Pose item = posedEntity == entity && posedArm == arm ? posedItem : null;
        if (item == null) {
            boolean smoke = isSmoke(stack);
            int side = arm == Arm.RIGHT ? 1 : -1;
            item = Pose.of(side == 1 ? model.rightArm : model.leftArm)
                    .then(idleInHand(smoke, isSlim(entity), side, displayScale(stack, entity, arm, false)));
        }
        matrices.push();
        try {
            item.apply(matrices);
            renderItem(entity, stack, arm, false, matrices, vertices, light);
            SmokeEmitter.fromHeldItem(entity, stack, matrices.peek(), displayScale(stack, entity, arm, false), false);
        } finally {
            matrices.pop();
        }
    }

    /** Model-pixel point on the raw arm frame (ModelPart.rotate) that holds the item. */
    public static Vector3f handPoint(boolean smoke, boolean slim, int side) {
        // Cigarette: between the fingertips at the front-bottom edge of the fist.
        // Can: centre of the fist's front face, the can stands in front of it.
        return (smoke ? new Vector3f(-side * centre(slim), 9.7f, -2) : new Vector3f(-side * centre(slim), 8.6f, -2)).div(16);
    }

    /** Item pose relative to the arm while it is only held. */
    public static Pose idleInHand(boolean smoke, boolean slim, int side, float scale) {
        Quaternionf rotation = smoke
                // Ember forward and slightly down and inwards, filter at the fingers.
                ? new Quaternionf().rotateY(MathHelper.RADIANS_PER_DEGREE * -side * 15).rotateX(MathHelper.RADIANS_PER_DEGREE * -65)
                // Upright; vanilla's held-item arm pose leans 18 degrees forward.
                : new Quaternionf().rotateX(MathHelper.RADIANS_PER_DEGREE * 18).rotateZ(MathHelper.PI);
        return Pose.holding(handPoint(smoke, slim, side), rotation, scaled(smoke ? SMOKE_GRIP : CAN_GRIP, scale));
    }

    /** Item pose relative to the head: filter or lower lid edge at the lips. */
    public static Pose atLips(boolean smoke, int side, float tilt, float scale) {
        if (smoke) {
            Vector3f direction = new Vector3f(-side * 0.25f, 0.25f, -1).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction);
            return Pose.holding(px(-side * 0.6f, -1.6f, -4.1f), rotation, scaled(FILTER_END, scale));
        }
        // The can's top points at the face; it tips further as the drink empties.
        float angle = MathHelper.RADIANS_PER_DEGREE * (50 + 50 * tilt);
        Vector3f axis = new Vector3f(0, -MathHelper.cos(angle), MathHelper.sin(angle));
        Quaternionf rotation = Pose.basis(axis, new Vector3f(-side, 0.5f, 0));
        return Pose.holding(px(-side * 0.4f, -1.4f, -4.15f), rotation, rimNearest(rotation, new Vector3f(0, 0, 1), scale));
    }

    /** Can pose relative to the body while it is being opened. */
    public static Pose chest(int side, float scale) {
        return new Pose(px(-side * 1.5f, 4, -7), Pose.basis(new Vector3f(0, -1, 0), new Vector3f(-side * 0.8f, 0, 0.6f)));
    }

    /**
     * Brings the fist to the item's grip. Where the arm cannot quite reach (extreme head
     * angles), the item moves to the fist instead, so it never floats away from the hand.
     */
    public static Pose hold(ModelPart hand, Vector3f handPoint, Pose item, Vector3f grip) {
        reach(hand, handPoint, item.transform(grip));
        Vector3f gap = Pose.of(hand).transform(handPoint).sub(item.transform(grip));
        return new Pose(new Vector3f(item.position()).add(gap), item.rotation());
    }

    /**
     * Turns a vanilla-posed arm so that {@code local} reaches {@code target}. The smallest
     * rotation keeps the vanilla twist; a short shoulder shift covers the rigid arm's length.
     */
    public static void reach(ModelPart part, Vector3f local, Vector3f target) {
        Pose pose = Pose.of(part);
        Vector3f current = pose.rotation().transform(new Vector3f(local));
        Vector3f wanted = new Vector3f(target).sub(pose.position());
        if (wanted.lengthSquared() < 1e-8f || current.lengthSquared() < 1e-8f) return;
        Quaternionf rotation = new Quaternionf().rotationTo(current, wanted).mul(pose.rotation());
        Vector3f shift = new Vector3f(wanted).sub(rotation.transform(new Vector3f(local)));
        if (shift.length() > MAX_SHOULDER_SHIFT) shift.normalize(MAX_SHOULDER_SHIFT);
        part.pivotX += shift.x * 16;
        part.pivotY += shift.y * 16;
        part.pivotZ += shift.z * 16;
        Vector3f angles = ConsumptionPose.modelAngles(rotation);
        part.pitch = angles.x;
        part.yaw = angles.y;
        part.roll = angles.z;
    }

    // ---------------------------------------------------------------- first person

    public static void firstPerson(AbstractClientPlayerEntity player, ItemStack stack, Arm arm, float tickDelta,
                                   float equip, float swing, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        boolean smoke = isSmoke(stack), slim = isSlim(player);
        int side = arm == Arm.RIGHT ? 1 : -1;
        Motion motion = motion(player, stack, tickDelta);
        float scale = displayScale(stack, player, arm, true);
        Vector3f grip = scaled(smoke ? SMOKE_GRIP : CAN_GRIP, scale);
        Pose rig = firstPersonRig(side, equip, swing);
        Pose target = motion != null && motion.opening ? firstPersonChest(side)
                : firstPersonLips(smoke, side, motion == null ? 0 : motion.tilt, scale);
        Pose item = rig.then(Pose.blend(firstPersonIdle(smoke, side, scale), target, motion == null ? 0 : motion.reach, grip));
        if (!player.isInvisible()) {
            Vector3f facing = item.rotation().transform(smoke ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, -1));
            renderArm(player, arm, firstPersonArm(rig.transform(shoulder(side)), item.transform(grip), facing,
                    handPoint(smoke, slim, side)), matrices, vertices, light);
            if (motion != null && motion.otherHand > 0 && player.getStackInHand(otherHand(player)).isEmpty()) {
                Vector3f tip = new Vector3f(side * centre(slim), 10.3f, 0).div(16);
                Vector3f tab = item.transform(scaled(new Vector3f(0, CAN_TOP + 0.4f, 0), scale));
                Vector3f rest = rig.transform(new Vector3f(-side * 0.5f, -1.05f, -0.45f));
                renderArm(player, arm.getOpposite(), firstPersonArm(rig.transform(shoulder(-side)),
                        rest.lerp(tab, motion.otherHand), new Vector3f(0, -0.3f, -1), tip), matrices, vertices, light);
            }
        }
        matrices.push();
        try {
            item.apply(matrices);
            renderItem(player, stack, arm, true, matrices, vertices, light);
            SmokeEmitter.fromHeldItem(player, stack, matrices.peek(), scale, true);
        } finally {
            matrices.pop();
        }
    }

    /** Vanilla-like equip dip and swing of the whole hand, in view space. */
    public static Pose firstPersonRig(int side, float equip, float swing) {
        float root = MathHelper.sin(MathHelper.sqrt(swing) * MathHelper.PI);
        return new Pose(new Vector3f(side * -0.4f * root,
                0.2f * MathHelper.sin(MathHelper.sqrt(swing) * MathHelper.TAU) - 0.6f * equip,
                -0.2f * MathHelper.sin(swing * MathHelper.PI)), new Quaternionf());
    }

    /** Below and in front of the camera, so the forearm rises from the bottom edge and mostly the fist shows. */
    public static Vector3f shoulder(int side) {
        return new Vector3f(side * 0.62f, -1.3f, -0.3f);
    }

    public static Pose firstPersonIdle(boolean smoke, int side, float scale) {
        if (smoke) {
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(-side * 0.35f, 0.55f, -0.75f).normalize());
            return Pose.holding(new Vector3f(side * 0.36f, -0.25f, -0.55f), rotation, scaled(SMOKE_GRIP, scale));
        }
        return new Pose(new Vector3f(side * 0.38f, -0.27f, -0.66f), Pose.basis(new Vector3f(0, 1, 0), new Vector3f(side * 0.75f, 0, 0.66f)));
    }

    /** The mouth is below the camera; these poses keep the draw readable at the bottom of the view. */
    public static Pose firstPersonLips(boolean smoke, int side, float tilt, float scale) {
        if (smoke) {
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(-side * 0.25f, 0.3f, -1).normalize());
            return Pose.holding(new Vector3f(side * 0.03f, -0.24f, -0.3f), rotation, scaled(FILTER_END, scale));
        }
        float angle = MathHelper.RADIANS_PER_DEGREE * (50 + 50 * tilt);
        Quaternionf rotation = Pose.basis(new Vector3f(0, MathHelper.cos(angle), MathHelper.sin(angle)), new Vector3f(side, -0.3f, 0));
        return Pose.holding(new Vector3f(side * 0.02f, -0.21f, -0.28f), rotation, rimNearest(rotation, new Vector3f(0, 0, 1), scale));
    }

    public static Pose firstPersonChest(int side) {
        return new Pose(new Vector3f(side * 0.16f, -0.24f, -0.58f),
                Pose.basis(new Vector3f(0, 0.9f, 0.42f), new Vector3f(side * 0.75f, 0, 0.66f)));
    }

    /**
     * A floating first-person arm: +Y runs from the shoulder towards the target, the
     * front of the fist faces {@code facing}, and the hand point lands on the target.
     */
    public static Pose firstPersonArm(Vector3f shoulder, Vector3f target, Vector3f facing, Vector3f handPoint) {
        Vector3f along = new Vector3f(target).sub(shoulder);
        Quaternionf rotation = Pose.basis(along, new Vector3f(facing).negate());
        rotation = new Quaternionf().rotationTo(rotation.transform(new Vector3f(handPoint)), along).mul(rotation);
        return Pose.holding(target, rotation, handPoint);
    }

    // ---------------------------------------------------------------- shared

    /** Lid edge point nearest to {@code towards}, in item-local blocks. */
    private static Vector3f rimNearest(Quaternionf rotation, Vector3f towards, float scale) {
        Vector3f local = new Quaternionf(rotation).conjugate().transform(new Vector3f(towards));
        local.y = 0;
        local.normalize(CAN_RIM).add(0, CAN_TOP, 0);
        return scaled(local, scale);
    }

    private static Arm activeArm(LivingEntity entity) {
        return entity.getActiveHand() == Hand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
    }

    private static Hand otherHand(LivingEntity entity) {
        return entity.getActiveHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    private static boolean isSmoke(ItemStack stack) {
        return ((MosVegasConsumableItem) stack.getItem()).definition().useAction().equals("smoke");
    }

    public static boolean isSlim(LivingEntity entity) {
        return entity instanceof AbstractClientPlayerEntity player && player.getSkinTextures().model() == SkinTextures.Model.SLIM;
    }

    private static float centre(boolean slim) {
        return slim ? 0.5f : 1;
    }

    static Vector3f px(float x, float y, float z) {
        return new Vector3f(x, y, z).div(16);
    }

    public static Vector3f scaled(Vector3f pixels, float scale) {
        return new Vector3f(pixels).mul(scale / 16);
    }

    private static ModelTransformationMode mode(Arm arm, boolean first) {
        boolean left = arm == Arm.LEFT;
        return first ? (left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND)
                : (left ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);
    }

    /** Scale of the item model's display transform, so custom models keep their configured size. */
    static float displayScale(ItemStack stack, LivingEntity entity, Arm arm, boolean first) {
        return MinecraftClient.getInstance().getItemRenderer().getModel(stack, entity.getWorld(), entity, entity.getId())
                .getTransformation().getTransformation(mode(arm, first)).scale.y();
    }

    private static void renderItem(LivingEntity entity, ItemStack stack, Arm arm, boolean first,
                                   MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        MinecraftClient.getInstance().getItemRenderer().renderItem(entity, stack, mode(arm, first), arm == Arm.LEFT,
                matrices, vertices, entity.getWorld(), light, OverlayTexture.DEFAULT_UV, entity.getId());
    }

    public static void renderArm(AbstractClientPlayerEntity player, Arm arm, Pose pose,
                                  MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        var renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
        matrices.push();
        FIRST_PERSON_ARM.set(true);
        try {
            pose.apply(matrices);
            if (arm == Arm.RIGHT) renderer.renderRightArm(matrices, vertices, light, player);
            else renderer.renderLeftArm(matrices, vertices, light, player);
        } finally {
            FIRST_PERSON_ARM.set(false);
            matrices.pop();
        }
    }
}
