package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;

final class TextEditorApp extends ComputerApp {
    private static final int LINE = 11;
    private static final int TOOLBAR = 18;
    private static final int UNDO_LIMIT = 200;
    private static final int KIND_OTHER = 0;
    private static final int KIND_TYPE = 1;
    private static final int KIND_DELETE = 2;
    private static final String UNTITLED = "/documents/untitled.txt";

    final State state = new State();
    private final AppKit kit = new AppKit(this);
    private final FileDialog dialog = new FileDialog(this, kit);
    private final List<int[]> rows = new ArrayList<>();
    private String layoutContent;
    private int[] rowLineStarts = new int[1];
    private String statsContent;
    private int statsWords;
    private int[] statsNewlines = new int[0];
    private int layoutWidth = -1;
    private int textX;
    private int textY;
    private int textWidth = 1;
    private int areaTop;
    private int areaBottom;
    private int visibleRows = 1;
    private boolean selectingWithMouse;
    private long lastClickTime;
    private int lastClickOffset = -1;
    private int clickCount;
    private String confirmAction = "";
    private long confirmUntil;
    private String notice = "";
    private int noticeColor = GREEN;
    private long noticeUntil;
    private boolean listed;
    private String matchQuery;
    private String matchContent;
    private final List<Integer> matches = new ArrayList<>();

    void sync() {
        var fileState = ComputerFileSystemClientState.get();
        if (fileState.openSerial() != state.openSerial) {
            state.openSerial = fileState.openSerial();
            String opened = fileState.openedPath();
            boolean requested = !state.pendingOpenPath.isEmpty() && opened.equals(state.pendingOpenPath);
            if (!opened.isEmpty() && !opened.toLowerCase(Locale.ROOT).endsWith(".antpaint")) {
                if (requested || !state.dirty) load(opened, fileState.openedContents());
                else if (!opened.equals(state.path)) notice(tr("notice.open_blocked"), AppKit.ERROR);
            }
            if (requested) state.pendingOpenPath = "";
        }
        if (!state.pendingPath.isEmpty() && fileState.mutationSerial() != state.pendingSerial
                && fileState.lastMutationPath().equals(state.pendingPath)) {
            resolvePending(fileState.lastMutationSuccess(), fileState.lastMutationError());
        }
        if (!state.content.equals(state.knownContent)) {
            state.knownContent = state.content;
            state.savedContent = state.content;
            state.undo.clear();
            state.redo.clear();
        }
        state.cursor = clamp(state.cursor, 0, state.content.length());
        if (state.anchor > state.content.length()) state.anchor = -1;
    }

    private void load(String path, String contents) {
        state.path = path;
        state.content = contents;
        state.knownContent = contents;
        state.savedContent = contents;
        state.cursor = 0;
        state.anchor = -1;
        state.scroll = 0;
        state.preferredX = -1;
        state.dirty = false;
        state.focused = true;
        state.undo.clear();
        state.redo.clear();
        state.deleteConfirm = false;
    }

