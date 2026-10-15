package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.api.event.progress.ProgressType;
import dev.ftb.mods.ftbquests.api.event.progress.QuestProgressEvent;
import dev.ftb.mods.ftbquests.api.event.progress.TaskProgressEvent;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.ftb.mods.ftbteams.api.event.TeamPlayerLoggedInEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class FTBQuestsCompat {
    public static TaskType ANTOS_TASK_TYPE;
    public static RewardType ANTOS_ARCHIVE_REWARD_TYPE;
    public static RewardType ANTOS_TASK_REWARD_TYPE;

    private FTBQuestsCompat() { }

    public static void initialize() {
        AntOSFTBBridge.register();
        ANTOS_TASK_TYPE = TaskTypes.register(id("task"), AntOSTask::new,
                () -> Icon.getIcon("minecraft:item/book"));
        ANTOS_ARCHIVE_REWARD_TYPE = RewardTypes.register(id("unlock_archive"), AntOSArchiveReward::new,
                () -> Icon.getIcon("minecraft:item/enchanted_book"));
        ANTOS_TASK_REWARD_TYPE = RewardTypes.register(id("grant_task"), AntOSTaskReward::new,
                () -> Icon.getIcon("minecraft:item/writable_book"));
        com.craisinlord.antos.content.computer.ComputerTaskCompletionEvents.register(FTBQuestsCompat::completeMappedFTBTasks);
        com.craisinlord.antos.content.computer.ComputerAccountLoginEvents.register(FTBQuestsCompat::reconcileAntOSTasks);
    }

    public static void onTaskProgress(TaskProgressEvent.Data event) {
        if (event.type() != ProgressType.COMPLETED) return;
        var progress = event.progressData();
        recordForFTBMembers(progress.onlineMembers(), AntOSFTBBridge.TASK_COMPLETED, progress.object().getCodeString());
    }

    public static void onQuestProgress(QuestProgressEvent.Data event) {
        if (event.type() != ProgressType.COMPLETED) return;
        var progress = event.progressData();
        recordForFTBMembers(progress.onlineMembers(), AntOSFTBBridge.QUEST_COMPLETED, progress.object().getCodeString());
    }

    public static void completeMappedFTBTasks(ServerPlayer player, Identifier antosTaskId) {
        if (player == null || antosTaskId == null) return;
        var data = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.level().getServer());
        var account = AntOSFTBBridge.account(player, data);
        java.util.Set<java.util.UUID> antosMembers = new java.util.HashSet<>();
        if (account == null) antosMembers.add(player.getUUID());
        else {
            var team = data.team(account.accountId());
            if (team == null) antosMembers.add(account.accountId());
            else team.members().forEach(member -> antosMembers.add(member.accountId()));
        }
        java.util.List<Task> mappedTasks = null;
        for (ServerPlayer recipient : player.level().getServer().getPlayerList().getPlayers()) {
            var recipientAccount = AntOSFTBBridge.account(recipient, data);
            java.util.UUID recipientKey = recipientAccount == null ? recipient.getUUID() : recipientAccount.accountId();
            if (!antosMembers.contains(recipientKey)) continue;
            var file = ServerQuestFile.getInstance();
            if (file == null) continue;
            if (mappedTasks == null) mappedTasks = mappedTasks(file, antosTaskId);
            if (mappedTasks.isEmpty()) return;
            java.util.List<Task> matching = mappedTasks;
            file.getTeamData(recipient).ifPresent(teamData -> {
                for (Task task : matching) {
                    if (!teamData.isCompleted(task)) teamData.setProgress(task, task.getMaxProgress());
                }
            });
        }
    }

    private static java.util.List<Task> mappedTasks(ServerQuestFile file, Identifier antosTaskId) {
        java.util.List<Task> matching = new java.util.ArrayList<>();
        for (Task task : file.getAllTasks()) {
            if (task instanceof AntOSTask mapped && mapped.antosTaskId().equals(antosTaskId)) matching.add(task);
        }
        return matching;
    }

    public static void onTeamPlayerLoggedIn(TeamPlayerLoggedInEvent.Data event) {
        ServerPlayer player = event.player();
        if (player == null) return;
        var data = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.level().getServer());
        reconcileAntOSTasks(player, AntOSFTBBridge.account(player, data));
    }

    private static void reconcileAntOSTasks(ServerPlayer player,
                                             com.craisinlord.antos.content.computer.ComputerWorkspaceData.AccountInfo account) {
        if (account == null) return;
        var workspace = new com.craisinlord.antos.content.computer.AccountWorkspace(player, account);
        ServerQuestFile file = ServerQuestFile.getInstance();
        if (file != null) file.getTeamData(player).ifPresent(teamData -> {
            for (Task task : file.getAllTasks()) {
                if (task instanceof AntOSTask mapped
                        && com.craisinlord.antos.content.computer.ComputerTasks.isTaskComplete(player, workspace, mapped.antosTaskId())
                        && !teamData.isCompleted(task)) {
                    teamData.setProgress(task, task.getMaxProgress());
                }
            }
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("antos", path);
    }

    private static void recordForFTBMembers(java.util.Collection<ServerPlayer> members, Identifier eventType,
                                             String objectId) {
        java.util.Set<java.util.UUID> antosTeams = new java.util.HashSet<>();
        for (ServerPlayer player : members) {
            if (player == null) continue;
            var data = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.level().getServer());
            var account = AntOSFTBBridge.account(player, data);
            if (account == null) continue;
            var antosTeam = data.team(account.accountId());
            if (antosTeam != null && !antosTeams.add(antosTeam.id())) continue;
            AntOSFTBBridge.recordForEligiblePlayer(player, account, eventType, objectId);
        }
    }
}
