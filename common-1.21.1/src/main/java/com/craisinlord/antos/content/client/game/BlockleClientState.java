package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockleClientState {
    private static final Map<BlockPos, ComputerAccessResultPayload> RESULTS = new ConcurrentHashMap<>();

    private BlockleClientState() {}

    public static void update(ComputerAccessResultPayload result) {
        if (result.data().startsWith(ComputerAccessPayload.BLOCKLE_STATE + "\0")
                || result.data().startsWith(ComputerAccessPayload.BLOCKLE_GUESS + "\0")) RESULTS.put(result.pos(), result);
    }

    public static ComputerAccessResultPayload get(BlockPos pos) { return RESULTS.get(pos); }
}
