package com.craisinlord.antos.content.client.game.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChessGame {
    public enum Status {
        ACTIVE,
        WHITE_WON,
        BLACK_WON,
        STALEMATE,
        DRAW_FIFTY_MOVES,
        DRAW_REPETITION,
        DRAW_MATERIAL;

        public boolean over() {
            return this != ACTIVE;
        }

        public boolean draw() {
            return this == STALEMATE || this == DRAW_FIFTY_MOVES || this == DRAW_REPETITION || this == DRAW_MATERIAL;
        }
    }

    private static final int[][] KNIGHT_OFFSETS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] QUEEN_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final ChessPiece[][] board = new ChessPiece[8][8];
    private final Map<String, Integer> positions;
    private ChessColor turn = ChessColor.WHITE;
    private Status status = Status.ACTIVE;
    private boolean whiteKingSide = true;
    private boolean whiteQueenSide = true;
    private boolean blackKingSide = true;
    private boolean blackQueenSide = true;
    private int enPassantX = -1;
    private int enPassantY = -1;
    private int halfmoveClock;
    private int fullmoveNumber = 1;

    public ChessGame() {
        positions = new HashMap<>();
        placeBackRank(ChessColor.BLACK, 0);
        placePawns(ChessColor.BLACK, 1);
        placePawns(ChessColor.WHITE, 6);
        placeBackRank(ChessColor.WHITE, 7);
        positions.put(positionKey(), 1);
    }

    private ChessGame(ChessGame source) {
        positions = null;
        for (int x = 0; x < 8; x++) System.arraycopy(source.board[x], 0, board[x], 0, 8);
        turn = source.turn;
        status = source.status;
        whiteKingSide = source.whiteKingSide;
        whiteQueenSide = source.whiteQueenSide;
        blackKingSide = source.blackKingSide;
        blackQueenSide = source.blackQueenSide;
        enPassantX = source.enPassantX;
        enPassantY = source.enPassantY;
        halfmoveClock = source.halfmoveClock;
        fullmoveNumber = source.fullmoveNumber;
    }

    private void placePawns(ChessColor color, int y) {
        for (int x = 0; x < 8; x++) board[x][y] = new ChessPiece(color, ChessPieceType.PAWN);
    }

    private void placeBackRank(ChessColor color, int y) {
        ChessPieceType[] order = {ChessPieceType.ROOK, ChessPieceType.KNIGHT, ChessPieceType.BISHOP, ChessPieceType.QUEEN,
                ChessPieceType.KING, ChessPieceType.BISHOP, ChessPieceType.KNIGHT, ChessPieceType.ROOK};
        for (int x = 0; x < 8; x++) board[x][y] = new ChessPiece(color, order[x]);
    }

    public ChessGame copy() {
        return new ChessGame(this);
    }

    public ChessPiece piece(int x, int y) {
        return inside(x, y) ? board[x][y] : null;
    }

    public ChessColor turn() {
        return turn;
    }

    public Status status() {
        return status;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public void resign(ChessColor color) {
        if (status == Status.ACTIVE) status = color == ChessColor.WHITE ? Status.BLACK_WON : Status.WHITE_WON;
    }

    public List<ChessMove> legalMoves() {
        return legalMoves(turn);
    }

    public List<ChessMove> legalMoves(ChessColor color) {
        List<ChessMove> legal = new ArrayList<>();
        List<ChessMove> pseudo = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board[x][y];
                if (piece == null || piece.color() != color) continue;
                pseudo.clear();
                addPseudoMoves(pseudo, x, y, piece);
                for (ChessMove move : pseudo) {
                    ChessGame copy = new ChessGame(this);
                    copy.apply(move);
                    if (!copy.inCheck(color)) legal.add(move);
                }
            }
        }
        return legal;
    }

    public List<ChessMove> legalMovesFrom(int x, int y) {
        ChessPiece piece = piece(x, y);
        if (piece == null || piece.color() != turn || status.over()) return List.of();
        List<ChessMove> result = new ArrayList<>();
        for (ChessMove move : legalMoves(turn)) if (move.fromX() == x && move.fromY() == y) result.add(move);
        return result;
    }

    public int pseudoMobility(ChessColor color) {
        List<ChessMove> moves = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board[x][y];
                if (piece != null && piece.color() == color && piece.type() != ChessPieceType.KING) addPseudoMoves(moves, x, y, piece);
            }
        }
        return moves.size();
    }

    public boolean play(ChessMove requested) {
        if (requested == null || status.over()) return false;
        ChessMove legal = null;
        for (ChessMove move : legalMoves(turn)) {
            if (!move.sameSquares(requested)) continue;
            ChessPieceType promotion = requested.promotion() == null ? ChessPieceType.QUEEN : requested.promotion();
            if (!move.isPromotion() || move.promotion() == promotion) {
                legal = move;
                break;
            }
        }
        if (legal == null) return false;
        advance(legal);
        if (legalMoves(turn).isEmpty()) {
            status = inCheck(turn) ? (turn == ChessColor.WHITE ? Status.BLACK_WON : Status.WHITE_WON) : Status.STALEMATE;
        } else if (insufficientMaterial()) {
            status = Status.DRAW_MATERIAL;
        } else if (halfmoveClock >= 100) {
            status = Status.DRAW_FIFTY_MOVES;
        } else if (positions != null && positions.merge(positionKey(), 1, Integer::sum) >= 3) {
            status = Status.DRAW_REPETITION;
        }
        return true;
    }

    ChessGame after(ChessMove move) {
        ChessGame copy = new ChessGame(this);
        copy.advance(move);
        return copy;
    }

    private void advance(ChessMove move) {
        ChessColor moving = turn;
        apply(move);
        turn = turn.opposite();
        if (moving == ChessColor.BLACK) fullmoveNumber++;
    }

    public boolean insufficientMaterial() {
        int minors = 0;
        int bishopsOnLight = 0;
        int bishopsOnDark = 0;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board[x][y];
                if (piece == null || piece.type() == ChessPieceType.KING) continue;
                switch (piece.type()) {
                    case KNIGHT -> minors++;
                    case BISHOP -> {
                        minors++;
                        if ((x + y) % 2 == 0) bishopsOnLight++;
                        else bishopsOnDark++;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        }
        if (minors <= 1) return true;
        return minors == bishopsOnLight + bishopsOnDark && (bishopsOnLight == 0 || bishopsOnDark == 0);
    }

    private String positionKey() {
        StringBuilder key = new StringBuilder(80);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                ChessPiece piece = board[x][y];
                key.append(piece == null ? '.' : (char) ((piece.color() == ChessColor.WHITE ? 'A' : 'a') + piece.type().ordinal()));
            }
        }
        key.append(turn == ChessColor.WHITE ? 'w' : 'b');
        key.append(whiteKingSide ? 'K' : '-').append(whiteQueenSide ? 'Q' : '-').append(blackKingSide ? 'k' : '-').append(blackQueenSide ? 'q' : '-');
        key.append(enPassantX).append(enPassantY);
        return key.toString();
    }

    private void addPseudoMoves(List<ChessMove> moves, int x, int y, ChessPiece piece) {
        switch (piece.type()) {
            case PAWN -> addPawnMoves(moves, x, y, piece.color());
            case KNIGHT -> {
                for (int[] offset : KNIGHT_OFFSETS) addIfAvailable(moves, x, y, x + offset[0], y + offset[1], piece.color());
            }
            case BISHOP -> addSliding(moves, x, y, piece.color(), BISHOP_DIRECTIONS);
            case ROOK -> addSliding(moves, x, y, piece.color(), ROOK_DIRECTIONS);
            case QUEEN -> addSliding(moves, x, y, piece.color(), QUEEN_DIRECTIONS);
            case KING -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0) addIfAvailable(moves, x, y, x + dx, y + dy, piece.color());
                    }
                }
                addCastling(moves, piece.color());
            }
        }
    }

    private void addPawnMoves(List<ChessMove> moves, int x, int y, ChessColor color) {
        int direction = color.pawnDirection();
        int forward = y + direction;
        if (inside(x, forward) && board[x][forward] == null) {
            addPawnMove(moves, x, y, x, forward, color);
            int doubleForward = y + direction * 2;
            if (y == color.pawnStartRank() && board[x][doubleForward] == null) moves.add(new ChessMove(x, y, x, doubleForward));
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            int targetX = x + dx;
            if (!inside(targetX, forward)) continue;
            ChessPiece target = board[targetX][forward];
            if (target != null && target.color() != color || targetX == enPassantX && forward == enPassantY) {
                addPawnMove(moves, x, y, targetX, forward, color);
            }
        }
    }

    private void addPawnMove(List<ChessMove> moves, int fromX, int fromY, int toX, int toY, ChessColor color) {
        if (toY != color.promotionRank()) {
            moves.add(new ChessMove(fromX, fromY, toX, toY));
            return;
        }
        moves.add(new ChessMove(fromX, fromY, toX, toY, ChessPieceType.QUEEN));
        moves.add(new ChessMove(fromX, fromY, toX, toY, ChessPieceType.ROOK));
        moves.add(new ChessMove(fromX, fromY, toX, toY, ChessPieceType.BISHOP));
        moves.add(new ChessMove(fromX, fromY, toX, toY, ChessPieceType.KNIGHT));
    }

    private void addSliding(List<ChessMove> moves, int x, int y, ChessColor color, int[][] directions) {
        for (int[] direction : directions) {
            int targetX = x + direction[0];
            int targetY = y + direction[1];
            while (inside(targetX, targetY)) {
                ChessPiece target = board[targetX][targetY];
                if (target != null) {
                    if (target.color() != color) moves.add(new ChessMove(x, y, targetX, targetY));
                    break;
                }
                moves.add(new ChessMove(x, y, targetX, targetY));
                targetX += direction[0];
                targetY += direction[1];
            }
        }
    }

    private void addIfAvailable(List<ChessMove> moves, int fromX, int fromY, int toX, int toY, ChessColor color) {
        if (!inside(toX, toY)) return;
        ChessPiece target = board[toX][toY];
        if (target == null || target.color() != color) moves.add(new ChessMove(fromX, fromY, toX, toY));
    }

    private void addCastling(List<ChessMove> moves, ChessColor color) {
        int y = color.backRank();
        ChessPiece king = board[4][y];
        if (king == null || king.type() != ChessPieceType.KING || king.color() != color) return;
        ChessColor enemy = color.opposite();
        if (attacked(4, y, enemy)) return;
        boolean kingSide = color == ChessColor.WHITE ? whiteKingSide : blackKingSide;
        boolean queenSide = color == ChessColor.WHITE ? whiteQueenSide : blackQueenSide;
        if (kingSide && board[5][y] == null && board[6][y] == null && isRook(7, y, color) && !attacked(5, y, enemy) && !attacked(6, y, enemy)) {
            moves.add(new ChessMove(4, y, 6, y));
        }
        if (queenSide && board[1][y] == null && board[2][y] == null && board[3][y] == null && isRook(0, y, color) && !attacked(3, y, enemy) && !attacked(2, y, enemy)) {
            moves.add(new ChessMove(4, y, 2, y));
        }
    }

    private boolean isRook(int x, int y, ChessColor color) {
        ChessPiece piece = board[x][y];
        return piece != null && piece.color() == color && piece.type() == ChessPieceType.ROOK;
    }

    private void apply(ChessMove move) {
        ChessPiece moving = board[move.fromX()][move.fromY()];
        if (moving == null) return;
        ChessPiece captured = board[move.toX()][move.toY()];
        if (moving.type() == ChessPieceType.KING) clearCastling(moving.color());
        else if (moving.type() == ChessPieceType.ROOK) clearRookCastling(moving.color(), move.fromX(), move.fromY());
        if (captured != null && captured.type() == ChessPieceType.ROOK) clearRookCastling(captured.color(), move.toX(), move.toY());
        boolean pawn = moving.type() == ChessPieceType.PAWN;
        boolean enPassant = pawn && captured == null && move.fromX() != move.toX() && move.toX() == enPassantX && move.toY() == enPassantY;
        if (enPassant) board[move.toX()][move.toY() - moving.color().pawnDirection()] = null;
        board[move.fromX()][move.fromY()] = null;
        ChessPiece placed = moving;
        if (pawn && move.toY() == moving.color().promotionRank()) {
            placed = new ChessPiece(moving.color(), move.promotion() == null ? ChessPieceType.QUEEN : move.promotion());
        }
        board[move.toX()][move.toY()] = placed;
        if (move.isCastle(moving)) {
            int y = moving.color().backRank();
            if (move.toX() > move.fromX()) {
                board[5][y] = board[7][y];
                board[7][y] = null;
            } else {
                board[3][y] = board[0][y];
                board[0][y] = null;
            }
        }
        enPassantX = -1;
        enPassantY = -1;
        if (pawn && Math.abs(move.toY() - move.fromY()) == 2) {
            enPassantX = move.fromX();
            enPassantY = (move.fromY() + move.toY()) / 2;
        }
        halfmoveClock = pawn || captured != null || enPassant ? 0 : halfmoveClock + 1;
    }

    private void clearCastling(ChessColor color) {
        if (color == ChessColor.WHITE) {
            whiteKingSide = false;
            whiteQueenSide = false;
        } else {
            blackKingSide = false;
            blackQueenSide = false;
        }
    }

    private void clearRookCastling(ChessColor color, int x, int y) {
        if (y != color.backRank()) return;
        if (color == ChessColor.WHITE) {
            if (x == 0) whiteQueenSide = false;
            else if (x == 7) whiteKingSide = false;
        } else {
            if (x == 0) blackQueenSide = false;
            else if (x == 7) blackKingSide = false;
        }
    }

    public boolean inCheck(ChessColor color) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board[x][y];
                if (piece != null && piece.color() == color && piece.type() == ChessPieceType.KING) return attacked(x, y, color.opposite());
            }
        }
        return true;
    }

    public int[] kingSquare(ChessColor color) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                ChessPiece piece = board[x][y];
                if (piece != null && piece.color() == color && piece.type() == ChessPieceType.KING) return new int[]{x, y};
            }
        }
        return null;
    }

    public boolean attacked(int targetX, int targetY, ChessColor attacker) {
        int pawnY = targetY - attacker.pawnDirection();
        for (int dx = -1; dx <= 1; dx += 2) {
            ChessPiece pawn = piece(targetX + dx, pawnY);
            if (pawn != null && pawn.color() == attacker && pawn.type() == ChessPieceType.PAWN) return true;
        }
        for (int[] offset : KNIGHT_OFFSETS) {
            ChessPiece knight = piece(targetX + offset[0], targetY + offset[1]);
            if (knight != null && knight.color() == attacker && knight.type() == ChessPieceType.KNIGHT) return true;
        }
        if (slider(targetX, targetY, attacker, ROOK_DIRECTIONS, ChessPieceType.ROOK)) return true;
        if (slider(targetX, targetY, attacker, BISHOP_DIRECTIONS, ChessPieceType.BISHOP)) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ChessPiece king = piece(targetX + dx, targetY + dy);
                if (king != null && king.color() == attacker && king.type() == ChessPieceType.KING) return true;
            }
        }
        return false;
    }

    private boolean slider(int targetX, int targetY, ChessColor attacker, int[][] directions, ChessPieceType type) {
        for (int[] direction : directions) {
            int x = targetX + direction[0];
            int y = targetY + direction[1];
            while (inside(x, y)) {
                ChessPiece piece = board[x][y];
                if (piece != null) {
                    if (piece.color() == attacker && (piece.type() == type || piece.type() == ChessPieceType.QUEEN)) return true;
                    break;
                }
                x += direction[0];
                y += direction[1];
            }
        }
        return false;
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8;
    }
}
