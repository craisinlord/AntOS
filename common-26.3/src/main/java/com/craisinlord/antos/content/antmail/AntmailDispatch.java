package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import net.minecraft.server.MinecraftServer;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;

public final class AntmailDispatch {
    private AntmailDispatch() { }
    public interface Access {
        AntmailDirectory directory();
        AntmailDirectory.Accounts accounts();
        AntmailMailbox mailbox(AntmailAddress address);
        long now();
        boolean coinsEnabled();
        boolean debit(UUID account, long amount);
        void credit(UUID account, long amount);
        void touch(AntmailAddress address);
        void notify(AntmailMessage message);
        default AntmailDirectory.Teams teams() { return AntmailDirectory.Teams.NONE; }
    }
    private static Access access(MinecraftServer server, AntmailServerData data) {
        return new Access() {
            public AntmailDirectory directory() { return data.directory(); }
            public AntmailDirectory.Accounts accounts() { return AntmailDispatch.accounts(server); }
            public AntmailMailbox mailbox(AntmailAddress address) { return data.mailboxOrCreate(address); }
            public long now() { return server.overworld().getGameTime(); }
            public boolean coinsEnabled() { return com.craisinlord.antos.config.AntOSSettings.appEnabled("ANTAZON"); }
            public boolean debit(UUID account, long amount) { return AntazonServerData.access(server).debit(account, amount); }
            public void credit(UUID account, long amount) { AntazonServerData.access(server).credit(account, amount); }
            public void touch(AntmailAddress address) { data.touchMailbox(address); }
            public void notify(AntmailMessage message) { AntmailNotifier.notify(server, message); }
            public AntmailDirectory.Teams teams() { return AntmailDispatch.teams(server); }
        };
    }
    /** Live view of AntOS progression teams for team mail. */
    public static AntmailDirectory.Teams teams(MinecraftServer server) {
        var data = ComputerWorkspaceData.access(server);
        return new AntmailDirectory.Teams() {
            public UUID teamOf(UUID account) { return data.teamIdOf(account); }
            public AntmailDirectory.TeamView team(UUID id) {
                var team = data.teamMailAudience(id);
                return team == null ? null : new AntmailDirectory.TeamView(team.id(), team.ownerName(), team.ownerId(), team.epochs());
            }
        };
    }
    public static AntmailDirectory.Accounts accounts(MinecraftServer server) {
        var data = ComputerWorkspaceData.access(server);
        return new AntmailDirectory.Accounts() {
            public UUID resolve(String reference) {
                if (reference == null) return null;
                try {
                    UUID id = UUID.fromString(reference);
                    return data.account(id) == null ? null : id;
                } catch (IllegalArgumentException ignored) { }
                try {
                    var account = data.accountByUsername(AntmailAddress.parse(reference.contains("@") ? reference : AntmailAddress.ofUsername(reference).fullAddress()).username());
                    return account == null ? null : account.accountId();
                } catch (IllegalArgumentException ignored) { return null; }
            }
            public List<AntmailDirectory.Person> search(String query) {
                return data.accounts().stream().filter(account -> (account.username() + "@antmail.com " + account.displayUsername()).toLowerCase(java.util.Locale.ROOT).contains(query))
                        .limit(AntmailDirectory.MAX_CONTACTS).map(account -> new AntmailDirectory.Person(account.accountId(), AntmailAddress.ofUsername(account.username()).fullAddress(), "")).toList();
            }
            public String address(UUID id) {
                var account = data.account(id);
                return account == null ? null : AntmailAddress.ofUsername(account.username()).fullAddress();
            }
        };
    }
    public static AntmailMessage send(MinecraftServer server, AntmailServerData data, UUID actor, AntmailAddress sender,
                                      UUID requestId, String recipientInput, String subject, String body,
                                      List<AntmailAttachment> attachments, IntPredicate budget) {
        return send(access(server, data), actor, sender, requestId, recipientInput, subject, body, attachments, budget);
    }
    public static AntmailMessage send(Access access, UUID actor, AntmailAddress sender, UUID requestId, String recipientInput,
                                      String subject, String body, List<AntmailAttachment> attachments, IntPredicate budget) {
        AntmailMailbox mailbox = access.mailbox(sender);
        AntmailMessage existing = mailbox.batch(requestId);
        if (existing != null) {
            if (mailbox.removeDraft(requestId)) access.touch(sender);
            if (existing.batch().cancelled()) throw new IllegalArgumentException("send_deleted");
            return existing;
        }
        if (!mailbox.canRecordBatch() || mailbox.sent().size() >= AntmailValidation.MAX_MAILBOX_MESSAGES && mailbox.findSent(requestId) == null) throw new IllegalArgumentException("sent_mailbox_full");
        var selections = AntmailRecipients.parse(recipientInput);
        boolean coins = attachments.stream().anyMatch(AntmailAttachment.Antcoins.class::isInstance);
        if (coins && (!AntmailRecipients.direct(recipientInput) || !access.coinsEnabled())) throw new IllegalArgumentException("direct_antcoins_only");
        if (attachments.stream().filter(AntmailAttachment.Antcoins.class::isInstance).count() > 1) throw new IllegalArgumentException("invalid_antcoins");
        var accounts = access.accounts();
        var audience = access.directory().expand(actor, selections, accounts, access.teams());
        AntmailAddress first = audience.accounts().isEmpty() ? sender : AntmailAddress.parse(accounts.address(audience.accounts().get(0)));
        var extracted = AntmailRenderMarkers.extract(body, attachments);
        AntmailMessage message = new AntmailMessage(requestId, sender, first, subject, extracted.body(), access.now(), extracted.attachments(), false);
        message.markFromPlayer();
        message.setBatch(new AntmailBatch(requestId, actor, AntmailRecipients.format(selections), audience, accounts));
        if (AntmailWire.encodeTag(message.toTag()).length() > 64000) throw new IllegalArgumentException("message_too_large");
        // Charge the rate budget only once the send is known to be valid, so a rejected send costs nothing per recipient.
        if (!budget.test(audience.accounts().size())) throw new IllegalArgumentException("rate_limited");
        mailbox.recordBatch(message);
        mailbox.addSent(message);
        mailbox.removeDraft(requestId);
        attempt(access, message);
        return message;
    }
    public static AntmailMessage retry(MinecraftServer server, AntmailServerData data, UUID actor, AntmailAddress sender, UUID id, IntPredicate budget) {
        return retry(access(server, data), actor, sender, id, budget);
    }
    public static AntmailMessage retry(Access access, UUID actor, AntmailAddress sender, UUID id, IntPredicate budget) {
        AntmailMessage message = access.mailbox(sender).batch(id);
        if (message == null || message.batch() == null || !message.batch().sender().equals(actor)) throw new IllegalArgumentException("not_found");
        AntmailBatch batch = message.batch();
        if (batch.cancelled()) throw new IllegalArgumentException("send_deleted");
        if (!access.directory().retryAllowed(batch.conversationKind(), batch.conversation(), actor, batch.senderEpoch(), access.teams())) throw new IllegalArgumentException("membership_changed");
        if (!budget.test(batch.failedCount())) throw new IllegalArgumentException("rate_limited");
        attempt(access, message);
        return message;
    }
    private static void attempt(Access access, AntmailMessage message) {
        AntmailBatch batch = message.batch();
        var accounts = access.accounts();
        long coins = message.attachments().stream().filter(AntmailAttachment.Antcoins.class::isInstance).mapToLong(value -> ((AntmailAttachment.Antcoins) value).amount()).sum();
        try {
            for (var row : batch.deliveries()) {
                if (row.successful()) continue;
                if (!access.directory().retryAllowed(batch.conversationKind(), batch.conversation(), row.account(), row.epoch(), access.teams())) {
                    batch.outcome(row.account(), "MEMBERSHIP_CHANGED");
                    continue;
                }
                String current = accounts.address(row.account());
                if (current == null) { batch.outcome(row.account(), "ADDRESS_NOT_FOUND"); continue; }
                AntmailAddress address = AntmailAddress.parse(current);
                AntmailMailbox destination = access.mailbox(address);
                if (destination.hasReceived(row.id())) { batch.outcome(row.account(), "DELIVERED"); continue; }
                if (destination.inboxSize() >= AntmailValidation.MAX_MAILBOX_MESSAGES) { batch.outcome(row.account(), "MAILBOX_FULL"); continue; }
                if (coins > 0 && !access.debit(batch.sender(), coins)) { batch.outcome(row.account(), "INSUFFICIENT_ANTCOINS"); continue; }
                AntmailMessage incoming = new AntmailMessage(row.id(), message.sender(), address, message.subject(), message.body(), message.createdAt(), message.attachments(), false);
                incoming.markFromPlayer();
                incoming.setConversation(batch.conversation(), batch.conversationKind(), batch.conversationName());
                incoming.setLogicalSend(batch.id());
                if (!destination.addIncoming(incoming)) {
                    if (coins > 0) access.credit(batch.sender(), coins);
                    batch.outcome(row.account(), "MAILBOX_FULL");
                    continue;
                }
                if (coins > 0) access.credit(row.account(), coins);
                batch.outcome(row.account(), "DELIVERED");
                access.touch(address);
                try { access.notify(incoming); } catch (RuntimeException exception) { AntmailDebug.error("Antmail notification failed after delivery", exception); }
            }
        } finally {
            message.setDeliveryStatus(batch.summary());
            access.touch(message.sender());
        }
    }
}
