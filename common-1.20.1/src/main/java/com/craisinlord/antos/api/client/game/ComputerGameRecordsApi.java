package com.craisinlord.antos.api.client.game;

import com.craisinlord.antos.content.computer.games.ComputerGameRecords;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class ComputerGameRecordsApi {
    private ComputerGameRecordsApi() { }

    public static void won(ResourceLocation mode, int seconds, int moves) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("wins", 1);
        if (seconds >= 0) values.put("time", seconds);
        if (moves >= 0) values.put("moves", moves);
        send(mode, values);
    }

    public static void score(ResourceLocation mode, int score) {
        if (score > 0) send(mode, Map.of("score", score));
    }

    private static void send(ResourceLocation mode, Map<String, Integer> values) {
        if (mode != null) ComputerNetworking.gameRecord(new ComputerGameRecords.Report(mode.toString(), values).encode());
    }
}
