package cn.sh1rocu.tacz.util.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class StrictNBTIngredient implements CustomIngredient {
    private final ItemStack stack;

    protected StrictNBTIngredient(ItemStack stack) {
        this.stack = stack;
    }

    /**
     * Creates a new ingredient matching the given stack and tag
     */
    public static StrictNBTIngredient of(ItemStack stack) {
        return new StrictNBTIngredient(stack);
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null)
            return false;
        //Can't use areItemStacksEqualUsingNBTShareTag because it compares stack size as well
        return this.stack.getItem() == input.getItem() && this.stack.getDamageValue() == input.getDamageValue() && areShareTagsEqual(this.stack, input);
    }

    private static boolean areShareTagsEqual(ItemStack stack, ItemStack other) {
        CompoundTag shareTagA = stack.getTag();
        CompoundTag shareTagB = other.getTag();
        if (shareTagA == null)
            return shareTagB == null;
        else
            return shareTagB != null && shareTagA.equals(shareTagB);
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return Stream.of(stack).toList();
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<StrictNBTIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static final ResourceLocation ID = new ResourceLocation("forge", "nbt");

    public static class Serializer implements CustomIngredientSerializer<StrictNBTIngredient> {
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

        private static final Codec<StrictNBTIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ingredient -> BuiltInRegistries.ITEM.getKey(ingredient.stack.getItem())),
                Codec.INT.optionalFieldOf("count", 1).forGetter(ingredient -> ingredient.stack.getCount()),
                NBT_CODEC.optionalFieldOf("nbt", new CompoundTag()).forGetter(ingredient -> ingredient.stack.getTag() == null ? new CompoundTag() : ingredient.stack.getTag())
        ).apply(instance, (id, count, nbt) -> {
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                throw new IllegalStateException("Unknown item '" + id + "'");
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), count);
            if (nbt.isEmpty()) {
                return new StrictNBTIngredient(stack);
            }
            stack.setTag(nbt);
            return new StrictNBTIngredient(stack);
        }));

        @Override
        public ResourceLocation getIdentifier() {
            return ID;
        }

        @Override
        public Codec<StrictNBTIngredient> getCodec(boolean allowEmpty) {
            return CODEC;
        }

        @Override
        public StrictNBTIngredient read(FriendlyByteBuf buffer) {
            return new StrictNBTIngredient(buffer.readItem());
        }

        @Override
        public void write(FriendlyByteBuf buffer, StrictNBTIngredient ingredient) {
            buffer.writeItem(ingredient.stack);
        }
    }
}
