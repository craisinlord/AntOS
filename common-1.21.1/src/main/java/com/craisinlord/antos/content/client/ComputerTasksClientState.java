package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.google.gson.JsonObject;
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
    /** Static task definitions, kept between refreshes so the server only resends them after a data reload. */
    private static final Map<String, Definitions> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> STATE_HASHES = new ConcurrentHashMap<>();
    /** Bumped whenever any visible task data changes, so screens can cache derived layout. */
    private static volatile int revision;
    private ComputerTasksClientState() { }
    public static void clearAll() { TASKS.clear(); RECEIVED.clear(); ERRORS.clear(); TEAMS.clear(); INVITES.clear(); TEAM_STATUS.clear(); CATEGORIES.clear(); GROUPS.clear(); DEFINITIONS.clear(); STATE_HASHES.clear(); revision++; }

    /** The "definitionsHash\0stateHash" the server can skip resending. */
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
            var root = JsonParser.parseString(result.data().substring(prefix.length())).getAsJsonObject();
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
                // Our cached definitions are stale and the server assumed otherwise; ask again for everything.
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
            ERRORS.remove(key);
            RECEIVED.add(key);
            revision++;
        } catch (RuntimeException ignored) { }
    }

    private static Definitions parseDefinitions(String hash, JsonObject root) {
        java.util.ArrayList<TaskDefinition> tasks = new java.util.ArrayList<>();
        if (root.has("tasks") && root.get("tasks").isJsonArray()) for (var element : root.getAsJsonArray("tasks")) {
            var row = element.getAsJsonObject();
            java.util.ArrayList<ObjectiveDefinition> objectives = new java.util.ArrayList<>();
            var objectiveArray = row.getAsJsonArray("objectives");
            if (objectiveArray != null) for (var objectiveElement : objectiveArray) {
                var objective = objectiveElement.getAsJsonObject();
                objectives.add(new ObjectiveDefinition(objective.get("description").getAsString(), objective.get("count").getAsInt(),
                        objective.get("optional").getAsBoolean(), objective.has("item") ? objective.get("item").getAsString() : ""));
            }
            java.util.ArrayList<TaskReward> rewards = new java.util.ArrayList<>();
            if (row.has("rewards") && row.get("rewards").isJsonArray()) for (var rewardElement : row.getAsJsonArray("rewards")) {
                var reward = rewardElement.getAsJsonObject();
                rewards.add(new TaskReward(reward.get("type").getAsString(), reward.get("item").getAsString(),
                        reward.get("count").getAsInt(), reward.get("experience").getAsInt(), reward.get("target").getAsString(),
                        reward.get("subject").getAsString()));
            }
            tasks.add(new TaskDefinition(row.get("id").getAsString(), row.get("title").getAsString(),
                    row.get("description").getAsString(), row.get("category").getAsString(),
                    row.get("x").getAsInt(), row.get("y").getAsInt(),
                    row.getAsJsonArray("requires").asList().stream().map(value -> value.getAsString()).toList(),
                    row.getAsJsonArray("archive_entries").asList().stream().map(value -> value.getAsString()).toList(),
                    row.get("total").getAsInt(), row.has("has_rewards") && row.get("has_rewards").getAsBoolean(),
                    row.has("icon_item") ? row.get("icon_item").getAsString() : "",
                    row.has("icon_entity") ? row.get("icon_entity").getAsString() : "",
                    !row.has("green_tint") || !row.get("green_tint").isJsonPrimitive() || row.get("green_tint").getAsBoolean(),
                    !row.has("render_mob_from_spawn_egg") || !row.get("render_mob_from_spawn_egg").isJsonPrimitive() || row.get("render_mob_from_spawn_egg").getAsBoolean(),
                    List.copyOf(objectives), List.copyOf(rewards)));
        }
        java.util.LinkedHashMap<String, CategoryInfo> categories = new java.util.LinkedHashMap<>();
        if (root.has("categories") && root.get("categories").isJsonArray()) for (var categoryElement : root.getAsJsonArray("categories")) {
            var category = categoryElement.getAsJsonObject();
            String id = category.get("id").getAsString();
            categories.put(id, new CategoryInfo(id, category.get("title").getAsString(), category.get("icon_item").getAsString(),
                    category.get("icon_entity").getAsString(), category.get("sort_order").getAsInt(), category.get("group").getAsString(),
                    !category.has("green_tint") || !category.get("green_tint").isJsonPrimitive() || category.get("green_tint").getAsBoolean(),
                    !category.has("render_mob_from_spawn_egg") || !category.get("render_mob_from_spawn_egg").isJsonPrimitive() || category.get("render_mob_from_spawn_egg").getAsBoolean()));
        }
        java.util.LinkedHashMap<String, GroupInfo> groups = new java.util.LinkedHashMap<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) for (var groupElement : root.getAsJsonArray("groups")) {
            var group = groupElement.getAsJsonObject();
            String id = group.get("id").getAsString();
            groups.put(id, new GroupInfo(id, group.get("title").getAsString(), group.get("icon_item").getAsString(),
                    group.get("icon_entity").getAsString(), group.get("sort_order").getAsInt(), group.get("collapsed").getAsBoolean(),
                    !group.has("green_tint") || !group.get("green_tint").isJsonPrimitive() || group.get("green_tint").getAsBoolean(),
                    !group.has("render_mob_from_spawn_egg") || !group.get("render_mob_from_spawn_egg").isJsonPrimitive() || group.get("render_mob_from_spawn_egg").getAsBoolean()));
        }
        return new Definitions(hash, List.copyOf(tasks), java.util.Collections.unmodifiableMap(categories),
                java.util.Collections.unmodifiableMap(groups));
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
            rows.add(new TaskRow(definition.id(), definition.title(), definition.description(), row.get("available").getAsBoolean(),
                    row.get("complete").getAsBoolean(), row.get("visible").getAsBoolean(), definition.category(),
                    definition.x(), definition.y(), definition.requires(), definition.archiveEntries(),
                    row.get("done").getAsInt(), definition.total(), definition.hasRewards(),
                    row.get("claimable").getAsBoolean(), row.get("claimed").getAsBoolean(),
                    definition.iconItem(), definition.iconEntity(), definition.greenTint(), definition.renderMobFromSpawnEgg(),
                    List.copyOf(objectives), definition.rewards()));
        }
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
    public static CategoryInfo category(String category) {
        Map<String, CategoryInfo> definitions = categories();
        CategoryInfo direct = definitions.get(category);
        if (direct != null) return direct;
        var id = net.minecraft.resources.ResourceLocation.tryParse(category);
        return id == null ? null : definitions.get(id.toString());
    }
    public static void clear() { String key = ComputerWorkspaceClientKey.of(); TASKS.remove(key); RECEIVED.remove(key); ERRORS.remove(key); TEAMS.remove(key); INVITES.remove(key); TEAM_STATUS.remove(key); CATEGORIES.remove(key); GROUPS.remove(key); DEFINITIONS.remove(key); STATE_HASHES.remove(key); revision++; }
    /** Drops one-shot messages but keeps the last snapshot, so reopening the computer shows tasks immediately while it refreshes. */
    public static void clearTransient() { TEAM_STATUS.remove(ComputerWorkspaceClientKey.of()); }
    private record Definitions(String hash, List<TaskDefinition> tasks, Map<String, CategoryInfo> categories, Map<String, GroupInfo> groups) { }
    private record TaskDefinition(String id, String title, String description, String category, int x, int y, List<String> requires,
                                  List<String> archiveEntries, int total, boolean hasRewards, String iconItem, String iconEntity,
                                  boolean greenTint, boolean renderMobFromSpawnEgg, List<ObjectiveDefinition> objectives, List<TaskReward> rewards) { }
    private record ObjectiveDefinition(String description, int count, boolean optional, String item) { }
    public record CategoryInfo(String id, String title, String iconItem, String iconEntity, int sortOrder, String group, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record GroupInfo(String id, String title, String iconItem, String iconEntity, int sortOrder, boolean collapsed, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record TaskRow(String id, String title, String description, boolean available, boolean complete, boolean visible,
                          String category, int x, int y, List<String> requires, List<String> archiveEntries,
                          int done, int total, boolean hasRewards, boolean claimable, boolean claimed, String iconItem, String iconEntity,
                          boolean greenTint, boolean renderMobFromSpawnEgg, List<TaskObjective> objectives, List<TaskReward> rewards) { }
    public record TaskObjective(String description, int progress, int count, boolean optional, String item, int displayGoal, boolean complete) { }
    public record TaskReward(String type, String item, int count, int experience, String target, String subject) { }
    public record TeamInfo(String id, boolean owner, String ownerName, List<String> members) { }
    public record TeamInvite(String id, String ownerName, String inviterName) { }
}
