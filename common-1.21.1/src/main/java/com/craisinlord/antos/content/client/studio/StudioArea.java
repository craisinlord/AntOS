package com.craisinlord.antos.content.client.studio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;

final class StudioArea extends AbstractWidget {
    private final StudioKit kit;
    private final int limit;
    private Model model;
    private Consumer<String> listener = value -> { };
    private int modelWidth;
    private int scroll;
    private long focusTime;
    private boolean syncing;

    StudioArea(StudioKit kit, int limit) {
        super(0, 0, 100, 30, Component.empty());
        this.kit = kit;
        this.limit = limit;
        rebuild("");
    }

    void listener(Consumer<String> value) { listener = value; }
    String value() { return model.value(); }
    int contentHeight() { return model.getLineCount() * 10; }

    void sync(String value) {
        if (model.value().equals(value)) return;
        syncing = true;
        try { model.setValue(value); } finally { syncing = false; }
    }

    void insert(String text) {
        model.insertText(text);
    }

    private void rebuild(String value) {
        Font font = kit.font();
        modelWidth = Math.max(10, width);
        syncing = true;
        try {
            model = new Model(font, modelWidth - 2);
            model.setCharacterLimit(limit);
            model.setValue(value);
            model.setValueListener(text -> { if (!syncing) listener.accept(text); });
            model.setCursorListener(this::follow);
        } finally { syncing = false; }
    }

    private void follow() {
        int line = model.getLineAtCursor() * 10;
        if (line < scroll) scroll = line;
        if (line + 10 > scroll + height) scroll = line + 10 - height;
        limitScroll();
    }

    private void limitScroll() { scroll = Math.max(0, Math.min(Math.max(0, contentHeight() - height), scroll)); }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (modelWidth != Math.max(10, width)) {
            String value = model.value();
            int cursor = model.cursor();
            rebuild(value);
            model.seekCursor(Whence.ABSOLUTE, Math.min(cursor, value.length()));
        }
        limitScroll();
        kit.clip(getX(), getY(), width, height);
        try {
            Font font = kit.font();
            int top = getY() - scroll;
            int cursor = model.cursor();
            Line selection = model.selection();
            for (Line line : model.lines()) {
                if (top + 10 > getY() && top < getY() + height) {
                    String shown = model.value().substring(line.begin(), line.end());
                    if (model.hasSelection() && selection.begin() <= line.end() && selection.end() >= line.begin()) {
                        int start = Math.max(line.begin(), selection.begin());
                        int end = Math.min(line.end(), selection.end());
                        if (end >= start) {
                            int left = getX() + font.width(model.value().substring(line.begin(), start));
                            int right = getX() + font.width(model.value().substring(line.begin(), end));
                            graphics.fill(left, top - 1, Math.max(left + 1, right), top + 9, StudioTheme.strongLine);
                        }
                    }
                    for (int from = 0; from <= shown.length(); ) {
                        int tab = shown.indexOf('	', from);
                        int to = tab < 0 ? shown.length() : tab;
                        if (to > from) graphics.drawString(font, shown.substring(from, to), getX() + font.width(shown.substring(0, from)), top, StudioTheme.text, false);
                        from = to + 1;
                    }
                    if (isFocused() && cursor >= line.begin() && cursor <= line.end() && (System.currentTimeMillis() - focusTime) / 400 % 2 == 0) {
                        int caret = getX() + font.width(model.value().substring(line.begin(), cursor));
                        graphics.fill(caret, top - 1, caret + 1, top + 9, StudioTheme.accent);
                    }
                }
                top += 10;
            }
        } finally { kit.unclip(); }
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) focusTime = System.currentTimeMillis();
        else if (model.hasSelection()) {
            model.setSelecting(false);
            model.seekCursor(Whence.ABSOLUTE, model.cursor());
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        model.setSelecting(Screen.hasShiftDown());
        model.seekCursorToPoint(x - getX(), (y - getY() + scroll) * 0.9);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!isFocused() || button != 0) return false;
        model.setSelecting(true);
        model.seekCursorToPoint(x - getX(), (y - getY() + scroll) * 0.9);
        model.setSelecting(Screen.hasShiftDown());
        return true;
    }

    boolean wheel(double amount) {
        int before = scroll;
        scroll -= (int) (amount * 10);
        limitScroll();
        return before != scroll;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!isFocused() || !StringUtil.isAllowedChatCharacter(character)) return false;
        model.insertText(Character.toString(character));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return isFocused() && model.keyPressed(key);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", getMessage(), model.value()));
    }

    private record Line(int begin, int end) { }

    private static final class Model extends MultilineTextField {
        private List<Line> lines;

        Model(Font font, int width) { super(font, width); }

        @Override
        public void setValueListener(Consumer<String> listener) {
            super.setValueListener(value -> {
                lines = null;
                listener.accept(value);
            });
        }

        @Override
        public void setValue(String text) {
            lines = null;
            super.setValue(text);
        }

        List<Line> lines() {
            if (lines == null) {
                List<Line> result = new ArrayList<>();
                for (StringView view : iterateLines()) result.add(new Line(view.beginIndex(), view.endIndex()));
                lines = List.copyOf(result);
            }
            return lines;
        }

        Line selection() {
            StringView view = getSelected();
            return new Line(view.beginIndex(), view.endIndex());
        }
    }
}
