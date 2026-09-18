package com.craisinlord.antos.content.client;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerArchiveUnlockClientState {
    private static final Map<BlockPos, List<ResourceLocation>> UNLOCKS = new ConcurrentHashMap<>();

    private ComputerArchiveUnlockClientState() { }

    public static void update(BlockPos pos, String snapshot) {
        try {
            var root = JsonParser.parseString(snapshot).getAsJsonObject();
            var array = root.getAsJsonArray("unlocked_entries");
            if (array == null) {
                UNLOCKS.put(pos, List.of());
                return;
            }
            List<ResourceLocation> ids = array.asList().stream().map(value -> {
                try { return ResourceLocation.parse(value.getAsString()); }
                catch (RuntimeException ignored) { return null; }
            }).filter(java.util.Objects::nonNull).distinct().toList();
            UNLOCKS.put(pos, ids);
        } catch (RuntimeException ignored) { }
    }

    public static List<ResourceLocation> get(BlockPos pos) {
        return UNLOCKS.getOrDefault(pos, List.of());
    }

    public static void clear(BlockPos pos) {
        UNLOCKS.remove(pos);
    }
}
