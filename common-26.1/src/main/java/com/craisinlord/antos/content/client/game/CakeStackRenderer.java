package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.ThemedGraphics;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CakeStackRenderer {
    public static final int FIELD_WIDTH = 214;
    private static final int[] SPONGE = {Ui.CAKE_SPONGE_1, Ui.CAKE_SPONGE_2, Ui.CAKE_SPONGE_3, Ui.CAKE_SPONGE_4, Ui.CAKE_SPONGE_5};
    private static final int[] ICING = {Ui.CAKE_ICING_1, Ui.CAKE_ICING_2, Ui.CAKE_ICING_3, Ui.CAKE_ICING_4, Ui.CAKE_ICING_5};

    private CakeStackRenderer() { }

    public static void render(ThemedGraphics g, Font font, int x, int y, int width, int height,
                              List<Layer> layers, int movingX, int movingWidth, int score, int best, int streak,
                              boolean gameOver, boolean greenTint, int ticks, int offcutX, int offcutWidth, int offcutTicks,
                              int perfectPulseTicks) {
        int right = x + width - 1;
        int bottom = y + height - 15;
        int fieldTop = y + 25;
        int fieldHeight = Math.max(20, bottom - fieldTop);
        g.fill(x, y, right, y + height, Ui.GAME_FILL);
        outline(g, x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
        String scoreText = tr("computer.antos.cake_stack.score", score);
        int scoreWidth = font.width(scoreText);
        g.drawString(font, fit(font, tr("computer.antos.cake_stack.title"), width - scoreWidth - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        g.drawString(font, scoreText, right - scoreWidth - 7, y + 4, Ui.TEXT, false);
        String tintText = tr(greenTint ? "computer.antos.cake_stack.tint_on" : "computer.antos.cake_stack.tint_off");
        int tintWidth = font.width(tintText);
        g.drawString(font, fit(font, tr("computer.antos.cake_stack.best", best), width - tintWidth - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        g.drawString(font, tintText, right - tintWidth - 7, y + 15, greenTint ? Ui.ACCENT : Ui.TEXT_MUTED, false);
        g.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);
        g.fill(x + 9, bottom + 1, right - 9, bottom + 2, Ui.mix(Ui.GAME_OUTLINE, Ui.BACKGROUND, 0.25F));
        int towerHeight = (layers.size() + 1) * 10;
        int camera = Math.max(0, towerHeight - fieldHeight + 12);
        int baseY = bottom + camera;
        for (int i = 0; i < layers.size(); i++) {
            int layerY = baseY - (i + 1) * 10;
            if (!visible(layerY, fieldTop, bottom)) continue;
            Layer layer = layers.get(i);
            drawCake(g, sx(x, width, layer.x()), layerY, span(width, layer.x(), layer.width()), i, greenTint, false);
        }
        int movingY = baseY - (layers.size() + 1) * 10;
        if (!gameOver) {
            if (visible(movingY, fieldTop, bottom)) drawCake(g, sx(x, width, movingX), movingY, span(width, movingX, movingWidth), layers.size(), greenTint, true);
            int markerY = Math.min(bottom - 4, movingY + 11);
            if (markerY >= fieldTop) {
                g.fill(x + 8, markerY, x + width - 8, markerY + 1, Ui.alpha(Ui.TEXT_MUTED, 0x88));
            }
        }
        if (offcutTicks > 0 && offcutWidth > 0) {
            int startY = gameOver ? movingY : baseY - layers.size() * 10;
            int fall = (10 - offcutTicks) * (10 - offcutTicks) / 3;
            int offY = startY + fall;
            if (offY >= fieldTop && offY + 8 <= bottom) {
                int ox = sx(x, width, offcutX);
                int ow = span(width, offcutX, offcutWidth);
                int color = gameOver ? layers.size() : layers.size() - 1;
                g.fill(ox, offY + 2, ox + ow, offY + 8, SPONGE[color % SPONGE.length]);
                g.fill(ox, offY + 1, ox + ow, offY + 3, ICING[color % ICING.length]);
            }
        }
        if (gameOver) {
            int boxWidth = Math.max(20, Math.min(width - 24, 150));
            int boxX = x + (width - boxWidth) / 2;
            int boxY = Math.max(y + 2, y + height / 2 - 15);
            g.fill(boxX, boxY, boxX + boxWidth, boxY + 30, Ui.alpha(Ui.BACKGROUND, 0xE8));
            outline(g, boxX, boxY, boxWidth, 30, Ui.ERROR);
            g.drawCenteredString(font, fit(font, tr("computer.antos.cake_stack.collapsed"), boxWidth - 8), x + width / 2, boxY + 5, Ui.ERROR);
            g.drawCenteredString(font, fit(font, tr("computer.antos.cake_stack.restart"), boxWidth - 8), x + width / 2, boxY + 17, Ui.TEXT_BRIGHT);
        } else {
            String hint = perfectPulseTicks > 0 ? tr("computer.antos.cake_stack.perfect")
                    : streak >= 2 ? tr("computer.antos.cake_stack.streak", streak)
                    : tr("computer.antos.cake_stack.hint");
            int hintColor = perfectPulseTicks > 0 ? Ui.CAKE_CREAM : streak >= 2 ? Ui.ACCENT : Ui.TEXT_MUTED;
            g.drawCenteredString(font, fit(font, hint, width - 14), x + width / 2, y + height - 11, hintColor);
        }
        if (ticks % 36 < 18 && !gameOver) {
            int glintX = x + 12 + Math.floorMod(ticks * 13, Math.max(1, width - 24));
            int glintY = fieldTop + 3 + Math.floorMod(ticks * 7, Math.max(1, fieldHeight - 12));
            g.fill(glintX, glintY, glintX + 1, glintY + 1, Ui.alpha(Ui.TEXT_BRIGHT, 0x66));
        }
    }

    private static boolean visible(int layerY, int fieldTop, int bottom) {
        return layerY - 1 >= fieldTop && layerY + 10 <= bottom + 1;
    }

    private static int sx(int x, int width, int value) {
        return x + value * width / FIELD_WIDTH;
    }

    private static int span(int width, int value, int size) {
        return Math.max(1, (value + size) * width / FIELD_WIDTH - value * width / FIELD_WIDTH);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        if (width <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private static void outline(ThemedGraphics g, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y, x + 1, y + height, color);
        g.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void drawCake(ThemedGraphics g, int x, int y, int width, int layer, boolean greenTint, boolean moving) {
        if (width <= 0) return;
        int sponge = SPONGE[layer % SPONGE.length];
        int icing = ICING[layer % ICING.length];
        if (greenTint) {
            sponge = Ui.mix(sponge, Ui.CAKE_GREEN_SPONGE, 0.62F);
            icing = Ui.mix(icing, Ui.CAKE_GREEN_ICING, 0.78F);
        }
        int height = 9;
        g.fill(x + 1, y + 2, x + width + 1, y + height + 2, Ui.alpha(Ui.BACKGROUND, 0xA0));
        g.fill(x, y + 2, x + width, y + height, Ui.mix(sponge, Ui.CAKE_CRUST, 0.16F));
        g.fill(x + 1, y + 3, x + width - 1, y + 6, sponge);
        g.fill(x + 1, y + 6, x + width - 1, y + 7, Ui.mix(sponge, Ui.CAKE_SHADE, 0.18F));
        g.fill(x, y + 1, x + width, y + 3, icing);
        if (width > 5) g.fill(x + 2, y, x + width - 2, y + 2, Ui.mix(icing, Ui.TEXT_BRIGHT, 0.28F));
        int berryCount = width >= 14 ? Math.min(5, width / 22) : 0;
        for (int i = 0; i < berryCount; i++) {
            int berryX = x + (i + 1) * width / (berryCount + 1);
            g.fill(berryX, y - 1, berryX + 2, y + 1, Ui.CAKE_CREAM);
            g.fill(berryX, y - 1, berryX + 1, y, greenTint ? Ui.CAKE_GREEN_ICING : Ui.CAKE_BERRY);
        }
        if (moving) {
            outline(g, x - 1, y - 1, width + 2, height + 1, Ui.alpha(Ui.TEXT_BRIGHT, 0xB0));
        }
    }

    public record Layer(int x, int width) { }
}
