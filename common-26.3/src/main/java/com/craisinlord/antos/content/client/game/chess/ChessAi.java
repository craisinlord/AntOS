package com.craisinlord.antos.content.client.game.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

public final class ChessAi {
    private static final int MATE = 1_000_000;

    private ChessAi() {
    }

    public static ChessMove choose(ChessGame game, ChessDifficulty difficulty, Random random, BooleanSupplier cancelled) {
        List<ChessMove> moves = new ArrayList<>(game.legalMoves());
        if (moves.isEmpty()) return null;
        if (random.nextDouble() < difficulty.blunderChance()) return moves.get(random.nextInt(moves.size()));
        ChessColor ai = game.turn();
        Collections.shuffle(moves, random);
        order(game, moves);
        ChessMove best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = -MATE * 2;
        for (ChessMove move : moves) {
            if (cancelled.getAsBoolean()) return null;
            int score = search(game.after(move), difficulty.depth() - 1, alpha, MATE * 2, ai, 1, cancelled);
            if (difficulty.noise() > 0) score += random.nextInt(difficulty.noise() * 2 + 1) - difficulty.noise();
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return best;
    }

    private static int search(ChessGame game, int depth, int alpha, int beta, ChessColor ai, int ply, BooleanSupplier cancelled) {
        if (depth <= 0) return evaluate(game, ai);
        List<ChessMove> moves = game.legalMoves();
        if (moves.isEmpty()) {
            if (!game.inCheck(game.turn())) return 0;
            return game.turn() == ai ? -MATE + ply : MATE - ply;
        }
        if (game.halfmoveClock() >= 100 || game.insufficientMaterial()) return 0;
        if (cancelled.getAsBoolean()) return 0;
        order(game, moves);
        boolean maximizing = game.turn() == ai;
        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (ChessMove move : moves) {
            int score = search(game.after(move), depth - 1, alpha, beta, ai, ply + 1, cancelled);
            if (maximizing) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha) break;
        }
        return best;
    }

    private static int evaluate(ChessGame game, ChessColor ai) {
        int score = 0;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = game.piece(x, y);
                if (piece == null) continue;
                int value = piece.type().value() + positional(piece, x, y);
                score += piece.color() == ai ? value : -value;
            }
        }
        score += (game.pseudoMobility(ai) - game.pseudoMobility(ai.opposite())) * 3;
        if (game.inCheck(ai.opposite())) score += 35;
        if (game.inCheck(ai)) score -= 35;
        return score;
    }

    private static int positional(ChessPiece piece, int x, int y) {
        int center = Math.abs(2 * x - 7) + Math.abs(2 * y - 7);
        return switch (piece.type()) {
            case PAWN -> (piece.color() == ChessColor.WHITE ? 6 - y : y - 1) * 8 + (x >= 2 && x <= 5 ? 6 : 0);
            case KNIGHT -> 30 - center * 3;
            case BISHOP -> 20 - center * 2;
            case ROOK -> 5;
            case QUEEN -> 10 - center;
            case KING -> y == piece.color().backRank() ? 12 : 0;
        };
    }

    private static void order(ChessGame game, List<ChessMove> moves) {
        moves.sort((first, second) -> Integer.compare(orderScore(game, second), orderScore(game, first)));
    }

    private static int orderScore(ChessGame game, ChessMove move) {
        ChessPiece moving = game.piece(move.fromX(), move.fromY());
        ChessPiece captured = game.piece(move.toX(), move.toY());
        int score = 0;
        if (captured != null && moving != null) score += captured.type().value() * 10 - moving.type().value();
        if (move.isPromotion()) score += move.promotion().value();
        if (move.isCastle(moving)) score += 75;
        return score;
    }
}
