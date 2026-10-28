package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.game.logic.redstonerush.Piece;
import com.craisinlord.antos.content.client.game.logic.redstonerush.RedstoneRushLayout;
import com.craisinlord.antos.content.client.game.logic.redstonerush.RedstoneRushLogic;
import com.craisinlord.antos.content.client.game.logic.redstonerush.Tile;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

final class RedstoneRushTutorial {
    enum Action { NONE, NEXT, BACK, CLOSE }

    private static final String[] PAGES = {"pick", "flow", "power", "cross", "fast"};
    private static final int PAD = 7;
    private static final int HOLD_TICKS = 30;

    private int page;
    private RedstoneRushLogic[] scene = new RedstoneRushLogic[0];
    private int hold;

    void open() {
        page = 0;
        build();
    }

    boolean last() {
        return page == PAGES.length - 1;
    }

    void next() {
        if (last()) return;
        page++;
        build();
    }

    void back() {
        if (page == 0) return;
        page--;
        build();
    }

    void tick() {
        if (page == 0) return;
        boolean running = false;
        for (RedstoneRushLogic logic : scene) {
            if (logic.state() == RedstoneRushLogic.State.FLOWING) {
                logic.advance(50L);
                running = true;
            }
        }
        if (running) {
            hold = 0;
            return;
        }
        if (++hold > HOLD_TICKS) build();
    }

    Action key(int keyCode) {
        if (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_D || keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER || keyCode == InputConstants.KEY_SPACE) {
            return last() ? Action.CLOSE : Action.NEXT;
        }
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_A || keyCode == InputConstants.KEY_BACKSPACE) return Action.BACK;
        if (keyCode == InputConstants.KEY_H || keyCode == InputConstants.KEY_X) return Action.CLOSE;
        return Action.NONE;
    }

    void render(ThemedGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY, GameButtons buttons, Runnable close, String sfx, int sfxColor) {
        String key = "computer.antos.redstone_rush.tutorial." + PAGES[page];
        String counter = Component.translatable("computer.antos.redstone_rush.tutorial.page", page + 1, PAGES.length).getString();
        RedstoneRushRenderer.header(g, font, x, y, width, Component.translatable("computer.antos.redstone_rush.title").getString(), counter,
                Component.translatable(key + ".title").getString(), sfx, sfxColor);
        int inner = width - PAD * 2;
        int fieldTop = y + 25;
        int buttonsTop = y + height - 15 - 2 - GameButtons.BUTTON_HEIGHT;
        int columns = 0;
        int rows = 0;
        for (RedstoneRushLogic logic : scene) {
            columns = Math.max(columns, logic.columns());
            rows += logic.rows();
        }
        int gap = 5;
        int available = Math.max(24, (buttonsTop - fieldTop) * 11 / 20);
        int cell = Math.max(10, Math.min(24, Math.min((inner - 30) / Math.max(1, columns + (page == 0 ? Piece.values().length : 0)), (available - 8 - gap * (scene.length - 1)) / Math.max(1, rows))));
        int top = fieldTop + 6;
        for (RedstoneRushLogic logic : scene) {
            boolean placing = page == 0;
            int boardWidth = RedstoneRushRenderer.boardWidth(logic.columns(), cell);
            int pieces = Piece.values().length;
            int extra = placing ? pieces * (cell + 3) + 10 : 0;
            int left = x + (width - boardWidth - extra) / 2 + 2;
            RedstoneRushRenderer.board(g, logic, left, top, cell, placing ? 3 : -1, placing ? 0 : -1, placing, placing ? logic.selectedTile() : null);
            if (placing) {
                int slotX = left + boardWidth + 10;
                for (Piece piece : Piece.values()) {
                    RedstoneRushRenderer.slot(g, logic.tileFor(piece), slotX, top, cell, piece == logic.selected(), !logic.unlocked(piece), false);
                    slotX += cell + 3;
                }
            }
            top += RedstoneRushRenderer.boardHeight(logic.rows(), cell) + gap;
        }
        int bodyTop = top + 3;
        int lines = Math.max(0, (buttonsTop - 3 - bodyTop) / 10);
        GameButtons.wrap(g, font, Component.translatable(key + ".body"), x + PAD, bodyTop, inner, Ui.TEXT, lines);
        int buttonWidth = Math.max(40, Math.min(80, (inner - 8) / 3));
        if (page > 0) buttons.button(g, font, x + PAD, buttonsTop, buttonWidth, Component.translatable("computer.antos.redstone_rush.tutorial.back"), mouseX, mouseY, this::back);
        if (!last()) buttons.button(g, font, x + (width - buttonWidth) / 2, buttonsTop, buttonWidth, Component.translatable("computer.antos.redstone_rush.tutorial.skip"), mouseX, mouseY, close);
        buttons.button(g, font, x + width - PAD - buttonWidth, buttonsTop, buttonWidth,
                Component.translatable(last() ? "computer.antos.redstone_rush.tutorial.play" : "computer.antos.redstone_rush.tutorial.next"), mouseX, mouseY, last() ? close : this::next);
        RedstoneRushRenderer.footer(g, font, x, y, width, height, Component.translatable("computer.antos.redstone_rush.tutorial.controls").getString(), Ui.TEXT_MUTED);
    }

    private void build() {
        hold = 0;
        scene = switch (page) {
            case 0 -> new RedstoneRushLogic[]{line(6, false, Tile.HORIZONTAL, Tile.HORIZONTAL)};
            case 1 -> new RedstoneRushLogic[]{
                    line(6, true, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL),
                    line(6, true, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.VERTICAL, Tile.HORIZONTAL)};
            case 2 -> new RedstoneRushLogic[]{line(10, true, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.REPEATER_EAST, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL)};
            case 3 -> new RedstoneRushLogic[]{loop()};
            default -> new RedstoneRushLogic[]{line(9, true, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL)};
        };
    }

    private static RedstoneRushLogic line(int columns, boolean run, Tile... tiles) {
        RedstoneRushLogic logic = new RedstoneRushLogic(7L);
        logic.startLevel(1, new RedstoneRushLayout(columns, 1, 0, 0, Tile.EAST, columns - 1, 0, Tile.WEST));
        for (int i = 0; i < tiles.length && i + 1 < columns - 1; i++) logic.setTile(i + 1, 0, tiles[i]);
        if (run) logic.fastForward();
        return logic;
    }

    private static RedstoneRushLogic loop() {
        RedstoneRushLogic logic = new RedstoneRushLogic(7L);
        logic.startLevel(1, new RedstoneRushLayout(5, 4, 0, 2, Tile.EAST, 2, 0, Tile.SOUTH));
        logic.setTile(1, 2, Tile.HORIZONTAL);
        logic.setTile(2, 2, Tile.CROSS);
        logic.setTile(3, 2, Tile.SOUTH_WEST);
        logic.setTile(3, 3, Tile.WEST_NORTH);
        logic.setTile(2, 3, Tile.NORTH_EAST);
        logic.setTile(2, 1, Tile.VERTICAL);
        logic.fastForward();
        return logic;
    }
}
