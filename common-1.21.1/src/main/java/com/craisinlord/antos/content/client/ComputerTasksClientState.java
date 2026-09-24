package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerTasksClientState {
    private static final Map<String, List<TaskRow>> TASKS = new ConcurrentHashMap<>();
    private static final Set<String> RECEIVED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ERRORS = ConcurrentHashMap.newKeySet();
    private ComputerTasksClientState() { }
    public static void clearAll() { TASKS.clear(); RECEIVED.clear(); ERRORS.clear(); }
    public static void update(ComputerAccessResultPayload result) {
        String prefix = ComputerAccessPayload.TASK_STATE + "\0\0";
        if (!result.data().startsWith(prefix)) return;
        try {
            var root = JsonParser.parseString(result.data().substring(prefix.length())).getAsJsonObject();
            if (root.has("error")) {
                TASKS.put(ComputerWorkspaceClientKey.of(), List.of());
                ERRORS.add(ComputerWorkspaceClientKey.of());
                RECEIVED.add(ComputerWorkspaceClientKey.of());
                return;
            }
            var array = root.getAsJsonArray("tasks"); java.util.ArrayList<TaskRow> rows = new java.util.ArrayList<>();
            if (array != null) for (var element : array) {
                var row = element.getAsJsonObject();
                java.util.ArrayList<TaskObjective> objectives = new java.util.ArrayList<>();
                var objectiveArray = row.getAsJsonArray("objectives");
                if (objectiveArray != null) for (var objectiveElement : objectiveArray) {
                    var objective = objectiveElement.getAsJsonObject();
                    objectives.add(new TaskObjective(objective.get("description").getAsString(), objective.get("progress").getAsInt(),
                            objective.get("count").getAsInt(), objective.get("optional").getAsBoolean()));
                }
                rows.add(new TaskRow(row.get("id").getAsString(), row.get("title").getAsString(),
                        row.get("description").getAsString(), row.get("available").getAsBoolean(),
                        row.get("complete").getAsBoolean(), row.get("visible").getAsBoolean(), row.get("category").getAsString(),
                        row.get("x").getAsInt(), row.get("y").getAsInt(),
                        row.getAsJsonArray("requires").asList().stream().map(value -> value.getAsString()).toList(),
                        row.getAsJsonArray("archive_entries").asList().stream().map(value -> value.getAsString()).toList(),
                        row.get("done").getAsInt(), row.get("total").getAsInt(), row.has("has_rewards") && row.get("has_rewards").getAsBoolean(),
                        row.has("icon_item") ? row.get("icon_item").getAsString() : "",
                        row.has("icon_entity") ? row.get("icon_entity").getAsString() : "",
                        List.copyOf(objectives)));
            }
            TASKS.put(ComputerWorkspaceClientKey.of(), List.copyOf(rows));
            ERRORS.remove(ComputerWorkspaceClientKey.of());
            RECEIVED.add(ComputerWorkspaceClientKey.of());
        } catch (RuntimeException ignored) { }
    }
    public static List<TaskRow> get() { return TASKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static boolean hasSnapshot() { return RECEIVED.contains(ComputerWorkspaceClientKey.of()); }
    public static boolean hasError() { return ERRORS.contains(ComputerWorkspaceClientKey.of()); }
    public static void clear() { TASKS.remove(ComputerWorkspaceClientKey.of()); RECEIVED.remove(ComputerWorkspaceClientKey.of()); ERRORS.remove(ComputerWorkspaceClientKey.of()); }
    public record TaskRow(String id, String title, String description, boolean available, boolean complete, boolean visible,
                          String category, int x, int y, List<String> requires, List<String> archiveEntries,
                          int done, int total, boolean hasRewards, String iconItem, String iconEntity,
                          List<TaskObjective> objectives) { }
    public record TaskObjective(String description, int progress, int count, boolean optional) { }
}
