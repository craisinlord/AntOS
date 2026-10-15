package com.craisinlord.antos.content.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ComputerTaskProgress {
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_TASKS = 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 8192;
    private static final int MAX_OBJECTIVES_PER_TASK = 256;
    private static final int MAX_TAG_ITEMS_PER_OBJECTIVE = 128;
    private final Set<Identifier> granted = new LinkedHashSet<>();
    private final Set<Identifier> completed = new LinkedHashSet<>();
    private final Set<Identifier> unlockedArchiveEntries = new LinkedHashSet<>();
    private final Map<Identifier, Map<String, Integer>> objectiveCounts = new LinkedHashMap<>();
    private final Map<Identifier, Map<String, Map<Identifier, Integer>>> objectiveItemCounts = new LinkedHashMap<>();
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

    public boolean grant(Identifier id) {
        return touched(id != null && granted.add(id));
    }

    public boolean isGranted(Identifier id) {
        return granted.contains(id);
    }

    public boolean isComplete(Identifier id) {
        return completed.contains(id);
    }

    public boolean markComplete(Identifier id) {
        return touched(id != null && completed.add(id));
    }

    public boolean unlockArchiveEntry(Identifier id) {
        return touched(id != null && unlockedArchiveEntries.add(id));
    }

    public Set<Identifier> unlockedArchiveEntries() {
        return Set.copyOf(unlockedArchiveEntries);
    }

    public boolean hasRecoverableProgress() {
        return !granted.isEmpty() || !completed.isEmpty() || !unlockedArchiveEntries.isEmpty() || !objectiveCounts.isEmpty()
                || !objectiveItemCounts.isEmpty() || !satisfiedObjectives.isEmpty() || !consumedObjectives.isEmpty()
                || !consumedObjectiveItems.isEmpty();
    }

    public ComputerTaskProgress copy() {
        ComputerTaskProgress copy = new ComputerTaskProgress();
        copy.granted.addAll(granted);
        copy.completed.addAll(completed);
        copy.unlockedArchiveEntries.addAll(unlockedArchiveEntries);
        objectiveCounts.forEach((taskId, counts) -> copy.objectiveCounts.put(taskId, new LinkedHashMap<>(counts)));
        objectiveItemCounts.forEach((taskId, objectives) -> {
            Map<String, Map<Identifier, Integer>> objectiveCopy = new LinkedHashMap<>();
            objectives.forEach((objectiveId, items) -> objectiveCopy.put(objectiveId, new LinkedHashMap<>(items)));
            copy.objectiveItemCounts.put(taskId, objectiveCopy);
        });
        copy.satisfiedObjectives.addAll(satisfiedObjectives);
        copy.consumedObjectives.addAll(consumedObjectives);
        copy.consumedObjectiveItems.addAll(consumedObjectiveItems);
        return copy;
    }

    public boolean sameContent(ComputerTaskProgress other) {
        if (other == this) return true;
        return other != null && granted.equals(other.granted) && completed.equals(other.completed)
                && unlockedArchiveEntries.equals(other.unlockedArchiveEntries) && objectiveCounts.equals(other.objectiveCounts)
                && objectiveItemCounts.equals(other.objectiveItemCounts) && satisfiedObjectives.equals(other.satisfiedObjectives)
                && consumedObjectives.equals(other.consumedObjectives) && consumedObjectiveItems.equals(other.consumedObjectiveItems);
    }

    public boolean mergeFrom(ComputerTaskProgress other) {
        if (other == null) return false;
        boolean changed = touched(granted.addAll(other.granted));
        changed |= touched(completed.addAll(other.completed));
        changed |= touched(unlockedArchiveEntries.addAll(other.unlockedArchiveEntries));
        for (Map.Entry<Identifier, Map<String, Integer>> task : other.objectiveCounts.entrySet()) {
            for (Map.Entry<String, Integer> objective : task.getValue().entrySet()) {
                int current = objectiveCount(task.getKey(), objective.getKey());
                if (objective.getValue() > current) {
                    setObjectiveCount(task.getKey(), objective.getKey(), objective.getValue());
                    changed = true;
                }
            }
        }
        for (Map.Entry<Identifier, Map<String, Map<Identifier, Integer>>> task : other.objectiveItemCounts.entrySet()) {
            for (Map.Entry<String, Map<Identifier, Integer>> objective : task.getValue().entrySet()) {
                for (Map.Entry<Identifier, Integer> item : objective.getValue().entrySet()) {
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

    public int objectiveCount(Identifier taskId, String objectiveId) {
        return objectiveCounts.getOrDefault(taskId, Map.of()).getOrDefault(objectiveId, 0);
    }

    public boolean setObjectiveCount(Identifier taskId, String objectiveId, int count) {
        int safeCount = Math.max(0, Math.min(1_000_000, count));
        Map<String, Integer> taskCounts = objectiveCounts.get(taskId);
        Integer previous = taskCounts == null ? null : taskCounts.get(objectiveId);
        if (previous == null && safeCount == 0) return false;
        if (previous != null && previous == safeCount) return false;
        objectiveCounts.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>()).put(objectiveId, safeCount);
        revision++;
        return true;
    }

    public int objectiveItemCount(Identifier taskId, String objectiveId, Identifier itemId) {
        return objectiveItemCounts.getOrDefault(taskId, Map.of()).getOrDefault(objectiveId, Map.of()).getOrDefault(itemId, 0);
    }

    public boolean setObjectiveItemCount(Identifier taskId, String objectiveId, Identifier itemId, int count) {
        if (taskId == null || objectiveId == null || itemId == null || objectiveId.isBlank()) return false;
        Map<Identifier, Integer> items = objectiveItemCounts.getOrDefault(taskId, Map.of()).getOrDefault(objectiveId, Map.of());
        int safeCount = Math.max(0, Math.min(1_000_000, count));
        Integer previous = items.get(itemId);
        if (previous == null && safeCount == 0 || previous != null && previous == safeCount) return false;
        objectiveItemCounts.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(objectiveId, ignored -> new LinkedHashMap<>()).put(itemId, safeCount);
        revision++;
        return true;
    }

    public boolean isObjectiveSatisfied(Identifier taskId, String objectiveId) {
        return satisfiedObjectives.contains(new ObjectiveKey(taskId, objectiveId));
    }

    public boolean markObjectiveSatisfied(Identifier taskId, String objectiveId) {
        return touched(taskId != null && objectiveId != null && satisfiedObjectives.add(new ObjectiveKey(taskId, objectiveId)));
    }

    public boolean isObjectiveConsumed(Identifier taskId, String objectiveId) {
        return consumedObjectives.contains(new ObjectiveKey(taskId, objectiveId));
    }

    public boolean markObjectiveConsumed(Identifier taskId, String objectiveId) {
        return touched(taskId != null && objectiveId != null && consumedObjectives.add(new ObjectiveKey(taskId, objectiveId)));
    }

    public boolean isObjectiveItemConsumed(Identifier taskId, String objectiveId, Identifier itemId) {
        return consumedObjectiveItems.contains(new ItemObjectiveKey(taskId, objectiveId, itemId));
    }

    public boolean markObjectiveItemConsumed(Identifier taskId, String objectiveId, Identifier itemId) {
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
        for (Map.Entry<Identifier, Map<String, Integer>> entry : objectiveCounts.entrySet()) {
            if (tasks.size() >= MAX_TASKS) break;
            CompoundTag task = new CompoundTag();
            task.putString("Id", entry.getKey().toString());
            ListTag objectives = new ListTag();
            for (Map.Entry<String, Integer> progress : entry.getValue().entrySet()) {
                CompoundTag objective = new CompoundTag();
                objective.putString("Id", progress.getKey());
                objective.putInt("Count", progress.getValue());
                objectives.add(objective);
                if (objectives.size() >= MAX_OBJECTIVES_PER_TASK) break;
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
            Map<Identifier, Integer> values = objectiveItemCounts.getOrDefault(key.taskId(), Map.of()).getOrDefault(key.objectiveId(), Map.of());
            Set<Identifier> itemIds = new LinkedHashSet<>(values.keySet());
            consumedObjectiveItems.stream().filter(item -> item.taskId().equals(key.taskId()) && item.objectiveId().equals(key.objectiveId()))
                    .forEach(item -> itemIds.add(item.itemId()));
            for (Identifier itemId : itemIds) {
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
        readIds(tag.getListOrEmpty("Granted"), granted, MAX_TASKS);
        readIds(tag.getListOrEmpty("Completed"), completed, MAX_TASKS);
        readIds(tag.getListOrEmpty("UnlockedArchiveEntries"), unlockedArchiveEntries, MAX_ARCHIVE_ENTRIES);
        ListTag tasks = tag.getListOrEmpty("ObjectiveCounts");
        for (int taskIndex = 0; taskIndex < tasks.size() && objectiveCounts.size() < MAX_TASKS; taskIndex++) {
            CompoundTag task = tasks.getCompoundOrEmpty(taskIndex);
            Identifier taskId = parse(task.getStringOr("Id", ""));
            if (taskId == null) continue;
            Map<String, Integer> values = new LinkedHashMap<>();
            ListTag objectives = task.getListOrEmpty("Objectives");
            for (int objectiveIndex = 0; objectiveIndex < objectives.size() && values.size() < MAX_OBJECTIVES_PER_TASK; objectiveIndex++) {
                CompoundTag objective = objectives.getCompoundOrEmpty(objectiveIndex);
                String objectiveId = objective.getStringOr("Id", "");
                if (objectiveId.length() <= 128 && !objectiveId.isBlank()) {
                    values.putIfAbsent(objectiveId, Math.max(0, Math.min(1_000_000, objective.getIntOr("Count", 0))));
                }
            }
            objectiveCounts.putIfAbsent(taskId, values);
        }
        ListTag states = tag.getListOrEmpty("ObjectiveStates");
        for (int stateIndex = 0; stateIndex < states.size() && stateIndex < MAX_TASKS * MAX_OBJECTIVES_PER_TASK; stateIndex++) {
            CompoundTag state = states.getCompoundOrEmpty(stateIndex);
            Identifier taskId = parse(state.getStringOr("Task", ""));
            String objectiveId = state.getStringOr("Objective", "");
            if (taskId == null || objectiveId.isBlank() || objectiveId.length() > 128) continue;
            ObjectiveKey key = new ObjectiveKey(taskId, objectiveId);
            if (state.getBooleanOr("Satisfied", false)) satisfiedObjectives.add(key);
            if (state.getBooleanOr("Consumed", false)) consumedObjectives.add(key);
            ListTag items = state.getListOrEmpty("Items");
            for (int itemIndex = 0; itemIndex < items.size() && itemIndex < MAX_TAG_ITEMS_PER_OBJECTIVE; itemIndex++) {
                CompoundTag item = items.getCompoundOrEmpty(itemIndex);
                Identifier itemId = parse(item.getStringOr("Id", ""));
                if (itemId != null) {
                    setObjectiveItemCount(taskId, objectiveId, itemId, item.getIntOr("Count", 0));
                    if (item.getBooleanOr("Consumed", false)) consumedObjectiveItems.add(new ItemObjectiveKey(taskId, objectiveId, itemId));
                }
            }
        }
    }

    private static ListTag writeIds(Set<Identifier> values, int maximum) {
        ListTag result = new ListTag();
        for (Identifier id : values) {
            if (result.size() >= maximum) break;
            result.add(StringTag.valueOf(id.toString()));
        }
        return result;
    }

    private static void readIds(ListTag list, Set<Identifier> values, int maximum) {
        for (int index = 0; index < list.size() && values.size() < maximum; index++) {
            Identifier id = parse(list.getStringOr(index, ""));
            if (id != null) values.add(id);
        }
    }

    private static Identifier parse(String value) {
        try { return Identifier.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private record ObjectiveKey(Identifier taskId, String objectiveId) { }
    private record ItemObjectiveKey(Identifier taskId, String objectiveId, Identifier itemId) { }
}
