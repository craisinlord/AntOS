package com.craisinlord.antos.content.client.game.chess;

public enum ChessPieceType {
    PAWN(100),
    KNIGHT(320),
    BISHOP(330),
    ROOK(500),
    QUEEN(900),
    KING(20000);

    private final int value;

    ChessPieceType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
