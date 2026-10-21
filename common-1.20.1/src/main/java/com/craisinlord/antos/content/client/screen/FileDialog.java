package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;


final class FileDialog {
    private static final int ROW = 16;
    private static final int MAX_NAME = 48;

    private final ComputerApp app;
    private final AppKit kit;
    private Mode mode = Mode.NONE;
    private String fileType = "TEXT";
    private String extension = "";
    private String title = "";
    private String warning = "";
    private String currentPath = "";
    private String value = "";
    private int cursor;
    private String error = "";
    private int scroll;
    private int maximum;
    private int visibleRows = 1;
    private int selected = -1;
    private Consumer<String> onPick = path -> { };
    private Function<String, String> onConfirm = name -> "";
    private Predicate<String> folderAllowed = path -> true;
    private String message = "";
    private String confirmLabel = "";
    private Runnable onAccept = () -> { };

    FileDialog(ComputerApp app, AppKit kit) {
        this.app = app;
        this.kit = kit;
    }

    boolean active() {
        return mode != Mode.NONE;
    }

    void openPicker(String fileType, String extension, String title, String warning, String currentPath, Consumer<String> onPick) {
        this.mode = Mode.OPEN;
        this.fileType = fileType;
        this.extension = extension;
        this.title = title;
        this.warning = warning;
        this.currentPath = currentPath;
        this.onPick = onPick;
        this.scroll = 0;
        this.selected = -1;
        ComputerNetworking.listFiles();
    }

    void openName(String title, String initial, Function<String, String> onConfirm) {
        this.mode = Mode.NAME;
        this.title = title;
        this.value = initial;
        this.cursor = initial.length();
        this.error = "";
        this.onConfirm = onConfirm;
    }

    void openFolderPicker(String title, String currentPath, Predicate<String> allowed, Consumer<String> onPick) {
        this.mode = Mode.FOLDER;
        this.title = title;
        this.warning = "";
        this.currentPath = currentPath;
        this.folderAllowed = allowed;
        this.onPick = onPick;
        this.scroll = 0;
        this.selected = -1;
        ComputerNetworking.listFiles();
    }

    void openConfirm(String title, String message, String confirmLabel, Runnable onAccept) {
        this.mode = Mode.CONFIRM;
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onAccept = onAccept;
    }

    void close() {
        mode = Mode.NONE;
        error = "";
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        if (mode == Mode.NONE) return;
        kit.suppressHover(false);
        g.fill(x, y, x + w, y + h, Ui.alpha(Ui.BACKGROUND, 0xB0));
        kit.block(x, y, w, h);
        if (mode == Mode.OPEN || mode == Mode.FOLDER) renderPicker(g, x, y, w, h);
        else if (mode == Mode.CONFIRM) renderConfirm(g, x, y, w, h);
        else renderName(g, x, y, w, h);
    }

