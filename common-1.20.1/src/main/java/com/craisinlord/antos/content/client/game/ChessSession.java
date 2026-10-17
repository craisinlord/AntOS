package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.content.client.ComputerTheme;
import com.craisinlord.antos.content.client.game.chess.ChessAi;
import com.craisinlord.antos.content.client.game.chess.ChessColor;
import com.craisinlord.antos.content.client.game.chess.ChessDifficulty;
import com.craisinlord.antos.content.client.game.chess.ChessGame;
import com.craisinlord.antos.content.client.game.chess.ChessMove;
import com.craisinlord.antos.content.client.game.chess.ChessPiece;
import com.craisinlord.antos.content.client.game.chess.ChessPieceType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChessSession implements ComputerGame.Session {
    private static final ExecutorService AI_THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AntOS Chess");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Map<ChessColor, Map<ChessPieceType, ResourceLocation>> TEXTURES = new EnumMap<>(ChessColor.class);
    private static final ChessPieceType[] PROMOTIONS = {ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT};
    private static final ChessPieceType[] CAPTURE_ORDER = {ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT, ChessPieceType.PAWN};
    private static final int[] START_COUNTS = {8, 2, 2, 2, 1, 1};
    private static final int RANK_GUTTER = 9;
    private static final int PANEL_GAP = 10;
    private static final int PAD = 8;
    private static final int LIGHT = 0xFF2B5A2B;
    private static final int DARK = 0xFF143014;
    private static final int LAST_LIGHT = 0xFF3D753D;
    private static final int LAST_DARK = 0xFF244C24;
    private static final int SELECTED = 0xFF4F9A4F;
    private static final long AI_DELAY_MILLIS = 350L;

    static {
        for (ChessColor color : ChessColor.values()) {
            Map<ChessPieceType, ResourceLocation> byType = new EnumMap<>(ChessPieceType.class);
            for (ChessPieceType type : ChessPieceType.values()) {
                String name = color.name().toLowerCase(Locale.ROOT) + "_" + type.name().toLowerCase(Locale.ROOT);
                byType.put(type, new ResourceLocation("antos", "textures/gui/chess/" + name + ".png"));
            }
            TEXTURES.put(color, byType);
        }
    }

    private final GameButtons buttons = new GameButtons();
    private final GameRewardTracker rewards = new GameRewardTracker();
    private final Random random = new Random();
    private ChessDifficulty difficulty;
    private ChessGame game;
    private int selectedX = -1;
    private int selectedY = -1;
    private List<ChessMove> targets = List.of();
    private List<ChessMove> promotionChoices = List.of();
    private ChessMove lastMove;
    private CompletableFuture<ChessMove> thinking;
    private volatile int generation;
    private long playerMovedAt;
    private boolean resigned;
    private boolean finished;
    private int cell = 20;
    private int boardLeft = RANK_GUTTER;
    private int boardTop;

    @Override
    public void tick() {
        rewards.tick();
        if (game == null || finished) return;
        if (thinking != null) {
            if (!thinking.isDone()) return;
            ChessMove move = thinking.getNow(null);
            thinking = null;
            if (move != null && game.play(move)) lastMove = move;
            checkFinished();
            return;
        }
        if (game.turn() == ChessColor.BLACK && System.currentTimeMillis() - playerMovedAt >= AI_DELAY_MILLIS) {
            ChessGame snapshot = game.copy();
            ChessDifficulty level = difficulty;
            int expected = generation;
            Random seed = new Random(random.nextLong());
            thinking = CompletableFuture.supplyAsync(() -> ChessAi.choose(snapshot, level, seed, () -> generation != expected), AI_THREAD)
                    .exceptionally(error -> null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        render(graphics, font, x, y, width, height, -1, -1);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        buttons.begin(x, y);
        if (game == null) renderMenu(graphics, font, x, y, width, mouseX, mouseY);
        else renderGame(graphics, font, x, y, width, height, mouseX, mouseY);
        rewards.render(graphics, font, x, y, width, height);
    }

    private void renderMenu(GuiGraphics graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
        graphics.drawString(font, GameButtons.trim(font, Component.translatable("computer.antos.chess.pick"), width - 8), x, y, GameButtons.PALE, false);
        int top = y + 16;
        for (ChessDifficulty option : ChessDifficulty.values()) {
            buttons.row(graphics, font, x, top, width - 8,
                    Component.translatable("computer.antos.chess.difficulty." + option.id()),
                    Component.translatable("computer.antos.chess.difficulty." + option.id() + ".hint"),
                    GameRewardTracker.dailyBonus(option.rewardMode()), mouseX, mouseY, () -> start(option));
            top += GameButtons.ROW_HEIGHT + GameButtons.ROW_GAP;
        }
    }

    private void renderGame(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        cell = height >= 173 && width >= 300 ? 20 : 18;
        int boardSize = cell * 8;
        int boardX = x + RANK_GUTTER;
        int boardY = y + 1;
        boardLeft = boardX - x;
        boardTop = boardY - y;
        int[] checked = !finished && game.inCheck(game.turn()) ? game.kingSquare(game.turn()) : null;
        int hoverX = Math.floorDiv(mouseX - boardLeft, cell);
        int hoverY = Math.floorDiv(mouseY - boardTop, cell);
        boolean hoverEnabled = promotionChoices.isEmpty() && playerCanMove();
        for (int column = 0; column < 8; column++) {
            for (int row = 0; row < 8; row++) {
                int left = boardX + column * cell;
                int top = boardY + row * cell;
                boolean light = (column + row) % 2 == 0;
                boolean last = lastMove != null && (lastMove.fromX() == column && lastMove.fromY() == row || lastMove.toX() == column && lastMove.toY() == row);
                int color = column == selectedX && row == selectedY ? SELECTED : last ? (light ? LAST_LIGHT : LAST_DARK) : light ? LIGHT : DARK;
                graphics.fill(left, top, left + cell, top + cell, color);
                if (checked != null && checked[0] == column && checked[1] == row) {
                    graphics.renderOutline(left, top, cell, cell, GameButtons.PALE);
                    graphics.renderOutline(left + 1, top + 1, cell - 2, cell - 2, GameButtons.PALE);
                }
                if (hoverEnabled && column == hoverX && row == hoverY) graphics.renderOutline(left, top, cell, cell, GameButtons.GREEN);
            }
        }
        graphics.renderOutline(boardX - 1, y, boardSize + 2, boardSize + 2, 0xFF315E43);
        int inset = (cell - 16) / 2;
        for (int column = 0; column < 8; column++) {
            for (int row = 0; row < 8; row++) {
                ChessPiece piece = game.piece(column, row);
                if (piece != null) drawPiece(graphics, piece, boardX + column * cell + inset, boardY + row * cell + inset);
            }
        }
        for (ChessMove move : targets) {
            int left = boardX + move.toX() * cell;
            int top = boardY + move.toY() * cell;
            if (game.piece(move.toX(), move.toY()) != null) {
                graphics.renderOutline(left + 1, top + 1, cell - 2, cell - 2, GameButtons.GREEN);
            } else {
                graphics.fill(left + cell / 2 - 2, top + cell / 2 - 2, left + cell / 2 + 2, top + cell / 2 + 2, GameButtons.GREEN);
            }
        }
        for (int index = 0; index < 8; index++) {
            String file = Character.toString((char) ('a' + index));
            graphics.drawString(font, file, boardX + index * cell + (cell - font.width(file)) / 2 + 1, boardY + boardSize + 3, GameButtons.MUTED, false);
            String rank = Integer.toString(8 - index);
            graphics.drawString(font, rank, x + (RANK_GUTTER - 1 - font.width(rank)) / 2, boardY + index * cell + (cell - 7) / 2, GameButtons.MUTED, false);
        }
        int panelX = boardX + boardSize + 1 + PANEL_GAP;
        renderPanel(graphics, font, panelX, y, x + width - panelX, boardSize + 2, mouseX, mouseY);
        if (!promotionChoices.isEmpty()) renderPromotion(graphics, font, boardX, boardY, boardSize);
    }

    private void renderPanel(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        GameButtons.panel(graphics, x, y, width, height, false);
        int inner = width - PAD * 2;
        Component level = Component.translatable("computer.antos.chess.difficulty." + difficulty.id());
        Component move = Component.translatable("computer.antos.chess.move", game.fullmoveNumber());
        graphics.drawString(font, move, x + width - PAD - font.width(move), y + PAD, GameButtons.MUTED, false);
        graphics.drawString(font, GameButtons.trim(font, level, inner - font.width(move) - 6), x + PAD, y + PAD, GameButtons.GREEN, false);
        graphics.fill(x + PAD, y + PAD + 13, x + width - PAD, y + PAD + 14, 0xFF315E43);
        Component status;
        Component detail = Component.empty();
        int statusColor = GameButtons.PALE;
        ChessGame.Status state = game.status();
        if (resigned) {
            status = Component.translatable("computer.antos.chess.lost");
            detail = Component.translatable("computer.antos.chess.resigned");
        } else if (state == ChessGame.Status.WHITE_WON) {
            status = Component.translatable("computer.antos.chess.won");
            detail = Component.translatable("computer.antos.chess.checkmate");
            statusColor = GameButtons.GREEN;
        } else if (state == ChessGame.Status.BLACK_WON) {
            status = Component.translatable("computer.antos.chess.lost");
            detail = Component.translatable("computer.antos.chess.checkmate");
        } else if (state.draw()) {
            status = Component.translatable("computer.antos.chess.draw");
            detail = Component.translatable("computer.antos.chess.draw." + state.name().toLowerCase(Locale.ROOT));
        } else if (game.turn() == ChessColor.BLACK) {
            status = Component.translatable("computer.antos.chess.thinking");
            detail = Component.translatable("computer.antos.chess.thinking_hint");
        } else if (game.inCheck(ChessColor.WHITE)) {
            status = Component.translatable("computer.antos.chess.check");
            detail = Component.translatable("computer.antos.chess.check_hint");
            statusColor = GameButtons.GREEN;
        } else {
            status = Component.translatable("computer.antos.chess.your_move");
            detail = Component.translatable(selectedX < 0 ? "computer.antos.chess.select_hint" : "computer.antos.chess.target_hint");
        }
        graphics.drawString(font, GameButtons.trim(font, status, inner), x + PAD, y + PAD + 20, statusColor, false);
        GameButtons.wrap(graphics, font, detail, x + PAD, y + PAD + 32, inner, GameButtons.MUTED, 2);
        int buttonY = y + height - PAD - GameButtons.BUTTON_HEIGHT;
        int capturedTop = y + PAD + 58;
        if (capturedTop + 45 <= buttonY - 6) {
            graphics.drawString(font, Component.translatable("computer.antos.chess.captured"), x + PAD, capturedTop, GameButtons.MUTED, false);
            drawCaptured(graphics, ChessColor.BLACK, x + PAD, capturedTop + 11, inner);
            drawCaptured(graphics, ChessColor.WHITE, x + PAD, capturedTop + 29, inner);
        }
        if (finished) {
            int half = (inner - 4) / 2;
            buttons.button(graphics, font, x + PAD, buttonY, half, Component.translatable("computer.antos.games.again"), mouseX, mouseY, () -> start(difficulty));
            buttons.button(graphics, font, x + PAD + inner - half, buttonY, half, Component.translatable("computer.antos.games.menu"), mouseX, mouseY, this::menu);
        } else {
            buttons.button(graphics, font, x + PAD, buttonY, inner, Component.translatable("computer.antos.chess.resign"), mouseX, mouseY, this::resign);
        }
    }

    private void drawCaptured(GuiGraphics graphics, ChessColor color, int x, int y, int width) {
        int[] counts = new int[ChessPieceType.values().length];
        for (int column = 0; column < 8; column++) {
            for (int row = 0; row < 8; row++) {
                ChessPiece piece = game.piece(column, row);
                if (piece != null && piece.color() == color) counts[piece.type().ordinal()]++;
            }
        }
        int promoted = 0;
        for (ChessPieceType type : CAPTURE_ORDER) promoted += Math.max(0, counts[type.ordinal()] - START_COUNTS[type.ordinal()]);
        List<ChessPieceType> captured = new ArrayList<>();
        for (ChessPieceType type : CAPTURE_ORDER) {
            int missing = START_COUNTS[type.ordinal()] - counts[type.ordinal()];
            if (type == ChessPieceType.PAWN) missing -= promoted;
            for (int index = 0; index < missing; index++) captured.add(type);
        }
        if (captured.isEmpty()) return;
        int stride = captured.size() == 1 ? 16 : Math.max(4, Math.min(14, (width - 16) / (captured.size() - 1)));
        for (int index = 0; index < captured.size(); index++) drawPiece(graphics, new ChessPiece(color, captured.get(index)), x + index * stride, y);
    }

    private void renderPromotion(GuiGraphics graphics, Font font, int boardX, int boardY, int boardSize) {
        int square = cell + 2;
        int spacing = square + 4;
        Component title = Component.translatable("computer.antos.chess.promote");
        int width = Math.max(PROMOTIONS.length * spacing - 4 + PAD * 2, font.width(title) + PAD * 2);
        int height = PAD + 12 + square + PAD;
        int left = boardX + (boardSize - width) / 2;
        int top = boardY + (boardSize - height) / 2;
        graphics.fill(left, top, left + width, top + height, 0xF0061206);
        graphics.renderOutline(left, top, width, height, GameButtons.GREEN);
        graphics.drawString(font, title, left + (width - font.width(title)) / 2, top + PAD, GameButtons.PALE, false);
        int squaresLeft = left + (width - (PROMOTIONS.length * spacing - 4)) / 2;
        for (int index = 0; index < PROMOTIONS.length; index++) {
            int squareX = squaresLeft + index * spacing;
            int squareY = top + PAD + 12;
            ChessPieceType type = PROMOTIONS[index];
            GameButtons.panel(graphics, squareX, squareY, square, square, false);
            drawPiece(graphics, new ChessPiece(ChessColor.WHITE, type), squareX + (square - 16) / 2, squareY + (square - 16) / 2);
            buttons.hitOnly(squareX, squareY, square, square, () -> promote(type));
        }
    }

    private static void drawPiece(GuiGraphics graphics, ChessPiece piece, int x, int y) {
        float[] tint = ComputerTheme.active().tint();
        graphics.setColor(tint[0], tint[1], tint[2], 1.0F);
        graphics.blit(TEXTURES.get(piece.color()).get(piece.type()), x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (game != null && !promotionChoices.isEmpty()) {
            if (!buttons.click(mouseX, mouseY)) promotionChoices = List.of();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && buttons.click(mouseX, mouseY)) return true;
        if (game == null) return false;
        int column = (int) Math.floor((mouseX - boardLeft) / cell);
        int row = (int) Math.floor((mouseY - boardTop) / cell);
        if (column < 0 || column > 7 || row < 0 || row > 7) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            clearSelection();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !playerCanMove()) return true;
        List<ChessMove> matching = new ArrayList<>();
        for (ChessMove move : targets) if (move.toX() == column && move.toY() == row) matching.add(move);
        if (!matching.isEmpty()) {
            if (matching.get(0).isPromotion()) promotionChoices = matching;
            else playerMove(matching.get(0));
            return true;
        }
        ChessPiece piece = game.piece(column, row);
        if (piece != null && piece.color() == ChessColor.WHITE && !(column == selectedX && row == selectedY)) {
            selectedX = column;
            selectedY = row;
            targets = game.legalMovesFrom(column, row);
        } else {
            clearSelection();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (game != null && !promotionChoices.isEmpty()) {
            ChessPieceType type = switch (keyCode) {
                case GLFW.GLFW_KEY_Q -> ChessPieceType.QUEEN;
                case GLFW.GLFW_KEY_R -> ChessPieceType.ROOK;
                case GLFW.GLFW_KEY_B -> ChessPieceType.BISHOP;
                case GLFW.GLFW_KEY_N -> ChessPieceType.KNIGHT;
                default -> null;
            };
            if (type != null) promote(type);
            return type != null;
        }
        if (game == null && keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + ChessDifficulty.values().length) {
            start(ChessDifficulty.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        if (game != null && keyCode == GLFW.GLFW_KEY_R) {
            start(difficulty);
            return true;
        }
        if (game != null && keyCode == GLFW.GLFW_KEY_M) {
            menu();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        generation++;
        thinking = null;
    }

    private boolean playerCanMove() {
        return game != null && !finished && thinking == null && game.turn() == ChessColor.WHITE;
    }

    private void promote(ChessPieceType type) {
        for (ChessMove move : promotionChoices) {
            if (move.promotion() == type) {
                promotionChoices = List.of();
                playerMove(move);
                return;
            }
        }
    }

    private void playerMove(ChessMove move) {
        if (!game.play(move)) return;
        lastMove = move;
        playerMovedAt = System.currentTimeMillis();
        clearSelection();
        checkFinished();
    }

    private void checkFinished() {
        if (finished || !game.status().over()) return;
        finished = true;
        if (game.status() == ChessGame.Status.WHITE_WON && !resigned) rewards.claim(difficulty.rewardMode());
    }

    private void resign() {
        if (finished) return;
        generation++;
        thinking = null;
        resigned = true;
        game.resign(ChessColor.WHITE);
        clearSelection();
        promotionChoices = List.of();
        checkFinished();
    }

    private void start(ChessDifficulty level) {
        generation++;
        difficulty = level;
        game = new ChessGame();
        thinking = null;
        lastMove = null;
        resigned = false;
        finished = false;
        promotionChoices = List.of();
        clearSelection();
    }

    private void menu() {
        generation++;
        thinking = null;
        game = null;
        promotionChoices = List.of();
        clearSelection();
    }

    private void clearSelection() {
        selectedX = -1;
        selectedY = -1;
        targets = List.of();
    }
}
