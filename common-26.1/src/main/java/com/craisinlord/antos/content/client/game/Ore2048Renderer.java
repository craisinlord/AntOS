package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.game.logic.ore2048.Ore2048Board;
import com.craisinlord.antos.content.client.game.logic.ore2048.Ore2048Board.Dir;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

final class Ore2048Renderer {
    private static final long SLIDE_MS = 90L;
    private static final long MERGE_MS = 110L;
    private static final long SPAWN_MS = 80L;
    private static final long BLAST_MS = 260L;
    static final long TIP_DELAY_MS = SLIDE_MS + SPAWN_MS;
    private static final int SIDE_WIDTH = 86;
    private static final int MAX_CELL = 64;
    private static final ItemStack[] TIERS = {
            ItemStack.EMPTY,
            new ItemStack(Items.COAL_ORE),
            new ItemStack(Items.COPPER_ORE),
            new ItemStack(Items.IRON_ORE),
            new ItemStack(Items.GOLD_ORE),
            new ItemStack(Items.REDSTONE_ORE),
            new ItemStack(Items.EMERALD_ORE),
            new ItemStack(Items.DIAMOND_ORE),
            new ItemStack(Items.ANCIENT_DEBRIS),
            new ItemStack(Items.DIAMOND_BLOCK),
            new ItemStack(Items.NETHERITE_BLOCK),
            new ItemStack(Items.BEACON),
            new ItemStack(Items.DRAGON_EGG)
    };
    private static final ItemStack TNT = new ItemStack(Items.TNT);

    private record Label(int x0, int y0, int size, String text, int color, boolean flash) { }

    private final List<Label> labels = new ArrayList<>();
    private int boardX = -1;
    private int boardY = -1;
    private int boardSize;

