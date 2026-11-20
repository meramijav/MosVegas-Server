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
import studio.merami.slimcore.paint.PaintJob;
import studio.merami.slimcore.rendering.AnimationFile;
import studio.merami.slimcore.rendering.Animator;
import studio.merami.slimcore.rendering.ModelGeometry;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.ClientConfig;
import studio.merami.sliminterfacefabric.client.light.VehicleLights;
import studio.merami.sliminterfacefabric.client.paint.PaintGuide;
import studio.merami.sliminterfacefabric.client.paint.PaintReveal;
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
    private static final int BLINK_TICKS = 16;
    private static final float SWEEP_SHARE = .6f;
    private static final float SWEEP_CELL = .02f;
    private static final int SWEEP_MAX_CELLS = 64;
    private static final float SWEEP_LIFT = .002f;
    private static final float AMBER_RED = 1;
    private static final float AMBER_GREEN = .56f;
    private static final float AMBER_BLUE = .06f;
    private static final float PAINT_LIFT = .2f;
    private static final float HIGHLIGHT_KEEP = .6f;
    private static final float FRONT_GLOW = .32f;
    private static final float FRONT_CELL = .2f;
    private static final int FRONT_MAX_CELLS = 40;
    private static final float PLATE_RIM_U = .5f;
    private static final float PLATE_RIM_V = .97f;
    private static final Map<ResourceId, Set<String>> GLASS_BRANCHES = new HashMap<>();
    private static final Map<ResourceId, Set<String>> GLOW_BRANCHES = new HashMap<>();
    private static final Map<ResourceId, Set<String>> PLATE_BRANCHES = new HashMap<>();
    private static final Map<JSONVehicle.Sequential, Set<String>> SEQUENTIAL_BRANCHES = new HashMap<>();
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
        boolean painted;
        boolean keepDark;
        float baseRed;
        float baseGreen;
        float baseBlue;
        FaceTint faces;
        boolean spraying;
        boolean sprayInterior;
        int sprayFrom;
        int sprayTo;
        Matrix4f rootInverse;
        PaintJob.Area highlight;
        boolean plates;
        boolean blankPlates;
        JSONVehicle.Sequential sequential;
        boolean sweepLeft;
        boolean sweepRight;
        int sweepLit;
        VertexConsumer buffer;
    }

    record FaceTint(int mask, float red, float green, float blue, int light) {
        boolean covers(ModelGeometry.Face face) {
            return (mask >> face.ordinal() & 1) != 0;
        }
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
            pass.blink = (builder.level().getGameTime() % BLINK_TICKS) < BLINK_TICKS / 2;
            pass.flash = vehicle.lockFlash(builder.level().getGameTime());
            if (!vehicle.dents().isEmpty() && !definition.dentBones.isEmpty()) {
                DentMesh mesh = dentMeshes.get(vehicle);
                if (mesh == null || !mesh.current(vehicle.dents())) dentMeshes.put(vehicle, mesh = new DentMesh(vehicle.dents()));
                pass.dentMesh = mesh;
            }
            ResourceLocation texture = getTextureLocation(builder);
            ResourceLocation plate = definition.plateFaces.isEmpty() ? null : PlateTextures.texture(definition.plates.style, vehicle.plate());
            pose.pushPose();
            draw(builder, pass, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), partial, light);
            if (!definition.plateFaces.isEmpty()) {
                pass.plates = true;
                pass.blankPlates = plate == null;
                pass.branch = plateBranch(definition, animator.model);
                pass.dentDepth = 0;
                draw(builder, pass, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(plate == null ? texture : plate)), partial, light);
                pass.plates = false;
            }
            if (!definition.glass.isEmpty()) {
                pass.glass = true;
                pass.branch = glassBranch(definition, animator.model);
                pass.dentDepth = 0;
                draw(builder, pass, pose, buffers.getBuffer(VehicleLights.active() ? VehicleRenderTypes.glass(texture) : RenderType.entityTranslucent(texture)), partial, light);
            }
            ResourceLocation dark = PaintMask.keep(texture, definition.render.paintDark, definition.render.paintChroma);
            if (dark != null && (!definition.paintBones.isEmpty() || !definition.interiorPaintBones.isEmpty())) {
                pass.glass = false;
                pass.glow = false;
                pass.keepDark = true;
                pass.branch = null;
                pass.dentDepth = 0;
                draw(builder, pass, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(dark)), partial, light);
            }
            JSONVehicle.Lamps lamps = definition.lamps;
            if (lamps != null && !lamps.glow.isEmpty() && !(vehicle.lampBroken(EntityVehicleF_Physics.TAILLIGHT_LEFT) && vehicle.lampBroken(EntityVehicleF_Physics.TAILLIGHT_RIGHT))) {
                boolean braking = vehicle.flag(EntityVehicleF_Physics.BRAKING);
                pass.brakeGlow = braking ? 1 : 0;
                pass.tailGlow = braking || vehicle.flag(EntityVehicleF_Physics.LIGHTS) ? 1 : 0;
                ResourceLocation mask = pass.tailGlow > 0 ? LampGlow.mask(texture) : null;
                if (mask != null) {
                    pass.glass = false;
                    pass.keepDark = false;
                    pass.glow = true;
                    pass.branch = glowBranch(definition, animator.model);
                    pass.dentDepth = 0;
                    draw(builder, pass, pose, buffers.getBuffer(RenderType.eyes(mask)), partial, light);
                }
            }
            if (lamps != null && !lamps.sequential.isEmpty()) sequential(builder, pass, pose, buffers, texture, animator.model, partial, light);
            PaintJob.Area highlight = PaintGuide.highlight(vehicle);
            if (highlight != null) {
                pass.glass = false;
                pass.glow = false;
                pass.keepDark = false;
                pass.branch = null;
                pass.dentDepth = 0;
                pass.highlight = highlight;
                draw(builder, pass, pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), partial, light);
                pass.highlight = null;
            }
            pose.popPose();
        }
        super.render(builder, yaw, partial, pose, buffers, light);
    }

    private void sequential(BuilderVehicle builder, Pass pass, PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, ModelGeometry model, float partial, int light) {
        EntityVehicleF_Physics vehicle = pass.vehicle;
        boolean left = pass.flash || vehicle.flag(EntityVehicleF_Physics.TURN_LEFT);
        boolean right = pass.flash || vehicle.flag(EntityVehicleF_Physics.TURN_RIGHT);
        if (!left && !right) return;
        float share = pass.flash ? 1 : sweepShare(builder.level().getGameTime(), partial);
        if (share < 0) return;
        pass.glass = false;
        pass.glow = false;
        pass.keepDark = false;
        pass.dentDepth = 0;
        for (JSONVehicle.Sequential strip : pass.definition.lamps.sequential) {
            boolean rear = strip.rear();
            pass.sweepLeft = left && !vehicle.lampBroken(rear ? EntityVehicleF_Physics.TAILLIGHT_LEFT : EntityVehicleF_Physics.HEADLIGHT_LEFT);
            pass.sweepRight = right && !vehicle.lampBroken(rear ? EntityVehicleF_Physics.TAILLIGHT_RIGHT : EntityVehicleF_Physics.HEADLIGHT_RIGHT);
            if (!pass.sweepLeft && !pass.sweepRight) continue;
            ResourceLocation lens = LampGlow.lens(texture, strip.white());
            if (lens == null) continue;
            pass.sequential = strip;
            pass.sweepLit = Math.min(strip.segments, 1 + (int) (share * strip.segments));
            pass.branch = sequentialBranch(strip, model);
            draw(builder, pass, pose, buffers.getBuffer(VehicleRenderTypes.lamp(lens)), partial, light);
        }
        pass.sequential = null;
    }

    private static float sweepShare(long time, float partial) {
        float phase = (time % BLINK_TICKS + partial) / (BLINK_TICKS / 2f);
        if (phase >= 1) return -1;
        return Math.min(1, phase / SWEEP_SHARE);
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
                    PLATE_BRANCHES.clear();
                    SEQUENTIAL_BRANCHES.clear();
                }
                BROKEN_MODELS.clear();
                LampGlow.clear();
                PaintMask.clear();
                PlateTextures.clear();
                PropModel.reloadAll();
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
        pass.rootInverse = pass.vehicle.painting() || pass.sequential != null ? new Matrix4f(pose.last().pose()).invert() : null;
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

    private static synchronized Set<String> sequentialBranch(JSONVehicle.Sequential strip, ModelGeometry model) {
        return SEQUENTIAL_BRANCHES.computeIfAbsent(strip, key -> branch(model, strip.bones));
    }

    private static synchronized Set<String> plateBranch(JSONVehicle definition, ModelGeometry model) {
        return PLATE_BRANCHES.computeIfAbsent(definition.modelLocation, key -> branch(model, List.copyOf(definition.plateFaces.keySet())));
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
            if (glow.bone.equals(bone)) return glow.brakeOnly() || pass.brakeGlow > 0 ? pass.brakeGlow : pass.tailGlow * (float) glow.tail;
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
        boolean wasPainted = pass.painted;
        boolean wasSpraying = pass.spraying;
        boolean wasSprayInterior = pass.sprayInterior;
        int sprayFrom = pass.sprayFrom;
        int sprayTo = pass.sprayTo;
        float baseRed = pass.baseRed;
        float baseGreen = pass.baseGreen;
        float baseBlue = pass.baseBlue;
        if (definition.paintBones.contains(name) || definition.interiorPaintBones.contains(name)) {
            if (pass.painted) {
                red = pass.baseRed;
                green = pass.baseGreen;
                blue = pass.baseBlue;
            } else {
                pass.painted = true;
                pass.baseRed = red;
                pass.baseGreen = green;
                pass.baseBlue = blue;
            }
            int color = vehicle.partColor(name);
            red *= paint(color, 16);
            green *= paint(color, 8);
            blue *= paint(color, 0);
            boolean interior = definition.interiorPaintBones.contains(name);
            int target = interior ? definition.interiorShade(name, vehicle.paintInterior()) : vehicle.paintBody();
            pass.spraying = pass.rootInverse != null && target >= 0;
            pass.sprayInterior = interior;
            pass.sprayFrom = color;
            pass.sprayTo = target;
        } else if (pass.painted && definition.paintKeepBones.contains(name)) {
            red = pass.baseRed;
            green = pass.baseGreen;
            blue = pass.baseBlue;
            pass.painted = false;
            pass.spraying = false;
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
        pass.painted = wasPainted;
        pass.spraying = wasSpraying;
        pass.sprayInterior = wasSprayInterior;
        pass.sprayFrom = sprayFrom;
        pass.sprayTo = sprayTo;
        pass.baseRed = baseRed;
        pass.baseGreen = baseGreen;
        pass.baseBlue = baseBlue;
    }

    static float paint(int color, int shift) {
        float luminance = (.3f * (color >> 16 & 255) + .59f * (color >> 8 & 255) + .11f * (color & 255)) / 255;
        float lift = PAINT_LIFT * (1 - luminance);
        return (color >> shift & 255) / 255f * (1 - lift) + lift;
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
        if (pass.plates) {
            ModelGeometry.Face face = definition.plateFaces.get(name);
            if (face == null || hidden) return;
            pass.faces = null;
            for (ModelGeometry.Cube cube : bone.cubes) {
                pose.pushPose();
                if (pass.blankPlates) drawCube(pass, pose, cube, light, overlay, 1, 1, 1, 1);
                else drawPlate(pass, pose, cube, face, light);
                pose.popPose();
            }
            return;
        }
        if (pass.sequential != null) {
            if (hidden || !pass.sequential.bones.contains(name)) return;
            for (ModelGeometry.Cube cube : bone.cubes) {
                pose.pushPose();
                drawSweep(pass, pose, cube);
                pose.popPose();
            }
            return;
        }
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
        if (pass.keepDark) {
            if (!pass.painted || hidden || definition.plateFaces.containsKey(name)) return;
            for (ModelGeometry.Cube cube : bone.cubes) {
                pose.pushPose();
                drawCube(pass, pose, cube, light, overlay, pass.baseRed, pass.baseGreen, pass.baseBlue, alpha);
                pose.popPose();
            }
            return;
        }
        JSONVehicle.Lamps lamps = definition.lamps;
        if (pass.highlight != null) {
            boolean lamp = lamps != null && (name.equals(lamps.headlightBoneName()) || name.equals(lamps.taillightBoneName()));
            boolean interior = pass.highlight == PaintJob.Area.INTERIOR;
            if (hidden || !pass.spraying || pass.sprayInterior != interior || lamp || isIndicator(name, true) || isIndicator(name, false)) return;
            float glow = PaintGuide.pulse();
            pass.faces = null;
            float share = PaintJob.share(vehicle.paintCoats(), pass.highlight);
            for (ModelGeometry.Cube cube : bone.cubes) {
                if (!interior && area(pass, pose, name, cube) != pass.highlight) continue;
                PaintReveal.Front front = PaintReveal.front(vehicle, pass.highlight, share);
                pose.pushPose();
                if (front != null) drawFront(pass, pose, cube, FULL_BRIGHT, overlay, glow, front, true);
                else drawCube(pass, pose, cube, FULL_BRIGHT, overlay, highlight(pass.sprayTo, 16), highlight(pass.sprayTo, 8), highlight(pass.sprayTo, 0), glow);
                pose.popPose();
            }
            return;
        }
        boolean head = lamps != null && name.equals(lamps.headlightBoneName());
        boolean tail = lamps != null && name.equals(lamps.taillightBoneName());
        boolean[] sides = head ? lamps.headlightBone.left : tail ? lamps.taillightBone.left : null;
        int[] masks = pass.painted ? definition.paintKeepFaces.get(name) : definition.paintFaces.get(name);
        float faceRed = pass.baseRed;
        float faceGreen = pass.baseGreen;
        float faceBlue = pass.baseBlue;
        int faceLight = light;
        if (masks != null && !pass.painted) {
            int color = vehicle.partColor(name);
            faceRed = paint(color, 16);
            faceGreen = paint(color, 8);
            faceBlue = paint(color, 0);
            faceLight = pass.unlit;
        }
        if (sides != null && !hidden) {
            int left = head ? EntityVehicleF_Physics.HEADLIGHT_LEFT : EntityVehicleF_Physics.TAILLIGHT_LEFT;
            int right = head ? EntityVehicleF_Physics.HEADLIGHT_RIGHT : EntityVehicleF_Physics.TAILLIGHT_RIGHT;
            if (vehicle.lampBroken(left) || vehicle.lampBroken(right)) {
                List<ModelGeometry.Cube> cubes = bone.cubes;
                for (int i = 0; i < cubes.size(); i++) {
                    boolean broken = i < sides.length && vehicle.lampBroken(sides[i] ? left : right);
                    pass.faces = faceTint(masks, i, faceRed, faceGreen, faceBlue, faceLight);
                    pose.pushPose();
                    if (broken) drawCube(pass, pose, cubes.get(i), pass.unlit, overlay, .24f, .22f, .21f, alpha);
                    else drawCube(pass, pose, cubes.get(i), light, overlay, red, green, blue, alpha);
                    pose.popPose();
                }
                pass.faces = null;
                return;
            }
        }
        if (hidden || definition.plateFaces.containsKey(name)) return;
        boolean spray = pass.spraying && !pass.glass && sides == null && !isIndicator(name, true) && !isIndicator(name, false);
        List<ModelGeometry.Cube> cubes = bone.cubes;
        for (int i = 0; i < cubes.size(); i++) {
            pass.faces = faceTint(masks, i, faceRed, faceGreen, faceBlue, faceLight);
            pose.pushPose();
            if (spray) drawSprayed(pass, pose, name, cubes.get(i), light, overlay, alpha);
            else drawCube(pass, pose, cubes.get(i), light, overlay, red, green, blue, alpha);
            pose.popPose();
        }
        pass.faces = null;
    }

    private static void drawPlate(Pass pass, PoseStack pose, ModelGeometry.Cube cube, ModelGeometry.Face face, int light) {
        cubeTransform(pose, cube);
        Matrix3f normalPose = pose.last().normal();
        Matrix4f position = new Matrix4f(pose.last().pose());
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            boolean front = quad.face() == face;
            float minU = Float.MAX_VALUE;
            float maxU = -Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            float maxV = -Float.MAX_VALUE;
            for (ModelGeometry.Vertex vertex : quad.vertices()) {
                minU = Math.min(minU, vertex.u());
                maxU = Math.max(maxU, vertex.u());
                minV = Math.min(minV, vertex.v());
                maxV = Math.max(maxV, vertex.v());
            }
            float spanU = Math.max(1e-6f, maxU - minU);
            float spanV = Math.max(1e-6f, maxV - minV);
            Vector3f normal = normalPose.transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
            fixInvertedFlatCube(cube, normal);
            for (ModelGeometry.Vertex vertex : quad.vertices()) {
                Vector4f at = position.transform(new Vector4f(vertex.x(), vertex.y(), vertex.z(), 1.0F));
                float u = front ? (vertex.u() - minU) / spanU : PLATE_RIM_U;
                float v = front ? (vertex.v() - minV) / spanV : PLATE_RIM_V;
                pass.buffer.vertex(at.x(), at.y(), at.z(), 1, 1, 1, 1, u, v, OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
            }
        }
    }

    private void drawSprayed(Pass pass, PoseStack pose, String bone, ModelGeometry.Cube cube, int light, int overlay, float alpha) {
        int coats = pass.vehicle.paintCoats();
        PaintJob.Area part = pass.sprayInterior ? PaintJob.Area.INTERIOR : area(pass, pose, bone, cube);
        float share = PaintJob.share(coats, part);
        PaintReveal.Front front = pass.dentMesh != null && pass.dentDepth > 0 ? null : PaintReveal.front(pass.vehicle, part, share);
        if (front != null) {
            drawFront(pass, pose, cube, light, overlay, alpha, front, false);
            return;
        }
        drawCube(pass, pose, cube, light, overlay, pass.baseRed * sprayed(pass, share, 16), pass.baseGreen * sprayed(pass, share, 8),
                pass.baseBlue * sprayed(pass, share, 0), alpha);
    }

    private void drawFront(Pass pass, PoseStack pose, ModelGeometry.Cube cube, int light, int overlay, float alpha, PaintReveal.Front front, boolean glow) {
        cubeTransform(pose, cube);
        Matrix3f normalPose = pose.last().normal();
        Matrix4f position = new Matrix4f(pose.last().pose());
        VertexConsumer buffer = pass.buffer;
        FaceTint faces = pass.faces;
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            Vector3f normal = normalPose.transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
            fixInvertedFlatCube(cube, normal);
            boolean own = faces != null && faces.covers(quad.face());
            ModelGeometry.Vertex[] corners = quad.vertices();
            int columns = own ? 1 : cells(corners[0], corners[1]);
            int rows = own ? 1 : cells(corners[1], corners[2]);
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    for (int corner = 0; corner < 4; corner++) {
                        float s = (column + (corner == 1 || corner == 2 ? 1 : 0)) / (float) columns;
                        float t = (row + (corner >= 2 ? 1 : 0)) / (float) rows;
                        float x = bilinear(corners[0].x(), corners[1].x(), corners[2].x(), corners[3].x(), s, t);
                        float y = bilinear(corners[0].y(), corners[1].y(), corners[2].y(), corners[3].y(), s, t);
                        float z = bilinear(corners[0].z(), corners[1].z(), corners[2].z(), corners[3].z(), s, t);
                        float u = bilinear(corners[0].u(), corners[1].u(), corners[2].u(), corners[3].u(), s, t);
                        float v = bilinear(corners[0].v(), corners[1].v(), corners[2].v(), corners[3].v(), s, t);
                        Vector4f at = position.transform(new Vector4f(x, y, z, 1.0F));
                        if (own) {
                            buffer.vertex(at.x(), at.y(), at.z(), faces.red(), faces.green(), faces.blue(), alpha, u, v, overlay, faces.light(), normal.x(), normal.y(), normal.z());
                            continue;
                        }
                        Vector4f root = pass.rootInverse.transform(new Vector4f(at));
                        float covered = front.at(root.x(), root.y(), root.z());
                        if (glow) {
                            float band = 4 * covered * (1 - covered) * FRONT_GLOW;
                            buffer.vertex(at.x(), at.y(), at.z(), Mth.lerp(band, highlight(pass.sprayTo, 16), 1), Mth.lerp(band, highlight(pass.sprayTo, 8), 1), Mth.lerp(band, highlight(pass.sprayTo, 0), 1),
                                    alpha * (1 - covered) + band, u, v, overlay, light, normal.x(), normal.y(), normal.z());
                        } else {
                            buffer.vertex(at.x(), at.y(), at.z(), pass.baseRed * sprayed(pass, covered, 16), pass.baseGreen * sprayed(pass, covered, 8), pass.baseBlue * sprayed(pass, covered, 0),
                                    alpha, u, v, overlay, light, normal.x(), normal.y(), normal.z());
                        }
                    }
                }
            }
        }
    }

    private static void drawSweep(Pass pass, PoseStack pose, ModelGeometry.Cube cube) {
        cubeTransform(pose, cube);
        Matrix3f normalPose = pose.last().normal();
        Matrix4f position = new Matrix4f(pose.last().pose());
        JSONVehicle.Sequential strip = pass.sequential;
        Vector4f[] at = new Vector4f[4];
        Vector4f[] root = new Vector4f[4];
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            ModelGeometry.Vertex[] corners = quad.vertices();
            for (int i = 0; i < 4; i++) {
                at[i] = position.transform(new Vector4f(corners[i].x(), corners[i].y(), corners[i].z(), 1.0F));
                root[i] = pass.rootInverse.transform(new Vector4f(at[i]));
            }
            if (!touches(strip, root)) continue;
            Vector3f normal = normalPose.transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
            fixInvertedFlatCube(cube, normal);
            float liftX = normal.x() * SWEEP_LIFT;
            float liftY = normal.y() * SWEEP_LIFT;
            float liftZ = normal.z() * SWEEP_LIFT;
            int columns = sweepCells(root[0], root[1]);
            int rows = sweepCells(root[1], root[2]);
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    float s = (column + .5f) / columns;
                    float t = (row + .5f) / rows;
                    float x = -bilinear(root[0].x(), root[1].x(), root[2].x(), root[3].x(), s, t);
                    float y = bilinear(root[0].y(), root[1].y(), root[2].y(), root[3].y(), s, t);
                    float z = -bilinear(root[0].z(), root[1].z(), root[2].z(), root[3].z(), s, t);
                    int segment = strip.segment(x, y, z);
                    if (segment < 0 || segment >= pass.sweepLit || !(x > 0 ? pass.sweepLeft : pass.sweepRight)) continue;
                    for (int corner = 0; corner < 4; corner++) {
                        float cs = (column + (corner == 1 || corner == 2 ? 1 : 0)) / (float) columns;
                        float ct = (row + (corner >= 2 ? 1 : 0)) / (float) rows;
                        pass.buffer.vertex(bilinear(at[0].x(), at[1].x(), at[2].x(), at[3].x(), cs, ct) + liftX, bilinear(at[0].y(), at[1].y(), at[2].y(), at[3].y(), cs, ct) + liftY,
                                bilinear(at[0].z(), at[1].z(), at[2].z(), at[3].z(), cs, ct) + liftZ, AMBER_RED, AMBER_GREEN, AMBER_BLUE, 1,
                                bilinear(corners[0].u(), corners[1].u(), corners[2].u(), corners[3].u(), cs, ct), bilinear(corners[0].v(), corners[1].v(), corners[2].v(), corners[3].v(), cs, ct),
                                OverlayTexture.NO_OVERLAY, FULL_BRIGHT, normal.x(), normal.y(), normal.z());
                    }
                }
            }
        }
    }

    private static boolean touches(JSONVehicle.Sequential strip, Vector4f[] root) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (Vector4f corner : root) {
            minX = Math.min(minX, -corner.x());
            maxX = Math.max(maxX, -corner.x());
            minY = Math.min(minY, corner.y());
            maxY = Math.max(maxY, corner.y());
            minZ = Math.min(minZ, -corner.z());
            maxZ = Math.max(maxZ, -corner.z());
        }
        boolean side = maxX >= strip.min.x && minX <= strip.max.x || minX <= -strip.min.x && maxX >= -strip.max.x;
        return side && maxY >= strip.min.y && minY <= strip.max.y && maxZ >= strip.min.z && minZ <= strip.max.z;
    }

    private static int sweepCells(Vector4f a, Vector4f b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return Mth.clamp(Mth.ceil(Mth.sqrt(dx * dx + dy * dy + dz * dz) / SWEEP_CELL), 1, SWEEP_MAX_CELLS);
    }

    private static int cells(ModelGeometry.Vertex a, ModelGeometry.Vertex b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return Mth.clamp(Mth.ceil(Mth.sqrt(dx * dx + dy * dy + dz * dz) / FRONT_CELL), 1, FRONT_MAX_CELLS);
    }

    private static float bilinear(float a, float b, float c, float d, float s, float t) {
        return Mth.lerp(t, Mth.lerp(s, a, b), Mth.lerp(s, d, c));
    }

    private static float highlight(int color, int shift) {
        return 1 - (1 - paint(color, shift)) * HIGHLIGHT_KEEP;
    }

    private static float sprayed(Pass pass, float share, int shift) {
        return paint(pass.sprayFrom, shift) * (1 - share) + paint(pass.sprayTo, shift) * share;
    }

    private static PaintJob.Area area(Pass pass, PoseStack pose, String bone, ModelGeometry.Cube cube) {
        PaintJob.Area part = pass.definition.paintAreaBones.get(bone);
        if (part != null) return part;
        float x = 0;
        float y = 0;
        float z = 0;
        int count = 0;
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            for (ModelGeometry.Vertex vertex : quad.vertices()) {
                x += vertex.x();
                y += vertex.y();
                z += vertex.z();
                count++;
            }
        }
        if (count == 0) return PaintJob.Area.TOP;
        pose.pushPose();
        cubeTransform(pose, cube);
        Vector4f at = pose.last().pose().transform(new Vector4f(x / count, y / count, z / count, 1));
        pose.popPose();
        pass.rootInverse.transform(at);
        return PaintJob.Area.of(pass.definition.damage.side(new Point3D(-at.x(), at.y(), -at.z())));
    }

    private static FaceTint faceTint(int[] masks, int cube, float red, float green, float blue, int light) {
        if (masks == null || cube >= masks.length || masks[cube] == 0) return null;
        return new FaceTint(masks[cube], red, green, blue, light);
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
            pass.dentMesh.renderCube(pose, cube, pass.buffer, light, overlay, red, green, blue, alpha, pass.faces);
            return;
        }
        cubeTransform(pose, cube);
        Matrix3f normalPose = pose.last().normal();
        Matrix4f position = new Matrix4f(pose.last().pose());
        VertexConsumer buffer = pass.buffer;
        FaceTint faces = pass.faces;
        for (ModelGeometry.Quad quad : cube.quads()) {
            if (quad == null) continue;
            Vector3f normal = normalPose.transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));
            fixInvertedFlatCube(cube, normal);
            boolean own = faces != null && faces.covers(quad.face());
            float r = own ? faces.red() : red;
            float g = own ? faces.green() : green;
            float b = own ? faces.blue() : blue;
            int l = own ? faces.light() : light;
            for (ModelGeometry.Vertex vertex : quad.vertices()) {
                Vector4f at = position.transform(new Vector4f(vertex.x(), vertex.y(), vertex.z(), 1.0F));
                buffer.vertex(at.x(), at.y(), at.z(), r, g, b, alpha, vertex.u(), vertex.v(), overlay, l, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
