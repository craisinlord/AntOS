package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.api.client.game.ComputerGame;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Random;

public final class MinesweeperSession implements ComputerGame.Session {
    enum Mode {
        SMALL("small", 9, 9, 10),
        LARGE("large", 16, 10, 25);

        final String id;
        final int columns;
        final int rows;
        final int mines;

        Mode(String id, int columns, int rows, int mines) {
            this.id = id;
            this.columns = columns;
            this.rows = rows;
            this.mines = mines;
        }

        String rewardMode() {
            return "minesweeper_" + id;
        }
    }

    private static final int PANEL_GAP = 10;
    private static final int PANEL_MIN = 104;
    private static final int PAD = 8;

    private final GameButtons buttons = new GameButtons();
    private final GameRewardTracker rewards = new GameRewardTracker();
    private final Random random = new Random();
    private Mode mode;
    private boolean[][] mines;
    private boolean[][] revealed;
    private boolean[][] flagged;
    private int[][] adjacent;
    private boolean generated;
    private boolean over;
    private boolean won;
    private int explodedX = -1;
    private int explodedY = -1;
    private int revealedSafe;
    private long startedAt;
    private long finishedAt;
    private int cell = 16;

    @Override
    public void tick() {
        rewards.tick();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        render(graphics, font, x, y, width, height, -1, -1);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        buttons.begin(x, y);
        if (mode == null) renderMenu(graphics, font, x, y, width, mouseX, mouseY);
        else renderBoard(graphics, font, x, y, width, height, mouseX, mouseY);
        rewards.render(graphics, font, x, y, width, height);
    }

    private void renderMenu(GuiGraphics graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
        graphics.drawString(font, GameButtons.trim(font, Component.translatable("computer.antos.minesweeper.pick"), width - 8), x, y, Ui.TEXT, false);
        int top = y + 16;
        for (Mode option : Mode.values()) {
            buttons.row(graphics, font, x, top, width - 8,
                    Component.translatable("computer.antos.minesweeper.mode." + option.id),
                    Component.translatable("computer.antos.minesweeper.size", option.columns, option.rows, option.mines),
                    GameRewardTracker.dailyBonus(option.rewardMode()), mouseX, mouseY, () -> start(option));
            top += GameButtons.ROW_HEIGHT + GameButtons.ROW_GAP;
        }
    }

    private void renderBoard(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        cell = Math.max(10, Math.min(18, Math.min((height - 2) / mode.rows, (width - 2 - PANEL_GAP - PANEL_MIN) / mode.columns)));
        int boardWidth = mode.columns * cell;
        int boardHeight = mode.rows * cell;
        int hoverColumn = Math.floorDiv(mouseX - 1, cell);
        int hoverRow = Math.floorDiv(mouseY - 1, cell);
        for (int column = 0; column < mode.columns; column++) {
            for (int row = 0; row < mode.rows; row++) {
                drawCell(graphics, font, column, row, x + 1 + column * cell, y + 1 + row * cell, !over && column == hoverColumn && row == hoverRow);
            }
        }
        graphics.renderOutline(x, y, boardWidth + 2, boardHeight + 2, Ui.GAME_OUTLINE);
        int panelX = x + boardWidth + 2 + PANEL_GAP;
        renderPanel(graphics, font, panelX, y, x + width - panelX, boardHeight + 2, mouseX, mouseY);
    }

