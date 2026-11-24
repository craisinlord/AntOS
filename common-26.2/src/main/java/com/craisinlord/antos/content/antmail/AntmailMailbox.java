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
    private final java.util.Map<UUID, AntmailMessage> batches = new java.util.LinkedHashMap<>();
    public AntmailMessage batch(UUID id) { return batches.get(id); }
    public void recordBatch(AntmailMessage message) {
        if (!batches.containsKey(message.id()) && batches.size() >= 4096) throw new IllegalArgumentException("send_journal_full");
        batches.put(message.id(), message);
        for (List<AntmailMessage> folder : List.of(inbox, sent, archived, trash)) for (int i = 0; i < folder.size(); i++) {
            if (folder.get(i).id().equals(message.id()) && folder.get(i).sender().equals(address)) folder.set(i, message);
        }
    }
    private AntmailMessage receipt(AntmailMessage message, boolean cancelled) {
        AntmailMessage receipt = new AntmailMessage(message.id(), message.sender(), message.sender(), "", "", message.createdAt(), List.of(), true);
        receipt.setBatch(message.batch().receipt(cancelled));
        receipt.setDeliveryStatus(cancelled ? "CANCELLED" : "DELIVERED");
        return receipt;
    }
    private boolean removeMessage(List<AntmailMessage> folder, UUID id) {
        boolean changed = folder.removeIf(message -> message.id().equals(id));
        if (changed && batches.containsKey(id) && findInbox(id) == null && findSent(id) == null && findArchived(id) == null && findTrash(id) == null) {
            AntmailMessage message = batches.get(id);
            batches.put(id, receipt(message, message.batch().failedCount() != 0 || message.batch().cancelled()));
        }
        return changed;
    }
    public boolean canRecordBatch() { return batches.size() < 4096; }
    public List<AntmailMessage> conversation(UUID id) { return conversation(id, new java.util.HashMap<>()); }
    


 
    public List<AntmailMessage> conversation(UUID id, java.util.Map<UUID, Integer> folders) {
        java.util.Map<UUID, AntmailMessage> rows = new java.util.LinkedHashMap<>();
        List<List<AntmailMessage>> lists = List.of(inbox, sent, archived, trash);
        int[] codes = {0, 1, 3, 4};
        for (int index = 0; index < lists.size(); index++) {
            for (AntmailMessage message : lists.get(index)) {
                if (!id.equals(message.conversationId())) continue;
                rows.put(message.id(), message);
                folders.putIfAbsent(message.id(), codes[index]);
            }
        }
        return rows.values().stream().sorted(java.util.Comparator.comparingLong(AntmailMessage::createdAt).reversed()).toList();
    }
    


 
    public java.util.Map<UUID, AntmailMessage> conversationHeads() {
        java.util.Map<UUID, AntmailMessage> heads = new java.util.LinkedHashMap<>();
        for (List<AntmailMessage> folder : List.of(inbox, sent, archived, trash)) {
            for (AntmailMessage message : folder) {
                if (message.conversationId() == null) continue;
                AntmailMessage known = heads.get(message.conversationId());
                if (known == null || message.conversationName() != null
                        && (known.conversationName() == null || message.createdAt() > known.createdAt())) heads.put(message.conversationId(), message);
            }
        }
        return heads;
    }
    public int folderOf(UUID id) {
        if (findInbox(id) != null) return 0;
        if (findSent(id) != null) return 1;
        if (findArchived(id) != null) return 3;
        if (findTrash(id) != null) return 4;
        return -1;
    }
    public boolean hasReceived(UUID id) { return findInbox(id) != null || findArchived(id) != null || findTrash(id) != null; }
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
    public boolean removeInbox(UUID id) { return removeMessage(inbox, id); }
    public boolean removeSent(UUID id) { return removeMessage(sent, id); }
    public boolean removeArchived(UUID id) { return removeMessage(archived, id); }
    public boolean removeTrash(UUID id) { return removeMessage(trash, id); }
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
        ListTag journal = new ListTag();
        for (AntmailMessage message : batches.values()) journal.add((message.batch().failedCount() == 0 ? receipt(message, message.batch().cancelled()) : message).toTag());
        tag.put("Batches", journal);
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

    public CompoundTag toPageTag(int folder, int page, String query, boolean unreadOnly, boolean attachmentsOnly,
                                 boolean draftAttachments, int maxRows) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Address", address.fullAddress());
        tag.putInt("Folder", folder);
        tag.putInt("Page", Math.max(0, page));
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
        java.util.Collections.reverse(filtered);
        filtered.sort(java.util.Comparator.comparingLong(AntmailMessage::createdAt).reversed());
        int offset = Math.max(0, Math.min(page, filtered.size()));
        int limit = Math.max(0, Math.min(20, maxRows));
        List<AntmailMessage> rows = filtered.subList(offset, Math.min(filtered.size(), offset + limit));
        tag.putInt("Page", offset);
        tag.putInt("NextPage", offset + rows.size());
        tag.putBoolean("More", offset + rows.size() < filtered.size());
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
            boolean full = draftAttachments || index == drafts.size() - 1;
            draftTags.add(full ? draft.toTag() : draft.withoutAttachments().toTag());
        }
        tag.put("Drafts", draftTags);
        return tag;
    }

    public static AntmailMailbox fromTag(CompoundTag tag) {
        AntmailMailbox mailbox = new AntmailMailbox(AntmailAddress.parse(tag.getStringOr("Address", "")));
        ListTag batchTags = tag.getListOrEmpty("Batches");
        for (int i = 0; i < batchTags.size() && mailbox.batches.size() < 4096; i++) {
            try {
                AntmailMessage batch = AntmailMessage.fromTag(batchTags.getCompoundOrEmpty(i));
                if (batch.batch() != null && batch.sender().equals(mailbox.address)) mailbox.batches.put(batch.id(), batch.batch().failedCount() == 0 ? mailbox.receipt(batch, batch.batch().cancelled()) : batch);
            } catch (RuntimeException ignored) { }
        }
        if (tag.contains("UnreadCount")) mailbox.reportedUnreadCount = Math.max(0, tag.getIntOr("UnreadCount", 0));
        readMessages(tag.getListOrEmpty("Inbox"), mailbox.inbox);
        readMessages(tag.getListOrEmpty("Sent"), mailbox.sent);
        readMessages(tag.getListOrEmpty("Archive"), mailbox.archived);
        readMessages(tag.getListOrEmpty("Trash"), mailbox.trash);
        ListTag draftTags = tag.getListOrEmpty("Drafts");
        for (int index = 0; index < draftTags.size() && mailbox.drafts.size() < AntmailValidation.MAX_DRAFTS; index++) {
            try { mailbox.drafts.add(AntmailDraft.fromTag(draftTags.getCompoundOrEmpty(index))); } catch (RuntimeException ignored) { }
        }
        for (List<AntmailMessage> folder : List.of(mailbox.inbox, mailbox.sent, mailbox.archived, mailbox.trash)) {
            for (int i = 0; i < folder.size(); i++) {
                AntmailMessage message = folder.get(i);
                AntmailMessage batch = mailbox.batches.get(message.id());
                if (message.batch() != null && batch != null && message.sender().equals(mailbox.address)) {
                    if (!batch.batch().deliveries().isEmpty()) message.setBatch(batch.batch());
                    else if (batch.batch().failedCount() == 0 && !batch.batch().cancelled()) {
                        for (var delivery : message.batch().deliveries()) message.batch().outcome(delivery.account(), "DELIVERED");
                    }
                    message.setDeliveryStatus(batch.deliveryStatus());
                    mailbox.batches.put(message.id(), message);
                }
            }
        }
        for (AntmailMessage message : new ArrayList<>(mailbox.batches.values())) {
            if (mailbox.folderOf(message.id()) < 0 && message.batch().failedCount() > 0) mailbox.batches.put(message.id(), mailbox.receipt(message, true));
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
            try { target.add(AntmailMessage.fromTag(tags.getCompoundOrEmpty(index))); } catch (RuntimeException ignored) { }
        }
    }

    public boolean involves(AntmailAddress other) {
        return involves(inbox, other) || involves(sent, other) || involves(archived, other) || involves(trash, other);
    }

    public java.util.Set<AntmailAddress> correspondents() {
        java.util.Set<AntmailAddress> addresses = new java.util.LinkedHashSet<>();
        collectCorrespondents(inbox, addresses);
        collectCorrespondents(sent, addresses);
        collectCorrespondents(archived, addresses);
        collectCorrespondents(trash, addresses);
        addresses.remove(address);
        return addresses;
    }

    public boolean hasReadSelfDeleting() {
        for (AntmailMessage message : inbox) if (message.read() && message.deleteAfterRead()) return true;
        return false;
    }

    private static void collectCorrespondents(List<AntmailMessage> messages, java.util.Set<AntmailAddress> addresses) {
        for (AntmailMessage message : messages) {
            if (message.sender() != null) addresses.add(message.sender());
            if (message.recipient() != null) addresses.add(message.recipient());
            if (message.batch() != null) for (var delivery : message.batch().deliveries()) addresses.add(AntmailAddress.parse(delivery.address()));
        }
    }

    private static boolean involves(List<AntmailMessage> messages, AntmailAddress other) {
        for (AntmailMessage message : messages) {
            if (other.equals(message.sender()) || other.equals(message.recipient()) || message.batch() != null && message.batch().deliveries().stream().anyMatch(row -> row.address().equals(other.fullAddress()))) return true;
        }
        return false;
    }

    private static boolean contains(List<AntmailMessage> messages, UUID id) {
        return messages.stream().anyMatch(message -> message.id().equals(id));
    }
}

