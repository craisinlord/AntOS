package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerAccessClientState {
    private static final Map<BlockPos, ComputerAccessResultPayload> RESULTS = new ConcurrentHashMap<>();

    private ComputerAccessClientState() {
    }

    public static void update(ComputerAccessResultPayload result) {
        com.craisinlord.antos.content.client.game.BlockleClientState.update(result);
        ComputerTasksClientState.update(result);
        ComputerStructureLocatorClientState.update(result);
        ComputerTerminalClientState.update(result);
        if (result.data().startsWith(com.craisinlord.antos.content.network.ComputerAccessPayload.ARCHIVE_STATE + "\0")) {
            String[] envelope = result.data().split("\u0000", 3);
            if (envelope.length == 3) {
                com.craisinlord.antos.content.guide.ComputerGuideData.applyNetworkSnapshot(envelope[2]);
                ComputerArchiveUnlockClientState.update(result.pos(), envelope[2]);
            }
        }
        // Ignore operation responses; they do not update login state.
        if (result.data().isBlank()) RESULTS.put(result.pos(), result);
        ComputerFileSystemClientState.update(result);
    }

    public static void clear(BlockPos pos) {
        RESULTS.remove(pos);
        ComputerTasksClientState.clear(pos);
        ComputerArchiveUnlockClientState.clear(pos);
        ComputerTerminalClientState.clear(pos);
    }

    public static ComputerAccessResultPayload get(BlockPos pos) {
        return RESULTS.get(pos);
    }
}


