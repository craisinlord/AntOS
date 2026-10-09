package com.craisinlord.antos.content.computer.blockle;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BlockleSavedData extends SavedData {
    private static final String ID = "antos_blockle";
    private final Map<String, State> games = new HashMap<>();
    private final Map<UUID, Long> rewardDays = new HashMap<>();

    public static State get(MinecraftServer server, String key) {
        BlockleSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BlockleSavedData::new, BlockleSavedData::load, null), ID);
        return data.games.computeIfAbsent(key, ignored -> new State());
    }

    public static void changed(MinecraftServer server) {
        server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BlockleSavedData::new, BlockleSavedData::load, null), ID).setDirty();
    }

    public static boolean claimReward(MinecraftServer server, UUID profile, long day) {
        BlockleSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BlockleSavedData::new, BlockleSavedData::load, null), ID);
        if (profile == null || data.rewardDays.getOrDefault(profile, Long.MIN_VALUE) == day) return false;
        data.rewardDays.put(profile, day);
        data.setDirty();
        return true;
    }

    private static BlockleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockleSavedData data = new BlockleSavedData();
        for (net.minecraft.nbt.Tag raw : tag.getList("Games", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            State state = new State();
            state.day = row.getLong("Day");
            for (net.minecraft.nbt.Tag guess : row.getList("Guesses", net.minecraft.nbt.Tag.TAG_STRING)) state.guesses.add(guess.getAsString());
            data.games.put(row.getString("Computer"), state);
        }
        for (net.minecraft.nbt.Tag raw : tag.getList("RewardDays", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            try {
                data.rewardDays.put(UUID.fromString(row.getString("Profile")), row.getLong("Day"));
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        games.forEach((key, state) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Computer", key);
            row.putLong("Day", state.day);
            ListTag guesses = new ListTag();
            state.guesses.forEach(guess -> guesses.add(net.minecraft.nbt.StringTag.valueOf(guess)));
            row.put("Guesses", guesses);
            rows.add(row);
        });
        tag.put("Games", rows);
        ListTag rewardRows = new ListTag();
        rewardDays.forEach((profile, day) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Profile", profile.toString());
            row.putLong("Day", day);
            rewardRows.add(row);
        });
        tag.put("RewardDays", rewardRows);
        return tag;
    }

    public static final class State {
        public long day = Long.MIN_VALUE;
        public final List<String> guesses = new ArrayList<>();
    }
}
