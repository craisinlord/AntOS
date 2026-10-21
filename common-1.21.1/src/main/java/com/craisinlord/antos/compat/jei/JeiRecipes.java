package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.client.JeiClientState;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

final class JeiRecipes {
    static final RecipeType<ListingRecipe> LISTINGS = RecipeType.create(AntOS.MODID, "antazon", ListingRecipe.class);
    static final RecipeType<SellRecipe> SELL = RecipeType.create(AntOS.MODID, "antazon_sell", SellRecipe.class);
    static final RecipeType<TaskRecipe> TASKS = RecipeType.create(AntOS.MODID, "task_rewards", TaskRecipe.class);
    private static final Tracked<ListingRecipe> TRACKED_LISTINGS = new Tracked<>(LISTINGS, JeiRecipes::listingVisible);
    private static final Tracked<SellRecipe> TRACKED_SELL = new Tracked<>(SELL, JeiRecipes::sellVisible);
    private static final Tracked<TaskRecipe> TRACKED_TASKS = new Tracked<>(TASKS, JeiRecipes::taskVisible);
    private static IJeiRuntime runtime;
    private static long version = -1L;
    private static boolean listening;

    private JeiRecipes() { }

    static void register(IRecipeRegistration registration) {
        if (!listening) {
            listening = true;
            JeiClientState.addListener(JeiRecipes::refresh);
        }
        runtime = null;
        rebuild(null);
        TRACKED_LISTINGS.register(registration);
        TRACKED_SELL.register(registration);
        TRACKED_TASKS.register(registration);
    }

    static void runtimeAvailable(IJeiRuntime available) {
        runtime = available;
        refresh();
    }

    static void runtimeUnavailable() {
        runtime = null;
        TRACKED_LISTINGS.forget();
        TRACKED_SELL.forget();
        TRACKED_TASKS.forget();
    }

    private static void refresh() {
        IJeiRuntime current = runtime;
        if (current == null) return;
        IRecipeManager manager = current.getRecipeManager();
        try {
            rebuild(manager);
            TRACKED_LISTINGS.reconcile(manager);
            TRACKED_SELL.reconcile(manager);
            TRACKED_TASKS.reconcile(manager);
        } catch (RuntimeException exception) {
            AntOS.LOGGER.error("Could not update AntOS JEI recipes", exception);
        }
    }

    private static void rebuild(IRecipeManager manager) {
        JeiClientState.Definitions definitions = JeiClientState.definitions();
        if (definitions.version() == version && manager != null) return;
        version = definitions.version();
        long current = version;
        TRACKED_LISTINGS.replace(manager, definitions.listings(), listing -> new ListingRecipe(current, listing));
        TRACKED_SELL.replace(manager, definitions.sell(), rule -> new SellRecipe(current, rule));
        TRACKED_TASKS.replace(manager, definitions.tasks(), task -> new TaskRecipe(current, task));
    }

    static boolean current(long recipeVersion) {
        return recipeVersion == JeiClientState.definitions().version() && JeiClientState.state().version() == recipeVersion;
    }

    private static boolean listingVisible(ListingRecipe recipe) {
        JeiClientState.Listing listing = recipe.listing();
        if (!listing.hidden()) return true;
        return current(recipe.version()) && !JeiClientState.state().listingLocked(listing);
    }

    private static boolean sellVisible(SellRecipe recipe) {
        if (!recipe.rule().hidden()) return true;
        return current(recipe.version()) && !JeiClientState.state().sellLocked(recipe.rule());
    }

    private static boolean taskVisible(TaskRecipe recipe) {
        return current(recipe.version()) && JeiClientState.state().taskStatus(recipe.task()) >= 0;
    }

    record ListingRecipe(long version, JeiClientState.Listing listing) { }

    record SellRecipe(long version, JeiClientState.Sell rule) { }

    record TaskRecipe(long version, JeiClientState.Task task) { }

    private static final class Tracked<T> {
        private final RecipeType<T> type;
        private final Predicate<T> visible;
        private List<Entry<T>> entries = List.of();

        private Tracked(RecipeType<T> type, Predicate<T> visible) {
            this.type = type;
            this.visible = visible;
        }

        private <S> void replace(IRecipeManager manager, List<S> sources, Function<S, T> factory) {
            if (manager != null) {
                List<T> shown = new ArrayList<>();
                for (Entry<T> entry : entries) if (entry.added && !entry.hidden) shown.add(entry.recipe);
                if (!shown.isEmpty()) manager.hideRecipes(type, shown);
            }
            List<Entry<T>> replaced = new ArrayList<>(sources.size());
            for (S source : sources) replaced.add(new Entry<>(factory.apply(source)));
            entries = replaced;
        }

        private void register(IRecipeRegistration registration) {
            List<T> shown = new ArrayList<>();
            for (Entry<T> entry : entries) {
                entry.added = false;
                entry.hidden = false;
                if (visible.test(entry.recipe)) {
                    entry.added = true;
                    shown.add(entry.recipe);
                }
            }
            if (!shown.isEmpty()) registration.addRecipes(type, shown);
        }

        private void forget() {
            for (Entry<T> entry : entries) {
                entry.added = false;
                entry.hidden = false;
            }
        }

        private void reconcile(IRecipeManager manager) {
            List<T> add = new ArrayList<>();
            List<T> hide = new ArrayList<>();
            List<T> unhide = new ArrayList<>();
            for (Entry<T> entry : entries) {
                boolean show = visible.test(entry.recipe);
                if (show && !entry.added) {
                    entry.added = true;
                    add.add(entry.recipe);
                } else if (show && entry.hidden) {
                    entry.hidden = false;
                    unhide.add(entry.recipe);
                } else if (!show && entry.added && !entry.hidden) {
                    entry.hidden = true;
                    hide.add(entry.recipe);
                }
            }
            if (!add.isEmpty()) manager.addRecipes(type, add);
            if (!unhide.isEmpty()) manager.unhideRecipes(type, unhide);
            if (!hide.isEmpty()) manager.hideRecipes(type, hide);
        }
    }

    private static final class Entry<T> {
        private final T recipe;
        private boolean added;
        private boolean hidden;

        private Entry(T recipe) {
            this.recipe = recipe;
        }
    }
}
