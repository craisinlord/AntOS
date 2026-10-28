package com.craisinlord.antos.content.client.game.logic.ore2048;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Ore2048Board {
    public static final int SIZE = 4;
    public static final int EMPTY = 0;
    public static final int TNT = -1;
    public static final int FUSE_MOVES = 3;
    public static final int DIAMOND_TIER = 9;
    public static final int BEACON_TIER = 11;
    public static final int BLAST_PROOF_TIER = 9;
    public static final int TNT_MIN_TIER = 6;
    public static final int TNT_MIN_FILLED = 10;
    public static final double TNT_CHANCE = 0.05;

    public enum Dir {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        public final int dx;
        public final int dy;

        Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    public record Slide(int fromRow, int fromCol, int toRow, int toCol, int tier) { }

    public record Merge(int row, int col, int tier) { }

    public record Spawn(int row, int col, int tier) { }

    public record MoveResult(boolean moved, List<Slide> slides, List<Merge> merges, List<int[]> blast, int blastRow, int blastCol,
                             Spawn spawn, int scoreGained, boolean diamondReached, boolean beaconReached, boolean fuseWarning, boolean lost) {
        static MoveResult none() {
            return new MoveResult(false, List.of(), List.of(), List.of(), -1, -1, null, 0, false, false, false, false);
        }

        public boolean blasted() {
            return blastRow >= 0;
        }

        public int topMergeTier() {
            int top = 0;
            for (Merge merge : merges) top = Math.max(top, merge.tier());
            return top;
        }
    }

    private final int[][] cells = new int[SIZE][SIZE];
    private final Random random;
    private int fuse;
    private long score;
    private int bestTier;
    private boolean diamond;
    private boolean beacon;
    private boolean over;
    boolean spawnEnabled = true;

    public Ore2048Board(long seed) {
        this(new Random(seed), true);
    }

    public Ore2048Board() {
        this(new Random(), true);
    }

    private Ore2048Board(Random random, boolean start) {
        this.random = random;
        if (start) {
            spawnTile(false);
            spawnTile(false);
        }
    }

    static Ore2048Board empty(long seed) {
        return new Ore2048Board(new Random(seed), false);
    }

    public int get(int row, int col) {
        return cells[row][col];
    }

    void set(int row, int col, int tier) {
        cells[row][col] = tier;
        if (tier > 0) bestTier = Math.max(bestTier, tier);
    }

    void setFuse(int fuse) {
        this.fuse = fuse;
    }

    void clear() {
        for (int[] row : cells) java.util.Arrays.fill(row, EMPTY);
        fuse = 0;
    }

    public int fuse() {
        return fuse;
    }

    public long score() {
        return score;
    }

    public int bestTier() {
        return bestTier;
    }

    public boolean beaconLit() {
        return beacon;
    }

    public boolean over() {
        return over;
    }

    public static long value(int tier) {
        return tier <= 0 ? 0L : 1L << Math.min(62, tier);
    }

    public int maxTier() {
        int max = 0;
        for (int[] row : cells) for (int tier : row) max = Math.max(max, tier);
        return max;
    }

    public int filled() {
        int count = 0;
        for (int[] row : cells) for (int tier : row) if (tier != EMPTY) count++;
        return count;
    }

    public boolean hasTnt() {
        for (int[] row : cells) for (int tier : row) if (tier == TNT) return true;
        return false;
    }

    public boolean canMove() {
        for (int row = 0; row < SIZE; row++) for (int col = 0; col < SIZE; col++) {
            int tier = cells[row][col];
            if (tier == EMPTY) return true;
            if (tier <= 0) continue;
            if (col + 1 < SIZE && cells[row][col + 1] == tier) return true;
            if (row + 1 < SIZE && cells[row + 1][col] == tier) return true;
        }
        return false;
    }

    public MoveResult move(Dir dir) {
        if (over) return MoveResult.none();
        List<Slide> slides = new ArrayList<>();
        List<Merge> merges = new ArrayList<>();
        int[][] next = new int[SIZE][SIZE];
        boolean moved = false;
        int gained = 0;
        for (int line = 0; line < SIZE; line++) {
            int[][] positions = line(dir, line);
            int[] out = new int[SIZE];
            boolean[] merged = new boolean[SIZE];
            int count = 0;
            for (int i = 0; i < SIZE; i++) {
                int row = positions[i][0];
                int col = positions[i][1];
                int tier = cells[row][col];
                if (tier == EMPTY) continue;
                if (count > 0 && tier > 0 && !merged[count - 1] && out[count - 1] == tier) {
                    out[count - 1] = tier + 1;
                    merged[count - 1] = true;
                    int[] target = positions[count - 1];
                    slides.add(new Slide(row, col, target[0], target[1], tier));
                    merges.add(new Merge(target[0], target[1], tier + 1));
                    gained += (int) Math.min(Integer.MAX_VALUE, value(tier + 1));
                    moved = true;
                } else {
                    out[count] = tier;
                    int[] target = positions[count];
                    slides.add(new Slide(row, col, target[0], target[1], tier));
                    if (target[0] != row || target[1] != col) moved = true;
                    count++;
                }
            }
            for (int i = 0; i < SIZE; i++) next[positions[i][0]][positions[i][1]] = out[i];
        }
        if (!moved) return MoveResult.none();
        for (int row = 0; row < SIZE; row++) System.arraycopy(next[row], 0, cells[row], 0, SIZE);
        score += gained;
        int topMerge = 0;
        for (Merge merge : merges) topMerge = Math.max(topMerge, merge.tier());
        bestTier = Math.max(bestTier, topMerge);
        boolean diamondNow = !diamond && topMerge >= DIAMOND_TIER;
        boolean beaconNow = !beacon && topMerge >= BEACON_TIER;
        if (diamondNow) diamond = true;
        if (beaconNow) beacon = true;
        List<int[]> blast = new ArrayList<>();
        int blastRow = -1;
        int blastCol = -1;
        int[] tnt = findTnt();
        boolean warning = false;
        if (tnt != null && fuse > 0) {
            fuse--;
            if (fuse <= 0) {
                blastRow = tnt[0];
                blastCol = tnt[1];
                detonate(blastRow, blastCol, blast);
            } else if (fuse == 1) warning = true;
        }
        Spawn spawn = spawnEnabled ? spawnTile(true) : null;
        if (!canMove()) over = true;
        return new MoveResult(true, slides, merges, blast, blastRow, blastCol, spawn, gained, diamondNow, beaconNow, warning, over);
    }

    private void detonate(int centerRow, int centerCol, List<int[]> blast) {
        for (int row = centerRow - 1; row <= centerRow + 1; row++) for (int col = centerCol - 1; col <= centerCol + 1; col++) {
            if (row < 0 || col < 0 || row >= SIZE || col >= SIZE) continue;
            if (cells[row][col] >= BLAST_PROOF_TIER) continue;
            cells[row][col] = EMPTY;
            blast.add(new int[]{row, col});
        }
        fuse = 0;
    }

    private int[] findTnt() {
        for (int row = 0; row < SIZE; row++) for (int col = 0; col < SIZE; col++) if (cells[row][col] == TNT) return new int[]{row, col};
        return null;
    }

    Spawn spawnTile(boolean allowTnt) {
        int empty = SIZE * SIZE - filled();
        if (empty == 0) return null;
        int pick = random.nextInt(empty);
        int filledBefore = filled();
        boolean tntAllowed = allowTnt && !hasTnt() && maxTier() >= TNT_MIN_TIER && filledBefore >= TNT_MIN_FILLED;
        int tier;
        if (tntAllowed && random.nextDouble() < TNT_CHANCE) tier = TNT;
        else tier = random.nextInt(10) == 0 ? 2 : 1;
        for (int row = 0; row < SIZE; row++) for (int col = 0; col < SIZE; col++) {
            if (cells[row][col] != EMPTY) continue;
            if (pick-- == 0) {
                cells[row][col] = tier;
                if (tier == TNT) fuse = FUSE_MOVES;
                else bestTier = Math.max(bestTier, tier);
                return new Spawn(row, col, tier);
            }
        }
        return null;
    }

    private static int[][] line(Dir dir, int index) {
        int[][] positions = new int[SIZE][2];
        for (int i = 0; i < SIZE; i++) {
            switch (dir) {
                case LEFT -> { positions[i][0] = index; positions[i][1] = i; }
                case RIGHT -> { positions[i][0] = index; positions[i][1] = SIZE - 1 - i; }
                case UP -> { positions[i][0] = i; positions[i][1] = index; }
                case DOWN -> { positions[i][0] = SIZE - 1 - i; positions[i][1] = index; }
            }
        }
        return positions;
    }
}
