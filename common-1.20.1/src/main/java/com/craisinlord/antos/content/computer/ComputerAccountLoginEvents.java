package com.craisinlord.antos.content.computer;

import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public final class ComputerAccountLoginEvents {
    private static final CopyOnWriteArrayList<BiConsumer<ServerPlayer, ComputerWorkspaceData.AccountInfo>> LISTENERS = new CopyOnWriteArrayList<>();

    private ComputerAccountLoginEvents() { }

    public static void register(BiConsumer<ServerPlayer, ComputerWorkspaceData.AccountInfo> listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void fire(ServerPlayer player, ComputerWorkspaceData.AccountInfo account) {
        if (player == null || account == null) return;
        for (BiConsumer<ServerPlayer, ComputerWorkspaceData.AccountInfo> listener : LISTENERS) listener.accept(player, account);
    }
}
