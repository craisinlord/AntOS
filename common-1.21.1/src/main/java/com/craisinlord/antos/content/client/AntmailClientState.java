package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.antmail.AntmailDebug;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntmailClientState {
    private static final int MAX_CACHED_DETAILS_PER_MAILBOX = 8;
    private static volatile AntmailAnternetResultPayload result;
    private static volatile AntmailAnternetResultPayload mailbox;
    private static volatile long version;
    private static final Map<java.util.UUID, AntmailMessage> DETAILS = new ConcurrentHashMap<>();

    private AntmailClientState() {
    }

    public static void update(AntmailAnternetResultPayload payload) {
        AntmailDebug.log("S2C result status=" + payload.status() + " detail=" + payload.detail()
                + " addressPresent=" + !payload.address().isBlank() + " dataChars=" + payload.data().length()
                + " version=" + payload.version());
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
                while (DETAILS.size() > MAX_CACHED_DETAILS_PER_MAILBOX) {
                    java.util.UUID oldest = DETAILS.keySet().iterator().next();
                    DETAILS.remove(oldest);
                }
            } catch (RuntimeException ignored) {
            }
        } else if (!payload.data().isBlank()) {
            mailbox = payload;
            version = payload.version();
        } else {
            mailbox = null;
            version = 0L;
        }
    }

    public static void clear() {
        result = null;
        mailbox = null;
        version = 0L;
        DETAILS.clear();
    }

    public static AntmailAnternetResultPayload get() {
        return result;
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
}


