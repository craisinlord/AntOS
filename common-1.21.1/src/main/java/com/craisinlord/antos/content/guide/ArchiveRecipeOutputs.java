package com.craisinlord.antos.content.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ArchiveRecipeOutputs {
    public static final ArchiveRecipeOutputs EMPTY = new ArchiveRecipeOutputs(Map.of());
    private static final List<String> SINGLE = List.of("result", "output", "output_item", "outputItem", "extraOutput", "result_item");
    private static final List<String> LISTS = List.of("results", "outputs", "outputItems", "output_items");
    private static Object cachedSource;
    private static int cachedSize = -1;
    private static Set<String> cachedTypes = Set.of();
    private static ArchiveRecipeOutputs cached = EMPTY;

    private final Map<String, List<Set<String>>> producers;

    private ArchiveRecipeOutputs(Map<String, List<Set<String>>> producers) {
        this.producers = producers;
    }

    public List<Set<String>> producers(String item) {
        return producers.getOrDefault(item, List.of());
    }

    public Set<String> items() {
        return producers.keySet();
    }

    public static synchronized ArchiveRecipeOutputs of(RecipeManager manager, RegistryAccess access, Collection<ComputerGuideData.Entry> entries) {
        if (manager == null || access == null) return EMPTY;
        Set<String> wanted = new HashSet<>();
        for (ComputerGuideData.Entry entry : entries) wanted.addAll(entry.lookupRecipes());
        if (wanted.isEmpty()) return EMPTY;
        Collection<RecipeHolder<?>> recipes = manager.getRecipes();
        if (manager == cachedSource && recipes.size() == cachedSize && wanted.equals(cachedTypes)) return cached;
        Map<String, List<Set<String>>> result = new HashMap<>();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            Set<String> keys = keys(recipe);
            boolean opted = keys.stream().anyMatch(wanted::contains);
            Set<String> items = new LinkedHashSet<>();
            try {
                ItemStack output = recipe.getResultItem(access);
                if (output != null && !output.isEmpty()) items.add(BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            } catch (RuntimeException ignored) { }
            if (opted) items.addAll(encodedOutputs(recipe, ops));
            for (String item : items) {
                if (item.equals("minecraft:air")) continue;
                result.computeIfAbsent(item, ignored -> new ArrayList<>()).add(keys);
            }
        }
        cachedSource = manager;
        cachedSize = recipes.size();
        cachedTypes = Set.copyOf(wanted);
        cached = new ArchiveRecipeOutputs(Map.copyOf(result));
        return cached;
    }

    private static Set<String> keys(Recipe<?> recipe) {
        Set<String> keys = new HashSet<>(2);
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (type != null) keys.add(type.toString());
        ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        if (serializer != null) keys.add(serializer.toString());
        return Set.copyOf(keys);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<String> encodedOutputs(Recipe<?> recipe, RegistryOps<JsonElement> ops) {
        Set<String> items = new LinkedHashSet<>();
        try {
            RecipeSerializer serializer = recipe.getSerializer();
            JsonElement encoded = (JsonElement) serializer.codec().codec().encodeStart(ops, recipe).result().orElse(null);
            if (encoded == null || !encoded.isJsonObject()) return items;
            JsonObject object = encoded.getAsJsonObject();
            for (String key : SINGLE) if (object.has(key)) collect(object.get(key), items, 0);
            for (String key : LISTS) if (object.has(key) && object.get(key).isJsonArray()) object.getAsJsonArray(key).forEach(value -> collect(value, items, 0));
        } catch (RuntimeException ignored) { }
        return items;
    }

    private static void collect(JsonElement element, Set<String> items, int depth) {
        if (element == null || depth > 2) return;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            add(element.getAsString(), items);
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> collect(value, items, depth + 1));
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("id", "item")) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                add(object.get(key).getAsString(), items);
                return;
            }
        }
        for (String key : List.of("stack", "itemStack", "item_stack", "item")) if (object.has(key)) collect(object.get(key), items, depth + 1);
    }

    private static void add(String value, Set<String> items) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) items.add(id.toString());
    }
}
