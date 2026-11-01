package studio.merami.sliminterfacefabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.ResourceId;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.rendering.AnimationFile;
import studio.merami.slimcore.rendering.Animator;
import studio.merami.slimcore.rendering.ModelGeometry;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.ClientConfig;
import studio.merami.sliminterfacefabric.client.light.VehicleLights;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.Convert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static studio.merami.slimcore.rendering.Animator.POSITION;
import static studio.merami.slimcore.rendering.Animator.ROTATION;
import static studio.merami.slimcore.rendering.Animator.SCALE;

public class VehicleRenderer extends EntityRenderer<BuilderVehicle> {
    private static final double INTERIOR_LOD = 32 * 32;
    private static final int FULL_BRIGHT = 0xf000f0;
    private static final float TAIL_GLOW = .45f;
    private static final Map<ResourceId, Set<String>> GLASS_BRANCHES = new HashMap<>();
    private static final Map<ResourceId, Set<String>> GLOW_BRANCHES = new HashMap<>();
    private static final Set<ResourceId> BROKEN_MODELS = new HashSet<>();
    private final Map<EntityVehicleF_Physics, DentMesh> dentMeshes = new WeakHashMap<>();
    private final Map<Animator, Animator.Pose> shown = new WeakHashMap<>();

    private static final class Pass {
        EntityVehicleF_Physics vehicle;
        JSONVehicle definition;
        Animator.Pose pose;
        Set<String> branch;
        DentMesh dentMesh;
        boolean glass;
        boolean glow;
        float tailGlow;
        float brakeGlow;
        boolean distant;
        boolean blink;
        boolean flash;
        int dentDepth;
        int unlit;
        VertexConsumer buffer;
    }

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(BuilderVehicle vehicle) {
        return Convert.location(vehicle.vehicle().definition().textureLocation);
    }

