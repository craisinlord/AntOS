package com.craisinlord.antos.api.archive;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom coordinate lookups for archive entries whose target is not a vanilla structure.
 * An entry opts in with {@code "locator": "<id>"} and {@code "dimension": "<dimension>"}.
 */
public final class ArchiveLocatorRegistry {
    private static final Map<ResourceLocation, Locator> LOCATORS = new ConcurrentHashMap<>();

    private ArchiveLocatorRegistry() {}

    public static void register(ResourceLocation id, Locator locator) {
        LOCATORS.put(Objects.requireNonNull(id), Objects.requireNonNull(locator));
    }

    public static Locator get(ResourceLocation id) {
        return LOCATORS.get(id);
    }

    @FunctionalInterface
    public interface Locator {
        /** Runs on the server thread. Returns the nearest target to {@code origin}, or null when none is in range. */
        BlockPos locate(ServerLevel level, BlockPos origin);
    }
}
