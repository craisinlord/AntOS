package com.craisinlord.antos.content.client.game.logic.pistonpush;

import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Block;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Terrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class PistonPushRules {
    public static final int UP = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;
    public static final int MAX_GROUP = 12;
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DY = {-1, 0, 1, 0};

    private PistonPushRules() {}

    public static int dx(int direction) {
        return DX[direction];
    }

    public static int dy(int direction) {
        return DY[direction];
    }

    public static boolean sticks(Block from, Block to) {
        if (to == Block.NONE) return false;
        if (from == Block.SLIME) return to != Block.HONEY;
        if (from == Block.HONEY) return to != Block.SLIME;
        return false;
    }

    public static MoveResult move(PistonPushBoard board, int direction) {
        int width = board.width();
        int dx = DX[direction];
        int dy = DY[direction];
        int fromX = board.playerX();
        int fromY = board.playerY();
        int toX = fromX + dx;
        int toY = fromY + dy;
        if (board.solid(toX, toY)) return MoveResult.blocked(direction, fromX, fromY);
        Block[] blocks = board.blocks();
        int target = toY * width + toX;
        if (blocks[target] == Block.NONE) {
            board.setPlayer(target);
            return new MoveResult(true, direction, fromX, fromY, toX, toY, List.of(), 0, 0, 0, false, false, board.solved());
        }
        boolean[] member = new boolean[blocks.length];
        List<Integer> group = new ArrayList<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        member[target] = true;
        group.add(target);
        pending.add(target);
        boolean slime = false;
        boolean honey = false;
        while (!pending.isEmpty()) {
            int cell = pending.poll();
            int cx = cell % width;
            int cy = cell / width;
            Block type = blocks[cell];
            if (type == Block.SLIME) slime = true;
            if (type == Block.HONEY) honey = true;
            for (int side = 0; side < 4; side++) {
                int nx = cx + DX[side];
                int ny = cy + DY[side];
                if (!board.inBounds(nx, ny)) continue;
                int next = ny * width + nx;
                if (member[next] || blocks[next] == Block.NONE) continue;
                if (side == direction || sticks(type, blocks[next])) {
                    member[next] = true;
                    group.add(next);
                    pending.add(next);
                    if (group.size() > MAX_GROUP) return MoveResult.blocked(direction, fromX, fromY);
                }
            }
        }
        int player = board.player();
        for (int cell : group) {
            int nx = cell % width + dx;
            int ny = cell / width + dy;
            if (board.solid(nx, ny) || ny * width + nx == player) return MoveResult.blocked(direction, fromX, fromY);
        }
        Terrain[] terrain = board.terrain();
        boolean[] coveredBefore = new boolean[blocks.length];
        for (int cell = 0; cell < blocks.length; cell++) coveredBefore[cell] = terrain[cell] == Terrain.PLATE && blocks[cell] != Block.NONE;
        Block[] moved = new Block[group.size()];
        for (int index = 0; index < group.size(); index++) {
            moved[index] = blocks[group.get(index)];
            blocks[group.get(index)] = Block.NONE;
        }
        List<BlockMove> moves = new ArrayList<>(group.size());
        for (int index = 0; index < group.size(); index++) {
            int cell = group.get(index);
            int nx = cell % width + dx;
            int ny = cell / width + dy;
            blocks[ny * width + nx] = moved[index];
            moves.add(new BlockMove(cell % width, cell / width, nx, ny, moved[index]));
        }
        board.setPlayer(target);
        int slide = 0;
        if (group.size() == 1) {
            BlockMove only = moves.get(0);
            int x = only.toX();
            int y = only.toY();
            while (board.terrain(x, y) == Terrain.ICE) {
                int nx = x + dx;
                int ny = y + dy;
                if (board.solid(nx, ny) || blocks[ny * width + nx] != Block.NONE || ny * width + nx == target) break;
                blocks[ny * width + nx] = blocks[y * width + x];
                blocks[y * width + x] = Block.NONE;
                x = nx;
                y = ny;
                slide++;
            }
            if (slide > 0) moves.set(0, new BlockMove(only.fromX(), only.fromY(), x, y, only.block()));
        }
        int covered = 0;
        int uncovered = 0;
        for (int cell = 0; cell < blocks.length; cell++) {
            if (terrain[cell] != Terrain.PLATE) continue;
            boolean now = blocks[cell] != Block.NONE;
            if (now && !coveredBefore[cell]) covered++;
            if (!now && coveredBefore[cell]) uncovered++;
        }
        return new MoveResult(true, direction, fromX, fromY, toX, toY, List.copyOf(moves), slide, covered, uncovered, slime, honey, board.solved());
    }

    public record BlockMove(int fromX, int fromY, int toX, int toY, Block block) {}

    public record MoveResult(boolean moved, int direction, int playerFromX, int playerFromY, int playerToX, int playerToY,
                             List<BlockMove> blocks, int slide, int platesCovered, int platesUncovered,
                             boolean slime, boolean honey, boolean solved) {
        static MoveResult blocked(int direction, int x, int y) {
            return new MoveResult(false, direction, x, y, x, y, List.of(), 0, 0, 0, false, false, false);
        }

        public boolean blocked() {
            return !moved;
        }

        public boolean pushed() {
            return !blocks.isEmpty();
        }
    }
}
