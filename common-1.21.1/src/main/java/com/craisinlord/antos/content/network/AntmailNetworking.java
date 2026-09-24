package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.antmail.AntmailAttachment;
import com.craisinlord.antos.content.antmail.AntmailDebug;
import com.craisinlord.antos.content.antmail.AntmailDraft;
import com.craisinlord.antos.content.antmail.AntmailWire;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AntmailNetworking {
    private static Consumer<Object> sender = payload -> { };
    private static volatile boolean senderConfigured;
    private static final AtomicBoolean senderErrorLogged = new AtomicBoolean();

    private AntmailNetworking() {
    }

    public static void setSender(Consumer<Object> sender) {
        AntmailNetworking.sender = sender;
        senderConfigured = sender != null;
    }

    public static void requestState() {
        requestState(AntmailStateRequestPayload.INBOX, 0, 0L);
    }

    public static void requestState(int folder, int page) {
        requestState(folder, page, 0L);
    }

    public static void requestState(int folder, int page, long knownVersion) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.STATE, "", "", "", "", "", ((long) folder << 32) | (page & 0xffffffffL), knownVersion));
    }

    public static void requestMessage(UUID messageId) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.MESSAGE, messageId.toString(), "", "", "", "", 0L, 0L));
    }

    public static void send(String recipient, String subject, String body, List<AntmailAttachment> attachments) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.SEND, recipient, subject, body, AntmailWire.encodeAttachments(attachments), "", 0L, 0L));
    }

    public static void markRead(UUID messageId) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.READ, messageId.toString(), "true", "", "", "", 0L, 0L));
    }

    public static void markUnread(UUID messageId) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.READ, messageId.toString(), "false", "", "", "", 0L, 0L));
    }

    public static void delete(UUID messageId, boolean sent) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.DELETE, messageId.toString(), Boolean.toString(sent), "", "", "", 0L, 0L));
    }

    public static void saveDraft(AntmailDraft draft) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.DRAFT, Integer.toString(AntmailDraftPayload.SAVE), AntmailWire.encodeTag(draft.toTag()), "", "", "", 0L, 0L));
    }

    public static void deleteDraft(UUID draftId) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.DRAFT, Integer.toString(AntmailDraftPayload.DELETE), draftId.toString(), "", "", "", 0L, 0L));
    }

    public static void retry(UUID messageId) {
        sendPayload(new AntmailAnternetPayload(AntmailAnternetPayload.RETRY, messageId.toString(), "", "", "", "", 0L, 0L));
    }

    private static void sendPayload(Object payload) {
        if (!senderConfigured) {
            if (senderErrorLogged.compareAndSet(false, true)) AntmailDebug.error("Network sender is not configured; dropping " + payload.getClass().getSimpleName(), new IllegalStateException("AntmailNetworking.setSender was not called"));
        }
        else sender.accept(payload);
    }
}


