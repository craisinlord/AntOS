package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class GameTip {
    private static final int MAX_WIDTH = 240;
    private static final int MAX_LINES = 6;

    private GameTip() { }

    public static boolean seen(String key) {
        return GameData.get(key, "").equals("1");
    }

    public static void markSeen(String key) {
        GameData.put(key, "1");
    }

    public static void render(ThemedGraphics g, Font font, int x, int y, int width, int height, Component title, Component body, Component hint) {
        int boxWidth = Math.max(20, Math.min(width - 24, MAX_WIDTH));
        int textWidth = Math.max(10, boxWidth - 16);
        List<FormattedCharSequence> lines = font.split(body, textWidth);
        int count = Math.min(lines.size(), Math.max(1, Math.min(MAX_LINES, (height - 46) / 10)));
        int boxHeight = 38 + count * 10;
        int boxX = x + (width - boxWidth) / 2;
        int boxY = Math.max(y + 2, y + height / 2 - boxHeight / 2);
        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, Ui.alpha(Ui.BACKGROUND, 0xF0));
        g.renderOutline(boxX, boxY, boxWidth, boxHeight, Ui.ACCENT);
        g.drawCenteredString(font, GameOverlay.fit(font, title.getString(), boxWidth - 8), x + width / 2, boxY + 6, Ui.ACCENT);
        g.fill(boxX + 8, boxY + 17, boxX + boxWidth - 8, boxY + 18, Ui.LINE);
        for (int index = 0; index < count; index++) g.drawString(font, lines.get(index), boxX + 8, boxY + 22 + index * 10, Ui.TEXT, false);
        g.drawCenteredString(font, GameOverlay.fit(font, hint.getString(), boxWidth - 8), x + width / 2, boxY + boxHeight - 12, Ui.TEXT_BRIGHT);
    }
}
