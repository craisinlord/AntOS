package com.craisinlord.antos.content.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ComputerTaskProgress {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TASKS = 512;
    private final Set<ResourceLocation> granted = new LinkedHashSet<>();
    private final Set<ResourceLocation> completed = new LinkedHashSet<>();
    private final Set<ResourceLocation> unlockedArchiveEntries = new LinkedHashSet<>();
    private final Map<ResourceLocation, Map<String, Integer>> objectiveCounts = new LinkedHashMap<>();

    public boolean grant(ResourceLocation id) {
        return id != null && granted.add(id);
    }

    public boolean isGranted(ResourceLocation id) {
        return granted.contains(id);
    }

    public boolean isComplete(ResourceLocation id) {
        return completed.contains(id);
    }

    public boolean markComplete(ResourceLocation id) {
        return id != null && completed.add(id);
    }

    public boolean unlockArchiveEntry(ResourceLocation id) {
        return id != null && unlockedArchiveEntries.add(id);
    }

    public Set<ResourceLocation> unlockedArchiveEntries() {
        return Set.copyOf(unlockedArchiveEntries);
    }

    public boolean hasRecoverableProgress() {
        return !granted.isEmpty() || !completed.isEmpty() || !unlockedArchiveEntries.isEmpty() || !objectiveCounts.isEmpty();
    }

    /** Combines a recovered/profile snapshot without regressing any local progress. */
    public boolean mergeFrom(ComputerTaskProgress other) {
        if (other == null) return false;
        boolean changed = granted.addAll(other.granted);
        changed |= completed.addAll(other.completed);
        changed |= unlockedArchiveEntries.addAll(other.unlockedArchiveEntries);
        for (Map.Entry<ResourceLocation, Map<String, Integer>> task : other.objectiveCounts.entrySet()) {
            for (Map.Entry<String, Integer> objective : task.getValue().entrySet()) {
                int current = objectiveCount(task.getKey(), objective.getKey());
                if (objective.getValue() > current) {
                    setObjectiveCount(task.getKey(), objective.getKey(), objective.getValue());
                    changed = true;
                }
            }
        }
        return changed;
    }

    public int objectiveCount(ResourceLocation taskId, String objectiveId) {
        return objectiveCounts.getOrDefault(taskId, Map.of()).getOrDefault(objectiveId, 0);
    }

    public boolean setObjectiveCount(ResourceLocation taskId, String objectiveId, int count) {
        int safeCount = Math.max(0, Math.min(1_000_000, count));
        Map<String, Integer> taskCounts = objectiveCounts.get(taskId);
        Integer previous = taskCounts == null ? null : taskCounts.get(objectiveId);
        if (previous == null && safeCount == 0) return false;
        if (previous != null && previous == safeCount) return false;
        objectiveCounts.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>()).put(objectiveId, safeCount);
        return true;
    }

    /** Clears task grants and completion state while preserving Archive unlocks. */
    public boolean clearTaskProgress() {
        boolean changed = !granted.isEmpty() || !completed.isEmpty() || !objectiveCounts.isEmpty();
        granted.clear();
        completed.clear();
        objectiveCounts.clear();
        return changed;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.put("Granted", writeIds(granted));
        tag.put("Completed", writeIds(completed));
        tag.put("UnlockedArchiveEntries", writeIds(unlockedArchiveEntries));
        ListTag tasks = new ListTag();
        for (Map.Entry<ResourceLocation, Map<String, Integer>> entry : objectiveCounts.entrySet()) {
            if (tasks.size() >= MAX_TASKS) break;
            CompoundTag task = new CompoundTag();
            task.putString("Id", entry.getKey().toString());
            ListTag objectives = new ListTag();
            for (Map.Entry<String, Integer> progress : entry.getValue().entrySet()) {
                CompoundTag objective = new CompoundTag();
                objective.putString("Id", progress.getKey());
                objective.putInt("Count", progress.getValue());
                objectives.add(objective);
                if (objectives.size() >= 64) break;
            }
            task.put("Objectives", objectives);
            tasks.add(task);
        }
        tag.put("ObjectiveCounts", tasks);
        return tag;
    }

    public void load(CompoundTag tag) {
        granted.clear();
        completed.clear();
        unlockedArchiveEntries.clear();
        objectiveCounts.clear();
        readIds(tag.getList("Granted", 8), granted);
        readIds(tag.getList("Completed", 8), completed);
        readIds(tag.getList("UnlockedArchiveEntries", 8), unlockedArchiveEntries);
        ListTag tasks = tag.getList("ObjectiveCounts", 10);
        for (int taskIndex = 0; taskIndex < tasks.size() && objectiveCounts.size() < MAX_TASKS; taskIndex++) {
            CompoundTag task = tasks.getCompound(taskIndex);
            ResourceLocation taskId = parse(task.getString("Id"));
            if (taskId == null) continue;
            Map<String, Integer> values = new LinkedHashMap<>();
            ListTag objectives = task.getList("Objectives", 10);
            for (int objectiveIndex = 0; objectiveIndex < objectives.size() && values.size() < 64; objectiveIndex++) {
                CompoundTag objective = objectives.getCompound(objectiveIndex);
                String objectiveId = objective.getString("Id");
                if (objectiveId.length() <= 128 && !objectiveId.isBlank()) {
                    values.putIfAbsent(objectiveId, Math.max(0, Math.min(1_000_000, objective.getInt("Count"))));
                }
            }
            objectiveCounts.putIfAbsent(taskId, values);
        }
    }

    private static ListTag writeIds(Set<ResourceLocation> values) {
        ListTag result = new ListTag();
        for (ResourceLocation id : values) {
            if (result.size() >= MAX_TASKS) break;
            result.add(StringTag.valueOf(id.toString()));
        }
        return result;
    }

    private static void readIds(ListTag list, Set<ResourceLocation> values) {
        for (int index = 0; index < list.size() && values.size() < MAX_TASKS; index++) {
            ResourceLocation id = parse(list.getString(index));
            if (id != null) values.add(id);
        }
    }

    private static ResourceLocation parse(String value) {
        try { return ResourceLocation.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }
}
