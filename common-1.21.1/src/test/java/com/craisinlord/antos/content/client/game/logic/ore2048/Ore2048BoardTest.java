package com.craisinlord.antos.content.client.game.logic.ore2048;

import com.craisinlord.antos.content.client.game.logic.ore2048.Ore2048Board.Dir;
import com.craisinlord.antos.content.client.game.logic.ore2048.Ore2048Board.MoveResult;

public final class Ore2048BoardTest {
    private static final int T = Ore2048Board.TNT;

    public static void main(String[] args) {
        lineMerges();
        allDirections();
        noOpMove();
        scoreAccounting();
        startingTiles();
        spawnOdds();
        tntBlocksMerges();
        fuseLength();
        centerBlast();
        cornerBlast();
        tntSpawnGating();
        lossDetection();
        winOnce();
        System.out.println("Ore2048BoardTest passed");
    }

    private static void lineMerges() {
        Ore2048Board board = board(new int[][]{{1, 1, 1, 1}, {1, 1, 2, 0}, {2, 0, 2, 2}, {0, 0, 0, 0}});
        board.move(Dir.LEFT);
        checkRow(board, 0, new int[]{2, 2, 0, 0}, "1,1,1,1 merges into two pairs");
        checkRow(board, 1, new int[]{2, 2, 0, 0}, "merged tile does not merge again");
        checkRow(board, 2, new int[]{3, 2, 0, 0}, "leading pair merges first");
    }

