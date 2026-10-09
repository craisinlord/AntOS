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
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_TASKS = 512;
    private static final int MAX_ARCHIVE_ENTRIES = 8192;
    private static final int MAX_OBJECTIVES_PER_TASK = 64;
    private static final int MAX_TAG_ITEMS_PER_OBJECTIVE = 128;
    private final Set<ResourceLocation> granted = new LinkedHashSet<>();
    private final Set<ResourceLocation> completed = new LinkedHashSet<>();
    private final Set<ResourceLocation> unlockedArchiveEntries = new LinkedHashSet<>();
    private final Map<ResourceLocation, Map<String, Integer>> objectiveCounts = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<String, Map<ResourceLocation, Integer>>> objectiveItemCounts = new LinkedHashMap<>();
    private final Set<ObjectiveKey> satisfiedObjectives = new LinkedHashSet<>();
    private final Set<ObjectiveKey> consumedObjectives = new LinkedHashSet<>();
    private final Set<ItemObjectiveKey> consumedObjectiveItems = new LinkedHashSet<>();
    private int revision;

    public int revision() {
        return revision;
    }

    private boolean touched(boolean changed) {
        if (changed) revision++;
        return changed;
    }

    public boolean grant(ResourceLocation id) {
        return touched(id != null && granted.add(id));
    }

    public boolean isGranted(ResourceLocation id) {
        return granted.contains(id);
    }

    public boolean isComplete(ResourceLocation id) {
        return completed.contains(id);
    }

    public boolean markComplete(ResourceLocation id) {
        return touched(id != null && completed.add(id));
    }

    public boolean unlockArchiveEntry(ResourceLocation id) {
        return touched(id != null && unlockedArchiveEntries.add(id));
    }

    public Set<ResourceLocation> unlockedArchiveEntries() {
        return Set.copyOf(unlockedArchiveEntries);
    }

    public boolean hasRecoverableProgress() {
        return !granted.isEmpty() || !completed.isEmpty() || !unlockedArchiveEntries.isEmpty() || !objectiveCounts.isEmpty()
                || !objectiveItemCounts.isEmpty() || !satisfiedObjectives.isEmpty() || !consumedObjectives.isEmpty()
                || !consumedObjectiveItems.isEmpty();
    }

    public boolean mergeFrom(ComputerTaskProgress other) {
        if (other == null) return false;
        boolean changed = touched(granted.addAll(other.granted));
        changed |= touched(completed.addAll(other.completed));
        changed |= touched(unlockedArchiveEntries.addAll(other.unlockedArchiveEntries));
        for (Map.Entry<ResourceLocation, Map<String, Integer>> task : other.objectiveCounts.entrySet()) {
            for (Map.Entry<String, Integer> objective : task.getValue().entrySet()) {
                int current = objectiveCount(task.getKey(), objective.getKey());
                if (objective.getValue() > current) {
                    setObjectiveCount(task.getKey(), objective.getKey(), objective.getValue());
                    changed = true;
                }
            }
        }
        for (Map.Entry<ResourceLocation, Map<String, Map<ResourceLocation, Integer>>> task : other.objectiveItemCounts.entrySet()) {
            for (Map.Entry<String, Map<ResourceLocation, Integer>> objective : task.getValue().entrySet()) {
                for (Map.Entry<ResourceLocation, Integer> item : objective.getValue().entrySet()) {
                    if (item.getValue() > objectiveItemCount(task.getKey(), objective.getKey(), item.getKey())) {
                        setObjectiveItemCount(task.getKey(), objective.getKey(), item.getKey(), item.getValue());
                        changed = true;
                    }
                }
            }
        }
        changed |= touched(satisfiedObjectives.addAll(other.satisfiedObjectives));
        changed |= touched(consumedObjectives.addAll(other.consumedObjectives));
        changed |= touched(consumedObjectiveItems.addAll(other.consumedObjectiveItems));
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
        revision++;
        return true;
    }

    public int objectiveItemCount(ResourceLocation taskId, String objectiveId, ResourceLocation itemId) {
        return objectiveItemCounts.getOrDefault(taskId, Map.of()).getOrDefault(objectiveId, Map.of()).getOrDefault(itemId, 0);
    }

    public boolean setObjectiveItemCount(ResourceLocation taskId, String objectiveId, ResourceLocation itemId, int count) {
        if (taskId == null || objectiveId == null || itemId == null || objectiveId.isBlank()) return false;
        Map<ResourceLocation, Integer> items = objectiveItemCounts.getOrDefault(taskId, Map.of()).getOrDefault(objectiveId, Map.of());
        int safeCount = Math.max(0, Math.min(1_000_000, count));
        Integer previous = items.get(itemId);
        if (previous == null && safeCount == 0 || previous != null && previous == safeCount) return false;
        objectiveItemCounts.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(objectiveId, ignored -> new LinkedHashMap<>()).put(itemId, safeCount);
        revision++;
        return true;
    }

    public boolean isObjectiveSatisfied(ResourceLocation taskId, String objectiveId) {
        return satisfiedObjectives.contains(new ObjectiveKey(taskId, objectiveId));
    }

    public boolean markObjectiveSatisfied(ResourceLocation taskId, String objectiveId) {
        return touched(taskId != null && objectiveId != null && satisfiedObjectives.add(new ObjectiveKey(taskId, objectiveId)));
    }

    public boolean isObjectiveConsumed(ResourceLocation taskId, String objectiveId) {
        return consumedObjectives.contains(new ObjectiveKey(taskId, objectiveId));
    }

    public boolean markObjectiveConsumed(ResourceLocation taskId, String objectiveId) {
        return touched(taskId != null && objectiveId != null && consumedObjectives.add(new ObjectiveKey(taskId, objectiveId)));
    }

    public boolean isObjectiveItemConsumed(ResourceLocation taskId, String objectiveId, ResourceLocation itemId) {
        return consumedObjectiveItems.contains(new ItemObjectiveKey(taskId, objectiveId, itemId));
    }

    public boolean markObjectiveItemConsumed(ResourceLocation taskId, String objectiveId, ResourceLocation itemId) {
        return touched(taskId != null && objectiveId != null && itemId != null
                && consumedObjectiveItems.add(new ItemObjectiveKey(taskId, objectiveId, itemId)));
    }

    public boolean clearTaskProgress() {
        boolean changed = !granted.isEmpty() || !completed.isEmpty() || !objectiveCounts.isEmpty() || !objectiveItemCounts.isEmpty()
                || !satisfiedObjectives.isEmpty() || !consumedObjectives.isEmpty() || !consumedObjectiveItems.isEmpty();
        granted.clear();
        completed.clear();
        objectiveCounts.clear();
        objectiveItemCounts.clear();
        satisfiedObjectives.clear();
        consumedObjectives.clear();
        consumedObjectiveItems.clear();
        return touched(changed);
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.put("Granted", writeIds(granted, MAX_TASKS));
        tag.put("Completed", writeIds(completed, MAX_TASKS));
        tag.put("UnlockedArchiveEntries", writeIds(unlockedArchiveEntries, MAX_ARCHIVE_ENTRIES));
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
        ListTag states = new ListTag();
        Set<ObjectiveKey> allKeys = new LinkedHashSet<>(satisfiedObjectives);
        allKeys.addAll(consumedObjectives);
        objectiveItemCounts.forEach((taskId, objectives) -> objectives.keySet().forEach(objectiveId -> allKeys.add(new ObjectiveKey(taskId, objectiveId))));
        consumedObjectiveItems.forEach(key -> allKeys.add(new ObjectiveKey(key.taskId(), key.objectiveId())));
        for (ObjectiveKey key : allKeys) {
            if (states.size() >= MAX_TASKS * MAX_OBJECTIVES_PER_TASK) break;
            CompoundTag state = new CompoundTag();
            state.putString("Task", key.taskId().toString());
            state.putString("Objective", key.objectiveId());
            state.putBoolean("Satisfied", satisfiedObjectives.contains(key));
            state.putBoolean("Consumed", consumedObjectives.contains(key));
            ListTag itemCounts = new ListTag();
            Map<ResourceLocation, Integer> values = objectiveItemCounts.getOrDefault(key.taskId(), Map.of()).getOrDefault(key.objectiveId(), Map.of());
            Set<ResourceLocation> itemIds = new LinkedHashSet<>(values.keySet());
            consumedObjectiveItems.stream().filter(item -> item.taskId().equals(key.taskId()) && item.objectiveId().equals(key.objectiveId()))
                    .forEach(item -> itemIds.add(item.itemId()));
            for (ResourceLocation itemId : itemIds) {
                if (itemCounts.size() >= MAX_TAG_ITEMS_PER_OBJECTIVE) break;
                CompoundTag item = new CompoundTag();
                item.putString("Id", itemId.toString());
                item.putInt("Count", values.getOrDefault(itemId, 0));
                item.putBoolean("Consumed", consumedObjectiveItems.contains(new ItemObjectiveKey(key.taskId(), key.objectiveId(), itemId)));
                itemCounts.add(item);
            }
            state.put("Items", itemCounts);
            states.add(state);
        }
        tag.put("ObjectiveStates", states);
        return tag;
    }

    public void load(CompoundTag tag) {
        revision++;
        granted.clear();
        completed.clear();
        unlockedArchiveEntries.clear();
        objectiveCounts.clear();
        objectiveItemCounts.clear();
        satisfiedObjectives.clear();
        consumedObjectives.clear();
        consumedObjectiveItems.clear();
        readIds(tag.getList("Granted", 8), granted, MAX_TASKS);
        readIds(tag.getList("Completed", 8), completed, MAX_TASKS);
        readIds(tag.getList("UnlockedArchiveEntries", 8), unlockedArchiveEntries, MAX_ARCHIVE_ENTRIES);
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
        ListTag states = tag.getList("ObjectiveStates", 10);
        for (int stateIndex = 0; stateIndex < states.size() && stateIndex < MAX_TASKS * MAX_OBJECTIVES_PER_TASK; stateIndex++) {
            CompoundTag state = states.getCompound(stateIndex);
            ResourceLocation taskId = parse(state.getString("Task"));
            String objectiveId = state.getString("Objective");
            if (taskId == null || objectiveId.isBlank() || objectiveId.length() > 128) continue;
            ObjectiveKey key = new ObjectiveKey(taskId, objectiveId);
            if (state.getBoolean("Satisfied")) satisfiedObjectives.add(key);
            if (state.getBoolean("Consumed")) consumedObjectives.add(key);
            ListTag items = state.getList("Items", 10);
            for (int itemIndex = 0; itemIndex < items.size() && itemIndex < MAX_TAG_ITEMS_PER_OBJECTIVE; itemIndex++) {
                CompoundTag item = items.getCompound(itemIndex);
                ResourceLocation itemId = parse(item.getString("Id"));
                if (itemId != null) {
                    setObjectiveItemCount(taskId, objectiveId, itemId, item.getInt("Count"));
                    if (item.getBoolean("Consumed")) consumedObjectiveItems.add(new ItemObjectiveKey(taskId, objectiveId, itemId));
                }
            }
        }
    }

    private static ListTag writeIds(Set<ResourceLocation> values, int maximum) {
        ListTag result = new ListTag();
        for (ResourceLocation id : values) {
            if (result.size() >= maximum) break;
            result.add(StringTag.valueOf(id.toString()));
        }
        return result;
    }

    private static void readIds(ListTag list, Set<ResourceLocation> values, int maximum) {
        for (int index = 0; index < list.size() && values.size() < maximum; index++) {
            ResourceLocation id = parse(list.getString(index));
            if (id != null) values.add(id);
        }
    }

    private static ResourceLocation parse(String value) {
        try { return ResourceLocation.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private record ObjectiveKey(ResourceLocation taskId, String objectiveId) { }
    private record ItemObjectiveKey(ResourceLocation taskId, String objectiveId, ResourceLocation itemId) { }
}
