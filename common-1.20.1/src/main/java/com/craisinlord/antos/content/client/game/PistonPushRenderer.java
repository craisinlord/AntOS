package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Block;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Terrain;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushRules;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class PistonPushRenderer {
    public static final int MIN_TILE = 8;
    public static final int MAX_TILE = 32;
    private static final ResourceLocation WALL = new ResourceLocation("minecraft", "textures/block/obsidian.png");
    private static final ResourceLocation FLOOR = new ResourceLocation("minecraft", "textures/block/smooth_stone.png");
    private static final ResourceLocation ICE = new ResourceLocation("minecraft", "textures/block/packed_ice.png");
    private static final ResourceLocation PLATE = new ResourceLocation("minecraft", "textures/block/stone.png");
    private static final ItemStack BASIC = new ItemStack(Blocks.HAY_BLOCK);
    private static final ItemStack SLIME = new ItemStack(Blocks.SLIME_BLOCK);
    private static final ItemStack HONEY = new ItemStack(Blocks.HONEY_BLOCK);

    private PistonPushRenderer() { }

    public static int tileSize(PistonPushBoard board, int width, int height) {
        int tile = Math.min(width / board.width(), height / board.height());
        if (tile >= 16) tile -= tile % 2;
        return Math.max(MIN_TILE, Math.min(MAX_TILE, tile));
    }

    public static void render(GuiGraphics g, PistonPushBoard board, int left, int top, int tile, PistonPushRules.MoveResult animation, float progress) {
        int boardWidth = board.width() * tile;
        int boardHeight = board.height() * tile;
        g.fill(left - 1, top - 1, left + boardWidth + 1, top + boardHeight + 1, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.renderOutline(left - 2, top - 2, boardWidth + 4, boardHeight + 4, Ui.GAME_OUTLINE);
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                drawTerrain(g, board.terrain(x, y), left + x * tile, top + y * tile, tile);
            }
        }
        float eased = ease(progress);
        boolean animating = animation != null && animation.moved() && progress < 1.0F;
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                Block block = board.block(x, y);
                if (block == Block.NONE) continue;
                float drawX = x;
                float drawY = y;
                if (animating) {
                    for (PistonPushRules.BlockMove move : animation.blocks()) {
                        if (move.toX() != x || move.toY() != y) continue;
                        drawX = move.fromX() + (move.toX() - move.fromX()) * eased;
                        drawY = move.fromY() + (move.toY() - move.fromY()) * eased;
                    }
                }
                boolean settled = drawX == x && drawY == y;
                if (settled && board.terrain(x, y) == Terrain.PLATE) glow(g, left + x * tile, top + y * tile, tile);
                drawBlock(g, block, left + drawX * tile, top + drawY * tile, tile);
            }
        }
        float playerX = board.playerX();
        float playerY = board.playerY();
        if (animating) {
            float walk = Math.min(1.0F, ease(Math.min(1.0F, progress * Math.max(1.0F, 1.0F + animation.slide()))));
            playerX = animation.playerFromX() + (animation.playerToX() - animation.playerFromX()) * walk;
            playerY = animation.playerFromY() + (animation.playerToY() - animation.playerFromY()) * walk;
        }
        drawPlayer(g, left + playerX * tile, top + playerY * tile, tile);
    }

    private static void drawTerrain(GuiGraphics g, Terrain terrain, int x, int y, int tile) {
        switch (terrain) {
            case WALL -> {
                blit(g, WALL, x, y, tile);
                g.fill(x, y + tile - 1, x + tile, y + tile, Ui.alpha(Ui.BACKGROUND, 0x60));
            }
            case FLOOR -> blit(g, FLOOR, x, y, tile);
            case ICE -> blit(g, ICE, x, y, tile);
            case PLATE -> {
                blit(g, FLOOR, x, y, tile);
                int inset = Math.max(2, tile / 6);
                g.fill(x + inset - 1, y + inset - 1, x + tile - inset + 1, y + tile - inset + 1, Ui.alpha(Ui.BACKGROUND, 0xA0));
                g.blit(PLATE, x + inset, y + inset, tile - inset * 2, tile - inset * 2, 0.0F, 0.0F, 16, 16, 16, 16);
                g.renderOutline(x + inset - 1, y + inset - 1, tile - inset * 2 + 2, tile - inset * 2 + 2, Ui.alpha(Ui.ACCENT, 0xD0));
            }
            default -> { }
        }
    }

    private static void glow(GuiGraphics g, int x, int y, int tile) {
        g.fill(x, y, x + tile, y + tile, Ui.alpha(Ui.ACCENT, 0x50));
        g.renderOutline(x, y, tile, tile, Ui.ACCENT);
    }

    private static void drawBlock(GuiGraphics g, Block block, float x, float y, int tile) {
        ItemStack stack = switch (block) {
            case SLIME -> SLIME;
            case HONEY -> HONEY;
            default -> BASIC;
        };
        float scale = tile / 16.0F;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static void drawPlayer(GuiGraphics g, float x, float y, int tile) {
        int size = tile >= 10 ? Math.max(8, (tile - 2) / 8 * 8) : tile - 2;
        int inset = (tile - size) / 2;
        g.pose().pushPose();
        g.pose().translate(x, y, 200.0F);
        g.fill(inset - 1, inset - 1, inset + size + 1, inset + size + 1, Ui.alpha(Ui.BACKGROUND, 0xB0));
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation skin = minecraft.player == null ? DefaultPlayerSkin.getDefaultSkin() : minecraft.player.getSkinTextureLocation();
        PlayerFaceRenderer.draw(g, skin, inset, inset, size);
        g.pose().popPose();
    }

    private static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int tile) {
        g.blit(texture, x, y, tile, tile, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    private static float ease(float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    public static void check(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 3, x + 1, y + 5, color);
        g.fill(x + 1, y + 4, x + 2, y + 6, color);
        g.fill(x + 2, y + 5, x + 3, y + 6, color);
        g.fill(x + 3, y + 3, x + 4, y + 5, color);
        g.fill(x + 4, y + 2, x + 5, y + 4, color);
        g.fill(x + 5, y + 1, x + 6, y + 3, color);
        g.fill(x + 6, y, x + 7, y + 2, color);
    }

    public static void star(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y, x + 4, y + 2, color);
        g.fill(x, y + 2, x + 7, y + 3, color);
        g.fill(x + 1, y + 3, x + 6, y + 4, color);
        g.fill(x + 2, y + 4, x + 5, y + 5, color);
        g.fill(x + 1, y + 5, x + 3, y + 6, color);
        g.fill(x + 4, y + 5, x + 6, y + 6, color);
        g.fill(x, y + 6, x + 2, y + 7, color);
        g.fill(x + 5, y + 6, x + 7, y + 7, color);
    }

    public static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 5, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 3, color);
        g.fill(x + 5, y + 1, x + 6, y + 3, color);
        g.fill(x - 1, y + 3, x + 7, y + 8, color);
    }
}
