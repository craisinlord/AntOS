package com.craisinlord.antos.content.antmail;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntmailMailbox {
    private final AntmailAddress address;
    private final List<AntmailMessage> inbox = new ArrayList<>();
    private final List<AntmailMessage> sent = new ArrayList<>();
    private final List<AntmailMessage> archived = new ArrayList<>();
    private final List<AntmailMessage> trash = new ArrayList<>();
    private final List<AntmailDraft> drafts = new ArrayList<>();
    private int reportedUnreadCount = -1;

    public AntmailMailbox(AntmailAddress address) {
        this.address = address;
    }

    public AntmailAddress address() { return address; }
    public List<AntmailMessage> inbox() { return List.copyOf(inbox); }
    public List<AntmailMessage> sent() { return List.copyOf(sent); }
    public List<AntmailMessage> archived() { return List.copyOf(archived); }
    public List<AntmailMessage> trash() { return List.copyOf(trash); }
    public List<AntmailDraft> drafts() { return List.copyOf(drafts); }
    public int inboxSize() { return inbox.size(); }
    public int draftCount() { return drafts.size(); }
    public int unreadCount() { return reportedUnreadCount >= 0 ? reportedUnreadCount : (int) inbox.stream().filter(message -> !message.read()).count(); }

    public boolean addIncoming(AntmailMessage message) {
        if (!address.equals(message.recipient()) || inbox.size() >= AntmailValidation.MAX_MAILBOX_MESSAGES || contains(inbox, message.id())) return false;
        inbox.add(message);
        return true;
    }

    public boolean addSent(AntmailMessage message) {
        if (!address.equals(message.sender()) || sent.size() >= AntmailValidation.MAX_MAILBOX_MESSAGES || contains(sent, message.id())) return false;
        sent.add(message);
        return true;
    }

    public boolean saveDraft(AntmailDraft draft) {
        if (!address.equals(draft.sender()) || drafts.size() >= AntmailValidation.MAX_DRAFTS && findDraft(draft.id()) == null) return false;
        drafts.removeIf(existing -> existing.id().equals(draft.id()));
        drafts.add(draft);
        return true;
    }

    public boolean removeDraft(UUID id) { return drafts.removeIf(draft -> draft.id().equals(id)); }
    public boolean removeInbox(UUID id) { return inbox.removeIf(message -> message.id().equals(id)); }
    public boolean removeSent(UUID id) { return sent.removeIf(message -> message.id().equals(id)); }
    public boolean removeArchived(UUID id) { return archived.removeIf(message -> message.id().equals(id)); }
    public boolean removeTrash(UUID id) { return trash.removeIf(message -> message.id().equals(id)); }
    public AntmailMessage findSent(UUID id) { return sent.stream().filter(message -> message.id().equals(id)).findFirst().orElse(null); }
    public AntmailMessage findInbox(UUID id) { return inbox.stream().filter(message -> message.id().equals(id)).findFirst().orElse(null); }
    public AntmailMessage findArchived(UUID id) { return archived.stream().filter(message -> message.id().equals(id)).findFirst().orElse(null); }
    public AntmailMessage findTrash(UUID id) { return trash.stream().filter(message -> message.id().equals(id)).findFirst().orElse(null); }

    public boolean markRead(UUID id) {
        for (AntmailMessage message : inbox) if (message.id().equals(id)) { message.markRead(); return true; }
        return false;
    }

    public boolean moveToArchive(UUID id, boolean fromSent) {
        List<AntmailMessage> source = fromSent ? sent : inbox;
        AntmailMessage message = source.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (message == null || archived.size() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return false;
        source.remove(message);
        archived.removeIf(value -> value.id().equals(id));
        archived.add(0, message);
        return true;
    }

    public boolean moveToTrash(UUID id, int sourceFolder) {
        List<AntmailMessage> source = sourceFolder == 1 ? sent : sourceFolder == 3 ? archived : inbox;
        AntmailMessage message = source.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (message == null || trash.size() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return false;
        source.remove(message);
        trash.removeIf(value -> value.id().equals(id));
        trash.add(0, message);
        return true;
    }

    public boolean restoreFromTrash(UUID id, int destinationFolder) {
        AntmailMessage message = trash.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (message == null) return false;
        trash.remove(message);
        List<AntmailMessage> destination = destinationFolder == 1 ? sent : destinationFolder == 3 ? archived : inbox;
        if (destination.size() >= AntmailValidation.MAX_MAILBOX_MESSAGES) {
            trash.add(message);
            return false;
        }
        destination.add(0, message);
        return true;
    }

    public boolean restoreFromArchive(UUID id) {
        return restoreFromArchive(id, 0);
    }

    public boolean restoreFromArchive(UUID id, int destinationFolder) {
        AntmailMessage message = archived.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        List<AntmailMessage> destination = destinationFolder == 1 ? sent : inbox;
        if (message == null || destination.size() >= AntmailValidation.MAX_MAILBOX_MESSAGES) return false;
        archived.remove(message);
        destination.add(0, message);
        return true;
    }

    public AntmailDraft findDraft(UUID id) { return drafts.stream().filter(draft -> draft.id().equals(id)).findFirst().orElse(null); }

    public CompoundTag toTag() {
        return toTag(true);
    }

    public CompoundTag toTag(boolean includeMessageAttachments) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Address", address.fullAddress());
        tag.put("Inbox", messagesToTag(inbox, includeMessageAttachments));
        tag.put("Sent", messagesToTag(sent, includeMessageAttachments));
        tag.put("Archive", messagesToTag(archived, includeMessageAttachments));
        tag.put("Trash", messagesToTag(trash, includeMessageAttachments));
        ListTag draftTags = new ListTag();
        for (AntmailDraft draft : drafts) draftTags.add(draft.toTag());
        tag.put("Drafts", draftTags);
        return tag;
    }

    public CompoundTag toPageTag(int folder, int page) {
        return toPageTag(folder, page, "", false, false);
    }

    public CompoundTag toPageTag(int folder, int page, String query, boolean unreadOnly, boolean attachmentsOnly) {
        return toPageTag(folder, page, query, unreadOnly, attachmentsOnly, true, Integer.MAX_VALUE);
    }

    /**
     * A folder snapshot for the client: list-form messages (short body preview, no attachment payloads) plus drafts.
     * The request key (folder, query, filters) is echoed back so the client can reuse it for "unchanged" checks.
     * {@code draftAttachments} and {@code maxRows} let the caller shrink an oversized snapshot to fit the packet cap.
     */
    public CompoundTag toPageTag(int folder, int page, String query, boolean unreadOnly, boolean attachmentsOnly,
                                 boolean draftAttachments, int maxRows) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Address", address.fullAddress());
        tag.putInt("Folder", folder);
        tag.putInt("Page", 0);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        tag.putString("Query", normalizedQuery);
        tag.putBoolean("UnreadOnly", unreadOnly);
        tag.putBoolean("AttachmentsOnly", attachmentsOnly);
        List<AntmailMessage> all = switch (folder) {
            case 1 -> sent;
            case 3 -> archived;
            case 4 -> trash;
            default -> inbox;
        };
        List<AntmailMessage> filtered = new ArrayList<>();
        for (AntmailMessage message : all) {
            if (unreadOnly && message.read()) continue;
            if (attachmentsOnly && !message.hasAttachments()) continue;
            if (!normalizedQuery.isBlank() && !message.searchText().contains(normalizedQuery)) continue;
            filtered.add(message);
        }
        List<AntmailMessage> rows = filtered.size() > maxRows ? filtered.subList(0, Math.max(0, maxRows)) : filtered;
        int unread = 0;
        for (AntmailMessage message : inbox) if (!message.read()) unread++;
        tag.putInt("PageSize", rows.size());
        if (rows.size() < filtered.size()) tag.putBoolean("Truncated", true);
        tag.putInt("TotalInbox", folder == 0 ? filtered.size() : inbox.size());
        tag.putInt("TotalSent", folder == 1 ? filtered.size() : sent.size());
        tag.putInt("UnreadCount", unread);
        tag.putInt("TotalArchive", folder == 3 ? filtered.size() : archived.size());
        tag.putInt("TotalTrash", folder == 4 ? filtered.size() : trash.size());
        tag.put("Inbox", folder == 0 ? messagesToListTag(rows) : new ListTag());
        tag.put("Sent", folder == 1 ? messagesToListTag(rows) : new ListTag());
        tag.put("Archive", folder == 3 ? messagesToListTag(rows) : new ListTag());
        tag.put("Trash", folder == 4 ? messagesToListTag(rows) : new ListTag());
        ListTag draftTags = new ListTag();
        for (int index = 0; index < drafts.size(); index++) {
            AntmailDraft draft = drafts.get(index);
            // The newest draft keeps its attachments even when trimming, since compose restores it.
            boolean full = draftAttachments || index == drafts.size() - 1;
            draftTags.add(full ? draft.toTag() : draft.withoutAttachments().toTag());
        }
        tag.put("Drafts", draftTags);
        return tag;
    }

    public static AntmailMailbox fromTag(CompoundTag tag) {
        AntmailMailbox mailbox = new AntmailMailbox(AntmailAddress.parse(tag.getString("Address")));
        if (tag.contains("UnreadCount")) mailbox.reportedUnreadCount = Math.max(0, tag.getInt("UnreadCount"));
        readMessages(tag.getList("Inbox", 10), mailbox.inbox);
        readMessages(tag.getList("Sent", 10), mailbox.sent);
        readMessages(tag.getList("Archive", 10), mailbox.archived);
        readMessages(tag.getList("Trash", 10), mailbox.trash);
        ListTag draftTags = tag.getList("Drafts", 10);
        for (int index = 0; index < draftTags.size() && mailbox.drafts.size() < AntmailValidation.MAX_DRAFTS; index++) {
            try { mailbox.drafts.add(AntmailDraft.fromTag(draftTags.getCompound(index))); } catch (RuntimeException ignored) { }
        }
        return mailbox;
    }

    private static ListTag messagesToTag(List<AntmailMessage> messages, boolean includeMessageAttachments) {
        ListTag tags = new ListTag();
        for (AntmailMessage message : messages) tags.add(message.toTag(includeMessageAttachments));
        return tags;
    }

    private static ListTag messagesToListTag(List<AntmailMessage> messages) {
        ListTag tags = new ListTag();
        for (AntmailMessage message : messages) tags.add(message.toListTag());
        return tags;
    }

    private static void readMessages(ListTag tags, List<AntmailMessage> target) {
        for (int index = 0; index < tags.size() && target.size() < AntmailValidation.MAX_MAILBOX_MESSAGES; index++) {
            try { target.add(AntmailMessage.fromTag(tags.getCompound(index))); } catch (RuntimeException ignored) { }
        }
    }

    private static boolean contains(List<AntmailMessage> messages, UUID id) {
        return messages.stream().anyMatch(message -> message.id().equals(id));
    }
}


