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
import net.minecraft.resources.Identifier;
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
    private static final java.util.Set<Identifier> OVERSIZED_ITEM_TAGS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Identifier> MISSING_ITEM_TAGS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Identifier, TagItems> TAG_ITEMS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong TAG_EPOCH = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong EVALUATION_EPOCH = new java.util.concurrent.atomic.AtomicLong();
    private static final Map<java.util.UUID, MergedProgress> MERGED_PROGRESS = new java.util.HashMap<>();
    private static final Map<ServerPlayer, EncodedState> ENCODED_STATES = new java.util.WeakHashMap<>();
    private static final Map<ServerPlayer, Map<String, String>> SENT_DEFINITIONS = new java.util.WeakHashMap<>();
    private static int mergedProgressTick = Integer.MIN_VALUE;
    private static volatile Dependents dependents;
    private ComputerTasks() { }


    public static void serverStopped() {
        synchronized (MERGED_PROGRESS) { MERGED_PROGRESS.clear(); }
        synchronized (ENCODED_STATES) { ENCODED_STATES.clear(); }
        synchronized (SENT_DEFINITIONS) { SENT_DEFINITIONS.clear(); }
        TAG_ITEMS.clear();
        ComputerLocationObjectives.serverStopped();
    }
    public static boolean giveRewardPool(ServerPlayer player, Identifier poolId) {
        if (player == null || ComputerGuideData.taskRewardPool(poolId) == null) return false;
        List<ItemStack> stacks = new java.util.ArrayList<>();
        rollRewardPool(poolId, player, 1, stacks, 108);
        if (!stacks.isEmpty()) dropRewardCrate(player, stacks);
        return true;
    }
    public static boolean grantTask(ServerPlayer player, ComputerWorkspace computer, Identifier taskId) {
        ComputerGuideData.Task task = task(taskId);
        if (task == null) return false;
        boolean changed = computer.taskProgress().grant(taskId);
        evaluateProgress(player, computer, computer.taskProgress(), changed);
        return changed;
    }

    public static boolean forceCompleteTask(ServerPlayer player, ComputerWorkspace computer, Identifier taskId) {
        ComputerGuideData.Task task = task(taskId);
        if (task == null) return false;
        ComputerTaskProgress progress = computer.taskProgress();
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) {
            progress.mergeFrom(ComputerWorkspaceData.access(player.level().getServer()).progress(workspaceId));
        }
        if (progress.isComplete(taskId)) return false;
        progress.grant(taskId);
        for (ComputerGuideData.Objective objective : task.objectives()) {
            progress.setObjectiveCount(taskId, objective.id(), objective.count());
            if (progress.markObjectiveSatisfied(taskId, objective.id())) {
                com.craisinlord.antos.content.antmail.AntmailNotifier.notifyObjectiveCompleted(player, task, objective);
            }
        }
        boolean newlyCompleted = progress.markComplete(taskId);
        if (newlyCompleted) {
            unlockArchiveRewards(computer, task);
        }
        evaluateProgress(player, computer, progress, true);
        if (newlyCompleted) ComputerTaskCompletionEvents.fire(player, taskId);
        return progress.isComplete(taskId);
    }

    public static final int MAX_CLAIM_ALL = 256;
    public static final int CRATE_ITEM_LIMIT = 108;

    public record ClaimAllResult(List<Identifier> claimed, List<Identifier> failed,
                                 List<ComputerGuideData.TaskReward> receipt, int crates) { }

    public static boolean claimTaskRewards(ServerPlayer player, ComputerWorkspace computer, Identifier taskId, Map<Integer, String> choices) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        if (claimTask(player, computer, taskId, choices, stacks) == null) return false;
        dropRewardCrates(player, stacks);
        computer.saveWorkspaceProgress();
        return true;
    }

    public static ClaimAllResult claimAllTaskRewards(ServerPlayer player, ComputerWorkspace computer,
                                                     Map<Identifier, Map<Integer, String>> requests) {
        List<Identifier> claimed = new java.util.ArrayList<>();
        List<Identifier> failed = new java.util.ArrayList<>();
        List<ComputerGuideData.TaskReward> receipt = new java.util.ArrayList<>();
        List<ItemStack> stacks = new java.util.ArrayList<>();
        if (player == null || computer == null || requests == null) return new ClaimAllResult(List.of(), List.of(), List.of(), 0);
        int processed = 0;
        for (Map.Entry<Identifier, Map<Integer, String>> request : requests.entrySet()) {
            if (processed++ >= MAX_CLAIM_ALL) break;
            List<ComputerGuideData.TaskReward> taskReceipt = claimTask(player, computer, request.getKey(), request.getValue(), stacks);
            if (taskReceipt == null) failed.add(request.getKey());
            else {
                claimed.add(request.getKey());
                receipt.addAll(taskReceipt);
            }
        }
        int crates = dropRewardCrates(player, stacks);
        if (!claimed.isEmpty()) computer.saveWorkspaceProgress();
        return new ClaimAllResult(List.copyOf(claimed), List.copyOf(failed), List.copyOf(receipt), crates);
    }

    private static List<ComputerGuideData.TaskReward> claimTask(ServerPlayer player, ComputerWorkspace computer, Identifier taskId,
                                                               Map<Integer, String> choices, List<ItemStack> stacks) {
        if (player == null || computer == null || taskId == null) return null;
        var account = accountForProgress(player);
        if (account == null) return null;
        ComputerGuideData.Task task = task(taskId);
        if (task == null || !hasClaimableRewards(task) || !isTaskComplete(player, computer, taskId)) return null;
        Map<Integer, String> selectedChoices = choices == null ? Map.of() : choices;
        for (int index = 0; index < task.rewards().size(); index++) {
            ComputerGuideData.TaskReward reward = task.rewards().get(index);
            if (reward.type().equals("item_pool") && reward.mode().equals("choice")
                    && !validPoolChoice(reward.poolId(), selectedChoices.get(index), 0)) return null;
            if (reward.type().equals("choice")) {
                int optionIndex = selectedChoiceIndex(selectedChoices.get(index));
                if (optionIndex < 0 || optionIndex >= reward.options().size()) return null;
            }
        }
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        if (data.hasClaimedTaskReward(account.accountId(), taskId)) return null;
        if (!data.markTaskRewardClaimed(account.accountId(), taskId)) return null;
        return grantRewards(player, task.id(), task.rewards(), selectedChoices, stacks);
    }

    public static boolean hasClaimableRewards(ComputerGuideData.Task task) {
        return task != null && task.rewards().stream().anyMatch(reward -> !reward.type().equals("archive"));
    }

    private static boolean isKillObjective(ComputerGuideData.Objective objective) {
        String type = objective.type().equals("stat") ? objective.statType() : objective.type();
        return type.equals("entity_killed") || type.equals("entity_killed_by");
    }

    public static boolean setEventObjectiveProgress(ServerPlayer player, ComputerWorkspace computer,
                                                     Identifier taskId, String objectiveId, int count) {
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

    public static LocationResult completeLocationObjective(ServerPlayer player, ComputerWorkspace computer,
                                                           Identifier taskId, String objectiveId) {
        ComputerGuideData.Task task = task(taskId);
        ComputerGuideData.Objective objective = task == null ? null : task.objectives().stream()
                .filter(candidate -> candidate.id().equals(objectiveId)).findFirst().orElse(null);
        if (objective == null) return LocationResult.DONE;
        ComputerTaskProgress progress = computer.taskProgress();
        var account = accountForProgress(player);
        if (account != null) progress.mergeFrom(ComputerWorkspaceData.access(player.level().getServer()).effectiveProgress(account.accountId(), progress));
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) progress.mergeFrom(ComputerWorkspaceData.access(player.level().getServer()).progress(workspaceId));
        if (progress.isComplete(taskId) || progress.objectiveCount(taskId, objectiveId) >= objective.count()) return LocationResult.DONE;
        for (ComputerGuideData.Task candidate : ComputerGuideData.tasks()) {
            if (!candidate.hasPrerequisites() && candidate.availability().equals("automatic")) progress.grant(candidate.id());
        }
        if (!available(task, progress)) return LocationResult.UNAVAILABLE;
        boolean changed = progress.setObjectiveCount(taskId, objectiveId, objective.count());
        evaluateProgress(player, computer, progress, changed);
        return LocationResult.DONE;
    }

    public static boolean resetTaskProgress(ComputerWorkspace computer) {
        boolean changed = computer.taskProgress().clearTaskProgress();
        invalidateEvaluations();
        if (changed) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
        return changed;
    }

    public static boolean taskExists(Identifier id) {
        return task(id) != null;
    }

    private static ComputerGuideData.Task task(Identifier id) {
        return ComputerGuideData.task(id);
    }

    private static ComputerWorkspaceData.AccountInfo accountForProgress(ServerPlayer player) {
        if (player == null) return null;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        var session = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        return session != null ? session : data.faceIdAccount(player.getUUID());
    }

    public static boolean isTaskComplete(ServerPlayer player, ComputerWorkspace computer, Identifier taskId) {
        if (player == null || computer == null || taskId == null || task(taskId) == null) return false;
        return mergedProgress(player, computer).isComplete(taskId);
    }

    public static void invalidateEvaluations() {
        EVALUATION_EPOCH.incrementAndGet();
    }

    private static ComputerTaskProgress mergedProgress(ServerPlayer player, ComputerWorkspace computer) {
        ComputerTaskProgress progress = computer.taskProgress();
        var account = accountForProgress(player);
        java.util.UUID accountId = account == null ? null : account.accountId();
        java.util.UUID workspaceId = computer.workspaceId();
        int tick = player.level().getServer().getTickCount();
        long epoch = EVALUATION_EPOCH.get();
        synchronized (MERGED_PROGRESS) {
            if (mergedProgressTick != tick) {
                MERGED_PROGRESS.clear();
                mergedProgressTick = tick;
            }
            MergedProgress cached = MERGED_PROGRESS.get(player.getUUID());
            if (cached != null && cached.computer() == computer && cached.progress() == progress && cached.revision() == progress.revision()
                    && cached.epoch() == epoch && java.util.Objects.equals(cached.accountId(), accountId)
                    && java.util.Objects.equals(cached.workspaceId(), workspaceId)) return progress;
        }
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        if (account != null) progress.mergeFrom(data.effectiveProgress(accountId, progress));
        if (workspaceId != null) progress.mergeFrom(data.progress(workspaceId));
        synchronized (MERGED_PROGRESS) {
            if (mergedProgressTick == tick) MERGED_PROGRESS.put(player.getUUID(),
                    new MergedProgress(computer, progress, progress.revision(), epoch, accountId, workspaceId));
        }
        return progress;
    }

    private record MergedProgress(ComputerWorkspace computer, ComputerTaskProgress progress, int revision, long epoch,
                                  java.util.UUID accountId, java.util.UUID workspaceId) { }

    public static void synchronizeSharedProgress(ServerPlayer player, ComputerWorkspace computer) {
        if (player == null || computer == null) return;
        var account = accountForProgress(player);
        if (account == null) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        boolean changed = computer.taskProgress().mergeFrom(data.effectiveProgress(account.accountId(), computer.taskProgress()));
        data.mergeTeamProgress(account.accountId(), computer.taskProgress());
        invalidateEvaluations();
        if (changed) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
    }

    public static String updateAndEncode(ServerPlayer player, ComputerWorkspace computer) {
        return updateAndEncode(player, computer, "", false);
    }

    public static String updateAndEncode(ServerPlayer player, ComputerWorkspace computer, String known) {
        return updateAndEncode(player, computer, known, true);
    }

    private static String updateAndEncode(ServerPlayer player, ComputerWorkspace computer, String known, boolean clientRequest) {
        ComputerTaskProgress progress = computer.taskProgress();
        evaluateProgress(player, computer, progress, false);
        String[] knownHashes = (known == null ? "" : known).split("\u0000", -1);
        String knownDefinitions = knownHashes.length > 0 ? knownHashes[0] : "";
        String knownState = knownHashes.length > 1 ? knownHashes[1] : "";
        EncodedDefinitions definitions = definitions();
        String workspaceKey = String.valueOf(computer.workspaceId());
        if (!clientRequest && knownDefinitions.isEmpty() && definitions.hash().equals(sentDefinitions(player, workspaceKey)))
            knownDefinitions = definitions.hash();
        EncodedState encodedState = encodeState(player, computer, progress);
        String state = encodedState.state();
        String stateHash = encodedState.hash();
        boolean sendDefinitions = !knownDefinitions.equals(definitions.hash());
        boolean sendState = sendDefinitions || !knownState.equals(stateHash);
        StringBuilder encoded = new StringBuilder(64 + (sendDefinitions ? definitions.json().length() : 0) + (sendState ? state.length() : 0));
        encoded.append("{\"defs_hash\":\"").append(definitions.hash()).append("\",\"state_hash\":\"").append(stateHash).append('"');
        if (sendDefinitions) encoded.append(",\"definitions\":").append(definitions.json());
        if (sendState) encoded.append(",\"state\":").append(state);
        encoded.append('}');
        String packed = compress(encoded.toString());
        if (packed.length() + 64 > com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS) {
            if (OVERSIZED_TASK_STATES.add(workspaceKey)) AntOS.LOGGER.error("Task snapshot for Anternet workspace {} exceeds the network payload limit; reduce task/objective definitions",
                    workspaceKey);
            forgetSentDefinitions(player, workspaceKey);
            JsonObject error = new JsonObject(); error.add("tasks", new JsonArray()); error.addProperty("error", "task_snapshot_too_large");
            return error.toString();
        }
        if (clientRequest) rememberSentDefinitions(player, workspaceKey, definitions.hash());
        return packed;
    }

    public static final String COMPRESSED_PREFIX = "gz:";
    private static final int COMPRESSION_THRESHOLD = 2048;

    private static String compress(String json) {
        if (json.length() < COMPRESSION_THRESHOLD) return json;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(json.length() / 8);
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            return json;
        }
        return COMPRESSED_PREFIX + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static void put(JsonObject row, String key, String value) {
        if (value != null && !value.isEmpty()) row.addProperty(key, value);
    }

    private static void put(JsonObject row, String key, int value, int fallback) {
        if (value != fallback) row.addProperty(key, value);
    }

    private static void put(JsonObject row, String key, long value, long fallback) {
        if (value != fallback) row.addProperty(key, value);
    }

    private static void put(JsonObject row, String key, float value, float fallback) {
        if (Float.compare(value, fallback) != 0) row.addProperty(key, value);
    }

    private static void put(JsonObject row, String key, boolean value, boolean fallback) {
        if (value != fallback) row.addProperty(key, value);
    }

    private static void putList(JsonObject row, String key, java.util.Collection<?> values) {
        if (values.isEmpty()) return;
        JsonArray array = new JsonArray();
        for (Object value : values) array.add(String.valueOf(value));
        row.add(key, array);
    }

    private static void putAppearance(JsonObject row, com.craisinlord.antos.content.guide.TaskAppearance appearance) {
        var fallback = com.craisinlord.antos.content.guide.TaskAppearance.DEFAULT;
        if (appearance.shape() != fallback.shape()) row.addProperty("shape", appearance.shape().name().toLowerCase(java.util.Locale.ROOT));
        put(row, "size", appearance.size(), fallback.size());
        put(row, "icon_scale", appearance.iconScale(), fallback.iconScale());
        if (appearance.lineAxis() != fallback.lineAxis()) row.addProperty("line_axis", appearance.lineAxis().name().toLowerCase(java.util.Locale.ROOT));
        put(row, "line_bend", appearance.lineBend(), fallback.lineBend());
    }

    private static String sentDefinitions(ServerPlayer player, String workspaceKey) {
        synchronized (SENT_DEFINITIONS) {
            Map<String, String> sent = SENT_DEFINITIONS.get(player);
            return sent == null ? null : sent.get(workspaceKey);
        }
    }

    private static void rememberSentDefinitions(ServerPlayer player, String workspaceKey, String hash) {
        synchronized (SENT_DEFINITIONS) {
            Map<String, String> sent = SENT_DEFINITIONS.computeIfAbsent(player, ignored -> new java.util.HashMap<>());
            if (sent.size() >= 16 && !sent.containsKey(workspaceKey)) sent.clear();
            sent.put(workspaceKey, hash);
        }
    }

    private static void forgetSentDefinitions(ServerPlayer player, String workspaceKey) {
        synchronized (SENT_DEFINITIONS) {
            Map<String, String> sent = SENT_DEFINITIONS.get(player);
            if (sent != null) sent.remove(workspaceKey);
        }
    }

    private static String hash(String value) {
        return Integer.toHexString(value.hashCode()) + "-" + Integer.toHexString(value.length());
    }

    private record EncodedDefinitions(List<ComputerGuideData.Task> tasks, List<ComputerGuideData.TaskCategory> categories,
                                      List<ComputerGuideData.TaskGroup> groups, Map<Identifier, ComputerGuideData.RewardPool> pools,
                                      List<ComputerGuideData.TaskLink> links, String json, String hash) { }

    private static volatile EncodedDefinitions encodedDefinitions;

    private static EncodedDefinitions definitions() {
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        List<ComputerGuideData.TaskCategory> categories = ComputerGuideData.taskCategories();
        List<ComputerGuideData.TaskGroup> groups = ComputerGuideData.taskGroups();
        Map<Identifier, ComputerGuideData.RewardPool> pools = ComputerGuideData.taskRewardPools();
        List<ComputerGuideData.TaskLink> links = ComputerGuideData.taskLinks();
        EncodedDefinitions cached = encodedDefinitions;
        if (cached != null && cached.tasks() == tasks && cached.categories() == categories && cached.groups() == groups && cached.pools() == pools
                && cached.links() == links) return cached;
        JsonObject root = new JsonObject(); JsonArray rows = new JsonArray();
        for (ComputerGuideData.Task task : tasks) {
            JsonObject row = new JsonObject(); row.addProperty("id", task.id().toString());
            put(row, "title", task.titleKey()); put(row, "description", task.descriptionKey());
            put(row, "category", task.category());
            put(row, "has_position", task.hasPosition(), false); put(row, "x", task.x(), 0); put(row, "y", task.y(), 0);
            put(row, "has_rewards", !task.rewards().isEmpty(), false);
            String iconTexture = "";
            String iconItem = task.iconItem();
            String iconEntity = iconItem.isBlank() ? task.iconEntity() : "";
            if (iconItem.isBlank() && iconEntity.isBlank()) for (ComputerGuideData.Objective objective : task.objectives()) {
                if (objective.type().equals("disk_installed")) {
                    var disk = objective.targetCategory().isBlank() ? ComputerDiskData.disk(Identifier.parse(objective.target())) : null;
                    Identifier categoryId = disk != null ? disk.category() : Identifier.tryParse(objective.targetCategory());
                    var category = categoryId == null ? null : ComputerGuideData.category(categoryId);
                    Identifier texture = category == null ? Identifier.fromNamespaceAndPath("antos", "item/floppy_disk/floppy_disk") : category.texture();
                    iconTexture = texture.getNamespace() + ":textures/" + texture.getPath() + ".png";
                    iconItem = "antos:floppy_disk";
                    break;
                }
                if (objective.type().equals("item")) { iconItem = objective.target(); break; }
                if (isKillObjective(objective)) { iconEntity = objective.target(); break; }
            }
            if (iconItem.isBlank() && iconEntity.isBlank()) {
                for (ComputerGuideData.TaskReward reward : task.rewards()) {
                    if (reward.type().equals("item") && reward.itemId() != null) { iconItem = reward.itemId().toString(); break; }
                }
            }
            if (iconItem.isBlank() && iconEntity.isBlank()) iconItem = "minecraft:paper";
            put(row, "icon_texture", iconTexture);
            put(row, "icon_item", iconItem);
            put(row, "icon_entity", iconEntity);
            putAppearance(row, task.appearance());
            put(row, "green_tint", task.greenTint(), true);
            put(row, "render_mob_from_spawn_egg", task.renderMobFromSpawnEgg(), true);
            if (!task.rewards().isEmpty()) {
                JsonArray rewards = new JsonArray();
                for (ComputerGuideData.TaskReward reward : task.rewards()) rewards.add(encodeTaskReward(reward));
                row.add("rewards", rewards);
            }
            putList(row, "requires", task.requires());
            putList(row, "requires_any", task.requiresAny());
            putList(row, "connects", task.connects());
            putList(row, "archive_entries", task.archiveEntries());
            putList(row, "showcase", task.showcase());
            int total = 0;
            for (ComputerGuideData.Objective objective : task.objectives()) if (!objective.optional()) total++;
            put(row, "total", total, 0);
            if (!task.objectives().isEmpty()) {
                JsonArray objectives = new JsonArray();
                for (ComputerGuideData.Objective objective : task.objectives()) {
                    JsonObject objectiveRow = new JsonObject();
                    put(objectiveRow, "description", objective.descriptionKey());
                    put(objectiveRow, "count", objective.count(), 1); put(objectiveRow, "optional", objective.optional(), false);
                    put(objectiveRow, "item", objective.type().equals("item") ? objective.target() : "");
                    objectives.add(objectiveRow);
                }
                row.add("objectives", objectives);
            }
            rows.add(row);
        }
        root.add("tasks", rows);
        JsonArray poolRows = new JsonArray();
        pools.values().stream().sorted(java.util.Comparator.comparing(pool -> pool.id().toString())).forEach(pool -> {
            JsonObject poolRow = new JsonObject();
            poolRow.addProperty("id", pool.id().toString());
            JsonArray entryRows = new JsonArray();
            for (ComputerGuideData.RewardPoolEntry entry : pool.entries()) {
                JsonObject entryRow = new JsonObject();
                put(entryRow, "item", entry.itemId() == null ? "" : entry.itemId().toString());
                put(entryRow, "pool", entry.poolId() == null ? "" : entry.poolId().toString());
                put(entryRow, "weight", entry.weight(), 1);
                put(entryRow, "count", entry.count(), 1);
                entryRows.add(entryRow);
            }
            poolRow.add("entries", entryRows);
            poolRows.add(poolRow);
        });
        root.add("reward_pools", poolRows);
        JsonArray categoryRows = new JsonArray();
        for (ComputerGuideData.TaskCategory category : categories) {
            JsonObject categoryRow = new JsonObject();
            categoryRow.addProperty("id", category.id().toString());
            put(categoryRow, "title", category.titleKey());
            put(categoryRow, "icon_item", category.iconItem());
            put(categoryRow, "icon_entity", category.iconEntity());
            put(categoryRow, "sort_order", category.sortOrder(), 0);
            put(categoryRow, "group", category.group() == null ? "" : category.group().toString());
            put(categoryRow, "green_tint", category.greenTint(), true);
            put(categoryRow, "render_mob_from_spawn_egg", category.renderMobFromSpawnEgg(), true);
            categoryRows.add(categoryRow);
        }
        root.add("categories", categoryRows);
        JsonArray groupRows = new JsonArray();
        for (ComputerGuideData.TaskGroup group : groups) {
            JsonObject groupRow = new JsonObject();
            groupRow.addProperty("id", group.id().toString());
            put(groupRow, "title", group.titleKey());
            put(groupRow, "icon_item", group.iconItem());
            put(groupRow, "icon_entity", group.iconEntity());
            put(groupRow, "sort_order", group.sortOrder(), 0);
            put(groupRow, "collapsed", group.collapsed(), false);
            put(groupRow, "green_tint", group.greenTint(), true);
            put(groupRow, "render_mob_from_spawn_egg", group.renderMobFromSpawnEgg(), true);
            groupRows.add(groupRow);
        }
        root.add("groups", groupRows);
        JsonArray linkRows = new JsonArray();
        for (ComputerGuideData.TaskLink link : links) {
            JsonObject linkRow = new JsonObject();
            linkRow.addProperty("id", link.id().toString());
            put(linkRow, "title", link.titleKey());
            put(linkRow, "description", link.descriptionKey());
            put(linkRow, "category", link.category());
            put(linkRow, "link_category", link.linkCategory() == null ? "" : link.linkCategory().toString());
            put(linkRow, "link_group", link.linkGroup() == null ? "" : link.linkGroup().toString());
            put(linkRow, "has_position", link.hasPosition(), false); put(linkRow, "x", link.x(), 0); put(linkRow, "y", link.y(), 0);
            put(linkRow, "icon_item", link.iconItem());
            put(linkRow, "icon_entity", link.iconEntity());
            if (link.greenTint() != null) linkRow.addProperty("green_tint", link.greenTint());
            if (link.renderMobFromSpawnEgg() != null) linkRow.addProperty("render_mob_from_spawn_egg", link.renderMobFromSpawnEgg());
            putAppearance(linkRow, link.appearance());
            putList(linkRow, "requires", link.requires());
            putList(linkRow, "connects", link.connects());
            linkRows.add(linkRow);
        }
        root.add("links", linkRows);
        String json = root.toString();
        EncodedDefinitions result = new EncodedDefinitions(tasks, categories, groups, pools, links, json, hash(json));
        encodedDefinitions = result;
        return result;
    }

    private static JsonObject encodeTaskReward(ComputerGuideData.TaskReward reward) {
        JsonObject row = new JsonObject();
        row.addProperty("type", reward.type());
        put(row, "item", reward.itemId() == null ? "" : reward.itemId().toString());
        put(row, "count", reward.count(), 1);
        put(row, "experience", reward.experiencePoints(), 0);
        put(row, "target", reward.target() == null ? "" : reward.target().toString());
        put(row, "subject", reward.subject());
        put(row, "pool", reward.poolId() == null ? "" : reward.poolId().toString());
        put(row, "mode", reward.mode());
        put(row, "antcoins", reward.antcoins(), 0L);
        if (!reward.options().isEmpty()) {
            JsonArray options = new JsonArray();
            for (ComputerGuideData.RewardOption option : reward.options()) {
                JsonObject optionRow = new JsonObject();
                optionRow.addProperty("id", option.id());
                put(optionRow, "title", option.title());
                JsonArray optionRewards = new JsonArray();
                for (ComputerGuideData.TaskReward optionReward : option.rewards()) optionRewards.add(encodeTaskReward(optionReward));
                optionRow.add("rewards", optionRewards);
                options.add(optionRow);
            }
            row.add("options", options);
        }
        return row;
    }

    private record EncodedState(List<ComputerGuideData.Task> tasks, java.util.UUID workspaceId, java.util.UUID accountId,
                                ComputerTaskProgress progress, ComputerWorkspaceData.TeamInfo team,
                                List<ComputerWorkspaceData.TeamInviteInfo> invites, java.util.BitSet claimed, List<Identifier> pinned,
                                Map<Item, Integer> inventory, long tagEpoch, String state, String hash) {
        boolean matches(List<ComputerGuideData.Task> currentTasks, java.util.UUID currentWorkspace, java.util.UUID currentAccount,
                        ComputerTaskProgress currentProgress, ComputerWorkspaceData.TeamInfo currentTeam,
                        List<ComputerWorkspaceData.TeamInviteInfo> currentInvites, java.util.BitSet currentClaimed,
                        List<Identifier> currentPinned, Map<Item, Integer> currentInventory, long currentTagEpoch) {
            return tasks == currentTasks && tagEpoch == currentTagEpoch && java.util.Objects.equals(workspaceId, currentWorkspace)
                    && java.util.Objects.equals(accountId, currentAccount) && java.util.Objects.equals(team, currentTeam)
                    && invites.equals(currentInvites) && claimed.equals(currentClaimed) && pinned.equals(currentPinned)
                    && inventory.equals(currentInventory) && progress.sameContent(currentProgress);
        }
    }

    private static EncodedState encodeState(ServerPlayer player, ComputerWorkspace computer, ComputerTaskProgress progress) {
        var account = accountForProgress(player);
        ComputerWorkspaceData accountData = ComputerWorkspaceData.access(player.level().getServer());
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        java.util.UUID accountId = account == null ? null : account.accountId();
        var team = account == null ? null : accountData.team(account.accountId());
        List<ComputerWorkspaceData.TeamInviteInfo> invitations = account == null ? List.of() : accountData.invitations(account.accountId());
        java.util.BitSet claimedTasks = new java.util.BitSet(tasks.size());
        if (account != null) for (int index = 0; index < tasks.size(); index++) {
            ComputerGuideData.Task task = tasks.get(index);
            if (hasClaimableRewards(task) && accountData.hasClaimedTaskReward(account.accountId(), task.id())) claimedTasks.set(index);
        }
        List<Identifier> pinnedTasks = account == null ? List.of() : accountData.pinnedTasks(account.accountId());
        ItemCounts counts = new ItemCounts(player);
        Map<Item, Integer> inventory = counts.snapshot();
        validateTagCache(player);
        long tagEpoch = TAG_EPOCH.get();
        java.util.UUID workspaceId = computer.workspaceId();
        EncodedState cached;
        synchronized (ENCODED_STATES) { cached = ENCODED_STATES.get(player); }
        if (cached != null && cached.matches(tasks, workspaceId, accountId, progress, team, invitations, claimedTasks, pinnedTasks, inventory, tagEpoch)
                && tagEpoch == TAG_EPOCH.get()) return cached;
        String state = encodeState(player, progress, tasks, account, team, invitations, claimedTasks, pinnedTasks, counts);
        EncodedState result = new EncodedState(tasks, workspaceId, accountId, progress.copy(), team, List.copyOf(invitations), claimedTasks,
                pinnedTasks, inventory, TAG_EPOCH.get() == tagEpoch ? tagEpoch : -1L, state, hash(state));
        synchronized (ENCODED_STATES) { ENCODED_STATES.put(player, result); }
        return result;
    }

    private static String encodeState(ServerPlayer player, ComputerTaskProgress progress, List<ComputerGuideData.Task> tasks,
                                      ComputerWorkspaceData.AccountInfo account, ComputerWorkspaceData.TeamInfo team,
                                      List<ComputerWorkspaceData.TeamInviteInfo> invitations, java.util.BitSet claimedTasks,
                                      List<Identifier> pinnedTasks, ItemCounts counts) {
        JsonObject root = new JsonObject(); JsonArray rows = new JsonArray();
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
        if (account != null) for (var invite : invitations) {
            JsonObject inviteRow = new JsonObject(); inviteRow.addProperty("id", invite.id().toString());
            inviteRow.addProperty("owner_name", invite.ownerName()); inviteRow.addProperty("inviter_name", invite.inviterName());
            invites.add(inviteRow);
        }
        root.add("team_invites", invites);
        JsonArray pinned = new JsonArray();
        for (Identifier pinnedTask : pinnedTasks) pinned.add(pinnedTask.toString());
        root.add("pinned", pinned);
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            ComputerGuideData.Task task = tasks.get(taskIndex);
            JsonObject row = new JsonObject(); row.addProperty("id", task.id().toString());
            boolean complete = progress.isComplete(task.id());
            boolean rewardsClaimable = hasClaimableRewards(task);
            boolean claimed = rewardsClaimable && account != null && claimedTasks.get(taskIndex);
            put(row, "complete", complete, false); put(row, "visible", visible(task, progress), false);
            put(row, "available", complete || available(task, progress), false);
            put(row, "claimable", complete && rewardsClaimable && account != null && !claimed, false);
            put(row, "claimed", claimed, false);
            int done = 0;
            boolean anyProgress = false;
            JsonArray objectiveProgress = new JsonArray();
            for (ComputerGuideData.Objective objective : task.objectives()) {
                ObjectiveView view = objectiveView(player, progress, task, objective, counts);
                if (view.complete() && !objective.optional()) done++;
                if (objective.type().equals("item_tag") && objective.tagMode().equals("all")) {
                    JsonObject objectiveState = new JsonObject();
                    objectiveState.addProperty("progress", view.progress());
                    objectiveState.addProperty("goal", view.goal());
                    objectiveState.addProperty("complete", view.complete());
                    objectiveProgress.add(objectiveState);
                    anyProgress = true;
                } else {
                    objectiveProgress.add(view.progress());
                    anyProgress |= view.progress() != 0;
                }
            }
            put(row, "done", done, 0);
            if (anyProgress) row.add("progress", objectiveProgress);
            rows.add(row);
        }
        root.add("tasks", rows);
        return root.toString();
    }

    public static void recordEvent(ServerPlayer player, ComputerWorkspace computer, String eventType, Identifier target) {
        recordEvent(player, computer, eventType, target, 1);
    }

    public static void recordCustomEvent(ServerPlayer player, ComputerWorkspace computer, Identifier type,
                                         Identifier target, int amount) {
        if (type == null || !TaskObjectiveRegistry.isRegistered(type)) return;
        recordEvent(player, computer, type.toString(), target, amount);
    }

    private static void recordEvent(ServerPlayer player, ComputerWorkspace computer, String eventType,
                                    Identifier target, int amount) {
        if (player == null || computer == null || target == null || amount <= 0) return;
        ComputerTaskProgress progress = computer.taskProgress();
        var account = accountForProgress(player);
        if (account != null) {
            progress.mergeFrom(ComputerWorkspaceData.access(player.level().getServer())
                    .effectiveProgress(account.accountId(), progress));
        }
        boolean eventChanged = false;
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (!task.hasPrerequisites() && task.availability().equals("automatic")) progress.grant(task.id());
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
                && ComputerWorkspaceData.access(player.level().getServer()).team(account.accountId()) != null;
        if (account != null) {
            progressChanged |= progress.mergeFrom(ComputerWorkspaceData.access(player.level().getServer())
                    .effectiveProgress(account.accountId(), progress));
        }
        java.util.UUID workspaceId = computer.workspaceId();
        if (workspaceId != null) {
            progressChanged |= progress.mergeFrom(ComputerWorkspaceData.access(player.level().getServer()).progress(workspaceId));
        }
        for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            if (!task.hasPrerequisites() && task.availability().equals("automatic")) progressChanged |= progress.grant(task.id());
        }
        boolean changed;
        List<Identifier> newlyCompleted = new java.util.ArrayList<>();
        java.util.Map<ComputerGuideData.Objective, Integer> liveProgress = new java.util.IdentityHashMap<>();
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        Dependents taskDependents = dependents(tasks);
        ItemCounts counts = new ItemCounts(player);
        boolean[] pending = new boolean[tasks.size()];
        java.util.Arrays.fill(pending, true);
        int passes = 0;
        do {
            changed = false;
            for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
                if (!pending[taskIndex]) continue;
                ComputerGuideData.Task task = tasks.get(taskIndex);
                if (progress.isComplete(task.id()) || !available(task, progress)) continue;
                pending[taskIndex] = false;
                int revisionBefore = progress.revision();
                boolean requiredDone = true;
                boolean anyRequired = false;
                for (ComputerGuideData.Objective objective : task.objectives()) {
                    boolean objectiveComplete = progress.isObjectiveSatisfied(task.id(), objective.id());
                    if (!objectiveComplete) {
                        boolean allTag = objective.type().equals("item_tag") && objective.tagMode().equals("all");
                        boolean priorItemProgressComplete = objective.type().equals("item") && objective.consume()
                                && progress.objectiveCount(task.id(), objective.id()) >= objective.count();
                        ObjectiveView view = allTag
                                ? evaluateAllTagObjective(player, progress, task, objective, sharedTeamProgress, objective.consume(), counts)
                                : evaluateObjective(player, progress, task, objective, liveProgress, sharedTeamProgress, counts);
                        if (view.changed()) {
                            changed = true;
                            progressChanged = true;
                        }
                        if (view.consumedItems()) {
                            liveProgress.clear();
                            counts.invalidate();
                            java.util.Arrays.fill(pending, true);
                        }
                        if (progress.setObjectiveCount(task.id(), objective.id(), view.progress())) {
                            changed = true;
                            progressChanged = true;
                        }
                        objectiveComplete = view.complete();
                        if (objectiveComplete && objective.consume() && !allTag && !priorItemProgressComplete) {
                            if (progress.isObjectiveConsumed(task.id(), objective.id())) {
                                objectiveComplete = true;
                            } else {
                                boolean liveComplete = evaluateObjective(player, progress, task, objective, liveProgress, false, counts).liveComplete();
                                if (!liveComplete || !consumeObjectiveItems(player, objective, counts)) objectiveComplete = false;
                                else {
                                    if (progress.markObjectiveConsumed(task.id(), objective.id())) {
                                        changed = true;
                                        progressChanged = true;
                                    }
                                    liveProgress.clear();
                                    java.util.Arrays.fill(pending, true);
                                }
                            }
                        }
                        if (objectiveComplete && progress.markObjectiveSatisfied(task.id(), objective.id())) {
                            changed = true;
                            progressChanged = true;
                            com.craisinlord.antos.content.antmail.AntmailNotifier.notifyObjectiveCompleted(player, task, objective);
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
                    for (int dependent : taskDependents.of(task.id())) pending[dependent] = true;
                }
                if (progress.revision() != revisionBefore) pending[taskIndex] = true;
            }
        } while (changed && ++passes < tasks.size() + 1);
        if (progressChanged) {
            computer.setChanged();
            computer.saveWorkspaceProgress();
        }
        boolean teamChanged = account != null && ComputerWorkspaceData.access(player.level().getServer()).mergeTeamProgress(account.accountId(), progress);
        if (progressChanged || teamChanged) invalidateEvaluations();
        for (Identifier taskId : newlyCompleted) ComputerTaskCompletionEvents.fire(player, taskId);
    }

    private static void unlockArchiveRewards(ComputerWorkspace computer, ComputerGuideData.Task task) {
        for (ComputerGuideData.TaskReward reward : task.rewards()) {
            if (!reward.type().equals("archive")) continue;
            if (ComputerGuideData.entry(reward.target()) != null) computer.taskProgress().unlockArchiveEntry(reward.target());
            else AntOS.LOGGER.warn("Task {} rewards missing Archive entry {}", task.id(), reward.target());
        }
    }

    public static List<ComputerGuideData.TaskReward> deliverRewards(ServerPlayer player, Identifier sourceId, List<ComputerGuideData.TaskReward> rewards, Map<Integer, String> choices) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        List<ComputerGuideData.TaskReward> receipt = grantRewards(player, sourceId, rewards, choices, stacks);
        dropRewardCrates(player, stacks);
        return receipt;
    }

    private static List<ComputerGuideData.TaskReward> grantRewards(ServerPlayer player, Identifier sourceId, List<ComputerGuideData.TaskReward> rewards,
                                                                   Map<Integer, String> choices, List<ItemStack> output) {
        if (rewards.isEmpty()) return List.of();
        List<ComputerGuideData.TaskReward> receipt = new java.util.ArrayList<>();
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int rewardIndex = 0; rewardIndex < rewards.size(); rewardIndex++) {
            ComputerGuideData.TaskReward reward = rewards.get(rewardIndex);
            switch (reward.type()) {
                case "item" -> {
                    addRewardItem(reward.itemId(), reward.count(), stacks, 108);
                }
                case "experience" -> { player.giveExperiencePoints(reward.experiencePoints()); receipt.add(reward); }
                case "experience_levels" -> { player.giveExperienceLevels(reward.count()); receipt.add(reward); }
                case "antcoins" -> {
                    var account = accountForProgress(player);
                    if (account != null) {
                        com.craisinlord.antos.content.antazon.AntazonServerData.access(player.level().getServer()).credit(account.accountId(), reward.antcoins());
                        receipt.add(reward);
                    }
                }
                case "item_pool" -> {
                    String selected = choices.get(rewardIndex);
                    if (reward.mode().equals("choice") && selected == null) continue;
                    List<ItemStack> poolStacks = new java.util.ArrayList<>();
                    int capacity = Math.max(0, 108 - totalRewardItems(stacks));
                    if (reward.mode().equals("random")) rollRewardPool(reward.poolId(), player, 1, poolStacks, capacity);
                    else addChosenPoolOutcome(reward.poolId(), selected, 1, poolStacks, capacity);
                    stacks.addAll(poolStacks);
                }
                case "choice" -> {
                    int optionIndex = selectedChoiceIndex(choices.get(rewardIndex));
                    if (optionIndex >= 0 && optionIndex < reward.options().size())
                        deliverChoiceOption(player, sourceId, reward.options().get(optionIndex), stacks);
                }
                case "advancement" -> {
                    var advancement = player.level().getServer().getAdvancements().get(reward.target());
                    if (advancement == null) {
                        AntOS.LOGGER.warn("Task {} rewards missing advancement {}", sourceId, reward.target());
                    } else {
                        advancement.value().criteria().keySet().forEach(criterion ->
                                player.getAdvancements().award(advancement, criterion));
                        receipt.add(reward);
                    }
                }
                case "effect" -> {
                    var effect = BuiltInRegistries.MOB_EFFECT.get(reward.target()).orElse(null);
                    if (effect == null) {
                        AntOS.LOGGER.warn("Task {} rewards missing status effect {}", sourceId, reward.target());
                    } else {
                        player.addEffect(new MobEffectInstance(effect, reward.durationTicks(), reward.amplifier()));
                        receipt.add(reward);
                    }
                }
                case "archive" -> {
                }
                case "mail" -> { if (deliverMailReward(player, sourceId, reward)) receipt.add(reward); }
            }
        }
        if (!stacks.isEmpty()) {
            Map<Identifier, Integer> items = new java.util.LinkedHashMap<>();
            for (ItemStack stack : stacks) items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
            items.forEach((item, count) -> receipt.add(new ComputerGuideData.TaskReward("item", item, count, 0, null, "", "", "", 0, 0, null, "", 0L)));
            output.addAll(stacks);
        }
        return List.copyOf(receipt);
    }

    public static int dropRewardCrates(ServerPlayer player, List<ItemStack> stacks) {
        List<List<ItemStack>> crates = packRewardCrates(stacks);
        for (List<ItemStack> crate : crates) dropRewardCrate(player, crate);
        return crates.size();
    }

    public static List<List<ItemStack>> packRewardCrates(List<ItemStack> stacks) {
        List<List<ItemStack>> crates = new java.util.ArrayList<>();
        if (stacks == null || stacks.isEmpty()) return crates;
        List<ItemStack> current = new java.util.ArrayList<>();
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = stack.copy();
            while (!remaining.isEmpty()) {
                if (count >= CRATE_ITEM_LIMIT) {
                    crates.add(current);
                    current = new java.util.ArrayList<>();
                    count = 0;
                }
                int amount = Math.min(remaining.getCount(), CRATE_ITEM_LIMIT - count);
                current.add(remaining.split(amount));
                count += amount;
            }
        }
        if (!current.isEmpty()) crates.add(current);
        return crates;
    }

    private static int selectedChoiceIndex(String selection) {
        if (selection == null || !selection.startsWith("option:")) return -1;
        try { return Integer.parseInt(selection.substring("option:".length())); } catch (RuntimeException ignored) { return -1; }
    }

    private static void deliverChoiceOption(ServerPlayer player, Identifier sourceId,
                                            ComputerGuideData.RewardOption option, List<ItemStack> stacks) {
        for (ComputerGuideData.TaskReward reward : option.rewards()) {
            switch (reward.type()) {
                case "item" -> addRewardItem(reward.itemId(), reward.count(), stacks, 108);
                case "antcoins" -> {
                    var account = accountForProgress(player);
                    if (account != null) com.craisinlord.antos.content.antazon.AntazonServerData.access(player.level().getServer())
                            .credit(account.accountId(), reward.antcoins());
                }
                case "item_pool" -> rollRewardPool(reward.poolId(), player, 1, stacks,
                        Math.max(0, 108 - totalRewardItems(stacks)));
                case "experience" -> player.giveExperiencePoints(reward.experiencePoints());
                case "experience_levels" -> player.giveExperienceLevels(reward.count());
                case "advancement" -> {
                    var advancement = player.level().getServer().getAdvancements().get(reward.target());
                    if (advancement != null) advancement.value().criteria().keySet().forEach(criterion -> player.getAdvancements().award(advancement, criterion));
                    else AntOS.LOGGER.warn("Task {} choice {} rewards missing advancement {}", sourceId, option.id(), reward.target());
                }
                case "effect" -> {
                    var effect = BuiltInRegistries.MOB_EFFECT.get(reward.target()).orElse(null);
                    if (effect != null) player.addEffect(new MobEffectInstance(effect, reward.durationTicks(), reward.amplifier()));
                    else AntOS.LOGGER.warn("Task {} choice {} rewards missing status effect {}", sourceId, option.id(), reward.target());
                }
                case "mail" -> deliverMailReward(player, sourceId, reward);
            }
        }
    }

    private static void rollRewardPool(Identifier poolId, ServerPlayer player, int multiplier, List<ItemStack> output, int itemLimit) {
        if (poolId == null || multiplier < 1 || totalRewardItems(output) >= itemLimit) return;
        ComputerGuideData.RewardPool pool = ComputerGuideData.taskRewardPool(poolId);
        if (pool == null || pool.entries().isEmpty()) return;
        int totalWeight = pool.entries().stream().mapToInt(ComputerGuideData.RewardPoolEntry::weight).sum();
        int choice = player.getRandom().nextInt(totalWeight);
        for (ComputerGuideData.RewardPoolEntry entry : pool.entries()) {
            choice -= entry.weight();
            if (choice < 0) {
                int count = (int) Math.min(108L, (long) entry.count() * multiplier);
                if (entry.itemId() != null) addRewardItem(entry.itemId(), count, output, itemLimit);
                else for (int draw = 0; draw < count && totalRewardItems(output) < itemLimit; draw++) rollRewardPool(entry.poolId(), player, 1, output, itemLimit);
                return;
            }
        }
    }

    private static void addChosenPoolOutcome(Identifier poolId, String path, int multiplier, List<ItemStack> output, int itemLimit) {
        if (poolId == null || path == null || path.isBlank() || totalRewardItems(output) >= itemLimit) return;
        ComputerGuideData.RewardPool pool = ComputerGuideData.taskRewardPool(poolId);
        if (pool == null) return;
        String[] parts = path.split("\\.", 2);
        int index;
        try { index = Integer.parseInt(parts[0]); } catch (RuntimeException ignored) { return; }
        if (index < 0 || index >= pool.entries().size()) return;
        ComputerGuideData.RewardPoolEntry entry = pool.entries().get(index);
        int count = (int) Math.min(108L, (long) entry.count() * multiplier);
        if (entry.itemId() != null) {
            if (parts.length == 1) addRewardItem(entry.itemId(), count, output, itemLimit);
        } else if (parts.length == 2) addChosenPoolOutcome(entry.poolId(), parts[1], count, output, itemLimit);
    }

    private static void addRewardItem(Identifier itemId, int count, List<ItemStack> output, int itemLimit) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return;
        int remaining = Math.min(Math.min(108, count), Math.max(0, itemLimit - totalRewardItems(output)));
        while (remaining > 0 && totalRewardItems(output) < itemLimit) {
            int amount = Math.min(item.getDefaultMaxStackSize(), remaining);
            output.add(new ItemStack(item, amount));
            remaining -= amount;
        }
    }

    private static int totalRewardItems(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    private static boolean validPoolChoice(Identifier poolId, String path, int depth) {
        if (poolId == null || path == null || path.isBlank() || depth > 8) return false;
        ComputerGuideData.RewardPool pool = ComputerGuideData.taskRewardPool(poolId);
        if (pool == null) return false;
        String[] parts = path.split("\\.", 2);
        int index;
        try { index = Integer.parseInt(parts[0]); } catch (RuntimeException ignored) { return false; }
        if (index < 0 || index >= pool.entries().size()) return false;
        ComputerGuideData.RewardPoolEntry entry = pool.entries().get(index);
        return entry.itemId() != null ? parts.length == 1
                : parts.length == 2 && validPoolChoice(entry.poolId(), parts[1], depth + 1);
    }

    public static void dropRewardCrate(ServerPlayer player, List<ItemStack> stacks) {
        if (player == null || stacks == null || stacks.isEmpty()) return;
        var level = player.level();
        BlockPos landing = rewardLandingSpots(player, 1).stream().findFirst()
                .orElse(player.blockPosition().relative(player.getDirection(), 1));
        RewardDropEntity drop = new RewardDropEntity(AntOSObjects.REWARD_DROP_ENTITY.get(), level);
        drop.setRewards(stacks, player);
        drop.followRecipient();
        drop.prepareDelivery(landing);
        RewardDropEntity.announceIncoming(player);
        level.addFreshEntity(drop);
    }

    private static boolean deliverMailReward(ServerPlayer player, Identifier sourceId, ComputerGuideData.TaskReward reward) {
        if (reward.target() != null) {
            var status = com.craisinlord.antos.content.antmail.AntmailEventData.sendDefinition(player, reward.target());
            return status == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.DELIVERED
                    || status == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.QUEUED;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress recipient = data.deliveryAddressFor(player);
        if (recipient == null) {
            AntOS.LOGGER.warn("Task {} mail reward could not be delivered because player {} has no Antmail profile",
                    sourceId, player.getGameProfile().name());
            return false;
        }
        try {
            AntmailAddress sender = AntmailAddress.ofUsername(reward.sender());
            AntmailMessage message = AntmailMessage.create(sender, recipient, reward.subject(), reward.body(),
                    player.level().getGameTime(), List.of(), sourceId.toString());
            var result = data.deliver(player.level().getServer(), message);
            if (result.status() == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.MAILBOX_FULL
                    || result.status() == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.FAILED) {
                AntOS.LOGGER.warn("Content {} mail reward could not be delivered to {}: {}", sourceId, recipient, result.detail());
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Content {} has an invalid mail reward", sourceId, exception);
            return false;
        }
    }

    private static List<BlockPos> rewardLandingSpots(ServerPlayer player, int stackCount) {
        var level = player.level();
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

    public static JsonObject lookupTasks(ComputerTaskProgress progress) {
        JsonObject result = new JsonObject();
        for (boolean finished : new boolean[] {false, true}) {
            for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
                if (progress.isComplete(task.id()) != finished || !visible(task, progress) || !finished && !available(task, progress)) continue;
                for (ComputerGuideData.Objective objective : task.objectives()) {
                    if (objective.type().equals("item") && !objective.target().isBlank() && !result.has(objective.target()))
                        result.addProperty(objective.target(), task.id().toString());
                }
            }
        }
        return result;
    }

    public static boolean available(ComputerGuideData.Task task, ComputerTaskProgress progress) {
        if (progress.isGranted(task.id())) return true;
        if (!task.hasPrerequisites()) return task.availability().equals("automatic");
        return task.prerequisitesMet(progress::isComplete);
    }

    public static boolean visible(ComputerGuideData.Task task, ComputerTaskProgress progress) {
        if (task.invisibleUntilCompleted() && !progress.isComplete(task.id())) return false;
        if (task.hideUntilDependenciesComplete() && !task.prerequisitesMet(progress::isComplete)) return false;
        return true;
    }

    private static int objectiveProgress(ServerPlayer player, ComputerGuideData.Objective objective, ItemCounts counts) {
        try {
            Identifier target = Identifier.parse(objective.target());
            if (objective.type().equals("disk_installed") || objective.type().equals("program_ending") || objective.type().equals("terminal_command")) {
                var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
                if (account == null) return 0;
                DiskProfileState state = ComputerWorkspaceData.access(player.level().getServer()).diskState(account.accountId());
                if (objective.type().equals("terminal_command")) return state.commandRun(target) ? 1 : 0;
                if (objective.type().equals("program_ending")) return state.endingCount(target, objective.ending());
                return objective.targetCategory().isBlank() ? state.activated(target) ? 1 : 0 : state.categoryCount(target);
            }
            if (objective.type().equals("item")) {
                Item item = BuiltInRegistries.ITEM.getValue(target);
                return counts.count(item);
            }
            if (objective.type().equals("item_tag")) {
                List<Item> items = itemTagItems(player, target);
                int total = 0;
                for (Item item : items) total += counts.count(item);
                return total;
            }
            if (objective.type().equals("advancement")) {
                var advancement = player.level().getServer().getAdvancements().get(target);
                return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone() ? objective.count() : 0;
            }
            if (objective.type().equals("stat")) {
                Identifier id = Identifier.parse(objective.target());
                Stat<?> stat = switch (objective.statType()) {
                    case "custom" -> Stats.CUSTOM.get(id);
                    case "block_mined" -> Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.getValue(id));
                    case "item_crafted" -> Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.getValue(id));
                    case "item_used" -> Stats.ITEM_USED.get(BuiltInRegistries.ITEM.getValue(id));
                    case "item_broken" -> Stats.ITEM_BROKEN.get(BuiltInRegistries.ITEM.getValue(id));
                    case "item_picked_up" -> Stats.ITEM_PICKED_UP.get(BuiltInRegistries.ITEM.getValue(id));
                    case "item_dropped" -> Stats.ITEM_DROPPED.get(BuiltInRegistries.ITEM.getValue(id));
                    case "entity_killed" -> Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.getValue(id));
                    case "entity_killed_by" -> Stats.ENTITY_KILLED_BY.get(BuiltInRegistries.ENTITY_TYPE.getValue(id));
                    default -> null;
                };
                return stat == null ? 0 : player.getStats().getValue(stat);
            }
        } catch (RuntimeException ignored) { }
        return 0;
    }

    private static boolean isEventDrivenObjective(String type) {
        if (type.equals("mail_read") || type.equals("archive_viewed") || ComputerLocationObjectives.TYPES.contains(type)) return true;
        Identifier id = Identifier.tryParse(type);
        return id != null && TaskObjectiveRegistry.isRegistered(id);
    }

    private static ObjectiveView objectiveView(ServerPlayer player, ComputerTaskProgress progress,
                                                ComputerGuideData.Task task, ComputerGuideData.Objective objective, ItemCounts counts) {
        boolean complete = progress.isObjectiveSatisfied(task.id(), objective.id());
        int progressValue = progress.objectiveCount(task.id(), objective.id());
        int goal = objectiveProgressGoal(player, objective);
        if (objective.type().equals("item_tag") && objective.tagMode().equals("any")
                && itemTagItems(player, Identifier.tryParse(objective.target())).isEmpty()) {
            return new ObjectiveView(progressValue, objective.count(), complete, complete, false);
        }
        if (objective.type().equals("item_tag") && objective.tagMode().equals("all")) {
            List<Item> items = itemTagItems(player, Identifier.tryParse(objective.target()));
            progressValue = 0;
            for (Item item : items) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                boolean consumed = progress.isObjectiveItemConsumed(task.id(), objective.id(), id);
                int value = consumed ? objective.count() : objective.sticky() ? progress.objectiveItemCount(task.id(), objective.id(), id)
                        : counts.count(item);
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
                                                    boolean sharedTeamProgress, ItemCounts counts) {
        if (objective.type().equals("item_tag") && objective.tagMode().equals("any")
                && itemTagItems(player, Identifier.tryParse(objective.target())).isEmpty()) {
            return new ObjectiveView(progress.objectiveCount(task.id(), objective.id()), objective.count(), false, false, false);
        }
        if (objective.type().equals("item_tag") && objective.tagMode().equals("all")) {
            List<Item> items = itemTagItems(player, Identifier.tryParse(objective.target()));
            int met = 0;
            boolean changed = false;
            for (Item item : items) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                int live = counts.count(item);
                int value = objective.sticky() ? Math.max(live, progress.objectiveItemCount(task.id(), objective.id(), id)) : live;
                if (sharedTeamProgress && objective.sticky()) value = Math.max(value, progress.objectiveItemCount(task.id(), objective.id(), id));
                if (objective.sticky()) changed |= progress.setObjectiveItemCount(task.id(), objective.id(), id, value);
                if (value >= objective.count()) met++;
            }
            boolean liveComplete = !items.isEmpty() && items.stream().allMatch(item -> counts.count(item) >= objective.count());
            return new ObjectiveView(met, items.size(), !items.isEmpty() && met == items.size(), liveComplete, changed);
        }
        int live = isEventDrivenObjective(objective.type())
                ? progress.objectiveCount(task.id(), objective.id())
                : liveProgress.computeIfAbsent(objective, value -> objectiveProgress(player, value, counts));
        int current = objective.sticky() ? Math.max(live, progress.objectiveCount(task.id(), objective.id())) : live;
        if (sharedTeamProgress && objective.sticky()) current = Math.max(current, progress.objectiveCount(task.id(), objective.id()));
        int value = Math.min(objective.count(), current);
        boolean liveComplete = live >= objective.count();
        return new ObjectiveView(value, objective.count(), value >= objective.count(), liveComplete, false);
    }

    private static ObjectiveView evaluateAllTagObjective(ServerPlayer player, ComputerTaskProgress progress,
                                                          ComputerGuideData.Task task, ComputerGuideData.Objective objective,
                                                          boolean sharedTeamProgress, boolean consume, ItemCounts counts) {
        List<Item> items = itemTagItems(player, Identifier.tryParse(objective.target()));
        int met = 0;
        boolean changed = false;
        boolean consumedItems = false;
        for (Item item : items) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            boolean itemConsumed = progress.isObjectiveItemConsumed(task.id(), objective.id(), itemId);
            int live = counts.count(item);
            int previous = progress.objectiveItemCount(task.id(), objective.id(), itemId);
            int value = Math.min(objective.count(), objective.sticky() ? Math.max(live, previous) : live);
            if (sharedTeamProgress && objective.sticky()) value = Math.max(value, previous);
            if (consume && !itemConsumed && live >= objective.count()) {
                boolean consumedItem = consumeItem(player, item, objective.count());
                counts.invalidate();
                if (consumedItem) {
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
            return itemTagItems(player, Identifier.tryParse(objective.target())).size();
        return objective.count();
    }

    private static List<Item> itemTagItems(ServerPlayer player, Identifier tagId) {
        if (player == null || tagId == null) return List.of();
        var registry = player.registryAccess().lookupOrThrow(Registries.ITEM);
        var tag = registry.get(TagKey.create(Registries.ITEM, tagId));
        if (tag.isEmpty()) {
            if (MISSING_ITEM_TAGS.add(tagId)) AntOS.LOGGER.warn("AntOS task references missing item tag {}", tagId);
            if (TAG_ITEMS.put(tagId, TagItems.MISSING) != TagItems.MISSING) TAG_EPOCH.incrementAndGet();
            return List.of();
        }
        net.minecraft.core.HolderSet.Named<Item> holders = tag.get();
        TagItems cached = TAG_ITEMS.get(tagId);
        if (cached != null && cached.matches(holders)) return cached.items();
        List<Item> items = tag.stream()
                .flatMap(holderSet -> holderSet.stream().map(holder -> holder.value()))
                .distinct().sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        if (items.size() > 128) {
            if (OVERSIZED_ITEM_TAGS.add(tagId)) AntOS.LOGGER.warn("AntOS task item tag {} contains {} items; objectives using tags are limited to 128 members", tagId, items.size());
            items = List.of();
        }
        TAG_ITEMS.put(tagId, TagItems.of(holders, items));
        TAG_EPOCH.incrementAndGet();
        return items;
    }

    private static void validateTagCache(ServerPlayer player) {
        if (TAG_ITEMS.isEmpty()) return;
        var registry = player.registryAccess().lookupOrThrow(Registries.ITEM);
        for (Map.Entry<Identifier, TagItems> entry : TAG_ITEMS.entrySet()) {
            var tag = registry.get(TagKey.create(Registries.ITEM, entry.getKey()));
            if (tag.isEmpty() ? entry.getValue() != TagItems.MISSING : !entry.getValue().matches(tag.get())) {
                TAG_ITEMS.remove(entry.getKey(), entry.getValue());
                TAG_EPOCH.incrementAndGet();
            }
        }
    }

    private record TagItems(net.minecraft.core.HolderSet.Named<Item> source, List<net.minecraft.core.Holder<Item>> holders, List<Item> items) {
        static final TagItems MISSING = new TagItems(null, List.of(), List.of());

        static TagItems of(net.minecraft.core.HolderSet.Named<Item> source, List<Item> items) {
            List<net.minecraft.core.Holder<Item>> holders = new java.util.ArrayList<>(source.size());
            for (int index = 0; index < source.size(); index++) holders.add(source.get(index));
            return new TagItems(source, holders, items);
        }

        boolean matches(net.minecraft.core.HolderSet.Named<Item> current) {
            if (current != source || current.size() != holders.size()) return false;
            for (int index = 0; index < holders.size(); index++) if (current.get(index) != holders.get(index)) return false;
            return true;
        }
    }

    private static final class ItemCounts {
        private final ServerPlayer player;
        private Map<Item, Integer> counts;

        private ItemCounts(ServerPlayer player) {
            this.player = player;
        }

        int count(Item item) {
            return counts().getOrDefault(item, 0);
        }

        void invalidate() {
            counts = null;
        }

        Map<Item, Integer> snapshot() {
            return Map.copyOf(counts());
        }

        private Map<Item, Integer> counts() {
            if (counts == null) {
                Map<Item, Integer> values = new java.util.HashMap<>();
                var inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    values.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
                counts = values;
            }
            return counts;
        }
    }

    private record Dependents(List<ComputerGuideData.Task> source, Map<Identifier, int[]> byRequirement) {
        int[] of(Identifier taskId) {
            int[] result = byRequirement.get(taskId);
            return result == null ? new int[0] : result;
        }
    }

    private static Dependents dependents(List<ComputerGuideData.Task> tasks) {
        Dependents current = dependents;
        if (current != null && current.source() == tasks) return current;
        Map<Identifier, List<Integer>> indices = new java.util.HashMap<>();
        for (int index = 0; index < tasks.size(); index++) {
            for (Identifier required : tasks.get(index).prerequisites()) indices.computeIfAbsent(required, ignored -> new java.util.ArrayList<>()).add(index);
        }
        Map<Identifier, int[]> byRequirement = new java.util.HashMap<>();
        indices.forEach((id, values) -> byRequirement.put(id, values.stream().mapToInt(Integer::intValue).toArray()));
        current = new Dependents(tasks, Map.copyOf(byRequirement));
        dependents = current;
        return current;
    }

    private static boolean consumeObjectiveItems(ServerPlayer player, ComputerGuideData.Objective objective, ItemCounts counts) {
        boolean consumed = consumeObjectiveItems(player, objective);
        counts.invalidate();
        return consumed;
    }

    private static boolean consumeObjectiveItems(ServerPlayer player, ComputerGuideData.Objective objective) {
        java.util.Map<Item, Integer> required = new java.util.LinkedHashMap<>();
        if (objective.type().equals("item")) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(objective.target()));
            required.put(item, objective.count());
        } else if (objective.type().equals("item_tag")) {
            if (objective.tagMode().equals("all")) return false;
            List<Item> items = itemTagItems(player, Identifier.tryParse(objective.target()));
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
