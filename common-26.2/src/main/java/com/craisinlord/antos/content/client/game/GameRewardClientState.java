package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class GameRewardClientState {
    private static final AtomicReference<Update> LATEST = new AtomicReference<>();
    private static final AtomicLong NEXT_REVISION = new AtomicLong();

    public record Update(String error, String data, long revision) {}

    private GameRewardClientState() {}

    public static void clearAll() { LATEST.set(null); }

    public static void update(ComputerAccessResultPayload result) {
        if (!result.data().startsWith(ComputerAccessPayload.GAME_REWARD + "\0")) return;
        String[] fields = result.data().split("\u0000", 3);
        LATEST.set(new Update(fields.length > 1 ? fields[1] : "", fields.length > 2 ? fields[2] : "", NEXT_REVISION.incrementAndGet()));
    }

    public static Update get() { return LATEST.get(); }
}
