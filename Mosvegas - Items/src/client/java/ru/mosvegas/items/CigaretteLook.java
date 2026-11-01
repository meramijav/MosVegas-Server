package ru.mosvegas.items;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/**
 * Visible state of a cigarette: remaining paper and ember glow. Derived from the stack
 * and the synced use time, so every client shows the same burning cigarette.
 */
public record CigaretteLook(float paper, boolean lit, float glow) {
    public static final float BUTT_PAPER = 0.04f;
    private static final float SMOULDER = 0.3f;
    private static float litterGlow = -1;

    public static CigaretteLook of(ItemStack stack, float tickDelta) {
        var item = (MosVegasConsumableItem) stack.getItem();
        if (MosVegasConsumableItem.isSpent(stack)) {
            float glow = Math.max(0, litterGlow);
            return new CigaretteLook(BUTT_PAPER, glow > 0, glow);
        }
        var player = MosVegasItemsClient.activePlayer(stack);
        int uses = item.definition().uses();
        int remaining = item.remainingUses(stack);
        if (player == null) return new CigaretteLook(ConsumptionPose.remainingFraction(remaining, uses, 0), remaining < uses,
                remaining < uses ? SMOULDER : 0);
        float total = item.getMaxUseTime(stack);
        float elapsed = player.getItemUseTime() + tickDelta;
        float fraction = MathHelper.clamp(elapsed / total, 0, 1);
        boolean consumes = !player.getAbilities().creativeMode || item.definition().consumeInCreative();
        // The paper burns while drawing, not while the hand is raised or lowered.
        float burned = consumes ? MathHelper.clamp((fraction - ConsumptionTimeline.INHALE_START)
                / (ConsumptionTimeline.INHALE_STOP - ConsumptionTimeline.INHALE_START), 0, 1) : 0;
        boolean lit = remaining < uses || fraction >= ConsumptionTimeline.INHALE_START;
        return new CigaretteLook(ConsumptionPose.remainingFraction(remaining, uses, burned), lit,
                lit ? SMOULDER + (1 - SMOULDER) * ConsumptionTimeline.inhale(elapsed, total) : 0);
    }

    /** Lets the litter renderer fade a dropped butt's ember for one draw. */
    public static void withLitterGlow(float glow, Runnable render) {
        litterGlow = glow;
        try {
            render.run();
        } finally {
            litterGlow = -1;
        }
    }
}
