package com.craisinlord.antos.content.computer;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public final class ComputerTaskCompletionEvents {
    private static final CopyOnWriteArrayList<BiConsumer<ServerPlayer, Identifier>> LISTENERS = new CopyOnWriteArrayList<>();

    private ComputerTaskCompletionEvents() { }

    public static void register(BiConsumer<ServerPlayer, Identifier> listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void fire(ServerPlayer player, Identifier taskId) {
        if (player == null || taskId == null) return;
        for (BiConsumer<ServerPlayer, Identifier> listener : LISTENERS) listener.accept(player, taskId);
    }
}
