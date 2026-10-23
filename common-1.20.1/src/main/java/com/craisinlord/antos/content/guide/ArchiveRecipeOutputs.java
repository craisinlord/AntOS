package com.craisinlord.antos.content.guide;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

public final class ArchiveRecipeOutputs {
    public static final ArchiveRecipeOutputs EMPTY = new ArchiveRecipeOutputs(Map.of());
    private static final Set<String> FIELDS = Set.of("result", "output", "outputItem", "extraOutput", "resultItem", "results", "outputs", "outputItems");
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
        Collection<Recipe<?>> recipes = manager.getRecipes();
        if (manager == cachedSource && recipes.size() == cachedSize && wanted.equals(cachedTypes)) return cached;
        Map<String, List<Set<String>>> result = new HashMap<>();
        for (Recipe<?> recipe : recipes) {
            Set<String> keys = keys(recipe);
            boolean opted = keys.stream().anyMatch(wanted::contains);
            Set<String> items = new LinkedHashSet<>();
            try {
                ItemStack output = recipe.getResultItem(access);
                if (output != null && !output.isEmpty()) items.add(BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            } catch (RuntimeException ignored) { }
            if (opted) items.addAll(fieldOutputs(recipe));
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

    private static Set<String> fieldOutputs(Recipe<?> recipe) {
        Set<String> items = new LinkedHashSet<>();
        for (Class<?> type = recipe.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !FIELDS.contains(field.getName())) continue;
                try {
                    field.setAccessible(true);
                    collect(field.get(recipe), items, 0);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return items;
    }

    private static void collect(Object value, Set<String> items, int depth) {
        if (value == null || depth > 2) return;
        if (value instanceof ItemStack stack) {
            if (!stack.isEmpty()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        } else if (value instanceof Item item) {
            items.add(BuiltInRegistries.ITEM.getKey(item).toString());
        } else if (value instanceof String string) {
            add(string, items);
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) collect(element, items, depth + 1);
        } else if (value instanceof Object[] array) {
            for (Object element : array) collect(element, items, depth + 1);
        }
    }

    private static void add(String value, Set<String> items) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) items.add(id.toString());
    }
}
