package com.craisinlord.antos.api.client.archive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ArchiveEntityPreviewRegistry {
    private static final Map<ResourceLocation, Consumer<LivingEntity>> CONFIGURATORS = new ConcurrentHashMap<>();

    private ArchiveEntityPreviewRegistry() {}

    public static void register(ResourceLocation entityType, Consumer<LivingEntity> configurator) {
        CONFIGURATORS.put(Objects.requireNonNull(entityType), Objects.requireNonNull(configurator));
    }

    public static void configure(ResourceLocation entityType, LivingEntity entity) {
        Consumer<LivingEntity> configurator = CONFIGURATORS.get(entityType);
        if (configurator != null) configurator.accept(entity);
    }
}
