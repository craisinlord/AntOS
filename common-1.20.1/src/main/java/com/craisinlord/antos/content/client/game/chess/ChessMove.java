package com.craisinlord.antos.content.client.game.chess;

public record ChessMove(int fromX, int fromY, int toX, int toY, ChessPieceType promotion) {
    public ChessMove(int fromX, int fromY, int toX, int toY) {
        this(fromX, fromY, toX, toY, null);
    }

    public boolean isPromotion() {
        return promotion != null;
    }

    public boolean isCastle(ChessPiece piece) {
        return piece != null && piece.type() == ChessPieceType.KING && Math.abs(toX - fromX) == 2;
    }

    public boolean sameSquares(ChessMove other) {
        return other != null && fromX == other.fromX && fromY == other.fromY && toX == other.toX && toY == other.toY;
    }
}
