package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Arrays;
import java.util.Random;

public final class FliptrisProgram implements ComputerGame.Session {
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int RESTART_DELAY = 12;
    private static final int[][][] SHAPES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}
    };
    private final int[][] board = new int[ROWS][COLS];
    private final Random random = new Random();
    private int[] bag = new int[0];
    private int piece;
    private int nextPiece = -1;
    private int rotation;
    private int pieceX;
    private int pieceY;
    private int score;
    private int best;
    private int lines;
    private int ticks;
    private int overTicks;
    private int clearTicks;
    private int lastCleared;
    private boolean paused;
    private boolean gameOver;

    public FliptrisProgram() {
        reset();
    }

    @Override
    public void tick() {
        if (gameOver) {
            if (overTicks < RESTART_DELAY) overTicks++;
            return;
        }
        if (paused) return;
        ticks++;
        if (clearTicks > 0) clearTicks--;
        if (ticks % Math.max(3, 18 - level()) == 0) stepGravity();
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height) {
        int right = x + width - 1;
        int fieldTop = y + 25;
        int fieldBottom = y + height - 15;
        g.fill(x, y, right, y + height, Ui.GAME_FILL);
        g.renderOutline(x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
        String scoreText = tr("computer.antos.fliptris.score", score);
        String bestText = tr("computer.antos.fliptris.best", best);
        g.drawString(font, fit(font, tr("computer.antos.fliptris.title"), width - font.width(scoreText) - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        g.drawString(font, scoreText, right - font.width(scoreText) - 7, y + 4, Ui.TEXT, false);
        g.drawString(font, fit(font, tr("computer.antos.fliptris.stats", lines, level()), width - font.width(bestText) - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        g.drawString(font, bestText, right - font.width(bestText) - 7, y + 15, Ui.TEXT_MUTED, false);
        g.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);

        int sideWidth = 44;
        int cell = Math.max(2, Math.min(12, Math.min((fieldBottom - fieldTop - 4) / ROWS, (width - sideWidth - 24) / COLS)));
        int boardW = cell * COLS;
        int boardH = cell * ROWS;
        int left = x + (width - boardW - sideWidth) / 2;
        int top = fieldTop + 2 + Math.max(0, (fieldBottom - fieldTop - 4 - boardH) / 2);
        g.fill(left - 1, top - 1, left + boardW + 1, top + boardH + 1, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.renderOutline(left - 2, top - 2, boardW + 4, boardH + 4, Ui.GAME_OUTLINE);
        for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLS; col++) {
            int value = board[row][col];
            if (value == 0) g.fill(left + col * cell, top + row * cell, left + col * cell + cell - 1, top + row * cell + cell - 1, Ui.alpha(Ui.SURFACE_SUNKEN, 0x90));
            else drawCell(g, left, top, cell, col, row, pieceColor(value - 1));
        }
        if (!gameOver) {
            int ghostY = pieceY;
            while (valid(pieceX, ghostY - 1, piece, rotation)) ghostY--;
            for (int[] block : blocks(piece, rotation)) {
                int col = pieceX + block[0];
                int row = ghostY + block[1];
                if (inside(col, row)) g.renderOutline(left + col * cell, top + row * cell, cell - 1, cell - 1, Ui.alpha(pieceColor(piece), 0x90));
            }
            for (int[] block : blocks(piece, rotation)) {
                int col = pieceX + block[0];
                int row = pieceY + block[1];
                if (inside(col, row)) drawCell(g, left, top, cell, col, row, pieceColor(piece));
            }
        }
        int sideX = left + boardW + 10;
        if (sideX + sideWidth - 6 <= right - 4) {
            g.drawString(font, tr("computer.antos.fliptris.next"), sideX, top, Ui.TEXT_MUTED, false);
            int mini = Math.max(3, Math.min(7, cell - 2));
            int boxSize = mini * 4 + 6;
            g.fill(sideX, top + 11, sideX + boxSize, top + 11 + boxSize, Ui.alpha(Ui.BACKGROUND, 0xC0));
            g.renderOutline(sideX, top + 11, boxSize, boxSize, Ui.GAME_OUTLINE);
            if (!gameOver) drawMiniPiece(g, sideX + 3, top + 14, nextPiece, mini);
        }

        if (gameOver || paused) {
            int boxWidth = Math.max(20, Math.min(width - 24, 150));
            int boxX = x + (width - boxWidth) / 2;
            int boxY = Math.max(y + 2, y + height / 2 - 15);
            g.fill(boxX, boxY, boxX + boxWidth, boxY + 30, Ui.alpha(Ui.BACKGROUND, 0xE8));
            g.renderOutline(boxX, boxY, boxWidth, 30, gameOver ? Ui.ERROR : Ui.ACCENT);
            g.drawCenteredString(font, fit(font, tr(gameOver ? "computer.antos.fliptris.game_over" : "computer.antos.fliptris.paused"), boxWidth - 8), x + width / 2, boxY + 5, gameOver ? Ui.ERROR : Ui.ACCENT);
            g.drawCenteredString(font, fit(font, tr(gameOver ? "computer.antos.fliptris.retry" : "computer.antos.fliptris.resume"), boxWidth - 8), x + width / 2, boxY + 17, Ui.TEXT_BRIGHT);
        } else {
            String hint = clearTicks > 0 ? tr(lastCleared == 4 ? "computer.antos.fliptris.four_lines" : "computer.antos.fliptris.cleared", lastCleared) : tr("computer.antos.fliptris.hint");
            g.drawCenteredString(font, fit(font, hint, width - 14), x + width / 2, y + height - 11, clearTicks > 0 ? Ui.ACCENT : Ui.TEXT_MUTED);
        }
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == 82) { reset(); return true; }
        if (keyCode == 80) { if (!gameOver) paused = !paused; return true; }
        boolean confirm = keyCode == 32 || keyCode == 257 || keyCode == 335;
        if (gameOver) {
            if (confirm && overTicks >= RESTART_DELAY) reset();
            return confirm;
        }
        if (paused) return false;
        if (keyCode == 263 || keyCode == 65) return move(-1);
        if (keyCode == 262 || keyCode == 68) return move(1);
        if (keyCode == 265 || keyCode == 87) { softDrop(); return true; }
        if (keyCode == 88 || keyCode == 264 || keyCode == 83 || keyCode == 90) { rotate(keyCode == 90 ? -1 : 1); return true; }
        if (confirm) { hardDrop(); return true; }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (gameOver && overTicks >= RESTART_DELAY) { reset(); return true; }
        if (paused) { paused = false; return true; }
        return false;
    }

    private void reset() {
        for (int[] row : board) Arrays.fill(row, 0);
        score = 0;
        lines = 0;
        ticks = 0;
        overTicks = 0;
        clearTicks = 0;
        paused = false;
        gameOver = false;
        bag = new int[0];
        nextPiece = -1;
        spawn();
    }

    private void spawn() {
        piece = nextPiece < 0 ? drawPiece() : nextPiece;
        nextPiece = drawPiece();
        rotation = 0;
        pieceX = 3;
        pieceY = ROWS - 2;
        if (!valid(pieceX, pieceY, piece, rotation)) endGame();
    }

    private void endGame() {
        gameOver = true;
        overTicks = 0;
        best = Math.max(best, score);
        sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F);
    }

    private int drawPiece() {
        if (bag.length == 0) {
            bag = new int[]{0, 1, 2, 3, 4, 5, 6};
            for (int i = bag.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int value = bag[i]; bag[i] = bag[j]; bag[j] = value;
            }
        }
        int result = bag[bag.length - 1];
        bag = Arrays.copyOf(bag, bag.length - 1);
        return result;
    }

    private boolean move(int dx) {
        if (valid(pieceX + dx, pieceY, piece, rotation)) pieceX += dx;
        return true;
    }

    private void rotate(int direction) {
        if (piece == 1) return;
        int next = Math.floorMod(rotation + direction, 4);
        int[][] kicks = {{0, 0}, {-1, 0}, {1, 0}, {-2, 0}, {2, 0}, {0, 1}, {0, -1}, {0, 2}, {0, -2}};
        for (int[] kick : kicks) if (valid(pieceX + kick[0], pieceY + kick[1], piece, next)) {
            pieceX += kick[0]; pieceY += kick[1]; rotation = next;
            sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.18F, 0.18F);
            return;
        }
    }

    private void stepGravity() {
        if (valid(pieceX, pieceY - 1, piece, rotation)) pieceY--;
        else lockPiece();
    }

    private void softDrop() {
        if (valid(pieceX, pieceY - 1, piece, rotation)) {
            pieceY--;
            score++;
        } else lockPiece();
    }

    private void hardDrop() {
        int distance = 0;
        while (valid(pieceX, pieceY - 1, piece, rotation)) { pieceY--; distance++; }
        score += distance * 2;
        lockPiece();
    }

    private void lockPiece() {
        for (int[] block : blocks(piece, rotation)) {
            int col = pieceX + block[0];
            int row = pieceY + block[1];
            if (!inside(col, row)) { endGame(); return; }
            board[row][col] = piece + 1;
        }
        int cleared = clearLines();
        if (cleared > 0) {
            int[] points = {0, 100, 300, 500, 800};
            score += points[cleared] * level();
            lines += cleared;
            lastCleared = cleared;
            clearTicks = 30;
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, cleared == 4 ? 1.45F : 1.0F + cleared * 0.12F, 0.5F);
        } else sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.9F, 0.2F);
        best = Math.max(best, score);
        spawn();
    }

    private int clearLines() {
        int count = 0;
        for (int row = 0; row < ROWS; row++) {
            if (!full(row)) continue;
            count++;
            for (int move = row; move < ROWS - 1; move++) board[move] = board[move + 1];
            board[ROWS - 1] = new int[COLS];
            row--;
        }
        return count;
    }

    private boolean full(int row) { for (int value : board[row]) if (value == 0) return false; return true; }
    private int level() { return Math.min(15, 1 + lines / 10); }
    private boolean inside(int col, int row) { return col >= 0 && col < COLS && row >= 0 && row < ROWS; }

    private boolean valid(int x, int y, int type, int turn) {
        for (int[] block : blocks(type, turn)) {
            int col = x + block[0];
            int row = y + block[1];
            if (!inside(col, row) || board[row][col] != 0) return false;
        }
        return true;
    }

    private static int[][] blocks(int type, int turn) {
        int[][] result = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int px = SHAPES[type][i][0];
            int py = SHAPES[type][i][1];
            for (int r = 0; r < turn; r++) { int next = 3 - py; py = px; px = next; }
            result[i][0] = px;
            result[i][1] = py;
        }
        return result;
    }

    private static void drawCell(GuiGraphics g, int left, int top, int cell, int col, int row, int color) {
        int px = left + col * cell;
        int py = top + row * cell;
        g.fill(px, py, px + cell - 1, py + cell - 1, color);
        if (cell >= 5) {
            g.fill(px, py, px + cell - 1, py + 1, Ui.alpha(Ui.TEXT_BRIGHT, 0x40));
            g.fill(px, py + cell - 2, px + cell - 1, py + cell - 1, Ui.alpha(Ui.BACKGROUND, 0x60));
        }
    }

    private static void drawMiniPiece(GuiGraphics g, int x, int y, int type, int size) {
        for (int[] block : blocks(type, 0)) {
            int px = x + block[0] * size;
            int py = y + block[1] * size;
            g.fill(px, py, px + size - 1, py + size - 1, pieceColor(type));
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        if (width <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private static int pieceColor(int type) {
        return switch (type) {
            case 0 -> Ui.ACCENT;
            case 1 -> Ui.mix(Ui.ACCENT, Ui.TEXT_BRIGHT, 0.30F);
            case 2 -> Ui.mix(Ui.ACCENT, Ui.TEXT_STRONG, 0.38F);
            case 3 -> Ui.mix(Ui.ACCENT, Ui.BOARD_SELECTED, 0.45F);
            case 4 -> Ui.mix(Ui.TEXT_STRONG, Ui.ACCENT, 0.24F);
            case 5 -> Ui.mix(Ui.ACCENT, Ui.TEXT, 0.48F);
            default -> Ui.mix(Ui.ACCENT, Ui.GAME_OUTLINE, 0.34F);
        };
    }

    private static void sound(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        var preferences = AntOSSettings.preferences();
        if (!preferences.interfaceSounds || preferences.interfaceVolume <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume * preferences.interfaceVolume / 100.0F));
    }
}
