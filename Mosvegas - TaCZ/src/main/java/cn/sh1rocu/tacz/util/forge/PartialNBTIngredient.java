package cn.sh1rocu.tacz.util.forge;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PartialNBTIngredient implements CustomIngredient {
    private final Set<Item> items;
    private final CompoundTag nbt;
    private final NbtPredicate predicate;

    protected PartialNBTIngredient(Set<Item> items, CompoundTag nbt) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a PartialNBTIngredient with no items");
        }
        this.items = Collections.unmodifiableSet(items);
        this.nbt = nbt;
        this.predicate = new NbtPredicate(nbt);
    }

    /**
     * Creates a new ingredient matching any item from the list, containing the given NBT
     */
    public static PartialNBTIngredient of(CompoundTag nbt, ItemLike... items) {
        return new PartialNBTIngredient(Arrays.stream(items).map(ItemLike::asItem).collect(Collectors.toSet()), nbt);
    }

    /**
     * Creates a new ingredient matching the given item, containing the given NBT
     */
    public static PartialNBTIngredient of(ItemLike item, CompoundTag nbt) {
        return new PartialNBTIngredient(Set.of(item.asItem()), nbt);
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null)
            return false;
        return items.contains(input.getItem()) && predicate.matches(input.getTag());
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return items.stream().map(item -> {
            ItemStack stack = new ItemStack(item);
            // copy NBT to prevent the stack from modifying the original, as capabilities or vanilla item durability will modify the tag
            stack.setTag(nbt.copy());
            return stack;
        }).toList();
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<PartialNBTIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static final ResourceLocation ID = new ResourceLocation("forge", "partial_nbt");

    private static Item itemOf(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalStateException("Unknown item '" + id + "'");
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    private static ResourceLocation idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static class Serializer implements CustomIngredientSerializer<PartialNBTIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        private static final Codec<CompoundTag> NBT_CODEC = Codec.either(Codec.STRING, CompoundTag.CODEC).xmap(
                either -> either.map(s -> {
                    try {
                        return TagParser.parseTag(s);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse NBT string: " + s, e);
                    }
                }, Function.identity()),
                tag -> com.mojang.datafixers.util.Either.right(tag)
        );

        private static final Codec<PartialNBTIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.either(
                        ResourceLocation.CODEC.flatXmap(
                                id -> BuiltInRegistries.ITEM.containsKey(id)
                                        ? DataResult.success(id)
                                        : DataResult.error(() -> "Unknown item '" + id + "'"),
                                id -> DataResult.success(idOf(BuiltInRegistries.ITEM.get(id)))
                        ),
                        ResourceLocation.CODEC.listOf()
                ).xmap(
                        either -> either.map(
                                id -> Set.of(BuiltInRegistries.ITEM.get(id)),
                                ids -> ids.stream().map(BuiltInRegistries.ITEM::get).collect(Collectors.toSet())
                        ),
                        items -> items.size() == 1
                                ? com.mojang.datafixers.util.Either.left(idOf(items.iterator().next()))
                                : com.mojang.datafixers.util.Either.right(items.stream().map(PartialNBTIngredient::idOf).toList())
                ).fieldOf("items").forGetter(ingredient -> ingredient.items),
                NBT_CODEC.fieldOf("nbt").forGetter(ingredient -> ingredient.nbt)
        ).apply(instance, PartialNBTIngredient::new));

        @Override
        public ResourceLocation getIdentifier() {
            return ID;
        }

        @Override
        public Codec<PartialNBTIngredient> getCodec(boolean allowEmpty) {
            return CODEC;
        }

        @Override
        public PartialNBTIngredient read(FriendlyByteBuf buffer) {
            Set<Item> items = Stream.generate(() -> BuiltInRegistries.ITEM.get(buffer.readResourceLocation())).limit(buffer.readVarInt()).collect(Collectors.toSet());
            CompoundTag nbt = buffer.readNbt();
            return new PartialNBTIngredient(items, Objects.requireNonNull(nbt));
        }

        @Override
        public void write(FriendlyByteBuf buffer, PartialNBTIngredient ingredient) {
            buffer.writeVarInt(ingredient.items.size());
            for (Item item : ingredient.items)
                buffer.writeResourceLocation(BuiltInRegistries.ITEM.getKey(item));
            buffer.writeNbt(ingredient.nbt);
        }
    }
}