    private static void allDirections() {
        Ore2048Board right = board(new int[][]{{1, 1, 1, 1}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        right.move(Dir.RIGHT);
        checkRow(right, 0, new int[]{0, 0, 2, 2}, "right merges toward the right edge");
        Ore2048Board up = board(new int[][]{{1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}});
        up.move(Dir.UP);
        checkCol(up, 0, new int[]{2, 2, 0, 0}, "up merges toward the top");
        Ore2048Board down = board(new int[][]{{0, 1, 0, 0}, {0, 1, 0, 0}, {0, 2, 0, 0}, {0, 0, 0, 0}});
        down.move(Dir.DOWN);
        checkCol(down, 1, new int[]{0, 0, 2, 2}, "down slides and merges toward the bottom");
    }

    private static void noOpMove() {
        Ore2048Board board = Ore2048Board.empty(3L);
        board.set(0, 0, 1);
        board.set(0, 1, 2);
        board.set(1, 0, T);
        board.setFuse(2);
        MoveResult result = board.move(Dir.LEFT);
        check(!result.moved(), "packed row cannot move left");
        check(result.spawn() == null, "no spawn after an invalid move");
        check(board.filled() == 3, "invalid move adds no tiles");
        check(board.fuse() == 2, "invalid move does not tick the fuse");
        check(board.score() == 0, "invalid move scores nothing");
        result = board.move(Dir.UP);
        check(!result.moved() && board.fuse() == 2, "up is also a no-op");
    }

    private static void scoreAccounting() {
        Ore2048Board board = board(new int[][]{{1, 1, 2, 2}, {3, 3, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        MoveResult result = board.move(Dir.LEFT);
        check(result.scoreGained() == 4 + 8 + 16, "score adds the value of every new tile");
        check(board.score() == 28, "board score accumulates");
        check(result.merges().size() == 3, "three merges reported");
        check(result.topMergeTier() == 4, "top merge tier is gold");
        check(result.slides().size() == 6, "every tile reports a slide");
        check(Ore2048Board.value(11) == 2048, "beacon tier is worth 2048");
    }

    private static void startingTiles() {
        for (long seed = 0; seed < 50; seed++) {
            Ore2048Board board = new Ore2048Board(seed);
            check(board.filled() == 2, "new board starts with two tiles");
            check(!board.hasTnt(), "new board starts without tnt");
        }
    }

    private static void spawnOdds() {
        Ore2048Board board = Ore2048Board.empty(42L);
        int ones = 0;
        int twos = 0;
        for (int i = 0; i < 10000; i++) {
            board.clear();
            int tier = board.spawnTile(true).tier();
            if (tier == 1) ones++;
            else if (tier == 2) twos++;
            else throw new AssertionError("unexpected spawn tier " + tier);
        }
        check(ones > 8800 && ones < 9200, "about 90 percent coal, got " + ones);
        check(twos > 800 && twos < 1200, "about 10 percent copper, got " + twos);
    }

    private static void tntBlocksMerges() {
        Ore2048Board board = board(new int[][]{{1, T, 1, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        board.setFuse(3);
        MoveResult result = board.move(Dir.LEFT);
        check(!result.moved(), "tnt between equal tiles prevents a merge");
        Ore2048Board slide = board(new int[][]{{1, 0, T, 1}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        slide.setFuse(3);
        result = slide.move(Dir.LEFT);
        check(result.moved() && result.merges().isEmpty(), "tnt slides without merging");
        checkRow(slide, 0, new int[]{1, T, 1, 0}, "tnt slides like a tile");
        Ore2048Board pair = board(new int[][]{{T, T, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        check(!pair.move(Dir.LEFT).moved(), "tnt never merges with tnt");
    }

    private static void fuseLength() {
        Ore2048Board board = board(new int[][]{{T, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 1}});
        board.setFuse(Ore2048Board.FUSE_MOVES);
        MoveResult first = board.move(Dir.LEFT);
        check(first.moved() && !first.blasted() && board.fuse() == 2, "first move ticks fuse to 2");
        check(!first.fuseWarning(), "no warning at fuse 2");
        MoveResult second = board.move(Dir.RIGHT);
        check(!second.blasted() && board.fuse() == 1, "second move ticks fuse to 1");
        check(second.fuseWarning(), "warning when fuse reaches 1");
        check(board.get(0, 3) == T, "tnt slid right");
        MoveResult third = board.move(Dir.LEFT);
        check(third.blasted(), "third valid move detonates");
        check(third.blastRow() == 0 && third.blastCol() == 0, "blast happens after the slide");
        check(!board.hasTnt() && board.fuse() == 0, "tnt is consumed");
    }

    private static void centerBlast() {
        Ore2048Board board = board(new int[][]{{4, 9, 4, 0}, {5, T, 3, 0}, {3, 10, 2, 0}, {0, 0, 0, 1}});
        board.setFuse(1);
        MoveResult result = board.move(Dir.LEFT);
        check(result.blasted() && result.blastRow() == 1 && result.blastCol() == 1, "blast centered on tnt");
        checkRow(board, 0, new int[]{0, 9, 0, 0}, "top row cleared except diamond block");
        checkRow(board, 1, new int[]{0, 0, 0, 0}, "middle row cleared");
        checkRow(board, 2, new int[]{0, 10, 0, 0}, "netherite survives the blast");
        checkRow(board, 3, new int[]{1, 0, 0, 0}, "tiles outside the blast survive");
        check(result.blast().size() == 7, "seven cells cleared, got " + result.blast().size());
    }

    private static void cornerBlast() {
        Ore2048Board board = board(new int[][]{{T, 2, 3, 0}, {3, 4, 5, 0}, {6, 7, 8, 0}, {0, 0, 0, 1}});
        board.setFuse(1);
        MoveResult result = board.move(Dir.LEFT);
        check(result.blasted(), "corner tnt detonates");
        checkRow(board, 0, new int[]{0, 0, 3, 0}, "corner blast clears only inside the board");
        checkRow(board, 1, new int[]{0, 0, 5, 0}, "corner blast second row");
        checkRow(board, 2, new int[]{6, 7, 8, 0}, "third row untouched");
        check(result.blast().size() == 4, "corner blast clears four cells");
    }

    private static void tntSpawnGating() {
        check(tntSpawns(6, 10, false) > 0, "tnt can spawn at emerald with ten tiles");
        check(tntSpawns(5, 10, false) == 0, "no tnt below emerald");
        check(tntSpawns(6, 9, false) == 0, "no tnt below ten tiles");
        check(tntSpawns(6, 10, true) == 0, "no second tnt");
        int count = tntSpawns(6, 10, false);
        check(count > 300 && count < 700, "tnt odds about 5 percent, got " + count);
    }

    private static int tntSpawns(int maxTier, int filled, boolean existing) {
        Ore2048Board board = Ore2048Board.empty(7L);
        int count = 0;
        for (int i = 0; i < 10000; i++) {
            board.clear();
            int placed = 0;
            for (int row = 0; row < 4 && placed < filled; row++) for (int col = 0; col < 4 && placed < filled; col++) {
                int tier = placed == 0 ? maxTier : existing && placed == 1 ? T : 1;
                board.set(row, col, tier);
                placed++;
            }
            Ore2048Board.Spawn spawn = board.spawnTile(true);
            if (spawn.tier() == T) {
                count++;
                check(board.fuse() == Ore2048Board.FUSE_MOVES, "fresh tnt has a full fuse");
            }
        }
        return count;
    }

    private static void lossDetection() {
        Ore2048Board board = board(new int[][]{{1, 2, 1, 2}, {2, 1, 2, 1}, {1, 2, 1, 2}, {2, 1, 2, 1}});
        check(!board.canMove(), "full checkerboard cannot move");
        Ore2048Board withTnt = board(new int[][]{{T, 2, 1, 2}, {2, 1, 2, 1}, {1, 2, 1, 2}, {2, 1, 2, 1}});
        check(!withTnt.canMove(), "tnt does not count as a merge");
        Ore2048Board open = board(new int[][]{{1, 1, 1, 2}, {2, 1, 2, 1}, {1, 2, 1, 2}, {2, 1, 2, 1}});
        check(open.canMove(), "adjacent pair can move");
        int[][] rows = {{1, 2, 1, 2}, {2, 1, 2, 1}, {1, 2, 1, 2}, {3, 4, 3, 0}};
        MoveResult result = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            Ore2048Board copy = Ore2048Board.empty(attempt);
            for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) copy.set(row, col, rows[row][col]);
            result = copy.move(Dir.RIGHT);
            if (result.lost()) {
                check(copy.over(), "board reports game over");
                check(!copy.move(Dir.LEFT).moved(), "no moves after game over");
                break;
            }
        }
        check(result != null && result.lost(), "filling the last cell without merges loses");
    }

    private static void winOnce() {
        Ore2048Board board = board(new int[][]{{10, 10, 0, 0}, {8, 8, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        MoveResult first = board.move(Dir.LEFT);
        check(first.beaconReached(), "beacon fires on the first 2048");
        check(first.diamondReached(), "diamond fires on the way");
        check(board.beaconLit(), "board remembers the beacon");
        check(board.bestTier() == 11, "best tier is beacon");
        board.set(2, 0, 10);
        board.set(2, 1, 10);
        MoveResult second = board.move(Dir.RIGHT);
        check(second.moved(), "play continues after the beacon");
        check(!second.beaconReached() && !second.diamondReached(), "win flags fire only once");
        check(!board.over(), "game is not over after winning");
        Ore2048Board diamond = board(new int[][]{{8, 8, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        MoveResult reached = diamond.move(Dir.LEFT);
        check(reached.diamondReached() && !reached.beaconReached(), "diamond block alone fires diamond");
    }

    private static Ore2048Board board(int[][] rows) {
        Ore2048Board board = Ore2048Board.empty(1L);
        board.spawnEnabled = false;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) board.set(row, col, rows[row][col]);
        return board;
    }

    private static void checkRow(Ore2048Board board, int row, int[] expected, String message) {
        for (int col = 0; col < 4; col++) check(board.get(row, col) == expected[col], message + " at col " + col + " got " + board.get(row, col));
    }

    private static void checkCol(Ore2048Board board, int col, int[] expected, String message) {
        for (int row = 0; row < 4; row++) check(board.get(row, col) == expected[row], message + " at row " + row + " got " + board.get(row, col));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
