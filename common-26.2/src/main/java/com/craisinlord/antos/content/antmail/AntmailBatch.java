package com.craisinlord.antos.content.antmail;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntmailBatch {
    private static final Gson JSON = new Gson();
    public record Delivery(UUID account, String address, UUID id, UUID epoch, String status) {
        public Delivery withStatus(String value) { return new Delivery(account, address, id, epoch, value); }
        public boolean successful() { return "DELIVERED".equals(status); }
    }
    private boolean cancelled;
    public boolean cancelled() { return cancelled; }
    public AntmailBatch receipt(boolean cancelled) {
        AntmailBatch receipt = new AntmailBatch();
        receipt.id = id; receipt.sender = sender; receipt.senderEpoch = senderEpoch; receipt.conversation = conversation; receipt.conversationKind = conversationKind; receipt.conversationName = conversationName;
        receipt.deliveries = List.of();
        receipt.selections = "";
        receipt.cancelled = cancelled;
        return receipt;
    }
    private AntmailBatch() { }
    private UUID id;
    private UUID sender;
    private UUID senderEpoch;
    private UUID conversation;
    /** "group" or "team"; absent in batches saved before team mail, which are always groups. */
    private String conversationKind;
    /** Group name or team owner name at send time, so history stays readable after leaving. */
    private String conversationName;
    private String selections;
    private List<Delivery> deliveries = new ArrayList<>();
    public AntmailBatch(UUID id, UUID sender, String selections, AntmailDirectory.Audience audience, AntmailDirectory.Accounts accounts) {
        this.id = id;
        this.sender = sender;
        this.selections = selections;
        this.conversation = audience.conversation();
        this.conversationKind = audience.conversation() == null ? null : audience.conversationKind();
        this.conversationName = audience.conversation() == null ? null : AntmailMessage.conversationNameOrNull(audience.conversationName());
        this.senderEpoch = audience.epochs().get(sender);
        for (UUID account : audience.accounts()) {
            UUID deliveryId = UUID.nameUUIDFromBytes((sender + "/" + id + "/" + account).getBytes(StandardCharsets.UTF_8));
            deliveries.add(new Delivery(account, accounts.address(account), deliveryId, audience.epochs().get(account), "PENDING"));
        }
    }
    public UUID id() { return id; }
    public UUID sender() { return sender; }
    public UUID conversation() { return conversation; }
    public String conversationName() { return conversation == null ? null : conversationName; }
    public String conversationKind() { return conversation == null ? null : conversationKind == null ? AntmailDirectory.CONVERSATION_GROUP : conversationKind; }
    public UUID senderEpoch() { return senderEpoch; }
    public String selections() { return selections; }
    public List<Delivery> deliveries() { return List.copyOf(deliveries); }
    public void outcome(UUID account, String status) {
        for (int i = 0; i < deliveries.size(); i++) {
            Delivery row = deliveries.get(i);
            if (row.account().equals(account) && !row.successful()) deliveries.set(i, row.withStatus(status));
        }
    }
    public int failedCount() { return (int) deliveries.stream().filter(value -> !value.successful()).count(); }
    public String summary() {
        return failedCount() == 0 ? "DELIVERED" : "FAILED // " + failedCount() + " / " + deliveries.size();
    }
    public String recipients() {
        if (deliveries.isEmpty()) return "0 recipients";
        return deliveries.get(0).address() + (deliveries.size() == 1 ? "" : " +" + (deliveries.size() - 1));
    }
    public String save() { return JSON.toJson(this); }
    public AntmailBatch copy() { return load(save()); }
    public static AntmailBatch load(String json) {
        AntmailBatch batch = JSON.fromJson(json, AntmailBatch.class);
        if (batch == null || batch.id == null || batch.sender == null || batch.selections == null
                || batch.selections.length() > 8192 || batch.deliveries == null
                || batch.conversationKind != null && !List.of(AntmailDirectory.CONVERSATION_GROUP, AntmailDirectory.CONVERSATION_TEAM).contains(batch.conversationKind) || batch.deliveries.size() > AntmailRecipients.MAX_RECIPIENTS) throw new IllegalArgumentException("invalid_batch");
        batch.conversationName = AntmailMessage.conversationNameOrNull(batch.conversationName);
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Delivery delivery : batch.deliveries) {
            if (delivery == null || delivery.account() == null || delivery.id() == null || delivery.status() == null
                    || !List.of("PENDING", "DELIVERED", "MAILBOX_FULL", "ADDRESS_NOT_FOUND", "MEMBERSHIP_CHANGED", "INSUFFICIENT_ANTCOINS").contains(delivery.status())
                    || !seen.add(delivery.account()) || !AntmailAddress.isValidAddress(delivery.address())) throw new IllegalArgumentException("invalid_batch");
        }
        return batch;
    }
}
