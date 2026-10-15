package com.craisinlord.antos.content.antmail;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class AntmailMailboxShard extends SavedData {
    private static final String PREFIX = "antos_mailbox_";
    private static final Map<String, net.minecraft.world.level.saveddata.SavedDataType<AntmailMailboxShard>> TYPES = new ConcurrentHashMap<>();
    static net.minecraft.world.level.saveddata.SavedDataType<AntmailMailboxShard> type(String id) {
        return TYPES.computeIfAbsent(id, key -> com.craisinlord.antos.content.AntOSSavedDataType.create(key, AntmailMailboxShard::new, tag -> load(tag, null), (data, tag) -> data.save(tag, null)));
    }
    private AntmailMailbox mailbox;
    private volatile boolean persisted;
    private volatile CompoundTag encoded;
    private volatile boolean awaitingPersist;

    static String key(AntmailAddress address) {
        return PREFIX + address.username().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    public static AntmailMailboxShard load(CompoundTag tag, HolderLookup.Provider registries) {
        AntmailMailboxShard shard = new AntmailMailboxShard();
        if (tag.contains("Mailbox")) {
            try {
                shard.mailbox = AntmailMailbox.fromTag(tag.getCompoundOrEmpty("Mailbox"));
            } catch (RuntimeException ignored) {
            }
        }
        shard.persisted = shard.mailbox != null;
        return shard;
    }

    boolean hasMailbox() {
        return mailbox != null;
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

    AntmailMailbox mailbox(AntmailAddress address) {
        if (mailbox == null) mailbox = new AntmailMailbox(address);
        return mailbox;
    }

    void setMailbox(AntmailMailbox mailbox) {
        this.mailbox = mailbox;
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (mailbox != null) {
            tag.put("Mailbox", mailbox.toTag());
            if (awaitingPersist && !persisted) encoded = tag.copy();
        }
        return tag;
    }
}