    private void renderPanel(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        GameButtons.panel(graphics, x, y, width, height, false);
        int inner = width - PAD * 2;
        graphics.drawString(font, GameButtons.trim(font, Component.translatable("computer.antos.minesweeper.mode." + mode.id), inner), x + PAD, y + PAD, Ui.TEXT_STRONG, false);
        graphics.fill(x + PAD, y + PAD + 13, x + width - PAD, y + PAD + 14, Ui.GAME_OUTLINE);
        int flags = 0;
        for (int column = 0; column < mode.columns; column++) for (int row = 0; row < mode.rows; row++) if (flagged[column][row]) flags++;
        long elapsed = !generated ? 0L : ((over ? finishedAt : System.currentTimeMillis()) - startedAt) / 1000L;
        stat(graphics, font, x, y + PAD + 20, width, Component.translatable("computer.antos.minesweeper.mines_label"), Integer.toString(Math.max(0, mode.mines - flags)));
        stat(graphics, font, x, y + PAD + 32, width, Component.translatable("computer.antos.minesweeper.time_label"), String.format(java.util.Locale.ROOT, "%d:%02d", elapsed / 60L, elapsed % 60L));
        int statusTop = y + PAD + 50;
        int buttonsTop = y + height - PAD - GameButtons.BUTTON_HEIGHT * 2 - 4;
        int maxLines = Math.max(0, (buttonsTop - 4 - statusTop) / 10);
        if (over) {
            graphics.drawString(font, GameButtons.trim(font, Component.translatable(won ? "computer.antos.minesweeper.cleared" : "computer.antos.minesweeper.boom"), inner), x + PAD, statusTop, won ? Ui.ACCENT : Ui.TEXT, false);
            GameButtons.wrap(graphics, font, Component.translatable("computer.antos.minesweeper.again"), x + PAD, statusTop + 12, inner, Ui.TEXT_MUTED, Math.max(0, maxLines - 1));
        } else {
            GameButtons.wrap(graphics, font, Component.translatable("computer.antos.minesweeper.hint"), x + PAD, statusTop, inner, Ui.TEXT_MUTED, maxLines);
        }
        buttons.button(graphics, font, x + PAD, buttonsTop, inner, Component.translatable("computer.antos.minesweeper.new"), mouseX, mouseY, () -> start(mode));
        buttons.button(graphics, font, x + PAD, buttonsTop + GameButtons.BUTTON_HEIGHT + 4, inner, Component.translatable("computer.antos.games.menu"), mouseX, mouseY, () -> mode = null);
    }

    private static void stat(GuiGraphics graphics, Font font, int x, int y, int width, Component label, String value) {
        graphics.drawString(font, label, x + PAD, y, Ui.TEXT_MUTED, false);
        graphics.drawString(font, value, x + width - PAD - font.width(value), y, Ui.TEXT, false);
    }

    private void drawCell(GuiGraphics graphics, Font font, int column, int row, int left, int top, boolean hover) {
        int size = cell;
        if (!revealed[column][row]) {
            graphics.fill(left, top, left + size, top + size, Ui.MINE_HIDDEN_SHADE);
            graphics.fill(left, top, left + size - 1, top + size - 1, Ui.MINE_HIDDEN_LIGHT);
            graphics.fill(left + 1, top + 1, left + size - 1, top + size - 1, hover ? Ui.SELECTION : Ui.MINE_HIDDEN);
            if (flagged[column][row]) drawFlag(graphics, left, top, size);
            else if (over && !won && mines[column][row]) drawMine(graphics, left, top, size, Ui.TEXT);
            if (over && flagged[column][row] && !mines[column][row]) {
                graphics.fill(left + 2, top + size / 2, left + size - 2, top + size / 2 + 1, Ui.TEXT);
            }
            return;
        }
        graphics.fill(left, top, left + size, top + size, Ui.SURFACE_DEEP);
        boolean exploded = column == explodedX && row == explodedY;
        graphics.fill(left + 1, top + 1, left + size, top + size, exploded ? Ui.ACCENT : Ui.SURFACE_SUNKEN);
        if (mines[column][row]) drawMine(graphics, left, top, size, exploded ? Ui.SURFACE_SUNKEN : Ui.TEXT);
        else if (adjacent[column][row] > 0) {
            String number = Integer.toString(adjacent[column][row]);
            graphics.drawString(font, number, left + (size - font.width(number)) / 2 + 1, top + (size - 7) / 2 + 1, numberColor(adjacent[column][row]), false);
        }
    }

    private static void drawFlag(GuiGraphics graphics, int left, int top, int size) {
        int poleX = left + size / 2;
        int pad = Math.max(2, (size - 10) / 2);
        graphics.fill(poleX, top + pad, poleX + 1, top + size - pad, Ui.TEXT);
        graphics.fill(poleX - 3, top + size - pad - 1, poleX + 4, top + size - pad, Ui.TEXT);
        graphics.fill(poleX - 4, top + pad, poleX, top + pad + 2, Ui.ACCENT);
        graphics.fill(poleX - 3, top + pad + 2, poleX, top + pad + 3, Ui.ACCENT);
        graphics.fill(poleX - 2, top + pad + 3, poleX, top + pad + 4, Ui.ACCENT);
    }

