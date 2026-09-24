package com.craisinlord.antos.content.client;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerArchiveUnlockClientState {
    private static final Map<String, List<ResourceLocation>> UNLOCKS = new ConcurrentHashMap<>();
    private static final Map<String, List<ResourceLocation>> INSTALLED_DISKS = new ConcurrentHashMap<>();

    private ComputerArchiveUnlockClientState() { }

    public static void clearAll() { UNLOCKS.clear(); INSTALLED_DISKS.clear(); }

    public static void update(String snapshot) {
        try {
            var root = JsonParser.parseString(snapshot).getAsJsonObject();
            var array = root.getAsJsonArray("unlocked_entries");
            List<ResourceLocation> ids = array == null ? List.of() : array.asList().stream().map(value -> {
                try { return ResourceLocation.parse(value.getAsString()); }
                catch (RuntimeException ignored) { return null; }
            }).filter(java.util.Objects::nonNull).distinct().toList();
            String key = ComputerWorkspaceClientKey.of();
            UNLOCKS.put(key, ids);
            var disks = root.getAsJsonArray("installed_disks");
            if (disks != null) {
                List<ResourceLocation> diskIds = disks.asList().stream().map(value -> {
                    try { return ResourceLocation.parse(value.getAsString()); }
                    catch (RuntimeException ignored) { return null; }
                }).filter(java.util.Objects::nonNull).distinct().toList();
                INSTALLED_DISKS.put(key, diskIds);
            }
        } catch (RuntimeException ignored) { }
    }

    public static List<ResourceLocation> get() {
        return UNLOCKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of());
    }

    public static List<ResourceLocation> installedDisks() {
        return INSTALLED_DISKS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of());
    }

    public static void clear() {
        UNLOCKS.remove(ComputerWorkspaceClientKey.of());
        INSTALLED_DISKS.remove(ComputerWorkspaceClientKey.of());
    }
}
