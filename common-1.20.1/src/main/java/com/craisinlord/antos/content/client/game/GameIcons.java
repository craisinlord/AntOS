package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.GameIcon;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class GameIcons {
    public static final GameIcon BLOCKLE = GameIcon.custom(GameIcons::blockle);
    public static final GameIcon CHESS = GameIcon.texture(new ResourceLocation("antos", "textures/gui/chess/white_knight.png"), 16, 16);
    public static final GameIcon MINESWEEPER = GameIcon.custom(GameIcons::minesweeper);
    public static final GameIcon CAKE_STACK = GameIcon.item(new ResourceLocation("minecraft", "cake"));
    public static final GameIcon NETHER_BREAKOUT = GameIcon.layered(GameIcon.item(new ResourceLocation("minecraft", "nether_gold_ore")),
            GameIcon.item(new ResourceLocation("minecraft", "fire_charge")));
    public static final GameIcon FLIPTRIS = GameIcon.custom(GameIcons::fliptris);
    public static final GameIcon PISTON_PUSH = GameIcon.item(new ResourceLocation("minecraft", "piston"));
    public static final GameIcon REDSTONE_RUSH = GameIcon.layered(GameIcon.item(new ResourceLocation("minecraft", "redstone_lamp")),
            GameIcon.item(new ResourceLocation("minecraft", "redstone")));
    public static final GameIcon FLAPPY_GHAST = GameIcon.custom(FlappyGhastRenderer::paintIcon);
    public static final GameIcon ORE_2048 = GameIcon.layered(GameIcon.item(new ResourceLocation("minecraft", "diamond_ore")),
            GameIcon.item(new ResourceLocation("minecraft", "beacon")));
    public static final GameIcon QUIZ = GameIcon.item(new ResourceLocation("minecraft", "writable_book"));
    public static final GameIcon ADVENTURE = GameIcon.item(new ResourceLocation("minecraft", "compass"));
    public static final GameIcon PROGRAM = GameIcon.sprite(new ResourceLocation("antos", "textures/gui/desktop/terminal.png"));

    private static final int GRID = 12;
    private static final int[][] FLIPTRIS_BOARD = {
            {0, 0, 1, 1, 1, 0},
            {0, 0, 0, 1, 0, 0},
            {0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 4},
            {2, 2, 0, 3, 3, 4},
            {2, 2, 3, 3, 5, 4}
    };

    private GameIcons() {
    }

    public static GameIcon program(String type) {
        return switch (type) {
            case "quiz" -> QUIZ;
            case "adventure" -> ADVENTURE;
            default -> PROGRAM;
        };
    }

    private static void blockle(GuiGraphics g, int x, int y, int size, int ticks, boolean hovered) {
        int unit = unit(size);
        int left = x + (size - GRID * unit) / 2;
        int top = y + (size - GRID * unit) / 2;
        int[][] rows = {
                {Ui.GAME_ABSENT, Ui.GAME_PRESENT, Ui.GAME_ABSENT},
                {Ui.GAME_PRESENT, Ui.GAME_CORRECT, Ui.GAME_ABSENT},
                {Ui.GAME_CORRECT, Ui.GAME_CORRECT, Ui.GAME_CORRECT}
        };
        int revealed = hovered ? Math.min(3, ticks / 4 % 5) : 3;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int color = row < 2 || column < revealed ? rows[row][column] : Ui.SURFACE;
                cell(g, left, top, unit, column * 4 + 1, row * 4 + 1, 3, 3, color);
            }
        }
    }

    private static void minesweeper(GuiGraphics g, int x, int y, int size, int ticks, boolean hovered) {
        int unit = unit(size);
        int left = x + (size - GRID * unit) / 2;
        int top = y + (size - GRID * unit) / 2;
        cell(g, left, top, unit, 0, 0, 12, 12, Ui.MINE_HIDDEN_SHADE);
        cell(g, left, top, unit, 0, 0, 11, 11, Ui.MINE_HIDDEN_LIGHT);
        cell(g, left, top, unit, 1, 1, 10, 10, hovered ? Ui.SELECTION : Ui.MINE_HIDDEN);
        int wave = hovered && ticks / 6 % 2 == 0 ? 1 : 0;
        cell(g, left, top, unit, 6, 2, 1, 7, Ui.TEXT);
        cell(g, left, top, unit, 4, 9, 5, 1, Ui.TEXT);
        cell(g, left, top, unit, 3, 2 + wave, 3, 1, Ui.ACCENT);
        cell(g, left, top, unit, 4, 3 + wave, 2, 1, Ui.ACCENT);
        cell(g, left, top, unit, 5, 4 + wave, 1, 1, Ui.ACCENT);
    }

    private static void fliptris(GuiGraphics g, int x, int y, int size, int ticks, boolean hovered) {
        int unit = unit(size);
        int left = x + (size - GRID * unit) / 2;
        int top = y + (size - GRID * unit) / 2;
        cell(g, left, top, unit, 0, 0, 12, 12, Ui.SURFACE_SUNKEN);
        boolean flipped = hovered && ticks / 10 % 2 == 1;
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 6; column++) {
                int piece = FLIPTRIS_BOARD[flipped ? 5 - row : row][column];
                if (piece == 0) continue;
                int px = left + column * 2 * unit;
                int py = top + row * 2 * unit;
                int inset = unit > 1 ? 1 : 0;
                g.fill(px, py, px + 2 * unit - inset, py + 2 * unit - inset, pieceColor(piece - 1));
            }
        }
    }

    private static int pieceColor(int type) {
        return switch (type) {
            case 0 -> Ui.ACCENT;
            case 1 -> Ui.mix(Ui.ACCENT, Ui.TEXT_BRIGHT, 0.30F);
            case 2 -> Ui.mix(Ui.ACCENT, Ui.TEXT_STRONG, 0.38F);
            case 3 -> Ui.mix(Ui.ACCENT, Ui.BOARD_SELECTED, 0.45F);
            default -> Ui.mix(Ui.TEXT_STRONG, Ui.ACCENT, 0.24F);
        };
    }

    private static int unit(int size) {
        return Math.max(1, size / GRID);
    }

    private static void cell(GuiGraphics g, int left, int top, int unit, int x, int y, int w, int h, int color) {
        g.fill(left + x * unit, top + y * unit, left + (x + w) * unit, top + (y + h) * unit, color);
    }
}