    private void renderPicker(GuiGraphics g, int x, int y, int w, int h) {
        int pw = Math.max(80, Math.min(w - 12, 300));
        int ph = Math.max(60, h - 12);
        int px = x + (w - pw) / 2;
        int py = y + (h - ph) / 2;
        g.fill(px, py, px + pw, py + ph, Ui.BACKGROUND);
        app.screen.box(g, px, py, px + pw, py + ph, Ui.BORDER_STRONG);
        g.drawString(app.font, Component.literal(app.screen.trimToWidth(title, pw - 34)), px + 8, py + 7, Ui.TEXT_STRONG, false);
        kit.button(g, px + pw - 20, py + 3, 16, 15, "X", false, true, this::close);
        int listTop = py + 21;
        if (!warning.isEmpty()) {
            g.drawString(app.font, Component.literal(app.screen.trimToWidth(warning, pw - 16)), px + 8, listTop, Ui.ERROR, false);
            listTop += 12;
        }
        g.fill(px + 6, listTop, px + pw - 6, listTop + 1, Ui.SURFACE);
        listTop += 4;
        int listBottom = py + ph - 16;
        List<String> files = mode == Mode.FOLDER ? folders() : files();
        visibleRows = Math.max(1, (listBottom - listTop) / ROW);
        maximum = Math.max(0, files.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maximum));
        if (selected >= files.size()) selected = files.size() - 1;
        if (files.isEmpty()) {
            g.drawString(app.font, Component.literal(tr(mode == Mode.FOLDER ? "no_folders" : "empty")), px + 8, listTop + 4, Ui.TEXT_STRONG, false);
            g.drawString(app.font, Component.literal(app.screen.trimToWidth(tr("empty_hint"), pw - 16)), px + 8, listTop + 16, Ui.TEXT_MUTED, false);
        }
        int rowWidth = pw - 18;
        for (int row = 0; row < visibleRows && scroll + row < files.size(); row++) {
            int index = scroll + row;
            String path = files.get(index);
            int rowY = listTop + row * ROW;
            if (mode == Mode.FOLDER) {
                renderFolderRow(g, path, index, px + 6, rowY, rowWidth);
                continue;
            }
            boolean hover = kit.hovered(px + 6, rowY, rowWidth, ROW - 1);
            boolean current = path.equals(currentPath);
            if (hover || index == selected) g.fill(px + 6, rowY, px + 6 + rowWidth, rowY + ROW - 1, Ui.HOVER);
            String name = path.substring(path.lastIndexOf('/') + 1);
            String folder = app.screen.parentDirectory(path);
            int folderWidth = Math.min(rowWidth / 2, app.font.width(folder));
            String shownFolder = app.screen.trimToWidth(folder, folderWidth);
            g.drawString(app.font, Component.literal(app.screen.trimToWidth(name, rowWidth - folderWidth - 14)), px + 10, rowY + 4,
                    current ? Ui.ACCENT : Ui.TEXT, false);
            g.drawString(app.font, Component.literal(shownFolder), px + 6 + rowWidth - 4 - app.font.width(shownFolder), rowY + 4, Ui.TEXT_MUTED, false);
            kit.hit(px + 6, rowY, rowWidth, ROW - 1, () -> pick(path));
        }
        kit.scrollbar(g, px + pw - 8, listTop, visibleRows * ROW, visibleRows, files.size(), scroll, maximum, value -> scroll = value);
        g.fill(px + 6, py + ph - 14, px + pw - 6, py + ph - 13, Ui.SURFACE);
        String footer = mode == Mode.FOLDER ? tr("folder_hint") : tr("count", files.size());
        g.drawString(app.font, Component.literal(app.screen.trimToWidth(footer, pw - 16)), px + 8, py + ph - 10, Ui.TEXT_MUTED, false);
    }

    private void renderFolderRow(GuiGraphics g, String path, int index, int x, int y, int width) {
        boolean allowed = folderAllowed.test(path);
        boolean hover = allowed && kit.hovered(x, y, width, ROW - 1);
        if (hover || index == selected) g.fill(x, y, x + width, y + ROW - 1, Ui.HOVER);
        int depth = path.equals("/") ? 0 : (int) path.chars().filter(character -> character == '/').count();
        int indent = 4 + Math.min(depth, 6) * 8;
        kit.icon(g, FileExplorerApp.icon("folder"), x + indent, y + 2, 12, allowed);
        String name = path.equals("/") ? tr("root") : path.substring(path.lastIndexOf('/') + 1);
        int color = !allowed ? Ui.TEXT_DISABLED : path.equals(currentPath) ? Ui.ACCENT : Ui.TEXT;
        g.drawString(app.font, Component.literal(app.screen.trimToWidth(name, width - indent - 22)), x + indent + 16, y + 4, color, false);
        if (allowed) kit.hit(x, y, width, ROW - 1, () -> pick(path));
    }

    private void renderConfirm(GuiGraphics g, int x, int y, int w, int h) {
        int pw = Math.max(80, Math.min(w - 12, 240));
        List<net.minecraft.util.FormattedCharSequence> lines = app.font.split(Component.literal(message), pw - 16);
        int ph = 52 + lines.size() * 11;
        int px = x + (w - pw) / 2;
        int py = y + Math.max(4, (h - ph) / 2);
        g.fill(px, py, px + pw, py + ph, Ui.BACKGROUND);
        app.screen.box(g, px, py, px + pw, py + ph, Ui.BORDER_STRONG);
        g.drawString(app.font, Component.literal(app.screen.trimToWidth(title, pw - 16)), px + 8, py + 7, Ui.ERROR, false);
        int lineY = py + 21;
        for (var line : lines) {
            g.drawString(app.font, line, px + 8, lineY, Ui.TEXT, false);
            lineY += 11;
        }
        int confirmWidth = Math.max(44, kit.labelWidth(confirmLabel));
        int cancelWidth = Math.max(44, kit.labelWidth(tr("cancel")));
        kit.button(g, px + pw - 8 - confirmWidth, py + ph - 24, confirmWidth, 18, confirmLabel, false, true, this::accept);
        kit.button(g, px + pw - 12 - confirmWidth - cancelWidth, py + ph - 24, cancelWidth, 18, tr("cancel"), false, true, this::close);
    }

    private void accept() {
        Runnable action = onAccept;
        close();
        action.run();
    }

    private List<String> folders() {
        List<FileListing.Line> lines = FileListing.lines();
        if (lines == folderSource) return folderCache;
        List<String> folders = new java.util.ArrayList<>();
        folders.add("/");
        lines.stream()
                .filter(line -> line.type().equals("DIRECTORY"))
                .map(FileListing.Line::path)
                .sorted(Comparator.comparing(path -> path.toLowerCase(Locale.ROOT)))
                .forEach(folders::add);
        folderSource = lines;
        folderCache = java.util.Collections.unmodifiableList(folders);
        return folderCache;
    }

    private List<FileListing.Line> folderSource;
    private List<String> folderCache = List.of();
    private List<FileListing.Line> fileSource;
    private String fileSourceType;
    private String fileSourceExtension;
    private List<String> fileCache = List.of();

    private void renderName(GuiGraphics g, int x, int y, int w, int h) {
        int pw = Math.max(80, Math.min(w - 12, 220));
        int ph = 76;
        int px = x + (w - pw) / 2;
        int py = y + Math.max(4, (h - ph) / 2);
        g.fill(px, py, px + pw, py + ph, Ui.BACKGROUND);
        app.screen.box(g, px, py, px + pw, py + ph, Ui.BORDER_STRONG);
        g.drawString(app.font, Component.literal(app.screen.trimToWidth(title, pw - 16)), px + 8, py + 7, Ui.TEXT_STRONG, false);
        kit.field(g, px + 8, py + 19, pw - 16, 18, value, cursor, true, tr("name_placeholder"));
        kit.hit(px + 8, py + 19, pw - 16, 18, this::placeCursor);
        String message = error.isEmpty() ? tr("name_hint") : error;
        g.drawString(app.font, Component.literal(app.screen.trimToWidth(message, pw - 16)), px + 8, py + 41, error.isEmpty() ? Ui.TEXT_MUTED : Ui.ERROR, false);
        int buttonWidth = Math.max(44, kit.labelWidth(tr("cancel")));
        kit.button(g, px + pw - 8 - buttonWidth, py + ph - 24, buttonWidth, 18, tr("ok"), false, true, this::confirm);
        kit.button(g, px + pw - 12 - buttonWidth * 2, py + ph - 24, buttonWidth, 18, tr("cancel"), false, true, this::close);
    }

    private void placeCursor() {
        cursor = value.length();
    }

    private List<String> files() {
        List<FileListing.Line> lines = FileListing.lines();
        if (lines == fileSource && java.util.Objects.equals(fileType, fileSourceType) && java.util.Objects.equals(extension, fileSourceExtension)) return fileCache;
        fileCache = lines.stream()
                .filter(line -> line.type().equals(fileType) && (extension.isEmpty() || line.path().toLowerCase(Locale.ROOT).endsWith(extension)))
                .map(FileListing.Line::path)
                .sorted(Comparator.comparing((String path) -> path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT)).thenComparing(path -> path))
                .toList();
        fileSource = lines;
        fileSourceType = fileType;
        fileSourceExtension = extension;
        return fileCache;
    }

    private void pick(String path) {
        close();
        onPick.accept(path);
    }

    private void confirm() {
        String result = onConfirm.apply(value.trim());
        if (result == null || result.isEmpty()) close();
        else error = result;
    }

    boolean scroll(double amount) {
        if (mode != Mode.OPEN && mode != Mode.FOLDER) return mode != Mode.NONE;
        scroll = Math.max(0, Math.min(maximum, scroll - (int) Math.signum(amount) * 2));
        return true;
    }

    boolean type(char codePoint) {
        if (mode != Mode.NAME) return mode != Mode.NONE;
        if (codePoint < 32 || codePoint == 127 || codePoint == '/' || codePoint == '\\' || value.length() >= MAX_NAME) return true;
        value = value.substring(0, cursor) + codePoint + value.substring(cursor);
        cursor++;
        error = "";
        return true;
    }

    boolean key(int keyCode, int modifiers) {
        if (mode == Mode.NONE) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (mode == Mode.CONFIRM) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) accept();
            return true;
        }
        if (mode == Mode.OPEN || mode == Mode.FOLDER) {
            List<String> files = mode == Mode.FOLDER ? folders() : files();
            if (keyCode == GLFW.GLFW_KEY_DOWN && !files.isEmpty()) selected = Math.min(files.size() - 1, selected + 1);
            else if (keyCode == GLFW.GLFW_KEY_UP && !files.isEmpty()) selected = Math.max(0, selected - 1);
            else if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && selected >= 0 && selected < files.size()
                    && (mode != Mode.FOLDER || folderAllowed.test(files.get(selected)))) pick(files.get(selected));
            if (selected >= 0) {
                if (selected < scroll) scroll = selected;
                if (selected >= scroll + visibleRows) scroll = selected - visibleRows + 1;
            }
            return true;
        }
        boolean control = app.screen.hasControl(modifiers);
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) confirm();
        else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cursor > 0) {
            value = value.substring(0, cursor - 1) + value.substring(cursor);
            cursor--;
            error = "";
        } else if (keyCode == GLFW.GLFW_KEY_DELETE && cursor < value.length()) {
            value = value.substring(0, cursor) + value.substring(cursor + 1);
            error = "";
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) cursor = Math.max(0, cursor - 1);
        else if (keyCode == GLFW.GLFW_KEY_RIGHT) cursor = Math.min(value.length(), cursor + 1);
        else if (keyCode == GLFW.GLFW_KEY_HOME) cursor = 0;
        else if (keyCode == GLFW.GLFW_KEY_END) cursor = value.length();
        else if (control && keyCode == GLFW.GLFW_KEY_A) cursor = value.length();
        else if (control && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null) for (char character : clip.toCharArray()) type(character);
        }
        return true;
    }

    private String tr(String key, Object... arguments) {
        return Component.translatable("computer.antos.file_dialog." + key, arguments).getString();
    }

    private enum Mode { NONE, OPEN, NAME, FOLDER, CONFIRM }
}