    void render(ThemedGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY, Ore2048Board board,
                Ore2048Board.MoveResult last, long movedAt, long best, boolean beaconOverlay, boolean soundEnabled, Component event, boolean tip) {
        int right = x + width - 1;
        int fieldTop = y + 25;
        int fieldBottom = y + height - 15;
        boolean over = board.over();
        boolean playing = !over && !beaconOverlay && !tip;
        g.fill(x, y, right, y + height, Ui.GAME_FILL);
        g.renderOutline(x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
        String scoreText = tr("computer.antos.ore_2048.score", board.score());
        String sfx = tr(soundEnabled ? "computer.antos.ore_2048.sfx_on" : "computer.antos.ore_2048.sfx_off");
        String stats = tr(board.beaconLit() ? "computer.antos.ore_2048.stats_endless" : "computer.antos.ore_2048.stats", best);
        g.drawString(font, GameOverlay.fit(font, tr("computer.antos.ore_2048.title"), width - font.width(scoreText) - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        g.drawString(font, scoreText, right - font.width(scoreText) - 7, y + 4, Ui.TEXT, false);
        g.drawString(font, GameOverlay.fit(font, stats, width - font.width(sfx) - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        g.drawString(font, sfx, right - font.width(sfx) - 7, y + 15, soundEnabled ? Ui.TEXT_MUTED : Ui.ERROR, false);
        g.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);

        int availableHeight = fieldBottom - fieldTop - 8;
        boolean side = width - SIDE_WIDTH - 24 >= Math.min(availableHeight, 96);
        int available = Math.max(24, Math.min(availableHeight, width - (side ? SIDE_WIDTH : 0) - 24));
        int gap = Math.max(2, Math.min(6, available / 40));
        int cell = Math.max(4, Math.min(MAX_CELL, (available - gap * 5) / 4));
        int size = cell * 4 + gap * 5;
        int left = x + (width - size - (side ? SIDE_WIDTH : 0)) / 2;
        int top = fieldTop + 4 + Math.max(0, (availableHeight - size) / 2);
        boardX = left - x;
        boardY = top - y;
        boardSize = size;
        g.fill(left - 1, top - 1, left + size + 1, top + size + 1, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.renderOutline(left - 2, top - 2, size + 4, size + 4, Ui.GAME_OUTLINE);
        if (playing) drawHoverEdge(g, left, top, size, edgeAt(mouseX, mouseY));
        for (int row = 0; row < Ore2048Board.SIZE; row++) for (int col = 0; col < Ore2048Board.SIZE; col++) {
            int px = cellPos(left, gap, cell, col);
            int py = cellPos(top, gap, cell, row);
            g.fill(px, py, px + cell, py + cell, Ui.alpha(Ui.SURFACE_SUNKEN, 0x90));
        }
        labels.clear();
        long now = System.currentTimeMillis();
        long elapsed = last == null ? Long.MAX_VALUE : now - movedAt;
        boolean blasting = false;
        long after = Long.MAX_VALUE;
        if (last != null && elapsed < SLIDE_MS) {
            float progress = ease(elapsed / (float) SLIDE_MS);
            int shownFuse = last.blasted() ? 1 : board.fuse();
            for (Ore2048Board.Slide slide : last.slides()) {
                float px = lerp(cellPos(left, gap, cell, slide.fromCol()), cellPos(left, gap, cell, slide.toCol()), progress);
                float py = lerp(cellPos(top, gap, cell, slide.fromRow()), cellPos(top, gap, cell, slide.toRow()), progress);
                drawTile(g, px, py, cell, slide.tier(), 1.0F, shownFuse, now);
            }
        } else {
            after = last == null ? Long.MAX_VALUE : elapsed - SLIDE_MS;
            for (int row = 0; row < Ore2048Board.SIZE; row++) for (int col = 0; col < Ore2048Board.SIZE; col++) {
                int tier = board.get(row, col);
                if (tier == Ore2048Board.EMPTY) continue;
                float scale = 1.0F;
                if (last != null && after < MERGE_MS && merged(last, row, col)) scale = 1.0F + 0.18F * (float) Math.sin(Math.PI * after / MERGE_MS);
                if (last != null && after < SPAWN_MS && last.spawn() != null && last.spawn().row() == row && last.spawn().col() == col) scale = Math.max(0.15F, after / (float) SPAWN_MS);
                drawTile(g, cellPos(left, gap, cell, col), cellPos(top, gap, cell, row), cell, tier, scale, board.fuse(), now);
            }
            blasting = last != null && last.blasted() && after < BLAST_MS;
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        for (Label label : labels) drawLabel(g, font, label);
        if (blasting) drawBlast(g, left, top, gap, cell, last, after / (float) BLAST_MS);
        g.pose().popPose();

        int sideX = left + size + 12;
        if (side && sideX + SIDE_WIDTH <= right - 4) drawSide(g, font, sideX, top, board);

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        if (beaconOverlay) {
            GameOverlay.render(g, font, x, y, width, height, Component.translatable("computer.antos.ore_2048.beacon"), Component.translatable("computer.antos.ore_2048.continue"), Ui.ACCENT);
        } else if (over) {
            GameOverlay.render(g, font, x, y, width, height, Component.translatable("computer.antos.ore_2048.game_over"), Component.translatable("computer.antos.ore_2048.retry"), Ui.ERROR);
        } else if (tip) {
            GameTip.render(g, font, x, y, width, height, Component.translatable("computer.antos.ore_2048.tip.tnt.title"),
                    Component.translatable("computer.antos.ore_2048.tip.tnt.body"), Component.translatable("computer.antos.ore_2048.tip.tnt.hint"));
        } else {
            boolean fuse = board.hasTnt() && board.fuse() > 0;
            String hint = event != null ? event.getString() : fuse ? tr("computer.antos.ore_2048.fuse", board.fuse()) : tr("computer.antos.ore_2048.hint");
            g.drawCenteredString(font, GameOverlay.fit(font, hint, width - 14), x + width / 2, y + height - 11, event != null || fuse ? Ui.ACCENT : Ui.TEXT_MUTED);
        }
        g.pose().popPose();
    }

    Dir edgeAt(double mouseX, double mouseY) {
        if (boardX < 0 || boardSize <= 0) return null;
        double dx = mouseX - (boardX + boardSize / 2.0);
        double dy = mouseY - (boardY + boardSize / 2.0);
        double half = boardSize / 2.0 + 2;
        if (Math.abs(dx) > half || Math.abs(dy) > half) return null;
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Dir.RIGHT : Dir.LEFT;
        if (dy == 0) return null;
        return dy > 0 ? Dir.DOWN : Dir.UP;
    }

    private static void drawHoverEdge(ThemedGraphics g, int left, int top, int size, Dir dir) {
        if (dir == null) return;
        int color = Ui.alpha(Ui.ACCENT, 0xB0);
        int reach = size / 3;
        int mid = size / 2;
        switch (dir) {
            case LEFT -> g.fill(left - 5, top + mid - reach / 2, left - 3, top + mid + reach / 2, color);
            case RIGHT -> g.fill(left + size + 3, top + mid - reach / 2, left + size + 5, top + mid + reach / 2, color);
            case UP -> g.fill(left + mid - reach / 2, top - 5, left + mid + reach / 2, top - 3, color);
            case DOWN -> g.fill(left + mid - reach / 2, top + size + 3, left + mid + reach / 2, top + size + 5, color);
        }
    }

    private static void drawSide(ThemedGraphics g, Font font, int sx, int top, Ore2048Board board) {
        int width = SIDE_WIDTH - 6;
        g.drawString(font, GameOverlay.fit(font, tr("computer.antos.ore_2048.best_tile"), width), sx, top, Ui.TEXT_MUTED, false);
        int box = 24;
        g.fill(sx, top + 11, sx + box, top + 11 + box, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.renderOutline(sx, top + 11, box, box, Ui.GAME_OUTLINE);
        int bestTier = board.bestTier();
        if (bestTier > 0) {
            g.renderItem(stack(bestTier), sx + 4, top + 15);
            g.drawString(font, GameOverlay.fit(font, label(bestTier), width - box - 4), sx + box + 4, top + 19, Ui.TEXT_STRONG, false);
        }
        int rowY = top + 42;
        boolean header = false;
        int[] tiers = {Ore2048Board.DIAMOND_TIER, Ore2048Board.BEACON_TIER};
        String[] modes = {"ore_2048_diamond", "ore_2048_beacon"};
        for (int i = 0; i < tiers.length; i++) {
            String bonus = GameRewardTracker.dailyBonus(modes[i]).getString();
            if (bonus.isEmpty()) continue;
            if (!header) {
                g.drawString(font, GameOverlay.fit(font, tr("computer.antos.ore_2048.daily"), width), sx, rowY, Ui.TEXT_MUTED, false);
                rowY += 10;
                header = true;
            }
            String tile = label(tiers[i]);
            String shown = GameOverlay.fit(font, bonus, width - font.width(tile) - 6);
            g.drawString(font, tile, sx, rowY, Ui.TEXT_MUTED, false);
            g.drawString(font, shown, sx + width - font.width(shown), rowY, Ui.TEXT, false);
            rowY += 10;
        }
    }

    private void drawTile(ThemedGraphics g, float px, float py, int cell, int tier, float scale, int fuse, long now) {
        boolean tnt = tier == Ore2048Board.TNT;
        float centerX = px + cell / 2.0F;
        float centerY = py + cell / 2.0F;
        int size = Math.max(2, Math.round(cell * scale));
        int x0 = Math.round(centerX - size / 2.0F);
        int y0 = Math.round(centerY - size / 2.0F);
        int fill = tnt ? Ui.mix(Ui.PANEL, Ui.DISPLAY_REDSTONE, 0.35F) : Ui.mix(Ui.PANEL, Ui.ACCENT, Math.min(0.45F, tier * 0.035F));
        g.fill(x0, y0, x0 + size, y0 + size, Ui.GAME_OUTLINE);
        g.fill(x0 + 1, y0 + 1, x0 + size - 1, y0 + size - 1, fill);
        if (size >= 6) g.fill(x0 + 1, y0 + 1, x0 + size - 1, y0 + 2, Ui.alpha(Ui.TEXT_BRIGHT, 0x30));
        if (tier >= Ore2048Board.BEACON_TIER) g.renderOutline(x0, y0, size, size, Ui.ACCENT);
        float itemScale = Math.max(0.25F, (size - Math.max(4.0F, size / 4.0F)) / 16.0F);
        g.pose().pushPose();
        g.pose().translate(centerX, centerY - (cell >= 22 ? size * 0.06F : 0.0F), 0.0F);
        g.pose().scale(itemScale, itemScale, 1.0F);
        g.renderItem(tnt ? TNT : stack(tier), -8, -8);
        g.pose().popPose();
        boolean flash = tnt && fuse == 1 && now / 120L % 2L == 0L;
        String text = scale >= 0.6F && cell >= 14 ? tnt ? Integer.toString(Math.max(0, fuse)) : label(tier) : "";
        int color = tnt && fuse == 1 ? Ui.ERROR : Ui.TEXT_BRIGHT;
        if (flash || !text.isEmpty()) labels.add(new Label(x0, y0, size, text, color, flash));
    }

    private static void drawLabel(ThemedGraphics g, Font font, Label label) {
        int x0 = label.x0();
        int y0 = label.y0();
        int size = label.size();
        if (label.flash()) g.fill(x0 + 1, y0 + 1, x0 + size - 1, y0 + size - 1, Ui.alpha(Ui.TEXT_BRIGHT, 0x70));
        if (label.text().isEmpty()) return;
        float textScale = font.width(label.text()) + 3 > size - 2 ? 0.5F : 1.0F;
        int textWidth = Math.round(font.width(label.text()) * textScale);
        int textHeight = Math.round(8 * textScale);
        int rightX = x0 + size - 2;
        int bottomY = y0 + size - 2;
        g.fill(rightX - textWidth - 2, bottomY - textHeight - 1, rightX, bottomY, Ui.alpha(Ui.BACKGROUND, 0xC8));
        g.pose().pushPose();
        g.pose().translate(rightX - textWidth - 1, bottomY - textHeight, 0.0F);
        g.pose().scale(textScale, textScale, 1.0F);
        g.drawString(font, label.text(), 0, 0, label.color(), false);
        g.pose().popPose();
    }

    private static void drawBlast(ThemedGraphics g, int left, int top, int gap, int cell, Ore2048Board.MoveResult last, float progress) {
        int alpha = Math.max(0, Math.min(255, Math.round((1.0F - progress) * 0xC0)));
        int color = Ui.alpha(Ui.mix(Ui.DISPLAY_SPARK, Ui.DISPLAY_FIRE, progress), alpha);
        for (int[] spot : last.blast()) {
            int px = cellPos(left, gap, cell, spot[1]);
            int py = cellPos(top, gap, cell, spot[0]);
            g.fill(px, py, px + cell, py + cell, color);
        }
        int cx = cellPos(left, gap, cell, last.blastCol()) + cell / 2;
        int cy = cellPos(top, gap, cell, last.blastRow()) + cell / 2;
        int radius = Math.round((cell + gap) * (0.4F + 1.1F * progress));
        int limit = gap * 2 + (cell + gap) * 4;
        int minX = Math.max(left - 1, cx - radius);
        int minY = Math.max(top - 1, cy - radius);
        int maxX = Math.min(left + limit - gap + 1, cx + radius);
        int maxY = Math.min(top + limit - gap + 1, cy + radius);
        g.renderOutline(minX, minY, maxX - minX, maxY - minY, Ui.alpha(Ui.DISPLAY_FIRE, alpha));
        int spark = Math.max(1, cell / 10);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0 + 0.3;
            int sx = Math.max(minX, Math.min(maxX - 1, cx + (int) Math.round(Math.cos(angle) * radius * 0.9)));
            int sy = Math.max(minY, Math.min(maxY - 1, cy + (int) Math.round(Math.sin(angle) * radius * 0.9)));
            g.fill(sx - spark, sy - spark, sx + spark, sy + spark, Ui.alpha(Ui.DISPLAY_SPARK, alpha));
        }
    }

    private static boolean merged(Ore2048Board.MoveResult last, int row, int col) {
        for (Ore2048Board.Merge merge : last.merges()) if (merge.row() == row && merge.col() == col) return true;
        return false;
    }

    private static int cellPos(int origin, int gap, int cell, int index) {
        return origin + gap + index * (cell + gap);
    }

    private static ItemStack stack(int tier) {
        if (tier <= 0) return ItemStack.EMPTY;
        return TIERS[Math.min(tier, TIERS.length - 1)];
    }

    private static String label(int tier) {
        long value = Ore2048Board.value(tier);
        return value < 10000L ? Long.toString(value) : value / 1024L + "K";
    }

    private static float ease(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
