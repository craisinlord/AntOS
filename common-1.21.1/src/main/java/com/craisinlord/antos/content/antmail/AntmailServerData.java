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
    private final Map<AntmailAddress, java.util.Set<ResourceLocation>> triggered = new LinkedHashMap<>();
    private final Map<AntmailAddress, Long> mailboxVersions = new LinkedHashMap<>();
    private final Map<AntmailAddress, AntmailProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, UUID> linkedAccounts = new LinkedHashMap<>();
    private final List<ScheduledMail> scheduled = new ArrayList<>();
    private final Map<AntmailAddress, Set<ResourceLocation>> scheduledIds = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<ResourceLocation>> poolSeen = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<ResourceLocation>> readDefinitions = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<ResourceLocation>> completedTasks = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<ResourceLocation>> packageClaims = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<ResourceLocation>> pendingPackages = new LinkedHashMap<>();
    private long randomMailDay = Long.MIN_VALUE;

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
        // Older saves kept a retry queue for undeliverable mail; fold anything left in it into the inboxes once.
        ListTag pendingTags = tag.getList("Pending", 10);
        for (int i = 0; i < pendingTags.size(); i++) {
            try {
                CompoundTag pendingTag = pendingTags.getCompound(i);
                AntmailMessage message = AntmailMessage.fromTag(pendingTag.getCompound("Message"));
                if (loaded.mailboxes.getOrCreate(AntmailAddress.parse(pendingTag.getString("Recipient"))).addIncoming(message)) {
                    message.setDeliveryStatus("DELIVERED");
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (!pendingTags.isEmpty()) loaded.setDirty();
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
        ListTag linkTags = tag.getList("LinkedAccounts", 10);
        for (int i = 0; i < linkTags.size(); i++) {
            try {
                CompoundTag link = linkTags.getCompound(i);
                data.linkedAccounts.put(link.getUUID("Player"), link.getUUID("Account"));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag scheduledTags = tag.getList("Scheduled", 10);
        for (int i = 0; i < scheduledTags.size(); i++) {
            try {
                CompoundTag entry = scheduledTags.getCompound(i);
                ScheduledMail scheduledMail = new ScheduledMail(AntmailAddress.parse(entry.getString("Address")),
                        ResourceLocation.parse(entry.getString("Id")), entry.getLong("DueDay"));
                data.scheduled.add(scheduledMail);
                addId(data.scheduledIds, scheduledMail.address(), scheduledMail.definitionId());
            } catch (RuntimeException ignored) {
            }
        }
        loadIdSets(tag.getList("PoolSeen", 10), data.poolSeen);
        loadIdSets(tag.getList("ReadDefinitions", 10), data.readDefinitions);
        loadIdSets(tag.getList("CompletedTasks", 10), data.completedTasks);
        loadIdSets(tag.getList("PackageClaims", 10), data.packageClaims);
        loadIdSets(tag.getList("PendingPackages", 10), data.pendingPackages);
        ListTag profileTags = tag.getList("AntmailProfiles", 10);
        for (int i = 0; i < profileTags.size(); i++) {
            CompoundTag profileTag = profileTags.getCompound(i);
            try {
                AntmailAddress address = AntmailAddress.parse(profileTag.getString("Address"));
                AntmailProfile profile = new AntmailProfile(profileTag.getString("DisplayName"), profileTag.getString("AvatarItem"));
                if (!profile.displayName().isBlank() || !profile.avatarItem().isBlank()) data.profiles.put(address, profile);
            } catch (RuntimeException ignored) {
            }
        }
        return loaded;
    }

    private static void loadIdSets(ListTag tags, Map<AntmailAddress, Set<ResourceLocation>> target) {
        for (int i = 0; i < tags.size(); i++) {
            try {
                CompoundTag row = tags.getCompound(i);
                Set<ResourceLocation> ids = new LinkedHashSet<>();
                ListTag idsTag = row.getList("Ids", 8);
                for (int j = 0; j < idsTag.size(); j++) ids.add(ResourceLocation.parse(idsTag.getString(j)));
                target.put(AntmailAddress.parse(row.getString("Address")), ids);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static ListTag saveIdSets(Map<AntmailAddress, Set<ResourceLocation>> source) {
        ListTag tags = new ListTag();
        for (Map.Entry<AntmailAddress, Set<ResourceLocation>> entry : source.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("Address", entry.getKey().fullAddress());
            ListTag idsTag = new ListTag();
            for (ResourceLocation id : entry.getValue()) idsTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            row.put("Ids", idsTag);
            tags.add(row);
        }
        return tags;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Mailboxes", mailboxes.toTag());
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
        ListTag linkTags = new ListTag();
        for (Map.Entry<UUID, UUID> entry : linkedAccounts.entrySet()) {
            CompoundTag link = new CompoundTag();
            link.putUUID("Player", entry.getKey());
            link.putUUID("Account", entry.getValue());
            linkTags.add(link);
        }
        tag.put("LinkedAccounts", linkTags);
        ListTag scheduledTags = new ListTag();
        for (ScheduledMail entry : scheduled) {
            CompoundTag row = new CompoundTag();
            row.putString("Address", entry.address().fullAddress());
            row.putString("Id", entry.definitionId().toString());
            row.putLong("DueDay", entry.dueDay());
            scheduledTags.add(row);
        }
        tag.put("Scheduled", scheduledTags);
        tag.put("PoolSeen", saveIdSets(poolSeen));
        tag.put("ReadDefinitions", saveIdSets(readDefinitions));
        tag.put("CompletedTasks", saveIdSets(completedTasks));
        tag.put("PackageClaims", saveIdSets(packageClaims));
        tag.put("PendingPackages", saveIdSets(pendingPackages));
        ListTag profileTags = new ListTag();
        for (Map.Entry<AntmailAddress, AntmailProfile> entry : profiles.entrySet()) {
            CompoundTag profileTag = new CompoundTag();
            profileTag.putString("Address", entry.getKey().fullAddress());
            profileTag.putString("DisplayName", entry.getValue().displayName());
            profileTag.putString("AvatarItem", entry.getValue().avatarItem());
            profileTags.add(profileTag);
        }
        tag.put("AntmailProfiles", profileTags);
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

    public synchronized AntmailAddress deliveryAddressFor(ServerPlayer player) {
        var account = deliveryAccount(player);
        return account == null ? null : AntmailAddress.ofUsername(account.username());
    }

    public synchronized com.craisinlord.antos.content.computer.ComputerWorkspaceData.AccountInfo deliveryAccount(ServerPlayer player) {
        if (player == null) return null;
        var session = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        if (session != null) return session;
        var workspaces = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.server);
        UUID linked = linkedAccounts.get(player.getUUID());
        var account = linked == null ? null : workspaces.account(linked);
        return account != null ? account : workspaces.faceIdAccount(player.getUUID());
    }

    public synchronized void linkAccount(UUID playerId, UUID accountId) {
        if (playerId == null || accountId == null || accountId.equals(linkedAccounts.get(playerId))) return;
        linkedAccounts.put(playerId, accountId);
        setDirty();
    }

    public Map<AntmailAddress, ServerPlayer> onlinePlayersByAddress(MinecraftServer server) {
        Map<AntmailAddress, ServerPlayer> players = new java.util.HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AntmailAddress address = deliveryAddressFor(player);
            if (address != null) players.putIfAbsent(address, player);
        }
        return players;
    }

    public List<ServerPlayer> onlinePlayersFor(MinecraftServer server, AntmailAddress address) {
        List<ServerPlayer> players = new ArrayList<>();
        if (address == null) return players;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (address.equals(deliveryAddressFor(player))) players.add(player);
        }
        return players;
    }

    public synchronized boolean isClaimed(AntmailAddress address, ResourceLocation triggerId) {
        return triggered.getOrDefault(address, Set.of()).contains(triggerId);
    }

    public synchronized boolean isScheduled(AntmailAddress address, ResourceLocation definitionId) {
        return scheduledIds.getOrDefault(address, Set.of()).contains(definitionId);
    }

    public synchronized void schedule(AntmailAddress address, ResourceLocation definitionId, long dueDay) {
        scheduled.add(new ScheduledMail(address, definitionId, dueDay));
        addId(scheduledIds, address, definitionId);
        setDirty();
    }

    public synchronized List<ScheduledMail> scheduled() {
        return List.copyOf(scheduled);
    }

    public synchronized void unschedule(ScheduledMail entry) {
        if (!scheduled.remove(entry)) return;
        boolean stillScheduled = false;
        for (ScheduledMail other : scheduled) {
            if (other.address().equals(entry.address()) && other.definitionId().equals(entry.definitionId())) {
                stillScheduled = true;
                break;
            }
        }
        if (!stillScheduled) {
            Set<ResourceLocation> ids = scheduledIds.get(entry.address());
            if (ids != null) {
                ids.remove(entry.definitionId());
                if (ids.isEmpty()) scheduledIds.remove(entry.address());
            }
        }
        setDirty();
    }

    private static boolean addId(Map<AntmailAddress, Set<ResourceLocation>> map, AntmailAddress address, ResourceLocation id) {
        return map.computeIfAbsent(address, ignored -> new LinkedHashSet<>()).add(id);
    }

    public synchronized Set<ResourceLocation> poolSeen(AntmailAddress address) {
        return Set.copyOf(poolSeen.getOrDefault(address, Set.of()));
    }

    public synchronized void markPoolSeen(AntmailAddress address, ResourceLocation id) {
        if (addId(poolSeen, address, id)) setDirty();
    }

    public synchronized void resetPoolSeen(AntmailAddress address, java.util.Collection<ResourceLocation> ids) {
        Set<ResourceLocation> seen = poolSeen.get(address);
        if (seen != null && seen.removeAll(ids)) setDirty();
    }

    public synchronized boolean hasReadDefinition(AntmailAddress address, ResourceLocation id) {
        return readDefinitions.getOrDefault(address, Set.of()).contains(id);
    }

    public synchronized void markDefinitionRead(AntmailAddress address, ResourceLocation id) {
        if (addId(readDefinitions, address, id)) setDirty();
    }

    public synchronized boolean hasCompletedTask(AntmailAddress address, ResourceLocation id) {
        return completedTasks.getOrDefault(address, Set.of()).contains(id);
    }

    public synchronized void markTaskCompleted(AntmailAddress address, ResourceLocation id) {
        if (addId(completedTasks, address, id)) setDirty();
    }

    public synchronized boolean claimPackage(AntmailAddress address, ResourceLocation id) {
        if (!addId(packageClaims, address, id)) return false;
        setDirty();
        return true;
    }

    public synchronized void addPendingPackage(AntmailAddress address, ResourceLocation id) {
        if (addId(pendingPackages, address, id)) setDirty();
    }

    public synchronized List<ResourceLocation> takePendingPackages(AntmailAddress address) {
        Set<ResourceLocation> pendingIds = pendingPackages.remove(address);
        if (pendingIds == null || pendingIds.isEmpty()) return List.of();
        setDirty();
        return List.copyOf(pendingIds);
    }

    public synchronized void purgeReadSelfDeleting(AntmailAddress address) {
        AntmailMailbox mailbox = mailboxes.get(address);
        if (mailbox == null) return;
        boolean changed = false;
        for (AntmailMessage message : mailbox.inbox()) {
            if (message.read() && message.deleteAfterRead()) changed |= mailbox.removeInbox(message.id());
        }
        if (changed) touchMailbox(address);
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
        boolean created = !mailboxes.contains(address);
        AntmailMailbox mailbox = mailboxes.getOrCreate(address);
        if (created) setDirty();
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

    public synchronized AntmailProfile profile(AntmailAddress address) {
        return profiles.getOrDefault(address, new AntmailProfile("", ""));
    }

    public synchronized void setProfile(AntmailAddress address, String displayName, String avatarItem, MinecraftServer server) {
        AntmailProfile profile = new AntmailProfile(displayName, avatarItem);
        if (profile.displayName().isBlank() && profile.avatarItem().isBlank()) profiles.remove(address);
        else profiles.put(address, profile);
        for (AntmailAddress accountAddress : profileAddresses(server)) touchMailbox(accountAddress);
        setDirty();
    }

    public synchronized void appendProfiles(CompoundTag snapshot, AntmailAddress ownAddress) {
        Set<AntmailAddress> addresses = new LinkedHashSet<>();
        if (ownAddress != null) addresses.add(ownAddress);
        collectProfileAddress(snapshot, addresses);
        collectProfileAddresses(snapshot.getList("Inbox", 10), addresses);
        collectProfileAddresses(snapshot.getList("Sent", 10), addresses);
        collectProfileAddresses(snapshot.getList("Archive", 10), addresses);
        collectProfileAddresses(snapshot.getList("Trash", 10), addresses);
        ListTag profileTags = new ListTag();
        for (AntmailAddress address : addresses) {
            AntmailProfile profile = profiles.get(address);
            if (profile == null) continue;
            CompoundTag profileTag = new CompoundTag();
            profileTag.putString("Address", address.fullAddress());
            profileTag.putString("DisplayName", profile.displayName());
            profileTag.putString("AvatarItem", profile.avatarItem());
            profileTags.add(profileTag);
        }
        snapshot.put("Profiles", profileTags);
    }

    private static void collectProfileAddresses(ListTag messages, Set<AntmailAddress> addresses) {
        for (int i = 0; i < messages.size(); i++) {
            CompoundTag message = messages.getCompound(i);
            try {
                if (message.contains("From")) addresses.add(AntmailAddress.parse(message.getString("From")));
                if (message.contains("To")) addresses.add(AntmailAddress.parse(message.getString("To")));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void collectProfileAddress(CompoundTag message, Set<AntmailAddress> addresses) {
        try {
            if (message.contains("From")) addresses.add(AntmailAddress.parse(message.getString("From")));
            if (message.contains("To")) addresses.add(AntmailAddress.parse(message.getString("To")));
        } catch (RuntimeException ignored) {
        }
    }

    public synchronized AntmailDeliveryResult deliver(MinecraftServer server, AntmailMessage message) {
        if (message == null) return AntmailDeliveryResult.failed(AntmailDeliveryResult.Status.MESSAGE_INVALID, null, "missing_message");
        if (com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(server)
                .accountByUsername(message.recipient().username()) == null) {
            return failedAndRecorded(message, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND, "address_not_found");
        }
        AntmailMailbox destination = mailboxes.getOrCreate(message.recipient());
        if (destination.inboxSize() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return failedAndRecorded(message, AntmailDeliveryResult.Status.MAILBOX_FULL, "mailbox_full");
        message.setDeliveryStatus("DELIVERED");
        AntmailMessage incoming = message.copy();
        destination.addIncoming(incoming);
        mailboxes.getOrCreate(message.sender()).addSent(message);
        touchMailbox(message.recipient());
        touchMailbox(message.sender());
        setDirty();
        AntmailNotifier.notify(server, incoming);
        return AntmailDeliveryResult.delivered(message);
    }

    private AntmailDeliveryResult failedAndRecorded(AntmailMessage message, AntmailDeliveryResult.Status status, String detail) {
        message.setDeliveryStatus("FAILED // " + detail.toUpperCase());
        mailboxes.getOrCreate(message.sender()).addSent(message);
        touchMailbox(message.sender());
        setDirty();
        return AntmailDeliveryResult.failed(status, message, detail);
    }

    public synchronized AntmailDeliveryResult retry(MinecraftServer server, AntmailAddress sender, UUID messageId) {
        AntmailMessage message = mailboxes.getOrCreate(sender).findSent(messageId);
        if (message == null) return null;
        return deliver(server, message);
    }

    public record ScheduledMail(AntmailAddress address, ResourceLocation definitionId, long dueDay) {
    }
}


