package com.craisinlord.antos.api.task;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskObjectiveRegistry {
    private static final Set<ResourceLocation> TYPES = ConcurrentHashMap.newKeySet();

    private TaskObjectiveRegistry() { }

    public static boolean register(ResourceLocation type) {
        if (type == null || type.getNamespace().equals("antos")) return false;
        return TYPES.add(type);
    }

    public static boolean isRegistered(ResourceLocation type) {
        return TYPES.contains(type);
    }

    public static Set<ResourceLocation> registeredTypes() {
        return Set.copyOf(TYPES);
    }
}
