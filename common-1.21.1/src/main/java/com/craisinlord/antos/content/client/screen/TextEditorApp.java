package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;

final class TextEditorApp extends ComputerApp {
    final State state = new State();

    void render(GuiGraphics g, int x, int y, int w, int h) {
        var fileState = ComputerFileSystemClientState.get();
        if (state.dirty
                && fileState.lastMutationSuccess()
                && (fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE
                || fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_SAVE)
                && fileState.lastMutationPath().equals(state.path)) {
            state.dirty = false;
        }
        if (!state.saveAsPendingPath.isBlank()
                && fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE
                && fileState.lastMutationPath().equals(state.saveAsPendingPath)) {
            if (fileState.lastMutationSuccess()) {
                state.path = state.saveAsPendingPath;
                state.dirty = false;
                state.justSavedAs = true;
            }
            state.saveAsPendingPath = "";
        }
        if (!state.movePendingPath.isBlank()
                && fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE
                && fileState.lastMutationPath().equals(state.movePendingPath)) {
            if (fileState.lastMutationSuccess()) {
                state.path = state.movePendingPath;
                state.justSavedAs = true;
            }
            state.movePendingPath = "";
        }
        if (!fileState.openedPath().isEmpty() && !state.dirty && !state.justSavedAs) {
            state.path = fileState.openedPath();
            state.content = fileState.openedContents();
            state.cursor = state.content.length();
        }
        state.justSavedAs = false;
        String editorTitle = state.renaming ? "RENAME // " + state.rename + "_"
                : state.path.substring(state.path.lastIndexOf('/') + 1) + (state.dirty ? " *" : "");
        g.drawString(font, Component.literal(screen.trimToWidth(editorTitle, Math.max(1, w - 92))), x, y, GREEN, false);
        if (fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE
                || fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_SAVE
                || fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE) {
            String status = state.dirty ? "UNSAVED" : fileState.lastMutationSuccess() ? "SAVED" : "ERROR";
            g.drawString(font, Component.literal(status), x + w - font.width(status), y, fileState.lastMutationSuccess() ? PALE_GREEN : GREEN, false);
        }
        String[] toolbarLabels = {"NEW", "OPEN", "WRITE", "SAVE", "SAVE AS"};
        int[] toolbarX = {0, 36, 76, 116, 152};
        int[] toolbarWidths = {34, 38, 38, 34, 62};
        for (int i = 0; i < toolbarLabels.length; i++) {
            drawButton(g, x + toolbarX[i], y + 14, Math.min(toolbarWidths[i], Math.max(0, w - toolbarX[i])), 20, toolbarLabels[i]);
        }
        g.fill(x + 4, y + 38, x + w - 4, y + h - 4, BLACK);
        screen.box(g, x + 4, y + 38, x + w - 4, y + h - 4, state.focused ? PALE_GREEN : DARK_GREEN);
        if (state.listing) {
            g.drawString(font, Component.literal("OPEN A TEXT FILE"), x + 10, y + 45, GREEN, false);
            List<String> textFiles = fileState.files().stream().filter(file -> {
                String[] fields = file.split("\\t", 3);
                return fields.length >= 3 && fields[0].equals("TEXT");
            }).toList();
            int visibleFiles = Math.max(1, (h - 112) / 14);
            int maximum = Math.max(0, textFiles.size() - visibleFiles);
            state.pickerMaximumScroll = maximum;
            state.pickerScroll = Math.max(0, Math.min(state.pickerScroll, maximum));
            screen.drawScrollbar(g, x + w - 8, y + 61, Math.max(1, h - 111), visibleFiles, textFiles.size(), state.pickerScroll, maximum);
            int shown = 0;
            for (int index = state.pickerScroll; index < textFiles.size() && shown < visibleFiles; index++, shown++) {
                String file = textFiles.get(index);
                String[] fields = file.split("\\t", 3);
                int rowY = y + 61 + shown * 14;
                if (screen.hovered(x + 8, rowY - 2, w - 20, 12)) g.fill(x + 8, rowY - 2, x + w - 12, rowY + 10, HOVER_FILL);
                String name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
                g.drawString(font, Component.literal(screen.trimToWidth(name, w - 34)), x + 10, rowY, PALE_GREEN, false);
            }
            if (textFiles.isEmpty()) g.drawString(font, Component.literal("NO TEXT FILES FOUND"), x + 10, y + 61, PALE_GREEN, false);
        } else {
            String content = state.content.isEmpty() ? "TYPE HERE..." : state.content;
            if (state.focused && screen.caretVisible()) content = content.substring(0, Math.min(state.cursor, content.length())) + "|"
                    + content.substring(Math.min(state.cursor, content.length()));
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(content), w - 32);
            int maxLines = Math.max(1, (h - 92) / 11);
            int maximum = Math.max(0, lines.size() - maxLines);
            state.maximumScroll = maximum;
            state.scroll = Math.max(0, Math.min(state.scroll, maximum));
            screen.drawScrollbar(g, x + w - 8, y + 44, Math.max(1, h - 88), maxLines, lines.size(), state.scroll, maximum);
            for (int i = state.scroll; i < lines.size() && i < state.scroll + maxLines; i++) {
                g.drawString(font, lines.get(i), x + 10, y + 48 + (i - state.scroll) * 11, state.focused ? PALE_GREEN : GREEN, false);
            }
        }
        if (state.deleteConfirm) {
            g.drawString(font, Component.literal("DELETE CURRENT FILE? [ ENTER / ESC ]"), x, y + h - 28, PALE_GREEN, false);
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int width, int height, String label) {
        if (width <= 0) return;
        boolean hover = screen.hovered(x, y, width, height);
        g.fill(x, y, x + width, y + height, hover ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, y, x + width, y + height, hover ? PALE_GREEN : GREEN);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + 6, hover ? GREEN : PALE_GREEN);
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        if (screen.inside(contentX + contentW - 9, contentY + (state.listing ? 61 : 44), 5,
                Math.max(1, contentH - (state.listing ? 111 : 88)), mouseX, mouseY)) {
            int trackTop = contentY + (state.listing ? 61 : 44);
            int trackHeight = Math.max(1, contentH - (state.listing ? 111 : 88));
            int maximum = state.listing ? state.pickerMaximumScroll : state.maximumScroll;
            int value = (int) Math.round(Math.max(0.0, Math.min(1.0, (mouseY - trackTop) / trackHeight)) * maximum);
            if (state.listing) state.pickerScroll = value;
            else state.scroll = value;
            return true;
        }
        if (screen.inside(contentX, contentY + 14, contentW, 20, mouseX, mouseY)) {
            int toolbarX = (int) mouseX - contentX;
            if (toolbarX < 34) newFile();
            else if (toolbarX >= 36 && toolbarX < 74) { state.listing = true; state.pickerScroll = 0; ComputerNetworking.listFiles(); }
            else if (toolbarX >= 76 && toolbarX < 114) write();
            else if (toolbarX >= 116 && toolbarX < 150) save();
            else if (toolbarX >= 152 && toolbarX < 214) beginSaveAs();
        } else if (state.listing) {
            var files = ComputerFileSystemClientState.get().files();
            int index = state.pickerScroll + ((int) mouseY - (contentY + 61)) / 14;
            if (mouseY < contentY + 61 || mouseY >= contentY + contentH - 50) return true;
            int textIndex = 0;
            for (String file : files) {
                String[] fields = file.split("\\t", 3);
                if (fields.length < 3 || !fields[0].equals("TEXT")) continue;
                if (textIndex++ == index) {
                    state.listing = false;
                    ComputerNetworking.openFile(fields[1]);
                    break;
                }
            }
        } else {
            state.focused = true;
            setCursorFromMouse(mouseX - (contentX + 10), mouseY - (contentY + 48), contentW - 32);
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (state.listing) {
            state.pickerScroll = Math.max(0, Math.min(state.pickerMaximumScroll,
                    state.pickerScroll - (int) Math.signum(scrollY)));
            return true;
        }
        state.scroll = Math.max(0, Math.min(state.maximumScroll,
                state.scroll - (int) Math.signum(scrollY)));
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (state.renaming && codePoint >= 32 && codePoint != '/' && codePoint != '\\') {
            if (state.rename.length() < 48) state.rename += codePoint;
            return true;
        }
        if (state.focused && codePoint >= 32) {
            replaceSelection(String.valueOf(codePoint));
            state.dirty = true;
            return true;
        }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (state.focused) {
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_A) { state.selectionStart = 0; state.cursor = state.content.length(); return true; }
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_C) { if (hasSelection()) Minecraft.getInstance().keyboardHandler.setClipboard(selectedText()); return true; }
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_X) { if (hasSelection()) { Minecraft.getInstance().keyboardHandler.setClipboard(selectedText()); replaceSelection(""); state.dirty = true; } return true; }
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_V) { String clip = Minecraft.getInstance().keyboardHandler.getClipboard(); if (clip != null && !clip.isEmpty()) { replaceSelection(clip.replace("\r", "")); state.dirty = true; } return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (hasSelection()) { replaceSelection(""); state.dirty = true; } else if (state.cursor > 0) { state.content = state.content.substring(0, state.cursor - 1) + state.content.substring(state.cursor); state.cursor--; state.dirty = true; } return true; }
            if (keyCode == GLFW.GLFW_KEY_DELETE && !screen.hasControl(modifiers)) { if (hasSelection()) { replaceSelection(""); state.dirty = true; } else if (state.cursor < state.content.length()) { state.content = state.content.substring(0, state.cursor) + state.content.substring(state.cursor + 1); state.dirty = true; } return true; }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { state.cursor = Math.max(0, state.cursor - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { state.cursor = Math.min(state.content.length(), state.cursor + 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) { moveCursorVertically(keyCode == GLFW.GLFW_KEY_UP); return true; }
            if (keyCode == GLFW.GLFW_KEY_HOME) { state.cursor = state.content.lastIndexOf('\n', Math.max(0, state.cursor - 1)) + 1; return true; }
            if (keyCode == GLFW.GLFW_KEY_END) { int next = state.content.indexOf('\n', state.cursor); state.cursor = next < 0 ? state.content.length() : next; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER) { replaceSelection("\n"); state.dirty = true; return true; }
            if (keyCode == GLFW.GLFW_KEY_S && screen.hasControl(modifiers) && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) { save(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.focused = false; return true; }
        }
        if (state.deleteConfirm) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.deleteConfirm = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { state.deleteConfirm = false; deleteFile(); return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && screen.hasControl(modifiers)) { state.deleteConfirm = true; state.focused = false; return true; }
        if (keyCode == GLFW.GLFW_KEY_S && screen.hasControl(modifiers) && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            beginSaveAs();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R && screen.hasControl(modifiers) && !state.path.isBlank()) {
            int slash = state.path.lastIndexOf('/');
            state.rename = state.path.substring(slash + 1);
            state.renaming = true;
            return true;
        }
        if (state.renaming) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!state.rename.isEmpty()) state.rename = state.rename.substring(0, state.rename.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.renaming = false;
                state.rename = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                renameFile();
                return true;
            }
        }
        return false;
    }

    private boolean hasSelection() { return state.selectionStart >= 0 && state.selectionStart != state.cursor; }

    private String selectedText() { int start = Math.min(state.selectionStart, state.cursor); int end = Math.max(state.selectionStart, state.cursor); return state.content.substring(start, end); }

    private void replaceSelection(String text) {
        int start = hasSelection() ? Math.min(state.selectionStart, state.cursor) : state.cursor;
        int end = hasSelection() ? Math.max(state.selectionStart, state.cursor) : state.cursor;
        state.content = state.content.substring(0, start) + text + state.content.substring(end);
        state.cursor = start + text.length();
        state.selectionStart = -1;
    }

    private void newFile() {
        state.path = "/documents/untitled.txt";
        state.content = "";
        state.cursor = 0;
        state.scroll = 0;
        state.dirty = true;
        state.listing = false;
        state.focused = true;
    }

    private void beginSaveAs() {
        int slash = state.path.lastIndexOf('/');
        state.rename = state.path.substring(slash + 1);
        state.saveAs = true;
        state.renaming = true;
        state.focused = false;
    }

    private void setCursorFromMouse(double mouseX, double mouseY, int width) {
        if (state.content.isEmpty()) { state.cursor = 0; return; }
        StringBuilder line = new StringBuilder();
        int cursor = 0;
        int targetLine = state.scroll + Math.max(0, (int) (mouseY / 11));
        int currentLine = 0;
        for (int index = 0; index < state.content.length(); index++) {
            char character = state.content.charAt(index);
            if (character == '\n' || font.width(line.toString() + character) > width) {
                if (currentLine == targetLine) {
                    state.cursor = index;
                    return;
                }
                currentLine++;
                line.setLength(0);
                if (character == '\n') continue;
            }
            line.append(character);
            cursor = index + 1;
        }
        state.cursor = cursor;
    }

    private void moveCursorVertically(boolean up) {
        int lineStart = state.content.lastIndexOf('\n', Math.max(0, state.cursor - 1)) + 1;
        int column = state.cursor - lineStart;
        int target = up ? lineStart - 1 : state.content.indexOf('\n', state.cursor);
        if (target < 0) return;
        int targetStart = up ? state.content.lastIndexOf('\n', target - 1) + 1 : target + 1;
        int targetEnd = state.content.indexOf('\n', targetStart);
        if (targetEnd < 0) targetEnd = state.content.length();
        state.cursor = Math.min(targetStart + column, targetEnd);
    }

    private void write() {
        boolean exists = ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + state.path + "\t"));
        if (exists) ComputerNetworking.saveFile(state.path, state.content);
        else ComputerNetworking.createFile(state.path, state.content);
        ComputerNetworking.listFiles();
    }

    private void save() {
        boolean exists = ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + state.path + "\t"));
        if (exists) ComputerNetworking.saveFile(state.path, state.content);
        else ComputerNetworking.createFile(state.path, state.content);
        ComputerNetworking.listFiles();
    }

    private void renameFile() {
        if (!state.rename.isBlank() && !state.rename.contains("/") && !ComputerFileSystem.isProtected(state.path)) {
            String parent = screen.parentDirectory(state.path);
            String destination = parent.equals("/") ? "/" + state.rename : parent + "/" + state.rename;
            if (state.saveAs) {
                state.saveAsPendingPath = destination;
                ComputerNetworking.createFile(destination, state.content);
            } else {
                ComputerNetworking.moveFile(state.path, destination);
                state.movePendingPath = destination;
            }
            ComputerNetworking.listFiles();
        }
        state.saveAs = false;
        state.renaming = false;
        state.rename = "";
    }

    private void deleteFile() {
        if (state.path.isBlank() || ComputerFileSystem.isProtected(state.path)) return;
        ComputerNetworking.deleteFile(state.path);
        state.path = "/documents/untitled.txt";
        state.content = "";
        state.cursor = 0;
        state.dirty = false;
        ComputerNetworking.listFiles();
    }

    static final class State {
        String path = "/documents/untitled.txt";
        String content = "";
        int cursor;
        int selectionStart = -1;
        int scroll;
        int maximumScroll;
        boolean dirty;
        boolean focused;
        boolean listing;
        int pickerScroll;
        int pickerMaximumScroll;
        boolean renaming;
        boolean saveAs;
        String saveAsPendingPath = "";
        String movePendingPath = "";
        boolean justSavedAs;
        boolean deleteConfirm;
        String rename = "";
    }
}
