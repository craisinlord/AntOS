package com.craisinlord.antos.content.computer.games;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class GameDataCodec {
    public static final String GET = "get";
    public static final String PUT = "put";
    public static final String BEST = "best";
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_VALUE_LENGTH = 2048;
    public static final int MAX_ENTRIES = 128;

    private GameDataCodec() {}

    public static boolean validKey(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.')) return false;
        }
        return true;
    }

    public static boolean validValue(String value) {
        if (value == null || value.length() > MAX_VALUE_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\0') return false;
        }
        return true;
    }

    public static String request(String op, String key, String value) {
        return op + "\0" + key + "\0" + value;
    }

    public static String[] parseRequest(String raw) {
        if (raw == null || raw.equals(GET) || raw.isEmpty()) return new String[]{GET, "", ""};
        String[] parts = raw.split("\0", -1);
        if (parts.length != 3) return null;
        if (!parts[0].equals(PUT) && !parts[0].equals(BEST)) return null;
        if (!validKey(parts[1]) || !validValue(parts[2])) return null;
        if (parts[0].equals(BEST) && parseLong(parts[2]) == null) return null;
        return parts;
    }

    public static Long parseLong(String value) {
        if (value == null || value.isEmpty() || value.length() > 19) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static boolean apply(Map<String, String> entries, String op, String key, String value) {
        if (op.equals(GET)) return false;
        String current = entries.get(key);
        if (current == null && entries.size() >= MAX_ENTRIES) return false;
        if (op.equals(BEST)) {
            Long existing = parseLong(current);
            Long proposed = parseLong(value);
            if (proposed == null || existing != null && existing >= proposed) return false;
        } else if (value.equals(current)) {
            return false;
        }
        if (op.equals(PUT) && value.isEmpty()) return entries.remove(key) != null;
        entries.put(key, value);
        return true;
    }

    public static String encode(Map<String, String> entries) {
        StringBuilder out = new StringBuilder();
        new TreeMap<>(entries).forEach((key, value) -> {
            if (!validKey(key) || !validValue(value)) return;
            if (out.length() > 0) out.append('\n');
            out.append(key).append('=').append(value);
        });
        return out.toString();
    }

    public static Map<String, String> decode(String raw) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return entries;
        for (String line : raw.split("\n")) {
            int split = line.indexOf('=');
            if (split <= 0) continue;
            String key = line.substring(0, split);
            String value = line.substring(split + 1);
            if (validKey(key) && validValue(value) && entries.size() < MAX_ENTRIES) entries.put(key, value);
        }
        return entries;
    }
}
