package com.craisinlord.antos.content.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ComputerAccountShard extends SavedData {
    static final String SNAPSHOT_TAG = "Snapshot";
    static final String DISKS_TAG = "Disks";
    static final String COLLECTED_DISKS_TAG = "CollectedDisks";
    static final String DISK_STATE_TAG = "DiskState";
    static final String DISK_REWARDS_TAG = "DiskRewards";
    final Set<ResourceLocation> profileDisks = new LinkedHashSet<>();
    final Set<ResourceLocation> collectedDisks = new LinkedHashSet<>();
    final Map<ResourceLocation, String> diskRewardReceipts = new LinkedHashMap<>();
    CompoundTag snapshot = new CompoundTag();
    DiskProfileState diskState = new DiskProfileState();
    private CompoundTag savedDiskState = diskState.save();
    private ComputerWorkspaceData owner;
    private long checkedEpoch = -1;
    private boolean persisted;

    static String dataId(UUID accountId) {
        return "antos_account_" + accountId;
    }

    void bind(ComputerWorkspaceData owner) {
        this.owner = owner;
    }

    boolean persisted() {
        return persisted;
    }

    private Object lock() {
        return owner == null ? this : owner;
    }

    static ComputerAccountShard load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerAccountShard shard = new ComputerAccountShard();
        shard.snapshot = tag.getCompound(SNAPSHOT_TAG).copy();
        ListTag diskTags = tag.getList(DISKS_TAG, 8);
        for (int index = 0; index < diskTags.size(); index++) {
            try { shard.profileDisks.add(ResourceLocation.tryParse(diskTags.getString(index))); }
            catch (RuntimeException ignored) { }
        }
        shard.collectedDisks.addAll(shard.profileDisks);
        ListTag collectedTags = tag.getList(COLLECTED_DISKS_TAG, 8);
        for (int index = 0; index < collectedTags.size(); index++) {
            try { shard.collectedDisks.add(ResourceLocation.tryParse(collectedTags.getString(index))); }
            catch (RuntimeException ignored) { }
        }
        shard.diskState = DiskProfileState.load(tag.getCompound(DISK_STATE_TAG));
        if (!tag.contains(DISK_STATE_TAG)) shard.diskState.seed(List.copyOf(shard.collectedDisks));
        shard.savedDiskState = shard.diskState.save();
        ListTag rewards = tag.getList(DISK_REWARDS_TAG, 10);
        for (int index = 0; index < rewards.size(); index++) {
            CompoundTag reward = rewards.getCompound(index);
            try { shard.diskRewardReceipts.put(ResourceLocation.tryParse(reward.getString("Disk")), reward.getString("Receipt")); }
            catch (RuntimeException ignored) { }
        }
        return shard;
    }

    static ComputerAccountShard load(CompoundTag tag) {
        return load(tag, null);
    }

    @Override
    public void setDirty() {
        super.setDirty();
        if (owner != null) owner.touch();
    }

    @Override
    public boolean isDirty() {
        if (super.isDirty()) return true;
        synchronized (lock()) {
            long epoch = owner == null ? checkedEpoch + 1 : owner.mutationEpoch();
            if (epoch == checkedEpoch) return false;
            checkedEpoch = epoch;
            if (diskState.save().equals(savedDiskState)) return false;
            super.setDirty();
            return true;
        }
    }

    public void save(File file, HolderLookup.Provider registries) {
        if (!isDirty()) return;
        if (SavedDataFiles.write(this, file, registries) && !persisted) {
            persisted = true;
            if (owner != null) owner.shardPersisted();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        synchronized (lock()) {
            write(tag);
            savedDiskState = tag.getCompound(DISK_STATE_TAG).copy();
            return tag;
        }
    }

    CompoundTag write(CompoundTag tag) {
        synchronized (lock()) {
            tag.put(SNAPSHOT_TAG, snapshot);
            tag.put(DISKS_TAG, strings(profileDisks));
            tag.put(COLLECTED_DISKS_TAG, strings(collectedDisks));
            tag.put(DISK_STATE_TAG, diskState.save());
            ListTag rewards = new ListTag();
            diskRewardReceipts.forEach((disk, receipt) -> {
                CompoundTag reward = new CompoundTag();
                reward.putString("Disk", disk.toString());
                reward.putString("Receipt", receipt);
                rewards.add(reward);
            });
            tag.put(DISK_REWARDS_TAG, rewards);
            return tag;
        }
    }

    private static ListTag strings(Set<ResourceLocation> values) {
        ListTag list = new ListTag();
        for (ResourceLocation value : values) list.add(StringTag.valueOf(value.toString()));
        return list;
    }
}
