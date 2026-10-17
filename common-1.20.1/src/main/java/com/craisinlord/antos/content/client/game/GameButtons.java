package com.craisinlord.antos.content.client.game;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class GameButtons {
    static final int GREEN = 0xFF65FF65;
    static final int PALE = 0xFFB8FFB8;
    static final int MUTED = 0xFF6E926E;
    static final int ROW_HEIGHT = 32;
    static final int ROW_GAP = 4;
    static final int BUTTON_HEIGHT = 18;
    private static final int FILL = 0xFF0A2118;
    private static final int HOVER = 0xFF173C2C;
    private static final int OUTLINE = 0xFF315E43;

    private final List<Hit> hits = new ArrayList<>();
    private int originX;
    private int originY;

    void begin(int originX, int originY) {
        hits.clear();
        this.originX = originX;
        this.originY = originY;
    }

    void button(GuiGraphics graphics, Font font, int x, int y, int width, Component label, int mouseX, int mouseY, Runnable action) {
        panel(graphics, x, y, width, BUTTON_HEIGHT, over(x, y, width, BUTTON_HEIGHT, mouseX, mouseY));
        FormattedCharSequence text = trim(font, label, width - 10);
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, y + 5, GREEN, false);
        hitOnly(x, y, width, BUTTON_HEIGHT, action);
    }

    void row(GuiGraphics graphics, Font font, int x, int y, int width, Component title, Component detail, Component badge, int mouseX, int mouseY, Runnable action) {
        panel(graphics, x, y, width, ROW_HEIGHT, over(x, y, width, ROW_HEIGHT, mouseX, mouseY));
        int arrowX = x + width - 36;
        int badgeWidth = badge.getString().isEmpty() ? 0 : font.width(badge);
        if (badgeWidth > 0) graphics.drawString(font, badge, arrowX - 10 - badgeWidth, y + 11, PALE, false);
        int textWidth = arrowX - x - 18 - (badgeWidth > 0 ? badgeWidth + 10 : 0);
        graphics.drawString(font, trim(font, title, textWidth), x + 8, y + 6, GREEN, false);
        graphics.drawString(font, trim(font, detail, textWidth), x + 8, y + 19, PALE, false);
        graphics.drawString(font, Component.literal("[ > ]"), arrowX, y + 11, GREEN, false);
        hitOnly(x, y, width, ROW_HEIGHT, action);
    }

    void hitOnly(int x, int y, int width, int height, Runnable action) {
        hits.add(new Hit(x - originX, y - originY, width, height, action));
    }

    boolean over(int x, int y, int width, int height, int mouseX, int mouseY) {
        int localX = x - originX;
        int localY = y - originY;
        return mouseX >= localX && mouseX < localX + width && mouseY >= localY && mouseY < localY + height;
    }

    boolean click(double mouseX, double mouseY) {
        for (Hit hit : List.copyOf(hits)) {
            if (mouseX >= hit.x() && mouseX < hit.x() + hit.width() && mouseY >= hit.y() && mouseY < hit.y() + hit.height()) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height, boolean hover) {
        graphics.fill(x, y, x + width, y + height, hover ? HOVER : FILL);
        graphics.renderOutline(x, y, width, height, OUTLINE);
    }

    static FormattedCharSequence trim(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("...")));
        return FormattedCharSequence.composite(net.minecraft.locale.Language.getInstance().getVisualOrder(cut), FormattedCharSequence.forward("...", Style.EMPTY));
    }

    static int wrap(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int count = Math.min(lines.size(), maxLines);
        for (int index = 0; index < count; index++) graphics.drawString(font, lines.get(index), x, y + index * 10, color, false);
        return count;
    }

    private record Hit(int x, int y, int width, int height, Runnable action) {}
}
