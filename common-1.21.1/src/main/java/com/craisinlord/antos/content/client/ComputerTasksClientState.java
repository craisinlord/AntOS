package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.google.gson.JsonObject;
import com.craisinlord.antos.content.guide.TaskAppearance;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerTasksClientState {
    private static final Map<String, List<TaskRow>> TASKS = new ConcurrentHashMap<>();
    private static final Set<String> RECEIVED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ERRORS = ConcurrentHashMap.newKeySet();
    private static final Map<String, TeamInfo> TEAMS = new ConcurrentHashMap<>();
    private static final Map<String, List<TeamInvite>> INVITES = new ConcurrentHashMap<>();
    private static final Map<String, String> TEAM_STATUS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, CategoryInfo>> CATEGORIES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, GroupInfo>> GROUPS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, RewardPoolInfo>> REWARD_POOLS = new ConcurrentHashMap<>();
    private static final Map<String, Definitions> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> STATE_HASHES = new ConcurrentHashMap<>();
    private static volatile int revision;
    private ComputerTasksClientState() { }
    public static void clearAll() { TASKS.clear(); RECEIVED.clear(); ERRORS.clear(); TEAMS.clear(); INVITES.clear(); TEAM_STATUS.clear(); CATEGORIES.clear(); GROUPS.clear(); REWARD_POOLS.clear(); DEFINITIONS.clear(); STATE_HASHES.clear(); revision++; }

    public static String requestToken() {
        String key = ComputerWorkspaceClientKey.of();
        Definitions definitions = DEFINITIONS.get(key);
        if (definitions == null || !RECEIVED.contains(key)) return "";
        return definitions.hash() + "\0" + STATE_HASHES.getOrDefault(key, "");
    }

    public static int revision() { return revision; }

    public static void update(ComputerAccessResultPayload result) {
        String teamResultPrefix = ComputerAccessPayload.TEAM_RESULT + "\0";
        if (result.data().startsWith(teamResultPrefix)) {
            String[] status = result.data().split("\u0000", 4);
            if (status.length == 4) TEAM_STATUS.put(ComputerWorkspaceClientKey.of(), status[3]);
            revision++;
            return;
        }
        String prefix = ComputerAccessPayload.TASK_STATE + "\0\0";
        if (!result.data().startsWith(prefix)) return;
        String key = ComputerWorkspaceClientKey.of();
        try {
            var root = JsonParser.parseString(decompress(result.data().substring(prefix.length()))).getAsJsonObject();
            if (root.has("error")) {
                TASKS.put(key, List.of());
                DEFINITIONS.remove(key);
                STATE_HASHES.remove(key);
                ERRORS.add(key);
                RECEIVED.add(key);
                revision++;
                return;
            }
            String definitionsHash = root.has("defs_hash") ? root.get("defs_hash").getAsString() : "";
            if (root.has("definitions") && root.get("definitions").isJsonObject()) {
                DEFINITIONS.put(key, parseDefinitions(definitionsHash, root.getAsJsonObject("definitions")));
            }
            Definitions definitions = DEFINITIONS.get(key);
            if (definitions == null || !definitions.hash().equals(definitionsHash)) {
                DEFINITIONS.remove(key);
                STATE_HASHES.remove(key);
                ComputerNetworking.requestTasks("");
                return;
            }
            if (!root.has("state") || !root.get("state").isJsonObject()) return;
            applyState(key, definitions, root.getAsJsonObject("state"));
            STATE_HASHES.put(key, root.has("state_hash") ? root.get("state_hash").getAsString() : "");
            CATEGORIES.put(key, definitions.categories());
            GROUPS.put(key, definitions.groups());
            REWARD_POOLS.put(key, definitions.rewardPools());
            ERRORS.remove(key);
            RECEIVED.add(key);
            revision++;
        } catch (RuntimeException ignored) { }
    }

    private static final String COMPRESSED_PREFIX = "gz:";
    private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;

    private static String decompress(String data) {
        if (!data.startsWith(COMPRESSED_PREFIX)) return data;
        byte[] compressed = java.util.Base64.getDecoder().decode(data.substring(COMPRESSED_PREFIX.length()));
        try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(compressed.length * 8);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) > 0) {
                if (out.size() + read > MAX_DECOMPRESSED_BYTES) throw new IllegalStateException("Task snapshot is too large");
                out.write(buffer, 0, read);
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Definitions parseDefinitions(String hash, JsonObject root) {
        java.util.ArrayList<TaskDefinition> tasks = new java.util.ArrayList<>();
        if (root.has("tasks") && root.get("tasks").isJsonArray()) for (var element : root.getAsJsonArray("tasks")) {
            var row = element.getAsJsonObject();
            java.util.ArrayList<ObjectiveDefinition> objectives = new java.util.ArrayList<>();
            if (row.has("objectives") && row.get("objectives").isJsonArray()) for (var objectiveElement : row.getAsJsonArray("objectives")) {
                var objective = objectiveElement.getAsJsonObject();
                objectives.add(new ObjectiveDefinition(string(objective, "description"), integer(objective, "count", 1),
                        flag(objective, "optional", false), string(objective, "item")));
            }
            java.util.ArrayList<TaskReward> rewards = new java.util.ArrayList<>();
            if (row.has("rewards") && row.get("rewards").isJsonArray()) for (var rewardElement : row.getAsJsonArray("rewards")) {
                rewards.add(parseTaskReward(rewardElement.getAsJsonObject()));
            }
            tasks.add(new TaskDefinition(row.get("id").getAsString(), string(row, "title"), string(row, "description"), string(row, "category"),
                    flag(row, "has_position", false), integer(row, "x", 0), integer(row, "y", 0),
                    strings(row, "requires"), strings(row, "archive_entries"),
                    integer(row, "total", 0), flag(row, "has_rewards", false),
                    string(row, "icon_item"), string(row, "icon_entity"),
                    flag(row, "green_tint", true), flag(row, "render_mob_from_spawn_egg", true),
                    TaskAppearance.parse(row), List.copyOf(objectives), List.copyOf(rewards), string(row, "icon_texture"),
                    strings(row, "connects"), strings(row, "showcase"), strings(row, "requires_any")));
        }
        java.util.ArrayList<LinkDefinition> links = new java.util.ArrayList<>();
        if (root.has("links") && root.get("links").isJsonArray()) for (var element : root.getAsJsonArray("links")) {
            var row = element.getAsJsonObject();
            links.add(new LinkDefinition(row.get("id").getAsString(), string(row, "title"), string(row, "description"),
                    string(row, "category"), string(row, "link_category"), string(row, "link_group"),
                    flag(row, "has_position", false), integer(row, "x", 0), integer(row, "y", 0),
                    string(row, "icon_item"), string(row, "icon_entity"),
                    row.has("green_tint") && row.get("green_tint").isJsonPrimitive() ? row.get("green_tint").getAsBoolean() : null,
                    row.has("render_mob_from_spawn_egg") && row.get("render_mob_from_spawn_egg").isJsonPrimitive() ? row.get("render_mob_from_spawn_egg").getAsBoolean() : null,
                    TaskAppearance.parse(row), strings(row, "requires"), strings(row, "connects")));
        }
        java.util.LinkedHashMap<String, CategoryInfo> categories = new java.util.LinkedHashMap<>();
        if (root.has("categories") && root.get("categories").isJsonArray()) for (var categoryElement : root.getAsJsonArray("categories")) {
            var category = categoryElement.getAsJsonObject();
            String id = category.get("id").getAsString();
            categories.put(id, new CategoryInfo(id, string(category, "title"), string(category, "icon_item"),
                    string(category, "icon_entity"), integer(category, "sort_order", 0), string(category, "group"),
                    flag(category, "green_tint", true), flag(category, "render_mob_from_spawn_egg", true)));
        }
        java.util.LinkedHashMap<String, GroupInfo> groups = new java.util.LinkedHashMap<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) for (var groupElement : root.getAsJsonArray("groups")) {
            var group = groupElement.getAsJsonObject();
            String id = group.get("id").getAsString();
            groups.put(id, new GroupInfo(id, string(group, "title"), string(group, "icon_item"),
                    string(group, "icon_entity"), integer(group, "sort_order", 0), flag(group, "collapsed", false),
                    flag(group, "green_tint", true), flag(group, "render_mob_from_spawn_egg", true)));
        }
        java.util.LinkedHashMap<String, RewardPoolInfo> rewardPools = new java.util.LinkedHashMap<>();
        if (root.has("reward_pools") && root.get("reward_pools").isJsonArray()) for (var poolElement : root.getAsJsonArray("reward_pools")) {
            var pool = poolElement.getAsJsonObject();
            java.util.ArrayList<RewardPoolEntryInfo> entries = new java.util.ArrayList<>();
            if (pool.has("entries") && pool.get("entries").isJsonArray()) for (var entryElement : pool.getAsJsonArray("entries")) {
                var entry = entryElement.getAsJsonObject();
                entries.add(new RewardPoolEntryInfo(string(entry, "item"), string(entry, "pool"),
                        integer(entry, "weight", 1), integer(entry, "count", 1)));
            }
            rewardPools.put(pool.get("id").getAsString(), new RewardPoolInfo(pool.get("id").getAsString(), List.copyOf(entries)));
        }
        return new Definitions(hash, List.copyOf(tasks), java.util.Collections.unmodifiableMap(categories),
                java.util.Collections.unmodifiableMap(groups), java.util.Collections.unmodifiableMap(rewardPools), List.copyOf(links));
    }

    private static List<String> strings(JsonObject row, String key) {
        if (!row.has(key) || !row.get(key).isJsonArray()) return List.of();
        return row.getAsJsonArray(key).asList().stream().map(value -> value.getAsString()).toList();
    }

    private static String string(JsonObject row, String key) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsString() : "";
    }

    private static int integer(JsonObject row, String key, int fallback) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsInt() : fallback;
    }

    private static long number(JsonObject row, String key, long fallback) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsLong() : fallback;
    }

    private static boolean flag(JsonObject row, String key, boolean fallback) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsBoolean() : fallback;
    }

    private static TaskRow linkRow(LinkDefinition link, Definitions definitions, Map<String, TaskRow> tasksById, List<TaskRow> tasks) {
        CategoryInfo category = link.linkCategory().isBlank() ? null : definitions.categories().get(link.linkCategory());
        GroupInfo group = link.linkGroup().isBlank() ? null : definitions.groups().get(link.linkGroup());
        boolean targetVisible = tasks.stream().anyMatch(task -> task.visible() && (link.linkCategory().isBlank()
                ? group != null && categoryInGroup(definitions, task.category(), link.linkGroup()) : task.category().equals(link.linkCategory())));
        boolean requirementsMet = link.requires().stream().allMatch(id -> tasksById.containsKey(id) && tasksById.get(id).complete());
        String targetTitle = category != null ? category.title() : group != null ? group.title() : categoryTitleKey(link.linkCategory());
        String title = link.title().isBlank() ? targetTitle : link.title();
        String targetIconItem = category != null ? category.iconItem() : group != null ? group.iconItem() : "";
        String targetIconEntity = category != null ? category.iconEntity() : group != null ? group.iconEntity() : "";
        boolean customIcon = !link.iconItem().isBlank() || !link.iconEntity().isBlank();
        String iconItem = customIcon ? link.iconItem() : targetIconItem;
        String iconEntity = customIcon ? link.iconEntity() : targetIconEntity;
        if (iconItem.isBlank() && iconEntity.isBlank()) iconItem = "minecraft:compass";
        boolean targetTint = category != null ? category.greenTint() : group == null || group.greenTint();
        boolean targetSpawnEgg = category != null ? category.renderMobFromSpawnEgg() : group == null || group.renderMobFromSpawnEgg();
        return new TaskRow(link.id(), title, link.description(), true, false, targetVisible && requirementsMet, link.category(),
                link.hasPosition(), link.x(), link.y(), List.of(), List.of(), 0, 0, false, false, false, iconItem, iconEntity,
                link.greenTint() != null ? link.greenTint() : targetTint,
                link.renderMobFromSpawnEgg() != null ? link.renderMobFromSpawnEgg() : targetSpawnEgg,
                link.appearance(), List.of(), List.of(), "", link.connects(), link.linkCategory(), link.linkGroup(), List.of(), List.of());
    }

    private static boolean categoryInGroup(Definitions definitions, String category, String group) {
        CategoryInfo info = definitions.categories().get(category);
        return info != null && info.group().equals(group);
    }

    private static String categoryTitleKey(String category) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(category);
        return id == null ? category : "task.category." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private static TaskReward parseTaskReward(JsonObject reward) {
        java.util.ArrayList<RewardOption> options = new java.util.ArrayList<>();
        if (reward.has("options") && reward.get("options").isJsonArray()) for (var optionElement : reward.getAsJsonArray("options")) {
            var option = optionElement.getAsJsonObject();
            java.util.ArrayList<TaskReward> optionRewards = new java.util.ArrayList<>();
            if (option.has("rewards") && option.get("rewards").isJsonArray()) for (var rewardElement : option.getAsJsonArray("rewards"))
                optionRewards.add(parseTaskReward(rewardElement.getAsJsonObject()));
            options.add(new RewardOption(option.get("id").getAsString(), string(option, "title"), List.copyOf(optionRewards)));
        }
        return new TaskReward(reward.get("type").getAsString(), string(reward, "item"),
                integer(reward, "count", 1), integer(reward, "experience", 0), string(reward, "target"),
                string(reward, "subject"), string(reward, "pool"), string(reward, "mode"), number(reward, "antcoins", 0L),
                List.copyOf(options));
    }

    private static void applyState(String key, Definitions definitions, JsonObject root) {
        if (root.has("team") && root.get("team").isJsonObject()) {
            var team = root.getAsJsonObject("team");
            TEAMS.put(key, new TeamInfo(team.get("id").getAsString(),
                    team.get("owner").getAsBoolean(), team.get("owner_name").getAsString(),
                    team.getAsJsonArray("members").asList().stream().map(value -> value.getAsString()).toList()));
        } else TEAMS.remove(key);
        java.util.ArrayList<TeamInvite> teamInvites = new java.util.ArrayList<>();
        if (root.has("team_invites") && root.get("team_invites").isJsonArray()) {
            for (var inviteElement : root.getAsJsonArray("team_invites")) {
                var invite = inviteElement.getAsJsonObject();
                teamInvites.add(new TeamInvite(invite.get("id").getAsString(), invite.get("owner_name").getAsString(),
                        invite.get("inviter_name").getAsString()));
            }
        }
        INVITES.put(key, List.copyOf(teamInvites));
        var array = root.getAsJsonArray("tasks");
        java.util.ArrayList<TaskRow> rows = new java.util.ArrayList<>();
        int index = 0;
        if (array != null) for (var element : array) {
            if (index >= definitions.tasks().size()) break;
            TaskDefinition definition = definitions.tasks().get(index++);
            var row = element.getAsJsonObject();
            if (!definition.id().equals(row.get("id").getAsString())) throw new IllegalStateException("Task state is out of order");
            var progress = row.getAsJsonArray("progress");
            java.util.ArrayList<TaskObjective> objectives = new java.util.ArrayList<>(definition.objectives().size());
            for (int objectiveIndex = 0; objectiveIndex < definition.objectives().size(); objectiveIndex++) {
                ObjectiveDefinition objective = definition.objectives().get(objectiveIndex);
                int value = 0;
                int goal = objective.count();
                boolean complete = false;
                if (progress != null && objectiveIndex < progress.size()) {
                    var valueElement = progress.get(objectiveIndex);
                    if (valueElement.isJsonObject()) {
                        var state = valueElement.getAsJsonObject();
                        value = state.has("progress") ? state.get("progress").getAsInt() : 0;
                        goal = state.has("goal") ? state.get("goal").getAsInt() : objective.count();
                        complete = state.has("complete") && state.get("complete").getAsBoolean();
                    } else value = valueElement.getAsInt();
                }
                complete |= goal > 0 && value >= goal;
                objectives.add(new TaskObjective(objective.description(), value, objective.count(), objective.optional(), objective.item(), goal, complete));
            }
            rows.add(new TaskRow(definition.id(), definition.title(), definition.description(), flag(row, "available", false),
                    flag(row, "complete", false), flag(row, "visible", false), definition.category(),
                    definition.hasPosition(), definition.x(), definition.y(), definition.requires(), definition.archiveEntries(),
                    integer(row, "done", 0), definition.total(), definition.hasRewards(),
                    flag(row, "claimable", false), flag(row, "claimed", false),
                    definition.iconItem(), definition.iconEntity(), definition.greenTint(), definition.renderMobFromSpawnEgg(), definition.appearance(),
                    List.copyOf(objectives), definition.rewards(), definition.iconTexture(), definition.connects(), "", "", definition.showcase(), definition.requiresAny()));
        }
        Map<String, TaskRow> tasksById = new java.util.HashMap<>();
        for (TaskRow row : rows) tasksById.put(row.id(), row);
        List<TaskRow> taskRows = List.copyOf(rows);
        for (LinkDefinition link : definitions.links()) rows.add(linkRow(link, definitions, tasksById, taskRows));
        TASKS.put(key, List.copyOf(rows));
    }

    public static List<TaskRow> get() { return TASKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static boolean hasSnapshot() { return RECEIVED.contains(ComputerWorkspaceClientKey.of()); }
    public static boolean hasError() { return ERRORS.contains(ComputerWorkspaceClientKey.of()); }
    public static TeamInfo team() { return TEAMS.get(ComputerWorkspaceClientKey.of()); }
    public static List<TeamInvite> invites() { return INVITES.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static String teamStatus() { return TEAM_STATUS.getOrDefault(ComputerWorkspaceClientKey.of(), ""); }
    public static Map<String, CategoryInfo> categories() { return CATEGORIES.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()); }
    public static Map<String, GroupInfo> groups() { return GROUPS.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()); }
    public static RewardPoolInfo rewardPool(String id) { return REWARD_POOLS.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()).get(id); }
    private record CategoryLookup(Map<String, CategoryInfo> source, Map<String, java.util.Optional<CategoryInfo>> resolved) { }
    private static volatile CategoryLookup categoryLookup;

    public static CategoryInfo category(String category) {
        Map<String, CategoryInfo> definitions = categories();
        if (category == null) return resolveCategory(definitions, null);
        CategoryLookup lookup = categoryLookup;
        if (lookup == null || lookup.source() != definitions) {
            lookup = new CategoryLookup(definitions, new ConcurrentHashMap<>());
            categoryLookup = lookup;
        }
        return lookup.resolved().computeIfAbsent(category, key -> java.util.Optional.ofNullable(resolveCategory(definitions, key))).orElse(null);
    }

    private static CategoryInfo resolveCategory(Map<String, CategoryInfo> definitions, String category) {
        CategoryInfo direct = definitions.get(category);
        if (direct != null) return direct;
        var id = net.minecraft.resources.ResourceLocation.tryParse(category);
        return id == null ? null : definitions.get(id.toString());
    }
    public static void clear() { String key = ComputerWorkspaceClientKey.of(); TASKS.remove(key); RECEIVED.remove(key); ERRORS.remove(key); TEAMS.remove(key); INVITES.remove(key); TEAM_STATUS.remove(key); CATEGORIES.remove(key); GROUPS.remove(key); REWARD_POOLS.remove(key); DEFINITIONS.remove(key); STATE_HASHES.remove(key); revision++; }
    public static void clearTransient() { TEAM_STATUS.remove(ComputerWorkspaceClientKey.of()); }
    private record Definitions(String hash, List<TaskDefinition> tasks, Map<String, CategoryInfo> categories, Map<String, GroupInfo> groups,
                               Map<String, RewardPoolInfo> rewardPools, List<LinkDefinition> links) { }
    private record LinkDefinition(String id, String title, String description, String category, String linkCategory, String linkGroup,
                                  boolean hasPosition, int x, int y, String iconItem, String iconEntity, Boolean greenTint, Boolean renderMobFromSpawnEgg,
                                  TaskAppearance appearance, List<String> requires, List<String> connects) { }
    private record TaskDefinition(String id, String title, String description, String category, boolean hasPosition, int x, int y, List<String> requires,
                                  List<String> archiveEntries, int total, boolean hasRewards, String iconItem, String iconEntity,
                                  boolean greenTint, boolean renderMobFromSpawnEgg, TaskAppearance appearance, List<ObjectiveDefinition> objectives, List<TaskReward> rewards, String iconTexture,
                                  List<String> connects, List<String> showcase, List<String> requiresAny) { }
    private record ObjectiveDefinition(String description, int count, boolean optional, String item) { }
    public record CategoryInfo(String id, String title, String iconItem, String iconEntity, int sortOrder, String group, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record GroupInfo(String id, String title, String iconItem, String iconEntity, int sortOrder, boolean collapsed, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record TaskRow(String id, String title, String description, boolean available, boolean complete, boolean visible,
                          String category, boolean hasPosition, int x, int y, List<String> requires, List<String> archiveEntries,
                          int done, int total, boolean hasRewards, boolean claimable, boolean claimed, String iconItem, String iconEntity,
                          boolean greenTint, boolean renderMobFromSpawnEgg, TaskAppearance appearance, List<TaskObjective> objectives, List<TaskReward> rewards, String iconTexture,
                          List<String> connects, String linkCategory, String linkGroup, List<String> showcase, List<String> requiresAny) {
        public boolean isLink() { return !linkCategory.isBlank() || !linkGroup.isBlank(); }
    }
    public record TaskObjective(String description, int progress, int count, boolean optional, String item, int displayGoal, boolean complete) { }
    public record TaskReward(String type, String item, int count, int experience, String target, String subject, String pool, String mode, long antcoins,
                             List<RewardOption> options) { }
    public record RewardOption(String id, String title, List<TaskReward> rewards) { }
    public record RewardPoolInfo(String id, List<RewardPoolEntryInfo> entries) { }
    public record RewardPoolEntryInfo(String item, String pool, int weight, int count) { }
    public record TeamInfo(String id, boolean owner, String ownerName, List<String> members) { }
    public record TeamInvite(String id, String ownerName, String inviterName) { }
}
