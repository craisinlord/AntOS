package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.content.client.JeiBridge;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

final class JeiHandoff implements JeiBridge.Handler {
    private final IJeiRuntime runtime;

    JeiHandoff(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Boolean usesFor(int keyCode, int scanCode) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (runtime.getKeyMappings().getShowUses().isActiveAndMatches(key)) return true;
        if (runtime.getKeyMappings().getShowRecipe().isActiveAndMatches(key)) return false;
        return null;
    }

    @Override
    public boolean has(ItemStack stack, boolean uses) {
        return runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(java.util.List.of(focus(stack, uses))).get().findAny().isPresent();
    }

    @Override
    public boolean show(ItemStack stack, boolean uses) {
        if (!has(stack, uses)) return false;
        runtime.getRecipesGui().show(focus(stack, uses));
        return true;
    }

    private mezz.jei.api.recipe.IFocus<ItemStack> focus(ItemStack stack, boolean uses) {
        return runtime.getJeiHelpers().getFocusFactory().createFocus(uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK, stack.copyWithCount(1));
    }

    @Override
    public boolean recipesScreen(Screen screen) {
        return screen instanceof IRecipesGui;
    }

    @Override
    public Screen parentOf(Screen screen) {
        return screen instanceof IRecipesGui gui ? gui.getParentScreen().orElse(null) : null;
    }
}
