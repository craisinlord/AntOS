package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerTerminalClientState {
    private static final Map<BlockPos, ComputerAccessResultPayload> RESULTS = new ConcurrentHashMap<>();

    private ComputerTerminalClientState() { }

    public static void update(ComputerAccessResultPayload result) {
        if (result.data().startsWith(ComputerAccessPayload.TERMINAL_COMMAND + "\0")) {
            RESULTS.put(result.pos(), result);
        }
    }

    public static ComputerAccessResultPayload consume(BlockPos pos) {
        return RESULTS.remove(pos);
    }

    public static void clear(BlockPos pos) {
        RESULTS.remove(pos);
    }
}
