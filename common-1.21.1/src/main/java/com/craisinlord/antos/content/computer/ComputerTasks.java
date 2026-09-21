package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
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
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.List;

public final class ComputerTasks {
    private static final java.util.Set<String> OVERSIZED_TASK_STATES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ComputerTasks() { }
    public static boolean grantTask(ServerPlayer player, ComputerBlockEntity computer, ResourceLocation taskId) {
        ComputerGuideData.Task task = task(taskId);
        if (task == null) return false;
        boolean changed = computer.taskProgress().grant(taskId);
        evaluateProgress(player, computer, computer.taskProgress(), changed);
        return changed;
    }

    public static boolean forceCompleteTask(ServerPlayer player, ComputerBlockEntity computer, ResourceLocation taskId) {
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
        if (progress.markComplete(taskId)) deliverRewards(player, computer, task);
        evaluateProgress(player, computer, progress, true);
        return progress.isComplete(taskId);
    }

    public static boolean setEventObjectiveProgress(ServerPlayer player, ComputerBlockEntity computer,
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

    public static boolean resetTaskProgress(ComputerBlockEntity computer) {
        boolean changed = computer.taskProgress().clearTaskProgress();
        if (changed) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
        return changed;
    }

    private static ComputerGuideData.Task task(ResourceLocation id) {
        return ComputerGuideData.tasks().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
    }

    public static boolean isTaskComplete(ServerPlayer player, ComputerBlockEntity computer, ResourceLocation taskId) {
        if (player == null || computer == null || taskId == null || task(taskId) == null) return false;
        ComputerTaskProgress progress = computer.taskProgress();
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) progress.mergeFrom(ComputerWorkspaceData.access(player.server).progress(workspaceId));
        return progress.isComplete(taskId);
    }

