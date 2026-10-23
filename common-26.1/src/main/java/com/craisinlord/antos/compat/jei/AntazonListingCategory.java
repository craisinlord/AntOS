package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.content.client.JeiClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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

final class AntazonListingCategory implements IRecipeCategory<JeiRecipes.ListingRecipe> {
    private static final int HEIGHT = 108;
    private static final int ROW_TOP = 16;
    private static final int PAYMENT_ROWS = 3;
    private static final int OUTPUT_X = 108;
    private static final int OUTPUT_COLUMNS = 3;
    private static final int OUTPUT_SLOTS = 9;
    private static final int STATUS_TOP = 77;
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    AntazonListingCategory(IGuiHelper helper, ItemStack icon) {
        this.icon = helper.createDrawableItemStack(icon);
        this.arrow = helper.getRecipeArrow();
    }

    @Override public IRecipeType<JeiRecipes.ListingRecipe> getRecipeType() { return JeiRecipes.LISTINGS; }

    @Override public Component getTitle() { return Component.translatable("jei.antos.category.antazon"); }

    @Override public IDrawable getIcon() { return icon; }

    @Override public int getWidth() { return JeiDrawing.WIDTH; }

    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JeiRecipes.ListingRecipe recipe, IFocusGroup focuses) {
        JeiClientState.Listing listing = recipe.listing();
        List<JeiClientState.Payment> payments = listing.payments();
        for (int row = 0; row < Math.min(PAYMENT_ROWS, payments.size()); row++) {
            JeiClientState.Payment payment = payments.get(row);
            if (JeiDrawing.antcoins(payment)) continue;
            List<ItemStack> stacks = JeiDrawing.payment(payment);
            if (stacks.isEmpty()) continue;
            int option = row + 1;
            builder.addSlot(RecipeIngredientRole.INPUT, 5, ROW_TOP + row * 19).addItemStacks(stacks)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.antos.payment_option", option)));
        }
        List<List<ItemStack>> outputs = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (JeiClientState.Stack reward : listing.rewards()) {
            ItemStack stack = JeiDrawing.stack(reward.item(), reward.count());
            if (!stack.isEmpty()) {
                outputs.add(List.of(stack));
                notes.add("");
            }
        }
        if (!listing.variants().isEmpty()) {
            List<ItemStack> variants = new ArrayList<>();
            for (String variant : listing.variants()) {
                ItemStack stack = JeiDrawing.stack(variant, 1);
                if (!stack.isEmpty()) variants.add(stack);
            }
            if (listing.variantMode().equals("all")) {
                for (ItemStack variant : variants) {
                    outputs.add(List.of(variant));
                    notes.add("");
                }
            } else if (!variants.isEmpty()) {
                outputs.add(variants);
                notes.add(listing.variantMode().equals("random") ? "jei.antos.variant.random" : "jei.antos.variant.choose");
            }
        }
        if (outputs.size() > OUTPUT_SLOTS) {
            List<ItemStack> overflow = new ArrayList<>();
            for (int index = OUTPUT_SLOTS - 1; index < outputs.size(); index++) overflow.addAll(outputs.get(index));
            outputs = new ArrayList<>(outputs.subList(0, OUTPUT_SLOTS - 1));
            outputs.add(overflow);
            notes = new ArrayList<>(notes.subList(0, OUTPUT_SLOTS - 1));
            notes.add("jei.antos.more_rewards");
        }
        for (int index = 0; index < outputs.size(); index++) {
            int x = OUTPUT_X + index % OUTPUT_COLUMNS * 19;
            int y = ROW_TOP + index / OUTPUT_COLUMNS * 19;
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).addItemStacks(outputs.get(index));
            String note = notes.get(index);
            if (!note.isEmpty()) slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(note)));
        }
    }

    @Override
    public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder, JeiRecipes.ListingRecipe recipe, IFocusGroup focuses) {
        builder.addGuiEventListener(new JeiOpenButton(getWidth(), new com.craisinlord.antos.content.client.ArchiveLookup.Target("listing", recipe.listing().id())));
    }

    @Override
    public void draw(JeiRecipes.ListingRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        JeiClientState.Listing listing = recipe.listing();
        JeiDrawing.panel(g, getWidth(), HEIGHT);
        JeiDrawing.text(g, JeiDrawing.listingName(listing), 5, 5, JeiOpenButton.titleWidth(getWidth()), Ui.TEXT_STRONG);
        JeiOpenButton.draw(g, getWidth(), mouseX, mouseY);
        JeiClientState.Deal deal = deal(recipe);
        List<JeiClientState.Payment> payments = listing.payments();
        for (int row = 0; row < Math.min(PAYMENT_ROWS, payments.size()); row++) {
            JeiClientState.Payment payment = payments.get(row);
            int y = ROW_TOP + row * 19;
            JeiDrawing.slot(g, 5, y);
            if (JeiDrawing.antcoins(payment)) JeiDrawing.coin(g, 9, y + 4);
            int price = deal != null && row < deal.prices().size() ? deal.prices().get(row) : payment.amount();
            int x = 26;
            String priceText = JeiDrawing.number(price);
            g.text(JeiDrawing.font(), priceText, x, y + 4, deal != null ? Ui.ACCENT : Ui.TEXT_STRONG, false);
            x += JeiDrawing.font().width(priceText) + 3;
            if (deal != null && price != payment.amount()) JeiDrawing.struck(g, JeiDrawing.number(payment.amount()), x, y + 4, Ui.TEXT_DIM);
        }
        if (payments.size() > PAYMENT_ROWS)
            JeiDrawing.text(g, Component.translatable("jei.antos.more_payments", payments.size() - PAYMENT_ROWS), 70, ROW_TOP + 40, 36, Ui.TEXT_MUTED);
        arrow.draw(g, 80, ROW_TOP + 18);
        int rewards = slots.getSlotViews(RecipeIngredientRole.OUTPUT).size();
        for (int index = 0; index < rewards; index++) JeiDrawing.slot(g, OUTPUT_X + index % OUTPUT_COLUMNS * 19, ROW_TOP + index / OUTPUT_COLUMNS * 19);
        List<JeiDrawing.Line> lines = lines(recipe);
        for (int index = 0; index < lines.size(); index++) {
            JeiDrawing.Line line = lines.get(index);
            JeiDrawing.text(g, line.text(), 5, STATUS_TOP + index * 10, getWidth() - 10, line.color());
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, JeiRecipes.ListingRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (JeiOpenButton.tooltip(tooltip, getWidth(), mouseX, mouseY)) return;
        List<JeiClientState.Payment> payments = recipe.listing().payments();
        for (int row = 0; row < Math.min(PAYMENT_ROWS, payments.size()); row++) {
            JeiClientState.Payment payment = payments.get(row);
            if (!JeiDrawing.antcoins(payment) || !JeiDrawing.inside(mouseX, mouseY, 4, ROW_TOP + row * 19 - 1, 18, 18)) continue;
            JeiClientState.Deal deal = deal(recipe);
            int price = deal != null && row < deal.prices().size() ? deal.prices().get(row) : payment.amount();
            tooltip.add(JeiDrawing.currency(price));
            tooltip.add(Component.translatable("jei.antos.payment_option", row + 1));
            return;
        }
        List<JeiDrawing.Line> lines = lines(recipe);
        for (int index = 0; index < lines.size(); index++) {
            if (!JeiDrawing.inside(mouseX, mouseY, 4, STATUS_TOP + index * 10 - 1, getWidth() - 8, 10)) continue;
            tooltip.addAll(lines.get(index).tooltip());
            return;
        }
    }

    private static JeiClientState.Deal deal(JeiRecipes.ListingRecipe recipe) {
        if (!JeiRecipes.current(recipe.version())) return null;
        return JeiClientState.state().deal(recipe.listing());
    }

    private static List<JeiDrawing.Line> lines(JeiRecipes.ListingRecipe recipe) {
        JeiClientState.Listing listing = recipe.listing();
        JeiClientState.State state = JeiClientState.state();
        boolean current = JeiRecipes.current(recipe.version());
        List<JeiDrawing.Line> lines = new ArrayList<>();
        if (current && state.listingLocked(listing)) {
            lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.locked_by", JeiDrawing.firstRequirement(listing.unlock())), Ui.ERROR,
                    JeiDrawing.requirements(listing.unlock())));
        }
        JeiClientState.Deal deal = current ? state.deal(listing) : null;
        if (deal != null) {
            List<Component> tooltip = new ArrayList<>();
            if (deal.remaining() > 0) tooltip.add(Component.translatable("jei.antos.deal.remaining", JeiDrawing.number(deal.remaining())));
            if (deal.endsAt() > 0L) tooltip.add(Component.translatable("jei.antos.deal.ends", JeiDrawing.duration(deal.endsAt() - System.currentTimeMillis())));
            Component label = deal.label().isBlank() ? Component.translatable("jei.antos.deal.default") : Component.translatable(deal.label());
            lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.deal", label, deal.discount()), Ui.ACCENT, tooltip));
        }
        if (listing.rotating()) {
            boolean out = current && state.listingUnavailable(listing);
            lines.add(new JeiDrawing.Line(Component.translatable(out ? "jei.antos.not_in_rotation" : "jei.antos.in_rotation"),
                    out ? Ui.TEXT_DIM : Ui.TEXT, List.of(Component.translatable("jei.antos.rotation.tooltip"))));
        }
        if (listing.limit() > 0) lines.add(new JeiDrawing.Line(JeiDrawing.limit(listing.limit(), listing.limitReset()), Ui.TEXT, List.of()));
        else if (listing.stock() > 0) lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.stock", JeiDrawing.number(listing.stock())), Ui.TEXT, List.of()));
        if (current && !state.account()) lines.add(new JeiDrawing.Line(Component.translatable("jei.antos.signed_out"), Ui.TEXT_MUTED,
                List.of(Component.translatable("jei.antos.signed_out.tooltip"))));
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
    }
}
