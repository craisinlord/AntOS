package com.craisinlord.antos.content.computer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public record ArchiveRecipeData(int columns, boolean gunBench, int shapedWidth, int shapedHeight,
                                List<List<ItemStack>> ingredients, List<ItemStack> results, ItemStack station) {
    public static final int MAX_SLOTS = 16;
    public static final int MAX_OPTIONS = 16;
    public static final int MAX_RESULTS = 4;
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchiveRecipeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArchiveRecipeData::columns,
            ByteBufCodecs.BOOL, ArchiveRecipeData::gunBench,
            ByteBufCodecs.VAR_INT, ArchiveRecipeData::shapedWidth,
            ByteBufCodecs.VAR_INT, ArchiveRecipeData::shapedHeight,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS)).apply(ByteBufCodecs.list(MAX_SLOTS)), ArchiveRecipeData::ingredients,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RESULTS)), ArchiveRecipeData::results,
            ItemStack.OPTIONAL_STREAM_CODEC, ArchiveRecipeData::station,
            ArchiveRecipeData::new
    );

    private static final Map<Class<?>, Optional<java.lang.reflect.Method>> RESULT_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<java.lang.reflect.Method>> INGREDIENT_METHODS = new ConcurrentHashMap<>();

    public static ArchiveRecipeData resolve(RecipeManager recipeManager, String recipeId, ContextMap context) {
        Identifier requestedId = Identifier.tryParse(recipeId);
        if (requestedId == null) return null;
        Item requestedItem = requestedItem(requestedId);
        RecipeHolder<?> holder = recipeManager.byKey(ResourceKey.create(Registries.RECIPE, requestedId)).orElse(null);
        if (holder == null && requestedItem != null) {
            holder = recipeManager.getRecipes().stream()
                    .filter(candidate -> recipeResults(candidate.value(), context).stream().anyMatch(stack -> stack.is(requestedItem)))
                    .min(Comparator.comparingInt(candidate -> recipePriority(candidate.value().getType())))
                    .orElse(null);
        }
        return holder == null ? null : fromRecipe(holder.value(), context);
    }

    public static ArchiveRecipeData resolve(Stream<RecipeDisplayEntry> entries, String recipeId, ContextMap context) {
        Identifier requestedId = Identifier.tryParse(recipeId);
        if (requestedId == null) return null;
        Item requestedItem = requestedItem(requestedId);
        if (requestedItem == null) return null;
        RecipeDisplayEntry entry = entries
                .filter(candidate -> entryResults(candidate, context).stream().anyMatch(stack -> stack.is(requestedItem)))
                .min(Comparator.comparingInt(candidate -> displayPriority(candidate.display())))
                .orElse(null);
        if (entry == null) return null;
        RecipeDisplay display = entry.display();
        Identifier typeId = BuiltInRegistries.RECIPE_DISPLAY.getKey(display.type());
        boolean gunBench = typeId != null && typeId.getNamespace().equals("scguns") && typeId.getPath().equals("gun_bench");
        List<SlotDisplay> ingredients = displayIngredients(display);
        if (ingredients == null) ingredients = entry.craftingRequirements().map(requirements -> requirements.stream()
                .map(Ingredient::display).toList()).orElseGet(List::of);
        ItemStack station = isCraftingDisplay(display) ? ItemStack.EMPTY : display.craftingStation().resolveForFirstStack(context);
        return create(display, ingredients, entryResults(entry, context), station, gunBench, context);
    }

    private static Item requestedItem(Identifier requestedId) {
        return BuiltInRegistries.ITEM.getOptional(requestedId).filter(item -> item != Items.AIR).orElse(null);
    }

    private static ArchiveRecipeData fromRecipe(Recipe<?> recipe, ContextMap context) {
        Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        boolean gunBench = typeId != null && typeId.getNamespace().equals("scguns") && typeId.getPath().equals("gun_bench");
        List<RecipeDisplay> displays = recipe.display();
        RecipeDisplay display = displays.isEmpty() ? null : displays.get(0);
        List<SlotDisplay> ingredients = gunBench ? null : displayIngredients(display);
        if (ingredients == null) ingredients = recipeIngredients(recipe);
        return create(display, ingredients, recipeResults(recipe, context), recipeStation(recipe.getType(), display, context), gunBench, context);
    }

    private static ArchiveRecipeData create(RecipeDisplay display, List<SlotDisplay> ingredients, List<ItemStack> results,
                                            ItemStack station, boolean gunBench, ContextMap context) {
        int shapedWidth = display instanceof ShapedCraftingRecipeDisplay shaped ? shaped.width() : 0;
        int shapedHeight = display instanceof ShapedCraftingRecipeDisplay shaped ? shaped.height() : 0;
        List<List<ItemStack>> slots = new ArrayList<>();
        for (SlotDisplay ingredient : ingredients) {
            if (slots.size() >= MAX_SLOTS) break;
            List<ItemStack> options = new ArrayList<>();
            for (ItemStack option : ingredient.resolveForStacks(context)) {
                if (options.size() >= MAX_OPTIONS) break;
                if (option != null && !option.isEmpty()) options.add(option.copy());
            }
            slots.add(List.copyOf(options));
        }
        List<ItemStack> shownResults = results.stream().limit(MAX_RESULTS).map(ItemStack::copy).toList();
        ItemStack shownStation = station == null ? ItemStack.EMPTY : station.copy();
        return new ArchiveRecipeData(gunBench ? 4 : 3, gunBench, shapedWidth, shapedHeight, List.copyOf(slots), shownResults, shownStation);
    }

    private static int recipePriority(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return 0;
        Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (typeId == null) return 3;
        if (typeId.getNamespace().equals("scguns")) return 1;
        if (typeId.getNamespace().equals("oritech")) return 2;
        return 3;
    }

    private static int displayPriority(RecipeDisplay display) {
        if (isCraftingDisplay(display)) return 0;
        Identifier typeId = BuiltInRegistries.RECIPE_DISPLAY.getKey(display.type());
        if (typeId == null) return 3;
        if (typeId.getNamespace().equals("scguns")) return 1;
        if (typeId.getNamespace().equals("oritech")) return 2;
        return 3;
    }

    private static boolean isCraftingDisplay(RecipeDisplay display) {
        return display instanceof ShapedCraftingRecipeDisplay || display instanceof ShapelessCraftingRecipeDisplay;
    }

    private static List<ItemStack> entryResults(RecipeDisplayEntry entry, ContextMap context) {
        return entry.resultItems(context).stream().filter(stack -> stack != null && !stack.isEmpty()).toList();
    }

    private static List<ItemStack> recipeResults(Recipe<?> recipe, ContextMap context) {
        ItemStack direct = ItemStack.EMPTY;
        try {
            for (RecipeDisplay display : recipe.display()) {
                direct = display.result().resolveForFirstStack(context);
                if (direct != null && !direct.isEmpty()) break;
            }
        } catch (RuntimeException ignored) {
            direct = ItemStack.EMPTY;
        }
        if (direct != null && !direct.isEmpty()) return List.of(direct);
        java.lang.reflect.Method method = RESULT_METHODS.computeIfAbsent(recipe.getClass(), type -> {
            try {
                return Optional.of(type.getMethod("getResults"));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
        if (method == null) return List.of();
        try {
            if (method.invoke(recipe) instanceof List<?> list) {
                List<ItemStack> stacks = new ArrayList<>();
                for (Object value : list) if (value instanceof ItemStack stack && !stack.isEmpty()) stacks.add(stack);
                return stacks;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return List.of();
    }

    private static List<SlotDisplay> displayIngredients(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
        return null;
    }

    private static List<SlotDisplay> recipeIngredients(Recipe<?> recipe) {
        java.lang.reflect.Method method = INGREDIENT_METHODS.computeIfAbsent(recipe.getClass(), type -> {
            try {
                return Optional.of(type.getMethod("getIngredients"));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
        if (method != null) {
            try {
                if (method.invoke(recipe) instanceof List<?> list && !list.isEmpty()) {
                    List<SlotDisplay> displays = new ArrayList<>();
                    for (Object value : list) displays.add(value instanceof Ingredient ingredient ? ingredient.display() : SlotDisplay.Empty.INSTANCE);
                    return displays;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        try {
            PlacementInfo placement = recipe.placementInfo();
            if (!placement.isImpossibleToPlace()) return placement.ingredients().stream().map(Ingredient::display).toList();
        } catch (RuntimeException ignored) { }
        return List.of();
    }

    private static ItemStack recipeStation(RecipeType<?> type, RecipeDisplay display, ContextMap context) {
        if (type == RecipeType.CRAFTING) return ItemStack.EMPTY;
        if (display != null) {
            ItemStack station = display.craftingStation().resolveForFirstStack(context);
            if (!station.isEmpty()) return station;
        }
        Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (typeId == null) return ItemStack.EMPTY;
        List<String> candidates = switch (typeId.toString()) {
            case "minecraft:smelting" -> List.of("minecraft:furnace");
            case "minecraft:blasting" -> List.of("minecraft:blast_furnace");
            case "minecraft:smoking" -> List.of("minecraft:smoker");
            case "minecraft:campfire_cooking" -> List.of("minecraft:campfire");
            case "minecraft:stonecutting" -> List.of("minecraft:stonecutter");
            case "minecraft:smithing" -> List.of("minecraft:smithing_table");
            default -> List.of(typeId + "_block", typeId.toString(), typeId.getNamespace() + ":" + typeId.getPath() + "_controller");
        };
        for (String candidate : candidates) {
            Identifier itemId = Identifier.tryParse(candidate);
            if (itemId == null) continue;
            var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item != null && item != Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }
}