    public static String updateAndEncode(ServerPlayer player, ComputerBlockEntity computer) {
        ComputerTaskProgress progress = computer.taskProgress();
        evaluateProgress(player, computer, progress, false);

        JsonObject root = new JsonObject(); JsonArray rows = new JsonArray();
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            JsonObject row = new JsonObject(); row.addProperty("id", task.id().toString());
            row.addProperty("title", task.titleKey()); row.addProperty("description", task.descriptionKey());
            row.addProperty("program", task.program()); row.addProperty("category", task.category());
            row.addProperty("x", task.x()); row.addProperty("y", task.y());
            row.addProperty("complete", progress.isComplete(task.id())); row.addProperty("visible", visible(task, progress));
            row.addProperty("available", available(task, progress) || progress.isComplete(task.id()));
            row.addProperty("has_rewards", !task.rewards().isEmpty());
            String iconItem = "";
            String iconEntity = "";
            for (ComputerGuideData.Objective objective : task.objectives()) {
                if (objective.type().equals("item")) { iconItem = objective.target(); break; }
                if (objective.type().equals("entity_killed") || objective.type().equals("entity_killed_by")) { iconEntity = objective.target(); break; }
            }
            if (iconItem.isBlank() && iconEntity.isBlank()) {
                for (ComputerGuideData.TaskReward reward : task.rewards()) {
                    if (reward.type().equals("item") && reward.itemId() != null) { iconItem = reward.itemId().toString(); break; }
                }
            }
            if (iconItem.isBlank() && iconEntity.isBlank()) iconItem = "minecraft:paper";
            row.addProperty("icon_item", iconItem);
            row.addProperty("icon_entity", iconEntity);
            JsonArray prerequisites = new JsonArray(); task.requires().forEach(id -> prerequisites.add(id.toString())); row.add("requires", prerequisites);
            JsonArray archiveEntries = new JsonArray(); task.archiveEntries().forEach(id -> archiveEntries.add(id.toString())); row.add("archive_entries", archiveEntries);
            int total = 0, done = 0;
            for (ComputerGuideData.Objective objective : task.objectives()) {
                if (objective.optional()) continue;
                total++; int count = progress.objectiveCount(task.id(), objective.id());
                if (count >= objective.count()) done++;
            }
            row.addProperty("done", done); row.addProperty("total", total);
            JsonArray objectives = new JsonArray();
            for (ComputerGuideData.Objective objective : task.objectives()) {
                JsonObject objectiveRow = new JsonObject(); objectiveRow.addProperty("id", objective.id());
                objectiveRow.addProperty("description", objective.descriptionKey());
                objectiveRow.addProperty("count", objective.count()); objectiveRow.addProperty("optional", objective.optional());
                objectiveRow.addProperty("progress", progress.objectiveCount(task.id(), objective.id()));
                objectives.add(objectiveRow);
            }
            row.add("objectives", objectives);
            rows.add(row);
        }
        root.add("tasks", rows);
        String encoded = root.toString();
        if (encoded.length() + 64 > com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS) {
            String computerKey = player.serverLevel().dimension().location() + ":" + computer.getBlockPos().asLong();
            if (OVERSIZED_TASK_STATES.add(computerKey)) AntOS.LOGGER.error("Task snapshot for AntOS computer {} in {} exceeds the network payload limit; reduce task/objective definitions",
                    computer.getBlockPos(), player.serverLevel().dimension().location());
            JsonObject error = new JsonObject(); error.add("tasks", new JsonArray()); error.addProperty("error", "task_snapshot_too_large");
            return error.toString();
        }
        return encoded;
    }

    public static void recordEvent(ServerPlayer player, ComputerBlockEntity computer, String eventType, ResourceLocation target) {
        recordEvent(player, computer, eventType, target, 1);
    }

    public static void recordCustomEvent(ServerPlayer player, ComputerBlockEntity computer, ResourceLocation type,
                                         ResourceLocation target, int amount) {
        if (type == null || !TaskObjectiveRegistry.isRegistered(type)) return;
        recordEvent(player, computer, type.toString(), target, amount);
    }

    private static void recordEvent(ServerPlayer player, ComputerBlockEntity computer, String eventType,
                                    ResourceLocation target, int amount) {
        if (player == null || computer == null || target == null || amount <= 0) return;
        ComputerTaskProgress progress = computer.taskProgress();
        boolean eventChanged = false;
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (task.requires().isEmpty() && task.availability().equals("automatic")) progress.grant(task.id());
        }
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (progress.isComplete(task.id()) || !available(task, progress)) continue;
            for (ComputerGuideData.Objective objective : task.objectives()) {
                if (objective.type().equals(eventType) && target.toString().equals(objective.target())) {
                    long updated = progress.objectiveCount(task.id(), objective.id()) + (long) amount;
                    eventChanged |= progress.setObjectiveCount(task.id(), objective.id(), (int) Math.min(1_000_000L, updated));
                }
            }
        }
        evaluateProgress(player, computer, progress, eventChanged);
    }

    private static void evaluateProgress(ServerPlayer player, ComputerBlockEntity computer, ComputerTaskProgress progress,
                                         boolean alreadyChanged) {
        boolean progressChanged = alreadyChanged;
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) {
            progressChanged |= progress.mergeFrom(ComputerWorkspaceData.access(player.server).progress(workspaceId));
        }
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (task.requires().isEmpty() && task.availability().equals("automatic")) progressChanged |= progress.grant(task.id());
        }
        boolean changed;
        int passes = 0;
        do {
            changed = false;
            for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
                if (progress.isComplete(task.id()) || !available(task, progress)) continue;
                boolean requiredDone = true;
                boolean anyRequired = false;
                for (ComputerGuideData.Objective objective : task.objectives()) {
                    int current = isEventDrivenObjective(objective.type())
                            ? progress.objectiveCount(task.id(), objective.id()) : objectiveProgress(player, objective);
                    int count = Math.min(objective.count(), current);
                    if (progress.setObjectiveCount(task.id(), objective.id(), count)) {
                        changed = true;
                        progressChanged = true;
                    }
                    if (!objective.optional()) { anyRequired = true; requiredDone &= count >= objective.count(); }
                }
                if ((!anyRequired || requiredDone) && progress.markComplete(task.id())) {
                    changed = true;
                    progressChanged = true;
                    deliverRewards(player, computer, task);
                }
            }
        } while (changed && ++passes < ComputerGuideData.tasks().size() + 1);
        if (progressChanged) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
    }

    private static void deliverRewards(ServerPlayer player, ComputerBlockEntity computer, ComputerGuideData.Task task) {
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
                    if (ComputerGuideData.entry(reward.target()) != null) {
                        computer.taskProgress().unlockArchiveEntry(reward.target());
                    } else {
                        AntOS.LOGGER.warn("Task {} rewards missing Archive entry {}", task.id(), reward.target());
                    }
                }
                case "mail" -> deliverMailReward(player, task, reward);
            }
        }
        if (stacks.isEmpty()) return;

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
        AntmailAddress recipient = data.addressFor(player);
        if (recipient == null) {
            AntOS.LOGGER.warn("Task {} mail reward could not be delivered because player {} has no registered Antmail address",
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
        if (type.equals("mail_read") || type.equals("archive_viewed")) return true;
        ResourceLocation id = ResourceLocation.tryParse(type);
        return id != null && TaskObjectiveRegistry.isRegistered(id);
    }
}