    @Override
    public void render(BuilderVehicle builder, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        EntityVehicleF_Physics vehicle = builder.vehicle();
        JSONVehicle definition = vehicle.definition();
        Animator animator = animator(vehicle, definition);
        if (animator != null) {
            float time = vehicle.ticks() + partial;
            animator.update(time, time / 20, vehicle::animationActive);
            Animator.Pose view = shown.computeIfAbsent(animator, key -> new Animator.Pose(key.pose()));
            view.copyFrom(animator.pose());
            VehicleAnimations.apply(vehicle, definition, partial, view);
            Pass pass = new Pass();
            pass.vehicle = vehicle;
            pass.definition = definition;
            pass.pose = view;
            pass.distant = entityRenderDispatcher.distanceToSqr(builder) > INTERIOR_LOD;
            pass.blink = (builder.level().getGameTime() % 16) < 8;
            pass.flash = vehicle.lockFlash(builder.level().getGameTime());
            if (!vehicle.dents().isEmpty() && !definition.dentBones.isEmpty()) {
                DentMesh mesh = dentMeshes.get(vehicle);
                if (mesh == null || !mesh.current(vehicle.dents())) dentMeshes.put(vehicle, mesh = new DentMesh(vehicle.dents()));
                pass.dentMesh = mesh;
            }
            ResourceLocation texture = getTextureLocation(builder);
            pose.pushPose();
            draw(builder, pass, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), partial, light);
            if (!definition.glass.isEmpty()) {
                pass.glass = true;
                pass.branch = glassBranch(definition, animator.model);
                pass.dentDepth = 0;
                draw(builder, pass, pose, buffers.getBuffer(VehicleLights.active() ? VehicleRenderTypes.glass(texture) : RenderType.entityTranslucent(texture)), partial, light);
            }
            JSONVehicle.Lamps lamps = definition.lamps;
            if (lamps != null && !lamps.glow.isEmpty() && !(vehicle.lampBroken(EntityVehicleF_Physics.TAILLIGHT_LEFT) && vehicle.lampBroken(EntityVehicleF_Physics.TAILLIGHT_RIGHT))) {
                boolean braking = vehicle.flag(EntityVehicleF_Physics.BRAKING);
                pass.brakeGlow = braking ? 1 : 0;
                pass.tailGlow = braking ? 1 : vehicle.flag(EntityVehicleF_Physics.LIGHTS) ? TAIL_GLOW : 0;
                ResourceLocation mask = pass.tailGlow > 0 ? LampGlow.mask(texture) : null;
                if (mask != null) {
                    pass.glass = false;
                    pass.glow = true;
                    pass.branch = glowBranch(definition, animator.model);
                    pass.dentDepth = 0;
                    draw(builder, pass, pose, buffers.getBuffer(RenderType.eyes(mask)), partial, light);
                }
            }
            pose.popPose();
        }
        super.render(builder, yaw, partial, pose, buffers, light);
    }

    private static Animator animator(EntityVehicleF_Physics vehicle, JSONVehicle definition) {
        if (definition.modelLocation == null || BROKEN_MODELS.contains(definition.modelLocation)) return null;
        try {
            return vehicle.animator();
        } catch (RuntimeException e) {
            BROKEN_MODELS.add(definition.modelLocation);
            SlimCore.LOGGER.error("Cannot draw vehicle {}: {}", definition.id, e.getMessage());
            return null;
        }
    }

    public static void registerReload() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SlimVehicle.id("models");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ModelGeometry.clearCache();
                AnimationFile.clearCache();
                synchronized (VehicleRenderer.class) {
                    GLASS_BRANCHES.clear();
                    GLOW_BRANCHES.clear();
                }
                BROKEN_MODELS.clear();
                LampGlow.clear();
            }
        });
    }

    private void draw(BuilderVehicle builder, Pass pass, PoseStack pose, VertexConsumer buffer, float partial, int light) {
        pass.buffer = buffer;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, builder.yRotO, builder.getYRot())));
        float scale = (float) pass.vehicle.scale();
        pose.scale(scale, scale, scale);
        pose.translate(0.0F, 0.01F, 0.0F);
        for (ModelGeometry.Bone bone : pass.pose.model.roots) drawBone(pass, pose, bone, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }

    private static boolean isIndicator(String bone, boolean left) {
        return bone.startsWith("indicator_") && bone.endsWith(left ? "left" : "right");
    }

    private static synchronized Set<String> glassBranch(AJSONMultipart definition, ModelGeometry model) {
        return GLASS_BRANCHES.computeIfAbsent(definition.modelLocation, key -> branch(model, definition.glass.stream().map(pane -> pane.bone).toList()));
    }

    private static synchronized Set<String> glowBranch(JSONVehicle definition, ModelGeometry model) {
        return GLOW_BRANCHES.computeIfAbsent(definition.modelLocation, key -> branch(model, definition.lamps.glow.stream().map(glow -> glow.bone).toList()));
    }

    private static Set<String> branch(ModelGeometry model, List<String> ends) {
        Set<String> branch = new HashSet<>();
        for (String end : ends) {
            for (ModelGeometry.Bone bone = model.bone(end); bone != null && branch.add(bone.name); bone = bone.parent) {
            }
        }
        return Set.copyOf(branch);
    }

    private static float glowLevel(Pass pass, String bone) {
        for (JSONVehicle.Glow glow : pass.definition.lamps.glow) {
            if (glow.bone.equals(bone)) return glow.brakeOnly() ? pass.brakeGlow : pass.tailGlow;
        }
        return 0;
    }

    private void drawBone(Pass pass, PoseStack pose, ModelGeometry.Bone bone, int light, int overlay, float red, float green, float blue, float alpha) {
        JSONVehicle definition = pass.definition;
        EntityVehicleF_Physics vehicle = pass.vehicle;
        String name = bone.name;
        pass.unlit = light;
        if (pass.branch != null && !pass.branch.contains(name)) return;
        if (pass.distant && definition.interiorBones.contains(name)) return;
        if (isIndicator(name, true) || isIndicator(name, false)) {
            boolean left = isIndicator(name, true);
            if (vehicle.flag(left ? EntityVehicleF_Physics.TURN_LEFT : EntityVehicleF_Physics.TURN_RIGHT) && pass.blink || pass.flash) {
                light = FULL_BRIGHT;
                red = 1;
                green = .55f;
                blue = .10f;
            } else if (vehicle.flag(EntityVehicleF_Physics.LIGHTS) && definition.lamps != null && definition.lamps.frontIndicator != null && name.startsWith("indicator_front")) {
                boolean led = ClientConfig.led(definition.lamps);
                light = FULL_BRIGHT;
                red = led ? .93f : 1;
                green = led ? .97f : .93f;
                blue = led ? 1 : .78f;
            }
        }
        if (definition.glassByBone.containsKey(name)) {
            float shade = 1 - vehicle.tint() / 115f;
            red = green = blue = shade;
            alpha = .22f + vehicle.tint() / 140f;
        }
        JSONVehicle.Lamps lamps = definition.lamps;
        if (lamps != null && name.equals(lamps.headlightBoneName()) && vehicle.flag(EntityVehicleF_Physics.LIGHTS)) {
            boolean led = ClientConfig.led(lamps);
            light = FULL_BRIGHT;
            red = led ? .93f : 1;
            green = led ? .97f : .96f;
            blue = led ? 1 : .72f;
        }
        if (lamps != null && name.equals(lamps.taillightBoneName()) && (vehicle.flag(EntityVehicleF_Physics.LIGHTS) || vehicle.flag(EntityVehicleF_Physics.BRAKING))) {
            light = FULL_BRIGHT;
            red = vehicle.flag(EntityVehicleF_Physics.BRAKING) ? 1 : .42f;
            green = .05f;
            blue = .02f;
        }
        if (definition.paintBones.contains(name)) {
            red *= ((vehicle.color() >> 16) & 255) / 255f;
            green *= ((vehicle.color() >> 8) & 255) / 255f;
            blue *= (vehicle.color() & 255) / 255f;
        }
        boolean dented = definition.dentBones.contains(name);
        if (dented) pass.dentDepth++;
        float[] values = pass.pose.values[bone.index];
        pose.pushPose();
        pose.translate(-values[POSITION] / 16.0F, values[POSITION + 1] / 16.0F, values[POSITION + 2] / 16.0F);
        pose.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
        if (values[ROTATION + 2] != 0) pose.mulPose(Axis.ZP.rotation(values[ROTATION + 2]));
        if (values[ROTATION + 1] != 0) pose.mulPose(Axis.YP.rotation(values[ROTATION + 1]));
        if (values[ROTATION] != 0) pose.mulPose(Axis.XP.rotation(values[ROTATION]));
        pose.scale(values[SCALE], values[SCALE + 1], values[SCALE + 2]);
        pose.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
        drawCubes(pass, pose, bone, light, overlay, red, green, blue, alpha);
        if (!pass.pose.hideChildren[bone.index]) {
            for (ModelGeometry.Bone child : bone.children) drawBone(pass, pose, child, light, overlay, red, green, blue, alpha);
        }
        pose.popPose();
        if (dented) pass.dentDepth--;
    }

    private static void hinge(PoseStack pose, ModelGeometry.Bone bone, float[] values, EntityVehicleF_Physics vehicle) {
        Point3D push = DamageModel.displacement(vehicle.dents(), new Point3D(-bone.pivotX / 16, bone.pivotY / 16, -bone.pivotZ / 16));
        if (push.lengthSqr() < 1e-8) return;
        Vector3f shift = new Vector3f((float) -push.x, (float) push.y, (float) -push.z);
        Vector3f back = new Matrix3f().rotateZ(values[ROTATION + 2]).rotateY(values[ROTATION + 1]).rotateX(values[ROTATION]).transpose().transform(new Vector3f(shift));
        pose.translate(back.x - shift.x, back.y - shift.y, back.z - shift.z);
    }

    private void drawCubes(Pass pass, PoseStack pose, ModelGeometry.Bone bone, int light, int overlay, float red, float green, float blue, float alpha) {
        JSONVehicle definition = pass.definition;
        EntityVehicleF_Physics vehicle = pass.vehicle;
        String name = bone.name;
        boolean hidden = pass.pose.hidden[bone.index];
        if (!vehicle.dents().isEmpty() && definition.openingsByBone.containsKey(name)) hinge(pose, bone, pass.pose.values[bone.index], vehicle);
        if (pass.glow) {
            float level = glowLevel(pass, name);
            if (level <= 0 || hidden) return;
            for (ModelGeometry.Cube cube : bone.cubes) {
                pose.pushPose();
                drawCube(pass, pose, cube, FULL_BRIGHT, overlay, level, level, level, 1);
                pose.popPose();
            }
            return;
        }
        if (definition.glassByBone.containsKey(name) != pass.glass) return;
        JSONVehicle.Lamps lamps = definition.lamps;
        boolean head = lamps != null && name.equals(lamps.headlightBoneName());
        boolean tail = lamps != null && name.equals(lamps.taillightBoneName());
        boolean[] sides = head ? lamps.headlightBone.left : tail ? lamps.taillightBone.left : null;
        if (sides != null && !hidden) {
            int left = head ? EntityVehicleF_Physics.HEADLIGHT_LEFT : EntityVehicleF_Physics.TAILLIGHT_LEFT;
            int right = head ? EntityVehicleF_Physics.HEADLIGHT_RIGHT : EntityVehicleF_Physics.TAILLIGHT_RIGHT;
            if (vehicle.lampBroken(left) || vehicle.lampBroken(right)) {
                List<ModelGeometry.Cube> cubes = bone.cubes;
                for (int i = 0; i < cubes.size(); i++) {
                    boolean broken = i < sides.length && vehicle.lampBroken(sides[i] ? left : right);
                    pose.pushPose();
                    if (broken) drawCube(pass, pose, cubes.get(i), pass.unlit, overlay, .24f, .22f, .21f, alpha);
                    else drawCube(pass, pose, cubes.get(i), light, overlay, red, green, blue, alpha);
                    pose.popPose();
                }
                return;
            }
        }
        if (hidden) return;
        for (ModelGeometry.Cube cube : bone.cubes) {
            pose.pushPose();
            drawCube(pass, pose, cube, light, overlay, red, green, blue, alpha);
            pose.popPose();
        }
    }

    static void cubeTransform(PoseStack pose, ModelGeometry.Cube cube) {
        pose.translate(cube.pivotX() / 16.0, cube.pivotY() / 16.0, cube.pivotZ() / 16.0);
        pose.mulPose(new Quaternionf().rotationXYZ(0.0F, 0.0F, (float) cube.rotZ()));
        pose.mulPose(new Quaternionf().rotationXYZ(0.0F, (float) cube.rotY(), 0.0F));
        pose.mulPose(new Quaternionf().rotationXYZ((float) cube.rotX(), 0.0F, 0.0F));
        pose.translate(-cube.pivotX() / 16.0, -cube.pivotY() / 16.0, -cube.pivotZ() / 16.0);
    }

    static void fixInvertedFlatCube(ModelGeometry.Cube cube, Vector3f normal) {
        if (normal.x() < 0.0F && (cube.sizeY() == 0.0 || cube.sizeZ() == 0.0)) normal.mul(-1.0F, 1.0F, 1.0F);
        if (normal.y() < 0.0F && (cube.sizeX() == 0.0 || cube.sizeZ() == 0.0)) normal.mul(1.0F, -1.0F, 1.0F);
        if (normal.z() < 0.0F && (cube.sizeX() == 0.0 || cube.sizeY() == 0.0)) normal.mul(1.0F, 1.0F, -1.0F);
    }

    private void drawCube(Pass pass, PoseStack pose, ModelGeometry.Cube cube, int light, int overlay, float red, float green, float blue, float alpha) {
        if (pass.dentMesh != null && pass.dentDepth > 0) {
            pass.dentMesh.renderCube(pose, cube, pass.buffer, light, overlay, red, green, blue, alpha);
            return;
        }
        cubeTransform(pose, cube);
        Matrix3f normalPose = pose.last().normal();
        Matrix4f position = new Matrix4f(pose.last().pose());
        VertexConsumer buffer = pass.buffer;
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            Vector3f normal = normalPose.transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
            fixInvertedFlatCube(cube, normal);
            for (ModelGeometry.Vertex vertex : quad.vertices()) {
                Vector4f at = position.transform(new Vector4f(vertex.x(), vertex.y(), vertex.z(), 1.0F));
                buffer.vertex(at.x(), at.y(), at.z(), red, green, blue, alpha, vertex.u(), vertex.v(), overlay, light, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