    private void resolvePending(boolean success, String error) {
        String kind = state.pendingKind;
        String path = state.pendingPath;
        state.pendingPath = "";
        if (!success) {
            notice(tr("notice.failed", AppKit.fileError(error)), AppKit.ERROR);
            return;
        }
        switch (kind) {
            case "save" -> {
                state.savedContent = state.pendingContent;
                state.dirty = !state.content.equals(state.pendingContent);
                notice(tr("notice.saved"), PALE_GREEN);
            }
            case "save_as" -> {
                state.path = path;
                state.savedContent = state.pendingContent;
                state.dirty = !state.content.equals(state.pendingContent);
                notice(tr("notice.saved"), PALE_GREEN);
            }
            case "rename" -> {
                state.path = path;
                notice(tr("notice.renamed"), PALE_GREEN);
            }
            case "delete" -> {
                reset();
                notice(tr("notice.deleted"), PALE_GREEN);
            }
            default -> { }
        }
        ComputerNetworking.listFiles();
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        if (!listed) {
            listed = true;
            ComputerNetworking.listFiles();
        }
        sync();
        kit.begin();
        kit.suppressHover(dialog.active());
        long now = System.currentTimeMillis();
        if (!confirmAction.isEmpty() && now > confirmUntil) confirmAction = "";
        renderToolbar(g, x, y, w);
        int top = y + TOOLBAR + 3;
        if (state.finding) {
            renderFindBar(g, x, top, w);
            top += TOOLBAR + 3;
        }
        renderText(g, x, top, w, y + h - 14 - top);
        renderFooter(g, x, y + h - 12, w);
        dialog.render(g, x, y, w, h);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderToolbar(GuiGraphics g, int x, int y, int w) {
        int buttonX = x;
        boolean confirmNew = confirmAction.equals("new");
        int newWidth = Math.max(kit.labelWidth(tr("new")), kit.labelWidth(tr("confirm")));
        kit.button(g, buttonX, y, newWidth, TOOLBAR, confirmNew ? tr("confirm") : tr("new"), confirmNew, true, tr("tip.new"), this::newFile);
        buttonX += newWidth + 3;
        buttonX = toolbarButton(g, buttonX, y, tr("open"), tr("tip.open"), this::beginOpen);
        buttonX = toolbarButton(g, buttonX, y, tr("save"), tr("tip.save"), this::save);
        buttonX = toolbarButton(g, buttonX, y, tr("save_as"), tr("tip.save_as"), this::beginSaveAs);
        int findWidth = kit.labelWidth(tr("find"));
        if (buttonX + 3 + findWidth <= x + w) {
            kit.button(g, x + w - findWidth, y, findWidth, TOOLBAR, tr("find"), state.finding, true, tr("tip.find"), this::toggleFind);
        }
    }

    private int toolbarButton(GuiGraphics g, int x, int y, String label, String tip, Runnable action) {
        int width = kit.labelWidth(label);
        kit.button(g, x, y, width, TOOLBAR, label, false, true, tip, action);
        return x + width + 3;
    }

    private void renderFindBar(GuiGraphics g, int x, int y, int w) {
        updateMatches();
        int buttonWidth = 18;
        String count = matches.isEmpty() ? state.findQuery.isEmpty() ? "" : tr("find.none")
                : tr("find.count", state.findIndex < 0 ? 0 : state.findIndex + 1, matches.size());
        int countWidth = Math.max(font.width(tr("find.none")), font.width(count)) + 8;
        int fieldWidth = Math.max(30, w - buttonWidth * 3 - 6 - countWidth);
        kit.field(g, x, y, fieldWidth, TOOLBAR, state.findQuery, state.findQuery.length(), state.findFocused, tr("find.placeholder"));
        kit.hit(x, y, fieldWidth, TOOLBAR, () -> {
            state.findFocused = true;
            state.focused = false;
        });
        g.drawString(font, Component.literal(count), x + fieldWidth + 5, y + 5, matches.isEmpty() ? AppKit.MUTED : PALE_GREEN, false);
        int buttonX = x + w - buttonWidth * 3 - 4;
        kit.button(g, buttonX, y, buttonWidth, TOOLBAR, "<", false, !matches.isEmpty(), tr("tip.find_previous"), () -> findStep(false));
        kit.button(g, buttonX + buttonWidth + 2, y, buttonWidth, TOOLBAR, ">", false, !matches.isEmpty(), tr("tip.find_next"), () -> findStep(true));
        kit.button(g, buttonX + buttonWidth * 2 + 4, y, buttonWidth, TOOLBAR, "X", false, true, tr("tip.find_close"), this::closeFind);
    }

    private void renderText(GuiGraphics g, int x, int top, int w, int height) {
        int bottom = top + Math.max(20, height);
        areaTop = top;
        areaBottom = bottom;
        int right = x + w;
        g.fill(x, top, right, bottom, BLACK);
        String lineNumbers = com.craisinlord.antos.config.AntOSSettings.preferences().editorLineNumbers;
        boolean gutter = lineNumbers.equals("on") || (lineNumbers.equals("auto") && w >= 300);
        int logicalLines = 1;
        if (gutter) {
            contentStats();
            logicalLines += statsNewlines.length;
        }
        int gutterWidth = gutter ? Math.max(20, font.width(String.valueOf(logicalLines)) + 10) : 0;
        if (gutter) {
            g.fill(x + 2, top + 2, x + 2 + gutterWidth, bottom - 2, AppKit.PANEL);
            g.fill(x + 2 + gutterWidth, top + 2, x + 3 + gutterWidth, bottom - 2, DARK_GREEN);
        }
        screen.box(g, x, top, right, bottom, state.focused && !dialog.active() ? PALE_GREEN : AppKit.SOFT);
        textX = x + 2 + gutterWidth + 5;
        textY = top + 6;
        textWidth = Math.max(10, right - 10 - textX);
        visibleRows = Math.max(1, (bottom - 2 - textY) / LINE);
        layout();
        state.maximumScroll = Math.max(0, rows.size() - visibleRows);
        if (state.revealCaret) {
            int caretRow = rowOf(state.cursor);
            if (caretRow < state.scroll) state.scroll = caretRow;
            if (caretRow >= state.scroll + visibleRows) state.scroll = caretRow - visibleRows + 1;
            state.revealCaret = false;
        }
        state.scroll = clamp(state.scroll, 0, state.maximumScroll);
        kit.hit(x, top, w, bottom - top, this::pressText);
        updateMatches();
        int selectionStart = hasSelection() ? Math.min(state.anchor, state.cursor) : -1;
        int selectionEnd = hasSelection() ? Math.max(state.anchor, state.cursor) : -1;
        int queryLength = state.finding ? state.findQuery.length() : 0;
        int lineNumber = 1;
        if (gutter && state.scroll > 0) lineNumber += rowLineStarts[Math.min(state.scroll, rows.size())];
        for (int row = state.scroll; row < rows.size() && row < state.scroll + visibleRows; row++) {
            int start = rows.get(row)[0];
            int end = rows.get(row)[1];
            int lineY = textY + (row - state.scroll) * LINE;
            if (queryLength > 0) {
                for (int match = firstMatchEndingAfter(start, queryLength); match < matches.size(); match++) {
                    int matchStart = matches.get(match);
                    int matchEnd = matchStart + queryLength;
                    if (matchStart >= end) break;
                    if (matchEnd <= start) continue;
                    highlight(g, start, Math.max(start, matchStart), Math.min(end, matchEnd), lineY, match == state.findIndex ? 0xFF4C8A4C : 0xFF1D3D1D);
                }
            }
            if (selectionStart >= 0 && selectionEnd > start && selectionStart <= end) {
                int from = Math.max(start, selectionStart);
                int to = Math.min(end, selectionEnd);
                if (to > from) highlight(g, start, from, to, lineY, AppKit.SELECTION);
                if (selectionEnd > end && end < state.content.length() && state.content.charAt(end) == '\n') {
                    int edge = textX + font.width(state.content.substring(start, end));
                    g.fill(edge, lineY - 1, edge + 3, lineY + 10, AppKit.SELECTION);
                }
            }
            if (end > start) g.drawString(font, Component.literal(state.content.substring(start, end)), textX, lineY, PALE_GREEN, false);
            if (gutter && isLineStart(start)) {
                String number = String.valueOf(lineNumber);
                g.drawString(font, Component.literal(number), x + 2 + gutterWidth - 4 - font.width(number), lineY, AppKit.MUTED, false);
            }
            if (gutter && isLineStart(start)) lineNumber++;
        }
        if (state.content.isEmpty()) {
            g.drawString(font, Component.literal(screen.trimToWidth(tr("placeholder"), textWidth)), textX + 2, textY, AppKit.PLACEHOLDER, false);
        }
        if (state.focused && !dialog.active() && screen.caretVisible()) {
            int caretRow = rowOf(state.cursor);
            if (caretRow >= state.scroll && caretRow < state.scroll + visibleRows) {
                int caretX = textX + font.width(state.content.substring(rows.get(caretRow)[0], state.cursor));
                int caretY = textY + (caretRow - state.scroll) * LINE;
                g.fill(caretX, caretY - 1, caretX + 1, caretY + 9, GREEN);
            }
        }
        kit.scrollbar(g, right - 6, top + 4, bottom - top - 8, visibleRows, rows.size(), state.scroll, state.maximumScroll, value -> state.scroll = value);
    }

    private int firstMatchEndingAfter(int offset, int queryLength) {
        int low = 0;
        int high = matches.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (matches.get(middle) + queryLength <= offset) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private void contentStats() {
        String content = state.content;
        if (content == statsContent) return;
        int count = 0;
        int newlines = 0;
        boolean inWord = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '\n') newlines++;
            boolean letter = !Character.isWhitespace(character);
            if (letter && !inWord) count++;
            inWord = letter;
        }
        int[] positions = new int[newlines];
        int next = 0;
        for (int index = 0; index < content.length() && next < newlines; index++) if (content.charAt(index) == '\n') positions[next++] = index;
        statsContent = content;
        statsWords = count;
        statsNewlines = positions;
    }

