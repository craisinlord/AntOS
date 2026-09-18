package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.AntmailResultPayload;
import com.craisinlord.antos.content.antmail.AntmailDebug;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntmailClientState {
    private static final int MAX_CACHED_DETAILS_PER_MAILBOX = 8;
    private static final Map<BlockPos, AntmailResultPayload> RESULTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, AntmailResultPayload> MAILBOXES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> VERSIONS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Map<java.util.UUID, AntmailMessage>> DETAILS = new ConcurrentHashMap<>();

    private AntmailClientState() {
    }

    public static void update(AntmailResultPayload payload) {
        AntmailDebug.log("S2C result pos=" + payload.pos() + " status=" + payload.status() + " detail=" + payload.detail()
                + " addressPresent=" + !payload.address().isBlank() + " dataChars=" + payload.data().length()
                + " version=" + payload.version());
        RESULTS.put(payload.pos(), payload);
        if ("unchanged".equals(payload.detail())) {
            if (MAILBOXES.containsKey(payload.pos())) {
                VERSIONS.put(payload.pos(), payload.version());
            } else {
                // Without a cached snapshot, retry with a full request.
                AntmailDebug.log("received unchanged without cached mailbox at " + payload.pos() + "; forcing full snapshot on next poll");
                VERSIONS.remove(payload.pos());
            }
            return;
        } else if ("message_detail".equals(payload.detail()) && !payload.data().isBlank()) {
            try {
                AntmailMessage message = AntmailMessage.fromTag(com.craisinlord.antos.content.antmail.AntmailWire.decodeTag(payload.data()));
                Map<java.util.UUID, AntmailMessage> details = DETAILS.computeIfAbsent(payload.pos(), ignored -> new ConcurrentHashMap<>());
                details.put(message.id(), message);
                while (details.size() > MAX_CACHED_DETAILS_PER_MAILBOX) {
                    java.util.UUID oldest = details.keySet().iterator().next();
                    details.remove(oldest);
                }
            } catch (RuntimeException ignored) {
            }
        } else if (!payload.data().isBlank()) {
            MAILBOXES.put(payload.pos(), payload);
            VERSIONS.put(payload.pos(), payload.version());
        } else {
            // Clear cached mail on failure so another mailbox is not shown.
            MAILBOXES.remove(payload.pos());
            VERSIONS.remove(payload.pos());
        }
    }

    public static void clear(BlockPos pos) {
        RESULTS.remove(pos);
        MAILBOXES.remove(pos);
        VERSIONS.remove(pos);
        DETAILS.remove(pos);
    }

    public static AntmailResultPayload get(BlockPos pos) {
        return RESULTS.get(pos);
    }

    public static AntmailResultPayload getMailbox(BlockPos pos) {
        return MAILBOXES.get(pos);
    }

    public static long getVersion(BlockPos pos) {
        return VERSIONS.getOrDefault(pos, 0L);
    }

    public static AntmailMessage getMessage(BlockPos pos, java.util.UUID id) {
        Map<java.util.UUID, AntmailMessage> details = DETAILS.get(pos);
        return details == null ? null : details.get(id);
    }
}


