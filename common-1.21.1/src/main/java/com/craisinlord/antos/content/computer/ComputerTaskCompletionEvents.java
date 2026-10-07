package com.craisinlord.antos.content.computer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public final class ComputerTaskCompletionEvents {
    private static final CopyOnWriteArrayList<BiConsumer<ServerPlayer, ResourceLocation>> LISTENERS = new CopyOnWriteArrayList<>();

    private ComputerTaskCompletionEvents() { }

    public static void register(BiConsumer<ServerPlayer, ResourceLocation> listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void fire(ServerPlayer player, ResourceLocation taskId) {
        if (player == null || taskId == null) return;
        for (BiConsumer<ServerPlayer, ResourceLocation> listener : LISTENERS) listener.accept(player, taskId);
    }
}
