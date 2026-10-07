package com.craisinlord.antos.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.events.ObjectCompletedEvent;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.resources.ResourceLocation;
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
        ObjectCompletedEvent.TASK.register(event -> {
            String objectId = event.getTask().getCodeString();
            recordForFTBMembers(event.getOnlineMembers(), AntOSFTBBridge.TASK_COMPLETED, objectId);
            return dev.architectury.event.EventResult.pass();
        });
        ObjectCompletedEvent.QUEST.register(event -> {
            String objectId = event.getQuest().getCodeString();
            recordForFTBMembers(event.getOnlineMembers(), AntOSFTBBridge.QUEST_COMPLETED, objectId);
            return dev.architectury.event.EventResult.pass();
        });
        TeamEvent.PLAYER_LOGGED_IN.register(FTBQuestsCompat::reconcileAntOSTasks);
    }

    public static void completeMappedFTBTasks(ServerPlayer player, ResourceLocation antosTaskId) {
        if (player == null || antosTaskId == null) return;
        var data = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.server);
        var account = AntOSFTBBridge.account(player, data);
        java.util.Set<java.util.UUID> antosMembers = new java.util.HashSet<>();
        if (account == null) antosMembers.add(player.getUUID());
        else {
            var team = data.team(account.accountId());
            if (team == null) antosMembers.add(account.accountId());
            else team.members().forEach(member -> antosMembers.add(member.accountId()));
        }
        for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
            var recipientAccount = AntOSFTBBridge.account(recipient, data);
            java.util.UUID recipientKey = recipientAccount == null ? recipient.getUUID() : recipientAccount.accountId();
            if (!antosMembers.contains(recipientKey)) continue;
            ServerQuestFile.getInstance().ifPresent(file -> file.getTeamData(recipient).ifPresent(teamData -> {
                for (Task task : file.getAllTasks()) {
                    if (task instanceof AntOSTask mapped && mapped.antosTaskId().equals(antosTaskId)
                            && !teamData.isCompleted(task)) {
                        teamData.setProgress(task, task.getMaxProgress());
                    }
                }
            }));
        }
    }

    private static void reconcileAntOSTasks(dev.ftb.mods.ftbteams.api.event.PlayerLoggedInAfterTeamEvent event) {
        ServerPlayer player = event.getPlayer();
        var data = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.server);
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        if (account == null) account = data.faceIdAccount(player.getUUID());
        reconcileAntOSTasks(player, account);
    }

    private static void reconcileAntOSTasks(ServerPlayer player,
                                             com.craisinlord.antos.content.computer.ComputerWorkspaceData.AccountInfo account) {
        if (account == null) return;
        var workspace = new com.craisinlord.antos.content.computer.AccountWorkspace(player, account);
        ServerQuestFile.getInstance().ifPresent(file -> file.getTeamData(player).ifPresent(teamData -> {
            for (Task task : file.getAllTasks()) {
                if (task instanceof AntOSTask mapped
                        && com.craisinlord.antos.content.computer.ComputerTasks.isTaskComplete(player, workspace, mapped.antosTaskId())
                        && !teamData.isCompleted(task)) {
                    teamData.setProgress(task, task.getMaxProgress());
                }
            }
        }));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("antos", path);
    }

    private static void recordForFTBMembers(java.util.List<ServerPlayer> members, ResourceLocation eventType,
                                             String objectId) {
        java.util.Set<java.util.UUID> antosTeams = new java.util.HashSet<>();
        for (ServerPlayer player : members) {
            if (player == null) continue;
            var data = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.server);
            var account = AntOSFTBBridge.account(player, data);
            if (account == null) continue;
            var antosTeam = data.team(account.accountId());
            if (antosTeam != null && !antosTeams.add(antosTeam.id())) continue;
            AntOSFTBBridge.recordForEligiblePlayer(player, account, eventType, objectId);
        }
    }
}
