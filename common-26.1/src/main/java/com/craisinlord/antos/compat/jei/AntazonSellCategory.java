package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.content.client.JeiClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class AntazonSellCategory implements IRecipeCategory<JeiRecipes.SellRecipe> {
    private static final int HEIGHT = 58;
    private static final int STATUS_TOP = 37;
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    AntazonSellCategory(IGuiHelper helper, ItemStack icon) {
        this.icon = helper.createDrawableItemStack(icon);
        this.arrow = helper.getRecipeArrow();
    }

    @Override public IRecipeType<JeiRecipes.SellRecipe> getRecipeType() { return JeiRecipes.SELL; }

    @Override public Component getTitle() { return Component.translatable("jei.antos.category.antazon_sell"); }

    @Override public IDrawable getIcon() { return icon; }

    @Override public int getWidth() { return JeiDrawing.WIDTH; }

    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JeiRecipes.SellRecipe recipe, IFocusGroup focuses) {
        ItemStack stack = JeiDrawing.stack(recipe.rule().item(), 1);
        if (!stack.isEmpty()) builder.addSlot(RecipeIngredientRole.INPUT, 5, 16).add(stack);
    }

    @Override
    public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder, JeiRecipes.SellRecipe recipe, IFocusGroup focuses) {
        builder.addGuiEventListener(new JeiOpenButton(getWidth(), new com.craisinlord.antos.content.client.ArchiveLookup.Target("sell", recipe.rule().item())));
    }

    @Override
    public void draw(JeiRecipes.SellRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        JeiDrawing.panel(g, getWidth(), HEIGHT);
        JeiDrawing.text(g, Component.translatable("jei.antos.sell.title"), 5, 5, JeiOpenButton.titleWidth(getWidth()), Ui.TEXT_STRONG);
        JeiOpenButton.draw(g, getWidth(), mouseX, mouseY);
        JeiDrawing.slot(g, 5, 16);
        arrow.draw(g, 28, 16);
        String value = JeiDrawing.number(recipe.rule().value());
        g.text(JeiDrawing.font(), value, 58, 20, Ui.ACCENT, false);
        int x = 58 + JeiDrawing.font().width(value) + 3;
        JeiDrawing.coin(g, x, 20);
        JeiDrawing.text(g, Component.translatable("jei.antos.sell.each"), x + 10, 20, getWidth() - x - 14, Ui.TEXT_MUTED);
        List<JeiDrawing.Line> lines = lines(recipe);
        for (int index = 0; index < lines.size(); index++) {
            JeiDrawing.Line line = lines.get(index);
            JeiDrawing.text(g, line.text(), 5, STATUS_TOP + index * 10, getWidth() - 10, line.color());
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, JeiRecipes.SellRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (JeiOpenButton.tooltip(tooltip, getWidth(), mouseX, mouseY)) return;
        if (JeiDrawing.inside(mouseX, mouseY, 56, 16, getWidth() - 60, 16)) {
            tooltip.add(JeiDrawing.currency(recipe.rule().value()));
            return;
        }
        List<JeiDrawing.Line> lines = lines(recipe);
        for (int index = 0; index < lines.size(); index++) {
            if (!JeiDrawing.inside(mouseX, mouseY, 4, STATUS_TOP + index * 10 - 1, getWidth() - 8, 10)) continue;
            tooltip.addAll(lines.get(index).tooltip());
            return;
        }
    }

    private static List<JeiDrawing.Line> lines(JeiRecipes.SellRecipe recipe) {
        JeiClientState.Sell rule = recipe.rule();
        JeiClientState.State state = JeiClientState.state();
        boolean current = JeiRecipes.current(recipe.version());
        List<JeiDrawing.Line> lines = new ArrayList<>();
        if (current && state.sellLocked(rule))
            lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.locked_by", JeiDrawing.firstRequirement(rule.unlock())), Ui.ERROR,
                    JeiDrawing.requirements(rule.unlock())));
        if (rule.limit() > 0) lines.add(new JeiDrawing.Line(JeiDrawing.limit(rule.limit(), rule.limitReset()), Ui.TEXT, List.of()));
        if (lines.size() < 2 && current && !state.account()) lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.signed_out"), Ui.TEXT_MUTED,
                List.of(Component.translatable("jei.antos.signed_out.tooltip"))));
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }
}
