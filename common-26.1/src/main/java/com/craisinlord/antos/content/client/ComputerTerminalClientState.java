package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerTerminalClientState {
    private static final Map<String, ComputerAccessResultPayload> RESULTS = new ConcurrentHashMap<>();

    private ComputerTerminalClientState() { }
    public static void clearAll() { RESULTS.clear(); }

    public static void update(ComputerAccessResultPayload result) {
        if (result.data().startsWith(ComputerAccessPayload.TERMINAL_COMMAND + "\0")) {
            RESULTS.put(ComputerWorkspaceClientKey.of(), result);
        }
    }

    public static ComputerAccessResultPayload consume() {
        return RESULTS.remove(ComputerWorkspaceClientKey.of());
    }

    public static void clear() {
        RESULTS.remove(ComputerWorkspaceClientKey.of());
    }
}
