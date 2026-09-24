package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessResultPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerAccessClientState {
    private static final Map<String, ComputerAccessResultPayload> RESULTS = new ConcurrentHashMap<>();

    private ComputerAccessClientState() {
    }

    public static void clearAll() {
        RESULTS.clear();
        ComputerTasksClientState.clearAll();
        ComputerStructureLocatorClientState.clearAll();
        ComputerTerminalClientState.clearAll();
        AntazonClientState.clearAll();
        ComputerArchiveUnlockClientState.clearAll();
        ComputerFileSystemClientState.clearAll();
        com.craisinlord.antos.content.client.game.BlockleClientState.clearAll();
    }

    public static void update(ComputerAccessResultPayload result) {
        com.craisinlord.antos.content.client.game.BlockleClientState.update(result);
        ComputerTasksClientState.update(result);
        ComputerStructureLocatorClientState.update(result);
        ComputerTerminalClientState.update(result);
        AntazonClientState.update(result);
        if (result.data().startsWith(com.craisinlord.antos.content.network.ComputerAccessPayload.ARCHIVE_STATE + "\0")) {
            String[] envelope = result.data().split("\u0000", 3);
            if (envelope.length == 3) {
                com.craisinlord.antos.content.guide.ComputerGuideData.applyNetworkSnapshot(envelope[2]);
                ComputerArchiveUnlockClientState.update(envelope[2]);
            }
        }
        // Ignore operation responses; they do not update login state.
        if (result.data().isBlank()) RESULTS.put(ComputerWorkspaceClientKey.of(), result);
        ComputerFileSystemClientState.update(result);
    }

    public static void updateAnternet(com.craisinlord.antos.content.network.AnternetComputerResultPayload result) {
        update(new ComputerAccessResultPayload(result.result(), result.hasPassword(), result.authenticated(), result.data()));
    }

    public static void clear() {
        RESULTS.remove(ComputerWorkspaceClientKey.of());
        ComputerTasksClientState.clear();
        ComputerArchiveUnlockClientState.clear();
        ComputerTerminalClientState.clear();
        AntazonClientState.clear();
    }

    public static ComputerAccessResultPayload get() {
        return RESULTS.get(ComputerWorkspaceClientKey.of());
    }
}


