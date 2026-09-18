package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerTasksClientState {
    private static final Map<BlockPos, List<TaskRow>> TASKS = new ConcurrentHashMap<>();
    private static final Set<BlockPos> RECEIVED = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> ERRORS = ConcurrentHashMap.newKeySet();
    private ComputerTasksClientState() { }
    public static void update(ComputerAccessResultPayload result) {
        String prefix = ComputerAccessPayload.TASK_STATE + "\0\0";
        if (!result.data().startsWith(prefix)) return;
        try {
            var root = JsonParser.parseString(result.data().substring(prefix.length())).getAsJsonObject();
            if (root.has("error")) {
                TASKS.put(result.pos(), List.of());
                ERRORS.add(result.pos());
                RECEIVED.add(result.pos());
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
                        row.get("done").getAsInt(), row.get("total").getAsInt(), List.copyOf(objectives)));
            }
            TASKS.put(result.pos(), List.copyOf(rows));
            ERRORS.remove(result.pos());
            RECEIVED.add(result.pos());
        } catch (RuntimeException ignored) { }
    }
    public static List<TaskRow> get(BlockPos pos) { return TASKS.getOrDefault(pos, List.of()); }
    public static boolean hasSnapshot(BlockPos pos) { return RECEIVED.contains(pos); }
    public static boolean hasError(BlockPos pos) { return ERRORS.contains(pos); }
    public static void clear(BlockPos pos) { TASKS.remove(pos); RECEIVED.remove(pos); ERRORS.remove(pos); }
    public record TaskRow(String id, String title, String description, boolean available, boolean complete, boolean visible,
                          String category, int x, int y, List<String> requires, List<String> archiveEntries,
                          int done, int total, List<TaskObjective> objectives) { }
    public record TaskObjective(String description, int progress, int count, boolean optional) { }
}
