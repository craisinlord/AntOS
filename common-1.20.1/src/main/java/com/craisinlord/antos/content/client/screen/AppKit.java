package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AntOSUiSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;

final class AppKit {
    static final int PANEL = 0xFF071007;
    static final int MUTED = 0xFF6E926E;
    static final int SOFT = 0xFF315531;
    static final int PLACEHOLDER = 0xFF7EA77E;
    static final int DISABLED = 0xFF587458;
    static final int ERROR = 0xFFFF7777;
    static final int SELECTION = 0xFF2A5E2A;

    private final ComputerApp app;
    private final List<Hit> hits = new ArrayList<>();
    private Track dragging;
    private boolean suppressHover;
    private String tooltip = "";
    private double clickX;
    private double clickY;
    private int clickButton;
    private int[] clip;
    private int focusIndex = -1;
    private boolean keyboardFocus;

    AppKit(ComputerApp app) {
        this.app = app;
    }

    void begin() {
        hits.clear();
        tooltip = "";
        suppressHover = false;
        clip = null;
    }

    void clip(int x, int y, int w, int h) {
        clip = new int[]{x, y, x + w, y + h};
    }

    void unclip() {
        clip = null;
    }

    void suppressHover(boolean value) {
        suppressHover = value;
    }

    boolean hovered(int x, int y, int w, int h) {
        if (suppressHover || !app.screen.hovered(x, y, w, h)) return false;
        if (clip == null) return true;
        int mouseX = app.screen.session.mouseX;
        int mouseY = app.screen.session.mouseY;
        return mouseX >= clip[0] && mouseX < clip[2] && mouseY >= clip[1] && mouseY < clip[3];
    }

    void hit(int x, int y, int w, int h, Runnable action) {
        if (clip != null) {
            int left = Math.max(x, clip[0]);
            int top = Math.max(y, clip[1]);
            int right = Math.min(x + w, clip[2]);
            int bottom = Math.min(y + h, clip[3]);
            if (right <= left || bottom <= top) return;
            hits.add(new Hit(left, top, right - left, bottom - top, action));
            return;
        }
        hits.add(new Hit(x, y, w, h, action));
    }