    private void highlight(GuiGraphics g, int rowStart, int from, int to, int lineY, int color) {
        int left = textX + font.width(state.content.substring(rowStart, from));
        int right = textX + font.width(state.content.substring(rowStart, to));
        g.fill(left, lineY - 1, Math.max(left + 1, right), lineY + 10, color);
    }

    private void renderFooter(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, DARK_GREEN);
        int textLine = y + 3;
        String right;
        int rightColor = AppKit.MUTED;
        if (state.deleteConfirm) {
            right = tr("notice.delete_confirm");
            rightColor = AppKit.ERROR;
        } else if (System.currentTimeMillis() < noticeUntil) {
            right = notice;
            rightColor = noticeColor;
        } else {
            int lineStart = state.content.lastIndexOf('\n', Math.max(0, state.cursor - 1)) + 1;
            if (state.cursor == 0) lineStart = 0;
            contentStats();
            int found = java.util.Arrays.binarySearch(statsNewlines, lineStart);
            int line = 1 + (found >= 0 ? found : -found - 1);
            String position = tr("position", line, state.cursor - lineStart + 1);
            String stats = hasSelection() ? tr("selected", Math.abs(state.cursor - state.anchor))
                    : tr("stats", wordCount(), state.content.length());
            right = w >= 300 || font.width(stats + "   " + position) <= w - 90 ? stats + "   " + position : position;
        }
        right = screen.trimToWidth(right, Math.max(10, w - 50));
        int rightWidth = font.width(right);
        g.drawString(font, Component.literal(right), x + w - rightWidth, textLine, rightColor, false);
        String name = fileName() + (state.dirty ? " *" : "");
        g.drawString(font, Component.literal(screen.trimToWidth(name, Math.max(10, w - rightWidth - 8))), x, textLine, state.dirty ? PALE_GREEN : GREEN, false);
    }

    private int wordCount() {
        contentStats();
        return statsWords;
    }

    private String fileName() {
        return state.path.substring(state.path.lastIndexOf('/') + 1);
    }

    private void layout() {
        if (state.content.equals(layoutContent) && layoutWidth == textWidth) return;
        layoutContent = state.content;
        layoutWidth = textWidth;
        rows.clear();
        String content = state.content;
        int lineStart = 0;
        while (true) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = content.length();
            int start = lineStart;
            do {
                int width = 0;
                int index = start;
                int lastBreak = -1;
                while (index < lineEnd) {
                    char character = content.charAt(index);
                    int characterWidth = font.width(String.valueOf(character));
                    if (width + characterWidth > textWidth && index > start) break;
                    width += characterWidth;
                    if (character == ' ') lastBreak = index + 1;
                    index++;
                }
                int end = index < lineEnd && lastBreak > start ? lastBreak : index;
                rows.add(new int[]{start, end});
                start = end;
            } while (start < lineEnd);
            if (lineEnd >= content.length()) break;
            lineStart = lineEnd + 1;
        }
        int[] starts = new int[rows.size() + 1];
        for (int index = 0; index < rows.size(); index++) starts[index + 1] = starts[index] + (isLineStart(rows.get(index)[0]) ? 1 : 0);
        rowLineStarts = starts;
    }

    private boolean isLineStart(int offset) {
        return offset == 0 || state.content.charAt(offset - 1) == '\n';
    }

    private int rowOf(int offset) {
        layout();
        int low = 0;
        int high = rows.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (rows.get(middle)[0] <= offset) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private boolean softWrapped(int[] row) {
        return row[1] < state.content.length() && state.content.charAt(row[1]) != '\n' && row[1] > row[0];
    }

    private int rowEnd(int[] row) {
        return softWrapped(row) && state.content.charAt(row[1] - 1) == ' ' ? row[1] - 1 : row[1];
    }

    private int offsetAt(int row, double pixelX) {
        int[] bounds = rows.get(clamp(row, 0, rows.size() - 1));
        int width = 0;
        for (int index = bounds[0]; index < bounds[1]; index++) {
            int characterWidth = font.width(String.valueOf(state.content.charAt(index)));
            if (pixelX < width + characterWidth / 2.0) return index;
            width += characterWidth;
        }
        return rowEnd(bounds);
    }

    private int offsetAtPoint(double mouseX, double mouseY) {
        layout();
        int row = state.scroll + (int) Math.floor((mouseY - textY + 1) / LINE);
        return offsetAt(clamp(row, 0, rows.size() - 1), mouseX - textX);
    }

    private int caretPixel() {
        int[] row = rows.get(rowOf(state.cursor));
        return font.width(state.content.substring(row[0], state.cursor));
    }

    private void pressText() {
        if (kit.clickButton() != 0) return;
        state.focused = true;
        state.findFocused = false;
        state.deleteConfirm = false;
        int offset = offsetAtPoint(kit.clickX(), kit.clickY());
        long now = System.currentTimeMillis();
        clickCount = now - lastClickTime < 350 && offset == lastClickOffset ? clickCount + 1 : 1;
        lastClickTime = now;
        lastClickOffset = offset;
        state.preferredX = -1;
        if (clickCount == 2) {
            selectWord(offset);
            return;
        }
        if (clickCount >= 3) {
            int start = state.content.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
            if (offset == 0) start = 0;
            int end = state.content.indexOf('\n', offset);
            state.anchor = start;
            state.cursor = end < 0 ? state.content.length() : end;
            return;
        }
        if (Screen.hasShiftDown()) {
            if (state.anchor < 0) state.anchor = state.cursor;
        } else state.anchor = offset;
        state.cursor = offset;
        selectingWithMouse = true;
    }

    private void selectWord(int offset) {
        int start = offset;
        int end = offset;
        while (start > 0 && wordCharacter(state.content.charAt(start - 1))) start--;
        while (end < state.content.length() && wordCharacter(state.content.charAt(end))) end++;
        if (start == end && end < state.content.length()) end++;
        state.anchor = start;
        state.cursor = end;
    }

    void drag(double mouseX, double mouseY) {
        if (kit.draggingScrollbar()) {
            kit.drag(mouseY);
            return;
        }
        if (!selectingWithMouse || dialog.active()) return;
        if (mouseY < areaTop + 2) state.scroll = Math.max(0, state.scroll - 1);
        else if (mouseY > areaBottom - 2) state.scroll = Math.min(state.maximumScroll, state.scroll + 1);
        state.cursor = offsetAtPoint(mouseX, clamp((int) mouseY, textY, textY + visibleRows * LINE - 1));
    }

    void release() {
        kit.release();
        selectingWithMouse = false;
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        boolean handled = kit.click(mouseX, mouseY, button);
        if (!handled && !dialog.active()) {
            state.focused = false;
            state.findFocused = false;
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (dialog.active()) return dialog.scroll(scrollY);
        state.scroll = clamp(state.scroll - (int) Math.signum(scrollY) * 3, 0, state.maximumScroll);
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (dialog.active()) return dialog.type(codePoint);
        if (codePoint < 32 || codePoint == 127) return false;
        if (state.finding && state.findFocused) {
            if (state.findQuery.length() < 48) state.findQuery += codePoint;
            state.findIndex = -1;
            findStep(true, true);
            return true;
        }
        if (!state.focused) return false;
        insert(String.valueOf(codePoint), KIND_TYPE);
        return true;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (dialog.active()) return dialog.key(keyCode, modifiers);
        boolean control = screen.hasControl(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (state.deleteConfirm) {
            state.deleteConfirm = false;
            if (enter) {
                deleteFile();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        }
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_S -> {
                    if (shift) beginSaveAs();
                    else save();
                    return true;
                }
                case GLFW.GLFW_KEY_O -> {
                    beginOpen();
                    return true;
                }
                case GLFW.GLFW_KEY_N -> {
                    newFile();
                    return true;
                }
                case GLFW.GLFW_KEY_R -> {
                    beginRename();
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    openFind();
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (fileExists(state.path) && !ComputerFileSystem.isProtected(state.path)) state.deleteConfirm = true;
                    else notice(tr("notice.save_first"), AppKit.ERROR);
                    return true;
                }
                default -> { }
            }
        }
        if (state.finding && state.findFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) closeFind();
            else if (enter) findStep(!shift);
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !state.findQuery.isEmpty()) {
                state.findQuery = state.findQuery.substring(0, state.findQuery.length() - 1);
                state.findIndex = -1;
                findStep(true, true);
            } else if (control && keyCode == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) {
                    String line = clip.replace("\r", "").replace("\n", " ");
                    state.findQuery = (state.findQuery + line).substring(0, Math.min(48, state.findQuery.length() + line.length()));
                    state.findIndex = -1;
                    findStep(true, true);
                }
            } else if (keyCode == GLFW.GLFW_KEY_TAB) {
                state.findFocused = false;
                state.focused = true;
            }
            return true;
        }
        if (!state.focused) return false;
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> {
                    state.anchor = 0;
                    state.cursor = state.content.length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (hasSelection()) Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                        insert("", KIND_OTHER);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) insert(clip.replace("\r", "").replace("\t", "    "), KIND_OTHER);
                    return true;
                }
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) redo();
                    else undo();
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    redo();
                    return true;
                }
                default -> { }
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) insert("", KIND_DELETE);
                else if (state.cursor > 0) {
                    int start = control ? previousWord(state.cursor) : state.cursor - 1;
                    replace(start, state.cursor, "", KIND_DELETE);
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) insert("", KIND_DELETE);
                else if (state.cursor < state.content.length()) replace(state.cursor, state.cursor + 1, "", KIND_DELETE);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (hasSelection() && !shift) moveTo(Math.min(state.anchor, state.cursor), false);
                else moveTo(control ? previousWord(state.cursor) : Math.max(0, state.cursor - 1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (hasSelection() && !shift) moveTo(Math.max(state.anchor, state.cursor), false);
                else moveTo(control ? nextWord(state.cursor) : Math.min(state.content.length(), state.cursor + 1), shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveRows(-1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveRows(1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                moveRows(-visibleRows, shift);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                moveRows(visibleRows, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveTo(control ? 0 : rows.isEmpty() ? 0 : rows.get(rowOf(state.cursor))[0], shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveTo(control ? state.content.length() : rowEnd(rows.get(rowOf(state.cursor))), shift);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insert("\n", KIND_OTHER);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                insert("    ", KIND_TYPE);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (hasSelection()) state.anchor = -1;
                else state.focused = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void moveTo(int offset, boolean extend) {
        if (extend) {
            if (state.anchor < 0) state.anchor = state.cursor;
        } else state.anchor = -1;
        state.cursor = clamp(offset, 0, state.content.length());
        state.preferredX = -1;
        state.revealCaret = true;
    }

    private void moveRows(int amount, boolean extend) {
        layout();
        int row = rowOf(state.cursor);
        int target = row + amount;
        int pixel = state.preferredX >= 0 ? state.preferredX : caretPixel();
        int offset;
        if (target < 0) offset = 0;
        else if (target >= rows.size()) offset = state.content.length();
        else offset = offsetAt(target, pixel);
        moveTo(offset, extend);
        state.preferredX = pixel;
        if (Math.abs(amount) > 1) state.scroll = clamp(state.scroll + amount, 0, state.maximumScroll);
    }

    private static boolean wordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '\'';
    }

    private int previousWord(int offset) {
        int index = offset;
        while (index > 0 && !wordCharacter(state.content.charAt(index - 1))) index--;
        while (index > 0 && wordCharacter(state.content.charAt(index - 1))) index--;
        return index;
    }

    private int nextWord(int offset) {
        int index = offset;
        while (index < state.content.length() && !wordCharacter(state.content.charAt(index))) index++;
        while (index < state.content.length() && wordCharacter(state.content.charAt(index))) index++;
        return index;
    }

    private boolean hasSelection() {
        return state.anchor >= 0 && state.anchor != state.cursor;
    }

    private String selectedText() {
        return state.content.substring(Math.min(state.anchor, state.cursor), Math.max(state.anchor, state.cursor));
    }

    private void insert(String text, int kind) {
        int start = hasSelection() ? Math.min(state.anchor, state.cursor) : state.cursor;
        int end = hasSelection() ? Math.max(state.anchor, state.cursor) : state.cursor;
        replace(start, end, text, kind);
    }

    private void replace(int start, int end, String text, int kind) {
        int room = ComputerFileSystem.MAX_FILE_SIZE - (state.content.length() - (end - start));
        if (text.length() > room) {
            text = text.substring(0, Math.max(0, room));
            notice(tr("notice.limit"), AppKit.ERROR);
            if (text.isEmpty() && start == end) return;
        }
        remember(kind);
        state.content = state.content.substring(0, start) + text + state.content.substring(end);
        state.knownContent = state.content;
        state.cursor = start + text.length();
        state.anchor = -1;
        state.preferredX = -1;
        state.dirty = !state.content.equals(state.savedContent);
        state.revealCaret = true;
        state.deleteConfirm = false;
    }

    private void remember(int kind) {
        long now = System.currentTimeMillis();
        if (kind == KIND_OTHER || kind != state.lastEditKind || now - state.lastEditTime > 1200 || state.undo.isEmpty()) {
            state.undo.addLast(new Snapshot(state.content, state.cursor, state.anchor));
            while (state.undo.size() > UNDO_LIMIT) state.undo.removeFirst();
        }
        state.redo.clear();
        state.lastEditKind = kind;
        state.lastEditTime = now;
    }

    private void undo() {
        if (state.undo.isEmpty()) return;
        state.redo.addLast(new Snapshot(state.content, state.cursor, state.anchor));
        restore(state.undo.removeLast());
    }

    private void redo() {
        if (state.redo.isEmpty()) return;
        state.undo.addLast(new Snapshot(state.content, state.cursor, state.anchor));
        restore(state.redo.removeLast());
    }

    private void restore(Snapshot snapshot) {
        state.content = snapshot.content();
        state.knownContent = snapshot.content();
        state.cursor = clamp(snapshot.cursor(), 0, state.content.length());
        state.anchor = snapshot.anchor() > state.content.length() ? -1 : snapshot.anchor();
        state.lastEditKind = -1;
        state.dirty = !state.content.equals(state.savedContent);
        state.revealCaret = true;
    }

    private void openFind() {
        state.finding = true;
        state.findFocused = true;
        state.focused = false;
        if (hasSelection() && !selectedText().contains("\n")) {
            String selection = selectedText();
            state.findQuery = selection.substring(0, Math.min(48, selection.length()));
        }
        state.findIndex = -1;
        findStep(true, true);
    }

    private void toggleFind() {
        if (state.finding) closeFind();
        else openFind();
    }

    private void closeFind() {
        state.finding = false;
        state.findFocused = false;
        state.focused = true;
    }

    private void updateMatches() {
        if (state.findQuery.equals(matchQuery) && state.content.equals(matchContent)) return;
        matchQuery = state.findQuery;
        matchContent = state.content;
        matches.clear();
        if (state.findQuery.isEmpty()) return;
        String haystack = state.content.toLowerCase(Locale.ROOT);
        String needle = state.findQuery.toLowerCase(Locale.ROOT);
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            matches.add(index);
            index = haystack.indexOf(needle, index + Math.max(1, needle.length()));
        }
        if (state.findIndex >= matches.size()) state.findIndex = -1;
    }

    private void findStep(boolean forward) {
        findStep(forward, false);
    }

    private void findStep(boolean forward, boolean fromCursor) {
        updateMatches();
        if (matches.isEmpty()) {
            state.findIndex = -1;
            return;
        }
        int origin = hasSelection() ? Math.min(state.anchor, state.cursor) : state.cursor;
        int target = -1;
        if (state.findIndex >= 0 && !fromCursor) {
            target = Math.floorMod(state.findIndex + (forward ? 1 : -1), matches.size());
        } else if (forward) {
            for (int index = 0; index < matches.size(); index++) if (matches.get(index) >= origin) { target = index; break; }
            if (target < 0) target = 0;
        } else {
            for (int index = matches.size() - 1; index >= 0; index--) if (matches.get(index) < origin) { target = index; break; }
            if (target < 0) target = matches.size() - 1;
        }
        state.findIndex = target;
        state.anchor = matches.get(target);
        state.cursor = state.anchor + state.findQuery.length();
        state.revealCaret = true;
    }

    private void newFile() {
        if (state.dirty && !confirmAction.equals("new")) {
            confirmAction = "new";
            confirmUntil = System.currentTimeMillis() + 3000L;
            return;
        }
        confirmAction = "";
        reset();
    }

    private void reset() {
        load(UNTITLED, "");
    }

    private void beginOpen() {
        dialog.openPicker("TEXT", "", tr("open.title"), state.dirty ? tr("open.unsaved") : "", state.path, path -> {
            state.pendingOpenPath = path;
            ComputerNetworking.openFile(path);
        });
    }

    private boolean fileExists(String path) {
        return FileListing.exists(path);
    }

    private void save() {
        if (!fileExists(state.path) || ComputerFileSystem.isProtected(state.path)) {
            beginSaveAs();
            return;
        }
        state.pendingContent = state.content;
        pending("save", state.path);
        ComputerNetworking.saveFile(state.path, state.content);
    }

    private void pending(String kind, String path) {
        state.pendingKind = kind;
        state.pendingPath = path;
        state.pendingSerial = ComputerFileSystemClientState.get().mutationSerial();
    }

    private String folder() {
        String parent = screen.parentDirectory(state.path);
        return parent.isBlank() || ComputerFileSystem.isProtected(parent) ? ComputerFileSystem.DOCUMENTS : parent;
    }

    private String destination(String name) {
        String parent = folder();
        return parent.equals("/") ? "/" + name : parent + "/" + name;
    }

    private String validate(String name) {
        if (name.isBlank()) return tr("error.blank");
        if (name.contains("/") || name.contains("\\") || name.startsWith(".")) return tr("error.invalid");
        if (name.toLowerCase(Locale.ROOT).endsWith(".antpaint")) return tr("error.extension");
        if (destination(name).length() > ComputerFileSystem.MAX_PATH_LENGTH) return tr("error.long");
        return "";
    }

    private void beginSaveAs() {
        dialog.openName(tr("save_as.title"), fileName(), input -> {
            String name = input.contains(".") ? input : input + ".txt";
            String error = validate(name);
            if (!error.isEmpty()) return error;
            String target = destination(name);
            if (fileExists(target)) {
                if (!target.equals(state.path)) return tr("error.exists");
                save();
                return "";
            }
            state.pendingContent = state.content;
            pending("save_as", target);
            ComputerNetworking.createFile(target, state.content);
            return "";
        });
    }

    private void beginRename() {
        if (!fileExists(state.path) || ComputerFileSystem.isProtected(state.path)) {
            notice(tr("notice.save_first"), AppKit.ERROR);
            return;
        }
        dialog.openName(tr("rename.title"), fileName(), input -> {
            String error = validate(input);
            if (!error.isEmpty()) return error;
            String target = destination(input);
            if (target.equals(state.path)) return "";
            if (fileExists(target)) return tr("error.exists");
            pending("rename", target);
            ComputerNetworking.moveFile(state.path, target);
            return "";
        });
    }

    private void deleteFile() {
        if (!fileExists(state.path) || ComputerFileSystem.isProtected(state.path)) return;
        pending("delete", state.path);
        ComputerNetworking.deleteFile(state.path);
    }

    private void notice(String text, int color) {
        notice = text;
        noticeColor = color;
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    private String tr(String key, Object... arguments) {
        return Component.translatable("computer.antos.text." + key, arguments).getString();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Snapshot(String content, int cursor, int anchor) { }

    static final class State {
        String path = UNTITLED;
        String content = "";
        String knownContent = "";
        String savedContent = "";
        int cursor;
        int anchor = -1;
        int preferredX = -1;
        int scroll;
        int maximumScroll;
        boolean revealCaret;
        boolean dirty;
        boolean focused;
        boolean deleteConfirm;
        boolean finding;
        boolean findFocused;
        String findQuery = "";
        int findIndex = -1;
        int openSerial = -1;
        String pendingOpenPath = "";
        String pendingKind = "";
        String pendingPath = "";
        String pendingContent = "";
        int pendingSerial;
        int lastEditKind = -1;
        long lastEditTime;
        final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
        final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    }
}
