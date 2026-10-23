package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.content.client.JeiClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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
import java.util.Locale;

final class TaskRewardCategory implements IRecipeCategory<JeiRecipes.TaskRecipe> {
    private static final int HEIGHT = 96;
    private static final int GRID_TOP = 27;
    private static final int COLUMNS = 8;
    private static final int SLOTS = 16;
    private static final int EXTRAS_TOP = 66;
    private static final int STATUS_TOP = 76;
    private final IDrawable icon;

    TaskRewardCategory(IGuiHelper helper, ItemStack icon) {
        this.icon = helper.createDrawableItemStack(icon);
    }

    @Override public IRecipeType<JeiRecipes.TaskRecipe> getRecipeType() { return JeiRecipes.TASKS; }

    @Override public Component getTitle() { return Component.translatable("jei.antos.category.task_rewards"); }

    @Override public IDrawable getIcon() { return icon; }

    @Override public int getWidth() { return JeiDrawing.WIDTH; }

    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JeiRecipes.TaskRecipe recipe, IFocusGroup focuses) {
        List<JeiClientState.Stack> items = new ArrayList<>();
        for (JeiClientState.Stack item : recipe.task().items()) if (!JeiDrawing.stack(item.item(), item.count()).isEmpty()) items.add(item);
        int shown = Math.min(items.size(), SLOTS);
        for (int index = 0; index < shown; index++) {
            List<JeiClientState.Stack> group = index == SLOTS - 1 && items.size() > SLOTS ? items.subList(index, items.size()) : List.of(items.get(index));
            List<ItemStack> stacks = new ArrayList<>();
            for (JeiClientState.Stack item : group) stacks.add(JeiDrawing.stack(item.item(), item.count()));
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, 5 + index % COLUMNS * 19, GRID_TOP + index / COLUMNS * 19).addItemStacks(stacks);
            slot.addRichTooltipCallback((view, tooltip) -> {
                ItemStack displayed = view.getDisplayedItemStack().orElse(ItemStack.EMPTY);
                for (JeiClientState.Stack item : group) {
                    if (displayed.isEmpty() || !JeiDrawing.stack(item.item(), 1).is(displayed.getItem())) continue;
                    tooltip.addAll(notes(item));
                    return;
                }
            });
        }
    }

    @Override
    public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder, JeiRecipes.TaskRecipe recipe, IFocusGroup focuses) {
        builder.addGuiEventListener(new JeiOpenButton(getWidth(), new com.craisinlord.antos.content.client.ArchiveLookup.Target("task", recipe.task().id())));
    }

    @Override
    public void draw(JeiRecipes.TaskRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        JeiClientState.Task task = recipe.task();
        JeiDrawing.panel(g, getWidth(), HEIGHT);
        JeiDrawing.text(g, Component.translatable(task.title()), 5, 5, JeiOpenButton.titleWidth(getWidth()), Ui.TEXT_STRONG);
        JeiOpenButton.draw(g, getWidth(), mouseX, mouseY);
        if (!task.category().isBlank()) JeiDrawing.text(g, Component.translatable(task.category()), 5, 15, getWidth() - 10, Ui.TEXT_MUTED);
        int count = slots.getSlotViews(RecipeIngredientRole.OUTPUT).size();
        for (int index = 0; index < count; index++) JeiDrawing.slot(g, 5 + index % COLUMNS * 19, GRID_TOP + index / COLUMNS * 19);
        int x = 5;
        if (task.antcoins() > 0L) {
            String value = "+" + JeiDrawing.number(task.antcoins());
            g.text(JeiDrawing.font(), value, x, EXTRAS_TOP, Ui.ACCENT, false);
            x += JeiDrawing.font().width(value) + 2;
            JeiDrawing.coin(g, x, EXTRAS_TOP);
            x += 12;
        }
        if (task.experience() > 0)
            x = JeiDrawing.text(g, Component.translatable("jei.antos.task.experience", JeiDrawing.number(task.experience())), x, EXTRAS_TOP, getWidth() - x - 5, Ui.TEXT) + 8;
        if (task.levels() > 0)
            JeiDrawing.text(g, Component.translatable("jei.antos.task.levels", JeiDrawing.number(task.levels())), x, EXTRAS_TOP, getWidth() - x - 5, Ui.TEXT);
        List<JeiDrawing.Line> lines = lines(recipe);
        for (int index = 0; index < lines.size(); index++) {
            JeiDrawing.Line line = lines.get(index);
            JeiDrawing.text(g, line.text(), 5, STATUS_TOP + index * 10, getWidth() - 10, line.color());
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, JeiRecipes.TaskRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (JeiOpenButton.tooltip(tooltip, getWidth(), mouseX, mouseY)) return;
        if (recipe.task().antcoins() > 0L && JeiDrawing.inside(mouseX, mouseY, 4, EXTRAS_TOP - 1, 60, 10)) {
            tooltip.add(JeiDrawing.currency(recipe.task().antcoins()));
            return;
        }
        List<JeiDrawing.Line> lines = lines(recipe);
        for (int index = 0; index < lines.size(); index++) {
            if (!JeiDrawing.inside(mouseX, mouseY, 4, STATUS_TOP + index * 10 - 1, getWidth() - 8, 10)) continue;
            tooltip.addAll(lines.get(index).tooltip());
            return;
        }
    }

    private static List<Component> notes(JeiClientState.Stack item) {
        List<Component> notes = new ArrayList<>();
        if (item.group().startsWith("option:"))
            notes.add(Component.translatable("jei.antos.task.option", Component.translatable(item.group().substring("option:".length()))));
        else if (item.group().equals("choice")) notes.add(Component.translatable("jei.antos.task.choice"));
        else if (item.group().equals("random")) notes.add(Component.translatable("jei.antos.task.random"));
        if (item.chance() < 1.0) notes.add(Component.translatable("jei.antos.task.chance", chance(item.chance())));
        return notes;
    }

    private static String chance(double chance) {
        double percent = chance * 100.0;
        return percent >= 10.0 ? String.format(Locale.ROOT, "%.0f%%", percent) : String.format(Locale.ROOT, "%.1f%%", percent);
    }

    private static List<JeiDrawing.Line> lines(JeiRecipes.TaskRecipe recipe) {
        JeiClientState.Task task = recipe.task();
        JeiClientState.State state = JeiClientState.state();
        boolean current = JeiRecipes.current(recipe.version());
        int status = current ? state.taskStatus(task) : -1;
        List<JeiDrawing.Line> lines = new ArrayList<>();
        JeiClientState.Definitions definitions = JeiClientState.definitions();
        List<Component> requirements = new ArrayList<>();
        if (!task.requires().isEmpty()) {
            requirements.add(Component.translatable("jei.antos.requirements.all"));
            for (String id : task.requires()) requirements.add(Component.translatable("jei.antos.requirement.task", JeiDrawing.taskTitle(definitions, id)));
        }
        if (!task.requiresAny().isEmpty()) {
            requirements.add(Component.translatable("jei.antos.requirements.any"));
            for (String id : task.requiresAny()) requirements.add(Component.translatable("jei.antos.requirement.task", JeiDrawing.taskTitle(definitions, id)));
        }
        switch (status) {
            case JeiClientState.TASK_CLAIMED -> lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.task.status.claimed"), Ui.TEXT_MUTED, List.of()));
            case JeiClientState.TASK_COMPLETE -> lines.add(new JeiDrawing.Line(Component.translatable(state.account()
                    ? "jei.antos.task.status.complete" : "jei.antos.task.status.done"), Ui.ACCENT, List.of()));
            case JeiClientState.TASK_AVAILABLE -> lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.task.status.available"), Ui.TEXT_STRONG, List.of()));
            default -> {
                Component first = !task.requires().isEmpty() ? JeiDrawing.taskTitle(definitions, task.requires().get(0))
                        : !task.requiresAny().isEmpty() ? JeiDrawing.taskTitle(definitions, task.requiresAny().get(0)) : null;
                lines.add(new JeiDrawing.Line(first == null ? Component.translatable("jei.antos.task.status.locked")
                        : Component.translatable("jei.antos.locked_by", first), Ui.ERROR, requirements));
            }
        }
        if (task.items().stream().anyMatch(item -> item.group().equals("choice") || item.group().startsWith("option:")))
            lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.task.choose_one"), Ui.TEXT, List.of()));
        if (current && !state.account()) lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.signed_out"), Ui.TEXT_MUTED,
                List.of(Component.translatable("jei.antos.signed_out.tooltip"))));
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }
}
