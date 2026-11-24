package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.client.gui.components.Whence;

final class AuthoringMultiline extends AbstractWidget {
    private final ContentAuthoring owner;
    private EditorTextModel model;
    private Consumer<String> listener = value -> { };
    private int modelWidth;
    private int limit = 4096;
    private int scroll;
    private long focusTime;

    AuthoringMultiline(ContentAuthoring owner, String label) {
        super(0, 0, 100, 56, Component.literal(label));
        this.owner = owner;
        rebuild("");
    }

    void setCharacterLimit(int limit) { this.limit = limit; model.setCharacterLimit(limit); }
    void setValue(String value) { model.setValue(value); }
    void setValueListener(Consumer<String> listener) { this.listener = listener; model.setValueListener(listener); }

    private void rebuild(String value) {
        modelWidth = Math.max(10, width);
        model = new EditorTextModel(owner.font, modelWidth - 2);
        model.setCharacterLimit(limit);
        model.setValue(value);
        model.setValueListener(listener);
        model.setCursorListener(this::scrollToCursor);
    }

    private void scrollToCursor() {
        int line = model.getLineAtCursor() * 10;
        if (line < scroll) scroll = line;
        if (line + 10 > scroll + height) scroll = line + 10 - height;
        scroll = Math.max(0, Math.min(Math.max(0, model.getLineCount() * 10 - height), scroll));
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (modelWidth != width) {
            String value = model.value();
            int cursor = model.cursor();
            rebuild(value);
            model.seekCursor(Whence.ABSOLUTE, Math.min(cursor, value.length()));
        }
        graphics.flush();
        owner.screen.enableComputerScissor(graphics, getX(), getY(), getX() + width, getY() + height);
        try {
            int top = getY() - scroll;
            int cursor = model.cursor();
            var selection = model.selection();
            for (var line : model.lines()) {
                if (top + 10 > getY() && top < getY() + height) {
                    if (model.hasSelection() && selection.beginIndex() <= line.endIndex() && selection.endIndex() >= line.beginIndex()) {
                        int start = Math.max(line.beginIndex(), selection.beginIndex());
                        int end = Math.min(line.endIndex(), selection.endIndex());
                        int left = getX() + owner.font.width(model.value().substring(line.beginIndex(), start));
                        int right = getX() + owner.font.width(model.value().substring(line.beginIndex(), end));
                        graphics.fill(left, top, Math.max(left + 1, right), top + 10, Ui.SELECTED_FILL);
                    }
                    graphics.drawString(owner.font, model.value().substring(line.beginIndex(), line.endIndex()), getX(), top, Ui.TEXT, false);
                    if (isFocused() && cursor >= line.beginIndex() && cursor <= line.endIndex() && (System.currentTimeMillis() - focusTime) / 400 % 2 == 0) {
                        int caret = getX() + owner.font.width(model.value().substring(line.beginIndex(), cursor));
                        graphics.fill(caret, top, caret + 1, top + 9, Ui.FOCUS);
                    }
                }
                top += 10;
            }
        } finally { graphics.flush(); graphics.disableScissor(); }
    }

    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (focused) focusTime = System.currentTimeMillis(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !isMouseOver(x, y)) return false;
        setFocused(true);
        model.setSelecting(Screen.hasShiftDown());
        model.seekCursorToPoint(x - getX(), y - getY() + scroll);
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!isFocused() || button != 0) return false;
        model.setSelecting(true);
        model.seekCursorToPoint(x - getX(), y - getY() + scroll);
        model.setSelecting(Screen.hasShiftDown());
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        if (!isMouseOver(x, y)) return false;
        scroll = Math.max(0, Math.min(Math.max(0, model.getLineCount() * 10 - height), scroll - (int) (vertical * 10)));
        return true;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (!isFocused() || !net.minecraft.SharedConstants.isAllowedChatCharacter(character)) return false;
        model.insertText(Character.toString(character));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return isFocused() && model.keyPressed(key); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", getMessage(), model.value()));
    }

    private record TextLine(int beginIndex, int endIndex) { }

    private static final class EditorTextModel extends MultilineTextField {
        private java.util.List<TextLine> lines;
        EditorTextModel(net.minecraft.client.gui.Font font, int width) { super(font, width); }
        @Override public void setValueListener(Consumer<String> listener) {
            super.setValueListener(value -> { lines = null; listener.accept(value); });
        }
        java.util.List<TextLine> lines() {
            if (lines == null) {
                java.util.List<TextLine> result = new java.util.ArrayList<>();
                for (StringView line : iterateLines()) result.add(new TextLine(line.beginIndex(), line.endIndex()));
                lines = java.util.List.copyOf(result);
            }
            return lines;
        }
        TextLine selection() { StringView value = getSelected(); return new TextLine(value.beginIndex(), value.endIndex()); }
    }
}
