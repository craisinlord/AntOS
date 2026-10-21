package com.craisinlord.antos.content.client;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerArchiveUnlockClientState {
    private static final Map<String, List<ResourceLocation>> UNLOCKS = new ConcurrentHashMap<>();
    private static final Map<String, List<ResourceLocation>> INSTALLED_DISKS = new ConcurrentHashMap<>();
    private static final Map<String, List<ResourceLocation>> COLLECTED_DISKS = new ConcurrentHashMap<>();
    private static final Map<String, List<ResourceLocation>> ACTIVATED_DISKS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, List<String>>> DISK_FILES = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, List<com.craisinlord.antos.content.guide.ComputerGuideData.TaskReward>>> DISK_REWARDS = new ConcurrentHashMap<>();

    private ComputerArchiveUnlockClientState() { }

    public static void clearAll() { UNLOCKS.clear(); INSTALLED_DISKS.clear(); COLLECTED_DISKS.clear(); DISK_REWARDS.clear(); ACTIVATED_DISKS.clear(); DISK_FILES.clear(); }

    public static void update(String snapshot) {
        try {
            var root = JsonParser.parseString(snapshot).getAsJsonObject();
            com.craisinlord.antos.content.guide.ComputerGuideData.applyNetworkSettings(root);
            var array = root.getAsJsonArray("unlocked_entries");
            List<ResourceLocation> ids = array == null ? List.of() : array.asList().stream().map(value -> {
                try { return ResourceLocation.tryParse(value.getAsString()); }
                catch (RuntimeException ignored) { return null; }
            }).filter(java.util.Objects::nonNull).distinct().toList();
            if (root.has("lookup_only")) {
                var activated = root.getAsJsonArray("activated_disks");
                ArchiveLookup.remember(ids, activated == null ? List.of() : activated.asList().stream()
                        .map(value -> ResourceLocation.tryParse(value.getAsString())).filter(java.util.Objects::nonNull).toList());
                Map<String, String> tasks = new java.util.HashMap<>();
                if (root.get("lookup_tasks") instanceof com.google.gson.JsonObject lookupTasks)
                    lookupTasks.entrySet().forEach(entry -> tasks.put(entry.getKey(), entry.getValue().getAsString()));
                ArchiveLookup.rememberTasks(tasks);
                return;
            }
            String key = ComputerWorkspaceClientKey.of();
            UNLOCKS.put(key, ids);
            var disks = root.getAsJsonArray("installed_disks");
            if (disks != null) {
                List<ResourceLocation> diskIds = disks.asList().stream().map(value -> {
                    try { return ResourceLocation.tryParse(value.getAsString()); }
                    catch (RuntimeException ignored) { return null; }
                }).filter(java.util.Objects::nonNull).distinct().toList();
                INSTALLED_DISKS.put(key, diskIds);
            }
            var collected = root.getAsJsonArray("collected_disks");
            COLLECTED_DISKS.put(key, collected == null ? INSTALLED_DISKS.getOrDefault(key, List.of())
                    : collected.asList().stream().map(value -> ResourceLocation.tryParse(value.getAsString()))
                    .filter(java.util.Objects::nonNull).distinct().toList());
            Map<ResourceLocation, List<com.craisinlord.antos.content.guide.ComputerGuideData.TaskReward>> rewards = new java.util.LinkedHashMap<>();
            var receipts = root.getAsJsonObject("disk_reward_receipts");
            if (receipts != null) receipts.entrySet().forEach(entry -> {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id != null) rewards.put(id, DiskRewardsClientState.decode(id, entry.getValue()));
            });
            DISK_REWARDS.put(key, Map.copyOf(rewards));
            var activated = root.getAsJsonArray("activated_disks");
            ACTIVATED_DISKS.put(key, activated == null ? collectedDisks() : activated.asList().stream()
                    .map(value -> ResourceLocation.tryParse(value.getAsString())).filter(java.util.Objects::nonNull).toList());
            Map<ResourceLocation, List<String>> files = new java.util.LinkedHashMap<>();
            if (root.has("disk_files")) root.getAsJsonObject("disk_files").entrySet().forEach(entry -> {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id != null) files.put(id, entry.getValue().getAsJsonArray().asList().stream().map(value -> value.getAsString()).toList());
            });
            DISK_FILES.put(key, Map.copyOf(files));
            ComputerProgramClientState.updateArchive(root);
            if (!key.equals("workspace:unbound")) ArchiveLookup.remember(ids, ACTIVATED_DISKS.getOrDefault(key, List.of()));
        } catch (RuntimeException ignored) { }
    }

    public static List<ResourceLocation> get() {
        return UNLOCKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of());
    }

    public static List<ResourceLocation> installedDisks() {
        return INSTALLED_DISKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of());
    }

    public static List<ResourceLocation> collectedDisks() {
        return COLLECTED_DISKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of());
    }

    public static List<ResourceLocation> activatedDisks() { return ACTIVATED_DISKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static List<String> diskFiles(ResourceLocation id) { return DISK_FILES.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()).getOrDefault(id, List.of()); }

    public static boolean hasSnapshot() {
        return UNLOCKS.containsKey(ComputerWorkspaceClientKey.of());
    }

    public static List<com.craisinlord.antos.content.guide.ComputerGuideData.TaskReward> diskRewards(ResourceLocation id) {
        return DISK_REWARDS.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()).getOrDefault(id, List.of());
    }

    public static void clear() {
        UNLOCKS.remove(ComputerWorkspaceClientKey.of());
        INSTALLED_DISKS.remove(ComputerWorkspaceClientKey.of());
        COLLECTED_DISKS.remove(ComputerWorkspaceClientKey.of());
        DISK_REWARDS.remove(ComputerWorkspaceClientKey.of());
        ACTIVATED_DISKS.remove(ComputerWorkspaceClientKey.of());
        DISK_FILES.remove(ComputerWorkspaceClientKey.of());
    }
}
