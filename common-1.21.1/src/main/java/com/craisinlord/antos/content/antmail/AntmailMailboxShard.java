package com.craisinlord.antos.content.antmail;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class AntmailMailboxShard extends SavedData {
    private static final String PREFIX = "antos_mailbox_";
    static final SavedData.Factory<AntmailMailboxShard> FACTORY = new SavedData.Factory<>(AntmailMailboxShard::new, AntmailMailboxShard::load, null);
    private AntmailMailbox mailbox;
    private boolean persisted;

    static String key(AntmailAddress address) {
        return PREFIX + address.username().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    public static AntmailMailboxShard load(CompoundTag tag, HolderLookup.Provider registries) {
        AntmailMailboxShard shard = new AntmailMailboxShard();
        shard.persisted = true;
        if (tag.contains("Mailbox", 10)) {
            try {
                shard.mailbox = AntmailMailbox.fromTag(tag.getCompound("Mailbox"));
            } catch (RuntimeException ignored) {
            }
        }
        return shard;
    }

    boolean hasMailbox() {
        return mailbox != null;
    }

    boolean persisted() {
        return persisted;
    }

    AntmailMailbox mailbox(AntmailAddress address) {
        if (mailbox == null) mailbox = new AntmailMailbox(address);
        return mailbox;
    }

    void setMailbox(AntmailMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (mailbox != null) tag.put("Mailbox", mailbox.toTag());
        return tag;
    }

    @Override
    public void save(java.io.File file, HolderLookup.Provider registries) {
        if (!isDirty()) return;
        if (com.craisinlord.antos.content.computer.SavedDataFiles.write(this, file, registries)) persisted = true;
    }
}
