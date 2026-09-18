package com.craisinlord.antos.api.client.game;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Register games during client initialization. */
public final class ComputerGameRegistry {
    private static final Map<ResourceLocation, ComputerGame> GAMES = new LinkedHashMap<>();

    private ComputerGameRegistry() {}

    public static synchronized void register(ComputerGame game) {
        Objects.requireNonNull(game, "game");
        if (GAMES.putIfAbsent(game.id(), game) != null) {
            throw new IllegalArgumentException("Duplicate AntOS game ID: " + game.id());
        }
    }

    public static synchronized ComputerGame get(ResourceLocation id) {
        return GAMES.get(id);
    }

    public static synchronized Collection<ComputerGame> all() {
        return java.util.List.copyOf(GAMES.values());
    }
}
