package com.craisinlord.antos.content.client.game.logic.pistonpush;

import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Block;

import java.util.Arrays;

public final class PistonPushSolver {
    public static final int DEFAULT_CAP = 3_000_000;
    private static final Block[] TYPES = Block.values();

    private PistonPushSolver() {}

    public static int solve(String[] rows, int cap) {
        PistonPushBoard board = PistonPushBoard.parse(rows);
        int count = board.blockCount();
        if (count > 5 || board.width() * board.height() > 255) throw new IllegalArgumentException("board too large for solver");
        long start = encode(board);
        LongSet seen = new LongSet(1 << 20);
        seen.add(start);
        long[] frontier = {start};
        int depth = 0;
        int[] cells = new int[count];
        Block[] types = new Block[count];
        while (frontier.length > 0) {
            long[] next = new long[Math.max(16, frontier.length * 2)];
            int size = 0;
            for (long state : frontier) {
                for (int direction = 0; direction < 4; direction++) {
                    decode(state, board, cells, types);
                    PistonPushRules.MoveResult result = PistonPushRules.move(board, direction);
                    if (!result.moved()) continue;
                    if (result.solved()) return depth + 1;
                    long key = encode(board);
                    if (!seen.add(key)) continue;
                    if (seen.size() > cap) throw new IllegalStateException("state cap exceeded");
                    if (size == next.length) next = Arrays.copyOf(next, size * 2);
                    next[size++] = key;
                }
            }
            frontier = Arrays.copyOf(next, size);
            depth++;
        }
        return -1;
    }

    public static String path(String[] rows, int cap) {
        int best = solve(rows, cap);
        if (best < 0) return null;
        return search(PistonPushBoard.parse(rows), best, new StringBuilder());
    }

    private static String search(PistonPushBoard board, int left, StringBuilder taken) {
        if (board.solved()) return taken.toString();
        if (left == 0) return null;
        for (int direction = 0; direction < 4; direction++) {
            PistonPushBoard.Snapshot before = board.snapshot();
            PistonPushRules.MoveResult result = PistonPushRules.move(board, direction);
            if (result.moved()) {
                PistonPushBoard copy = board.copy();
                int remaining = solveFrom(copy, left - 1);
                if (remaining == left - 1) {
                    taken.append("URDL".charAt(direction));
                    String found = search(board, left - 1, taken);
                    if (found != null) return found;
                    taken.setLength(taken.length() - 1);
                }
            }
            board.restore(before);
        }
        return null;
    }

    private static int solveFrom(PistonPushBoard board, int limit) {
        if (board.solved()) return 0;
        int count = board.blockCount();
        int[] cells = new int[count];
        Block[] types = new Block[count];
        long start = encode(board);
        LongSet seen = new LongSet(1 << 16);
        seen.add(start);
        long[] frontier = {start};
        int depth = 0;
        while (frontier.length > 0 && depth < limit) {
            long[] next = new long[Math.max(16, frontier.length * 2)];
            int size = 0;
            for (long state : frontier) {
                for (int direction = 0; direction < 4; direction++) {
                    decode(state, board, cells, types);
                    PistonPushRules.MoveResult result = PistonPushRules.move(board, direction);
                    if (!result.moved()) continue;
                    if (result.solved()) return depth + 1;
                    long key = encode(board);
                    if (!seen.add(key)) continue;
                    if (size == next.length) next = Arrays.copyOf(next, size * 2);
                    next[size++] = key;
                }
            }
            frontier = Arrays.copyOf(next, size);
            depth++;
        }
        return -1;
    }

    static long encode(PistonPushBoard board) {
        long key = board.player();
        int width = board.width();
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < width; x++) {
                Block block = board.block(x, y);
                if (block == Block.NONE) continue;
                key = (key << 10) | ((long) (y * width + x) << 2) | block.ordinal();
            }
        }
        return key;
    }

    static void decode(long key, PistonPushBoard board, int[] cells, Block[] types) {
        for (int index = cells.length - 1; index >= 0; index--) {
            types[index] = TYPES[(int) (key & 3L)];
            cells[index] = (int) ((key >> 2) & 255L);
            key >>>= 10;
        }
        board.setState((int) key, cells, types);
    }

    public static void main(String[] args) {
        for (PistonPushLevels.Level level : PistonPushLevels.all()) {
            long started = System.currentTimeMillis();
            String result;
            try {
                result = path(level.rows(), DEFAULT_CAP);
            } catch (RuntimeException failure) {
                result = "ERROR " + failure.getMessage();
            }
            PistonPushBoard board = PistonPushBoard.parse(level.rows());
            System.out.println(level.id() + " par=" + level.par() + " optimal=" + (result == null ? "none" : result.startsWith("ERROR") ? result : result.length() + " " + result)
                    + " blocks=" + board.blockCount() + " floor=" + board.floorCount() + " ms=" + (System.currentTimeMillis() - started));
        }
    }

    static final class LongSet {
        private long[] keys;
        private boolean[] used;
        private int size;
        private int mask;

        LongSet(int expected) {
            int capacity = Integer.highestOneBit(Math.max(16, expected) * 2);
            keys = new long[capacity];
            used = new boolean[capacity];
            mask = capacity - 1;
        }

        int size() {
            return size;
        }

        boolean add(long key) {
            if ((size + 1) * 2 > keys.length) grow();
            int slot = slot(key);
            while (used[slot]) {
                if (keys[slot] == key) return false;
                slot = (slot + 1) & mask;
            }
            used[slot] = true;
            keys[slot] = key;
            size++;
            return true;
        }

        private int slot(long key) {
            long mixed = key * 0x9E3779B97F4A7C15L;
            return (int) (mixed >>> 32) & mask;
        }

        private void grow() {
            long[] oldKeys = keys;
            boolean[] oldUsed = used;
            keys = new long[oldKeys.length * 2];
            used = new boolean[oldKeys.length * 2];
            mask = keys.length - 1;
            size = 0;
            for (int index = 0; index < oldKeys.length; index++) if (oldUsed[index]) add(oldKeys[index]);
        }
    }
}
