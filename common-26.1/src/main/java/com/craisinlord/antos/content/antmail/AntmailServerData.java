package com.craisinlord.antos.content.antmail;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
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
    private final Map<AntmailAddress, MailboxIndex> mailboxIndex = new LinkedHashMap<>();
    private final Map<AntmailAddress, AntmailMailboxShard> shards = new java.util.HashMap<>();
    private net.minecraft.world.level.storage.SavedDataStorage storage;
    private MinecraftServer server;
    private AntmailMailboxStore legacyMailboxes;
    private CompoundTag legacyTag;
    private final Set<AntmailAddress> migrated = new LinkedHashSet<>();
    private final Map<AntmailAddress, java.util.Set<Identifier>> triggered = new LinkedHashMap<>();
    private final Map<AntmailAddress, Long> mailboxVersions = new LinkedHashMap<>();
    private final Map<AntmailAddress, AntmailProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, UUID> linkedAccounts = new LinkedHashMap<>();
    private static final net.minecraft.world.level.saveddata.SavedDataType<AntmailServerData> FACTORY = com.craisinlord.antos.content.AntOSSavedDataType.create(ID, AntmailServerData::create, tag -> load(tag, null), (data, tag) -> data.save(tag, null));
    private final Map<ScheduledMail, Long> scheduled = new LinkedHashMap<>();
    private final Map<AntmailAddress, Map<Identifier, Set<ScheduledMail>>> scheduledIds = new java.util.HashMap<>();
    private final java.util.TreeMap<Long, ScheduledMail> readySchedule = new java.util.TreeMap<>();
    private final java.util.TreeMap<Long, Set<ScheduledMail>> futureSchedule = new java.util.TreeMap<>();
    private final Map<AntmailAddress, Set<ScheduledMail>> parkedFull = new java.util.HashMap<>();
    private final Map<AntmailAddress, Set<ScheduledMail>> parkedOffline = new java.util.HashMap<>();
    private final Set<AntmailAddress> onlineAddresses = new java.util.HashSet<>();
    private final java.util.ArrayDeque<AntmailAddress> randomMailQueue = new java.util.ArrayDeque<>();
    final Map<AntmailAddress, Set<Identifier>> advancementPending = new java.util.HashMap<>();
    Object advancementPendingKey;
    private long scheduleSequence;
    private long scheduleDay = Long.MIN_VALUE;
    private final Map<AntmailAddress, Set<Identifier>> poolSeen = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<Identifier>> readDefinitions = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<Identifier>> completedTasks = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<Identifier>> packageClaims = new LinkedHashMap<>();
    private final Map<AntmailAddress, Set<Identifier>> pendingPackages = new LinkedHashMap<>();
    private long randomMailDay = Long.MIN_VALUE;

    public static AntmailServerData create() {
        return new AntmailServerData();
    }

    public static AntmailServerData load(CompoundTag tag, HolderLookup.Provider registries) {
        AntmailServerData data = new AntmailServerData();
        AntmailServerData loaded = data;
        ListTag indexTags = tag.getListOrEmpty("MailboxIndex");
        for (int i = 0; i < indexTags.size(); i++) {
            try {
                CompoundTag row = indexTags.getCompoundOrEmpty(i);
                Set<AntmailAddress> correspondents = new LinkedHashSet<>();
                ListTag correspondentTags = row.getListOrEmpty("Correspondents");
                for (int j = 0; j < correspondentTags.size(); j++) {
                    try {
                        correspondents.add(AntmailAddress.parse(correspondentTags.getStringOr(j, "")));
                    } catch (RuntimeException ignored) {
                    }
                }
                data.mailboxIndex.put(AntmailAddress.parse(row.getStringOr("Address", "")),
                        new MailboxIndex(Math.max(0, row.getIntOr("InboxSize", 0)), row.getBooleanOr("Purgeable", false), correspondents));
            } catch (RuntimeException ignored) {
            }
        }
        if (tag.contains("Mailboxes") || tag.contains("Pending")) {
            CompoundTag legacy = new CompoundTag();
            if (tag.contains("Mailboxes")) legacy.put("Mailboxes", tag.getCompoundOrEmpty("Mailboxes"));
            if (tag.contains("Pending")) legacy.put("Pending", tag.getListOrEmpty("Pending"));
            AntmailMailboxStore restored = AntmailMailboxStore.fromTag(tag.getCompoundOrEmpty("Mailboxes"));
            // Older saves kept a retry queue for undeliverable mail; fold anything left in it into the inboxes once.
            ListTag pendingTags = tag.getListOrEmpty("Pending");
            for (int i = 0; i < pendingTags.size(); i++) {
                try {
                    CompoundTag pendingTag = pendingTags.getCompoundOrEmpty(i);
                    AntmailMessage message = AntmailMessage.fromTag(pendingTag.getCompoundOrEmpty("Message"));
                    if (restored.getOrCreate(AntmailAddress.parse(pendingTag.getStringOr("Recipient", ""))).addIncoming(message)) {
                        message.setDeliveryStatus("DELIVERED");
                    }
                } catch (RuntimeException ignored) {
                }
            }
            for (AntmailMailbox mailbox : restored.mailboxes()) data.mailboxIndex.put(mailbox.address(), MailboxIndex.of(mailbox));
            data.legacyMailboxes = restored;
            data.legacyTag = legacy;
            loaded.setDirty();
        }
        ListTag triggeredTags = tag.getListOrEmpty("Triggered");
        for (int i = 0; i < triggeredTags.size(); i++) {
            CompoundTag triggeredTag = triggeredTags.getCompoundOrEmpty(i);
            try {
                AntmailAddress address = AntmailAddress.parse(triggeredTag.getStringOr("Address", ""));
                java.util.Set<Identifier> ids = new java.util.LinkedHashSet<>();
                ListTag idsTag = triggeredTag.getListOrEmpty("Ids");
                for (int j = 0; j < idsTag.size(); j++) ids.add(Identifier.parse(idsTag.getStringOr(j, "")));
                data.triggered.put(address, ids);
            } catch (RuntimeException ignored) {
            }
        }
        data.randomMailDay = tag.contains("RandomMailDay") ? tag.getLongOr("RandomMailDay", 0L) : Long.MIN_VALUE;
        ListTag randomQueueTags = tag.getListOrEmpty("RandomMailQueue");
        for (int i = 0; i < randomQueueTags.size(); i++) {
            try {
                data.randomMailQueue.add(AntmailAddress.parse(randomQueueTags.getStringOr(i, "")));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag versionTags = tag.getListOrEmpty("MailboxVersions");
        for (int i = 0; i < versionTags.size(); i++) {
            try {
                CompoundTag versionTag = versionTags.getCompoundOrEmpty(i);
                data.mailboxVersions.put(AntmailAddress.parse(versionTag.getStringOr("Address", "")), Math.max(1L, versionTag.getLongOr("Version", 0L)));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag linkTags = tag.getListOrEmpty("LinkedAccounts");
        for (int i = 0; i < linkTags.size(); i++) {
            try {
                CompoundTag link = linkTags.getCompoundOrEmpty(i);
                UUID playerId = link.getIntArray("Player").map(UUIDUtil::uuidFromIntArray).orElse(null);
                UUID accountId = link.getIntArray("Account").map(UUIDUtil::uuidFromIntArray).orElse(null);
                if (playerId != null && accountId != null) data.linkedAccounts.put(playerId, accountId);
            } catch (RuntimeException ignored) {
            }
        }
        ListTag scheduledTags = tag.getListOrEmpty("Scheduled");
        for (int i = 0; i < scheduledTags.size(); i++) {
            try {
                CompoundTag entry = scheduledTags.getCompoundOrEmpty(i);
                ScheduledMail scheduledMail = new ScheduledMail(AntmailAddress.parse(entry.getStringOr("Address", "")),
                        Identifier.parse(entry.getStringOr("Id", "")), entry.getLongOr("DueDay", 0L));
                data.addScheduled(scheduledMail);
            } catch (RuntimeException ignored) {
            }
        }
        loadIdSets(tag.getListOrEmpty("PoolSeen"), data.poolSeen);
        loadIdSets(tag.getListOrEmpty("ReadDefinitions"), data.readDefinitions);
        loadIdSets(tag.getListOrEmpty("CompletedTasks"), data.completedTasks);
        loadIdSets(tag.getListOrEmpty("PackageClaims"), data.packageClaims);
        loadIdSets(tag.getListOrEmpty("PendingPackages"), data.pendingPackages);
        ListTag profileTags = tag.getListOrEmpty("AntmailProfiles");
        for (int i = 0; i < profileTags.size(); i++) {
            CompoundTag profileTag = profileTags.getCompoundOrEmpty(i);
            try {
                AntmailAddress address = AntmailAddress.parse(profileTag.getStringOr("Address", ""));
                AntmailProfile profile = new AntmailProfile(profileTag.getStringOr("DisplayName", ""), profileTag.getStringOr("AvatarItem", ""));
                if (!profile.displayName().isBlank() || !profile.avatarItem().isBlank()) data.profiles.put(address, profile);
            } catch (RuntimeException ignored) {
            }
        }
        return loaded;
    }

    private static void loadIdSets(ListTag tags, Map<AntmailAddress, Set<Identifier>> target) {
        for (int i = 0; i < tags.size(); i++) {
            try {
                CompoundTag row = tags.getCompoundOrEmpty(i);
                Set<Identifier> ids = new LinkedHashSet<>();
                ListTag idsTag = row.getListOrEmpty("Ids");
                for (int j = 0; j < idsTag.size(); j++) ids.add(Identifier.parse(idsTag.getStringOr(j, "")));
                target.put(AntmailAddress.parse(row.getStringOr("Address", "")), ids);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static ListTag saveIdSets(Map<AntmailAddress, Set<Identifier>> source) {
        ListTag tags = new ListTag();
        for (Map.Entry<AntmailAddress, Set<Identifier>> entry : source.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("Address", entry.getKey().fullAddress());
            ListTag idsTag = new ListTag();
            for (Identifier id : entry.getValue()) idsTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            row.put("Ids", idsTag);
            tags.add(row);
        }
        return tags;
    }
    @Override
    public boolean isDirty() {
        if (super.isDirty()) return true;
        if (legacyTag == null || legacyMailboxes != null) return false;
        for (AntmailAddress address : migrated) {
            AntmailMailboxShard shard = shards.get(address);
            if (shard != null && !shard.confirmPersisted(server, AntmailMailboxShard.key(address))) return false;
        }
        return true;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (legacyTag != null) {
            boolean unsaved = legacyMailboxes != null;
            for (AntmailAddress address : migrated) {
                AntmailMailboxShard shard = shards.get(address);
                if (shard != null && !shard.confirmPersisted(server, AntmailMailboxShard.key(address))) unsaved = true;
            }
            if (unsaved) {
                for (String key : legacyTag.keySet()) tag.put(key, legacyTag.get(key).copy());
            } else {
                legacyTag = null;
                migrated.clear();
            }
        }
        ListTag indexTags = new ListTag();
        for (Map.Entry<AntmailAddress, MailboxIndex> entry : mailboxIndex.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Address", entry.getKey().fullAddress());
            row.putInt("InboxSize", entry.getValue().inboxSize());
            if (entry.getValue().purgeable()) row.putBoolean("Purgeable", true);
            ListTag correspondentTags = new ListTag();
            for (AntmailAddress address : entry.getValue().correspondents()) correspondentTags.add(net.minecraft.nbt.StringTag.valueOf(address.fullAddress()));
            row.put("Correspondents", correspondentTags);
            indexTags.add(row);
        }
        tag.put("MailboxIndex", indexTags);
        ListTag triggeredTags = new ListTag();
        for (Map.Entry<AntmailAddress, java.util.Set<Identifier>> entry : triggered.entrySet()) {
            CompoundTag triggeredTag = new CompoundTag();
            triggeredTag.putString("Address", entry.getKey().fullAddress());
            ListTag idsTag = new ListTag();
            for (Identifier id : entry.getValue()) idsTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            triggeredTag.put("Ids", idsTag);
            triggeredTags.add(triggeredTag);
        }
        tag.put("Triggered", triggeredTags);
        tag.putLong("RandomMailDay", randomMailDay);
        if (!randomMailQueue.isEmpty()) {
            ListTag randomQueueTags = new ListTag();
            for (AntmailAddress address : randomMailQueue) randomQueueTags.add(net.minecraft.nbt.StringTag.valueOf(address.fullAddress()));
            tag.put("RandomMailQueue", randomQueueTags);
        }
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
            link.putIntArray("Player", UUIDUtil.uuidToIntArray(entry.getKey()));
            link.putIntArray("Account", UUIDUtil.uuidToIntArray(entry.getValue()));
            linkTags.add(link);
        }
        tag.put("LinkedAccounts", linkTags);
        ListTag scheduledTags = new ListTag();
        for (ScheduledMail entry : scheduled.keySet()) {
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
        com.craisinlord.antos.content.AntOSSavedDataType.migrateLegacyFile(server, ID);
        net.minecraft.world.level.storage.SavedDataStorage dataStorage = server.overworld().getDataStorage();
        AntmailServerData data = dataStorage.computeIfAbsent(FACTORY);
        if (data.storage != dataStorage) data.attach(server, dataStorage);
        return data;
    }

    private synchronized void attach(MinecraftServer server, net.minecraft.world.level.storage.SavedDataStorage dataStorage) {
        if (storage == dataStorage) return;
        com.craisinlord.antos.content.AntOSSavedDataType.migrateLegacyPrefix(server, "antos_mailbox_");
        storage = dataStorage;
        this.server = server;
        shards.clear();
        if (legacyMailboxes == null) return;
        for (AntmailMailbox mailbox : legacyMailboxes.mailboxes()) {
            String key = AntmailMailboxShard.key(mailbox.address());
            AntmailMailboxShard shard = dataStorage.get(AntmailMailboxShard.type(key));
            if (shard == null) {
                shard = new AntmailMailboxShard();
                dataStorage.set(AntmailMailboxShard.type(key), shard);
            }
            if (!shard.hasMailbox()) {
                shard.setMailbox(mailbox);
                shard.awaitPersist();
                shard.setDirty();
                migrated.add(mailbox.address());
            }
            shards.put(mailbox.address(), shard);
            refreshIndex(mailbox.address(), shard.mailbox(mailbox.address()));
        }
        legacyMailboxes = null;
        setDirty();
    }

    private AntmailMailboxShard shard(AntmailAddress address) {
        AntmailMailboxShard shard = shards.get(address);
        if (shard != null) return shard;
        shard = storage.computeIfAbsent(AntmailMailboxShard.type(AntmailMailboxShard.key(address)));
        shards.put(address, shard);
        refreshIndex(address, shard.mailbox(address));
        return shard;
    }

    private AntmailMailbox loadedMailbox(AntmailAddress address) {
        if (address == null) return null;
        if (!mailboxIndex.containsKey(address)) {
            if (storage == null || shards.containsKey(address)) return null;
            AntmailMailboxShard orphan = storage.get(AntmailMailboxShard.type(AntmailMailboxShard.key(address)));
            if (orphan == null || !orphan.hasMailbox()) return null;
            shards.put(address, orphan);
            refreshIndex(address, orphan.mailbox(address));
            setDirty();
            return orphan.mailbox(address);
        }
        return shard(address).mailbox(address);
    }

    private AntmailMailbox loadOrCreate(AntmailAddress address) {
        boolean created = !mailboxIndex.containsKey(address);
        AntmailMailboxShard shard = shard(address);
        if (created) {
            shard.setDirty();
            setDirty();
        }
        return shard.mailbox(address);
    }

    private void refreshIndex(AntmailAddress address, AntmailMailbox mailbox) {
        MailboxIndex entry = MailboxIndex.of(mailbox);
        if (entry.equals(mailboxIndex.get(address))) return;
        mailboxIndex.put(address, entry);
        setDirty();
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
        var workspaces = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.level().getServer());
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

    public synchronized boolean isClaimed(AntmailAddress address, Identifier triggerId) {
        return triggered.getOrDefault(address, Set.of()).contains(triggerId);
    }

    public synchronized boolean isScheduled(AntmailAddress address, Identifier definitionId) {
        Map<Identifier, Set<ScheduledMail>> ids = scheduledIds.get(address);
        return ids != null && ids.containsKey(definitionId);
    }

    public synchronized void schedule(AntmailAddress address, Identifier definitionId, long dueDay) {
        if (addScheduled(new ScheduledMail(address, definitionId, dueDay))) setDirty();
    }

    private boolean addScheduled(ScheduledMail entry) {
        if (scheduled.containsKey(entry)) return false;
        long sequence = scheduleSequence++;
        scheduled.put(entry, sequence);
        scheduledIds.computeIfAbsent(entry.address(), ignored -> new java.util.HashMap<>())
                .computeIfAbsent(entry.definitionId(), ignored -> new LinkedHashSet<>()).add(entry);
        if (scheduleDay != Long.MIN_VALUE && entry.dueDay() <= scheduleDay) readySchedule.put(sequence, entry);
        else futureSchedule.computeIfAbsent(entry.dueDay(), ignored -> new LinkedHashSet<>()).add(entry);
        return true;
    }

    public synchronized List<ScheduledMail> scheduled() {
        return List.copyOf(scheduled.keySet());
    }

    public synchronized boolean isScheduled(ScheduledMail entry) {
        return scheduled.containsKey(entry);
    }

    public synchronized List<ScheduledMail> dueSchedule(long today) {
        if (today != scheduleDay) {
            scheduleDay = today;
            var due = futureSchedule.headMap(today, true);
            for (Set<ScheduledMail> entries : due.values()) {
                for (ScheduledMail entry : entries) readySchedule.put(scheduled.get(entry), entry);
            }
            due.clear();
            wakeAll(parkedFull);
            wakeAll(parkedOffline);
        }
        return readySchedule.isEmpty() ? List.of() : List.copyOf(readySchedule.values());
    }

    public synchronized List<ScheduledMail> scheduledFor(AntmailAddress address, Identifier definitionId) {
        Map<Identifier, Set<ScheduledMail>> ids = scheduledIds.get(address);
        Set<ScheduledMail> entries = ids == null ? null : ids.get(definitionId);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public synchronized void parkUntilSpace(ScheduledMail entry) {
        park(entry, parkedFull);
    }

    public synchronized void parkUntilOnline(ScheduledMail entry) {
        park(entry, parkedOffline);
    }

    private void park(ScheduledMail entry, Map<AntmailAddress, Set<ScheduledMail>> target) {
        Long sequence = scheduled.get(entry);
        if (sequence == null) return;
        detach(entry, sequence);
        target.computeIfAbsent(entry.address(), ignored -> new LinkedHashSet<>()).add(entry);
    }

    private void wake(Map<AntmailAddress, Set<ScheduledMail>> source, AntmailAddress address) {
        Set<ScheduledMail> entries = source.remove(address);
        if (entries == null) return;
        for (ScheduledMail entry : entries) {
            Long sequence = scheduled.get(entry);
            if (sequence != null) readySchedule.put(sequence, entry);
        }
    }

    private void wakeAll(Map<AntmailAddress, Set<ScheduledMail>> source) {
        for (AntmailAddress address : List.copyOf(source.keySet())) wake(source, address);
    }

    public synchronized void wakeOnline(AntmailAddress address) {
        if (address != null) wake(parkedOffline, address);
    }

    public synchronized void updateOnline(java.util.Collection<AntmailAddress> online) {
        for (AntmailAddress address : online) {
            if (!onlineAddresses.contains(address)) wake(parkedOffline, address);
        }
        onlineAddresses.clear();
        onlineAddresses.addAll(online);
    }

    private void detach(ScheduledMail entry, long sequence) {
        readySchedule.remove(sequence);
        Set<ScheduledMail> future = futureSchedule.get(entry.dueDay());
        if (future != null && future.remove(entry) && future.isEmpty()) futureSchedule.remove(entry.dueDay());
        removeParked(parkedFull, entry);
        removeParked(parkedOffline, entry);
    }

    private static void removeParked(Map<AntmailAddress, Set<ScheduledMail>> source, ScheduledMail entry) {
        Set<ScheduledMail> entries = source.get(entry.address());
        if (entries != null && entries.remove(entry) && entries.isEmpty()) source.remove(entry.address());
    }

    public synchronized void unschedule(ScheduledMail entry) {
        Long sequence = scheduled.remove(entry);
        if (sequence == null) return;
        detach(entry, sequence);
        Map<Identifier, Set<ScheduledMail>> ids = scheduledIds.get(entry.address());
        if (ids != null) {
            Set<ScheduledMail> entries = ids.get(entry.definitionId());
            if (entries != null && entries.remove(entry) && entries.isEmpty()) ids.remove(entry.definitionId());
            if (ids.isEmpty()) scheduledIds.remove(entry.address());
        }
        setDirty();
    }

    public synchronized boolean inboxFull(AntmailAddress address) {
        AntmailMailboxShard shard = shards.get(address);
        if (shard != null) return shard.mailbox(address).inboxSize() >= AntmailValidation.MAX_MAILBOX_MESSAGES;
        MailboxIndex entry = mailboxIndex.get(address);
        return entry != null && entry.inboxSize() >= AntmailValidation.MAX_MAILBOX_MESSAGES;
    }

    synchronized void queueRandomMail(java.util.Collection<AntmailAddress> addresses) {
        randomMailQueue.clear();
        randomMailQueue.addAll(addresses);
        if (!randomMailQueue.isEmpty()) setDirty();
    }

    synchronized List<AntmailAddress> takeRandomMail(int count) {
        if (randomMailQueue.isEmpty()) return List.of();
        List<AntmailAddress> batch = new ArrayList<>(Math.min(count, randomMailQueue.size()));
        while (batch.size() < count && !randomMailQueue.isEmpty()) batch.add(randomMailQueue.poll());
        setDirty();
        return batch;
    }

    private static boolean addId(Map<AntmailAddress, Set<Identifier>> map, AntmailAddress address, Identifier id) {
        return map.computeIfAbsent(address, ignored -> new LinkedHashSet<>()).add(id);
    }

    public synchronized Set<Identifier> poolSeen(AntmailAddress address) {
        return Set.copyOf(poolSeen.getOrDefault(address, Set.of()));
    }

    public synchronized void markPoolSeen(AntmailAddress address, Identifier id) {
        if (addId(poolSeen, address, id)) setDirty();
    }

    public synchronized void resetPoolSeen(AntmailAddress address, java.util.Collection<Identifier> ids) {
        Set<Identifier> seen = poolSeen.get(address);
        if (seen != null && seen.removeAll(ids)) setDirty();
    }

    public synchronized boolean hasReadDefinition(AntmailAddress address, Identifier id) {
        return readDefinitions.getOrDefault(address, Set.of()).contains(id);
    }

    public synchronized void markDefinitionRead(AntmailAddress address, Identifier id) {
        if (addId(readDefinitions, address, id)) setDirty();
    }

    public synchronized boolean hasCompletedTask(AntmailAddress address, Identifier id) {
        return completedTasks.getOrDefault(address, Set.of()).contains(id);
    }

    public synchronized void markTaskCompleted(AntmailAddress address, Identifier id) {
        if (addId(completedTasks, address, id)) setDirty();
    }

    public synchronized boolean claimPackage(AntmailAddress address, Identifier id) {
        if (!addId(packageClaims, address, id)) return false;
        setDirty();
        return true;
    }

    public synchronized void addPendingPackage(AntmailAddress address, Identifier id) {
        if (addId(pendingPackages, address, id)) setDirty();
    }

    public synchronized List<Identifier> takePendingPackages(AntmailAddress address) {
        Set<Identifier> pendingIds = pendingPackages.remove(address);
        if (pendingIds == null || pendingIds.isEmpty()) return List.of();
        setDirty();
        return List.copyOf(pendingIds);
    }

    public synchronized void purgeReadSelfDeleting(AntmailAddress address) {
        MailboxIndex entry = mailboxIndex.get(address);
        if (entry == null || !entry.purgeable() && !shards.containsKey(address)) return;
        AntmailMailbox mailbox = loadedMailbox(address);
        if (mailbox == null) return;
        boolean changed = false;
        for (AntmailMessage message : mailbox.inbox()) {
            if (message.read() && message.deleteAfterRead()) changed |= mailbox.removeInbox(message.id());
        }
        if (changed) touchMailbox(address);
    }

    public synchronized boolean claimTrigger(AntmailAddress address, Identifier triggerId) {
        java.util.Set<Identifier> ids = triggered.computeIfAbsent(address, ignored -> new java.util.LinkedHashSet<>());
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
        return loadedMailbox(address);
    }

    public synchronized AntmailMailbox mailboxOrCreate(AntmailAddress address) {
        return loadOrCreate(address);
    }

    public synchronized long mailboxVersion(AntmailAddress address) {
        return mailboxVersions.getOrDefault(address, 1L);
    }

    public synchronized void touchMailbox(AntmailAddress address) {
        if (address == null) return;
        AntmailMailboxShard shard = shards.get(address);
        if (shard != null) {
            shard.setDirty();
            refreshIndex(address, shard.mailbox(address));
        }
        bumpVersion(address);
    }

    private void bumpVersion(AntmailAddress address) {
        mailboxVersions.put(address, mailboxVersion(address) + 1L);
        if (parkedFull.containsKey(address) && !inboxFull(address)) wake(parkedFull, address);
        setDirty();
    }

    public synchronized AntmailProfile profile(AntmailAddress address) {
        return profiles.getOrDefault(address, new AntmailProfile("", ""));
    }

    public synchronized void setProfile(AntmailAddress address, String displayName, String avatarItem, MinecraftServer server) {
        AntmailProfile profile = new AntmailProfile(displayName, avatarItem);
        boolean blank = profile.displayName().isBlank() && profile.avatarItem().isBlank();
        AntmailProfile previous = profiles.get(address);
        if (blank ? previous == null : profile.equals(previous)) return;
        if (blank) profiles.remove(address);
        else profiles.put(address, profile);
        bumpVersion(address);
        for (Map.Entry<AntmailAddress, MailboxIndex> entry : List.copyOf(mailboxIndex.entrySet())) {
            if (entry.getKey().equals(address)) continue;
            AntmailMailboxShard shard = shards.get(entry.getKey());
            boolean involves = shard != null ? shard.mailbox(entry.getKey()).involves(address) : entry.getValue().correspondents().contains(address);
            if (involves) bumpVersion(entry.getKey());
        }
        setDirty();
    }

    public synchronized void appendProfiles(CompoundTag snapshot, AntmailAddress ownAddress) {
        Set<AntmailAddress> addresses = new LinkedHashSet<>();
        if (ownAddress != null) addresses.add(ownAddress);
        collectProfileAddress(snapshot, addresses);
        collectProfileAddresses(snapshot.getListOrEmpty("Inbox"), addresses);
        collectProfileAddresses(snapshot.getListOrEmpty("Sent"), addresses);
        collectProfileAddresses(snapshot.getListOrEmpty("Archive"), addresses);
        collectProfileAddresses(snapshot.getListOrEmpty("Trash"), addresses);
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
            CompoundTag message = messages.getCompoundOrEmpty(i);
            try {
                if (message.contains("From")) addresses.add(AntmailAddress.parse(message.getStringOr("From", "")));
                if (message.contains("To")) addresses.add(AntmailAddress.parse(message.getStringOr("To", "")));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void collectProfileAddress(CompoundTag message, Set<AntmailAddress> addresses) {
        try {
            if (message.contains("From")) addresses.add(AntmailAddress.parse(message.getStringOr("From", "")));
            if (message.contains("To")) addresses.add(AntmailAddress.parse(message.getStringOr("To", "")));
        } catch (RuntimeException ignored) {
        }
    }

    public synchronized AntmailDeliveryResult deliver(MinecraftServer server, AntmailMessage message) {
        return deliver(server, message, true);
    }

    public synchronized AntmailDeliveryResult deliver(MinecraftServer server, AntmailMessage message, boolean recordFullMailbox) {
        if (message == null) return AntmailDeliveryResult.failed(AntmailDeliveryResult.Status.MESSAGE_INVALID, null, "missing_message");
        if (com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(server)
                .accountByUsername(message.recipient().username()) == null) {
            return failedAndRecorded(message, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND, "address_not_found");
        }
        AntmailMailbox destination = loadOrCreate(message.recipient());
        if (destination.inboxSize() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return !recordFullMailbox
                ? AntmailDeliveryResult.failed(AntmailDeliveryResult.Status.MAILBOX_FULL, message, "mailbox_full") : failedAndRecorded(message, AntmailDeliveryResult.Status.MAILBOX_FULL, "mailbox_full");
        message.setDeliveryStatus("DELIVERED");
        AntmailMessage incoming = message.copy();
        destination.addIncoming(incoming);
        loadOrCreate(message.sender()).addSent(message);
        touchMailbox(message.recipient());
        touchMailbox(message.sender());
        AntmailNotifier.notify(server, incoming);
        return AntmailDeliveryResult.delivered(message);
    }

    private AntmailDeliveryResult failedAndRecorded(AntmailMessage message, AntmailDeliveryResult.Status status, String detail) {
        message.setDeliveryStatus("FAILED // " + detail.toUpperCase());
        loadOrCreate(message.sender()).addSent(message);
        touchMailbox(message.sender());
        return AntmailDeliveryResult.failed(status, message, detail);
    }

    public synchronized AntmailDeliveryResult retry(MinecraftServer server, AntmailAddress sender, UUID messageId) {
        AntmailMessage message = loadOrCreate(sender).findSent(messageId);
        if (message == null) return null;
        return deliver(server, message);
    }

    public record ScheduledMail(AntmailAddress address, Identifier definitionId, long dueDay) {
    }

    private record MailboxIndex(int inboxSize, boolean purgeable, Set<AntmailAddress> correspondents) {
        private static MailboxIndex of(AntmailMailbox mailbox) {
            return new MailboxIndex(mailbox.inboxSize(), mailbox.hasReadSelfDeleting(), mailbox.correspondents());
        }
    }
}


