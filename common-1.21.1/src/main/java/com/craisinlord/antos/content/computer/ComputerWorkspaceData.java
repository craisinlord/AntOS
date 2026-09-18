package com.craisinlord.antos.content.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ComputerWorkspaceData extends SavedData {
    private static final String DATA_ID = "antos_computer_workspaces";
    private static final String WORKSPACES_TAG = "Workspaces";
    private static final String OWNERS_TAG = "Owners";
    private final Map<UUID, CompoundTag> workspaces = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeWorkspaceByOwner = new LinkedHashMap<>();

    public static ComputerWorkspaceData access(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ComputerWorkspaceData::new, ComputerWorkspaceData::load, null), DATA_ID);
    }

    private static ComputerWorkspaceData load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        ListTag workspaces = tag.getList(WORKSPACES_TAG, 10);
        for (int index = 0; index < workspaces.size(); index++) {
            CompoundTag row = workspaces.getCompound(index);
            try {
                UUID workspaceId = UUID.fromString(row.getString("Id"));
                data.workspaces.put(workspaceId, row.getCompound("Progress").copy());
            } catch (RuntimeException ignored) { }
        }
        ListTag owners = tag.getList(OWNERS_TAG, 10);
        for (int index = 0; index < owners.size(); index++) {
            CompoundTag row = owners.getCompound(index);
            try {
                data.activeWorkspaceByOwner.put(UUID.fromString(row.getString("Owner")), UUID.fromString(row.getString("Workspace")));
            } catch (RuntimeException ignored) { }
        }
        return data;
    }

    public synchronized boolean hasWorkspace(UUID owner) {
        UUID workspace = activeWorkspace(owner);
        return progress(workspace).hasRecoverableProgress();
    }

    public synchronized UUID activeWorkspace(UUID owner) {
        return owner == null ? null : activeWorkspaceByOwner.get(owner);
    }

    public synchronized ComputerTaskProgress progress(UUID workspaceId) {
        ComputerTaskProgress result = new ComputerTaskProgress();
        CompoundTag saved = workspaceId == null ? null : workspaces.get(workspaceId);
        if (saved != null) result.load(saved.copy());
        return result;
    }

    /** Starts a new workspace and makes it the owner's active recovery point. */
    public synchronized UUID createWorkspace(UUID owner, ComputerTaskProgress progress) {
        if (owner == null || progress == null) return null;
        UUID workspaceId = UUID.randomUUID();
        activeWorkspaceByOwner.put(owner, workspaceId);
        workspaces.put(workspaceId, progress.save());
        setDirty();
        return workspaceId;
    }

    public synchronized boolean setActiveWorkspace(UUID owner, UUID workspaceId) {
        if (owner == null || workspaceId == null || !workspaces.containsKey(workspaceId)) return false;
        UUID previous = activeWorkspaceByOwner.put(owner, workspaceId);
        if (!workspaceId.equals(previous)) setDirty();
        return true;
    }

    public synchronized void save(UUID workspaceId, ComputerTaskProgress progress) {
        if (workspaceId == null || progress == null) return;
        CompoundTag snapshot = progress.save();
        CompoundTag previous = workspaces.put(workspaceId, snapshot);
        if (previous == null || !previous.equals(snapshot)) setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : workspaces.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", entry.getKey().toString());
            row.put("Progress", entry.getValue().copy());
            rows.add(row);
        }
        tag.put(WORKSPACES_TAG, rows);
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, UUID> entry : activeWorkspaceByOwner.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Owner", entry.getKey().toString());
            row.putString("Workspace", entry.getValue().toString());
            owners.add(row);
        }
        tag.put(OWNERS_TAG, owners);
        return tag;
    }
}
