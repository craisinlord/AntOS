package com.craisinlord.antos.content.computer.games;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ComputerGameData extends SavedData {
    private static final String ID = "antos_game_data";
    private static final SavedData.Factory<ComputerGameData> FACTORY = new SavedData.Factory<>(ComputerGameData::new, ComputerGameData::load, null);
    private final Map<UUID, Map<String, String>> profiles = new HashMap<>();

    private static ComputerGameData access(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static Map<String, String> entries(MinecraftServer server, UUID profile) {
        Map<String, String> entries = access(server).profiles.get(profile);
        return entries == null ? Map.of() : Map.copyOf(entries);
    }

    public static void apply(MinecraftServer server, UUID profile, String op, String key, String value) {
        if (profile == null) return;
        ComputerGameData data = access(server);
        Map<String, String> entries = data.profiles.computeIfAbsent(profile, ignored -> new HashMap<>());
        if (GameDataCodec.apply(entries, op, key, value)) data.setDirty();
        if (entries.isEmpty()) data.profiles.remove(profile);
    }

    private static ComputerGameData load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerGameData data = new ComputerGameData();
        for (net.minecraft.nbt.Tag raw : tag.getList("Profiles", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            UUID profile;
            try {
                profile = UUID.fromString(row.getString("Profile"));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            Map<String, String> entries = GameDataCodec.decode(row.getString("Data"));
            if (!entries.isEmpty()) data.profiles.put(profile, new HashMap<>(entries));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        profiles.forEach((profile, entries) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Profile", profile.toString());
            row.putString("Data", GameDataCodec.encode(entries));
            rows.add(row);
        });
        tag.put("Profiles", rows);
        return tag;
    }
}
