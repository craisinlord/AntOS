package com.craisinlord.antos.api.task;

import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskObjectiveRegistry {
    private static final Set<Identifier> TYPES = ConcurrentHashMap.newKeySet();

    private TaskObjectiveRegistry() { }

    public static boolean register(Identifier type) {
        if (type == null || type.getNamespace().equals("antos")) return false;
        return TYPES.add(type);
    }

    public static boolean isRegistered(Identifier type) {
        return TYPES.contains(type);
    }

    public static Set<Identifier> registeredTypes() {
        return Set.copyOf(TYPES);
    }
}
