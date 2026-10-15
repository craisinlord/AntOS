package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockleClientState {
    private static final Map<String, Update> RESULTS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_REVISION = new AtomicLong();

    public record Update(ComputerAccessResultPayload result, long revision) {}

    private BlockleClientState() {}
    public static void clearAll() { RESULTS.clear(); }

    public static void update(ComputerAccessResultPayload result) {
        if (result.data().startsWith(ComputerAccessPayload.BLOCKLE_STATE + "\0")
                || result.data().startsWith(ComputerAccessPayload.BLOCKLE_GUESS + "\0")) {
            RESULTS.put(com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of(), new Update(result, NEXT_REVISION.incrementAndGet()));
        }
    }

    public static Update get() { return RESULTS.get(com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of()); }
}
