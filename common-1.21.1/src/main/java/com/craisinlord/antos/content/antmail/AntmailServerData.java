package com.craisinlord.antos.content.antmail;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntmailServerData extends SavedData {
    private static final String ID = "antos_antmail";
    private final AntmailMailboxStore mailboxes = new AntmailMailboxStore();
    private final List<PendingMessage> pending = new ArrayList<>();
    private final Map<AntmailAddress, java.util.Set<ResourceLocation>> triggered = new LinkedHashMap<>();
    private final Map<AntmailAddress, Long> mailboxVersions = new LinkedHashMap<>();
    private long randomMailDay = Long.MIN_VALUE;
    private int retryTicks;

    public static AntmailServerData create() {
        return new AntmailServerData();
    }

    public static AntmailServerData load(CompoundTag tag, HolderLookup.Provider registries) {
        AntmailServerData data = new AntmailServerData();
        AntmailServerData loaded = data;
        AntmailMailboxStore restored = AntmailMailboxStore.fromTag(tag.getCompound("Mailboxes"));
        for (AntmailMailbox mailbox : restored.mailboxes()) {
            loaded.mailboxes.put(mailbox);
        }
        ListTag pendingTags = tag.getList("Pending", 10);
        for (int i = 0; i < pendingTags.size(); i++) {
            try {
                CompoundTag pendingTag = pendingTags.getCompound(i);
                loaded.pending.add(new PendingMessage(AntmailAddress.parse(pendingTag.getString("Recipient")), AntmailMessage.fromTag(pendingTag.getCompound("Message"))));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag triggeredTags = tag.getList("Triggered", 10);
        for (int i = 0; i < triggeredTags.size(); i++) {
            CompoundTag triggeredTag = triggeredTags.getCompound(i);
            try {
                AntmailAddress address = AntmailAddress.parse(triggeredTag.getString("Address"));
                java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
                ListTag idsTag = triggeredTag.getList("Ids", 8);
                for (int j = 0; j < idsTag.size(); j++) ids.add(ResourceLocation.parse(idsTag.getString(j)));
                data.triggered.put(address, ids);
            } catch (RuntimeException ignored) {
            }
        }
        data.randomMailDay = tag.contains("RandomMailDay") ? tag.getLong("RandomMailDay") : Long.MIN_VALUE;
        ListTag versionTags = tag.getList("MailboxVersions", 10);
        for (int i = 0; i < versionTags.size(); i++) {
            try {
                CompoundTag versionTag = versionTags.getCompound(i);
                data.mailboxVersions.put(AntmailAddress.parse(versionTag.getString("Address")), Math.max(1L, versionTag.getLong("Version")));
            } catch (RuntimeException ignored) {
            }
        }
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Mailboxes", mailboxes.toTag());
        ListTag pendingTags = new ListTag();
        for (PendingMessage message : pending) {
            CompoundTag pendingTag = new CompoundTag();
            pendingTag.putString("Recipient", message.recipient().fullAddress());
            pendingTag.put("Message", message.message().toTag());
            pendingTags.add(pendingTag);
        }
        tag.put("Pending", pendingTags);
        ListTag triggeredTags = new ListTag();
        for (Map.Entry<AntmailAddress, java.util.Set<ResourceLocation>> entry : triggered.entrySet()) {
            CompoundTag triggeredTag = new CompoundTag();
            triggeredTag.putString("Address", entry.getKey().fullAddress());
            ListTag idsTag = new ListTag();
            for (ResourceLocation id : entry.getValue()) idsTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            triggeredTag.put("Ids", idsTag);
            triggeredTags.add(triggeredTag);
        }
        tag.put("Triggered", triggeredTags);
        tag.putLong("RandomMailDay", randomMailDay);
        ListTag versionTags = new ListTag();
        for (Map.Entry<AntmailAddress, Long> entry : mailboxVersions.entrySet()) {
            CompoundTag versionTag = new CompoundTag();
            versionTag.putString("Address", entry.getKey().fullAddress());
            versionTag.putLong("Version", entry.getValue());
            versionTags.add(versionTag);
        }
        tag.put("MailboxVersions", versionTags);
        return tag;
    }

    private static AntmailServerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AntmailServerData::create, AntmailServerData::load, null), ID);
    }

    public static AntmailServerData access(MinecraftServer server) {
        return get(server);
    }

    public synchronized UUID accountIdForAddress(MinecraftServer server, AntmailAddress address) {
        var account = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(server).accountByUsername(address.username());
        return account == null ? null : account.accountId();
    }

    public synchronized List<AntmailAddress> profileAddresses(MinecraftServer server) {
        return com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(
                server).accounts().stream().map(account -> AntmailAddress.ofUsername(account.username())).toList();
    }

    public synchronized AntmailAddress addressFor(ServerPlayer player) {
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        return account == null ? null : AntmailAddress.ofUsername(account.username());
    }

    public synchronized boolean claimTrigger(AntmailAddress address, ResourceLocation triggerId) {
        java.util.Set<ResourceLocation> ids = triggered.computeIfAbsent(address, ignored -> new java.util.LinkedHashSet<>());
        if (!ids.add(triggerId)) return false;
        setDirty();
        return true;
    }

    public synchronized boolean beginRandomMailDay(long day) {
        if (randomMailDay == day) return false;
        randomMailDay = day;
        setDirty();
        return true;
    }

    public synchronized AntmailMailbox mailbox(AntmailAddress address) {
        return mailboxes.get(address);
    }

    public synchronized AntmailMailbox mailboxOrCreate(AntmailAddress address) {
        AntmailMailbox mailbox = mailboxes.getOrCreate(address);
        setDirty();
        return mailbox;
    }

    public synchronized long mailboxVersion(AntmailAddress address) {
        return mailboxVersions.getOrDefault(address, 1L);
    }

    public synchronized void touchMailbox(AntmailAddress address) {
        if (address == null) return;
        mailboxVersions.put(address, mailboxVersion(address) + 1L);
        setDirty();
    }

    public synchronized void drainAvailable(MinecraftServer server) {
        if (pending.isEmpty() || ++retryTicks < 20) return;
        retryTicks = 0;
        pending.stream().map(PendingMessage::recipient).distinct().toList().forEach(address -> drain(server, address));
    }

    public synchronized AntmailDeliveryResult deliver(MinecraftServer server, AntmailMessage message) {
        if (message == null) return AntmailDeliveryResult.failed(AntmailDeliveryResult.Status.MESSAGE_INVALID, null, "missing_message");
        if (com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(server)
                .accountByUsername(message.recipient().username()) == null) {
            return failedAndRecorded(message, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND, "address_not_found");
        }
        AntmailMailbox destination = mailboxes.getOrCreate(message.recipient());
        if (destination.inbox().size() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return failedAndRecorded(message, AntmailDeliveryResult.Status.MAILBOX_FULL, "mailbox_full");
        message.setDeliveryStatus("DELIVERED");
        destination.addIncoming(message);
        mailboxes.getOrCreate(message.sender()).addSent(message);
        touchMailbox(message.recipient());
        touchMailbox(message.sender());
        setDirty();
        return AntmailDeliveryResult.delivered(message);
    }

    private AntmailDeliveryResult failedAndRecorded(AntmailMessage message, AntmailDeliveryResult.Status status, String detail) {
        message.setDeliveryStatus("FAILED // " + detail.toUpperCase());
        mailboxes.getOrCreate(message.sender()).addSent(message);
        touchMailbox(message.sender());
        setDirty();
        return AntmailDeliveryResult.failed(status, message, detail);
    }

    public synchronized void drain(MinecraftServer server, AntmailAddress address) {
        AntmailMailbox mailbox = mailboxes.getOrCreate(address);
        final boolean[] delivered = {false};
        Set<AntmailAddress> deliveredSenders = new LinkedHashSet<>();
        pending.removeIf(pendingMessage -> {
            if (!pendingMessage.recipient().equals(address) || mailbox.inbox().size() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return false;
            if (!mailbox.addIncoming(pendingMessage.message())) return false;
            pendingMessage.message().setDeliveryStatus("DELIVERED");
            delivered[0] = true;
            deliveredSenders.add(pendingMessage.message().sender());
            return true;
        });
        if (delivered[0]) {
            touchMailbox(address);
            for (AntmailAddress sender : deliveredSenders) touchMailbox(sender);
            setDirty();
        }
    }

    public synchronized AntmailDeliveryResult retry(MinecraftServer server, AntmailAddress sender, UUID messageId) {
        AntmailMessage message = mailboxes.getOrCreate(sender).findSent(messageId);
        if (message == null) return null;
        pending.removeIf(item -> item.message().id().equals(messageId));
        return deliver(server, message);
    }

    private record PendingMessage(AntmailAddress recipient, AntmailMessage message) {
    }
}


