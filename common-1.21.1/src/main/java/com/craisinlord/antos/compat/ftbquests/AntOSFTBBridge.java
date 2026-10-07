package com.craisinlord.antos.compat.ftbquests;

import com.craisinlord.antos.api.task.TaskObjectiveRegistry;
import com.craisinlord.antos.content.computer.AccountWorkspace;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AntOSFTBBridge {
    public static final ResourceLocation QUEST_COMPLETED = ResourceLocation.fromNamespaceAndPath("ftbquests", "quest_completed");
    public static final ResourceLocation TASK_COMPLETED = ResourceLocation.fromNamespaceAndPath("ftbquests", "task_completed");

    private AntOSFTBBridge() { }

    public static void register() {
        TaskObjectiveRegistry.register(QUEST_COMPLETED);
        TaskObjectiveRegistry.register(TASK_COMPLETED);
    }

    public static void onFTBTaskCompleted(ServerPlayer player, String ftbId) {
        recordForEligiblePlayer(player, TASK_COMPLETED, ftbId);
    }

    public static void onFTBQuestCompleted(ServerPlayer player, String ftbId) {
        recordForEligiblePlayer(player, QUEST_COMPLETED, ftbId);
    }

    public static boolean applyOrQueueGrant(ServerPlayer player, String action, ResourceLocation target) {
        if (player == null || target == null || !validTarget(action, target)) return false;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        ComputerWorkspaceData.AccountInfo account = account(player, data);
        if (account != null) return applyGrant(player, account, action, target);
        return data.queuePendingAntOSGrant(player.getUUID(), action, target);
    }

    public static void deliverPending(ServerPlayer player, ComputerWorkspaceData.AccountInfo account) {
        if (player == null || account == null) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        for (ComputerWorkspaceData.PendingAntOSGrant grant : data.pendingAntOSGrants(player.getUUID())) {
            if (applyGrant(player, account, grant.action(), grant.target())) {
                data.removePendingAntOSGrant(player.getUUID(), grant);
            }
        }
    }

    static void recordForEligiblePlayer(ServerPlayer player, ResourceLocation eventType, String rawId) {
        if (player == null || rawId == null || rawId.isBlank() || rawId.length() > 32) return;
        ResourceLocation target = ResourceLocation.tryParse("ftbquests:" + rawId.toLowerCase(java.util.Locale.ROOT));
        if (target == null) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        ComputerWorkspaceData.AccountInfo account = account(player, data);
        if (account == null) return;
        recordForEligiblePlayer(player, account, eventType, rawId);
    }

    static void recordForEligiblePlayer(ServerPlayer player, ComputerWorkspaceData.AccountInfo account,
                                        ResourceLocation eventType, String rawId) {
        if (player == null || account == null || rawId == null || rawId.isBlank() || rawId.length() > 32) return;
        ResourceLocation target = ResourceLocation.tryParse("ftbquests:" + rawId.toLowerCase(java.util.Locale.ROOT));
        if (target == null) return;
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        ComputerTasks.recordCustomEvent(player, workspace, eventType, target, 1);
        workspace.saveWorkspaceProgress();
    }

    static ComputerWorkspaceData.AccountInfo account(ServerPlayer player, ComputerWorkspaceData data) {
        ComputerWorkspaceData.AccountInfo session = AnternetAccountHandler.session(player);
        return session != null ? session : data.faceIdAccount(player.getUUID());
    }

    private static boolean applyGrant(ServerPlayer player, ComputerWorkspaceData.AccountInfo account,
                                      String action, ResourceLocation target) {
        if (!validTarget(action, target)) return true;
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        if (action.equals("unlock_archive")) {
            boolean changed = workspace.taskProgress().unlockArchiveEntry(target);
            if (changed) {
                workspace.saveWorkspaceProgress();
                ComputerWorkspaceData.access(player.server).mergeTeamProgress(account.accountId(), workspace.taskProgress());
            }
            return true;
        }
        ComputerTasks.grantTask(player, workspace, target);
        workspace.saveWorkspaceProgress();
        return true;
    }

    private static boolean validTarget(String action, ResourceLocation target) {
        boolean valid = action.equals("unlock_archive") && ComputerGuideData.entry(target) != null
                || action.equals("grant_task") && ComputerGuideData.tasks().stream().anyMatch(task -> task.id().equals(target));
        if (!valid) com.craisinlord.antos.AntOS.LOGGER.warn("Ignoring invalid FTB Quests AntOS reward target: {} {}", action, target);
        return valid;
    }
}