    void focusTarget(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    boolean focusNext(boolean reverse) {
        if (hits.isEmpty()) return false;
        focusIndex = focusIndex < 0 ? reverse ? hits.size() - 1 : 0
                : Math.floorMod(focusIndex + (reverse ? -1 : 1), hits.size());
        keyboardFocus = true;
        return true;
    }

    boolean activateFocused() {
        if (!keyboardFocus || focusIndex < 0 || focusIndex >= hits.size()) return false;
        AntOSUiSounds.action(hits.get(focusIndex).action());
        return true;
    }

    void drawFocus(GuiGraphics g) {
        if (!keyboardFocus || focusIndex < 0 || focusIndex >= hits.size()) return;
        Hit hit = hits.get(focusIndex);
        g.pose().pushPose();
        try {
            g.pose().translate(0.0F, 0.0F, 180.0F);
            outline(g, hit.x() - 2, hit.y() - 2, hit.w() + 4, hit.h() + 4, BLACK);
            outline(g, hit.x() - 1, hit.y() - 1, hit.w() + 2, hit.h() + 2, PALE_GREEN);
        } finally {
            g.pose().popPose();
        }
    }

    void block(int x, int y, int w, int h) {
        hits.add(new Hit(x, y, w, h, () -> { }));
    }

    boolean click(double mouseX, double mouseY, int button) {
        clickX = mouseX;
        clickY = mouseY;
        clickButton = button;
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (ComputerScreen.inside(hit.x(), hit.y(), hit.w(), hit.h(), mouseX, mouseY)) {
                focusIndex = index;
                keyboardFocus = false;
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    double clickX() {
        return clickX;
    }

    double clickY() {
        return clickY;
    }

    int clickButton() {
        return clickButton;
    }

    boolean draggingScrollbar() {
        return dragging != null;
    }

    void drag(double mouseY) {
        if (dragging == null) return;
        double progress = Math.max(0, Math.min(1, (mouseY - dragging.y() - dragging.thumb() / 2.0) / Math.max(1, dragging.h() - dragging.thumb())));
        dragging.setter().accept((int) Math.round(progress * dragging.maximum()));
    }

    void release() {
        dragging = null;
    }

    void scrollbar(GuiGraphics g, int x, int top, int h, int visible, int total, int scroll, int maximum, IntConsumer setter) {
        if (maximum <= 0) return;
        app.screen.drawScrollbar(g, x, top, h, visible, total, scroll, maximum);
        Track track = new Track(top, h, app.screen.scrollbarThumbHeight(h, visible, total), maximum, setter);
        hit(x - 2, top, 7, h, () -> {
            dragging = track;
            drag(clickY);
        });
    }

    void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    int labelWidth(String label) {
        return app.font.width(label) + 12;
    }

    void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean enabled, Runnable action) {
        button(g, x, y, w, h, label, active, enabled, "", action);
    }

    void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean enabled, String tip, Runnable action) {
        if (w <= 0) return;
        boolean hover = enabled && hovered(x, y, w, h);
        g.fill(x, y, x + w, y + h, active || hover ? HOVER_FILL : PANEL);
        app.screen.box(g, x, y, x + w, y + h, active || hover ? GREEN : DARK_GREEN);
        int color = enabled ? active || hover ? GREEN : PALE_GREEN : DISABLED;
        float scale = Math.min(1.0F, (w - 6.0F) / Math.max(1, app.font.width(label)));
        g.pose().pushPose();
        try {
            g.pose().translate(x + w / 2.0F, y + (h - 8 * scale) / 2.0F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(app.font, Component.literal(label), -app.font.width(label) / 2, 0, color, false);
        } finally {
            g.pose().popPose();
        }
        if (hover && !tip.isEmpty()) tooltip = tip;
        if (enabled) hit(x, y, w, h, () -> AntOSUiSounds.action(action));
    }

    void iconButton(GuiGraphics g, int x, int y, int size, ResourceLocation icon, boolean active, boolean enabled, String tip, Runnable action) {
        boolean hover = enabled && hovered(x, y, size, size);
        g.fill(x, y, x + size, y + size, active || hover ? HOVER_FILL : PANEL);
        outline(g, x, y, size, size, active ? PALE_GREEN : hover ? GREEN : DARK_GREEN);
        icon(g, icon, x + (size - 16) / 2, y + (size - 16) / 2, 16, enabled);
        if (!suppressHover && app.screen.hovered(x, y, size, size) && !tip.isEmpty()) tooltip = tip;
        if (enabled) hit(x, y, size, size, () -> AntOSUiSounds.action(action));
    }

    void icon(GuiGraphics g, ResourceLocation texture, int x, int y, int size, boolean enabled) {
        if (enabled) {
            PixelIcons.draw(g, texture, x, y, size);
            return;
        }
        g.setColor(0.42F, 0.42F, 0.42F, 1.0F);
        PixelIcons.draw(g, texture, x, y, size);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static String fileError(String code) {
        String value = code == null || code.isBlank() ? "unknown" : code;
        return Component.translatableWithFallback("computer.antos.file_error." + value, value.toUpperCase(java.util.Locale.ROOT).replace('_', ' ')).getString();
    }

    void tooltip(String text) {
        tooltip = text;
    }

    void drawTooltip(GuiGraphics g, int left, int top, int right, int bottom) {
        if (tooltip.isEmpty()) return;
        int availableWidth = Math.max(1, right - left - 10);
        var lines = app.font.split(Component.literal(tooltip), availableWidth);
        int count = Math.min(lines.size(), Math.max(1, (bottom - top - 6) / 11));
        int width = Math.min(right - left, lines.stream().limit(count).mapToInt(app.font::width).max().orElse(0) + 10);
        int height = count * 11 + 6;
        int x = Math.max(left, Math.min(right - width, app.screen.session.mouseX + 8));
        int y = app.screen.session.mouseY + 12;
        if (y + height > bottom) y = app.screen.session.mouseY - height - 4;
        y = Math.max(top, y);
        g.pose().pushPose();
        try {
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.fill(x, y, x + width, y + height, BLACK);
            outline(g, x, y, width, height, GREEN);
            for (int index = 0; index < count; index++) g.drawString(app.font, lines.get(index), x + 5, y + 4 + index * 11, PALE_GREEN, false);
        } finally {
            g.pose().popPose();
        }
    }

    void field(GuiGraphics g, int x, int y, int w, int h, String value, int cursor, boolean focused, String placeholder) {
        g.fill(x, y, x + w, y + h, DARK_GREEN);
        app.screen.box(g, x, y, x + w, y + h, focused ? PALE_GREEN : SOFT);
        int inner = Math.max(1, w - 10);
        int textY = y + (h - 8) / 2;
        if (value.isEmpty()) {
            if (!placeholder.isEmpty()) g.drawString(app.font, Component.literal(app.screen.trimToWidth(placeholder, inner)), x + 5, textY, PLACEHOLDER, false);
            if (focused && app.screen.caretVisible()) g.fill(x + 5, textY - 1, x + 6, textY + 9, PALE_GREEN);
            return;
        }
        int caret = Math.max(0, Math.min(cursor, value.length()));
        int start = 0;
        while (start < caret && app.font.width(value.substring(start, caret)) > inner - 2) start++;
        String visible = app.font.plainSubstrByWidth(value.substring(start), inner);
        g.drawString(app.font, Component.literal(visible), x + 5, textY, PALE_GREEN, false);
        if (focused && app.screen.caretVisible()) {
            int caretX = x + 5 + app.font.width(value.substring(start, caret));
            g.fill(caretX, textY - 1, caretX + 1, textY + 9, PALE_GREEN);
        }
    }

    void divider(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y + 3, x + 1, y + h - 3, SOFT);
    }

    private record Hit(int x, int y, int w, int h, Runnable action) { }

    private record Track(int y, int h, int thumb, int maximum, IntConsumer setter) { }
}
