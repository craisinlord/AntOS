package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.api.task.TaskObjectiveRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.List;
import java.util.Map;

public final class ComputerTasks {
    private static final java.util.Set<String> OVERSIZED_TASK_STATES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<ResourceLocation> OVERSIZED_ITEM_TAGS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<ResourceLocation> MISSING_ITEM_TAGS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ComputerTasks() { }
    public static boolean grantTask(ServerPlayer player, ComputerWorkspace computer, ResourceLocation taskId) {
        ComputerGuideData.Task task = task(taskId);
        if (task == null) return false;
        boolean changed = computer.taskProgress().grant(taskId);
        evaluateProgress(player, computer, computer.taskProgress(), changed);
        return changed;
    }

    public static boolean forceCompleteTask(ServerPlayer player, ComputerWorkspace computer, ResourceLocation taskId) {
        ComputerGuideData.Task task = task(taskId);
        if (task == null) return false;
        ComputerTaskProgress progress = computer.taskProgress();
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) {
            progress.mergeFrom(ComputerWorkspaceData.access(player.server).progress(workspaceId));
        }
        if (progress.isComplete(taskId)) return false;
        progress.grant(taskId);
        for (ComputerGuideData.Objective objective : task.objectives()) {
            progress.setObjectiveCount(taskId, objective.id(), objective.count());
        }
        boolean newlyCompleted = progress.markComplete(taskId);
        if (newlyCompleted) {
            unlockArchiveRewards(computer, task);
        }
        evaluateProgress(player, computer, progress, true);
        if (newlyCompleted) ComputerTaskCompletionEvents.fire(player, taskId);
        return progress.isComplete(taskId);
    }

    public static boolean claimTaskRewards(ServerPlayer player, ComputerWorkspace computer, ResourceLocation taskId) {
        if (player == null || computer == null || taskId == null) return false;
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        if (account == null) return false;
        ComputerGuideData.Task task = task(taskId);
        if (task == null || !hasClaimableRewards(task) || !isTaskComplete(player, computer, taskId)) return false;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        if (data.hasClaimedTaskReward(account.accountId(), taskId)) return false;
        if (!data.markTaskRewardClaimed(account.accountId(), taskId)) return false;
        deliverRewards(player, computer, task);
        computer.saveWorkspaceProgress();
        return true;
    }

    private static boolean hasClaimableRewards(ComputerGuideData.Task task) {
        return task != null && task.rewards().stream().anyMatch(reward -> !reward.type().equals("archive"));
    }

    private static boolean isKillObjective(ComputerGuideData.Objective objective) {
        String type = objective.type().equals("stat") ? objective.statType() : objective.type();
        return type.equals("entity_killed") || type.equals("entity_killed_by");
    }

    public static boolean setEventObjectiveProgress(ServerPlayer player, ComputerWorkspace computer,
                                                     ResourceLocation taskId, String objectiveId, int count) {
        ComputerGuideData.Task task = task(taskId);
        if (task == null) return false;
        ComputerGuideData.Objective objective = task.objectives().stream()
                .filter(candidate -> candidate.id().equals(objectiveId)).findFirst().orElse(null);
        if (objective == null || !isEventDrivenObjective(objective.type())) return false;
        int safeCount = Math.max(0, Math.min(objective.count(), count));
        boolean changed = computer.taskProgress().setObjectiveCount(taskId, objectiveId, safeCount);
        evaluateProgress(player, computer, computer.taskProgress(), changed);
        return true;
    }

    public enum LocationResult { DONE, UNAVAILABLE }

    /** Fills a location objective for a player who is standing in its target; see {@link ComputerLocationObjectives}. */
    public static LocationResult completeLocationObjective(ServerPlayer player, ComputerWorkspace computer,
                                                           ResourceLocation taskId, String objectiveId) {
        ComputerGuideData.Task task = task(taskId);
        ComputerGuideData.Objective objective = task == null ? null : task.objectives().stream()
                .filter(candidate -> candidate.id().equals(objectiveId)).findFirst().orElse(null);
        if (objective == null) return LocationResult.DONE;
        ComputerTaskProgress progress = computer.taskProgress();
        var account = accountForProgress(player);
        if (account != null) progress.mergeFrom(ComputerWorkspaceData.access(player.server).effectiveProgress(account.accountId(), progress));
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) progress.mergeFrom(ComputerWorkspaceData.access(player.server).progress(workspaceId));
        if (progress.isComplete(taskId) || progress.objectiveCount(taskId, objectiveId) >= objective.count()) return LocationResult.DONE;
        for (ComputerGuideData.Task candidate : ComputerGuideData.tasks()) {
            if (candidate.requires().isEmpty() && candidate.availability().equals("automatic")) progress.grant(candidate.id());
        }
        if (!available(task, progress)) return LocationResult.UNAVAILABLE;
        boolean changed = progress.setObjectiveCount(taskId, objectiveId, objective.count());
        evaluateProgress(player, computer, progress, changed);
        return LocationResult.DONE;
    }

    public static boolean resetTaskProgress(ComputerWorkspace computer) {
        boolean changed = computer.taskProgress().clearTaskProgress();
        if (changed) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
        return changed;
    }

    private static ComputerGuideData.Task task(ResourceLocation id) {
        return ComputerGuideData.task(id);
    }

    private static ComputerWorkspaceData.AccountInfo accountForProgress(ServerPlayer player) {
        if (player == null) return null;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        var session = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        return session != null ? session : data.faceIdAccount(player.getUUID());
    }

    public static boolean isTaskComplete(ServerPlayer player, ComputerWorkspace computer, ResourceLocation taskId) {
        if (player == null || computer == null || taskId == null || task(taskId) == null) return false;
        ComputerTaskProgress progress = computer.taskProgress();
        var account = accountForProgress(player);
        if (account != null) progress.mergeFrom(ComputerWorkspaceData.access(player.server).effectiveProgress(account.accountId(), progress));
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) progress.mergeFrom(ComputerWorkspaceData.access(player.server).progress(workspaceId));
        return progress.isComplete(taskId);
    }

    public static void synchronizeSharedProgress(ServerPlayer player, ComputerWorkspace computer) {
        if (player == null || computer == null) return;
        var account = accountForProgress(player);
        if (account == null) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        boolean changed = computer.taskProgress().mergeFrom(data.effectiveProgress(account.accountId(), computer.taskProgress()));
        data.mergeTeamProgress(account.accountId(), computer.taskProgress());
        if (changed) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
    }

    public static String updateAndEncode(ServerPlayer player, ComputerWorkspace computer) {
        return updateAndEncode(player, computer, "");
    }

    /**
     * Encodes the Tasks snapshot in two halves: static definitions (cached until a data reload) and per-player state.
     * {@code known} is the client's "definitionsHash\0stateHash"; any half the client already holds is left out.
     */
    public static String updateAndEncode(ServerPlayer player, ComputerWorkspace computer, String known) {
        ComputerTaskProgress progress = computer.taskProgress();
        evaluateProgress(player, computer, progress, false);
        String[] knownHashes = (known == null ? "" : known).split("\u0000", -1);
        String knownDefinitions = knownHashes.length > 0 ? knownHashes[0] : "";
        String knownState = knownHashes.length > 1 ? knownHashes[1] : "";
        EncodedDefinitions definitions = definitions();
        String state = encodeState(player, progress);
        String stateHash = hash(state);
        boolean sendDefinitions = !knownDefinitions.equals(definitions.hash());
        boolean sendState = sendDefinitions || !knownState.equals(stateHash);
        StringBuilder encoded = new StringBuilder(64 + (sendDefinitions ? definitions.json().length() : 0) + (sendState ? state.length() : 0));
        encoded.append("{\"defs_hash\":\"").append(definitions.hash()).append("\",\"state_hash\":\"").append(stateHash).append('"');
        if (sendDefinitions) encoded.append(",\"definitions\":").append(definitions.json());
        if (sendState) encoded.append(",\"state\":").append(state);
        encoded.append('}');
        if (encoded.length() + 64 > com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS) {
            String workspaceKey = String.valueOf(computer.workspaceId());
            if (OVERSIZED_TASK_STATES.add(workspaceKey)) AntOS.LOGGER.error("Task snapshot for Anternet workspace {} exceeds the network payload limit; reduce task/objective definitions",
                    workspaceKey);
            JsonObject error = new JsonObject(); error.add("tasks", new JsonArray()); error.addProperty("error", "task_snapshot_too_large");
            return error.toString();
        }
        return encoded.toString();
    }

    private static String hash(String value) {
        return Integer.toHexString(value.hashCode()) + "-" + Integer.toHexString(value.length());
    }

    private record EncodedDefinitions(List<ComputerGuideData.Task> tasks, List<ComputerGuideData.TaskCategory> categories,
                                      List<ComputerGuideData.TaskGroup> groups, String json, String hash) { }

    private static volatile EncodedDefinitions encodedDefinitions;

    /** Definitions only change on a data reload, which swaps the cached sorted lists, so they are encoded once per reload. */
    private static EncodedDefinitions definitions() {
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        List<ComputerGuideData.TaskCategory> categories = ComputerGuideData.taskCategories();
        List<ComputerGuideData.TaskGroup> groups = ComputerGuideData.taskGroups();
        EncodedDefinitions cached = encodedDefinitions;
        if (cached != null && cached.tasks() == tasks && cached.categories() == categories && cached.groups() == groups) return cached;
        JsonObject root = new JsonObject(); JsonArray rows = new JsonArray();
        for (ComputerGuideData.Task task : tasks) {
            JsonObject row = new JsonObject(); row.addProperty("id", task.id().toString());
            row.addProperty("title", task.titleKey()); row.addProperty("description", task.descriptionKey());
            row.addProperty("program", task.program()); row.addProperty("category", task.category());
            row.addProperty("x", task.x()); row.addProperty("y", task.y());
            row.addProperty("has_rewards", !task.rewards().isEmpty());
            String iconItem = task.iconItem();
            String iconEntity = iconItem.isBlank() ? task.iconEntity() : "";
            if (iconItem.isBlank() && iconEntity.isBlank()) for (ComputerGuideData.Objective objective : task.objectives()) {
                if (objective.type().equals("item")) { iconItem = objective.target(); break; }
                if (isKillObjective(objective)) { iconEntity = objective.target(); break; }
            }
            if (iconItem.isBlank() && iconEntity.isBlank()) {
                for (ComputerGuideData.TaskReward reward : task.rewards()) {
                    if (reward.type().equals("item") && reward.itemId() != null) { iconItem = reward.itemId().toString(); break; }
                }
            }
            if (iconItem.isBlank() && iconEntity.isBlank()) iconItem = "minecraft:paper";
            row.addProperty("icon_item", iconItem);
            row.addProperty("icon_entity", iconEntity);
            row.addProperty("green_tint", task.greenTint());
            row.addProperty("render_mob_from_spawn_egg", task.renderMobFromSpawnEgg());
            JsonArray rewards = new JsonArray();
            for (ComputerGuideData.TaskReward reward : task.rewards()) {
                JsonObject rewardRow = new JsonObject();
                rewardRow.addProperty("type", reward.type());
                rewardRow.addProperty("item", reward.itemId() == null ? "" : reward.itemId().toString());
                rewardRow.addProperty("count", reward.count());
                rewardRow.addProperty("experience", reward.experiencePoints());
                rewardRow.addProperty("target", reward.target() == null ? "" : reward.target().toString());
                rewardRow.addProperty("subject", reward.subject());
                rewards.add(rewardRow);
            }
            row.add("rewards", rewards);
            JsonArray prerequisites = new JsonArray(); task.requires().forEach(id -> prerequisites.add(id.toString())); row.add("requires", prerequisites);
            JsonArray archiveEntries = new JsonArray(); task.archiveEntries().forEach(id -> archiveEntries.add(id.toString())); row.add("archive_entries", archiveEntries);
            int total = 0;
            for (ComputerGuideData.Objective objective : task.objectives()) if (!objective.optional()) total++;
            row.addProperty("total", total);
            JsonArray objectives = new JsonArray();
            for (ComputerGuideData.Objective objective : task.objectives()) {
                JsonObject objectiveRow = new JsonObject(); objectiveRow.addProperty("id", objective.id());
                objectiveRow.addProperty("description", objective.descriptionKey());
                objectiveRow.addProperty("count", objective.count()); objectiveRow.addProperty("optional", objective.optional());
                objectiveRow.addProperty("item", objective.type().equals("item") ? objective.target() : "");
                objectives.add(objectiveRow);
            }
            row.add("objectives", objectives);
            rows.add(row);
        }
        root.add("tasks", rows);
        JsonArray categoryRows = new JsonArray();
        for (ComputerGuideData.TaskCategory category : categories) {
            JsonObject categoryRow = new JsonObject();
            categoryRow.addProperty("id", category.id().toString());
            categoryRow.addProperty("title", category.titleKey());
            categoryRow.addProperty("icon_item", category.iconItem());
            categoryRow.addProperty("icon_entity", category.iconEntity());
            categoryRow.addProperty("sort_order", category.sortOrder());
            categoryRow.addProperty("group", category.group() == null ? "" : category.group().toString());
            categoryRow.addProperty("green_tint", category.greenTint());
            categoryRow.addProperty("render_mob_from_spawn_egg", category.renderMobFromSpawnEgg());
            categoryRows.add(categoryRow);
        }
        root.add("categories", categoryRows);
        JsonArray groupRows = new JsonArray();
        for (ComputerGuideData.TaskGroup group : groups) {
            JsonObject groupRow = new JsonObject();
            groupRow.addProperty("id", group.id().toString());
            groupRow.addProperty("title", group.titleKey());
            groupRow.addProperty("icon_item", group.iconItem());
            groupRow.addProperty("icon_entity", group.iconEntity());
            groupRow.addProperty("sort_order", group.sortOrder());
            groupRow.addProperty("collapsed", group.collapsed());
            groupRow.addProperty("green_tint", group.greenTint());
            groupRow.addProperty("render_mob_from_spawn_egg", group.renderMobFromSpawnEgg());
            groupRows.add(groupRow);
        }
        root.add("groups", groupRows);
        String json = root.toString();
        EncodedDefinitions result = new EncodedDefinitions(tasks, categories, groups, json, hash(json));
        encodedDefinitions = result;
        return result;
    }

    /** Per-player state; task rows follow the definition order so the client can pair them by index. */
    private static String encodeState(ServerPlayer player, ComputerTaskProgress progress) {
        var account = accountForProgress(player);
        ComputerWorkspaceData accountData = ComputerWorkspaceData.access(player.server);
        JsonObject root = new JsonObject(); JsonArray rows = new JsonArray();
        var team = account == null ? null : accountData.team(account.accountId());
        if (team == null) root.add("team", com.google.gson.JsonNull.INSTANCE);
        else {
            JsonObject teamRow = new JsonObject();
            teamRow.addProperty("id", team.id().toString());
            teamRow.addProperty("owner", team.ownerId().equals(account.accountId()));
            teamRow.addProperty("owner_name", team.ownerName());
            JsonArray members = new JsonArray();
            for (var member : team.members()) members.add(member.displayUsername());
            teamRow.add("members", members);
            root.add("team", teamRow);
        }
        JsonArray invites = new JsonArray();
        if (account != null) for (var invite : accountData.invitations(account.accountId())) {
            JsonObject inviteRow = new JsonObject(); inviteRow.addProperty("id", invite.id().toString());
            inviteRow.addProperty("owner_name", invite.ownerName()); inviteRow.addProperty("inviter_name", invite.inviterName());
            invites.add(inviteRow);
        }
        root.add("team_invites", invites);
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            JsonObject row = new JsonObject(); row.addProperty("id", task.id().toString());
            boolean complete = progress.isComplete(task.id());
            boolean rewardsClaimable = hasClaimableRewards(task);
            boolean claimed = rewardsClaimable && account != null && accountData.hasClaimedTaskReward(account.accountId(), task.id());
            row.addProperty("complete", complete); row.addProperty("visible", visible(task, progress));
            row.addProperty("available", complete || available(task, progress));
            row.addProperty("claimable", complete && rewardsClaimable && account != null && !claimed);
            row.addProperty("claimed", claimed);
            int done = 0;
            JsonArray objectiveProgress = new JsonArray();
            for (ComputerGuideData.Objective objective : task.objectives()) {
                ObjectiveView view = objectiveView(player, progress, task, objective);
                if (view.complete() && !objective.optional()) done++;
                if (objective.type().equals("item_tag") && objective.tagMode().equals("all")) {
                    JsonObject objectiveState = new JsonObject();
                    objectiveState.addProperty("progress", view.progress());
                    objectiveState.addProperty("goal", view.goal());
                    objectiveState.addProperty("complete", view.complete());
                    objectiveProgress.add(objectiveState);
                } else objectiveProgress.add(view.progress());
            }
            row.addProperty("done", done);
            row.add("progress", objectiveProgress);
            rows.add(row);
        }
        root.add("tasks", rows);
        return root.toString();
    }

    public static void recordEvent(ServerPlayer player, ComputerWorkspace computer, String eventType, ResourceLocation target) {
        recordEvent(player, computer, eventType, target, 1);
    }

    public static void recordCustomEvent(ServerPlayer player, ComputerWorkspace computer, ResourceLocation type,
                                         ResourceLocation target, int amount) {
        if (type == null || !TaskObjectiveRegistry.isRegistered(type)) return;
        recordEvent(player, computer, type.toString(), target, amount);
    }

    private static void recordEvent(ServerPlayer player, ComputerWorkspace computer, String eventType,
                                    ResourceLocation target, int amount) {
        if (player == null || computer == null || target == null || amount <= 0) return;
        ComputerTaskProgress progress = computer.taskProgress();
        var account = accountForProgress(player);
        if (account != null) {
            progress.mergeFrom(ComputerWorkspaceData.access(player.server)
                    .effectiveProgress(account.accountId(), progress));
        }
        boolean eventChanged = false;
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (task.requires().isEmpty() && task.availability().equals("automatic")) progress.grant(task.id());
        }
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (progress.isComplete(task.id()) || !available(task, progress)) continue;
            for (ComputerGuideData.Objective objective : task.objectives()) {
                boolean targetMatches = eventType.startsWith("ftbquests:")
                        ? target.getPath().equalsIgnoreCase(objective.target())
                        : target.toString().equals(objective.target());
                if (objective.type().equals(eventType) && targetMatches) {
                    long updated = progress.objectiveCount(task.id(), objective.id()) + (long) amount;
                    eventChanged |= progress.setObjectiveCount(task.id(), objective.id(), (int) Math.min(1_000_000L, updated));
                }
            }
        }
        evaluateProgress(player, computer, progress, eventChanged);
    }

    private static void evaluateProgress(ServerPlayer player, ComputerWorkspace computer, ComputerTaskProgress progress,
                                         boolean alreadyChanged) {
        boolean progressChanged = alreadyChanged;
        var account = accountForProgress(player);
        boolean sharedTeamProgress = account != null
                && ComputerWorkspaceData.access(player.server).team(account.accountId()) != null;
        if (account != null) {
            progressChanged |= progress.mergeFrom(ComputerWorkspaceData.access(player.server)
                    .effectiveProgress(account.accountId(), progress));
        }
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) {
            progressChanged |= progress.mergeFrom(ComputerWorkspaceData.access(player.server).progress(workspaceId));
        }
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (task.requires().isEmpty() && task.availability().equals("automatic")) progressChanged |= progress.grant(task.id());
        }
        boolean changed;
        List<ResourceLocation> newlyCompleted = new java.util.ArrayList<>();
        java.util.Map<ComputerGuideData.Objective, Integer> liveProgress = new java.util.IdentityHashMap<>();
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        int passes = 0;
        do {
            changed = false;
            for (ComputerGuideData.Task task : tasks) {
                if (progress.isComplete(task.id()) || !available(task, progress)) continue;
                boolean requiredDone = true;
                boolean anyRequired = false;
                for (ComputerGuideData.Objective objective : task.objectives()) {
                    boolean objectiveComplete = progress.isObjectiveSatisfied(task.id(), objective.id());
                    if (!objectiveComplete) {
                        boolean allTag = objective.type().equals("item_tag") && objective.tagMode().equals("all");
                        boolean priorItemProgressComplete = objective.type().equals("item") && objective.consume()
                                && progress.objectiveCount(task.id(), objective.id()) >= objective.count();
                        ObjectiveView view = allTag
                                ? evaluateAllTagObjective(player, progress, task, objective, sharedTeamProgress, objective.consume())
                                : evaluateObjective(player, progress, task, objective, liveProgress, sharedTeamProgress);
                        if (view.changed()) {
                            changed = true;
                            progressChanged = true;
                        }
                        if (view.consumedItems()) liveProgress.clear();
                        if (progress.setObjectiveCount(task.id(), objective.id(), view.progress())) {
                            changed = true;
                            progressChanged = true;
                        }
                        objectiveComplete = view.complete();
                        if (objectiveComplete && objective.consume() && !allTag && !priorItemProgressComplete) {
                            if (progress.isObjectiveConsumed(task.id(), objective.id())) {
                                objectiveComplete = true;
                            } else {
                                boolean liveComplete = evaluateObjective(player, progress, task, objective, liveProgress, false).liveComplete();
                                if (!liveComplete || !consumeObjectiveItems(player, objective)) objectiveComplete = false;
                                else {
                                    if (progress.markObjectiveConsumed(task.id(), objective.id())) {
                                        changed = true;
                                        progressChanged = true;
                                    }
                                    liveProgress.clear();
                                }
                            }
                        }
                        if (objectiveComplete && progress.markObjectiveSatisfied(task.id(), objective.id())) {
                            changed = true;
                            progressChanged = true;
                            if (progress.setObjectiveCount(task.id(), objective.id(), objectiveProgressGoal(player, objective))) {
                                changed = true;
                                progressChanged = true;
                            }
                        }
                    }
                    if (!objective.optional()) { anyRequired = true; requiredDone &= objectiveComplete; }
                }
                if ((!anyRequired || requiredDone) && progress.markComplete(task.id())) {
                    changed = true;
                    progressChanged = true;
                    unlockArchiveRewards(computer, task);
                    newlyCompleted.add(task.id());
                }
            }
        } while (changed && ++passes < tasks.size() + 1);
        if (progressChanged) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
        if (account != null) ComputerWorkspaceData.access(player.server).mergeTeamProgress(account.accountId(), progress);
        for (ResourceLocation taskId : newlyCompleted) ComputerTaskCompletionEvents.fire(player, taskId);
    }

    private static void unlockArchiveRewards(ComputerWorkspace computer, ComputerGuideData.Task task) {
        for (ComputerGuideData.TaskReward reward : task.rewards()) {
            if (!reward.type().equals("archive")) continue;
            if (ComputerGuideData.entry(reward.target()) != null) computer.taskProgress().unlockArchiveEntry(reward.target());
            else AntOS.LOGGER.warn("Task {} rewards missing Archive entry {}", task.id(), reward.target());
        }
    }

    private static void deliverRewards(ServerPlayer player, ComputerWorkspace computer, ComputerGuideData.Task task) {
        if (task.rewards().isEmpty()) return;
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (ComputerGuideData.TaskReward reward : task.rewards()) {
            switch (reward.type()) {
                case "item" -> {
                    Item item = BuiltInRegistries.ITEM.get(reward.itemId());
                    if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
                    int remaining = reward.count();
                    while (remaining > 0) {
                        int amount = Math.min(item.getDefaultMaxStackSize(), remaining);
                        stacks.add(new ItemStack(item, amount));
                        remaining -= amount;
                    }
                }
                case "experience" -> player.giveExperiencePoints(reward.experiencePoints());
                case "experience_levels" -> player.giveExperienceLevels(reward.count());
                case "advancement" -> {
                    var advancement = player.serverLevel().getServer().getAdvancements().get(reward.target());
                    if (advancement == null) {
                        AntOS.LOGGER.warn("Task {} rewards missing advancement {}", task.id(), reward.target());
                    } else {
                        advancement.value().criteria().keySet().forEach(criterion ->
                                player.getAdvancements().award(advancement, criterion));
                    }
                }
                case "effect" -> {
                    var effect = BuiltInRegistries.MOB_EFFECT.getHolder(reward.target()).orElse(null);
                    if (effect == null) {
                        AntOS.LOGGER.warn("Task {} rewards missing status effect {}", task.id(), reward.target());
                    } else {
                        player.addEffect(new MobEffectInstance(effect, reward.durationTicks(), reward.amplifier()));
                    }
                }
                case "archive" -> {
                }
                case "mail" -> deliverMailReward(player, task, reward);
            }
        }
        if (stacks.isEmpty()) return;
        dropRewardCrate(player, stacks);
    }

    /** Drops an Antazon-style reward crate next to the player, with the usual "incoming" announcement. */
    public static void dropRewardCrate(ServerPlayer player, List<ItemStack> stacks) {
        if (player == null || stacks == null || stacks.isEmpty()) return;
        var level = player.serverLevel();
        BlockPos landing = rewardLandingSpots(player, 1).stream().findFirst()
                .orElse(player.blockPosition().relative(player.getDirection(), 1));
        RewardDropEntity drop = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), level);
        drop.setPos(landing.getX() + 0.5D, RewardDropEntity.spawnHeight(level, landing), landing.getZ() + 0.5D);
        drop.setRewards(stacks, player);
        RewardDropEntity.announceIncoming(player);
        level.addFreshEntity(drop);
    }

    private static void deliverMailReward(ServerPlayer player, ComputerGuideData.Task task, ComputerGuideData.TaskReward reward) {
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress recipient = data.deliveryAddressFor(player);
        if (recipient == null) {
            AntOS.LOGGER.warn("Task {} mail reward could not be delivered because player {} has no Antmail profile",
                    task.id(), player.getGameProfile().getName());
            return;
        }
        try {
            AntmailAddress sender = AntmailAddress.ofUsername(reward.sender());
            AntmailMessage message = AntmailMessage.create(sender, recipient, reward.subject(), reward.body(),
                    player.serverLevel().getGameTime(), List.of(), task.id().toString());
            var result = data.deliver(player.server, message);
            if (result.status() == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.MAILBOX_FULL
                    || result.status() == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.FAILED) {
                AntOS.LOGGER.warn("Task {} mail reward could not be delivered to {}: {}", task.id(), recipient, result.detail());
            }
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Task {} has an invalid mail reward", task.id(), exception);
        }
    }

    private static List<BlockPos> rewardLandingSpots(ServerPlayer player, int stackCount) {
        var level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        List<BlockPos> spots = new java.util.ArrayList<>();
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,2},{2,-2},{-2,-2},{4,0},{-4,0},{0,4},{0,-4}};
        int wanted = Math.max(1, Math.min(12, (stackCount + 26) / 27));
        for (int[] offset : offsets) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                BlockPos candidate = origin.offset(offset[0], yOffset, offset[1]);
                BlockPos floor = candidate.below();
                if (level.isInWorldBounds(candidate) && level.getBlockState(candidate).canBeReplaced()
                        && !level.getBlockState(candidate).getFluidState().isSource()
                        && level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)
                        && spots.stream().noneMatch(existing -> existing.distSqr(candidate) < 4.0D)) {
                    spots.add(candidate);
                    break;
                }
            }
            if (spots.size() >= wanted) break;
        }
        return spots;
    }

    private static boolean available(ComputerGuideData.Task task, ComputerTaskProgress progress) {
        if (progress.isGranted(task.id())) return true;
        if (task.requires().isEmpty()) return task.availability().equals("automatic");
        for (ResourceLocation required : task.requires()) if (!progress.isComplete(required)) return false;
        return true;
    }

    private static boolean visible(ComputerGuideData.Task task, ComputerTaskProgress progress) {
        if (task.invisibleUntilCompleted() && !progress.isComplete(task.id())) return false;
        if (task.hideUntilDependenciesComplete()) {
            for (ResourceLocation required : task.requires()) if (!progress.isComplete(required)) return false;
        }
        return true;
    }

    private static int objectiveProgress(ServerPlayer player, ComputerGuideData.Objective objective) {
        try {
            ResourceLocation target = ResourceLocation.parse(objective.target());
            if (objective.type().equals("item")) {
                Item item = BuiltInRegistries.ITEM.get(target);
                return player.getInventory().countItem(item);
            }
            if (objective.type().equals("item_tag")) {
                List<Item> items = itemTagItems(player, target);
                int total = 0;
                for (Item item : items) total += player.getInventory().countItem(item);
                return total;
            }
            if (objective.type().equals("advancement")) {
                var advancement = player.serverLevel().getServer().getAdvancements().get(target);
                return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone() ? objective.count() : 0;
            }
            if (objective.type().equals("stat")) {
                ResourceLocation id = ResourceLocation.parse(objective.target());
                Stat<?> stat = switch (objective.statType()) {
                    case "custom" -> Stats.CUSTOM.get(id);
                    case "block_mined" -> Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.get(id));
                    case "item_crafted" -> Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.get(id));
                    case "item_used" -> Stats.ITEM_USED.get(BuiltInRegistries.ITEM.get(id));
                    case "item_broken" -> Stats.ITEM_BROKEN.get(BuiltInRegistries.ITEM.get(id));
                    case "item_picked_up" -> Stats.ITEM_PICKED_UP.get(BuiltInRegistries.ITEM.get(id));
                    case "item_dropped" -> Stats.ITEM_DROPPED.get(BuiltInRegistries.ITEM.get(id));
                    case "entity_killed" -> Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.get(id));
                    case "entity_killed_by" -> Stats.ENTITY_KILLED_BY.get(BuiltInRegistries.ENTITY_TYPE.get(id));
                    default -> null;
                };
                return stat == null ? 0 : player.getStats().getValue(stat);
            }
        } catch (RuntimeException ignored) { }
        return 0;
    }

    private static boolean isEventDrivenObjective(String type) {
        if (type.equals("mail_read") || type.equals("archive_viewed") || ComputerLocationObjectives.TYPES.contains(type)) return true;
        ResourceLocation id = ResourceLocation.tryParse(type);
        return id != null && TaskObjectiveRegistry.isRegistered(id);
    }

    private static ObjectiveView objectiveView(ServerPlayer player, ComputerTaskProgress progress,
                                                ComputerGuideData.Task task, ComputerGuideData.Objective objective) {
        boolean complete = progress.isObjectiveSatisfied(task.id(), objective.id());
        int progressValue = progress.objectiveCount(task.id(), objective.id());
        int goal = objectiveProgressGoal(player, objective);
        if (objective.type().equals("item_tag") && objective.tagMode().equals("any")
                && itemTagItems(player, ResourceLocation.tryParse(objective.target())).isEmpty()) {
            return new ObjectiveView(progressValue, objective.count(), complete, complete, false);
        }
        if (objective.type().equals("item_tag") && objective.tagMode().equals("all")) {
            List<Item> items = itemTagItems(player, ResourceLocation.tryParse(objective.target()));
            progressValue = 0;
            for (Item item : items) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                boolean consumed = progress.isObjectiveItemConsumed(task.id(), objective.id(), id);
                int value = consumed ? objective.count() : objective.sticky() ? progress.objectiveItemCount(task.id(), objective.id(), id)
                        : player.getInventory().countItem(item);
                if (value >= objective.count() && (!objective.consume() || consumed)) progressValue++;
            }
            goal = items.size();
            complete |= goal > 0 && progressValue >= goal;
            if (complete && goal > 0) progressValue = goal;
        } else {
            goal = objective.count();
            complete |= progressValue >= goal;
        }
        if (complete && goal > 0) progressValue = goal;
        return new ObjectiveView(Math.max(0, progressValue), Math.max(0, goal), complete, complete, false);
    }

    private static ObjectiveView evaluateObjective(ServerPlayer player, ComputerTaskProgress progress,
                                                    ComputerGuideData.Task task, ComputerGuideData.Objective objective,
                                                    java.util.Map<ComputerGuideData.Objective, Integer> liveProgress,
                                                    boolean sharedTeamProgress) {
        if (objective.type().equals("item_tag") && objective.tagMode().equals("any")
                && itemTagItems(player, ResourceLocation.tryParse(objective.target())).isEmpty()) {
            return new ObjectiveView(progress.objectiveCount(task.id(), objective.id()), objective.count(), false, false, false);
        }
        if (objective.type().equals("item_tag") && objective.tagMode().equals("all")) {
            List<Item> items = itemTagItems(player, ResourceLocation.tryParse(objective.target()));
            int met = 0;
            boolean changed = false;
            for (Item item : items) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                int live = player.getInventory().countItem(item);
                int value = objective.sticky() ? Math.max(live, progress.objectiveItemCount(task.id(), objective.id(), id)) : live;
                if (sharedTeamProgress && objective.sticky()) value = Math.max(value, progress.objectiveItemCount(task.id(), objective.id(), id));
                if (objective.sticky()) changed |= progress.setObjectiveItemCount(task.id(), objective.id(), id, value);
                if (value >= objective.count()) met++;
            }
            boolean liveComplete = !items.isEmpty() && items.stream().allMatch(item -> player.getInventory().countItem(item) >= objective.count());
            return new ObjectiveView(met, items.size(), !items.isEmpty() && met == items.size(), liveComplete, changed);
        }
        int live = isEventDrivenObjective(objective.type())
                ? progress.objectiveCount(task.id(), objective.id())
                : liveProgress.computeIfAbsent(objective, value -> objectiveProgress(player, value));
        int current = objective.sticky() ? Math.max(live, progress.objectiveCount(task.id(), objective.id())) : live;
        if (sharedTeamProgress && objective.sticky()) current = Math.max(current, progress.objectiveCount(task.id(), objective.id()));
        int value = Math.min(objective.count(), current);
        boolean liveComplete = live >= objective.count();
        return new ObjectiveView(value, objective.count(), value >= objective.count(), liveComplete, false);
    }

    private static ObjectiveView evaluateAllTagObjective(ServerPlayer player, ComputerTaskProgress progress,
                                                          ComputerGuideData.Task task, ComputerGuideData.Objective objective,
                                                          boolean sharedTeamProgress, boolean consume) {
        List<Item> items = itemTagItems(player, ResourceLocation.tryParse(objective.target()));
        int met = 0;
        boolean changed = false;
        boolean consumedItems = false;
        for (Item item : items) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            boolean itemConsumed = progress.isObjectiveItemConsumed(task.id(), objective.id(), itemId);
            int live = player.getInventory().countItem(item);
            int previous = progress.objectiveItemCount(task.id(), objective.id(), itemId);
            int value = Math.min(objective.count(), objective.sticky() ? Math.max(live, previous) : live);
            if (sharedTeamProgress && objective.sticky()) value = Math.max(value, previous);
            if (consume && !itemConsumed && live >= objective.count()) {
                if (consumeItem(player, item, objective.count())) {
                    itemConsumed = true;
                    value = objective.count();
                    consumedItems = true;
                    changed |= progress.markObjectiveItemConsumed(task.id(), objective.id(), itemId);
                }
            }
            if (itemConsumed) value = objective.count();
            if (objective.sticky() || itemConsumed) changed |= progress.setObjectiveItemCount(task.id(), objective.id(), itemId, value);
            if (value >= objective.count() && (!consume || itemConsumed)) met++;
        }
        if (items.isEmpty()) return new ObjectiveView(0, 0, false, false, changed, consumedItems);
        changed |= progress.setObjectiveCount(task.id(), objective.id(), met);
        boolean complete = met == items.size();
        return new ObjectiveView(met, items.size(), complete, complete, changed, consumedItems);
    }

    static int objectiveProgressGoal(ServerPlayer player, ComputerGuideData.Objective objective) {
        if (objective.type().equals("item_tag") && objective.tagMode().equals("all"))
            return itemTagItems(player, ResourceLocation.tryParse(objective.target())).size();
        return objective.count();
    }

    private static List<Item> itemTagItems(ServerPlayer player, ResourceLocation tagId) {
        if (player == null || tagId == null) return List.of();
        var registry = player.registryAccess().lookupOrThrow(Registries.ITEM);
        var tag = registry.get(TagKey.create(Registries.ITEM, tagId));
        if (tag.isEmpty()) {
            if (MISSING_ITEM_TAGS.add(tagId)) AntOS.LOGGER.warn("AntOS task references missing item tag {}", tagId);
            return List.of();
        }
        List<Item> items = tag.stream()
                .flatMap(holderSet -> holderSet.stream().map(holder -> holder.value()))
                .distinct().sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        if (items.size() > 128) {
            if (OVERSIZED_ITEM_TAGS.add(tagId)) AntOS.LOGGER.warn("AntOS task item tag {} contains {} items; objectives using tags are limited to 128 members", tagId, items.size());
            return List.of();
        }
        return items;
    }

    private static boolean consumeObjectiveItems(ServerPlayer player, ComputerGuideData.Objective objective) {
        java.util.Map<Item, Integer> required = new java.util.LinkedHashMap<>();
        if (objective.type().equals("item")) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(objective.target()));
            required.put(item, objective.count());
        } else if (objective.type().equals("item_tag")) {
            if (objective.tagMode().equals("all")) return false;
            List<Item> items = itemTagItems(player, ResourceLocation.tryParse(objective.target()));
            if (items.isEmpty()) return false;
            int remaining = objective.count();
            for (Item item : items) {
                int amount = Math.min(remaining, player.getInventory().countItem(item));
                if (amount > 0) required.put(item, amount);
                remaining -= amount;
                if (remaining == 0) break;
            }
            if (remaining > 0) return false;
        } else return false;
        return removeItems(player, required);
    }

    private static boolean consumeItem(ServerPlayer player, Item item, int count) {
        if (item == null || item == net.minecraft.world.item.Items.AIR || count <= 0
                || player.getInventory().countItem(item) < count) return false;
        return removeItems(player, java.util.Map.of(item, count));
    }

    private static boolean removeItems(ServerPlayer player, java.util.Map<Item, Integer> required) {
        if (required.isEmpty() || required.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getKey() == net.minecraft.world.item.Items.AIR
                || player.getInventory().countItem(entry.getKey()) < entry.getValue())) return false;
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && stack.is(entry.getKey())) {
                    int removed = Math.min(remaining, stack.getCount());
                    stack.shrink(removed);
                    remaining -= removed;
                    if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            if (remaining > 0) return false;
        }
        player.getInventory().setChanged();
        return true;
    }

    private record ObjectiveView(int progress, int goal, boolean complete, boolean liveComplete, boolean changed, boolean consumedItems) {
        private ObjectiveView(int progress, int goal, boolean complete, boolean liveComplete, boolean changed) {
            this(progress, goal, complete, liveComplete, changed, false);
        }
    }
}
