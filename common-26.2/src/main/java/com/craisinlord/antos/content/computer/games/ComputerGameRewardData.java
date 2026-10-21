package com.craisinlord.antos.content.computer.games;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ComputerGameRewardData extends SavedData {
    private static final String ID = "antos_game_rewards";
    private static final net.minecraft.world.level.saveddata.SavedDataType<ComputerGameRewardData> FACTORY = com.craisinlord.antos.content.AntOSSavedDataType.create(ID, ComputerGameRewardData::new, tag -> load(tag, null), (data, tag) -> data.save(tag, null));
    private final Map<String, Long> claimDays = new HashMap<>();
    private long prunedDay = Long.MIN_VALUE;

    private static ComputerGameRewardData access(MinecraftServer server) {
        ComputerGameRewardData data = server.overworld().getDataStorage().computeIfAbsent(FACTORY);
        long day = server.overworld().getOverworldClockTime() / 24000L;
        if (data.prunedDay != day) {
            data.prunedDay = day;
            if (data.claimDays.values().removeIf(claimed -> claimed != day)) data.setDirty();
        }
        return data;
    }

    public static boolean claim(MinecraftServer server, UUID profile, String mode, long day) {
        if (profile == null || mode == null || mode.isBlank()) return false;
        ComputerGameRewardData data = access(server);
        String key = profile + "|" + mode;
        if (data.claimDays.getOrDefault(key, Long.MIN_VALUE) == day) return false;
        data.claimDays.put(key, day);
        data.setDirty();
        return true;
    }

    private static ComputerGameRewardData load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerGameRewardData data = new ComputerGameRewardData();
        for (net.minecraft.nbt.Tag raw : tag.getListOrEmpty("Claims")) {
            CompoundTag row = (CompoundTag) raw;
            data.claimDays.put(row.getStringOr("Key", ""), row.getLongOr("Day", 0L));
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        claimDays.forEach((key, day) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Key", key);
            row.putLong("Day", day);
            rows.add(row);
        });
        tag.put("Claims", rows);
        return tag;
    }
}
