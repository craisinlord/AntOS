package com.craisinlord.antos.content.guide;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailValidation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComputerGuideData extends SimplePreparableReloadListener<ComputerGuideData.LoadedData> {
    private static final int MAX_TASK_DEFINITIONS = 512;
    private static final int MAX_TASK_OBJECTIVES = 64;
    private static final String TASK_CATEGORY_DIRECTORY = "computer/task/category";
    private static final String TASK_GROUP_DIRECTORY = "computer/task/group";
    private static final ComputerGuideData INSTANCE = new ComputerGuideData();
    private static final ResourceLocation INTRODUCTION = ResourceLocation.fromNamespaceAndPath("antos", "introduction");
    private static volatile Map<ResourceLocation, Entry> entries = Map.of();
    private static volatile Map<ResourceLocation, Entry> fieldGuideEntries = Map.of();
    private static volatile java.util.Set<ResourceLocation> fieldGuideConflicts = java.util.Set.of();
    private static volatile Map<ResourceLocation, Disk> disks = Map.of();
    private static volatile long diskTextureRevision;
    private static volatile Map<ResourceLocation, DiskCategory> categories = Map.of();
    private static volatile Map<ResourceLocation, Wallpaper> wallpapers = Map.of();
    private static volatile Map<ResourceLocation, Task> tasks = Map.of();
    private static volatile Map<ResourceLocation, TaskCategory> taskCategories = Map.of();
    private static volatile Map<ResourceLocation, TaskGroup> taskGroups = Map.of();
    private static volatile Boolean serverUnlockAllArchiveEntries;
    private static volatile Boolean serverUnlockAllGameEntries;

    private ComputerGuideData() {
    }

    public static ComputerGuideData instance() {
        return INSTANCE;
    }

    public static Entry entry(ResourceLocation id) {
        Entry entry = entries.get(id);
        return entry != null ? entry : fieldGuideEntries.get(id);
    }

    public static boolean replaceFieldGuideEntries(Map<ResourceLocation, Entry> definitions) {
        Map<ResourceLocation, Entry> filtered = new LinkedHashMap<>();
        java.util.Set<ResourceLocation> conflicts = new java.util.LinkedHashSet<>();
        definitions.forEach((id, entry) -> {
            if (entries.containsKey(id)) {
                conflicts.add(id);
            } else {
                filtered.put(id, entry);
            }
        });
        for (ResourceLocation id : conflicts) {
            if (!fieldGuideConflicts.contains(id)) {
                AntOS.LOGGER.warn("Field Guide Archive entry {} conflicts with an existing AntOS entry and was skipped", id);
            }
        }
        fieldGuideConflicts = java.util.Set.copyOf(conflicts);
        Map<ResourceLocation, Entry> replacement = Map.copyOf(filtered);
        if (fieldGuideEntries.equals(replacement)) return false;
        fieldGuideEntries = replacement;
        return true;
    }

    public static Component archiveTitle(Entry entry) {
        String key = entry.titleKey();
        return key.startsWith("literal:") ? Component.literal(key.substring("literal:".length())) : Component.translatable(key);
    }

    private static Map<ResourceLocation, Entry> allEntries() {
        Map<ResourceLocation, Entry> result = new LinkedHashMap<>(fieldGuideEntries);
        result.putAll(entries);
        return result;
    }

    public static Disk disk(ResourceLocation id) {
        return disks.get(id);
    }

    public static DiskCategory category(ResourceLocation id) {
        return categories.get(id);
    }

    public static ResourceLocation diskTexture(ResourceLocation diskId) {
        Disk disk = disks.get(diskId);
        DiskCategory category = disk == null ? null : categories.get(disk.category());
        return category == null ? ResourceLocation.fromNamespaceAndPath("antos", "item/floppy_disk/floppy_disk") : category.texture();
    }

    /** Current server data-pack mapping, sent to clients for vanilla floppy model overrides. */
    public static Map<ResourceLocation, ResourceLocation> diskCategoryMappings() {
        Map<ResourceLocation, ResourceLocation> result = new HashMap<>();
        disks.values().forEach(disk -> result.put(disk.id(), disk.category()));
        return Map.copyOf(result);
    }

    public static long diskTextureRevision() {
        return diskTextureRevision;
    }

    public static Wallpaper wallpaper(ResourceLocation id) {
        if (com.craisinlord.antos.content.computer.ComputerDesktopState.DEFAULT_WALLPAPER.equals(id)) {
            return new Wallpaper(id, "wallpaper.antos.grid_ant", "", "cover");
        }
        return wallpapers.get(id);
    }

    /** Sorted views are cached against the map instance they were built from; a reload swaps the map and rebuilds them. */
    private record SortedView<T>(Map<ResourceLocation, T> source, List<T> sorted) { }
    private static volatile SortedView<Task> sortedTasks;
    private static volatile SortedView<TaskCategory> sortedTaskCategories;
    private static volatile SortedView<TaskGroup> sortedTaskGroups;

    public static List<Task> tasks() {
        Map<ResourceLocation, Task> current = tasks;
        SortedView<Task> view = sortedTasks;
        if (view == null || view.source() != current) {
            view = new SortedView<>(current, current.values().stream()
                    .sorted(Comparator.comparingInt(Task::sortOrder).thenComparing(task -> task.id().toString())).toList());
            sortedTasks = view;
        }
        return view.sorted();
    }

    public static Task task(ResourceLocation id) {
        return id == null ? null : tasks.get(id);
    }

    public static List<TaskCategory> taskCategories() {
        Map<ResourceLocation, TaskCategory> current = taskCategories;
        SortedView<TaskCategory> view = sortedTaskCategories;
        if (view == null || view.source() != current) {
            view = new SortedView<>(current, current.values().stream()
                    .sorted(Comparator.comparingInt(TaskCategory::sortOrder).thenComparing(category -> category.id().toString())).toList());
            sortedTaskCategories = view;
        }
        return view.sorted();
    }

    public static List<TaskGroup> taskGroups() {
        Map<ResourceLocation, TaskGroup> current = taskGroups;
        SortedView<TaskGroup> view = sortedTaskGroups;
        if (view == null || view.source() != current) {
            view = new SortedView<>(current, current.values().stream()
                    .sorted(Comparator.comparingInt(TaskGroup::sortOrder).thenComparing(group -> group.id().toString())).toList());
            sortedTaskGroups = view;
        }
        return view.sorted();
    }

    public static List<Wallpaper> wallpapers() {
        List<Wallpaper> result = new ArrayList<>();
        result.add(wallpaper(com.craisinlord.antos.content.computer.ComputerDesktopState.DEFAULT_WALLPAPER));
        wallpapers.values().stream().sorted(Comparator.comparing(value -> value.id().toString())).forEach(result::add);
        return List.copyOf(result);
    }

    private static boolean unlockAllArchiveEntries() {
        Boolean synced = serverUnlockAllArchiveEntries;
        return synced != null ? synced : AntOSSettings.unlockAllArchiveEntries();
    }

    public static boolean unlockAllGameEntries() {
        // Games are registered on the client, so a server-side false value must not
        // suppress a local client's choice to expose all installed games.
        Boolean synced = serverUnlockAllGameEntries;
        return AntOSSettings.unlockAllGameEntries() || Boolean.TRUE.equals(synced);
    }

    public static List<Entry> entriesFor(List<ResourceLocation> diskIds) {
        return entriesFor(diskIds, List.of());
    }

    public static List<Entry> entriesFor(List<ResourceLocation> diskIds, java.util.Collection<ResourceLocation> additionallyUnlocked) {
        Map<ResourceLocation, Entry> archiveEntries = allEntries();
        if (unlockAllArchiveEntries()) {
            Map<ResourceLocation, Entry> result = new LinkedHashMap<>();
            result.put(INTRODUCTION, introduction());
            result.putAll(archiveEntries);
            return result.values().stream().sorted(Comparator.comparing(entry -> entry.titleKey().toString())).toList();
        }
        Map<ResourceLocation, Entry> result = new LinkedHashMap<>();
        archiveEntries.values().stream().filter(Entry::unlockedByDefault)
                .forEach(entry -> result.put(entry.id(), entry));
        for (ResourceLocation diskId : diskIds) {
            if (INTRODUCTION.equals(diskId)) {
                result.put(INTRODUCTION, introduction());
                continue;
            }
            Disk disk = disks.get(diskId);
            if (disk == null) {
                continue;
            }
            for (ResourceLocation entryId : disk.entries()) {
                Entry entry = archiveEntries.get(entryId);
                if (entry != null) {
                    result.putIfAbsent(entryId, entry);
                }
            }
        }
        for (ResourceLocation entryId : additionallyUnlocked) {
            Entry entry = archiveEntries.get(entryId);
            if (entry != null) result.putIfAbsent(entryId, entry);
        }
        return result.values().stream().sorted(Comparator.comparing(entry -> entry.titleKey().toString())).toList();
    }

    private static Entry introduction() {
        return new Entry(INTRODUCTION, "article", "general", "guide.antos.entry.introduction.title",
                "guide.antos.entry.introduction.subtitle", List.of("guide.antos.entry.introduction.description"),
                "antos:floppy_disk", "", "", "", "", "", "", 0, "", "", "", "", 0.0F, 1.0F, true, false);
    }

    /** Encodes loaded definitions for connected clients. */
    public static String encodeNetworkSnapshot() {
        return encodeNetworkSnapshot(List.of());
    }

    public static String encodeNetworkSnapshot(java.util.Collection<ResourceLocation> unlockedEntries) {
        JsonObject root = new JsonObject();
        JsonArray unlockedArray = new JsonArray();
        unlockedEntries.forEach(id -> unlockedArray.add(id.toString()));
        root.add("unlocked_entries", unlockedArray);
        JsonArray entryArray = new JsonArray();
        allEntries().values().stream().sorted(Comparator.comparing(entry -> entry.id().toString())).forEach(entry -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", entry.id().toString());
            if (!entry.type().equals("article")) object.addProperty("type", entry.type());
            if (!entry.category().equals("general")) object.addProperty("category", entry.category());
            object.addProperty("title", entry.titleKey());
            if (!entry.subtitleKey().isEmpty()) object.addProperty("subtitle", entry.subtitleKey());
            if (!entry.descriptionKeys().isEmpty()) {
                JsonArray descriptions = new JsonArray();
                entry.descriptionKeys().forEach(descriptions::add);
                object.add("description", descriptions);
            }
            addIfPresent(object, "item", entry.itemId());
            addIfPresent(object, "entity", entry.entityId());
            addIfPresent(object, "enchantment", entry.enchantmentId());
            addIfPresent(object, "recipe", entry.recipeId());
            addIfPresent(object, "structure", entry.structureId());
            addIfPresent(object, "structure_tag", entry.structureTagId());
            addIfPresent(object, "dimension", entry.dimensionId());
            if (entry.searchRadius() != 0) object.addProperty("search_radius", entry.searchRadius());
            addIfPresent(object, "locator", entry.locatorId());
            addIfPresent(object, "cover_item", entry.coverItemId());
            addIfPresent(object, "cover_entity", entry.coverEntityId());
            addIfPresent(object, "cover_potion", entry.coverPotionId());
            if (entry.rotation() != 0.0F) object.addProperty("rotation", entry.rotation());
            if (entry.renderScale() != 1.0F) object.addProperty("render_scale", entry.renderScale());
            if (!entry.greenTint()) object.addProperty("green_tint", false);
            if (entry.unlockedByDefault()) object.addProperty("unlocked_by_default", true);
            entryArray.add(object);
        });
        root.add("entries", entryArray);

        JsonArray diskArray = new JsonArray();
        disks.values().stream().sorted(Comparator.comparing(disk -> disk.id().toString())).forEach(disk -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", disk.id().toString());
            object.addProperty("category", disk.category().toString());
            object.addProperty("title", disk.titleKey());
            JsonArray diskEntries = new JsonArray();
            disk.entries().forEach(id -> diskEntries.add(id.toString()));
            object.add("entries", diskEntries);
            object.addProperty("wallpaper", disk.wallpaper());
            JsonArray tasks = new JsonArray();
            disk.tasks().forEach(id -> tasks.add(id.toString()));
            object.add("tasks", tasks);
            diskArray.add(object);
        });
        root.add("disks", diskArray);
        JsonArray categoryArray = new JsonArray();
        categories.values().stream().sorted(Comparator.comparing(category -> category.id().toString())).forEach(category -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", category.id().toString());
            object.addProperty("title", category.titleKey());
            object.addProperty("texture", category.texture().toString());
            categoryArray.add(object);
        });
        root.add("categories", categoryArray);
        JsonArray wallpaperArray = new JsonArray();
        wallpapers.values().stream().sorted(Comparator.comparing(wallpaper -> wallpaper.id().toString())).forEach(wallpaper -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", wallpaper.id().toString());
            object.addProperty("title", wallpaper.titleKey());
            object.addProperty("texture", wallpaper.textureId());
            object.addProperty("fit", wallpaper.fit());
            wallpaperArray.add(object);
        });
        root.add("wallpapers", wallpaperArray);
        root.addProperty("unlockAllArchiveEntries", AntOSSettings.unlockAllArchiveEntries());
        root.addProperty("unlockAllGameEntries", AntOSSettings.unlockAllGameEntries());
        return root.toString();
    }

    private static void addIfPresent(JsonObject object, String key, String value) {
        if (value != null && !value.isEmpty()) object.addProperty(key, value);
    }

    /** Applies the server's definitions on the client. */
    public static void applyNetworkSnapshot(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, Entry> loadedEntries = new HashMap<>();
            Map<ResourceLocation, Disk> loadedDisks = new HashMap<>();
            Map<ResourceLocation, DiskCategory> loadedCategories = new HashMap<>();
            Map<ResourceLocation, Wallpaper> loadedWallpapers = new HashMap<>();
            Map<ResourceLocation, Task> loadedTasks = new HashMap<>();
            JsonArray entryArray = root.getAsJsonArray("entries");
            if (entryArray != null) {
                for (JsonElement element : entryArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseEntry(ResourceLocation.parse(string(object, "id", "")), object, loadedEntries);
                }
            }
            JsonArray diskArray = root.getAsJsonArray("disks");
            if (diskArray != null) {
                for (JsonElement element : diskArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseDisk(ResourceLocation.parse(string(object, "id", "")), object, loadedDisks);
                }
            }
            JsonArray categoryArray = root.getAsJsonArray("categories");
            if (categoryArray != null) {
                for (JsonElement element : categoryArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseCategory(ResourceLocation.parse(string(object, "id", "")), object, loadedCategories);
                }
            }
            JsonArray wallpaperArray = root.getAsJsonArray("wallpapers");
            if (wallpaperArray != null) {
                for (JsonElement element : wallpaperArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseWallpaper(ResourceLocation.parse(string(object, "id", "")), object, loadedWallpapers);
                }
            }
            JsonArray taskArray = root.getAsJsonArray("tasks");
            if (taskArray != null) for (JsonElement element : taskArray) parseTask(element.getAsJsonObject(), loadedTasks);
            Map<ResourceLocation, Entry> loadedFieldGuideEntries = new HashMap<>();
            loadedEntries.entrySet().removeIf(entry -> {
                if (!entry.getKey().getNamespace().equals("fieldguide_compat")) return false;
                loadedFieldGuideEntries.put(entry.getKey(), entry.getValue());
                return true;
            });
            entries = Map.copyOf(loadedEntries);
            fieldGuideEntries = Map.copyOf(loadedFieldGuideEntries);
            disks = Map.copyOf(loadedDisks);
            categories = Map.copyOf(loadedCategories);
            wallpapers = Map.copyOf(loadedWallpapers);
            if (taskArray != null) tasks = Map.copyOf(loadedTasks);
            if (root.has("unlockAllArchiveEntries")) serverUnlockAllArchiveEntries = root.get("unlockAllArchiveEntries").getAsBoolean();
            if (root.has("unlockAllGameEntries")) serverUnlockAllGameEntries = root.get("unlockAllGameEntries").getAsBoolean();
            AntOS.LOGGER.info("Loaded {} computer archive entries and {} disks from the server", entries.size(), disks.size());
        } catch (RuntimeException exception) {
            AntOS.LOGGER.error("Failed to decode computer archive data from the server", exception);
        }
    }

    @Override
    protected LoadedData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Entry> loadedEntries = new HashMap<>();
        Map<ResourceLocation, Disk> loadedDisks = new HashMap<>();
        Map<ResourceLocation, DiskCategory> loadedCategories = new HashMap<>();
        Map<ResourceLocation, Wallpaper> loadedWallpapers = new HashMap<>();
        Map<ResourceLocation, Task> loadedTasks = new HashMap<>();
        Map<ResourceLocation, TaskCategory> loadedTaskCategories = new HashMap<>();
        Map<ResourceLocation, TaskGroup> loadedTaskGroups = new HashMap<>();
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/entry", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/entry");
            try {
                JsonElement json = JsonParser.parseReader(resource.getValue().openAsReader());
                parseEntry(id, json, loadedEntries);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer archive entry {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/disk", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/disk");
            try {
                JsonElement json = JsonParser.parseReader(resource.getValue().openAsReader());
                parseDisk(id, json, loadedDisks);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer archive disk {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/category", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/category");
            try {
                JsonElement json = JsonParser.parseReader(resource.getValue().openAsReader());
                parseCategory(id, json, loadedCategories);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load floppy disk category {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/wallpaper", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/wallpaper");
            try {
                JsonElement json = JsonParser.parseReader(resource.getValue().openAsReader());
                parseWallpaper(id, json, loadedWallpapers);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer wallpaper {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources(TASK_CATEGORY_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), TASK_CATEGORY_DIRECTORY);
            try { parseTaskCategory(id, JsonParser.parseReader(resource.getValue().openAsReader()), loadedTaskCategories); }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task category {}", resource.getKey(), exception); }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources(TASK_GROUP_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), TASK_GROUP_DIRECTORY);
            try { parseTaskGroup(id, JsonParser.parseReader(resource.getValue().openAsReader()), loadedTaskGroups); }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task group {}", resource.getKey(), exception); }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/task", path -> path.getPath().endsWith(".json")).entrySet().stream()
                .filter(resource -> !resource.getKey().getPath().startsWith(TASK_CATEGORY_DIRECTORY + "/") && !resource.getKey().getPath().startsWith(TASK_GROUP_DIRECTORY + "/"))
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (loadedTasks.size() >= MAX_TASK_DEFINITIONS) {
                AntOS.LOGGER.warn("AntOS task limit reached ({}); additional task definitions are skipped", MAX_TASK_DEFINITIONS);
                break;
            }
            ResourceLocation id = resourceId(resource.getKey(), "computer/task");
            try { parseTask(id, JsonParser.parseReader(resource.getValue().openAsReader()), loadedTasks); }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task {}", resource.getKey(), exception); }
        }
        return new LoadedData(Map.copyOf(loadedEntries), Map.copyOf(loadedDisks), Map.copyOf(loadedCategories), Map.copyOf(loadedWallpapers), Map.copyOf(loadedTasks),
                Map.copyOf(loadedTaskCategories), Map.copyOf(loadedTaskGroups));
    }

    @Override
    protected void apply(LoadedData data, ResourceManager resourceManager, ProfilerFiller profiler) {
        entries = data.entries();
        disks = data.disks();
        categories = data.categories();
        diskTextureRevision++;
        wallpapers = data.wallpapers();
        tasks = data.tasks();
        taskCategories = data.taskCategories();
        taskGroups = data.taskGroups();
        validateLoadedData(data, resourceManager);
        int snapshotLength = encodeNetworkSnapshot().length();
        if (snapshotLength > com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS) {
            AntOS.LOGGER.error("AntOS guide sync data is {} characters; it exceeds the network limit of {}. Reduce loaded Archive entries, disks, wallpapers, or descriptions.",
                    snapshotLength, com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS);
        }
        AntOS.LOGGER.info("Loaded AntOS data: {} Archive entries, {} disks, {} wallpapers, {} tasks, {} task categories, and {} task groups",
                entries.size(), disks.size(), wallpapers.size(), tasks.size(), taskCategories.size(), taskGroups.size());
    }

    private static void validateLoadedData(LoadedData data, ResourceManager resourceManager) {
        java.util.Set<ResourceLocation> advancementIds = resourceManager.listResources("advancement", path -> path.getPath().endsWith(".json"))
                .keySet().stream().map(path -> resourceId(path, "advancement")).collect(java.util.stream.Collectors.toSet());
        java.util.Set<ResourceLocation> antmailIds = resourceManager.listResources("antmail/message", path -> path.getPath().endsWith(".json"))
                .keySet().stream().map(path -> resourceId(path, "antmail/message")).collect(java.util.stream.Collectors.toSet());
        for (Disk disk : data.disks().values()) {
            if (!data.categories().containsKey(disk.category())) AntOS.LOGGER.warn("AntOS disk {} refers to missing category {}", disk.id(), disk.category());
            for (ResourceLocation entry : disk.entries()) if (!data.entries().containsKey(entry))
                AntOS.LOGGER.warn("AntOS disk {} refers to missing Archive entry {}", disk.id(), entry);
            for (ResourceLocation task : disk.tasks()) if (!data.tasks().containsKey(task))
                AntOS.LOGGER.warn("AntOS disk {} grants missing task {}", disk.id(), task);
            if (!disk.wallpaper().isBlank()) {
                ResourceLocation wallpaper = ResourceLocation.tryParse(disk.wallpaper());
                if (wallpaper == null || !wallpaper.equals(com.craisinlord.antos.content.computer.ComputerDesktopState.DEFAULT_WALLPAPER)
                        && !data.wallpapers().containsKey(wallpaper)) {
                    AntOS.LOGGER.warn("AntOS disk {} unlocks missing or invalid wallpaper {}", disk.id(), disk.wallpaper());
                }
            }
        }
        Map<String, ResourceLocation> positionedTasks = new HashMap<>();
        for (Task task : data.tasks().values()) {
            if (task.x() >= 0 && task.y() >= 0) {
                String positionKey = task.category() + ":" + task.x() + "," + task.y();
                ResourceLocation previous = positionedTasks.putIfAbsent(positionKey, task.id());
                if (previous != null) AntOS.LOGGER.warn("AntOS tasks {} and {} share map position ({}, {}) in category {}",
                        previous, task.id(), task.x(), task.y(), task.category());
            }
            for (ResourceLocation required : task.requires()) if (!data.tasks().containsKey(required))
                AntOS.LOGGER.warn("AntOS task {} requires missing task {}", task.id(), required);
            for (ResourceLocation entry : task.archiveEntries()) if (!data.entries().containsKey(entry))
                AntOS.LOGGER.warn("AntOS task {} refers to missing Archive entry {}", task.id(), entry);
            for (Objective objective : task.objectives()) {
                if (objective.type().equals("advancement")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !advancementIds.contains(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing advancement {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().equals("item")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing item {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().equals("mail_read")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !antmailIds.contains(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing Antmail message {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().contains(":") && ResourceLocation.tryParse(objective.type()) != null) {
                    ResourceLocation type = ResourceLocation.parse(objective.type());
                    if (!com.craisinlord.antos.api.task.TaskObjectiveRegistry.isRegistered(type)) {
                        AntOS.LOGGER.warn("AntOS task {} objective {} uses unregistered custom objective type {}", task.id(), objective.id(), type);
                    }
                }
            }
            for (TaskReward reward : task.rewards()) {
                if (reward.type().equals("item") && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(reward.itemId()))
                    AntOS.LOGGER.warn("AntOS task {} rewards missing item {}", task.id(), reward.itemId());
                if (reward.type().equals("archive") && !data.entries().containsKey(reward.target()))
                    AntOS.LOGGER.warn("AntOS task {} rewards missing Archive entry {}", task.id(), reward.target());
                if (reward.type().equals("effect") && !net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.containsKey(reward.target()))
                    AntOS.LOGGER.warn("AntOS task {} rewards missing status effect {}", task.id(), reward.target());
                if (reward.type().equals("advancement") && !advancementIds.contains(reward.target()))
                    AntOS.LOGGER.warn("AntOS task {} rewards missing advancement {}", task.id(), reward.target());
            }
        }
        for (Task task : data.tasks().values()) warnMissingIcon("task", task.id(), task.iconItem(), task.iconEntity());
        for (TaskCategory category : data.taskCategories().values()) {
            warnMissingIcon("task category", category.id(), category.iconItem(), category.iconEntity());
            if (category.group() != null && !data.taskGroups().containsKey(category.group()))
                AntOS.LOGGER.warn("AntOS task category {} refers to missing task group {}; it is shown ungrouped", category.id(), category.group());
        }
        for (TaskGroup group : data.taskGroups().values()) warnMissingIcon("task group", group.id(), group.iconItem(), group.iconEntity());
        warnTaskCycles(data.tasks());
    }

    private static void warnMissingIcon(String kind, ResourceLocation id, String iconItem, String iconEntity) {
        if (!iconItem.isBlank()) {
            ResourceLocation item = ResourceLocation.tryParse(iconItem);
            if (item == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item))
                AntOS.LOGGER.warn("AntOS {} {} uses missing icon item {}", kind, id, iconItem);
        }
        if (!iconEntity.isBlank()) {
            ResourceLocation entity = ResourceLocation.tryParse(iconEntity);
            if (entity == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(entity))
                AntOS.LOGGER.warn("AntOS {} {} uses missing icon entity {}", kind, id, iconEntity);
        }
    }

    private static void warnTaskCycles(Map<ResourceLocation, Task> taskData) {
        java.util.Set<ResourceLocation> complete = new java.util.HashSet<>();
        java.util.Set<ResourceLocation> active = new java.util.LinkedHashSet<>();
        java.util.Set<ResourceLocation> reported = new java.util.HashSet<>();
        for (ResourceLocation id : taskData.keySet()) visitTask(id, taskData, complete, active, reported);
    }

    private static void visitTask(ResourceLocation id, Map<ResourceLocation, Task> taskData,
                                  java.util.Set<ResourceLocation> complete, java.util.Set<ResourceLocation> active,
                                  java.util.Set<ResourceLocation> reported) {
        if (complete.contains(id)) return;
        if (!active.add(id)) {
            if (reported.add(id)) AntOS.LOGGER.warn("AntOS task prerequisite cycle detected at {}", id);
            return;
        }
        Task task = taskData.get(id);
        if (task != null) for (ResourceLocation required : task.requires()) if (taskData.containsKey(required))
            visitTask(required, taskData, complete, active, reported);
        active.remove(id);
        complete.add(id);
    }

    private static ResourceLocation resourceId(ResourceLocation path, String directory) {
        String prefix = directory + "/";
        String value = path.getPath().startsWith(prefix) ? path.getPath().substring(prefix.length()) : path.getPath();
        if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        return ResourceLocation.fromNamespaceAndPath(path.getNamespace(), value);
    }

    private static void parseEntry(ResourceLocation id, JsonElement element, Map<ResourceLocation, Entry> target) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String type = string(object, "type", "article");
        String category = string(object, "category", "general");
        String title = string(object, "title", "guide.antos.missing_title");
        String subtitle = string(object, "subtitle", "");
        List<String> description = strings(object.get("description"));
        String item = string(object, "item", "");
        String entity = string(object, "entity", "");
        String enchantment = string(object, "enchantment", "");
        String recipe = string(object, "recipe", "");
        String structure = string(object, "structure", "");
        String structureTag = string(object, "structure_tag", "");
        String dimension = string(object, "dimension", "");
        int searchRadius = object.has("search_radius") && object.get("search_radius").isJsonPrimitive()
                ? Math.max(0, Math.min(1000, object.get("search_radius").getAsInt())) : 0;
        String locator = string(object, "locator", "");
        String coverItem = string(object, "cover_item", "");
        String coverEntity = string(object, "cover_entity", "");
        String coverPotion = string(object, "cover_potion", "");
        float rotation = object.has("rotation") && object.get("rotation").isJsonPrimitive()
                ? object.get("rotation").getAsFloat() : 0.0F;
        float renderScale = object.has("render_scale") && object.get("render_scale").isJsonPrimitive()
                ? object.get("render_scale").getAsFloat() : 1.0F;
        renderScale = Math.max(0.1F, Math.min(3.0F, renderScale));
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive()
                || object.get("green_tint").getAsBoolean();
        boolean unlockedByDefault = object.has("unlocked_by_default") && object.get("unlocked_by_default").isJsonPrimitive()
                && object.get("unlocked_by_default").getAsBoolean();
        target.put(id, new Entry(id, type, category, title, subtitle, description, item, entity, enchantment, recipe, structure, structureTag, dimension, searchRadius, locator,
                coverItem, coverEntity, coverPotion, rotation, renderScale, greenTint, unlockedByDefault));
    }

    private static void parseDisk(ResourceLocation id, JsonElement element, Map<ResourceLocation, Disk> target) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        ResourceLocation category = parseCategoryId(string(object, "category", "general"), id.getNamespace());
        String title = string(object, "title", "guide.antos.missing_disk_title");
        List<ResourceLocation> entryIds = new ArrayList<>();
        JsonElement entriesElement = object.get("entries");
        if (entriesElement != null && entriesElement.isJsonArray()) {
            for (JsonElement entry : entriesElement.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    try {
                        entryIds.add(ResourceLocation.parse(entry.getAsString()));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        List<ResourceLocation> taskIds = new ArrayList<>();
        JsonElement tasksElement = object.get("tasks");
        if (tasksElement != null && tasksElement.isJsonArray()) {
            for (JsonElement task : tasksElement.getAsJsonArray()) {
                if (task.isJsonPrimitive() && task.getAsJsonPrimitive().isString()) {
                    try { taskIds.add(ResourceLocation.parse(task.getAsString())); }
                    catch (RuntimeException ignored) { }
                }
            }
        }
        String wallpaper = string(object, "wallpaper", "");
        target.put(id, new Disk(id, category, title, List.copyOf(entryIds), wallpaper, List.copyOf(taskIds)));
    }

    private static void parseCategory(ResourceLocation id, JsonElement element, Map<ResourceLocation, DiskCategory> target) {
        if (!element.isJsonObject()) return;
        if (id.toString().length() > 256) {
            AntOS.LOGGER.warn("Ignoring AntOS task with overlong ID {}", id);
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String title = string(object, "title", "guide.antos.category." + id.getPath());
        ResourceLocation texture;
        try {
            texture = ResourceLocation.parse(string(object, "texture", "antos:item/floppy_disk/floppy_disk"));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.error("Invalid texture for floppy disk category {}", id, exception);
            texture = ResourceLocation.fromNamespaceAndPath("antos", "item/floppy_disk/floppy_disk");
        }
        target.put(id, new DiskCategory(id, title, texture));
    }

    private static ResourceLocation parseCategoryId(String value, String fallbackNamespace) {
        return value.indexOf(':') >= 0 ? ResourceLocation.parse(value)
                : ResourceLocation.fromNamespaceAndPath(fallbackNamespace, value);
    }

    private static void parseWallpaper(ResourceLocation id, JsonElement element, Map<ResourceLocation, Wallpaper> target) {
        if (!element.isJsonObject() || id.equals(com.craisinlord.antos.content.computer.ComputerDesktopState.DEFAULT_WALLPAPER)) return;
        JsonObject object = element.getAsJsonObject();
        String title = string(object, "title", "wallpaper." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        String texture = string(object, "texture", "");
        String fit = string(object, "fit", "cover").toLowerCase(java.util.Locale.ROOT);
        if (texture.isBlank() || !(fit.equals("cover") || fit.equals("stretch"))) {
            AntOS.LOGGER.warn("Ignoring invalid computer wallpaper {}: texture is required and fit must be cover or stretch", id);
            return;
        }
        try {
            ResourceLocation.parse(texture);
            target.put(id, new Wallpaper(id, title, texture, fit));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring computer wallpaper {} with invalid texture ID {}", id, texture);
        }
    }

    private static void parseTask(ResourceLocation id, JsonElement element, Map<ResourceLocation, Task> target) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        String title = string(object, "title", "task." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".title");
        String description = string(object, "description", "");
        String program = string(object, "program", "");
        String category = string(object, "category", "antos:general");
        String availability = string(object, "availability", "disk");
        if (!availability.equals("disk") && !availability.equals("automatic")) { AntOS.LOGGER.warn("Ignoring task {} with invalid availability", id); return; }
        int order = object.has("sort_order") ? Math.max(-100000, Math.min(100000, object.get("sort_order").getAsInt())) : 0;
        int x = -1, y = -1;
        JsonElement positionElement = object.get("position");
        if (positionElement != null && positionElement.isJsonObject()) {
            JsonObject position = positionElement.getAsJsonObject();
            x = boundedPosition(position, "x"); y = boundedPosition(position, "y");
        }
        List<ResourceLocation> requires = resourceIds(object.get("requires"));
        List<ResourceLocation> archives = resourceIds(object.get("archive_entries"));
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive() || object.get("green_tint").getAsBoolean();
        boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive() || object.get("render_mob_from_spawn_egg").getAsBoolean();
        List<TaskReward> rewards = new ArrayList<>();
        JsonElement rewardElement = object.get("rewards");
        int rewardUnits = 0;
        if (rewardElement != null && rewardElement.isJsonArray()) for (JsonElement item : rewardElement.getAsJsonArray()) {
            if (!item.isJsonObject() || rewards.size() >= 27) continue;
            JsonObject row = item.getAsJsonObject();
            String rewardType = string(row, "type", "");
            try {
                switch (rewardType) {
                    case "item" -> {
                        ResourceLocation itemId = ResourceLocation.parse(string(row, "item", ""));
                        int count = row.has("count") ? row.get("count").getAsInt() : 1;
                        if (count < 1 || count > 108 || rewardUnits + count > 108) {
                            AntOS.LOGGER.warn("Ignoring item rewards for task {} because the combined reward exceeds 108 items", id);
                            continue;
                        }
                        rewardUnits += count;
                        rewards.add(new TaskReward("item", itemId, count, 0, null, "", "", "", 0, 0));
                    }
                    case "experience" -> {
                        int points = Math.max(0, Math.min(1_000_000, row.get("points").getAsInt()));
                        if (points > 0) rewards.add(new TaskReward("experience", null, 0, points, null, "", "", "", 0, 0));
                    }
                    case "experience_levels" -> {
                        int levels = Math.max(0, Math.min(1000, row.get("levels").getAsInt()));
                        if (levels > 0) rewards.add(new TaskReward("experience_levels", null, levels, 0, null, "", "", "", 0, 0));
                    }
                    case "archive" -> {
                        ResourceLocation entryId = ResourceLocation.parse(string(row, "entry", ""));
                        rewards.add(new TaskReward("archive", null, 0, 0, entryId, "", "", "", 0, 0));
                    }
                    case "advancement" -> {
                        ResourceLocation advancementId = ResourceLocation.parse(string(row, "advancement", ""));
                        rewards.add(new TaskReward("advancement", null, 0, 0, advancementId, "", "", "", 0, 0));
                    }
                    case "effect" -> {
                        ResourceLocation effectId = ResourceLocation.parse(string(row, "effect", ""));
                        int duration = Math.max(1, Math.min(72_000, row.has("duration_ticks") ? row.get("duration_ticks").getAsInt() : 1200));
                        int amplifier = Math.max(0, Math.min(255, row.has("amplifier") ? row.get("amplifier").getAsInt() : 0));
                        rewards.add(new TaskReward("effect", null, 0, 0, effectId, "", "", "", duration, amplifier));
                    }
                    case "mail" -> {
                        String sender = AntmailAddress.ofUsername(string(row, "sender", "")).username();
                        String subject = string(row, "subject", "");
                        String body = string(row, "body", "");
                        if (subject.isBlank() || subject.length() > AntmailValidation.MAX_SUBJECT_LENGTH
                                || body.length() > AntmailValidation.MAX_BODY_LENGTH) {
                            AntOS.LOGGER.warn("Ignoring invalid mail reward for task {} (subject/body length or empty subject)", id);
                            continue;
                        }
                        rewards.add(new TaskReward("mail", null, 0, 0, null, sender, subject, body, 0, 0));
                    }
                    default -> AntOS.LOGGER.warn("Ignoring unsupported {} reward for task {}", rewardType, id);
                }
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring malformed {} reward for task {}", rewardType, id);
            }
        }
        List<Objective> objectives = new ArrayList<>();
        java.util.Set<String> objectiveIds = new java.util.HashSet<>();
        JsonElement objectiveElement = object.get("objectives");
        if (objectiveElement != null && objectiveElement.isJsonArray()) for (JsonElement item : objectiveElement.getAsJsonArray()) {
            if (objectives.size() >= MAX_TASK_OBJECTIVES) {
                AntOS.LOGGER.warn("AntOS task {} exceeds the {} objective limit; remaining objectives are skipped", id, MAX_TASK_OBJECTIVES);
                break;
            }
            if (!item.isJsonObject()) continue;
            JsonObject row = item.getAsJsonObject(); String objectiveId = string(row, "id", "");
            String type = string(row, "type", ""); String destination = string(row, "target", "");
            String statType = string(row, "stat_type", "");
            String tagMode = string(row, "tag_mode", "any");
            boolean supportedStat = type.equals("stat") && List.of("custom", "block_mined", "item_crafted", "item_used", "item_broken", "item_picked_up", "item_dropped", "entity_killed", "entity_killed_by").contains(statType);
            boolean eventObjective = type.equals("mail_read") || type.equals("archive_viewed")
                    || com.craisinlord.antos.content.computer.ComputerLocationObjectives.TYPES.contains(type)
                    || type.contains(":") && ResourceLocation.tryParse(type) != null;
            if (objectiveId.isBlank() || objectiveId.length() > 128 || !objectiveIds.add(objectiveId)
                    || destination.isBlank() || !(type.equals("item") || type.equals("item_tag") || type.equals("advancement") || supportedStat || eventObjective)
                    || type.equals("item_tag") && !tagMode.equals("any") && !tagMode.equals("all")
                    || row.has("consume") && row.get("consume").isJsonPrimitive() && row.get("consume").getAsBoolean()
                    && !type.equals("item") && !type.equals("item_tag")) {
                AntOS.LOGGER.warn("Ignoring task {} objective '{}' with duplicate/invalid ID, missing target, or unsupported type '{}'", id, objectiveId, type);
                continue;
            }
            if ((type.equals("item") || type.equals("item_tag")) && destination.startsWith("#")) {
                AntOS.LOGGER.warn("Ignoring task {} objective {}: use type 'item_tag' with a tag ID without #", id, objectiveId);
                continue;
            }
            if (type.equals("item_tag")) {
                ResourceLocation tagId = ResourceLocation.tryParse(destination);
                if (tagId == null) {
                    AntOS.LOGGER.warn("Ignoring task {} objective {} with invalid item tag {}", id, objectiveId, destination);
                    continue;
                }
            }
            if (type.equals("item")) {
                ResourceLocation itemId = ResourceLocation.tryParse(destination);
                if (itemId == null) {
                    AntOS.LOGGER.warn("Ignoring task {} objective {} with invalid item ID {}", id, objectiveId, destination);
                    continue;
                }
            }
            int count = Math.max(1, Math.min(1_000_000, row.has("count") ? row.get("count").getAsInt() : 1));
            objectives.add(new Objective(objectiveId, type, destination, statType, string(row, "description", ""), count,
                    row.has("optional") && row.get("optional").getAsBoolean(),
                    !row.has("sticky") || !row.get("sticky").isJsonPrimitive() || row.get("sticky").getAsBoolean(),
                    tagMode,
                    row.has("consume") && row.get("consume").isJsonPrimitive() && row.get("consume").getAsBoolean(),
                    !row.has("notify_player") || !row.get("notify_player").isJsonPrimitive() || row.get("notify_player").getAsBoolean()));
        }
        target.put(id, new Task(id, title, description, program, category, order, availability, requires, objectives, archives, rewards, x, y,
                object.has("hide_until_dependencies_complete") && object.get("hide_until_dependencies_complete").getAsBoolean(),
                object.has("invisible_until_completed") && object.get("invisible_until_completed").getAsBoolean(), greenTint,
                string(object, "icon", ""), string(object, "icon_entity", ""), renderMobFromSpawnEgg));
    }

    private static void parseTaskCategory(ResourceLocation id, JsonElement element, Map<ResourceLocation, TaskCategory> target) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        String title = string(object, "title", "task.category." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        String groupValue = string(object, "group", "");
        ResourceLocation group = null;
        if (!groupValue.isBlank()) {
            group = ResourceLocation.tryParse(groupValue);
            if (group == null) AntOS.LOGGER.warn("AntOS task category {} has invalid group {}; it is shown ungrouped", id, groupValue);
        }
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive()
                || object.get("green_tint").getAsBoolean();
        boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive() || object.get("render_mob_from_spawn_egg").getAsBoolean();
        target.put(id, new TaskCategory(id, title, string(object, "icon", ""), string(object, "icon_entity", ""), boundedSortOrder(object), group, greenTint, renderMobFromSpawnEgg));
    }

    private static void parseTaskGroup(ResourceLocation id, JsonElement element, Map<ResourceLocation, TaskGroup> target) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        String title = string(object, "title", "task.group." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        boolean collapsed = object.has("collapsed") && object.get("collapsed").isJsonPrimitive() && object.get("collapsed").getAsBoolean();
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive()
                || object.get("green_tint").getAsBoolean();
        boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive() || object.get("render_mob_from_spawn_egg").getAsBoolean();
        target.put(id, new TaskGroup(id, title, string(object, "icon", ""), string(object, "icon_entity", ""), boundedSortOrder(object), collapsed, greenTint, renderMobFromSpawnEgg));
    }

    private static int boundedSortOrder(JsonObject object) {
        if (!object.has("sort_order")) return 0;
        try { return Math.max(-100000, Math.min(100000, object.get("sort_order").getAsInt())); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static int boundedPosition(JsonObject position, String key) {
        if (!position.has(key)) return -1;
        try { return Math.max(0, Math.min(128, position.get(key).getAsInt())); }
        catch (RuntimeException ignored) { return -1; }
    }

    private static void parseTask(JsonObject object, Map<ResourceLocation, Task> target) {
        try { parseTask(ResourceLocation.parse(string(object, "id", "")), object, target); }
        catch (RuntimeException exception) { AntOS.LOGGER.warn("Ignoring malformed AntOS task in network snapshot", exception); }
    }

    private static List<ResourceLocation> resourceIds(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) if (value.isJsonPrimitive()) try { result.add(ResourceLocation.parse(value.getAsString())); } catch (RuntimeException ignored) { }
        return List.copyOf(result);
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static List<String> strings(JsonElement element) {
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            return List.of(element.getAsString());
        }
        if (!element.isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement value : array) {
            if (value.isJsonPrimitive()) {
                values.add(value.getAsString());
            } else if (value.isJsonObject()) {
                JsonObject render = value.getAsJsonObject();
                String item = string(render, "item", "");
                String entity = string(render, "entity", "");
                String enchantment = string(render, "enchantment", "");
                String recipe = string(render, "recipe", "");
                if (!item.isBlank()) values.add("@item:" + item);
                else if (!entity.isBlank()) values.add("@entity:" + entity);
                else if (!enchantment.isBlank()) values.add("@enchantment:" + enchantment);
                else if (!recipe.isBlank()) values.add("@recipe:" + recipe);
            }
        }
        return List.copyOf(values);
    }

    public record Entry(ResourceLocation id, String type, String category, String titleKey, String subtitleKey,
                         List<String> descriptionKeys, String itemId, String entityId, String enchantmentId,
                         String recipeId, String structureId, String structureTagId, String dimensionId, int searchRadius, String locatorId, String coverItemId,
                         String coverEntityId, String coverPotionId, float rotation, float renderScale, boolean greenTint,
                         boolean unlockedByDefault) {
    }

    public record Disk(ResourceLocation id, ResourceLocation category, String titleKey, List<ResourceLocation> entries, String wallpaper, List<ResourceLocation> tasks) {
    }

    public record DiskCategory(ResourceLocation id, String titleKey, ResourceLocation texture) { }

    public record Wallpaper(ResourceLocation id, String titleKey, String textureId, String fit) {
    }

    public record Task(ResourceLocation id, String titleKey, String descriptionKey, String program, String category, int sortOrder, String availability,
                       List<ResourceLocation> requires, List<Objective> objectives, List<ResourceLocation> archiveEntries, List<TaskReward> rewards, int x, int y,
                       boolean hideUntilDependenciesComplete, boolean invisibleUntilCompleted, boolean greenTint, String iconItem, String iconEntity, boolean renderMobFromSpawnEgg) { }
    public record TaskCategory(ResourceLocation id, String titleKey, String iconItem, String iconEntity, int sortOrder, ResourceLocation group, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record TaskGroup(ResourceLocation id, String titleKey, String iconItem, String iconEntity, int sortOrder, boolean collapsed, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record Objective(String id, String type, String target, String statType, String descriptionKey, int count, boolean optional, boolean sticky, String tagMode, boolean consume, boolean notifyPlayer) { }
    public record TaskReward(String type, ResourceLocation itemId, int count, int experiencePoints, ResourceLocation target,
                             String sender, String subject, String body, int durationTicks, int amplifier) { }

    record LoadedData(Map<ResourceLocation, Entry> entries, Map<ResourceLocation, Disk> disks, Map<ResourceLocation, DiskCategory> categories, Map<ResourceLocation, Wallpaper> wallpapers, Map<ResourceLocation, Task> tasks,
                      Map<ResourceLocation, TaskCategory> taskCategories, Map<ResourceLocation, TaskGroup> taskGroups) {
    }
}


