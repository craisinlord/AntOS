package com.craisinlord.antos.content.client.game.chess;

public enum ChessColor {
    WHITE,
    BLACK;

    public ChessColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    public int pawnDirection() {
        return this == WHITE ? -1 : 1;
    }

    public int pawnStartRank() {
        return this == WHITE ? 6 : 1;
    }

    public int promotionRank() {
        return this == WHITE ? 0 : 7;
    }

    public int backRank() {
        return this == WHITE ? 7 : 0;
    }
}
