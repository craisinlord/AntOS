package com.craisinlord.antos.content.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ComputerAccountShard extends SavedData {
    static final String SNAPSHOT_TAG = "Snapshot";
    static final String DISKS_TAG = "Disks";
    static final String COLLECTED_DISKS_TAG = "CollectedDisks";
    static final String DISK_STATE_TAG = "DiskState";
    static final String DISK_REWARDS_TAG = "DiskRewards";
    private static final Map<String, net.minecraft.world.level.saveddata.SavedDataType<ComputerAccountShard>> TYPES = new ConcurrentHashMap<>();

    static net.minecraft.world.level.saveddata.SavedDataType<ComputerAccountShard> type(String id) {
        return TYPES.computeIfAbsent(id, key -> com.craisinlord.antos.content.AntOSSavedDataType.create(key, ComputerAccountShard::new, ComputerAccountShard::decode, (data, tag) -> data.save(tag, null)));
    }
    final Set<Identifier> profileDisks = new LinkedHashSet<>();
    final Set<Identifier> collectedDisks = new LinkedHashSet<>();
    final Map<Identifier, String> diskRewardReceipts = new LinkedHashMap<>();
    CompoundTag snapshot = new CompoundTag();
    DiskProfileState diskState = new DiskProfileState();
    private CompoundTag savedDiskState = diskState.save();
    private ComputerWorkspaceData owner;
    private long checkedEpoch = -1;
    private volatile boolean persisted;
    private volatile CompoundTag encoded;
    private volatile boolean awaitingPersist;

    static String dataId(UUID accountId) {
        return "antos_account_" + accountId;
    }

    void bind(ComputerWorkspaceData owner) {
        this.owner = owner;
    }

    boolean persisted() {
        return persisted;
    }

    void awaitPersist() {
        awaitingPersist = true;
    }

    boolean confirmPersisted(net.minecraft.server.MinecraftServer server, String id) {
        CompoundTag expected = encoded;
        if (persisted || expected == null || server == null) return persisted;
        if (com.craisinlord.antos.content.AntOSSavedDataType.isWritten(server, id, expected)) {
            persisted = true;
            encoded = null;
        }
        return persisted;
    }

    private Object lock() {
        return owner == null ? this : owner;
    }

    private static ComputerAccountShard decode(CompoundTag tag) {
        ComputerAccountShard shard = load(tag, null);
        shard.persisted = true;
        return shard;
    }

    static ComputerAccountShard load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerAccountShard shard = new ComputerAccountShard();
        shard.snapshot = tag.getCompoundOrEmpty(SNAPSHOT_TAG).copy();
        ListTag diskTags = tag.getListOrEmpty(DISKS_TAG);
        for (int index = 0; index < diskTags.size(); index++) {
            try { shard.profileDisks.add(Identifier.parse(diskTags.getStringOr(index, ""))); }
            catch (RuntimeException ignored) { }
        }
        shard.collectedDisks.addAll(shard.profileDisks);
        ListTag collectedTags = tag.getListOrEmpty(COLLECTED_DISKS_TAG);
        for (int index = 0; index < collectedTags.size(); index++) {
            try { shard.collectedDisks.add(Identifier.parse(collectedTags.getStringOr(index, ""))); }
            catch (RuntimeException ignored) { }
        }
        shard.diskState = DiskProfileState.load(tag.getCompoundOrEmpty(DISK_STATE_TAG));
        if (!tag.contains(DISK_STATE_TAG)) shard.diskState.seed(List.copyOf(shard.collectedDisks));
        shard.savedDiskState = shard.diskState.save();
        ListTag rewards = tag.getListOrEmpty(DISK_REWARDS_TAG);
        for (int index = 0; index < rewards.size(); index++) {
            CompoundTag reward = rewards.getCompoundOrEmpty(index);
            try { shard.diskRewardReceipts.put(Identifier.parse(reward.getStringOr("Disk", "")), reward.getStringOr("Receipt", "")); }
            catch (RuntimeException ignored) { }
        }
        return shard;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty && owner != null) owner.touch();
    }

    @Override
    public boolean isDirty() {
        if (super.isDirty()) return true;
        synchronized (lock()) {
            long epoch = owner == null ? checkedEpoch + 1 : owner.mutationEpoch();
            if (epoch == checkedEpoch) return false;
            checkedEpoch = epoch;
            if (diskState.save().equals(savedDiskState)) return false;
            super.setDirty(true);
            return true;
        }
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        synchronized (lock()) {
            write(tag);
            savedDiskState = tag.getCompoundOrEmpty(DISK_STATE_TAG).copy();
            if (awaitingPersist && !persisted) encoded = tag.copy();
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

    private static ListTag strings(Set<Identifier> values) {
        ListTag list = new ListTag();
        for (Identifier value : values) list.add(StringTag.valueOf(value.toString()));
        return list;
    }
}
