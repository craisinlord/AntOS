package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.computer.ArchiveRecipeData;
import com.craisinlord.antos.content.network.ArchiveRecipeNetworking;
import com.craisinlord.antos.content.network.ArchiveRecipeResultPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ArchiveRecipeClientState {
    private static final int MAX_ENTRIES = 1024;
    private static final Map<String, Optional<ArchiveRecipeData>> RECIPES = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();

    private ArchiveRecipeClientState() { }

    public static Optional<ArchiveRecipeData> request(String recipeId) {
        Optional<ArchiveRecipeData> cached = RECIPES.get(recipeId);
        if (cached != null) return cached;
        if (PENDING.size() + RECIPES.size() < MAX_ENTRIES && !PENDING.contains(recipeId)
                && ArchiveRecipeNetworking.request(recipeId)) PENDING.add(recipeId);
        return null;
    }

    public static void update(ArchiveRecipeResultPayload payload) {
        if (!PENDING.remove(payload.recipeId())) return;
        RECIPES.put(payload.recipeId(), payload.recipe());
    }

    public static void clear() {
        RECIPES.clear();
        PENDING.clear();
    }
}
