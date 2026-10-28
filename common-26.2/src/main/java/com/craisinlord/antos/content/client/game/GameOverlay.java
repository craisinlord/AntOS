package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.ThemedGraphics;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class GameOverlay {
    private GameOverlay() { }

    public static void render(ThemedGraphics g, Font font, int x, int y, int width, int height, Component title, Component hint, int accent) {
        int boxWidth = Math.max(20, Math.min(width - 24, Math.max(150, Math.max(font.width(title), hint == null ? 0 : font.width(hint)) + 16)));
        int boxHeight = hint == null ? 18 : 30;
        int boxX = x + (width - boxWidth) / 2;
        int boxY = Math.max(y + 2, y + height / 2 - boxHeight / 2);
        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, Ui.alpha(Ui.BACKGROUND, 0xE8));
        g.renderOutline(boxX, boxY, boxWidth, boxHeight, accent);
        g.drawCenteredString(font, fit(font, title.getString(), boxWidth - 8), x + width / 2, boxY + 5, accent);
        if (hint != null) g.drawCenteredString(font, fit(font, hint.getString(), boxWidth - 8), x + width / 2, boxY + 17, Ui.TEXT_BRIGHT);
    }

    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        if (width <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
