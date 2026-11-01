package ru.mosvegas.items;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MosVegasConsumableItem extends Item implements GeoItem {
    public static final String USES_TAG = "MosVegasUses";
    public static final String OPENED_TAG = "MosVegasOpened";
    public static final String SPENT_TAG = "MosVegasSpent";
    private static final String UNIT_TAG = "MosVegasUnit";
    // Client entrypoint installs these without loading renderer classes on dedicated servers.
    public static Function<MosVegasConsumableItem, Object> rendererFactory = item -> null;
    public static Predicate<ItemStack> isBeingUsed = stack -> false;
    private final ConsumableDefinition definition;
    private final UseAction useAction;
    private final SoundEvent useSound;
    private final SoundEvent startSound;
    private final SoundEvent finishSound;
    private final String[] descriptionLines;
    private final RawAnimation idleAnimation;
    private final RawAnimation useAnimation;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderer = GeoItem.makeRenderer(this);
    private RegistryReferences registryReferences;

    public MosVegasConsumableItem(Settings settings, ConsumableDefinition definition) {
        super(settings);
        this.definition = definition;
        this.useAction = switch (definition.useAction()) {
            case "drink" -> UseAction.DRINK;
            case "eat" -> UseAction.EAT;
            default -> UseAction.NONE;
        };
        this.useSound = soundEvent(definition.sound());
        this.startSound = soundEvent(definition.startSound());
        this.finishSound = soundEvent(definition.finishSound());
        this.descriptionLines = definition.description().isEmpty() ? new String[0] : definition.description().split("\n");
        this.idleAnimation = loopAnimation(definition.idleAnimation());
        this.useAnimation = loopAnimation(definition.useAnimation());
    }

    public ConsumableDefinition definition() { return definition; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // An empty can is only thrown away with the drop key.
        if (isSpent(stack)) return TypedActionResult.pass(stack);
        if (user.getItemCooldownManager().isCoolingDown(this)
                || (!isOpening(stack) && !definition.alwaysEdible() && !user.canConsume(false))) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /** A closed can is opened by a short use of its own before the first sip. */
    public boolean isOpening(ItemStack stack) {
        return startSound != null && !isOpened(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return isOpening(stack) ? definition.openTime() : definition.useTime(); }

    @Override
    public UseAction getUseAction(ItemStack stack) { return isOpening(stack) ? UseAction.NONE : useAction; }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        // Opening, sips and puffs change NBT; the hand must not dip as if re-equipping.
        return false;
    }

    @Override
    public Text getName(ItemStack stack) {
        return isSpent(stack) ? Text.translatable("item.mosvegas_items.empty", Text.translatable(definition.name()))
                : Text.translatable(definition.name());
    }
    @Override
    public Text getName() { return Text.translatable(definition.name()); }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server)) return;
        int total = getMaxUseTime(stack);
        int elapsed = total - remainingUseTicks;
        if (isOpening(stack)) {
            // The tab pops while the other hand pulls it.
            if (elapsed == ConsumptionTimeline.tick(ConsumptionTimeline.TAB_POP, total)) playSound(server, user, startSound);
            return;
        }
        ConsumableDefinition.ParticleDefinition p = definition.particles();
        if (definition.useAction().equals("smoke")) {
            // Crackle while drawing; breathe the smoke out after lowering the hand.
            int inhale = ConsumptionTimeline.tick(ConsumptionTimeline.INHALE_START, total);
            if (elapsed >= inhale && elapsed <= ConsumptionTimeline.tick(ConsumptionTimeline.INHALE_END, total)
                    && (elapsed - inhale) % definition.soundInterval() == 0) playSound(server, user, useSound);
            if (!p.id().isEmpty() && ConsumptionTimeline.exhaling(elapsed, total) && elapsed % p.interval() == 0)
                exhale(server, user, p);
            return;
        }
        // Drinking and eating are heard once the item has reached the mouth.
        if (elapsed > 0 && elapsed % definition.soundInterval() == 0) playSound(server, user, useSound);
        if (!p.id().isEmpty() && elapsed % p.interval() == 0) {
            DefaultParticleType particle = registryReferences().particle();
            Vec3d pos = user.getEyePos().add(user.getRotationVector().multiply(0.35)).add(0, -0.12, 0);
            server.spawnParticles(particle, pos.x, pos.y, pos.z, p.count(), p.spread(), p.spread(), p.spread(), p.speed());
        }
    }

    /** Smoke leaving the mouth along the view direction; count 0 makes the packet carry each velocity. */
    private void exhale(ServerWorld world, LivingEntity user, ConsumableDefinition.ParticleDefinition p) {
        DefaultParticleType particle = registryReferences().particle();
        Vec3d look = user.getRotationVector();
        Vec3d mouth = user.getEyePos().add(look.multiply(0.28)).add(0, -0.14, 0);
        for (int i = 0; i < p.count(); i++) {
            var random = world.random;
            Vec3d velocity = look.multiply(p.speed() * (0.7 + random.nextFloat() * 0.6))
                    .add(random.nextGaussian() * 0.012, -0.008 + random.nextGaussian() * 0.006, random.nextGaussian() * 0.012);
            world.spawnParticles(particle, mouth.x + random.nextGaussian() * p.spread(), mouth.y + random.nextGaussian() * p.spread() * 0.5,
                    mouth.z + random.nextGaussian() * p.spread(), 0, velocity.x, velocity.y, velocity.z, 1);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        // Only the server changes hunger, effects and charges.
        if (world.isClient) return stack;
        if (isOpening(stack)) return open(stack, user);
        if (user instanceof ServerPlayerEntity player) Criteria.CONSUME_ITEM.trigger(player, stack);
        if (user instanceof PlayerEntity player) {
            player.getHungerManager().add(definition.hunger(), definition.saturation());
            player.incrementStat(Stats.USED.getOrCreateStat(this));
            if (definition.cooldown() > 0) player.getItemCooldownManager().set(this, definition.cooldown());
        }
        RegistryReferences references = registryReferences();
        for (ResolvedEffect resolved : references.effects()) {
            ConsumableDefinition.EffectDefinition e = resolved.definition();
            if (world.random.nextFloat() >= e.chance()) continue;
            StatusEffect effect = resolved.effect();
            if (effect.isInstant()) effect.applyInstantEffect(user, user, user, e.amplifier(), 1.0);
            else user.addStatusEffect(new StatusEffectInstance(effect, e.duration(), e.amplifier(), e.ambient(), e.showParticles(), e.showIcon()));
        }
        playSound(world, user, finishSound);
        user.emitGameEvent(GameEvent.EAT);
        if (user instanceof PlayerEntity player && player.getAbilities().creativeMode && !definition.consumeInCreative())
            return stack;

        int remaining = remainingUses(stack) - 1;
        if (remaining > 0) {
            // A partially consumed unit is separated from untouched units to prevent charge duplication.
            ItemStack partial = stack.copyWithCount(1);
            NbtCompound nbt = partial.getOrCreateNbt();
            nbt.putInt(USES_TAG, remaining);
            if (!nbt.containsUuid(UNIT_TAG)) nbt.putUuid(UNIT_TAG, UUID.randomUUID());
            stack.decrement(1);
            if (!stack.isEmpty()) {
                // Free the active slot before insertion; otherwise the remainder merges into
                // the original stack that LivingEntity is about to replace with partial.
                if (user instanceof PlayerEntity player) player.setStackInHand(player.getActiveHand(), partial);
                giveOrDrop(user, stack.copy());
            }
            return partial;
        }
        ItemStack used = stack.copyWithCount(1);
        stack.decrement(1);
        List<ItemStack> leftovers = new ArrayList<>(2);
        switch (definition.discard()) {
            // The empty can stays in the hand until the player throws it with the drop key.
            case "throw" -> leftovers.add(empty());
            // The butt is flicked to the floor right away.
            case "drop" -> {
                if (world instanceof ServerWorld server) LitterEntity.flick(server, user, user.getActiveHand(), used);
                user.swingHand(user.getActiveHand(), true);
            }
            default -> {}
        }
        if (references.remainder() != null) leftovers.add(new ItemStack(references.remainder()));
        for (ItemStack leftover : leftovers) {
            if (stack.isEmpty()) stack = leftover;
            else giveOrDrop(user, leftover);
        }
        return stack;
    }

    /** Empty containers carry no unit identity, so they stack with each other. */
    public ItemStack empty() {
        ItemStack empty = new ItemStack(this);
        markOpened(empty);
        empty.getNbt().remove(UNIT_TAG);
        markSpent(empty);
        return empty;
    }

    private static void giveOrDrop(LivingEntity user, ItemStack extra) {
        if (user instanceof PlayerEntity player) {
            if (!insertPreservingExtra(player, extra)) player.dropItem(extra, false);
        } else user.dropStack(extra);
    }

    private static boolean insertPreservingExtra(PlayerEntity player, ItemStack extra) {
        var inventory = player.getInventory();
        if (!player.getAbilities().creativeMode) return inventory.insertStack(extra);
        // Vanilla inventory insertion deletes overflow in creative. Roleplay items
        // still have finite portions there, so only insert chunks that fit.
        while (!extra.isEmpty()) {
            int slot = inventory.getOccupiedSlotWithRoomForStack(extra);
            if (slot < 0) slot = inventory.getEmptySlot();
            if (slot < 0) return false;
            ItemStack target = inventory.getStack(slot);
            int capacity = target.isEmpty() ? extra.getMaxCount() : target.getMaxCount() - target.getCount();
            inventory.insertStack(slot, extra.split(Math.min(capacity, extra.getCount())));
        }
        return true;
    }

    /** Opening changes one physical unit; untouched units return to the inventory unopened. */
    private ItemStack open(ItemStack stack, LivingEntity user) {
        if (user instanceof PlayerEntity player && (!player.getAbilities().creativeMode || definition.consumeInCreative())
                && stack.getCount() > 1) {
            ItemStack opened = stack.copyWithCount(1);
            markOpened(opened);
            stack.decrement(1);
            // Free the active slot first so the returned units cannot merge into it.
            player.setStackInHand(player.getActiveHand(), opened);
            giveOrDrop(player, stack.copy());
            return opened;
        }
        markOpened(stack);
        return stack;
    }

    public boolean isOpened(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(OPENED_TAG);
    }

    /** Marks the copy shown by a thrown can or dropped butt. */
    public static void markSpent(ItemStack stack) {
        stack.getOrCreateNbt().putBoolean(SPENT_TAG, true);
    }

    public static boolean isSpent(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(SPENT_TAG);
    }

    private static void markOpened(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(OPENED_TAG, true);
        if (!nbt.containsUuid(UNIT_TAG)) nbt.putUuid(UNIT_TAG, UUID.randomUUID());
    }

    private void playSound(World world, LivingEntity user, SoundEvent sound) {
        if (sound == null) return;
        world.playSound(null, user.getX(), user.getY(), user.getZ(), sound,
                SoundCategory.PLAYERS, definition.volume(), definition.pitch());
    }

    private static SoundEvent soundEvent(String id) {
        return id.isEmpty() ? null : SoundEvent.of(new Identifier(id));
    }

    private static RawAnimation loopAnimation(String name) {
        return name.isEmpty() ? null : RawAnimation.begin().thenLoop(name);
    }

    // Registries are complete at server startup; resolve once rather than during every use tick.
    void resolveRegistryReferences() {
        List<ResolvedEffect> effects = new ArrayList<>(definition.effects().size());
        for (ConsumableDefinition.EffectDefinition effect : definition.effects()) {
            Identifier id = new Identifier(effect.id());
            if (!Registries.STATUS_EFFECT.containsId(id))
                throw new IllegalStateException(definition.id() + ": unknown effect " + effect.id());
            effects.add(new ResolvedEffect(effect, Registries.STATUS_EFFECT.get(id)));
        }
        DefaultParticleType particle = null;
        String particleId = definition.particles().id();
        if (!particleId.isEmpty()) {
            if (!(Registries.PARTICLE_TYPE.get(new Identifier(particleId)) instanceof DefaultParticleType simple))
                throw new IllegalStateException(definition.id() + ": particle must be a registered simple particle: " + particleId);
            particle = simple;
        }
        Item remainder = null;
        if (!definition.remainder().isEmpty()) {
            Identifier id = new Identifier(definition.remainder());
            if (!Registries.ITEM.containsId(id))
                throw new IllegalStateException(definition.id() + ": unknown remainder " + definition.remainder());
            remainder = Registries.ITEM.get(id);
        }
        registryReferences = new RegistryReferences(List.copyOf(effects), particle, remainder);
    }

    private RegistryReferences registryReferences() {
        if (registryReferences == null) resolveRegistryReferences();
        return registryReferences;
    }

    private record ResolvedEffect(ConsumableDefinition.EffectDefinition definition, StatusEffect effect) {}
    private record RegistryReferences(List<ResolvedEffect> effects, DefaultParticleType particle, Item remainder) {}

    public int remainingUses(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(USES_TAG, NbtElement.NUMBER_TYPE))
            return Math.max(1, Math.min(definition.uses(), nbt.getInt(USES_TAG)));
        return definition.uses();
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        // Untouched items show no bar; it appears once the first portion is used.
        return definition.uses() > 1 && !isSpent(stack) && remainingUses(stack) < definition.uses();
    }
    @Override
    public int getItemBarStep(ItemStack stack) { return Math.round(13f * remainingUses(stack) / definition.uses()); }
    @Override
    public int getItemBarColor(ItemStack stack) {
        return MathHelper.hsvToRgb(remainingUses(stack) / (float) definition.uses() / 3f, 1f, 1f);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        if (isSpent(stack)) {
            tooltip.add(Text.translatable("tooltip.mosvegas_items.throw_empty", Text.keybind("key.drop")).formatted(Formatting.GRAY));
            return;
        }
        for (String line : descriptionLines) tooltip.add(Text.translatable(line).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.mosvegas_items.category",
                Text.translatable("category.mosvegas_items." + definition.category())).formatted(Formatting.DARK_GRAY));
        if (definition.uses() > 1) tooltip.add(Text.translatable("tooltip.mosvegas_items.uses", remainingUses(stack), definition.uses()).formatted(Formatting.GOLD));
        if (definition.cooldown() > 0) tooltip.add(Text.translatable("tooltip.mosvegas_items.cooldown", definition.cooldown() / 20f).formatted(Formatting.DARK_GRAY));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "consume", 3, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            RawAnimation animation = stack != null && isBeingUsed.test(stack) ? useAnimation : idleAnimation;
            return animation == null ? PlayState.STOP : state.setAndContinue(animation);
        }));
    }

    @Override
    public boolean isPerspectiveAware() { return true; }
    @Override
    public void createRenderer(Consumer<Object> consumer) { consumer.accept(rendererFactory.apply(this)); }
    @Override
    public Supplier<Object> getRenderProvider() { return renderer; }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

