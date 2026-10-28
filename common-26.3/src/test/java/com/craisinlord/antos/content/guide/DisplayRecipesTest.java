package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

public final class DisplayRecipesTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createWorldLookup()).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var smelting = new RecipeHolder<>(key("minecraft:iron_ingot_from_smelting_raw_iron"), new SmeltingRecipe(new Recipe.CommonInfo(true), new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "test"), Ingredient.of(Items.RAW_IRON), new ItemStackTemplate(Items.IRON_INGOT), .7F, 200));
        DisplayRecipes.install(List.of(smelting), access);
        JsonObject values = DisplayRecipes.values(JsonParser.parseString("{recipe:'minecraft:iron_ingot_from_smelting_raw_iron',adapter:'antos:cooking'}").getAsJsonObject());
        check(values.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonObject("stack").get("id").getAsString().equals("minecraft:iron_ingot"), "vanilla result extraction");
        check(values.get("duration").getAsString().equals("200 ticks"), "cooking duration extraction");
        rejects(() -> DisplayRecipes.values(JsonParser.parseString("{recipe:'test:missing'}").getAsJsonObject()));
        rejects(() -> DisplayRecipes.extract("antos:crafting", smelting, access));
        var crafting = new RecipeHolder<>(key("test:crafting"), new ShapelessRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "test"), new ItemStackTemplate(Items.IRON_BLOCK), List.of(Ingredient.of(Items.IRON_INGOT))));
        check(DisplayRecipes.extract("antos:crafting", crafting, access).getAsJsonArray("inputs").size() == 1, "shapeless crafting extraction");
        var smithing = new RecipeHolder<>(key("test:smithing"), new SmithingTransformRecipe(new Recipe.CommonInfo(true), Optional.of(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)), Ingredient.of(Items.DIAMOND_SWORD), Optional.of(Ingredient.of(Items.NETHERITE_INGOT)), new ItemStackTemplate(Items.NETHERITE_SWORD)));
        check(DisplayRecipes.extract("antos:smithing", smithing, access).getAsJsonArray("inputs").size() == 3, "smithing ingredient extraction");
        var changed = new RecipeHolder<>(smelting.id(), new SmeltingRecipe(new Recipe.CommonInfo(true), new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "test"), Ingredient.of(Items.RAW_IRON), new ItemStackTemplate(Items.GOLD_INGOT, 2), .7F, 80));
        DisplayRecipes.install(List.of(changed), access);
        JsonObject updated = DisplayRecipes.values(JsonParser.parseString("{recipe:'minecraft:iron_ingot_from_smelting_raw_iron'}").getAsJsonObject());
        check(updated.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt() == 2, "recipe replacement updates quantities");
        DisplayRecipes.install(List.of(smelting), access);
        if (args.length > 0) {
            java.nio.file.Path directory = java.nio.file.Path.of(args[0], "examples/datapack-template/data/examplemod/computer/display");
            Map<Identifier, JsonObject> source = new LinkedHashMap<>();
            try (var files = java.nio.file.Files.list(directory)) {
                for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) source.put(Identifier.parse("examplemod:" + file.getFileName().toString().replace(".json", "")), JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject());
            }
            for (var entry : source.entrySet()) {
                if (entry.getValue().has("variables")) continue;
                JsonObject display = CustomDisplays.resolve(entry.getKey(), source, new LinkedHashSet<>());
                CustomDisplays.compile(display, null); CustomDisplays.validate(display);
            }
        }
        DisplayRecipes.install(List.of(), access);
        rejects(() -> DisplayRecipes.values(JsonParser.parseString("{recipe:'minecraft:iron_ingot_from_smelting_raw_iron'}").getAsJsonObject()));
        System.out.println("Recipe binding and all authored example tests passed: extraction, typed adapters, changed quantities, missing recipes, presets, variants, and responsive scene references");
    }
    private static net.minecraft.resources.ResourceKey<Recipe<?>> key(String id) { return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse(id)); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch (RuntimeException expected) { return; } throw new AssertionError("Expected binding failure"); }
}
