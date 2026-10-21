package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

public final class DisplayRecipes {
    @FunctionalInterface
    public interface Adapter { JsonObject extract(Recipe<?> recipe, RegistryAccess registries); }
    private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();
    private static volatile Map<String, Recipe<?>> recipes = Map.of();
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

    public static synchronized void register(ResourceLocation id, Adapter adapter) {
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

    public static synchronized void install(Collection<Recipe<?>> source, RegistryAccess access) {
        registries = access;
        Map<String, Recipe<?>> replacement = new LinkedHashMap<>();
        for (Recipe<?> recipe : source) replacement.put(recipe.getId().toString(), recipe);
        recipes = Map.copyOf(replacement);
    }

    public static JsonObject values(JsonObject binding) {
        String recipeId = CustomDisplays.string(binding, "recipe", "");
        String adapterId = CustomDisplays.string(binding, "adapter", "antos:vanilla");
        Recipe<?> recipe = recipes.get(recipeId);
        if (recipe == null) throw new IllegalArgumentException("recipe_binding.recipe: missing or not yet loaded recipe " + recipeId);
        return extract(adapterId, recipe, registries);
    }

    public static JsonObject extract(String adapterId, Recipe<?> recipe, RegistryAccess access) {
        Adapter adapter = ADAPTERS.get(adapterId);
        if (adapter == null) throw new IllegalArgumentException("recipe_binding.adapter: unsupported adapter " + adapterId);
        if (adapterId.equals("antos:cooking") && !(recipe instanceof AbstractCookingRecipe) || adapterId.equals("antos:crafting") && !(recipe instanceof CraftingRecipe) || adapterId.equals("antos:smithing") && !(recipe instanceof SmithingRecipe)) throw new IllegalArgumentException("recipe_binding: recipe does not match adapter " + adapterId);
        JsonObject values = adapter.extract(recipe, access);
        if (values == null || !values.has("inputs") || !values.has("outputs")) throw new IllegalArgumentException("recipe_binding: adapter returned no inputs/outputs");
        return values.deepCopy();
    }

    private static JsonObject vanilla(Recipe<?> recipe, RegistryAccess access) {
        if (!(recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe || recipe instanceof SmithingRecipe) || recipe.isSpecial()) throw new IllegalArgumentException("recipe_binding: vanilla adapter cannot describe this recipe; register an adapter");
        if (recipe instanceof SmithingTrimRecipe) throw new IllegalArgumentException("recipe_binding: smithing trim outputs depend on inputs; register a specialized adapter");
        ItemStack output = recipe.getResultItem(access);
        if (output.isEmpty()) throw new IllegalArgumentException("recipe_binding: dynamic or missing result requires a specialized adapter");
        JsonObject result = new JsonObject(); JsonArray inputs = new JsonArray(), outputs = new JsonArray();
        List<Ingredient> ingredients = new ArrayList<>(recipe.getIngredients());
        if (recipe instanceof SmithingTransformRecipe transform) {
            net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                RecipeSerializer.SMITHING_TRANSFORM.toNetwork(buffer, transform);
                ingredients.clear();
                for (int index = 0; index < 3; index++) ingredients.add(Ingredient.fromNetwork(buffer));
            } finally {
                buffer.release();
            }
        }
        for (Ingredient ingredient : ingredients) {
            JsonObject slot = new JsonObject();
            JsonElement encoded = ingredient.isEmpty() ? JsonNull.INSTANCE : ingredient.toJson();
            if (encoded.isJsonObject() && encoded.getAsJsonObject().has("tag")) {
                slot.add("tag", encoded.getAsJsonObject().get("tag")); slot.addProperty("role", "input"); inputs.add(slot); continue;
            }
            JsonArray choices = new JsonArray();
            for (ItemStack stack : ingredient.getItems()) {
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
        if (recipe instanceof ShapedRecipe shaped) result.addProperty("columns", shaped.getWidth());
        if (recipe instanceof AbstractCookingRecipe cooking) result.addProperty("duration", cooking.getCookingTime() + " ticks");
        return result;
    }

    private static JsonElement stack(ItemStack stack, RegistryAccess access) {
        return ItemStack.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, access), stack).getOrThrow(false, message -> { });
    }
}
