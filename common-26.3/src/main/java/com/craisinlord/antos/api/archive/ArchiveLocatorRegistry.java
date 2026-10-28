package com.craisinlord.antos.api.archive;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ArchiveLocatorRegistry {
    private static final Map<Identifier, Locator> LOCATORS = new ConcurrentHashMap<>();

    private ArchiveLocatorRegistry() {}

    public static void register(Identifier id, Locator locator) {
        LOCATORS.put(Objects.requireNonNull(id), Objects.requireNonNull(locator));
    }

    public static Locator get(Identifier id) {
        return LOCATORS.get(id);
    }

    @FunctionalInterface
    public interface Locator {
        BlockPos locate(ServerLevel level, BlockPos origin);
    }
}
