package com.craisinlord.antos.content.editor;

import java.util.ArrayList;
import java.util.List;

public final class AuthoringWire {
    public static final int CHUNK_CHARS = 4096;
    public static final int MAX_CHARS = 300_000;
    public static final int MAX_CHUNKS = (MAX_CHARS + CHUNK_CHARS - 2) / (CHUNK_CHARS - 1);
    private AuthoringWire() { }

    public record Chunk(String id, int index, int total, String data) {
        public Chunk {
            if (id == null || id.length() > 36 || index < 0 || total < 1 || total > MAX_CHUNKS || index >= total
                    || data == null || data.length() > CHUNK_CHARS) throw new IllegalArgumentException("Invalid editor packet");
        }
    }

    public static List<Chunk> split(String id, String text) {
        if (text.length() > MAX_CHARS) throw new IllegalArgumentException("Editor document exceeds transfer limit");
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + CHUNK_CHARS);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
            pieces.add(text.substring(start, end));
            start = end;
        }
        if (pieces.isEmpty()) pieces.add("");
        List<Chunk> result = new ArrayList<>(pieces.size());
        for (int index = 0; index < pieces.size(); index++) result.add(new Chunk(id, index, pieces.size(), pieces.get(index)));
        return List.copyOf(result);
    }

    public static final class Assembly {
        private final String id;
        private final int total;
        private final StringBuilder text = new StringBuilder();
        private final long started;
        private int next;

        public Assembly(Chunk first, long now) {
            if (first.index() != 0) throw new IllegalArgumentException("Editor transfer must start with its first packet");
            id = first.id();
            total = first.total();
            started = now;
        }

        public String accept(Chunk chunk, long now) {
            if (now - started > 15_000 || !id.equals(chunk.id()) || total != chunk.total() || next != chunk.index()
                    || text.length() + chunk.data().length() > MAX_CHARS) throw new IllegalArgumentException("Incomplete or expired editor transfer");
            text.append(chunk.data());
            next++;
            return next == total ? text.toString() : null;
        }

        public boolean expired(long now) { return now - started > 15_000; }
    }
}
