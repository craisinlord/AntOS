package com.craisinlord.antos.content.computer;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

public final class DiskCollectionTest {
    public static void main(String[] args) throws Exception {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var account = data.createAccount("collector", "x").account();
        var other = data.createAccount("other_collector", "x").account();
        ResourceLocation disk = ResourceLocation.parse("examplemod:field_notes");
        check(!data.addProfileDisk(UUID.randomUUID(), disk), "unknown accounts cannot install disks");
        check(data.addProfileDisk(account.accountId(), disk), "first insert installs the disk");
        check(!data.addProfileDisk(account.accountId(), disk), "duplicate insert is unchanged");
        check(data.collectedDisks(account.accountId()).contains(disk), "installation records collection history");
        check(data.collectedDisks(other.accountId()).isEmpty(), "collections belong to one profile");
        CompoundTag installedSave = data.save(new CompoundTag(), null);
        check(data.removeProfileDisk(account.accountId(), disk), "installed disk can be ejected");
        check(!data.profileDisks(account.accountId()).contains(disk), "ejection removes installation");
        check(data.collectedDisks(account.accountId()).contains(disk), "ejection preserves collection");
        ComputerWorkspaceData restored = load(data.save(new CompoundTag(), null));
        check(restored.profileDisks(account.accountId()).isEmpty(), "ejected disk stays ejected after reload");
        check(restored.collectedDisks(account.accountId()).contains(disk), "collection survives reload");
        check(restored.addProfileDisk(account.accountId(), disk), "ejected disk can be reinserted");
        check(restored.collectedDisks(account.accountId()).size() == 1, "reinsert does not duplicate history");
        check(!restored.claimDiskRewards(UUID.randomUUID(), disk), "unknown profile cannot claim disk rewards");
        check(!restored.claimDiskRewards(other.accountId(), disk), "uncollected disk cannot pay rewards");
        check(restored.claimDiskRewards(account.accountId(), disk), "first reward claim succeeds");
        check(!restored.claimDiskRewards(account.accountId(), disk), "duplicate reward claim is rejected");
        restored.recordDiskRewardReceipt(account.accountId(), disk, "[{\"type\":\"antcoins\",\"amount\":100}]");
        restored.removeProfileDisk(account.accountId(), disk);
        var paid = load(restored.save(new CompoundTag(), null));
        check(!paid.claimDiskRewards(account.accountId(), disk), "ejection and save reload do not reset reward claims");
        check(paid.diskRewardReceipts(account.accountId()).get(disk).contains("100"), "delivered reward receipt survives reload");
        paid.addProfileDisk(other.accountId(), disk);
        check(paid.claimDiskRewards(other.accountId(), disk), "another profile claims the same disk independently");
        ListTag accounts = installedSave.getList("Accounts", 10);
        for (int index = 0; index < accounts.size(); index++) {
            accounts.getCompound(index).remove("CollectedDisks");
            accounts.getCompound(index).remove("DiskState");
        }
        ComputerWorkspaceData migrated = load(installedSave);
        check(migrated.profileDisks(account.accountId()).contains(disk), "migration preserves installed access");
        check(migrated.collectedDisks(account.accountId()).contains(disk), "old installed disks seed collection history");
        check(migrated.activatedDisks(account.accountId()).contains(disk), "legacy disk access remains activated when encryption is added");
        check(migrated.claimDiskRewards(account.accountId(), disk), "legacy installed disks can claim newly configured rewards once");
        check(migrated.collectedDisks(other.accountId()).isEmpty(), "migration does not leak another profile's disks");
        System.out.println("Disk collection tests passed");
    }

    private static ComputerWorkspaceData load(CompoundTag tag) throws Exception {
        Method loader = ComputerWorkspaceData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        loader.setAccessible(true);
        return (ComputerWorkspaceData) loader.invoke(null, tag, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