    private static void drawMine(GuiGraphics graphics, int left, int top, int size, int color) {
        int centerX = left + size / 2;
        int centerY = top + size / 2;
        graphics.fill(centerX - 2, centerY - 3, centerX + 3, centerY + 4, color);
        graphics.fill(centerX - 3, centerY - 2, centerX + 4, centerY + 3, color);
        graphics.fill(centerX - 4, centerY, centerX + 5, centerY + 1, color);
        graphics.fill(centerX, centerY - 4, centerX + 1, centerY + 5, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && buttons.click(mouseX, mouseY)) return true;
        if (mode == null) return false;
        int column = (int) Math.floor((mouseX - 1) / cell);
        int row = (int) Math.floor((mouseY - 1) / cell);
        if (column < 0 || row < 0 || column >= mode.columns || row >= mode.rows) return false;
        if (over) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) start(mode);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!revealed[column][row]) flagged[column][row] = !flagged[column][row];
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || revealed[column][row]) {
            chord(column, row);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            reveal(column, row);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_R && mode != null) {
            start(mode);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            mode = null;
            return true;
        }
        if (mode == null && keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + Mode.values().length) {
            start(Mode.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        return false;
    }

    private void start(Mode next) {
        mode = next;
        mines = new boolean[next.columns][next.rows];
        revealed = new boolean[next.columns][next.rows];
        flagged = new boolean[next.columns][next.rows];
        adjacent = new int[next.columns][next.rows];
        generated = false;
        over = false;
        won = false;
        explodedX = -1;
        explodedY = -1;
        revealedSafe = 0;
        startedAt = 0L;
        finishedAt = 0L;
    }

    private void generate(int safeColumn, int safeRow) {
        int placed = 0;
        while (placed < mode.mines) {
            int column = random.nextInt(mode.columns);
            int row = random.nextInt(mode.rows);
            if (mines[column][row] || Math.abs(column - safeColumn) <= 1 && Math.abs(row - safeRow) <= 1) continue;
            mines[column][row] = true;
            placed++;
        }
        for (int column = 0; column < mode.columns; column++) {
            for (int row = 0; row < mode.rows; row++) {
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (valid(column + dx, row + dy) && mines[column + dx][row + dy]) count++;
                adjacent[column][row] = count;
            }
        }
        generated = true;
        startedAt = System.currentTimeMillis();
    }

    private void reveal(int column, int row) {
        if (!valid(column, row) || revealed[column][row] || flagged[column][row] || over) return;
        if (!generated) generate(column, row);
        if (mines[column][row]) {
            revealed[column][row] = true;
            explodedX = column;
            explodedY = row;
            finish(false);
            return;
        }
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        pending.add(new int[]{column, row});
        while (!pending.isEmpty()) {
            int[] cell = pending.poll();
            int cx = cell[0];
            int cy = cell[1];
            if (!valid(cx, cy) || revealed[cx][cy] || flagged[cx][cy] || mines[cx][cy]) continue;
            revealed[cx][cy] = true;
            revealedSafe++;
            if (adjacent[cx][cy] != 0) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) pending.add(new int[]{cx + dx, cy + dy});
        }
        if (revealedSafe >= mode.columns * mode.rows - mode.mines) finish(true);
    }

    private void chord(int column, int row) {
        if (!revealed[column][row] || adjacent[column][row] == 0) return;
        int flags = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (valid(column + dx, row + dy) && flagged[column + dx][row + dy]) flags++;
        if (flags != adjacent[column][row]) return;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) reveal(column + dx, row + dy);
    }

    private void finish(boolean victory) {
        over = true;
        won = victory;
        finishedAt = System.currentTimeMillis();
        if (victory) {
            for (int column = 0; column < mode.columns; column++) for (int row = 0; row < mode.rows; row++) if (mines[column][row]) flagged[column][row] = true;
            rewards.claim(mode.rewardMode());
        }
    }

    private boolean valid(int column, int row) {
        return column >= 0 && row >= 0 && column < mode.columns && row < mode.rows;
    }

    private static int numberColor(int count) {
        return switch (count) {
            case 1 -> Ui.ACCENT;
            case 2 -> Ui.TEXT;
            case 3 -> Ui.MINE_NUMBER_3;
            case 4 -> Ui.MINE_NUMBER_4;
            case 5 -> Ui.MINE_NUMBER_5;
            case 6 -> Ui.TEXT_BRIGHT;
            case 7 -> Ui.MINE_NUMBER_7;
            case 8 -> Ui.MINE_NUMBER_8;
            default -> 0;
        };
    }
}
