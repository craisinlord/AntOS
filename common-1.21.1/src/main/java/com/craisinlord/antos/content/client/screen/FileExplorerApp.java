package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;

final class FileExplorerApp extends ComputerApp {
    final State state = new State();

    void render(GuiGraphics g, int x, int y, int w, int h) {
        var fileSystem = ComputerFileSystemClientState.get();
        List<String[]> rows = rows();
        int rowTop = y + 57;
        int rowBottom = y + h - 39;
        int visible = Math.max(1, (rowBottom - rowTop) / 14);
        int maximum = Math.max(0, rows.size() - visible);
        state.maximumScroll = maximum;
        state.scroll = Math.max(0, Math.min(maximum, state.scroll));
        g.drawString(font, Component.literal("FILE EXPLORER"), x, y, GREEN, false);
        drawBreadcrumbs(g, x, y + 12, w);
        String[] labels = {"NEW", "MOVE", "RENAME", "DELETE", "REFRESH"};
        int[] widths = {34, 36, 42, 40, 48};
        int actionX = x;
        boolean hasSelection = selectionMutable();
        for (int index = 0; index < labels.length; index++) {
            boolean enabled = index == 0 || index == 4 || hasSelection;
            drawButton(g, actionX, y + 25, widths[index], labels[index], enabled);
            actionX += widths[index] + 2;
        }
        g.fill(x, y + 43, x + w, y + 44, DARK_GREEN);
        g.drawString(font, Component.literal("TYPE  NAME"), x + 2, y + 47, GREEN, false);
        String sizeHeader = "SIZE";
        g.drawString(font, Component.literal(sizeHeader), x + w - font.width(sizeHeader) - 8, y + 47, GREEN, false);
        screen.drawScrollbar(g, x + w - 4, rowTop, Math.max(1, rowBottom - rowTop), visible, rows.size(), state.scroll, maximum);
        for (int index = state.scroll; index < rows.size() && index < state.scroll + visible; index++) {
            String[] fields = rows.get(index);
            int line = rowTop + (index - state.scroll) * 14;
            boolean selected = fields[1].equals(state.selected);
            boolean hover = screen.hovered(x, line - 2, w - 6, 13);
            if (selected || hover) g.fill(x, line - 2, x + w - 6, line + 11, selected ? 0xFF204820 : HOVER_FILL);
            String icon = fields[0].equals("DIRECTORY") ? "[D]" : fields[0].equals("TEXT") ? "[T]" : "[I]";
            String name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
            String size = screen.trimToWidth(fields[2], 32);
            int sizeWidth = font.width(size);
            g.drawString(font, Component.literal(icon), x + 2, line, fields[0].equals("DIRECTORY") ? GREEN : PALE_GREEN, false);
            g.drawString(font, Component.literal(screen.trimToWidth(name, Math.max(12, w - sizeWidth - 48))), x + 26, line,
                    selected ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal(size), x + w - sizeWidth - 8, line, PALE_GREEN, false);
        }
        if (rows.isEmpty()) {
            String message = !fileSystem.success() && fileSystem.error().isBlank() ? "SYNCING FILES..."
                    : !fileSystem.error().isBlank() ? "COULD NOT LOAD // " + fileSystem.error() : "THIS FOLDER IS EMPTY";
            g.drawString(font, Component.literal(screen.trimToWidth(message, w - 12)), x + 3, rowTop + 4, PALE_GREEN, false);
            if (fileSystem.success() && fileSystem.error().isBlank()) g.drawString(font, Component.literal("USE NEW TO ADD A FOLDER"), x + 3, rowTop + 17, GREEN, false);
        }
        g.fill(x, y + h - 34, x + w, y + h - 33, DARK_GREEN);
        String selectedDetails = selectedDetails(rows);
        g.drawString(font, Component.literal(screen.trimToWidth(selectedDetails, w - 4)), x, y + h - 28, PALE_GREEN, false);
        String footer = footer(fileSystem);
        g.drawString(font, Component.literal(screen.trimToWidth(footer, w - 4)), x, y + h - 15, GREEN, false);
    }

