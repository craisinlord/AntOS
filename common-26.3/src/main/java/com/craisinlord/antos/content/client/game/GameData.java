package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.ComputerWorkspaceClientKey;
import com.craisinlord.antos.content.computer.games.GameDataCodec;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GameData {
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    private GameData() {}

    public static void clearAll() {
        CACHE.clear();
        REQUESTED.clear();
    }

    public static void update(ComputerAccessResultPayload result) {
        String prefix = ComputerAccessPayload.GAME_DATA + "\0";
        if (!result.data().startsWith(prefix)) return;
        String[] fields = result.data().split("\0", 3);
        if (fields.length < 3 || !fields[1].isEmpty()) return;
        Map<String, String> received = GameDataCodec.decode(fields[2]);
        Map<String, String> local = entries();
        received.forEach((key, value) -> {
            Long incoming = GameDataCodec.parseLong(value);
            Long known = GameDataCodec.parseLong(local.get(key));
            if (key.endsWith(".best") && incoming != null && known != null && known > incoming) return;
            local.put(key, value);
        });
    }

    public static void request() {
        String workspace = ComputerWorkspaceClientKey.of();
        REQUESTED.add(workspace);
        ComputerNetworking.gameData(GameDataCodec.GET);
    }

    public static String get(String key, String fallback) {
        ensureRequested();
        String value = entries().get(key);
        return value == null ? fallback : value;
    }

    public static long getLong(String key, long fallback) {
        Long value = GameDataCodec.parseLong(get(key, null));
        return value == null ? fallback : value;
    }

    public static void put(String key, String value) {
        if (!GameDataCodec.validKey(key) || !GameDataCodec.validValue(value)) return;
        if (GameDataCodec.apply(entries(), GameDataCodec.PUT, key, value)) {
            ComputerNetworking.gameData(GameDataCodec.request(GameDataCodec.PUT, key, value));
        }
    }

    public static long best(String gameId) {
        return getLong(gameId + ".best", 0L);
    }

    public static void submitBest(String gameId, long score) {
        String key = gameId + ".best";
        if (score <= 0L || !GameDataCodec.validKey(key)) return;
        String value = Long.toString(score);
        if (GameDataCodec.apply(entries(), GameDataCodec.BEST, key, value)) {
            ComputerNetworking.gameData(GameDataCodec.request(GameDataCodec.BEST, key, value));
        }
    }

    private static void ensureRequested() {
        if (!REQUESTED.contains(ComputerWorkspaceClientKey.of())) request();
    }

    private static Map<String, String> entries() {
        return CACHE.computeIfAbsent(ComputerWorkspaceClientKey.of(), ignored -> new ConcurrentHashMap<>());
    }
}
