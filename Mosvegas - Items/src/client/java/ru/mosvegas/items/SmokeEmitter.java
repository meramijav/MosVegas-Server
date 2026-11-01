package ru.mosvegas.items;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Smoke rises from where the ember is actually drawn, in any perspective and for every
 * player in view. Renderers record the ember; the world tick emits at a steady rate.
 */
public final class SmokeEmitter {
    private record Ember(Vec3d position, float glow, long tick) {}
    private static final Map<Object, Ember> EMBERS = new WeakHashMap<>();

    private SmokeEmitter() {}

    public static void fromHeldItem(LivingEntity entity, ItemStack stack, MatrixStack.Entry entry, float scale, boolean firstPerson) {
        if (!(stack.getItem() instanceof MosVegasConsumableItem item) || !item.definition().useAction().equals("smoke")) return;
        float top = 7, glow = 0.5f;
        if (item.definition().model().equals(ConsumableItemRenderer.CIGARETTE_MODEL)) {
            CigaretteLook look = CigaretteLook.of(stack, MinecraftClient.getInstance().getTickDelta());
            if (!look.lit()) return;
            top = ConsumptionPose.emberTop(look.paper());
            glow = look.glow();
        }
        record(entity, entry, HeldConsumableRenderer.scaled(new Vector3f(0, top, 0), scale), firstPerson, glow);
    }

    /** {@code local} is a point in the current render frame; render frames are camera-relative and rotated. */
    public static void record(Object source, MatrixStack.Entry entry, Vector3f local, boolean firstPerson, float glow) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Vector3f view = entry.getPositionMatrix().transformPosition(new Vector3f(local));
        if (firstPerson) {
            // Hands use a fixed 70 degree field of view; find the world point drawn at the same spot.
            float k = (float) (Math.tan(Math.toRadians(client.options.getFov().getValue() / 2.0)) / Math.tan(Math.toRadians(35)));
            view.mul(k, k, 1);
        }
        Vector3f offset = RenderSystem.getInverseViewRotationMatrix().transform(view);
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        EMBERS.put(source, new Ember(camera.add(offset.x, offset.y, offset.z), glow, client.world.getTime()));
    }

    public static void tick(ClientWorld world) {
        long now = world.getTime();
        EMBERS.values().removeIf(ember -> now - ember.tick > 2);
        var random = world.random;
        for (Ember ember : EMBERS.values()) {
            // A drawing smoker pulls the smoke in; a resting cigarette smokes steadily.
            if (random.nextFloat() > 0.65f - 0.35f * Math.max(0, ember.glow - 0.3f)) continue;
            Vec3d p = ember.position;
            world.addParticle(MosVegasParticles.CIGARETTE_SMOKE, p.x, p.y + 0.01, p.z,
                    (random.nextFloat() - 0.5f) * 0.004, 0.006 + random.nextFloat() * 0.006, (random.nextFloat() - 0.5f) * 0.004);
        }
    }
}
