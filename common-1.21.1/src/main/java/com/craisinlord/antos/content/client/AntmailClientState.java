package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.antmail.AntmailDebug;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailProfile;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntmailClientState {
    private static final int MAX_CACHED_DETAILS_PER_MAILBOX = 32;
    private static volatile AntmailAnternetResultPayload result;
    private static volatile AntmailAnternetResultPayload sendResult;
    private static volatile AntmailAnternetResultPayload mailbox;
    private static volatile long version;
    private static volatile Snapshot snapshot;
    private static volatile boolean refreshRequested;
    private static final Map<java.util.UUID, AntmailMessage> DETAILS = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<java.util.UUID, AntmailMessage> eldest) {
                    return size() > MAX_CACHED_DETAILS_PER_MAILBOX;
                }
            });
    private static final Map<String, AntmailProfile> PROFILES = new ConcurrentHashMap<>();
    private static volatile java.util.List<JunkSender> junkBlocked = java.util.List.of();
    private static volatile boolean junkBlockAll;

    public record JunkSender(String address, String name) {
    }

    private AntmailClientState() {
    }

    public static void update(AntmailAnternetResultPayload payload) {
        AntmailDebug.log("S2C result status=" + payload.status() + " detail=" + payload.detail()
                + " addressPresent=" + !payload.address().isBlank() + " dataChars=" + payload.data().length()
                + " version=" + payload.version());
        if (payload.detail().startsWith("send:")) {
            payload = new AntmailAnternetResultPayload(payload.status(), payload.address(), payload.messageId(),
                    payload.detail().substring(5), payload.data(), payload.version());
            sendResult = payload;
        }
        result = payload;
        if ("unchanged".equals(payload.detail())) {
            if (mailbox != null) {
                version = payload.version();
            } else {
                AntmailDebug.log("received unchanged without cached mailbox; forcing full snapshot on next poll");
                version = 0L;
            }
            return;
        } else if ("message_detail".equals(payload.detail()) && !payload.data().isBlank()) {
            try {
                AntmailMessage message = AntmailMessage.fromTag(com.craisinlord.antos.content.antmail.AntmailWire.decodeTag(payload.data()));
                DETAILS.put(message.id(), message);
            } catch (RuntimeException ignored) {
            }
        } else if (!payload.data().isBlank()) {
            try {
                CompoundTag tag = com.craisinlord.antos.content.antmail.AntmailWire.decodeTag(payload.data());
                Snapshot decoded = new Snapshot(payload, tag, AntmailMailbox.fromTag(tag), requestKey(tag.getInt("Folder"),
                        tag.getString("Query"), tag.getBoolean("UnreadOnly"), tag.getBoolean("AttachmentsOnly")));
                updateProfiles(tag);
                updateJunk(tag);
                snapshot = decoded;
                mailbox = payload;
                version = payload.version();
            } catch (RuntimeException exception) {
                AntmailDebug.error("Could not decode Antmail snapshot", exception);
                version = 0L;
            }
        } else if ("unauthorized".equals(payload.detail()) || "unconfigured".equals(payload.detail())
                || "state_encode_failed".equals(payload.detail())) {
            mailbox = null;
            snapshot = null;
            version = 0L;
        } else if (!"result_too_large".equals(payload.detail()) && !"invalid_action".equals(payload.detail())
                && !"rate_limited".equals(payload.detail()) && !"profile_rate_limited".equals(payload.detail())) {
            refreshRequested = true;
        }
    }

    public static boolean consumeRefreshRequest() {
        boolean requested = refreshRequested;
        refreshRequested = false;
        return requested;
    }

    public static String requestKey(int folder, String query, boolean unreadOnly, boolean attachmentsOnly) {
        String normalized = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return folder + "|" + unreadOnly + "|" + attachmentsOnly + "|" + normalized;
    }

    public static Snapshot snapshot(AntmailAnternetResultPayload payload) {
        Snapshot current = snapshot;
        return current != null && current.payload() == payload ? current : null;
    }

    public static String snapshotKey() {
        Snapshot current = snapshot;
        return current == null ? "" : current.key();
    }

    public record Snapshot(AntmailAnternetResultPayload payload, CompoundTag tag, AntmailMailbox mailbox, String key) {
    }

    public static void clear() {
        result = null;
        sendResult = null;
        mailbox = null;
        snapshot = null;
        refreshRequested = false;
        version = 0L;
        DETAILS.clear();
        PROFILES.clear();
        junkBlocked = java.util.List.of();
        junkBlockAll = false;
    }

    public static AntmailAnternetResultPayload get() {
        return result;
    }

    public static AntmailAnternetResultPayload getSendResult() {
        return sendResult;
    }

    public static AntmailAnternetResultPayload getMailbox() {
        return mailbox;
    }

    public static long getVersion() {
        return version;
    }

    public static AntmailMessage getMessage(java.util.UUID id) {
        return DETAILS.get(id);
    }

    public static AntmailProfile getProfile(String address) {
        if (address == null || address.isBlank()) return new AntmailProfile("", "");
        try {
            return PROFILES.getOrDefault(AntmailAddress.parse(address).fullAddress(), new AntmailProfile("", ""));
        } catch (RuntimeException ignored) {
            return new AntmailProfile("", "");
        }
    }

    public static java.util.List<JunkSender> junkBlocked() {
        return junkBlocked;
    }

    public static boolean junkBlockAll() {
        return junkBlockAll;
    }

    public static boolean junkSenderBlocked(String address) {
        if (address == null) return false;
        for (JunkSender sender : junkBlocked) if (sender.address().equalsIgnoreCase(address)) return true;
        return false;
    }

    public static void setJunkSenderBlocked(String address, String name, boolean blocked) {
        java.util.List<JunkSender> updated = new java.util.ArrayList<>(junkBlocked);
        updated.removeIf(sender -> sender.address().equalsIgnoreCase(address));
        if (blocked) updated.add(new JunkSender(address, name));
        junkBlocked = java.util.List.copyOf(updated);
    }

    public static void setJunkBlockAll(boolean blocked) {
        junkBlockAll = blocked;
    }

    private static void updateJunk(CompoundTag snapshot) {
        if (!snapshot.contains("JunkBlocked", 9)) return;
        java.util.List<JunkSender> loaded = new java.util.ArrayList<>();
        ListTag rows = snapshot.getList("JunkBlocked", 10);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            if (!row.getString("Address").isBlank()) loaded.add(new JunkSender(row.getString("Address"), row.getString("Name")));
        }
        junkBlocked = java.util.List.copyOf(loaded);
        junkBlockAll = snapshot.getBoolean("JunkBlockAll");
    }

    private static void updateProfiles(CompoundTag snapshot) {
        try {
            if (!snapshot.contains("Profiles", 9)) return;
            PROFILES.clear();
            ListTag profiles = snapshot.getList("Profiles", 10);
            for (int i = 0; i < profiles.size(); i++) {
                CompoundTag profile = profiles.getCompound(i);
                try {
                    String address = AntmailAddress.parse(profile.getString("Address")).fullAddress();
                    PROFILES.put(address, new AntmailProfile(profile.getString("DisplayName"), profile.getString("AvatarItem")));
                } catch (RuntimeException ignored) {
                }
            }
        } catch (RuntimeException ignored) {
        }
    }
}