    private List<String[]> rows() {
        return ComputerFileSystemClientState.get().files().stream().map(file -> file.split("\\t", 3))
                .filter(fields -> fields.length >= 3 && isDirectChild(fields[1], state.directory))
                .sorted((left, right) -> {
                    int directoryOrder = Boolean.compare(!left[0].equals("DIRECTORY"), !right[0].equals("DIRECTORY"));
                    if (directoryOrder != 0) return directoryOrder;
                    String leftName = left[1].substring(left[1].lastIndexOf('/') + 1);
                    String rightName = right[1].substring(right[1].lastIndexOf('/') + 1);
                    return String.CASE_INSENSITIVE_ORDER.compare(leftName, rightName);
                }).toList();
    }

    private void drawBreadcrumbs(GuiGraphics g, int x, int y, int w) {
        List<String> paths = visibleBreadcrumbPaths(w);
        int cursor = x;
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            String label = path.isEmpty() ? "..." : index == 0 ? "/" : path.substring(path.lastIndexOf('/') + 1);
            String segment = path.isEmpty() ? "..." : index == 0 ? "[/]" : screen.trimToWidth(label, Math.max(10, w / 3));
            if (screen.hovered(cursor - 1, y - 2, font.width(segment) + 3, 12)) screen.drawHover(g, cursor - 1, y - 2, font.width(segment) + 3, 12);
            g.drawString(font, Component.literal(segment), cursor, y, index == paths.size() - 1 ? PALE_GREEN : GREEN, false);
            cursor += font.width(segment);
            if (index + 1 < paths.size()) {
                g.drawString(font, Component.literal(" > "), cursor, y, DARK_GREEN, false);
                cursor += font.width(" > ");
            }
        }
    }

    private List<String> visibleBreadcrumbPaths(int width) {
        List<String> paths = breadcrumbPaths();
        int totalWidth = 0;
        for (String path : paths) {
            String label = path.equals("/") ? "[/]" : path.substring(path.lastIndexOf('/') + 1);
            totalWidth += font.width(label) + font.width(" > ");
        }
        if (totalWidth <= width) return paths;
        if (paths.size() > 2) return List.of("/", "", paths.get(paths.size() - 2), paths.get(paths.size() - 1));
        return List.of("/", "", paths.get(paths.size() - 1));
    }

    private List<String> breadcrumbPaths() {
        List<String> paths = new ArrayList<>(List.of("/"));
        String current = "";
        for (String part : state.directory.split("/")) {
            if (part.isBlank()) continue;
            current += "/" + part;
            paths.add(current);
        }
        return paths;
    }

    private String breadcrumbAt(double mouseX, double mouseY, int x, int y) {
        List<String> paths = visibleBreadcrumbPaths(screen.windowWidth(screen.activeWindow) - 16);
        int cursor = x;
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            if (path.isEmpty()) {
                cursor += font.width("...") + font.width(" > ");
                continue;
            }
            String label = index == 0 ? "[/]" : screen.trimToWidth(path.substring(path.lastIndexOf('/') + 1), Math.max(10, (screen.windowWidth(screen.activeWindow) - 16) / 3));
            String segment = label;
            int segmentWidth = font.width(segment);
            if (screen.inside(cursor - 1, y - 2, segmentWidth + 3, 12, mouseX, mouseY)) return paths.get(index);
            cursor += segmentWidth;
            if (index + 1 < paths.size()) cursor += font.width(" > ");
        }
        return null;
    }

    private int buttonAt(int x) {
        int[] widths = {34, 36, 42, 40, 48};
        int cursor = 0;
        for (int index = 0; index < widths.length; index++) {
            if (x >= cursor && x < cursor + widths[index]) return index;
            cursor += widths[index] + 2;
        }
        return -1;
    }

    private void drawButton(GuiGraphics g, int x, int y, int width, String label, boolean enabled) {
        boolean hover = enabled && screen.hovered(x, y, width, 14);
        g.fill(x, y, x + width, y + 14, hover ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, y, x + width, y + 14, enabled ? hover ? PALE_GREEN : GREEN : 0xFF315531);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + 3, enabled ? PALE_GREEN : 0xFF4A754A);
    }

    private String selectedDetails(List<String[]> rows) {
        for (String[] fields : rows) {
            if (fields[1].equals(state.selected)) {
                String name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
                return fields[0] + " // " + name + " // " + fields[2];
            }
        }
        return state.directory.equals("/") ? "ROOT DIRECTORY" : state.directory;
    }

    private boolean selectionMutable() {
        return !state.selected.isBlank() && !state.selected.equals("/")
                && !ComputerFileSystem.isProtected(state.selected)
                && ComputerFileSystemClientState.get().files().stream().anyMatch(file -> {
                    String[] fields = file.split("\\t", 3);
                    return fields.length >= 2 && fields[1].equals(state.selected);
                });
    }

    private String footer(ComputerFileSystemClientState.State fileSystem) {
        if (state.deleteConfirm) return "DELETE " + state.selected + "? ENTER / ESC";
        if (state.creatingDirectory) return "FOLDER: " + state.rename + "_  ENTER / ESC";
        if (state.moving) return "MOVE TO: " + state.rename + "_  ENTER / ESC";
        if (state.renaming) return "RENAME: " + state.rename + "_  ENTER / ESC";
        if (fileSystem.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_DELETE
                || fileSystem.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE
                || fileSystem.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE) {
            String action = switch (fileSystem.lastMutationAction()) {
                case com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_DELETE -> "DELETED";
                case com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE -> "MOVED";
                default -> "CREATED";
            };
            return fileSystem.lastMutationSuccess() ? action + " // " + fileSystem.lastMutationPath()
                    : "ERROR // " + fileSystem.lastMutationError();
        }
        return "CTRL+N NEW  CTRL+M MOVE  CTRL+R RENAME  DEL DELETE";
    }

    private boolean isDirectChild(String path, String directory) {
        if (path.equals(directory)) return false;
        String prefix = directory.equals("/") ? "/" : directory + "/";
        if (!path.startsWith(prefix)) return false;
        return path.indexOf('/', prefix.length()) < 0;
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        if (state.deleteConfirm || state.creatingDirectory
                || state.moving || state.renaming) return true;
        if (mouseY >= contentY + 12 && mouseY < contentY + 24) {
            String breadcrumb = breadcrumbAt(mouseX, mouseY, contentX, contentY + 12);
            if (breadcrumb != null) {
                state.directory = breadcrumb;
                state.selected = "";
                state.scroll = 0;
            }
            return true;
        }
        if (mouseY >= contentY + 25 && mouseY < contentY + 39) {
            int buttonIndex = buttonAt((int) (mouseX - contentX));
            if (buttonIndex == 0) {
                beginCreateDirectory();
            } else if (buttonIndex == 1 && selectionMutable()) {
                beginMoveSelection();
            } else if (buttonIndex == 2 && selectionMutable()) {
                beginRenameSelectedFile();
            } else if (buttonIndex == 3 && selectionMutable()) {
                state.creatingDirectory = false;
                state.moving = false;
                state.renaming = false;
                state.deleteConfirm = true;
            } else if (buttonIndex == 4) {
                state.scroll = 0;
                ComputerNetworking.listFiles();
            }
            return true;
        }
        List<String[]> rows = rows();
        int rowTop = contentY + 57;
        int rowBottom = contentY + contentH - 39;
        int visible = Math.max(1, (rowBottom - rowTop) / 14);
        if (mouseX >= contentX + contentW - 6 && mouseY >= rowTop && mouseY < rowBottom) {
            int maximum = Math.max(0, rows.size() - visible);
            int offset = Math.max(0, Math.min(rowBottom - rowTop, (int) mouseY - rowTop));
            state.scroll = maximum == 0 ? 0 : (int) Math.round((double) offset / Math.max(1, rowBottom - rowTop) * maximum);
            return true;
        }
        for (int rowIndex = state.scroll; rowIndex < rows.size() && rowIndex < state.scroll + visible; rowIndex++) {
            String[] fields = rows.get(rowIndex);
            int rowY = rowTop + (rowIndex - state.scroll) * 14;
            if (!screen.inside(contentX, rowY - 2, contentW - 6, 14, mouseX, mouseY)) continue;
            long now = System.currentTimeMillis();
            boolean activate = fields[1].equals(state.lastClickedPath)
                    && now - state.lastClickTime <= 350L;
            state.selected = fields[1];
            state.lastClickedPath = fields[1];
            state.lastClickTime = now;
            if (fields[0].equals("DIRECTORY")) {
                if (activate) {
                    state.directory = fields[1];
                    state.selected = "";
                    state.scroll = 0;
                    state.lastClickedPath = "";
                }
            }
            else if (activate && (fields[0].equals("TEXT") || fields[0].equals("IMAGE"))) {
                ComputerNetworking.openFile(fields[1]);
                screen.open(fields[1].endsWith(".antpaint") ? "PAINT" : "TEXT");
                state.lastClickedPath = "";
            }
            break;
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        state.scroll = Math.max(0, Math.min(state.maximumScroll,
                state.scroll - (int) Math.signum(scrollY)));
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (state.renaming && codePoint >= 32 && codePoint != '/' && codePoint != '\\') {
            if (state.rename.length() < 48) state.rename += codePoint;
            return true;
        }
        if (state.creatingDirectory && codePoint >= 32 && codePoint != '/' && codePoint != '\\') {
            if (state.rename.length() < 48) state.rename += codePoint;
            return true;
        }
        if (state.moving && codePoint >= 32 && codePoint != '\\') {
            if (state.rename.length() < 64) state.rename += codePoint;
            return true;
        }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (state.deleteConfirm) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.deleteConfirm = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { state.deleteConfirm = false; deleteSelectedFile(); return true; }
            return true;
        }
        if (state.creatingDirectory) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!state.rename.isEmpty()) state.rename = state.rename.substring(0, state.rename.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.creatingDirectory = false; state.rename = ""; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!state.rename.isBlank()) {
                    String folder = state.rename.replace("\\", "\\\\").replace("\"", "\\\"");
                    ComputerNetworking.terminalCommand(state.directory, "mkdir \"" + folder + "\"");
                }
                state.creatingDirectory = false;
                state.rename = "";
                ComputerNetworking.listFiles();
                return true;
            }
        }
        if (state.moving) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!state.rename.isEmpty()) state.rename = state.rename.substring(0, state.rename.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.moving = false; state.rename = ""; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!state.rename.isBlank() && !state.selected.isBlank()) {
                    String destination = state.rename.startsWith("/")
                            ? ComputerFileSystem.normalize(state.rename)
                            : ComputerFileSystem.normalize(state.directory + "/" + state.rename);
                    ComputerNetworking.moveFile(state.selected, destination);
                }
                state.moving = false;
                state.rename = "";
                ComputerNetworking.listFiles();
                return true;
            }
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
                renameSelectedFile();
                return true;
            }
        }
        if (state.creatingDirectory || state.moving || state.renaming) return true;
        if (keyCode == GLFW.GLFW_KEY_R && screen.hasControl(modifiers)) {
            beginRenameSelectedFile();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE && selectionMutable()) {
            state.creatingDirectory = false;
            state.moving = false;
            state.renaming = false;
            state.deleteConfirm = true;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_N && screen.hasControl(modifiers)) {
            beginCreateDirectory();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_M && screen.hasControl(modifiers) && selectionMutable()) {
            beginMoveSelection();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_F5) {
            state.scroll = 0;
            ComputerNetworking.listFiles();
            return true;
        }
        return false;
    }

    private void beginRenameSelectedFile() {
        if (state.selected.isEmpty() || state.selected.equals("/") || ComputerFileSystem.isProtected(state.selected)) return;
        state.deleteConfirm = false;
        state.creatingDirectory = false;
        state.moving = false;
        int slash = state.selected.lastIndexOf('/');
        state.rename = state.selected.substring(slash + 1);
        state.renaming = true;
    }

    private void beginCreateDirectory() {
        state.deleteConfirm = false;
        state.creatingDirectory = true;
        state.moving = false;
        state.renaming = false;
        state.rename = "";
    }

    private void beginMoveSelection() {
        if (!selectionMutable()) return;
        state.deleteConfirm = false;
        state.moving = true;
        state.creatingDirectory = false;
        state.renaming = false;
        state.rename = "";
    }

    private void renameSelectedFile() {
        if (!state.rename.isBlank() && !state.rename.contains("/")) {
            String parent = screen.parentDirectory(state.selected);
            String destination = parent.equals("/") ? "/" + state.rename : parent + "/" + state.rename;
            ComputerNetworking.moveFile(state.selected, destination);
            state.selected = destination;
            ComputerNetworking.listFiles();
        }
        state.renaming = false;
        state.rename = "";
    }

    private void deleteSelectedFile() {
        if (state.selected.isEmpty() || state.selected.equals("/") || ComputerFileSystem.isProtected(state.selected)) return;
        ComputerNetworking.deleteFile(state.selected);
        state.selected = "";
        ComputerNetworking.listFiles();
    }

    static final class State {
        int scroll;
        int maximumScroll;
        String lastClickedPath = "";
        long lastClickTime;
        boolean deleteConfirm;
        boolean creatingDirectory;
        boolean moving;
        String directory = "/";
        String selected = "";
        String rename = "";
        boolean renaming;
    }
}
