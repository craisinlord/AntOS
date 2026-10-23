package com.craisinlord.antos.config;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NotificationHistory {
    public static final int LIMIT = 100;
    private static final Gson GSON = new Gson();
    private final List<Entry> entries = new ArrayList<>();

    public record Entry(String id, String kind, String title, String message, long receivedAt, boolean read,
                        String source, String iconItem, String iconEntity) {
        public Entry {
            source = source == null ? "" : source;
            iconItem = iconItem == null ? "" : iconItem;
            iconEntity = iconEntity == null ? "" : iconEntity;
        }

        Entry asRead() { return new Entry(id, kind, title, message, receivedAt, true, source, iconItem, iconEntity); }
    }

    public static NotificationHistory decode(String json) {
        NotificationHistory history = new NotificationHistory();
        if (json == null || json.isBlank()) return history;
        try {
            Entry[] loaded = GSON.fromJson(json, Entry[].class);
            if (loaded != null) {
                for (Entry entry : loaded) {
                    if (entry == null || entry.id() == null || entry.kind() == null || entry.title() == null || entry.message() == null) continue;
                    if (history.entries.stream().anyMatch(existing -> existing.id().equals(entry.id()))) continue;
                    history.entries.add(new Entry(entry.id(), entry.kind(), entry.title(), entry.message(), entry.receivedAt(), entry.read(),
                            entry.source(), entry.iconItem(), entry.iconEntity()));
                    if (history.entries.size() == LIMIT) break;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return history;
    }

    public Entry add(String kind, String title, String message, long receivedAt) {
        return add(kind, title, message, receivedAt, "", "", "");
    }

    public Entry add(String kind, String title, String message, long receivedAt, String source, String iconItem, String iconEntity) {
        Entry entry = new Entry(UUID.randomUUID().toString(), kind, title, message, receivedAt, false, source, iconItem, iconEntity);
        entries.add(0, entry);
        while (entries.size() > LIMIT) entries.removeLast();
        return entry;
    }

    public List<Entry> entries() { return List.copyOf(entries); }
    public long unreadCount() { return entries.stream().filter(entry -> !entry.read()).count(); }
    public String encode() { return GSON.toJson(entries); }

    public void markRead(String id) {
        entries.replaceAll(entry -> entry.id().equals(id) ? entry.asRead() : entry);
    }

    public void markAllRead() { entries.replaceAll(Entry::asRead); }
    public void clear() { entries.clear(); }
}
