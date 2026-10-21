package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.AntOSObjects;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@JeiPlugin
public final class AntOSJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(AntOS.MODID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        ItemStack computer = computer();
        registration.addRecipeCategories(
                new AntazonListingCategory(helper, computer),
                new AntazonSellCategory(helper, computer),
                new TaskRewardCategory(helper, new ItemStack(Items.WRITABLE_BOOK)));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        JeiRecipes.register(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack computer = computer();
        if (computer.is(Items.BARRIER)) return;
        registration.addCraftingStation(JeiRecipes.LISTINGS, computer);
        registration.addCraftingStation(JeiRecipes.SELL, computer);
        registration.addCraftingStation(JeiRecipes.TASKS, computer);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiRecipes.runtimeAvailable(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRecipes.runtimeUnavailable();
    }

    private static ItemStack computer() {
        try {
            return new ItemStack(AntOSObjects.COMPUTER_ITEM.get());
        } catch (RuntimeException exception) {
            return new ItemStack(Items.BARRIER);
        }
    }
}
