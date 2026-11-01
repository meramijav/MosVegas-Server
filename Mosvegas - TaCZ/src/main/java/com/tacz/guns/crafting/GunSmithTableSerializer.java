package com.tacz.guns.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.*;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作台配方序列化器
 */
public class GunSmithTableSerializer implements RecipeSerializer<GunSmithTableRecipe> {
    public static final MapCodec<GunSmithTableRecipe> MAP_CODEC = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString("materials"), ops.createString("result"));
        }

        @Override
        public <T> DataResult<GunSmithTableRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
            T map = ops.createMap(input.entries());
            JsonElement json = ops.convertTo(JsonOps.INSTANCE, map);
            return fromJsonElement(json);
        }

        @Override
        public <T> RecordBuilder<T> encode(GunSmithTableRecipe input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            JsonElement json = toJsonElement(input);
            T converted = JsonOps.INSTANCE.convertTo(ops, json);
            DataResult<MapLike<T>> mapLikeResult = ops.getMap(converted);
            if (mapLikeResult.result().isPresent()) {
                mapLikeResult.result().get().entries().forEach(pair -> prefix.add(pair.getFirst(), pair.getSecond()));
            }
            return prefix;
        }
    };

    public static final Codec<GunSmithTableRecipe> CODEC = MAP_CODEC.codec();

    @Override
    public Codec<GunSmithTableRecipe> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public GunSmithTableRecipe fromNetwork(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        List<GunSmithTableIngredient> ingredients = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ingredients.add(new GunSmithTableIngredient(Ingredient.fromNetwork(buffer), buffer.readInt()));
        }
        ItemStack resultItem = buffer.readItem();
        ResourceLocation group = buffer.readResourceLocation();
        GunSmithTableResult result = new GunSmithTableResult(resultItem, group);
        return new GunSmithTableRecipe(result, ingredients);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, GunSmithTableRecipe recipe) {
        buffer.writeInt(recipe.getInputs().size());
        for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
            ingredient.getIngredient().toNetwork(buffer);
            buffer.writeInt(ingredient.getCount());
        }
        buffer.writeItem(recipe.getResult().getResult());
        buffer.writeResourceLocation(recipe.getResult().getGroup());
    }

    private static DataResult<GunSmithTableRecipe> fromJsonElement(JsonElement json) {
        try {
            TableRecipe tableRecipe = CommonAssetsManager.GSON.fromJson(json, TableRecipe.class);
            if (tableRecipe == null || tableRecipe.getResult() == null) {
                return DataResult.error(() -> "Invalid gun smith table recipe: " + json);
            }
            return DataResult.success(new GunSmithTableRecipe(tableRecipe));
        } catch (Exception e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static JsonElement toJsonElement(GunSmithTableRecipe recipe) {
        JsonObject json = new JsonObject();
        JsonArray materials = new JsonArray();
        for (GunSmithTableIngredient input : recipe.getInputs()) {
            JsonObject material = new JsonObject();
            material.add("item", Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, input.getIngredient())
                    .getOrThrow(false, message -> {
                        throw new IllegalStateException("Failed to encode ingredient: " + message);
                    }));
            material.addProperty("count", input.getCount());
            materials.add(material);
        }
        json.add("materials", materials);

        ItemStack output = recipe.getOutput();
        ResourceLocation group = recipe.getResult().getGroup();
        if (group != null && !group.equals(TabConfig.TAB_EMPTY)) {
            json.addProperty("group", group.toString());
        }
        json.addProperty("type", GunSmithTableResult.CUSTOM);
        JsonObject item = new JsonObject();
        item.addProperty("item", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
        item.addProperty("count", output.getCount());
        if (output.getTag() != null) {
            item.addProperty("nbt", output.getTag().toString());
        }
        json.add("item", item);
        return json;
    }
}
