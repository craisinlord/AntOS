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
    private static final int MAX_TASK_DEFINITIONS = 1024;
    private static final int MAX_TASK_OBJECTIVES = 256;
    private static final int MAX_GALLERY_ITEMS = 32;
    private static final String TASK_CATEGORY_DIRECTORY = "computer/task/category";
    private static final String TASK_GROUP_DIRECTORY = "computer/task/group";
    private static final String TASK_REWARD_POOL_DIRECTORY = "computer/task/reward_pool";
    private static final ComputerGuideData INSTANCE = new ComputerGuideData();
    private static final ResourceLocation INTRODUCTION = new ResourceLocation("antos", "introduction");
    private static volatile Map<ResourceLocation, Entry> entries = Map.of();
    private static volatile Map<ResourceLocation, ArchiveFolder> archiveFolders = Map.of();
    private static volatile Map<ResourceLocation, Entry> fieldGuideEntries = Map.of();
    private static volatile java.util.Set<ResourceLocation> fieldGuideConflicts = java.util.Set.of();
    private static volatile Map<ResourceLocation, Disk> disks = Map.of();
    private static volatile long diskTextureRevision;
    private static volatile Map<ResourceLocation, DiskCategory> categories = Map.of();
    private static volatile Map<ResourceLocation, Wallpaper> wallpapers = Map.of();
    private static volatile Map<ResourceLocation, Task> tasks = Map.of();
    private static volatile Map<ResourceLocation, RewardPool> taskRewardPools = Map.of();
    private static volatile Map<ResourceLocation, TaskCategory> taskCategories = Map.of();
    private static volatile Map<ResourceLocation, TaskGroup> taskGroups = Map.of();
    private static volatile Map<ResourceLocation, TaskLink> taskLinks = Map.of();
    private static volatile Boolean serverUnlockAllArchiveEntries;
    private static volatile Boolean serverUnlockAllGameEntries;
    private static volatile Boolean serverShowIntroductionEntry;
    private static final Wallpaper DEFAULT_WALLPAPER = new Wallpaper(com.craisinlord.antos.content.computer.ComputerDesktopState.DEFAULT_WALLPAPER, "wallpaper.antos.grid_ant", "", "cover");
    private static final java.util.concurrent.atomic.AtomicLong DEFINITION_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong DATAPACK_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static final Map<Object, String> SENT_ARCHIVE_DEFINITIONS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile MergedEntries mergedEntries;
    private static final int ENTRY_VIEW_CACHE_SIZE = 4;
    private static volatile EntriesView[] entriesViews = new EntriesView[0];
    private static volatile SortedView<Disk> sortedDisks;
    private static volatile SortedView<Wallpaper> sortedWallpapers;
    private static volatile ArchiveDefinitionsCache archiveDefinitionsCache;

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
        DEFINITION_REVISION.incrementAndGet();
        return true;
    }

    public static Component archiveTitle(Entry entry) {
        String key = entry.titleKey();
        return key.startsWith("literal:") ? Component.literal(key.substring("literal:".length())) : Component.translatable(key);
    }

    public static ArchiveFolder archiveFolder(ResourceLocation id) {
        return id == null ? null : archiveFolders.get(id);
    }

    public static ResourceLocation archiveFolderId(Entry entry) {
        return entry.folder() != null && archiveFolders.containsKey(entry.folder()) ? entry.folder() : null;
    }

    public static List<ResourceLocation> archiveFolderPath(ResourceLocation id) {
        ArchiveFolder folder = archiveFolder(id);
        if (folder == null) return List.of();
        return folder.parent() == null ? List.of(id) : List.of(folder.parent(), id);
    }

    public static boolean archiveFolderContains(ResourceLocation ancestor, ResourceLocation folderId) {
        if (ancestor == null) return true;
        ArchiveFolder folder = archiveFolder(folderId);
        return folder != null && (ancestor.equals(folder.id()) || ancestor.equals(folder.parent()));
    }

    public static Component archiveFolderTitle(ArchiveFolder folder) {
        String key = folder.titleKey();
        return key.startsWith("literal:") ? Component.literal(key.substring(8)) : Component.translatable(key);
    }

    public static long definitionRevision() {
        return DEFINITION_REVISION.get();
    }

    public static long datapackRevision() {
        return DATAPACK_REVISION.get();
    }

    private record MergedEntries(Map<ResourceLocation, Entry> fieldGuide, Map<ResourceLocation, Entry> base, Map<ResourceLocation, Entry> merged) { }

    private static Map<ResourceLocation, Entry> allEntries() {
        Map<ResourceLocation, Entry> fieldGuide = fieldGuideEntries;
        Map<ResourceLocation, Entry> base = entries;
        MergedEntries cached = mergedEntries;
        if (cached == null || cached.fieldGuide() != fieldGuide || cached.base() != base) {
            Map<ResourceLocation, Entry> result = new LinkedHashMap<>(fieldGuide);
            result.putAll(base);
            cached = new MergedEntries(fieldGuide, base, java.util.Collections.unmodifiableMap(result));
            mergedEntries = cached;
        }
        return cached.merged();
    }

    public static Disk disk(ResourceLocation id) {
        return disks.get(id);
    }

    public static List<Disk> disks() {
        Map<ResourceLocation, Disk> current = disks;
        SortedView<Disk> view = sortedDisks;
        if (view == null || view.source() != current) {
            view = new SortedView<>(current, current.values().stream().sorted(Comparator.comparing((Disk disk) -> disk.id().toString())).toList());
            sortedDisks = view;
        }
        return view.sorted();
    }

    public static DiskCategory category(ResourceLocation id) {
        return categories.get(id);
    }

    public static ResourceLocation diskTexture(ResourceLocation diskId) {
        Disk disk = disks.get(diskId);
        DiskCategory category = disk == null ? null : categories.get(disk.category());
        return category == null ? new ResourceLocation("antos", "item/floppy_disk/floppy_disk") : category.texture();
    }

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
            return DEFAULT_WALLPAPER;
        }
        return wallpapers.get(id);
    }

    private record SortedView<T>(Map<ResourceLocation, T> source, List<T> sorted) { }
    private static volatile SortedView<Task> sortedTasks;
    private static volatile SortedView<TaskCategory> sortedTaskCategories;
    private static volatile SortedView<TaskGroup> sortedTaskGroups;
    private static volatile SortedView<TaskLink> sortedTaskLinks;

    public static List<TaskLink> taskLinks() {
        Map<ResourceLocation, TaskLink> current = taskLinks;
        SortedView<TaskLink> view = sortedTaskLinks;
        if (view == null || view.source() != current) {
            view = new SortedView<>(current, current.values().stream()
                    .sorted(Comparator.comparingInt(TaskLink::sortOrder).thenComparing(link -> link.id().toString())).toList());
            sortedTaskLinks = view;
        }
        return view.sorted();
    }

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

    public static Map<ResourceLocation, RewardPool> taskRewardPools() { return taskRewardPools; }

    public static RewardPool taskRewardPool(ResourceLocation id) { return id == null ? null : taskRewardPools.get(id); }

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
        Map<ResourceLocation, Wallpaper> current = wallpapers;
        SortedView<Wallpaper> view = sortedWallpapers;
        if (view == null || view.source() != current) {
            List<Wallpaper> result = new ArrayList<>();
            result.add(DEFAULT_WALLPAPER);
            current.values().stream().sorted(Comparator.comparing((Wallpaper value) -> value.id().toString())).forEach(result::add);
            view = new SortedView<>(current, List.copyOf(result));
            sortedWallpapers = view;
        }
        return view.sorted();
    }

    private static boolean showIntroductionEntry() {
        Boolean synced = serverShowIntroductionEntry;
        return synced != null ? synced : AntOSSettings.showIntroductionEntry();
    }

    private static boolean unlockAllArchiveEntries() {
        Boolean synced = serverUnlockAllArchiveEntries;
        return synced != null ? synced : AntOSSettings.unlockAllArchiveEntries();
    }

    public static boolean unlockAllGameEntries() {
        Boolean synced = serverUnlockAllGameEntries;
        return AntOSSettings.unlockAllGameEntries() || Boolean.TRUE.equals(synced);
    }

    public static List<Entry> entriesFor(List<ResourceLocation> diskIds) {
        return entriesFor(diskIds, List.of());
    }

    public static List<Entry> entriesFor(List<ResourceLocation> diskIds, java.util.Collection<ResourceLocation> additionallyUnlocked) {
        return entriesView(diskIds, additionallyUnlocked).sorted();
    }

    public static Entry availableEntry(List<ResourceLocation> diskIds, java.util.Collection<ResourceLocation> additionallyUnlocked, ResourceLocation id) {
        return id == null ? null : entriesView(diskIds, additionallyUnlocked).index().get(id);
    }

    private record EntriesView(Map<ResourceLocation, Entry> archive, Map<ResourceLocation, Disk> disks, boolean unlockAll, boolean showIntroduction,
                               List<ResourceLocation> diskIds, List<ResourceLocation> unlocked, List<Entry> sorted, Map<ResourceLocation, Entry> index) { }

    private static EntriesView entriesView(List<ResourceLocation> diskIds, java.util.Collection<ResourceLocation> additionallyUnlocked) {
        Map<ResourceLocation, Entry> archiveEntries = allEntries();
        Map<ResourceLocation, Disk> currentDisks = disks;
        boolean unlockAll = unlockAllArchiveEntries();
        boolean showIntroduction = showIntroductionEntry();
        EntriesView[] views = entriesViews;
        for (EntriesView view : views) {
            if (view.archive() == archiveEntries && view.disks() == currentDisks && view.unlockAll() == unlockAll
                    && view.showIntroduction() == showIntroduction
                    && (unlockAll || sameOrder(view.diskIds(), diskIds) && sameOrder(view.unlocked(), additionallyUnlocked))) {
                return view;
            }
        }
        List<Entry> sorted = computeEntries(archiveEntries, currentDisks, unlockAll, showIntroduction, diskIds, additionallyUnlocked);
        Map<ResourceLocation, Entry> index = new HashMap<>();
        for (Entry entry : sorted) index.putIfAbsent(entry.id(), entry);
        EntriesView view = new EntriesView(archiveEntries, currentDisks, unlockAll, showIntroduction,
                unlockAll ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(diskIds)),
                unlockAll ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(additionallyUnlocked)),
                sorted, java.util.Collections.unmodifiableMap(index));
        EntriesView[] updated = new EntriesView[Math.min(ENTRY_VIEW_CACHE_SIZE, views.length + 1)];
        updated[0] = view;
        System.arraycopy(views, 0, updated, 1, updated.length - 1);
        entriesViews = updated;
        return view;
    }

    private static boolean sameOrder(List<ResourceLocation> cached, java.util.Collection<ResourceLocation> current) {
        if (cached == current) return true;
        if (cached.size() != current.size()) return false;
        java.util.Iterator<ResourceLocation> iterator = current.iterator();
        for (ResourceLocation value : cached) {
            if (!iterator.hasNext() || !java.util.Objects.equals(value, iterator.next())) return false;
        }
        return true;
    }

    private static List<Entry> computeEntries(Map<ResourceLocation, Entry> archiveEntries, Map<ResourceLocation, Disk> disks, boolean unlockAll, boolean showIntroduction,
                                              List<ResourceLocation> diskIds, java.util.Collection<ResourceLocation> additionallyUnlocked) {
        if (unlockAll) {
            Map<ResourceLocation, Entry> result = new LinkedHashMap<>();
            if (showIntroduction) result.put(INTRODUCTION, introduction());
            result.putAll(archiveEntries);
            return result.values().stream().sorted(Comparator.comparing(entry -> entry.titleKey().toString())).toList();
        }
        Map<ResourceLocation, Entry> result = new LinkedHashMap<>();
        archiveEntries.values().stream().filter(Entry::unlockedByDefault)
                .forEach(entry -> result.put(entry.id(), entry));
        for (ResourceLocation diskId : diskIds) {
            if (INTRODUCTION.equals(diskId)) {
                if (showIntroduction) result.put(INTRODUCTION, introduction());
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

    public static String encodeNetworkSnapshot() {
        return encodeNetworkSnapshot(List.of());
    }

    public static String encodeNetworkSnapshot(java.util.Collection<ResourceLocation> unlockedEntries) {
        JsonObject root = new JsonObject();
        JsonArray unlockedArray = new JsonArray();
        unlockedEntries.forEach(id -> unlockedArray.add(id.toString()));
        root.add("unlocked_entries", unlockedArray);
        encodeDefinitions(allEntries(), disks, categories, wallpapers, archiveFolders).entrySet().forEach(value -> root.add(value.getKey(), value.getValue()));
        encodeNetworkSettings(root);
        return root.toString();
    }

    public static void encodeNetworkSettings(JsonObject root) {
        root.addProperty("unlockAllArchiveEntries", AntOSSettings.unlockAllArchiveEntries());
        root.addProperty("unlockAllGameEntries", AntOSSettings.unlockAllGameEntries());
        root.addProperty("showIntroductionEntry", AntOSSettings.showIntroductionEntry());
        AntOSSettings.encodeNetworkSettings(root);
    }

    public static void applyNetworkSettings(JsonObject root) {
        if (root.has("unlockAllArchiveEntries")) serverUnlockAllArchiveEntries = root.get("unlockAllArchiveEntries").getAsBoolean();
        if (root.has("unlockAllGameEntries")) serverUnlockAllGameEntries = root.get("unlockAllGameEntries").getAsBoolean();
        if (root.has("showIntroductionEntry")) serverShowIntroductionEntry = root.get("showIntroductionEntry").getAsBoolean();
        AntOSSettings.applyNetworkSettings(root);
    }

    public record ArchiveDefinitions(String json, String hash) { }

    private record ArchiveDefinitionsCache(Map<ResourceLocation, Entry> entries, Map<ResourceLocation, Disk> disks, Map<ResourceLocation, DiskCategory> categories,
                                           Map<ResourceLocation, Wallpaper> wallpapers, Map<ResourceLocation, ArchiveFolder> folders, long displayRevision, ArchiveDefinitions value) { }

    public static ArchiveDefinitions archiveDefinitions() {
        Map<ResourceLocation, Entry> currentEntries = allEntries();
        Map<ResourceLocation, Disk> currentDisks = disks;
        Map<ResourceLocation, DiskCategory> currentCategories = categories;
        Map<ResourceLocation, Wallpaper> currentWallpapers = wallpapers;
        Map<ResourceLocation, ArchiveFolder> currentFolders = archiveFolders;
        long displayRevision = CustomDisplays.revision();
        ArchiveDefinitionsCache cached = archiveDefinitionsCache;
        if (cached != null && cached.entries() == currentEntries && cached.disks() == currentDisks
                && cached.categories() == currentCategories && cached.wallpapers() == currentWallpapers && cached.folders() == currentFolders && cached.displayRevision() == displayRevision) {
            return cached.value();
        }
        String json = encodeDefinitions(currentEntries, currentDisks, currentCategories, currentWallpapers, currentFolders).toString();
        ArchiveDefinitions value = new ArchiveDefinitions(json, hash(json));
        archiveDefinitionsCache = new ArchiveDefinitionsCache(currentEntries, currentDisks, currentCategories, currentWallpapers, currentFolders, displayRevision, value);
        return value;
    }

    public static boolean claimArchiveDefinitionsSend(Object connection, String hash) {
        if (connection == null) return true;
        return !hash.equals(SENT_ARCHIVE_DEFINITIONS.put(connection, hash));
    }

    public static void forgetArchiveDefinitionsSend(Object connection) {
        if (connection != null) SENT_ARCHIVE_DEFINITIONS.remove(connection);
    }

    private static String hash(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 16);
        } catch (java.security.NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode()) + ":" + value.length();
        }
    }

    private static JsonObject encodeDefinitions(Map<ResourceLocation, Entry> entrySource, Map<ResourceLocation, Disk> diskSource,
                                                Map<ResourceLocation, DiskCategory> categorySource, Map<ResourceLocation, Wallpaper> wallpaperSource,
                                                Map<ResourceLocation, ArchiveFolder> folderSource) {
        JsonObject root = new JsonObject();
        JsonArray entryArray = new JsonArray();
        entrySource.values().stream().sorted(Comparator.comparing((Entry entry) -> entry.id().toString())).forEach(entry -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", entry.id().toString());
            if (!entry.type().equals("article")) object.addProperty("type", entry.type());
            if (!entry.category().equals("general")) object.addProperty("category", entry.category());
            object.addProperty("title", entry.titleKey());
            if (entry.folder() != null) object.addProperty("folder", entry.folder().toString());
            if (entry.sortOrder() != 0) object.addProperty("sort_order", entry.sortOrder());
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
            if (!entry.gallery().isEmpty()) {
                JsonArray gallery = new JsonArray();
                for (GalleryItem galleryItem : entry.gallery()) {
                    if (galleryItem.kind().equals("auto") && galleryItem.nameKey().isEmpty()) {
                        gallery.add(galleryItem.id());
                        continue;
                    }
                    JsonObject galleryObject = new JsonObject();
                    galleryObject.addProperty(galleryItem.kind().equals("auto") ? "id" : galleryItem.kind(), galleryItem.id());
                    if (!galleryItem.nameKey().isEmpty()) galleryObject.addProperty("name", galleryItem.nameKey());
                    gallery.add(galleryObject);
                }
                object.add("gallery", gallery);
            }
            addIfPresent(object, "gallery_title", entry.galleryTitleKey());
            if (!entry.lookup().isEmpty()) {
                JsonArray lookup = new JsonArray();
                entry.lookup().forEach(lookup::add);
                object.add("lookup", lookup);
            }
            if (!entry.lookupRecipes().isEmpty()) {
                JsonArray lookupRecipes = new JsonArray();
                entry.lookupRecipes().forEach(lookupRecipes::add);
                object.add("lookup_recipes", lookupRecipes);
            }
            entryArray.add(object);
        });
        root.add("entries", entryArray);
        JsonArray folderArray = new JsonArray();
        folderSource.values().stream().sorted(Comparator.comparing(folder -> folder.id().toString())).forEach(folder -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", folder.id().toString());
            object.addProperty("title", folder.titleKey());
            object.addProperty("icon_item", folder.iconItem());
            if (!folder.greenTint()) object.addProperty("green_tint", false);
            if (folder.parent() != null) object.addProperty("parent", folder.parent().toString());
            object.addProperty("sort_order", folder.sortOrder());
            folderArray.add(object);
        });
        root.add("archive_folders", folderArray);

        JsonArray diskArray = new JsonArray();
        diskSource.values().stream().sorted(Comparator.comparing((Disk disk) -> disk.id().toString())).forEach(disk -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", disk.id().toString());
            object.addProperty("category", disk.category().toString());
            object.addProperty("title", disk.titleKey());
            object.addProperty("description", disk.descriptionKey());
            object.addProperty("hidden", disk.hidden());
            object.add("rewards", encodeRewards(disk.rewards()));
            disk.features().encodeClient(object);
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
        categorySource.values().stream().sorted(Comparator.comparing((DiskCategory category) -> category.id().toString())).forEach(category -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", category.id().toString());
            object.addProperty("title", category.titleKey());
            object.addProperty("texture", category.texture().toString());
            categoryArray.add(object);
        });
        root.add("categories", categoryArray);
        JsonArray wallpaperArray = new JsonArray();
        wallpaperSource.values().stream().sorted(Comparator.comparing((Wallpaper wallpaper) -> wallpaper.id().toString())).forEach(wallpaper -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", wallpaper.id().toString());
            object.addProperty("title", wallpaper.titleKey());
            object.addProperty("texture", wallpaper.textureId());
            object.addProperty("fit", wallpaper.fit());
            wallpaperArray.add(object);
        });
        root.add("wallpapers", wallpaperArray);
        return root;
    }

    private static void addIfPresent(JsonObject object, String key, String value) {
        if (value != null && !value.isEmpty()) object.addProperty(key, value);
    }

    public static void applyNetworkSnapshot(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, JsonObject> loadedDisplays = root.has("displays") ? CustomDisplays.decode(root.get("displays")) : null;
            Map<ResourceLocation, Entry> loadedEntries = new HashMap<>();
            Map<ResourceLocation, ArchiveFolder> loadedFolders = new HashMap<>();
            Map<ResourceLocation, Disk> loadedDisks = new HashMap<>();
            Map<ResourceLocation, DiskCategory> loadedCategories = new HashMap<>();
            Map<ResourceLocation, Wallpaper> loadedWallpapers = new HashMap<>();
            Map<ResourceLocation, Task> loadedTasks = new HashMap<>();
            JsonArray folderArray = root.getAsJsonArray("archive_folders");
            if (folderArray != null) for (JsonElement element : folderArray) {
                JsonObject object = element.getAsJsonObject();
                parseArchiveFolder(new ResourceLocation(string(object, "id", "")), object, loadedFolders);
            }
            JsonArray entryArray = root.getAsJsonArray("entries");
            if (entryArray != null) {
                for (JsonElement element : entryArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseEntry(ResourceLocation.tryParse(string(object, "id", "")), object, loadedEntries);
                }
            }
            JsonArray diskArray = root.getAsJsonArray("disks");
            if (diskArray != null) {
                for (JsonElement element : diskArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseDisk(ResourceLocation.tryParse(string(object, "id", "")), object, loadedDisks);
                }
            }
            JsonArray categoryArray = root.getAsJsonArray("categories");
            if (categoryArray != null) {
                for (JsonElement element : categoryArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseCategory(ResourceLocation.tryParse(string(object, "id", "")), object, loadedCategories);
                }
            }
            JsonArray wallpaperArray = root.getAsJsonArray("wallpapers");
            if (wallpaperArray != null) {
                for (JsonElement element : wallpaperArray) {
                    JsonObject object = element.getAsJsonObject();
                    parseWallpaper(ResourceLocation.tryParse(string(object, "id", "")), object, loadedWallpapers);
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
            if (loadedDisplays != null) CustomDisplays.apply(loadedDisplays);
            archiveFolders = normalizeArchiveFolders(loadedFolders);
            fieldGuideEntries = Map.copyOf(loadedFieldGuideEntries);
            disks = Map.copyOf(loadedDisks);
            categories = Map.copyOf(loadedCategories);
            wallpapers = Map.copyOf(loadedWallpapers);
            if (taskArray != null) tasks = Map.copyOf(loadedTasks);
            DEFINITION_REVISION.incrementAndGet();
            applyNetworkSettings(root);
            AntOS.LOGGER.debug("Loaded {} computer archive entries and {} disks from the server", entries.size(), disks.size());
        } catch (RuntimeException exception) {
            AntOS.LOGGER.error("Failed to decode computer archive data from the server", exception);
        }
    }

    @Override
    protected LoadedData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Entry> loadedEntries = new HashMap<>();
        Map<ResourceLocation, ArchiveFolder> loadedFolders = new HashMap<>();
        Map<ResourceLocation, Disk> loadedDisks = new HashMap<>();
        Map<ResourceLocation, DiskCategory> loadedCategories = new HashMap<>();
        Map<ResourceLocation, Wallpaper> loadedWallpapers = new HashMap<>();
        Map<ResourceLocation, Task> loadedTasks = new HashMap<>();
        Map<ResourceLocation, RewardPool> loadedRewardPools = new HashMap<>();
        Map<ResourceLocation, TaskCategory> loadedTaskCategories = new HashMap<>();
        Map<ResourceLocation, TaskGroup> loadedTaskGroups = new HashMap<>();
        Map<ResourceLocation, TaskLink> loadedTaskLinks = new HashMap<>();
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/archive_folder", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/archive_folder");
            try {
                parseArchiveFolder(id, readJson(resource.getValue()), loadedFolders);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer archive folder {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/entry", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/entry");
            try {
                JsonElement json = readJson(resource.getValue());
                parseEntry(id, json, loadedEntries);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer archive entry {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/disk", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/disk");
            try {
                JsonElement json = readJson(resource.getValue());
                parseDisk(id, json, loadedDisks);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer archive disk {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/category", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/category");
            try {
                JsonElement json = readJson(resource.getValue());
                parseCategory(id, json, loadedCategories);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load floppy disk category {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/wallpaper", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), "computer/wallpaper");
            try {
                JsonElement json = readJson(resource.getValue());
                parseWallpaper(id, json, loadedWallpapers);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load computer wallpaper {}", resource.getKey(), exception);
            }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources(TASK_CATEGORY_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), TASK_CATEGORY_DIRECTORY);
            try { parseTaskCategory(id, readJson(resource.getValue()), loadedTaskCategories); }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task category {}", resource.getKey(), exception); }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources(TASK_GROUP_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), TASK_GROUP_DIRECTORY);
            try { parseTaskGroup(id, readJson(resource.getValue()), loadedTaskGroups); }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task group {}", resource.getKey(), exception); }
        }
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources(TASK_REWARD_POOL_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(resource.getKey(), TASK_REWARD_POOL_DIRECTORY);
            try { parseRewardPool(id, readJson(resource.getValue()), loadedRewardPools); }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task reward pool {}", resource.getKey(), exception); }
        }
        validateRewardPools(loadedRewardPools);
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resource : resourceManager.listResources("computer/task", path -> path.getPath().endsWith(".json")).entrySet().stream()
                .filter(resource -> !resource.getKey().getPath().startsWith(TASK_CATEGORY_DIRECTORY + "/") && !resource.getKey().getPath().startsWith(TASK_GROUP_DIRECTORY + "/")
                        && !resource.getKey().getPath().startsWith(TASK_REWARD_POOL_DIRECTORY + "/"))
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (loadedTasks.size() + loadedTaskLinks.size() >= MAX_TASK_DEFINITIONS) {
                AntOS.LOGGER.warn("AntOS task limit reached ({}); additional task definitions are skipped", MAX_TASK_DEFINITIONS);
                break;
            }
            ResourceLocation id = resourceId(resource.getKey(), "computer/task");
            try {
                JsonElement json = readJson(resource.getValue());
                if (isTaskLink(json)) parseTaskLink(id, json, loadedTaskLinks);
                else parseTask(id, json, loadedTasks);
            }
            catch (Exception exception) { AntOS.LOGGER.error("Failed to load computer task {}", resource.getKey(), exception); }
        }
        loadedTasks.replaceAll((id, task) -> {
            List<TaskReward> validRewards = new ArrayList<>();
            for (TaskReward reward : task.rewards()) {
                if (reward.type().equals("item_pool")) {
                    if (loadedRewardPools.containsKey(reward.poolId())) validRewards.add(reward);
                    else AntOS.LOGGER.warn("Task {} refers to missing or invalid reward pool {}", id, reward.poolId());
                } else if (reward.type().equals("choice")) {
                    List<RewardOption> validOptions = reward.options().stream().filter(option -> option.rewards().stream()
                            .allMatch(optionReward -> !optionReward.type().equals("item_pool") || loadedRewardPools.containsKey(optionReward.poolId()))).toList();
                    if (validOptions.size() >= 2) validRewards.add(new TaskReward(reward.type(), reward.itemId(), reward.count(), reward.experiencePoints(),
                            reward.target(), reward.sender(), reward.subject(), reward.body(), reward.durationTicks(), reward.amplifier(), reward.poolId(),
                            reward.mode(), reward.antcoins(), validOptions));
                    else AntOS.LOGGER.warn("Task {} has a reward choice with fewer than two valid options", id);
                } else validRewards.add(reward);
            }
            return new Task(task.id(), task.titleKey(), task.descriptionKey(), task.program(), task.category(), task.sortOrder(), task.availability(),
                    task.requires(), task.objectives(), task.archiveEntries(), validRewards, task.hasPosition(), task.x(), task.y(), task.hideUntilDependenciesComplete(),
                    task.invisibleUntilCompleted(), task.greenTint(), task.iconItem(), task.iconEntity(), task.renderMobFromSpawnEgg(), task.appearance(), task.connects(), task.showcase(),
                    task.requiresAny());
        });
        loadedDisks.replaceAll((id, disk) -> {
            List<TaskReward> rewards = disk.rewards().stream().filter(reward -> {
                if (!reward.type().equals("item_pool") || loadedRewardPools.containsKey(reward.poolId())) return true;
                AntOS.LOGGER.warn("Disk {} refers to missing or invalid reward pool {}", id, reward.poolId());
                return false;
            }).toList();
            return new Disk(disk.id(), disk.category(), disk.titleKey(), disk.entries(), disk.wallpaper(), disk.tasks(), disk.descriptionKey(), disk.hidden(), rewards, disk.features());
        });
        int displayBudget = CustomDisplays.MAX_TOTAL_CHARS;
        LoadedData data = new LoadedData(normalizeArchiveFolders(loadedFolders), Map.copyOf(loadedEntries), Map.copyOf(loadedDisks), Map.copyOf(loadedCategories), Map.copyOf(loadedWallpapers), Map.copyOf(loadedTasks), Map.copyOf(loadedRewardPools),
                Map.copyOf(loadedTaskCategories), Map.copyOf(loadedTaskGroups), Map.copyOf(loadedTaskLinks), com.craisinlord.antos.content.computer.ComputerDiskFiles.load(resourceManager),
                com.craisinlord.antos.content.computer.ComputerProgramData.load(resourceManager),
                com.craisinlord.antos.content.computer.terminal.TerminalCommandData.load(resourceManager, loadedRewardPools), CustomDisplays.load(resourceManager, displayBudget));
        validateLoadedData(data, resourceManager);
        Map<ResourceLocation, Entry> snapshotEntries = new LinkedHashMap<>(fieldGuideEntries);
        snapshotEntries.putAll(data.entries());
        JsonObject snapshot = new JsonObject();
        snapshot.add("unlocked_entries", new JsonArray());
        encodeDefinitions(snapshotEntries, data.disks(), data.categories(), data.wallpapers(), data.archiveFolders()).entrySet().forEach(value -> snapshot.add(value.getKey(), value.getValue()));
        encodeNetworkSettings(snapshot);
        int snapshotLength = snapshot.toString().length();
        if (snapshotLength > com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS) {
            AntOS.LOGGER.error("AntOS guide sync data is {} characters; it exceeds the network limit of {}. Reduce loaded Archive entries, disks, wallpapers, or descriptions.",
                    snapshotLength, com.craisinlord.antos.content.network.ComputerAccessResultPayload.MAX_DATA_CHARS);
        }
        return data;
    }

    private static JsonElement readJson(net.minecraft.server.packs.resources.Resource resource) throws java.io.IOException {
        try (java.io.Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        }
    }

    @Override
    protected void apply(LoadedData data, ResourceManager resourceManager, ProfilerFiller profiler) {
        CustomDisplays.apply(data.displays());
        entries = data.entries();
        archiveFolders = data.archiveFolders();
        disks = data.disks();
        com.craisinlord.antos.content.computer.ComputerDiskData.apply(data.disks(), data.diskFiles(), data.programs());
        com.craisinlord.antos.content.computer.terminal.TerminalCommandData.apply(data.terminalCommands());
        categories = data.categories();
        diskTextureRevision++;
        wallpapers = data.wallpapers();
        tasks = data.tasks();
        taskRewardPools = data.rewardPools();
        taskCategories = data.taskCategories();
        taskGroups = data.taskGroups();
        taskLinks = data.taskLinks();
        DEFINITION_REVISION.incrementAndGet();
        DATAPACK_REVISION.incrementAndGet();
        AntOS.LOGGER.info("Loaded AntOS data: {} Archive entries, {} disks, {} wallpapers, {} tasks, {} task links, {} task categories, {} task groups, and {} terminal commands",
                entries.size(), disks.size(), wallpapers.size(), tasks.size(), taskLinks.size(), taskCategories.size(), taskGroups.size(), data.terminalCommands().size());
    }

    private static void validateDisplayReferences(ResourceLocation owner, List<String> content, Map<ResourceLocation, JsonObject> displays) {
        for (String part : content) {
            if (!part.startsWith("@display:")) continue;
            ResourceLocation id = ResourceLocation.tryParse(part.substring(9));
            if (id == null || !displays.containsKey(id) && !CustomDisplays.pending(id)) AntOS.LOGGER.warn("AntOS content {} refers to missing display {}", owner, part.substring(9));
        }
    }

    private static void validateLoadedData(LoadedData data, ResourceManager resourceManager) {
        for (Entry entry : data.entries().values()) validateDisplayReferences(entry.id(), entry.descriptionKeys(), data.displays());
        for (Task task : data.tasks().values()) validateDisplayReferences(task.id(), task.showcase(), data.displays());
        for (Entry entry : data.entries().values()) {
            if (entry.folder() != null && !data.archiveFolders().containsKey(entry.folder()))
                AntOS.LOGGER.warn("Archive entry {} refers to missing folder {}; it is shown at the Archive root", entry.id(), entry.folder());
        }
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
        for (var command : data.terminalCommands().values()) {
            for (ResourceLocation entry : command.entries()) if (!data.entries().containsKey(entry))
                AntOS.LOGGER.warn("AntOS terminal command {} refers to missing Archive entry {}", command.id(), entry);
            for (ResourceLocation task : command.tasks()) if (!data.tasks().containsKey(task))
                AntOS.LOGGER.warn("AntOS terminal command {} grants missing task {}", command.id(), task);
            if (command.wallpaper() != null && !command.wallpaper().equals(com.craisinlord.antos.content.computer.ComputerDesktopState.DEFAULT_WALLPAPER)
                    && !data.wallpapers().containsKey(command.wallpaper()))
                AntOS.LOGGER.warn("AntOS terminal command {} unlocks missing wallpaper {}", command.id(), command.wallpaper());
            for (ResourceLocation program : command.programs()) if (!data.programs().containsKey(program))
                AntOS.LOGGER.warn("AntOS terminal command {} grants missing program {}", command.id(), program);
            for (var file : command.files()) if (!data.diskFiles().containsKey(com.craisinlord.antos.content.computer.ComputerDiskFiles.sourceId(file.source())))
                AntOS.LOGGER.warn("AntOS terminal command {} refers to missing file {}", command.id(), file.source());
            for (ResourceLocation disk : command.requires().disks()) if (!data.disks().containsKey(disk))
                AntOS.LOGGER.warn("AntOS terminal command {} requires missing disk {}", command.id(), disk);
            for (ResourceLocation task : command.requires().tasks()) if (!data.tasks().containsKey(task))
                AntOS.LOGGER.warn("AntOS terminal command {} requires missing task {}", command.id(), task);
            for (ResourceLocation required : command.requires().commands()) if (!data.terminalCommands().containsKey(required))
                AntOS.LOGGER.warn("AntOS terminal command {} requires missing terminal command {}", command.id(), required);
        }
        Map<String, ResourceLocation> positionedTasks = new HashMap<>();
        for (Task task : data.tasks().values()) {
            if (task.hasPosition()) {
                String positionKey = task.category() + ":" + task.x() + "," + task.y();
                ResourceLocation previous = positionedTasks.putIfAbsent(positionKey, task.id());
                if (previous != null) AntOS.LOGGER.warn("AntOS tasks {} and {} share map position ({}, {}) in category {}",
                        previous, task.id(), task.x(), task.y(), task.category());
            }
            for (ResourceLocation required : task.requires()) if (!data.tasks().containsKey(required))
                AntOS.LOGGER.warn("AntOS task {} requires missing task {}", task.id(), required);
            for (ResourceLocation required : task.requiresAny()) if (!data.tasks().containsKey(required))
                AntOS.LOGGER.warn("AntOS task {} requires_any missing task {}", task.id(), required);
            for (ResourceLocation connected : task.connects()) if (!data.tasks().containsKey(connected) && !data.taskLinks().containsKey(connected))
                AntOS.LOGGER.warn("AntOS task {} connects to missing task or link {}", task.id(), connected);
            for (ResourceLocation entry : task.archiveEntries()) if (!data.entries().containsKey(entry))
                AntOS.LOGGER.warn("AntOS task {} refers to missing Archive entry {}", task.id(), entry);
            for (Objective objective : task.objectives()) {
                if (objective.type().equals("advancement")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !advancementIds.contains(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing advancement {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().equals("item")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing item {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().equals("terminal_command")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !data.terminalCommands().containsKey(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing terminal command {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().equals("mail_read")) {
                    ResourceLocation id = ResourceLocation.tryParse(objective.target());
                    if (id == null || !antmailIds.contains(id)) AntOS.LOGGER.warn("AntOS task {} objective {} refers to missing Antmail message {}", task.id(), objective.id(), objective.target());
                } else if (objective.type().contains(":") && ResourceLocation.tryParse(objective.type()) != null) {
                    ResourceLocation type = ResourceLocation.tryParse(objective.type());
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
        for (TaskLink link : data.taskLinks().values()) {
            if (link.hasPosition()) {
                String positionKey = link.category() + ":" + link.x() + "," + link.y();
                ResourceLocation previous = positionedTasks.putIfAbsent(positionKey, link.id());
                if (previous != null) AntOS.LOGGER.warn("AntOS task link {} shares map position ({}, {}) with {} in category {}",
                        link.id(), link.x(), link.y(), previous, link.category());
            }
            if (link.linkCategory() != null && !data.taskCategories().containsKey(link.linkCategory())
                    && data.tasks().values().stream().noneMatch(task -> task.category().equals(link.linkCategory().toString())))
                AntOS.LOGGER.warn("AntOS task link {} opens missing task category {}", link.id(), link.linkCategory());
            if (link.linkGroup() != null && !data.taskGroups().containsKey(link.linkGroup()))
                AntOS.LOGGER.warn("AntOS task link {} opens missing task group {}", link.id(), link.linkGroup());
            for (ResourceLocation required : link.requires()) if (!data.tasks().containsKey(required))
                AntOS.LOGGER.warn("AntOS task link {} requires missing task {}", link.id(), required);
            for (ResourceLocation connected : link.connects()) if (!data.tasks().containsKey(connected) && !data.taskLinks().containsKey(connected))
                AntOS.LOGGER.warn("AntOS task link {} connects to missing task or link {}", link.id(), connected);
            warnMissingIcon("task link", link.id(), link.iconItem(), link.iconEntity());
        }
        for (TaskCategory category : data.taskCategories().values()) {
            warnMissingIcon("task category", category.id(), category.iconItem(), category.iconEntity());
            if (category.group() != null && !data.taskGroups().containsKey(category.group()))
                AntOS.LOGGER.warn("AntOS task category {} refers to missing task group {}; it is shown ungrouped", category.id(), category.group());
        }
        for (TaskGroup group : data.taskGroups().values()) warnMissingIcon("task group", group.id(), group.iconItem(), group.iconEntity());
        warnTaskCycles(data.tasks());
        validateLookups(data.entries());
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
        if (task != null) for (ResourceLocation required : task.prerequisites()) if (taskData.containsKey(required))
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
        return new ResourceLocation(path.getNamespace(), value);
    }

    private static void parseArchiveFolder(ResourceLocation id, JsonElement element, Map<ResourceLocation, ArchiveFolder> target) {
        JsonObject object = element.getAsJsonObject();
        String parentValue = string(object, "parent", "");
        ResourceLocation parent = parentValue.isBlank() ? null : ResourceLocation.tryParse(parentValue);
        if (!parentValue.isBlank() && parent == null)
            AntOS.LOGGER.warn("Archive folder {} has invalid parent {}; it is shown at the Archive root", id, parentValue);
        target.put(id, new ArchiveFolder(id, string(object, "title", "literal:" + id.getPath()),
                string(object, "icon_item", "minecraft:book"), boundedSortOrder(object), parent, !Boolean.FALSE.equals(optionalFlag(object, "green_tint"))));
    }

    private static Map<ResourceLocation, ArchiveFolder> normalizeArchiveFolders(Map<ResourceLocation, ArchiveFolder> definitions) {
        Map<ResourceLocation, ArchiveFolder> result = new HashMap<>();
        for (ArchiveFolder folder : definitions.values()) {
            java.util.Set<ResourceLocation> path = new java.util.HashSet<>();
            path.add(folder.id());
            ResourceLocation parent = folder.parent();
            int depth = 0;
            String problem = null;
            while (parent != null) {
                if (!path.add(parent)) {
                    problem = "a cyclic parent relationship";
                    break;
                }
                ArchiveFolder ancestor = definitions.get(parent);
                if (ancestor == null) {
                    problem = "a missing parent " + parent;
                    break;
                }
                depth++;
                parent = ancestor.parent();
            }
            if (problem == null && depth > 1) problem = "more than one level of subfolders";
            if (problem != null) {
                AntOS.LOGGER.warn("Archive folder {} has {}; it is shown at the Archive root", folder.id(), problem);
                folder = new ArchiveFolder(folder.id(), folder.titleKey(), folder.iconItem(), folder.sortOrder(), null, folder.greenTint());
            }
            result.put(folder.id(), folder);
        }
        return Map.copyOf(result);
    }

    private static void parseEntry(ResourceLocation id, JsonElement element, Map<ResourceLocation, Entry> target) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String type = string(object, "type", "article");
        String category = string(object, "category", "general");
        String folderValue = string(object, "folder", "");
        ResourceLocation folder = folderValue.isBlank() ? null : ResourceLocation.tryParse(folderValue);
        if (!folderValue.isBlank() && folder == null)
            AntOS.LOGGER.warn("Archive entry {} has invalid folder {}; it is shown at the Archive root", id, folderValue);
        String title = string(object, "title", "guide.antos.missing_title");
        String subtitle = string(object, "subtitle", "");
        List<String> description = object.has("content") ? CustomDisplays.content(object.get("content")) : strings(object.get("description"));
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
                coverItem, coverEntity, coverPotion, rotation, renderScale, greenTint, unlockedByDefault, gallery(object.get("gallery")),
                string(object, "gallery_title", ""), folder, boundedSortOrder(object), lookupIds(object.get("lookup")),
                recipeTypeIds(object.get("lookup_recipes"))));
    }

    private static List<String> recipeTypeIds(JsonElement element) {
        List<String> result = new ArrayList<>();
        for (String value : strings(element)) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && ResourceLocation.tryParse(trimmed) != null && !result.contains(trimmed)) result.add(trimmed);
        }
        return result;
    }

    private static List<String> lookupIds(JsonElement element) {
        List<String> result = new ArrayList<>();
        for (String value : strings(element)) {
            String trimmed = value.trim();
            String id = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
            if (!id.isEmpty() && ResourceLocation.tryParse(id) != null && !result.contains(trimmed)) result.add(trimmed);
        }
        return result;
    }

    public static java.util.Collection<Entry> archiveEntries() {
        return allEntries().values();
    }

    private static void validateLookups(Map<ResourceLocation, Entry> entries) {
        Map<String, ResourceLocation> claimed = new HashMap<>();
        for (Entry entry : entries.values()) {
            for (String id : entry.lookup()) {
                if (!id.startsWith("#")) {
                    ResourceLocation item = ResourceLocation.tryParse(id);
                    if (item == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item))
                        AntOS.LOGGER.warn("Archive entry {} looks up missing item {}", entry.id(), id);
                }
                ResourceLocation previous = claimed.putIfAbsent(id, entry.id());
                if (previous != null && !previous.equals(entry.id()))
                    AntOS.LOGGER.warn("Archive entries {} and {} both look up {}; {} wins", previous, entry.id(), id, previous);
            }
        }
        Map<String, ResourceLocation> stations = new HashMap<>();
        for (Entry entry : entries.values()) {
            for (String type : entry.lookupRecipes()) {
                ResourceLocation id = ResourceLocation.tryParse(type);
                if (id == null || !net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.containsKey(id)
                        && !net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id))
                    AntOS.LOGGER.warn("Archive entry {} looks up recipes of missing type {}", entry.id(), type);
                ResourceLocation previous = stations.putIfAbsent(type, entry.id());
                if (previous != null && !previous.equals(entry.id()))
                    AntOS.LOGGER.warn("Archive entries {} and {} both look up {} recipes; their shared outputs open neither", previous, entry.id(), type);
            }
        }
    }

    private static List<GalleryItem> gallery(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<GalleryItem> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (result.size() >= MAX_GALLERY_ITEMS) break;
            if (value.isJsonPrimitive()) {
                String id = value.getAsString();
                if (ResourceLocation.tryParse(id) != null) result.add(new GalleryItem("auto", id, ""));
            } else if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                String kind = object.has("entity") ? "entity" : object.has("item") ? "item" : object.has("id") ? "auto" : "";
                String id = kind.isEmpty() ? "" : string(object, kind.equals("auto") ? "id" : kind, "");
                if (!kind.isEmpty() && ResourceLocation.tryParse(id) != null) result.add(new GalleryItem(kind, id, string(object, "name", "")));
            }
        }
        return List.copyOf(result);
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
                        entryIds.add(ResourceLocation.tryParse(entry.getAsString()));
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
                    try { taskIds.add(ResourceLocation.tryParse(task.getAsString())); }
                    catch (RuntimeException ignored) { }
                }
            }
        }
        String wallpaper = string(object, "wallpaper", "");
        String description = string(object, "description", "");
        boolean hidden = object.has("hidden") && object.get("hidden").isJsonPrimitive()
                && object.get("hidden").getAsJsonPrimitive().isBoolean() && object.get("hidden").getAsBoolean();
        target.put(id, new Disk(id, category, title, List.copyOf(entryIds), wallpaper, List.copyOf(taskIds), description, hidden,
                parseRewards(id, object.get("rewards"), false), DiskFeatures.parse(object)));
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
            texture = ResourceLocation.tryParse(string(object, "texture", "antos:item/floppy_disk/floppy_disk"));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.error("Invalid texture for floppy disk category {}", id, exception);
            texture = new ResourceLocation("antos", "item/floppy_disk/floppy_disk");
        }
        target.put(id, new DiskCategory(id, title, texture));
    }

    private static ResourceLocation parseCategoryId(String value, String fallbackNamespace) {
        return value.indexOf(':') >= 0 ? ResourceLocation.tryParse(value)
                : new ResourceLocation(fallbackNamespace, value);
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
            ResourceLocation.tryParse(texture);
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
        int x = 0, y = 0;
        boolean hasPosition = false;
        JsonElement positionElement = object.get("position");
        if (positionElement != null && positionElement.isJsonObject()) {
            JsonObject position = positionElement.getAsJsonObject();
            Integer parsedX = boundedPosition(position, "x");
            Integer parsedY = boundedPosition(position, "y");
            if (parsedX != null && parsedY != null) {
                x = parsedX;
                y = parsedY;
                hasPosition = true;
            }
        }
        List<ResourceLocation> requires = resourceIds(object.get("requires"));
        List<ResourceLocation> archives = resourceIds(object.get("archive_entries"));
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive() || object.get("green_tint").getAsBoolean();
        boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive() || object.get("render_mob_from_spawn_egg").getAsBoolean();
        List<TaskReward> rewards = parseRewards(id, object.get("rewards"), true);
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
            String targetCategory = string(row, "target_category", "");
            String ending = string(row, "ending", "");
            if (type.equals("disk_installed") && !targetCategory.isBlank()) {
                if (!destination.isBlank()) { AntOS.LOGGER.warn("Disk objective {} must use target or target_category", objectiveId); continue; }
                destination = targetCategory;
            }
            if ((type.equals("disk_installed") || type.equals("program_ending")) && (ResourceLocation.tryParse(destination) == null || ending.length() > 64 || ending.contains("#"))) continue;
            if (type.equals("terminal_command") && ResourceLocation.tryParse(destination) == null) continue;
            String statType = string(row, "stat_type", "");
            String tagMode = string(row, "tag_mode", "any");
            boolean supportedStat = type.equals("stat") && List.of("custom", "block_mined", "item_crafted", "item_used", "item_broken", "item_picked_up", "item_dropped", "entity_killed", "entity_killed_by").contains(statType);
            boolean eventObjective = type.equals("disk_installed") || type.equals("program_ending") || type.equals("terminal_command") || type.equals("mail_read") || type.equals("archive_viewed")
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
            if (type.equals("disk_installed") && targetCategory.isBlank()) count = 1;
            if (type.equals("program_ending") && !ending.isBlank()) count = 1;
            if (type.equals("terminal_command")) count = 1;
            objectives.add(new Objective(objectiveId, type, destination, statType, string(row, "description", ""), count,
                    row.has("optional") && row.get("optional").getAsBoolean(),
                    !row.has("sticky") || !row.get("sticky").isJsonPrimitive() || row.get("sticky").getAsBoolean(),
                    tagMode,
                    row.has("consume") && row.get("consume").isJsonPrimitive() && row.get("consume").getAsBoolean(),
                    !row.has("notify_player") || !row.get("notify_player").isJsonPrimitive() || row.get("notify_player").getAsBoolean(), targetCategory, ending));
        }
        target.put(id, new Task(id, title, description, program, category, order, availability, requires, objectives, archives, rewards, hasPosition, x, y,
                object.has("hide_until_dependencies_complete") && object.get("hide_until_dependencies_complete").getAsBoolean(),
                object.has("invisible_until_completed") && object.get("invisible_until_completed").getAsBoolean(), greenTint,
                string(object, "icon", ""), string(object, "icon_entity", ""), renderMobFromSpawnEgg, TaskAppearance.parse(object), resourceIds(object.get("connects")),
                object.has("content") ? CustomDisplays.content(object.get("content")) : List.copyOf(strings(object.get("showcase"))), resourceIds(object.get("requires_any"))));
    }

    private static boolean isTaskLink(JsonElement element) {
        if (!element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        return string(object, "type", "").equals("link") || object.has("link_category") || object.has("link_group");
    }

    private static void parseTaskLink(ResourceLocation id, JsonElement element, Map<ResourceLocation, TaskLink> target) {
        JsonObject object = element.getAsJsonObject();
        ResourceLocation linkCategory = optionalId(string(object, "link_category", ""));
        ResourceLocation linkGroup = linkCategory != null ? null : optionalId(string(object, "link_group", ""));
        if (linkCategory == null && linkGroup == null) {
            AntOS.LOGGER.warn("Ignoring task link {} without a valid link_category or link_group", id);
            return;
        }
        int x = 0, y = 0;
        boolean hasPosition = false;
        JsonElement positionElement = object.get("position");
        if (positionElement != null && positionElement.isJsonObject()) {
            Integer parsedX = boundedPosition(positionElement.getAsJsonObject(), "x");
            Integer parsedY = boundedPosition(positionElement.getAsJsonObject(), "y");
            if (parsedX != null && parsedY != null) {
                x = parsedX;
                y = parsedY;
                hasPosition = true;
            }
        }
        target.put(id, new TaskLink(id, string(object, "title", ""), string(object, "description", ""), string(object, "category", "antos:general"),
                boundedSortOrder(object), linkCategory, linkGroup, hasPosition, x, y, resourceIds(object.get("requires")), resourceIds(object.get("connects")),
                string(object, "icon", ""), string(object, "icon_entity", ""), optionalFlag(object, "green_tint"),
                optionalFlag(object, "render_mob_from_spawn_egg"), TaskAppearance.parse(object)));
    }

    private static ResourceLocation optionalId(String value) {
        return value == null || value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    private static Boolean optionalFlag(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        try { return object.get(key).getAsBoolean(); }
        catch (RuntimeException ignored) { return null; }
    }

    public static List<TaskReward> parseRewards(ResourceLocation id, JsonElement rewardElement, boolean interactive) {
        List<TaskReward> rewards = new ArrayList<>();

        int rewardUnits = 0;
        if (rewardElement != null && rewardElement.isJsonArray()) for (JsonElement item : rewardElement.getAsJsonArray()) {
            if (!item.isJsonObject() || rewards.size() >= 27) continue;
            JsonObject row = item.getAsJsonObject();
            String rewardType = string(row, "type", "");
            if (!interactive && (rewardType.equals("archive") || rewardType.equals("choice")
                    || rewardType.equals("item_pool") && string(row, "mode", "random").equalsIgnoreCase("choice"))) {
                AntOS.LOGGER.warn("Ignoring interactive or Archive reward {} on disk {}", rewardType, id);
                continue;
            }
            try {
                switch (rewardType) {
                    case "item" -> {
                        ResourceLocation itemId = ResourceLocation.tryParse(string(row, "item", ""));
                        int count = row.has("count") ? row.get("count").getAsInt() : 1;
                        if (count < 1 || count > 108 || rewardUnits + count > 108) {
                            AntOS.LOGGER.warn("Ignoring item rewards for content {} because the combined reward exceeds 108 items", id);
                            continue;
                        }
                        rewardUnits += count;
                        rewards.add(new TaskReward("item", itemId, count, 0, null, "", "", "", 0, 0, null, "", 0L));
                    }
                    case "experience" -> {
                        int points = Math.max(0, Math.min(1_000_000, row.get("points").getAsInt()));
                        if (points > 0) rewards.add(new TaskReward("experience", null, 0, points, null, "", "", "", 0, 0, null, "", 0L));
                    }
                    case "experience_levels" -> {
                        int levels = Math.max(0, Math.min(1000, row.get("levels").getAsInt()));
                        if (levels > 0) rewards.add(new TaskReward("experience_levels", null, levels, 0, null, "", "", "", 0, 0, null, "", 0L));
                    }
                    case "antcoins" -> {
                        long amount = Math.max(0L, Math.min(1_000_000_000L, row.get("amount").getAsLong()));
                        if (amount > 0L) rewards.add(new TaskReward("antcoins", null, 0, 0, null, "", "", "", 0, 0, null, "", amount));
                    }
                    case "item_pool" -> {
                        ResourceLocation poolId = ResourceLocation.tryParse(string(row, "pool", ""));
                        String mode = string(row, "mode", "random").toLowerCase(java.util.Locale.ROOT);
                        if (!mode.equals("random") && !mode.equals("choice")) throw new IllegalArgumentException("Invalid pool mode");
                        rewards.add(new TaskReward("item_pool", null, 0, 0, null, "", "", "", 0, 0, poolId, mode, 0L));
                    }
                    case "choice" -> {
                        JsonElement optionsElement = row.get("options");
                        if (optionsElement == null || !optionsElement.isJsonArray() || optionsElement.getAsJsonArray().size() < 2
                                || optionsElement.getAsJsonArray().size() > 6) throw new IllegalArgumentException("Invalid choice options");
                        List<RewardOption> options = new ArrayList<>();
                        java.util.Set<String> optionIds = new java.util.HashSet<>();
                        for (JsonElement optionElement : optionsElement.getAsJsonArray()) {
                            if (!optionElement.isJsonObject()) continue;
                            JsonObject option = optionElement.getAsJsonObject();
                            String optionId = string(option, "id", "");
                            String optionTitle = string(option, "title", optionId);
                            JsonElement optionRewardsElement = option.get("rewards");
                            if (optionId.isBlank() || optionId.length() > 64 || optionTitle.isBlank() || optionTitle.length() > 128
                                    || !optionIds.add(optionId) || optionRewardsElement == null || !optionRewardsElement.isJsonArray()
                                    || optionRewardsElement.getAsJsonArray().isEmpty() || optionRewardsElement.getAsJsonArray().size() > 8) continue;
                            List<TaskReward> optionRewards = new ArrayList<>();
                            int optionItemCount = 0;
                            boolean validOption = true;
                            for (JsonElement optionRewardElement : optionRewardsElement.getAsJsonArray()) {
                                if (!optionRewardElement.isJsonObject()) { validOption = false; break; }
                                TaskReward optionReward = parseChoiceOptionReward(optionRewardElement.getAsJsonObject(), id);
                                if (optionReward == null) { validOption = false; break; }
                                if (optionReward.type().equals("item")) optionItemCount += optionReward.count();
                                if (optionItemCount > 108) { validOption = false; break; }
                                optionRewards.add(optionReward);
                            }
                            if (validOption && !optionRewards.isEmpty()) options.add(new RewardOption(optionId, optionTitle, List.copyOf(optionRewards)));
                        }
                        if (options.size() < 2) throw new IllegalArgumentException("Choice needs at least two valid options");
                        rewards.add(new TaskReward("choice", null, 0, 0, null, "", "", "", 0, 0, null, "", 0L, List.copyOf(options)));
                    }
                    case "archive" -> {
                        ResourceLocation entryId = ResourceLocation.tryParse(string(row, "entry", ""));
                        rewards.add(new TaskReward("archive", null, 0, 0, entryId, "", "", "", 0, 0, null, "", 0L));
                    }
                    case "advancement" -> {
                        ResourceLocation advancementId = ResourceLocation.tryParse(string(row, "advancement", ""));
                        rewards.add(new TaskReward("advancement", null, 0, 0, advancementId, "", "", "", 0, 0, null, "", 0L));
                    }
                    case "effect" -> {
                        ResourceLocation effectId = ResourceLocation.tryParse(string(row, "effect", ""));
                        int duration = Math.max(1, Math.min(72_000, row.has("duration_ticks") ? row.get("duration_ticks").getAsInt() : 1200));
                        int amplifier = Math.max(0, Math.min(255, row.has("amplifier") ? row.get("amplifier").getAsInt() : 0));
                        rewards.add(new TaskReward("effect", null, 0, 0, effectId, "", "", "", duration, amplifier, null, "", 0L));
                    }
                    case "mail" -> {
                        if (row.has("message")) {
                            ResourceLocation message = ResourceLocation.tryParse(string(row, "message", ""));
                            rewards.add(new TaskReward("mail", null, 0, 0, message, "", "", "", 0, 0, null, "", 0L));
                            continue;
                        }
                        String sender = AntmailAddress.ofUsername(string(row, "sender", "")).username();
                        String subject = string(row, "subject", "");
                        String body = string(row, "body", "");
                        if (subject.isBlank() || subject.length() > AntmailValidation.MAX_SUBJECT_LENGTH
                                || body.length() > AntmailValidation.MAX_BODY_LENGTH) {
                            AntOS.LOGGER.warn("Ignoring invalid mail reward for content {} (subject/body length or empty subject)", id);
                            continue;
                        }
                        rewards.add(new TaskReward("mail", null, 0, 0, null, sender, subject, body, 0, 0, null, "", 0L));
                    }
                    default -> AntOS.LOGGER.warn("Ignoring unsupported {} reward for content {}", rewardType, id);
                }
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring malformed {} reward for content {}", rewardType, id);
            }
        }
        return List.copyOf(rewards);
    }

    public static JsonArray encodeRewards(List<TaskReward> rewards) {
        JsonArray result = new JsonArray();
        for (TaskReward reward : rewards) {
            JsonObject row = new JsonObject();
            row.addProperty("type", reward.type());
            switch (reward.type()) {
                case "item" -> { row.addProperty("item", reward.itemId().toString()); row.addProperty("count", reward.count()); }
                case "experience" -> row.addProperty("points", reward.experiencePoints());
                case "experience_levels" -> row.addProperty("levels", reward.count());
                case "antcoins" -> row.addProperty("amount", reward.antcoins());
                case "item_pool" -> { row.addProperty("pool", reward.poolId().toString()); row.addProperty("mode", reward.mode()); }
                case "archive" -> row.addProperty("entry", reward.target().toString());
                case "advancement" -> row.addProperty("advancement", reward.target().toString());
                case "effect" -> {
                    row.addProperty("effect", reward.target().toString());
                    row.addProperty("duration_ticks", reward.durationTicks());
                    row.addProperty("amplifier", reward.amplifier());
                }
                case "mail" -> {
                    if (reward.target() != null) row.addProperty("message", reward.target().toString());
                    else {
                        row.addProperty("sender", reward.sender());
                        row.addProperty("subject", reward.subject());
                        row.addProperty("body", reward.body());
                    }
                }
                case "choice" -> {
                    JsonArray options = new JsonArray();
                    for (RewardOption option : reward.options()) {
                        JsonObject object = new JsonObject();
                        object.addProperty("id", option.id());
                        object.addProperty("title", option.title());
                        object.add("rewards", encodeRewards(option.rewards()));
                        options.add(object);
                    }
                    row.add("options", options);
                }
            }
            result.add(row);
        }
        return result;
    }

    private static TaskReward parseChoiceOptionReward(JsonObject row, ResourceLocation taskId) {
        String type = string(row, "type", "");
        try {
            return switch (type) {
                case "item" -> {
                    ResourceLocation itemId = ResourceLocation.tryParse(string(row, "item", ""));
                    int count = row.has("count") ? row.get("count").getAsInt() : 1;
                    if (count < 1 || count > 108 || net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) yield null;
                    yield new TaskReward("item", itemId, count, 0, null, "", "", "", 0, 0, null, "", 0L);
                }
                case "antcoins" -> {
                    long amount = row.get("amount").getAsLong();
                    if (amount < 1L || amount > 1_000_000_000L) yield null;
                    yield new TaskReward("antcoins", null, 0, 0, null, "", "", "", 0, 0, null, "", amount);
                }
                case "item_pool" -> {
                    ResourceLocation poolId = ResourceLocation.tryParse(string(row, "pool", ""));
                    String mode = string(row, "mode", "random").toLowerCase(java.util.Locale.ROOT);
                    if (!mode.equals("random")) yield null;
                    yield new TaskReward("item_pool", null, 0, 0, null, "", "", "", 0, 0, poolId, mode, 0L);
                }
                case "experience" -> {
                    int points = Math.max(0, Math.min(1_000_000, row.get("points").getAsInt()));
                    if (points < 1) yield null;
                    yield new TaskReward("experience", null, 0, points, null, "", "", "", 0, 0, null, "", 0L);
                }
                case "experience_levels" -> {
                    int levels = Math.max(0, Math.min(1000, row.get("levels").getAsInt()));
                    if (levels < 1) yield null;
                    yield new TaskReward("experience_levels", null, levels, 0, null, "", "", "", 0, 0, null, "", 0L);
                }
                case "advancement" -> new TaskReward("advancement", null, 0, 0,
                        ResourceLocation.tryParse(string(row, "advancement", "")), "", "", "", 0, 0, null, "", 0L);
                case "effect" -> {
                    ResourceLocation effectId = ResourceLocation.tryParse(string(row, "effect", ""));
                    int duration = Math.max(1, Math.min(72_000, row.has("duration_ticks") ? row.get("duration_ticks").getAsInt() : 1200));
                    int amplifier = Math.max(0, Math.min(255, row.has("amplifier") ? row.get("amplifier").getAsInt() : 0));
                    yield new TaskReward("effect", null, 0, 0, effectId, "", "", "", duration, amplifier, null, "", 0L);
                }
                case "mail" -> {
                    String sender = AntmailAddress.ofUsername(string(row, "sender", "")).username();
                    String subject = string(row, "subject", "");
                    String body = string(row, "body", "");
                    if (subject.isBlank() || subject.length() > AntmailValidation.MAX_SUBJECT_LENGTH || body.length() > AntmailValidation.MAX_BODY_LENGTH) yield null;
                    yield new TaskReward("mail", null, 0, 0, null, sender, subject, body, 0, 0, null, "", 0L);
                }
                default -> null;
            };
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring malformed reward option entry for task {}", taskId);
            return null;
        }
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

    private static Integer boundedPosition(JsonObject position, String key) {
        if (!position.has(key)) return null;
        try { return Math.max(-128, Math.min(128, position.get(key).getAsInt())); }
        catch (RuntimeException ignored) { return null; }
    }

    private static void parseRewardPool(ResourceLocation id, JsonElement element, Map<ResourceLocation, RewardPool> target) {
        if (!element.isJsonObject()) return;
        JsonElement entriesElement = element.getAsJsonObject().get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray() || entriesElement.getAsJsonArray().size() > 64) {
            AntOS.LOGGER.warn("Ignoring task reward pool {} with missing or oversized entries", id);
            return;
        }
        List<RewardPoolEntry> entries = new ArrayList<>();
        for (JsonElement value : entriesElement.getAsJsonArray()) {
            if (!value.isJsonObject()) continue;
            JsonObject row = value.getAsJsonObject();
            try {
                boolean hasItem = row.has("item");
                boolean hasPool = row.has("pool");
                if (hasItem == hasPool) continue;
                ResourceLocation itemId = hasItem ? ResourceLocation.tryParse(string(row, "item", "")) : null;
                ResourceLocation poolId = hasPool ? ResourceLocation.tryParse(string(row, "pool", "")) : null;
                int weight = row.has("weight") ? row.get("weight").getAsInt() : 1;
                int count = row.has("count") ? row.get("count").getAsInt() : 1;
                if (weight < 1 || weight > 1_000_000 || count < 1 || count > 108) continue;
                if (itemId != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) continue;
                entries.add(new RewardPoolEntry(itemId, poolId, weight, count));
            } catch (RuntimeException ignored) { }
        }
        if (entries.isEmpty()) {
            AntOS.LOGGER.warn("Ignoring empty task reward pool {}", id);
            return;
        }
        target.put(id, new RewardPool(id, List.copyOf(entries)));
    }

    private static void validateRewardPools(Map<ResourceLocation, RewardPool> pools) {
        java.util.Set<ResourceLocation> invalid = new java.util.LinkedHashSet<>();
        java.util.Set<ResourceLocation> complete = new java.util.HashSet<>();
        List<ResourceLocation> active = new ArrayList<>();
        for (ResourceLocation id : pools.keySet()) validateRewardPool(id, pools, complete, active, invalid);
        boolean changed;
        do {
            changed = false;
            for (RewardPool pool : pools.values()) if (!invalid.contains(pool.id())) {
                if (pool.entries().stream().anyMatch(entry -> entry.poolId() != null && !pools.containsKey(entry.poolId()))) {
                    invalid.add(pool.id());
                    changed = true;
                }
            }
        } while (changed);
        for (ResourceLocation id : invalid) {
            if (pools.remove(id) != null) AntOS.LOGGER.warn("Ignoring task reward pool {} because it contains a cycle or missing pool reference", id);
        }
    }

    private static void validateRewardPool(ResourceLocation id, Map<ResourceLocation, RewardPool> pools,
                                           java.util.Set<ResourceLocation> complete, List<ResourceLocation> active,
                                           java.util.Set<ResourceLocation> invalid) {
        if (active.size() >= 8) { invalid.add(id); return; }
        if (complete.contains(id)) return;
        int cycleStart = active.indexOf(id);
        if (cycleStart >= 0) {
            invalid.addAll(active.subList(cycleStart, active.size()));
            return;
        }
        RewardPool pool = pools.get(id);
        if (pool == null) return;
        active.add(id);
        for (RewardPoolEntry entry : pool.entries()) if (entry.poolId() != null) {
            if (!pools.containsKey(entry.poolId())) invalid.add(id);
            else validateRewardPool(entry.poolId(), pools, complete, active, invalid);
        }
        active.remove(active.size() - 1);
        complete.add(id);
    }

    private static void parseTask(JsonObject object, Map<ResourceLocation, Task> target) {
        try { parseTask(ResourceLocation.tryParse(string(object, "id", "")), object, target); }
        catch (RuntimeException exception) { AntOS.LOGGER.warn("Ignoring malformed AntOS task in network snapshot", exception); }
    }

    private static List<ResourceLocation> resourceIds(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) if (value.isJsonPrimitive()) try { result.add(ResourceLocation.tryParse(value.getAsString())); } catch (RuntimeException ignored) { }
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
                         boolean unlockedByDefault, List<GalleryItem> gallery, String galleryTitleKey, ResourceLocation folder, int sortOrder,
                         List<String> lookup, List<String> lookupRecipes) {
        public Entry {
            lookup = lookup == null ? List.of() : List.copyOf(lookup);
            lookupRecipes = lookupRecipes == null ? List.of() : List.copyOf(lookupRecipes);
        }
        public Entry(ResourceLocation id, String type, String category, String titleKey, String subtitleKey,
                     List<String> descriptionKeys, String itemId, String entityId, String enchantmentId,
                     String recipeId, String structureId, String structureTagId, String dimensionId, int searchRadius, String locatorId, String coverItemId,
                     String coverEntityId, String coverPotionId, float rotation, float renderScale, boolean greenTint,
                     boolean unlockedByDefault, List<GalleryItem> gallery, String galleryTitleKey, ResourceLocation folder, int sortOrder,
                     List<String> lookup) {
            this(id, type, category, titleKey, subtitleKey, descriptionKeys, itemId, entityId, enchantmentId, recipeId, structureId, structureTagId,
                    dimensionId, searchRadius, locatorId, coverItemId, coverEntityId, coverPotionId, rotation, renderScale, greenTint, unlockedByDefault,
                    gallery, galleryTitleKey, folder, sortOrder, lookup, List.of());
        }
        public Entry(ResourceLocation id, String type, String category, String titleKey, String subtitleKey,
                     List<String> descriptionKeys, String itemId, String entityId, String enchantmentId,
                     String recipeId, String structureId, String structureTagId, String dimensionId, int searchRadius, String locatorId, String coverItemId,
                     String coverEntityId, String coverPotionId, float rotation, float renderScale, boolean greenTint,
                     boolean unlockedByDefault, List<GalleryItem> gallery, String galleryTitleKey, ResourceLocation folder, int sortOrder) {
            this(id, type, category, titleKey, subtitleKey, descriptionKeys, itemId, entityId, enchantmentId, recipeId, structureId, structureTagId,
                    dimensionId, searchRadius, locatorId, coverItemId, coverEntityId, coverPotionId, rotation, renderScale, greenTint, unlockedByDefault,
                    gallery, galleryTitleKey, folder, sortOrder, List.of(), List.of());
        }
        public Entry(ResourceLocation id, String type, String category, String titleKey, String subtitleKey,
                     List<String> descriptionKeys, String itemId, String entityId, String enchantmentId,
                     String recipeId, String structureId, String structureTagId, String dimensionId, int searchRadius, String locatorId, String coverItemId,
                     String coverEntityId, String coverPotionId, float rotation, float renderScale, boolean greenTint,
                     boolean unlockedByDefault, List<GalleryItem> gallery, String galleryTitleKey) {
            this(id, type, category, titleKey, subtitleKey, descriptionKeys, itemId, entityId, enchantmentId, recipeId, structureId, structureTagId,
                    dimensionId, searchRadius, locatorId, coverItemId, coverEntityId, coverPotionId, rotation, renderScale, greenTint, unlockedByDefault,
                    gallery, galleryTitleKey, null, 0);
        }
        public Entry(ResourceLocation id, String type, String category, String titleKey, String subtitleKey,
                     List<String> descriptionKeys, String itemId, String entityId, String enchantmentId,
                     String recipeId, String structureId, String structureTagId, String dimensionId, int searchRadius, String locatorId, String coverItemId,
                     String coverEntityId, String coverPotionId, float rotation, float renderScale, boolean greenTint,
                     boolean unlockedByDefault) {
            this(id, type, category, titleKey, subtitleKey, descriptionKeys, itemId, entityId, enchantmentId, recipeId, structureId, structureTagId,
                    dimensionId, searchRadius, locatorId, coverItemId, coverEntityId, coverPotionId, rotation, renderScale, greenTint, unlockedByDefault,
                    List.of(), "");
        }
    }

    public record ArchiveFolder(ResourceLocation id, String titleKey, String iconItem, int sortOrder, ResourceLocation parent, boolean greenTint) {
        public ArchiveFolder(ResourceLocation id, String titleKey, String iconItem, int sortOrder, ResourceLocation parent) {
            this(id, titleKey, iconItem, sortOrder, parent, true);
        }
        public ArchiveFolder(ResourceLocation id, String titleKey, String iconItem, int sortOrder) {
            this(id, titleKey, iconItem, sortOrder, null);
        }
    }

    public record GalleryItem(String kind, String id, String nameKey) {
        public boolean entity() { return kind.equals("entity"); }

        public boolean item() { return kind.equals("item"); }
    }

    public record Disk(ResourceLocation id, ResourceLocation category, String titleKey, List<ResourceLocation> entries, String wallpaper, List<ResourceLocation> tasks, String descriptionKey, boolean hidden, List<TaskReward> rewards, DiskFeatures features) {
        public Disk(ResourceLocation id, ResourceLocation category, String titleKey, List<ResourceLocation> entries, String wallpaper, List<ResourceLocation> tasks, String descriptionKey, boolean hidden, List<TaskReward> rewards) {
            this(id, category, titleKey, entries, wallpaper, tasks, descriptionKey, hidden, rewards, DiskFeatures.EMPTY);
        }
        public Disk(ResourceLocation id, ResourceLocation category, String titleKey, List<ResourceLocation> entries, String wallpaper, List<ResourceLocation> tasks, String descriptionKey, boolean hidden) {
            this(id, category, titleKey, entries, wallpaper, tasks, descriptionKey, hidden, List.of());
        }
        public Disk(ResourceLocation id, ResourceLocation category, String titleKey, List<ResourceLocation> entries, String wallpaper, List<ResourceLocation> tasks) {
            this(id, category, titleKey, entries, wallpaper, tasks, "", false);
        }
    }

    public record DiskCategory(ResourceLocation id, String titleKey, ResourceLocation texture) { }

    public record Wallpaper(ResourceLocation id, String titleKey, String textureId, String fit) {
    }

    public record Task(ResourceLocation id, String titleKey, String descriptionKey, String program, String category, int sortOrder, String availability,
                       List<ResourceLocation> requires, List<Objective> objectives, List<ResourceLocation> archiveEntries, List<TaskReward> rewards, boolean hasPosition, int x, int y,
                       boolean hideUntilDependenciesComplete, boolean invisibleUntilCompleted, boolean greenTint, String iconItem, String iconEntity, boolean renderMobFromSpawnEgg, TaskAppearance appearance,
                       List<ResourceLocation> connects, List<String> showcase, List<ResourceLocation> requiresAny) {
        public boolean hasPrerequisites() { return !requires.isEmpty() || !requiresAny.isEmpty(); }

        public boolean prerequisitesMet(java.util.function.Predicate<ResourceLocation> complete) {
            for (ResourceLocation required : requires) if (!complete.test(required)) return false;
            if (requiresAny.isEmpty()) return true;
            for (ResourceLocation required : requiresAny) if (complete.test(required)) return true;
            return false;
        }

        public List<ResourceLocation> prerequisites() {
            if (requiresAny.isEmpty()) return requires;
            List<ResourceLocation> all = new ArrayList<>(requires);
            all.addAll(requiresAny);
            return all;
        }
    }
    public record TaskLink(ResourceLocation id, String titleKey, String descriptionKey, String category, int sortOrder, ResourceLocation linkCategory, ResourceLocation linkGroup,
                           boolean hasPosition, int x, int y, List<ResourceLocation> requires, List<ResourceLocation> connects, String iconItem, String iconEntity,
                           Boolean greenTint, Boolean renderMobFromSpawnEgg, TaskAppearance appearance) { }
    public record TaskCategory(ResourceLocation id, String titleKey, String iconItem, String iconEntity, int sortOrder, ResourceLocation group, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record TaskGroup(ResourceLocation id, String titleKey, String iconItem, String iconEntity, int sortOrder, boolean collapsed, boolean greenTint, boolean renderMobFromSpawnEgg) { }
    public record Objective(String id, String type, String target, String statType, String descriptionKey, int count, boolean optional, boolean sticky, String tagMode, boolean consume, boolean notifyPlayer, String targetCategory, String ending) {
        public Objective(String id, String type, String target, String statType, String descriptionKey, int count, boolean optional, boolean sticky, String tagMode, boolean consume, boolean notifyPlayer) {
            this(id, type, target, statType, descriptionKey, count, optional, sticky, tagMode, consume, notifyPlayer, "", "");
        }
    }
    public record TaskReward(String type, ResourceLocation itemId, int count, int experiencePoints, ResourceLocation target,
                             String sender, String subject, String body, int durationTicks, int amplifier,
                             ResourceLocation poolId, String mode, long antcoins, List<RewardOption> options) {
        public TaskReward(String type, ResourceLocation itemId, int count, int experiencePoints, ResourceLocation target,
                          String sender, String subject, String body, int durationTicks, int amplifier,
                          ResourceLocation poolId, String mode, long antcoins) {
            this(type, itemId, count, experiencePoints, target, sender, subject, body, durationTicks, amplifier, poolId, mode, antcoins, List.of());
        }
    }
    public record RewardOption(String id, String title, List<TaskReward> rewards) { }
    public record RewardPool(ResourceLocation id, List<RewardPoolEntry> entries) { }
    public record RewardPoolEntry(ResourceLocation itemId, ResourceLocation poolId, int weight, int count) { }

    record LoadedData(Map<ResourceLocation, ArchiveFolder> archiveFolders, Map<ResourceLocation, Entry> entries, Map<ResourceLocation, Disk> disks, Map<ResourceLocation, DiskCategory> categories, Map<ResourceLocation, Wallpaper> wallpapers, Map<ResourceLocation, Task> tasks, Map<ResourceLocation, RewardPool> rewardPools,
                      Map<ResourceLocation, TaskCategory> taskCategories, Map<ResourceLocation, TaskGroup> taskGroups, Map<ResourceLocation, TaskLink> taskLinks,
                      Map<ResourceLocation, String> diskFiles, Map<ResourceLocation, com.craisinlord.antos.content.computer.ComputerProgramData.Program> programs,
                      Map<ResourceLocation, com.craisinlord.antos.content.computer.terminal.TerminalCommandData.Definition> terminalCommands,
                      Map<ResourceLocation, JsonObject> displays) {
    }
}

