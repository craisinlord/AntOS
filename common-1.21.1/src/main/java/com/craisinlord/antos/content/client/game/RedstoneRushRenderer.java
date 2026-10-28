package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.game.logic.redstonerush.RedstoneRushLogic;
import com.craisinlord.antos.content.client.game.logic.redstonerush.Tile;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class RedstoneRushRenderer {
    static final float OVERLAY_Z = 400.0F;
    private static final int[] POWER = {
            Ui.REDSTONE_DUST_OFF, Ui.REDSTONE_1, Ui.REDSTONE_2, Ui.REDSTONE_3, Ui.REDSTONE_4, Ui.REDSTONE_5, Ui.REDSTONE_6, Ui.REDSTONE_7,
            Ui.REDSTONE_8, Ui.REDSTONE_9, Ui.REDSTONE_10, Ui.REDSTONE_11, Ui.REDSTONE_12, Ui.REDSTONE_13, Ui.REDSTONE_14, Ui.REDSTONE_15
    };
    private static final ItemStack TORCH = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack LAMP = new ItemStack(Items.REDSTONE_LAMP);
    private static final ItemStack OBSIDIAN = new ItemStack(Items.OBSIDIAN);
    private static final ItemStack REPEATER = new ItemStack(Items.REPEATER);

    private RedstoneRushRenderer() { }

    static int power(int level) {
        return POWER[Math.max(0, Math.min(15, level))];
    }

    static void frame(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width - 1, y + height, Ui.GAME_FILL);
        g.renderOutline(x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
    }

    static void header(GuiGraphics g, Font font, int x, int y, int width, String title, String main, String stats, String side, int sideColor) {
        int right = x + width - 1;
        g.drawString(font, GameOverlay.fit(font, title, width - font.width(main) - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        g.drawString(font, main, right - font.width(main) - 7, y + 4, Ui.TEXT, false);
        g.drawString(font, GameOverlay.fit(font, stats, width - font.width(side) - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        g.drawString(font, side, right - font.width(side) - 7, y + 15, sideColor, false);
        g.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);
    }

    static void footer(GuiGraphics g, Font font, int x, int y, int width, int height, String text, int color) {
        g.drawCenteredString(font, GameOverlay.fit(font, text, width - 14), x + width / 2, y + height - 11, color);
    }

    static int boardWidth(int columns, int cell) {
        return columns * cell + 3;
    }

    static int boardHeight(int rows, int cell) {
        return rows * cell + 3;
    }

    static void board(GuiGraphics g, RedstoneRushLogic logic, int left, int top, int cell, int hoverX, int hoverY, boolean cursor, Tile ghost) {
        int columns = logic.columns();
        int rows = logic.rows();
        g.fill(left - 1, top - 1, left + columns * cell, top + rows * cell, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.renderOutline(left - 2, top - 2, columns * cell + 3, rows * cell + 3, Ui.GAME_OUTLINE);
        for (int x = 0; x < columns; x++) {
            for (int y = 0; y < rows; y++) {
                boolean hover = x == hoverX && y == hoverY;
                cell(g, logic, x, y, left + x * cell, top + y * cell, cell, hover && logic.replaceable(x, y) && active(logic));
            }
        }
        if (ghost != null && logic.inside(hoverX, hoverY) && logic.replaceable(hoverX, hoverY) && active(logic) && logic.tile(hoverX, hoverY) != ghost) {
            ghost(g, ghost, left + hoverX * cell, top + hoverY * cell, cell);
        }
        if (logic.state() == RedstoneRushLogic.State.FLOWING) spark(g, logic, left, top, cell);
        if (logic.state() == RedstoneRushLogic.State.LOST && logic.inside(logic.lossX(), logic.lossY())) {
            outline(g, left + logic.lossX() * cell, top + logic.lossY() * cell, cell, Ui.ERROR);
        }
        if (cursor && logic.inside(hoverX, hoverY)) outline(g, left + hoverX * cell, top + hoverY * cell, cell, Ui.ACCENT);
    }

    private static void outline(GuiGraphics g, int left, int top, int cell, int color) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 260.0F);
        g.renderOutline(left - 1, top - 1, cell + 1, cell + 1, color);
        g.pose().popPose();
    }

    private static boolean active(RedstoneRushLogic logic) {
        return logic.state() == RedstoneRushLogic.State.COUNTDOWN || logic.state() == RedstoneRushLogic.State.FLOWING;
    }

    private static void cell(GuiGraphics g, RedstoneRushLogic logic, int x, int y, int left, int top, int size, boolean hover) {
        Tile tile = logic.tile(x, y);
        int inner = size - 1;
        int background = hover ? Ui.GAME_HOVER : logic.placed(x, y) ? Ui.alpha(Ui.SURFACE_DEEP, 0xC0) : Ui.alpha(Ui.SURFACE_SUNKEN, 0x90);
        g.fill(left, top, left + inner, top + inner, background);
        switch (tile) {
            case EMPTY -> { }
            case SOURCE -> {
                stub(g, left, top, size, logic.layout().sourceDirection(), power(15));
                item(g, TORCH, left, top, size);
            }
            case LAMP -> lamp(g, logic, left, top, size);
            case OBSIDIAN -> item(g, OBSIDIAN, left, top, size);
            default -> tile(g, tile, left, top, size, logic.power(x, y, 0), logic.power(x, y, 1));
        }
    }

    static void slot(GuiGraphics g, Tile tile, int left, int top, int size, boolean selected, boolean locked, boolean hover) {
        int inner = size - 1;
        g.fill(left - 1, top - 1, left + inner + 1, top + inner + 1, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.fill(left, top, left + inner, top + inner, hover && !locked ? Ui.GAME_HOVER : Ui.alpha(Ui.SURFACE_SUNKEN, 0x90));
        tile(g, tile, left, top, size, 0, 0);
        if (locked) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 255.0F);
            g.fill(left, top, left + inner, top + inner, Ui.alpha(Ui.BACKGROUND, 0xB0));
            g.pose().popPose();
        }
        outline(g, left, top, size, selected ? Ui.ACCENT : Ui.GAME_OUTLINE);
    }

    static void ghost(GuiGraphics g, Tile tile, int left, int top, int size) {
        int color = Ui.alpha(Ui.REDSTONE_SPARK, 0xA0);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 255.0F);
        g.fill(left, top, left + size - 1, top + size - 1, Ui.alpha(Ui.REDSTONE_SPARK, 0x24));
        for (int side = 0; side < 4; side++) if (tile.opens(side)) stub(g, left, top, size, side, color);
        if (tile.repeater()) {
            int inner = size - 1;
            arrow(g, left + inner / 2, top + inner / 2, tile.facing(), Math.max(3, inner / 2 - 1), Ui.alpha(Ui.TEXT_BRIGHT, 0xD0));
        }
        g.pose().popPose();
    }

    static void tile(GuiGraphics g, Tile tile, int left, int top, int size, int powerA, int powerB) {
        if (tile.repeater()) {
            int color = power(powerA);
            stub(g, left, top, size, tile.facing(), color);
            stub(g, left, top, size, Tile.opposite(tile.facing()), color);
            item(g, REPEATER, left, top, size);
            int inner = size - 1;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 250.0F);
            arrow(g, left + inner / 2, top + inner / 2, tile.facing(), Math.max(3, inner / 2 - 1), powerA > 0 ? Ui.REDSTONE_SPARK : Ui.TEXT_BRIGHT);
            g.pose().popPose();
            return;
        }
        if (tile == Tile.CROSS) {
            int inner = size - 1;
            int cx = left + inner / 2;
            int cy = top + inner / 2;
            int half = half(size);
            stub(g, left, top, size, Tile.EAST, power(powerA));
            stub(g, left, top, size, Tile.WEST, power(powerA));
            int shadow = Ui.alpha(Ui.SHADOW, 0xB0);
            g.fill(cx - half - 1, cy - half - 1, cx - half, cy + half + 2, shadow);
            g.fill(cx + half + 1, cy - half - 1, cx + half + 2, cy + half + 2, shadow);
            stub(g, left, top, size, Tile.NORTH, power(powerB));
            stub(g, left, top, size, Tile.SOUTH, power(powerB));
            return;
        }
        int color = power(powerA);
        for (int side = 0; side < 4; side++) if (tile.opens(side)) stub(g, left, top, size, side, color);
    }

    private static void lamp(GuiGraphics g, RedstoneRushLogic logic, int left, int top, int size) {
        boolean lit = logic.lampLit();
        int inner = size - 1;
        if (lit) {
            g.fill(left, top, left + inner, top + inner, Ui.alpha(Ui.REDSTONE_LAMP_GLOW, 0x90));
            g.fill(left + 2, top + 2, left + inner - 2, top + inner - 2, Ui.REDSTONE_LAMP_GLOW);
        }
        stub(g, left, top, size, logic.layout().lampSide(), lit ? power(15) : power(0));
        item(g, LAMP, left, top, size);
        if (lit) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 250.0F);
            int pad = Math.max(2, inner / 4);
            g.fill(left + pad, top + pad, left + inner - pad, top + inner - pad, Ui.alpha(Ui.REDSTONE_LAMP_CORE, 0x78));
            g.pose().popPose();
        }
    }

    private static void spark(GuiGraphics g, RedstoneRushLogic logic, int left, int top, int cell) {
        int hx = logic.headX();
        int hy = logic.headY();
        if (!logic.inside(hx, hy)) return;
        int inner = cell - 1;
        int cx = left + hx * cell + inner / 2;
        int cy = top + hy * cell + inner / 2;
        int travel = Math.round(logic.flowProgress() * (cell / 2.0F));
        int sx = cx + Tile.DX[logic.headDirection()] * travel;
        int sy = cy + Tile.DY[logic.headDirection()] * travel;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 260.0F);
        g.fill(sx - 2, sy - 2, sx + 3, sy + 3, Ui.alpha(Ui.REDSTONE_SPARK, 0x90));
        g.fill(sx - 1, sy - 1, sx + 2, sy + 2, Ui.REDSTONE_LAMP_CORE);
        g.pose().popPose();
    }

    private static int half(int size) {
        return size >= 24 ? 2 : 1;
    }

    private static void stub(GuiGraphics g, int left, int top, int size, int side, int color) {
        int inner = size - 1;
        int cx = left + inner / 2;
        int cy = top + inner / 2;
        int half = half(size);
        switch (side) {
            case Tile.NORTH -> g.fill(cx - half, top, cx + half + 1, cy + half + 1, color);
            case Tile.EAST -> g.fill(cx - half, cy - half, left + size, cy + half + 1, color);
            case Tile.SOUTH -> g.fill(cx - half, cy - half, cx + half + 1, top + size, color);
            default -> g.fill(left, cy - half, cx + half + 1, cy + half + 1, color);
        }
    }

    private static void arrow(GuiGraphics g, int cx, int cy, int direction, int reach, int color) {
        for (int i = 0; i < 3; i++) {
            int distance = reach - i;
            if (Tile.vertical(direction)) {
                int row = cy + Tile.DY[direction] * distance;
                g.fill(cx - i, row, cx + i + 1, row + 1, color);
            } else {
                int column = cx + Tile.DX[direction] * distance;
                g.fill(column, cy - i, column + 1, cy + i + 1, color);
            }
        }
    }

    private static void item(GuiGraphics g, ItemStack stack, int left, int top, int size) {
        int inner = size - 1;
        float scale = Math.min(1.5F, (inner - 2) / 16.0F);
        g.pose().pushPose();
        g.pose().translate(left + (inner - 16.0F * scale) / 2.0F, top + (inner - 16.0F * scale) / 2.0F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
