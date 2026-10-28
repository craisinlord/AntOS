package com.craisinlord.antos.content.client.game.logic.pistonpush;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PistonPushGame {
    public static final int MAX_UNDO = 1000;

    private final String[] rows;
    private final Deque<PistonPushBoard.Snapshot> history = new ArrayDeque<>();
    private PistonPushBoard board;
    private int moves;

    public PistonPushGame(String... rows) {
        this.rows = rows.clone();
        this.board = PistonPushBoard.parse(this.rows);
    }

    public PistonPushBoard board() {
        return board;
    }

    public int moves() {
        return moves;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public PistonPushRules.MoveResult move(int direction) {
        if (board.solved()) return PistonPushRules.MoveResult.blocked(direction, board.playerX(), board.playerY());
        PistonPushBoard.Snapshot before = board.snapshot();
        PistonPushRules.MoveResult result = PistonPushRules.move(board, direction);
        if (!result.moved()) return result;
        history.push(before);
        while (history.size() > MAX_UNDO) history.removeLast();
        moves++;
        return result;
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        board.restore(history.pop());
        moves = Math.max(0, moves - 1);
        return true;
    }

    public void restart() {
        board = PistonPushBoard.parse(rows);
        history.clear();
        moves = 0;
    }
}
