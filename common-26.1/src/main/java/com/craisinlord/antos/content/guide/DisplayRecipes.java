package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;

public final class DisplayRecipes {
    @FunctionalInterface
    public interface Adapter { JsonObject extract(RecipeHolder<?> recipe, RegistryAccess registries); }
    private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();
    private static volatile Map<String, RecipeHolder<?>> recipes = Map.of();
    private static RegistryAccess registries;
    private static Object server;
    private static long revision = -1;

    static {
        ADAPTERS.put("antos:vanilla", DisplayRecipes::vanilla);
        ADAPTERS.put("antos:crafting", DisplayRecipes::vanilla);
        ADAPTERS.put("antos:cooking", DisplayRecipes::vanilla);
        ADAPTERS.put("antos:smithing", DisplayRecipes::vanilla);
    }

    private DisplayRecipes() { }
    public static boolean ready() { return registries != null; }
    public static synchronized void serverStopped() { server = null; revision = -1; recipes = Map.of(); registries = null; }

    public static synchronized void register(Identifier id, Adapter adapter) {
        if (ADAPTERS.putIfAbsent(id.toString(), Objects.requireNonNull(adapter)) != null) throw new IllegalArgumentException("Duplicate recipe adapter " + id);
        revision = -1;
    }

    public static synchronized void refresh(MinecraftServer current) {
        long next = ComputerGuideData.datapackRevision();
        if (server == current && revision == next) return;
        server = current; revision = next;
        install(current.getRecipeManager().getRecipes(), current.registryAccess());
        CustomDisplays.recompile();
    }

    public static synchronized void install(Collection<RecipeHolder<?>> source, RegistryAccess access) {
        registries = access;
        Map<String, RecipeHolder<?>> replacement = new LinkedHashMap<>();
        for (RecipeHolder<?> recipe : source) replacement.put(recipe.id().identifier().toString(), recipe);
        recipes = Map.copyOf(replacement);
    }

    public static JsonObject values(JsonObject binding) {
        String recipeId = CustomDisplays.string(binding, "recipe", "");
        String adapterId = CustomDisplays.string(binding, "adapter", "antos:vanilla");
        RecipeHolder<?> recipe = recipes.get(recipeId);
        if (recipe == null) throw new IllegalArgumentException("recipe_binding.recipe: missing or not yet loaded recipe " + recipeId);
        return extract(adapterId, recipe, registries);
    }

    public static JsonObject extract(String adapterId, RecipeHolder<?> recipe, RegistryAccess access) {
        Adapter adapter = ADAPTERS.get(adapterId);
        if (adapter == null) throw new IllegalArgumentException("recipe_binding.adapter: unsupported adapter " + adapterId);
        if (adapterId.equals("antos:cooking") && !(recipe.value() instanceof AbstractCookingRecipe) || adapterId.equals("antos:crafting") && !(recipe.value() instanceof CraftingRecipe) || adapterId.equals("antos:smithing") && !(recipe.value() instanceof SmithingRecipe)) throw new IllegalArgumentException("recipe_binding: recipe does not match adapter " + adapterId);
        JsonObject values = adapter.extract(recipe, access);
        if (values == null || !values.has("inputs") || !values.has("outputs")) throw new IllegalArgumentException("recipe_binding: adapter returned no inputs/outputs");
        return values.deepCopy();
    }

    private static JsonObject vanilla(RecipeHolder<?> holder, RegistryAccess access) {
        Recipe<?> recipe = holder.value();
        if (!(recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe || recipe instanceof SmithingRecipe) || recipe.isSpecial()) throw new IllegalArgumentException("recipe_binding: vanilla adapter cannot describe this recipe; register an adapter");
        if (recipe instanceof SmithingTrimRecipe) throw new IllegalArgumentException("recipe_binding: smithing trim outputs depend on inputs; register a specialized adapter");
        ContextMap context = new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, access).create(SlotDisplayContext.CONTEXT);
        RecipeDisplay display = recipe.display().isEmpty() ? null : recipe.display().get(0);
        ItemStack output = display == null ? ItemStack.EMPTY : display.result().resolveForFirstStack(context);
        if (output.isEmpty()) throw new IllegalArgumentException("recipe_binding: dynamic or missing result requires a specialized adapter");
        JsonObject result = new JsonObject(); JsonArray inputs = new JsonArray(), outputs = new JsonArray();
        List<SlotDisplay> ingredients = switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
            case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
            case FurnaceRecipeDisplay furnace -> List.of(furnace.ingredient());
            case SmithingRecipeDisplay smithing -> List.of(smithing.template(), smithing.base(), smithing.addition());
            default -> throw new IllegalArgumentException("recipe_binding: vanilla adapter cannot describe this recipe; register an adapter");
        };
        for (SlotDisplay ingredient : ingredients) {
            JsonObject slot = new JsonObject();
            if (ingredient instanceof SlotDisplay.TagSlotDisplay tag) {
                slot.addProperty("tag", tag.tag().location().toString()); slot.addProperty("role", "input"); inputs.add(slot); continue;
            }
            JsonArray choices = new JsonArray();
            for (ItemStack stack : ingredient.resolveForStacks(context)) {
                if (choices.size() >= 128) throw new IllegalArgumentException("recipe_binding: ingredient alternatives exceed limit");
                choices.add(stack(stack, access));
            }
            if (choices.isEmpty()) { slot.addProperty("empty", true); }
            else slot.add("stacks", choices);
            slot.addProperty("role", "input"); inputs.add(slot);
        }
        JsonObject outputSlot = new JsonObject(); outputSlot.add("stack", stack(output, access)); outputSlot.addProperty("count", output.getCount()); outputSlot.addProperty("role", "output"); outputs.add(outputSlot);
        if (recipe instanceof SmithingTransformRecipe) outputSlot.addProperty("consumption", "computer.antos.display.smithing_components");
        result.add("inputs", inputs); result.add("outputs", outputs);
        if (display instanceof ShapedCraftingRecipeDisplay shaped) result.addProperty("columns", shaped.width());
        if (recipe instanceof AbstractCookingRecipe cooking) result.addProperty("duration", cooking.cookingTime() + " ticks");
        return result;
    }

    private static JsonElement stack(ItemStack stack, RegistryAccess access) {
        return ItemStack.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, access), stack).getOrThrow();
    }
}
