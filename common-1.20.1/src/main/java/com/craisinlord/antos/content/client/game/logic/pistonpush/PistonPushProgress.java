package com.craisinlord.antos.content.client.game.logic.pistonpush;

import java.util.Map;
import java.util.TreeMap;

public final class PistonPushProgress {
    public static final String KEY = "piston_push.progress";
    public static final int MAX_MOVES = 99999;
    public static final int UNLOCK_CLEARS = 4;

    private PistonPushProgress() {}

    public static Map<String, Integer> decode(String value) {
        Map<String, Integer> progress = new TreeMap<>();
        if (value == null || value.isEmpty()) return progress;
        for (String entry : value.split(";")) {
            int split = entry.indexOf('=');
            if (split <= 0 || split != entry.lastIndexOf('=')) continue;
            String id = entry.substring(0, split);
            String count = entry.substring(split + 1);
            if (!validId(id) || count.isEmpty() || count.length() > 5) continue;
            boolean digits = true;
            for (int index = 0; index < count.length(); index++) if (count.charAt(index) < '0' || count.charAt(index) > '9') digits = false;
            if (!digits) continue;
            int moves = Integer.parseInt(count);
            if (moves <= 0) continue;
            progress.merge(id, moves, Math::min);
        }
        return progress;
    }

    public static String encode(Map<String, Integer> progress) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(progress).entrySet()) {
            Integer moves = entry.getValue();
            if (!validId(entry.getKey()) || moves == null || moves <= 0 || moves > MAX_MOVES) continue;
            if (!out.isEmpty()) out.append(';');
            out.append(entry.getKey()).append('=').append(moves);
        }
        return out.toString();
    }

    public static boolean record(Map<String, Integer> progress, String id, int moves) {
        if (!validId(id) || moves <= 0) return false;
        int clamped = Math.min(MAX_MOVES, moves);
        Integer known = progress.get(id);
        if (known != null && known <= clamped) return false;
        progress.put(id, clamped);
        return true;
    }

    public static boolean solved(Map<String, Integer> progress, String id) {
        return progress.containsKey(id);
    }

    public static boolean atPar(Map<String, Integer> progress, PistonPushLevels.Level level) {
        Integer best = progress.get(level.id());
        return best != null && best <= level.par();
    }

    public static int clears(Map<String, Integer> progress, int world) {
        int count = 0;
        for (PistonPushLevels.Level level : PistonPushLevels.world(world)) if (solved(progress, level.id())) count++;
        return count;
    }

    public static boolean unlocked(Map<String, Integer> progress, int world) {
        if (world <= 0) return true;
        if (world >= PistonPushLevels.WORLDS) return false;
        return unlocked(progress, world - 1) && clears(progress, world - 1) >= UNLOCK_CLEARS;
    }

    private static boolean validId(String id) {
        if (id.isEmpty() || id.length() > 16) return false;
        for (int index = 0; index < id.length(); index++) {
            char c = id.charAt(index);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) return false;
        }
        return true;
    }
}
