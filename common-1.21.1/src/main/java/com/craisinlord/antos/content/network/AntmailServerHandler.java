package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailDebug;
import com.craisinlord.antos.content.antmail.AntmailAttachment;
import com.craisinlord.antos.content.antmail.AntmailDeliveryResult;
import com.craisinlord.antos.content.antmail.AntmailDraft;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.antmail.AntmailValidation;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class AntmailServerHandler {
    private static final int MAX_RESULT_DATA_CHARS = AntmailAnternetResultPayload.MAX_DATA_CHARS;
    private static BiConsumer<ServerPlayer, AntmailAnternetResultPayload> resultSender = (player, payload) -> { };
    private static volatile boolean resultSenderConfigured;

    private AntmailServerHandler() {
    }

    public static void setResultSender(BiConsumer<ServerPlayer, AntmailAnternetResultPayload> sender) {
        resultSender = sender;
        resultSenderConfigured = sender != null;
    }

    public static void handle(ServerPlayer player, AntmailAnternetPayload payload) {
        if (payload.first().length() > 65536 || payload.second().length() > 65536 || payload.third().length() > 65536 || payload.fourth().length() > 65536 || payload.fifth().length() > 65536) return;
        var account = AnternetAccountHandler.session(player);
        if (account == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        switch (payload.action()) {
            case AntmailAnternetPayload.STATE -> {
                int folder = (int) (payload.number() >> 32);
                int page = (int) payload.number();
                handleState(player, new AntmailStateRequestPayload(folder, page, payload.version()), payload.first(),
                        Boolean.parseBoolean(payload.second()), Boolean.parseBoolean(payload.third()));
            }
            case AntmailAnternetPayload.MESSAGE -> handle(player, new AntmailMessageRequestPayload(payload.first()));
            case AntmailAnternetPayload.SEND -> handle(player, new AntmailSendPayload(payload.first(), payload.second(), payload.third(), payload.fourth()));
            case AntmailAnternetPayload.READ -> handle(player, new AntmailReadPayload(payload.first(), Boolean.parseBoolean(payload.second())));
            case AntmailAnternetPayload.DELETE -> handle(player, new AntmailDeletePayload(payload.first(), Boolean.parseBoolean(payload.second())));
            case AntmailAnternetPayload.DRAFT -> {
                try { handle(player, new AntmailDraftPayload(Integer.parseInt(payload.first()), payload.second())); }
                catch (NumberFormatException exception) { result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), "", "", "invalid_action", ""); }
            }
            case AntmailAnternetPayload.RETRY -> handle(player, new AntmailRetryPayload(payload.first()));
            case AntmailAnternetPayload.ARCHIVE -> handleMove(player, payload.first(), Boolean.parseBoolean(payload.second()) ? 1 : 0);
            case AntmailAnternetPayload.TRASH -> handleMove(player, payload.first(), parseFolder(payload.second(), 0), true);
            case AntmailAnternetPayload.RESTORE -> handleRestore(player, payload.first(), parseFolder(payload.second(), 0));
            case AntmailAnternetPayload.PERMANENT_DELETE -> handlePermanentDelete(player, payload.first());
            case AntmailAnternetPayload.UNARCHIVE -> handleUnarchive(player, payload.first(), parseFolder(payload.second(), 0));
            case AntmailAnternetPayload.UPDATE_PROFILE -> handleProfileUpdate(player, payload.first(), payload.second());
            default -> result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "invalid_action", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailStateRequestPayload payload) {
        handleState(player, payload, "", false, false);
    }

    private static void handleState(ServerPlayer player, AntmailStateRequestPayload payload, String query,
                                    boolean unreadOnly, boolean attachmentsOnly) {
        AntmailDebug.log("state request received player=" + player.getGameProfile().getName()
                + " folder=" + payload.folder() + " page=" + payload.page() + " knownVersion=" + payload.knownVersion());
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            AntmailDebug.log("state request rejected: computer missing, out of reach, unauthenticated, or owned by another user");
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        AntmailMailbox mailbox = data.mailboxOrCreate(address);
        if (payload.knownVersion() == data.mailboxVersion(address)) {
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "unchanged", "");
            return;
        }
        int folder = payload.folder() < AntmailStateRequestPayload.INBOX || payload.folder() > AntmailStateRequestPayload.TRASH
                ? AntmailStateRequestPayload.INBOX : payload.folder();
        int page = 0;
        try {
            String encoded = encodeSnapshot(data, mailbox, address, folder, page, query, unreadOnly, attachmentsOnly);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "", encoded);
        } catch (RuntimeException exception) {
            AntmailDebug.error("Failed to encode state for " + address.fullAddress(), exception);
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), "", "state_encode_failed", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailMessageRequestPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            AntmailMessage message = mailbox.findInbox(id);
            if (message == null) message = mailbox.findSent(id);
            if (message == null) message = mailbox.findArchived(id);
            if (message == null) message = mailbox.findTrash(id);
            if (message == null) {
                result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "not_found", "");
                return;
            }
            var detail = message.toTag();
            data.appendProfiles(detail, address);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), payload.messageId(), "message_detail", AntmailWire.encodeTag(detail));
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailSendPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress sender = addressFor(player, data);
        if (sender == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unconfigured", "");
            return;
        }
        long antcoins = 0L;
        UUID senderAccount = computer.workspaceOwner() == null ? player.getUUID() : computer.workspaceOwner();
        boolean debited = false;
        try {
            AntmailAddress recipient = AntmailAddress.parse(payload.recipient());
            List<AntmailAttachment> attachments = AntmailWire.decodeAttachments(payload.attachments());
            if (!com.craisinlord.antos.config.AntOSSettings.appEnabled("ANTAZON")
                    && attachments.stream().anyMatch(AntmailAttachment.Antcoins.class::isInstance)) {
                throw new IllegalArgumentException("antcoin attachments are disabled");
            }
            long transfer = attachments.stream().filter(AntmailAttachment.Antcoins.class::isInstance).mapToLong(value -> ((AntmailAttachment.Antcoins) value).amount()).sum();
            if (attachments.stream().filter(AntmailAttachment.Antcoins.class::isInstance).count() > 1) throw new IllegalArgumentException("only one antcoin transfer is allowed");
            UUID recipientAccount = antcoinAccount(player.server, recipient);
            if (transfer > 0 && recipientAccount == null) throw new IllegalArgumentException("recipient has no AntOS account");
            AntmailMessage message = AntmailMessage.create(sender, recipient, payload.subject(), payload.body(), player.serverLevel().getGameTime(), attachments);
            message.markFromPlayer();
            antcoins = transfer;
            if (antcoins > 0 && !AntazonServerData.access(player.server).debit(senderAccount, antcoins)) throw new IllegalArgumentException("insufficient antcoins");
            debited = antcoins > 0;
            AntmailDeliveryResult delivery = data.deliver(player.server, message);
            if (antcoins > 0 && delivery.status() != AntmailDeliveryResult.Status.DELIVERED && delivery.status() != AntmailDeliveryResult.Status.QUEUED) {
                AntazonServerData.access(player.server).credit(senderAccount, antcoins);
                debited = false;
            } else if (antcoins > 0) {
                AntazonServerData.access(player.server).credit(recipientAccount, antcoins);
            }
            result(player, delivery.status().ordinal(), sender.fullAddress(), message.id().toString(), delivery.detail(), "");
        } catch (RuntimeException exception) {
            if (debited) AntazonServerData.access(player.server).credit(senderAccount, antcoins);
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), "", "", "invalid_message", "");
        }
    }

    private static void handleProfileUpdate(ServerPlayer player, String displayNameInput, String avatarItemInput) {
        if (authorizedComputer(player) == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        String displayName = net.minecraft.ChatFormatting.stripFormatting(displayNameInput == null ? "" : displayNameInput)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        String avatarItem = avatarItemInput == null ? "" : avatarItemInput.trim();
        if (displayName.length() > 32) {
            sendProfileUpdateResult(player, data, address, "invalid_display_name", AntmailDeliveryResult.Status.MESSAGE_INVALID);
            return;
        }
        if (!avatarItem.isBlank()) {
            if (avatarItem.length() > 128) {
                sendProfileUpdateResult(player, data, address, "invalid_item_id", AntmailDeliveryResult.Status.MESSAGE_INVALID);
                return;
            }
            if (!avatarItem.contains(":")) avatarItem = "minecraft:" + avatarItem;
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.resources.ResourceLocation.tryParse(avatarItem);
            if (itemId == null || net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
                sendProfileUpdateResult(player, data, address, "invalid_item_id", AntmailDeliveryResult.Status.MESSAGE_INVALID);
                return;
            }
            avatarItem = itemId.toString();
        }
        data.setProfile(address, displayName, avatarItem, player.server);
        sendProfileUpdateResult(player, data, address, "profile_saved", AntmailDeliveryResult.Status.DELIVERED);
    }

    private static void sendProfileUpdateResult(ServerPlayer player, AntmailServerData data, AntmailAddress address,
                                                String detail, AntmailDeliveryResult.Status status) {
        result(player, status.ordinal(), address.fullAddress(), "", detail, "");
    }

    private static UUID antcoinAccount(net.minecraft.server.MinecraftServer server, AntmailAddress address) {
        return AntmailServerData.access(server).accountIdForAddress(server, address);
    }

    public static void handle(ServerPlayer player, AntmailReadPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            AntmailMessage message = mailbox.findInbox(id);
            boolean newlyRead = payload.read() && message != null && !message.read();
            boolean changed = payload.read() ? mailbox.markRead(id) : markUnread(mailbox, id);
            if (newlyRead && message != null && !message.definitionId().isBlank()) {
                try {
                    com.craisinlord.antos.content.computer.ComputerTasks.recordEvent(player, computer, "mail_read", net.minecraft.resources.ResourceLocation.parse(message.definitionId()));
                    ComputerAccessHandler.sendArchive(player, computer);
                } catch (RuntimeException ignored) { }
                com.craisinlord.antos.content.antmail.AntmailEventData.onMailRead(player, address, message);
            }
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), changed ? "" : "not_found", "");
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailDeletePayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = payload.sent() ? mailbox.removeSent(id) : mailbox.removeInbox(id);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), changed ? "" : "not_found", "");
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailDraftPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        AntmailMailbox mailbox = data.mailboxOrCreate(address);
        try {
            if (payload.action() == AntmailDraftPayload.SAVE) {
                AntmailDraft draft = AntmailDraft.fromTag(AntmailWire.decodeTag(payload.data()));
                AntmailDraft stored = new AntmailDraft(draft.id(), address, draft.recipient(), draft.subject(), draft.body(), draft.attachments());
                if (!mailbox.saveDraft(stored)) throw new IllegalArgumentException("draft_limit");
            } else if (payload.action() == AntmailDraftPayload.DELETE) {
                if (!mailbox.removeDraft(UUID.fromString(payload.data()))) throw new IllegalArgumentException("not_found");
            } else {
                throw new IllegalArgumentException("invalid_action");
            }
            data.touchMailbox(address);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "draft_saved", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), address.fullAddress(), "", "draft_failed", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailRetryPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress sender = addressFor(player, data);
        if (sender == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            AntmailDeliveryResult delivery = data.retry(player.server, sender, UUID.fromString(payload.messageId()));
            result(player, delivery == null ? AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal() : delivery.status().ordinal(), sender.fullAddress(), payload.messageId(), delivery == null ? "not_found" : delivery.detail(), "");
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), sender.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    private static int parseFolder(String value, int fallback) {
        try { return Math.max(0, Math.min(4, Integer.parseInt(value))); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static void handleMove(ServerPlayer player, String messageId, int sourceFolder) {
        handleMove(player, messageId, sourceFolder, false);
    }

    private static void handleMove(ServerPlayer player, String messageId, int sourceFolder, boolean toTrash) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = toTrash ? mailbox.moveToTrash(id, sourceFolder) : mailbox.moveToArchive(id, sourceFolder == AntmailStateRequestPayload.SENT);
            if (changed) data.touchMailbox(address);
            int folder = sourceFolder >= AntmailStateRequestPayload.ARCHIVE ? sourceFolder : AntmailStateRequestPayload.INBOX;
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? (toTrash ? "moved_to_trash" : "archived") : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static void handleRestore(ServerPlayer player, String messageId, int destinationFolder) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = mailbox.restoreFromTrash(id, destinationFolder);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? "restored" : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static void handlePermanentDelete(ServerPlayer player, String messageId) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = mailbox.removeTrash(id);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? "permanently_deleted" : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static void handleUnarchive(ServerPlayer player, String messageId, int destinationFolder) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = mailbox.restoreFromArchive(id, destinationFolder);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? "restored_to_inbox" : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static String encodeSnapshot(AntmailServerData data, AntmailMailbox mailbox, AntmailAddress address, int folder, int page,
                                         String query, boolean unreadOnly, boolean attachmentsOnly) {
        boolean draftAttachments = true;
        int maxRows = Integer.MAX_VALUE;
        while (true) {
            var snapshot = mailbox.toPageTag(folder, page, query, unreadOnly, attachmentsOnly, draftAttachments, maxRows);
            data.appendProfiles(snapshot, address);
            String encoded = AntmailWire.encodeTag(snapshot);
            if (encoded.length() <= MAX_RESULT_DATA_CHARS) return encoded;
            int rows = snapshot.getInt("PageSize");
            if (draftAttachments) draftAttachments = false;
            else if (rows > 0) maxRows = rows / 2;
            else throw new IllegalArgumentException("Antmail snapshot does not fit the result packet");
        }
    }

    private static ComputerWorkspace authorizedComputer(ServerPlayer player) {
        ComputerWorkspace computer = computer(player);
        return computer != null && computer.canUseFileSystem(player) ? computer : null;
    }

    private static boolean markUnread(AntmailMailbox mailbox, UUID id) {
        for (com.craisinlord.antos.content.antmail.AntmailMessage message : mailbox.inbox()) {
            if (message.id().equals(id)) {
                message.markUnread();
                return true;
            }
        }
        return false;
    }

    private static ComputerWorkspace computer(ServerPlayer player) {
        var account = AnternetAccountHandler.session(player);
        if (account != null) return new com.craisinlord.antos.content.computer.AccountWorkspace(player, account);
        return null;
    }

    private static AntmailAddress addressFor(ServerPlayer player, AntmailServerData data) {
        return data.addressFor(player);
    }

    private static void result(ServerPlayer player, int status, String address, String messageId, String detail, String data) {
        long version = 0L;
        try {
            if (!address.isBlank()) version = AntmailServerData.access(player.server).mailboxVersion(AntmailAddress.parse(address));
        } catch (IllegalArgumentException ignored) {
        }
        AntmailDebug.log("sending result player=" + player.getGameProfile().getName() + " status=" + status
                + " detail=" + detail + " addressPresent=" + !address.isBlank() + " dataChars=" + data.length() + " version=" + version);
        if (data.length() > MAX_RESULT_DATA_CHARS) {
            AntmailDebug.error("Dropping oversized Antmail result for " + address, new IllegalStateException("dataChars=" + data.length()));
            status = AntmailDeliveryResult.Status.FAILED.ordinal();
            detail = "result_too_large";
            data = "";
        }
        if (!resultSenderConfigured) {
            AntmailDebug.error("Result sender is not configured; dropping response", new IllegalStateException("AntmailServerHandler.setResultSender was not called"));
            return;
        }
        resultSender.accept(player, new AntmailAnternetResultPayload(status, address, messageId, detail, data, version));
    }
}


