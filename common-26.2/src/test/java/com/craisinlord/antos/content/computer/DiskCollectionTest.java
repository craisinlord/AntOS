package com.craisinlord.antos.content.computer;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class DiskCollectionTest {
    public static void main(String[] args) throws Exception {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var account = data.createAccount("collector", "x").account();
        var other = data.createAccount("other_collector", "x").account();
        Identifier disk = Identifier.parse("examplemod:field_notes");
        check(!data.addProfileDisk(UUID.randomUUID(), disk), "unknown accounts cannot install disks");
        check(data.addProfileDisk(account.accountId(), disk), "first insert installs the disk");
        check(!data.addProfileDisk(account.accountId(), disk), "duplicate insert is unchanged");
        check(data.collectedDisks(account.accountId()).contains(disk), "installation records collection history");
        check(data.collectedDisks(other.accountId()).isEmpty(), "collections belong to one profile");
        CompoundTag installedSave = (CompoundTag) encode(type(), data);
        Map<UUID, CompoundTag> installedShards = new java.util.HashMap<>();
        shards(data).forEach((id, shard) -> installedShards.put(id, shard.write(new CompoundTag()).copy()));
        check(data.removeProfileDisk(account.accountId(), disk), "installed disk can be ejected");
        check(!data.profileDisks(account.accountId()).contains(disk), "ejection removes installation");
        check(data.collectedDisks(account.accountId()).contains(disk), "ejection preserves collection");
        ComputerWorkspaceData restored = reload(data);
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
        var paid = reload(restored);
        check(!paid.claimDiskRewards(account.accountId(), disk), "ejection and save reload do not reset reward claims");
        check(paid.diskRewardReceipts(account.accountId()).get(disk).contains("100"), "delivered reward receipt survives reload");
        paid.addProfileDisk(other.accountId(), disk);
        check(paid.claimDiskRewards(other.accountId(), disk), "another profile claims the same disk independently");
        ListTag accounts = installedSave.getListOrEmpty("Accounts");
        for (int index = 0; index < accounts.size(); index++) {
            CompoundTag row = accounts.getCompoundOrEmpty(index);
            CompoundTag shard = installedShards.get(UUID.fromString(row.getStringOr("Id", "")));
            if (shard == null) continue;
            row.put("Disks", shard.getListOrEmpty("Disks"));
            row.put("DiskRewards", shard.getListOrEmpty("DiskRewards"));
        }
        ComputerWorkspaceData legacy = decode(type(), installedSave);
        CompoundTag resaved = (CompoundTag) encode(type(), legacy);
        check(resaved.getListOrEmpty("Accounts").getCompoundOrEmpty(0).getListOrEmpty("Disks").size() == 1, "unmigrated legacy disks survive another save");
        ComputerWorkspaceData migrated = decode(type(), resaved);
        legacyShards(migrated).forEach((id, tag) -> install(migrated, id, decode(ComputerAccountShard.type(ComputerAccountShard.dataId(id)), tag)));
        check(migrated.profileDisks(account.accountId()).contains(disk), "migration preserves installed access");
        check(migrated.collectedDisks(account.accountId()).contains(disk), "old installed disks seed collection history");
        check(migrated.activatedDisks(account.accountId()).contains(disk), "legacy disk access remains activated when encryption is added");
        check(migrated.claimDiskRewards(account.accountId(), disk), "legacy installed disks can claim newly configured rewards once");
        check(migrated.collectedDisks(other.accountId()).isEmpty(), "migration does not leak another profile's disks");
        System.out.println("Disk collection tests passed");
    }

    private static ComputerWorkspaceData reload(ComputerWorkspaceData data) throws Exception {
        ComputerWorkspaceData restored = decode(type(), encode(type(), data));
        for (var entry : shards(data).entrySet()) {
            var shardType = ComputerAccountShard.type(ComputerAccountShard.dataId(entry.getKey()));
            install(restored, entry.getKey(), decode(shardType, encode(shardType, entry.getValue())));
        }
        return restored;
    }

    private static void install(ComputerWorkspaceData data, UUID accountId, ComputerAccountShard shard) {
        shard.bind(data);
        shards(data).put(accountId, shard);
    }

    @SuppressWarnings("unchecked")
    private static SavedDataType<ComputerWorkspaceData> type() throws Exception {
        var field = ComputerWorkspaceData.class.getDeclaredField("FACTORY");
        field.setAccessible(true);
        return (SavedDataType<ComputerWorkspaceData>) field.get(null);
    }

    private static Map<UUID, ComputerAccountShard> shards(ComputerWorkspaceData data) {
        return field(data, "shards");
    }

    private static Map<UUID, CompoundTag> legacyShards(ComputerWorkspaceData data) {
        return field(data, "legacyShards");
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(ComputerWorkspaceData data, String name) {
        try {
            var field = ComputerWorkspaceData.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(data);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <T extends SavedData> Tag encode(SavedDataType<T> type, T value) {
        return type.codec().encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    private static <T extends SavedData> T decode(SavedDataType<T> type, Tag tag) {
        return type.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
