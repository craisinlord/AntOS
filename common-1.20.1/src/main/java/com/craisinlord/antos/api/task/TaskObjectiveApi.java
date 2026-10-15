package com.craisinlord.antos.api.task;

import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.computer.ComputerTasks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class TaskObjectiveApi {
    private TaskObjectiveApi() { }

    /** Records matching objective progress. Call on the logical server. */
    public static boolean record(ServerPlayer player, ComputerBlockEntity computer,
                                 ResourceLocation type, ResourceLocation target, int amount) {
        if (player == null || computer == null || target == null || amount <= 0
                || !TaskObjectiveRegistry.isRegistered(type)) return false;
        ComputerTasks.recordCustomEvent(player, computer, type, target, amount);
        return true;
    }
}
