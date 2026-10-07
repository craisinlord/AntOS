package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.computer.paint.AntPaintHistory;
import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.computer.paint.AntPaintFile;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.computer.paint.AntPaintTool;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.HEIGHT;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.WIDTH;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;

final class PaintApp extends ComputerApp {
    final State state = new State();

    boolean paintStrokeActive;

    int paintStrokeButton = -1;

    int lastPaintPixelX = -1;

    int lastPaintPixelY = -1;

    void drag(Window window, double mouseX, double mouseY, int button) {
        if (!paintStrokeActive || button != paintStrokeButton) return;
        int x = -WIDTH / 2 + ComputerScreen.windowLocalX(window) + 8;
        int y = -HEIGHT / 2 + ComputerScreen.windowLocalY(window) + 58;
        paintAt(mouseX, mouseY, x, y, window.renderWidth - 16, Math.max(1, window.renderHeight - 66));
    }

    void release(int button) {
        if (button != paintStrokeButton) return;
        paintStrokeActive = false;
        paintStrokeButton = -1;
        lastPaintPixelX = -1;
        lastPaintPixelY = -1;
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        var fileState = ComputerFileSystemClientState.get();
        if (!state.dirty && fileState.openedPath().endsWith(".antpaint")
                && (!fileState.openedPath().equals(state.loadedPath)
                || !fileState.openedContents().equals(state.loadedContents)) && !fileState.openedContents().isEmpty()) {
            try {
                AntPaintFile file = AntPaintFileCodec.decode(Base64.getDecoder().decode(fileState.openedContents()));
                state.name = file.filename();
                screen.session.antmail.state.paintPath = fileState.openedPath();
                state.canvas = file.canvas().copy();
                state.loadedPath = fileState.openedPath();
                state.loadedContents = fileState.openedContents();
            } catch (IllegalArgumentException ignored) {
            }
        }
        g.drawString(font, Component.literal(screen.trimToWidth("ANTPAINT // " + state.name + (state.dirty ? " *" : ""), w)), x, y, GREEN, false);
        renderToolbar(g, x, y);
        int canvasHeight = Math.max(1, h - 32);
        int size = Math.max(1, Math.min(w / AntPaintCanvas.WIDTH, canvasHeight / AntPaintCanvas.HEIGHT));
        int cx = x + (w - size * AntPaintCanvas.WIDTH) / 2;
        int cy = y + 30 + Math.max(0, (canvasHeight - size * AntPaintCanvas.HEIGHT) / 2);
        g.fill(cx - 1, cy - 1, cx + size * AntPaintCanvas.WIDTH + 1, cy + size * AntPaintCanvas.HEIGHT + 1, BLACK);
        g.fill(cx, cy, cx + size * AntPaintCanvas.WIDTH, cy + size * AntPaintCanvas.HEIGHT, BLACK);
        for (int py = 0; py < AntPaintCanvas.HEIGHT; py++) for (int px = 0; px < AntPaintCanvas.WIDTH; px++) if (state.canvas.get(px, py)) g.fill(cx + px * size, cy + py * size, cx + (px + 1) * size, cy + (py + 1) * size, GREEN);
        int hoverX = (screen.session.mouseX - cx) / size;
        int hoverY = (screen.session.mouseY - cy) / size;
        if (screen.caretVisible() && screen.session.mouseX >= cx && screen.session.mouseY >= cy && hoverX >= 0 && hoverX < AntPaintCanvas.WIDTH && hoverY >= 0 && hoverY < AntPaintCanvas.HEIGHT) {
            int pixelX = cx + hoverX * size;
            int pixelY = cy + hoverY * size;
            g.fill(pixelX, pixelY, pixelX + size, pixelY + 1, PALE_GREEN);
            g.fill(pixelX, pixelY + size - 1, pixelX + size, pixelY + size, PALE_GREEN);
            g.fill(pixelX, pixelY, pixelX + 1, pixelY + size, PALE_GREEN);
            g.fill(pixelX + size - 1, pixelY, pixelX + size, pixelY + size, PALE_GREEN);
        }
    }

    private void renderToolbar(GuiGraphics g, int x, int y) {
        for (int index = 0; index < 7; index++) {
            int buttonX = x + index * 28;
            boolean hover = screen.hovered(buttonX, y + 11, 22, 17);
            if (hover) screen.drawHover(g, buttonX, y + 11, 22, 17);
            if (index < 3 && ((index == 0 && state.tool == AntPaintTool.PENCIL)
                    || (index == 1 && state.tool == AntPaintTool.ERASER)
                    || (index == 2 && state.tool == AntPaintTool.FILL))) {
                screen.box(g, buttonX, y + 11, buttonX + 22, y + 28, PALE_GREEN);
            }
            toolbarIcon(g, index, buttonX + 11, y + 19);
        }
    }

    private void toolbarIcon(GuiGraphics g, int index, int x, int y) {
        String externalIcon = switch (index) {
            case 0 -> "pencil";
            case 1 -> "eraser";
            case 2 -> "bucket";
            case 3 -> "trash";
            case 4 -> "undo";
            case 5 -> "redo";
            case 6 -> "save";
            default -> null;
        };
        if (externalIcon != null) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                    "antos", "textures/gui/antpaint/" + externalIcon + ".png");
            g.blit(texture, x - 8, y - 8, 16, 16, 0, 0, 24, 24, 24, 24);
            return;
        }
        int color = PALE_GREEN;
        String[][] sprites = {
                {
                        ".............##.",
                        "............####",
                        "...........##++#",
                        "..........##+++##",
                        ".........##+++##.",
                        "........##+++##..",
                        ".......##+++##...",
                        "......##+++##....",
                        ".....##+++##.....",
                        "....##+++##......",
                        "...##+++##.......",
                        "..##+++##........",
                        ".##++##..........",
                        ".####............",
                        "..##.............",
                        "...t............."
                },
                {
                        "................",
                        "...........####.",
                        ".........###++##",
                        ".......###+++##.",
                        ".....###+++##...",
                        "...###+++##.....",
                        ".###+++##.......",
                        "##+++###........",
                        "#++###...........",
                        "####.............",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        "....########....",
                        "...##......##...",
                        "..##........##..",
                        "..##........##..",
                        ".##############.",
                        "..############..",
                        "..##++++++++##..",
                        "...##++++++##...",
                        "...##++gg++##...",
                        "....##+gg+##....",
                        "....##+gg+##....",
                        ".....######.....",
                        "......####......",
                        "................",
                        "................",
                        "................"
                },
                {
                        ".....######.....",
                        "...##########...",
                        "....##.##.##....",
                        "..############..",
                        "..##++++++++##..",
                        "..##++##++##++##.",
                        "..##++##++##++##.",
                        "..##++##++##++##.",
                        "...##++++++++##..",
                        "...############..",
                        "....##......##...",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        "......####......",
                        "................",
                        "................",
                        "....##..........",
                        "...####.........",
                        "..##++##........",
                        ".##++++##.......",
                        "##++++++++++##..",
                        ".##++++##.......",
                        "..##++##........",
                        "...####.........",
                        "....##..........",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        "......####......",
                        "................",
                        "................",
                        "....##..........",
                        "...####.........",
                        "..##++##........",
                        ".##++++##.......",
                        "##++++++++++##..",
                        ".##++++##.......",
                        "..##++##........",
                        "...####.........",
                        "....##..........",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        ".##############.",
                        "#++++++++++++++#",
                        "#++##########++#",
                        "#++#........#++#",
                        "#++#........#++#",
                        "#++##########++#",
                        "#++++++++++++++#",
                        "#++##########++#",
                        "#++#++++++++#++#",
                        "#++#++gggg++#++#",
                        "#++#++gggg++#++#",
                        "#++#++++++++#++#",
                        "#++##########++#",
                        "#++++++++++++++#",
                        "################",
                        "................"
                }
        };
        String[] sprite = sprites[Math.max(0, Math.min(index, sprites.length - 1))];
        if (index == 5) {
            sprite = sprite.clone();
            for (int row = 0; row < sprite.length; row++) {
                sprite[row] = new StringBuilder(sprite[row].substring(0, Math.min(16, sprite[row].length()))).reverse().toString();
            }
        }
        for (int row = 0; row < 16; row++) {
            String line = row < sprite.length ? sprite[row] : "";
            if (line.length() > 16) line = line.substring(0, 16);
            for (int column = 0; column < 16; column++) {
                char pixel = column < line.length() ? line.charAt(column) : '.';
                if (pixel != '#' && pixel != '+' && pixel != 'g' && pixel != 't') continue;
                for (int[] offset : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                    int dx = offset[0];
                    int dy = offset[1];
                    int nx = column + dx;
                    int ny = row + dy;
                    if (nx < 0 || nx >= 16 || ny < 0 || ny >= 16) continue;
                    String neighborLine = ny < sprite.length ? sprite[ny] : "";
                    char neighbor = nx < neighborLine.length() ? neighborLine.charAt(nx) : '.';
                    if (neighbor == '.' || neighbor == ' ') g.fill(x - 8 + nx, y - 8 + ny, x - 7 + nx, y - 7 + ny, 0xFF78A878);
                }
            }
        }
        for (int row = 0; row < 16; row++) {
            String line = row < sprite.length ? sprite[row] : "";
            for (int column = 0; column < 16; column++) {
                char pixel = column < line.length() ? line.charAt(column) : '.';
                if (pixel == '#') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, BLACK);
                else if (pixel == '+') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, color);
                else if (pixel == 'g') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, GREEN);
                else if (pixel == 't') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, 0xFF657465);
            }
        }
    }

    private void toolbarIconLegacy(GuiGraphics g, int index, int x, int y) {
        int color = PALE_GREEN;
        if (index == 0) {
            g.fill(x - 9, y + 4, x - 5, y + 8, BLACK);
            g.fill(x - 7, y + 2, x - 3, y + 7, BLACK);
            g.fill(x - 5, y, x + 3, y + 6, BLACK);
            g.fill(x - 2, y - 3, x + 6, y + 3, BLACK);
            g.fill(x + 4, y - 6, x + 8, y - 1, BLACK);
            g.fill(x - 8, y + 5, x - 6, y + 7, color);
            g.fill(x - 6, y + 3, x - 4, y + 5, color);
            g.fill(x - 4, y + 1, x - 2, y + 3, color);
            g.fill(x - 2, y - 1, x + 1, y + 2, color);
            g.fill(x + 1, y - 3, x + 4, y, color);
            g.fill(x + 4, y - 5, x + 6, y - 2, color);
            g.fill(x - 4, y + 3, x - 2, y + 5, GREEN);
            g.fill(x - 2, y + 1, x + 1, y + 3, GREEN);
            g.fill(x + 1, y - 1, x + 4, y + 1, GREEN);
            g.fill(x + 3, y - 4, x + 6, y - 2, BLACK);
            g.fill(x + 5, y - 6, x + 8, y - 4, BLACK);
            g.fill(x + 5, y - 4, x + 7, y - 2, color);
        } else if (index == 1) {
            g.fill(x - 9, y - 3, x + 5, y + 7, BLACK);
            g.fill(x - 6, y - 6, x + 8, y + 4, BLACK);
            g.fill(x - 7, y - 2, x - 2, y + 3, color);
            g.fill(x - 4, y - 4, x + 2, y + 1, color);
            g.fill(x - 1, y - 5, x + 5, y, color);
            g.fill(x - 9, y + 4, x + 3, y + 8, BLACK);
            g.fill(x - 7, y + 4, x - 1, y + 6, color);
            g.fill(x - 6, y - 1, x - 3, y + 2, PALE_GREEN);
            g.fill(x - 3, y - 3, x, y, PALE_GREEN);
            g.fill(x + 1, y - 4, x + 4, y - 1, PALE_GREEN);
            g.fill(x + 3, y, x + 6, y + 3, BLACK);
            g.fill(x + 5, y - 3, x + 8, y + 1, BLACK);
            g.fill(x + 5, y - 2, x + 6, y, color);
        } else if (index == 2) {
            g.fill(x - 6, y - 7, x + 6, y - 4, BLACK);
            g.fill(x - 8, y - 5, x - 5, y + 1, BLACK);
            g.fill(x + 5, y - 5, x + 8, y + 1, BLACK);
            g.fill(x - 6, y - 5, x + 6, y - 3, color);
            g.fill(x - 7, y - 2, x + 7, y + 2, BLACK);
            g.fill(x - 5, y - 1, x + 5, y + 1, color);
            g.fill(x - 5, y + 1, x + 5, y + 7, BLACK);
            g.fill(x - 4, y + 3, x + 4, y + 6, color);
            g.fill(x - 3, y + 2, x + 4, y + 4, PALE_GREEN);
            g.fill(x - 4, y + 6, x + 4, y + 8, BLACK);
            g.fill(x - 2, y + 5, x + 3, y + 6, GREEN);
            g.fill(x - 5, y - 6, x - 3, y - 4, GREEN);
            g.fill(x + 3, y - 6, x + 5, y - 4, GREEN);
        } else if (index == 3) {
            g.fill(x - 6, y - 7, x + 6, y - 4, BLACK);
            g.fill(x - 3, y - 9, x + 3, y - 6, BLACK);
            g.fill(x - 5, y - 3, x + 5, y + 8, BLACK);
            g.fill(x - 3, y - 1, x + 3, y + 6, color);
            g.fill(x - 4, y - 2, x + 4, y, PALE_GREEN);
            g.fill(x - 3, y + 1, x - 1, y + 6, GREEN);
            g.fill(x + 1, y + 1, x + 3, y + 6, GREEN);
            g.fill(x - 8, y - 8, x + 8, y - 5, BLACK);
            g.fill(x - 7, y - 4, x + 7, y - 2, BLACK);
            g.fill(x - 8, y - 8, x - 5, y + 8, BLACK);
            g.fill(x + 5, y - 8, x + 8, y + 8, BLACK);
            g.fill(x - 9, y - 7, x - 7, y + 8, color);
            g.fill(x + 7, y - 7, x + 9, y + 8, color);
            g.fill(x - 8, y - 1, x + 8, y + 1, BLACK);
            g.fill(x - 8, y + 5, x + 8, y + 7, BLACK);
            g.fill(x - 8, y - 8, x + 8, y - 6, color);
            g.fill(x - 7, y - 7, x + 7, y - 5, BLACK);
        } else if (index == 4) {
            g.fill(x - 9, y - 4, x - 5, y + 2, color);
            g.fill(x - 7, y - 6, x - 3, y - 2, color);
            g.fill(x - 7, y, x - 3, y + 4, color);
            g.fill(x - 5, y + 3, x + 3, y + 7, color);
            g.fill(x + 1, y + 1, x + 6, y + 5, color);
            g.fill(x + 4, y - 3, x + 8, y + 3, color);
            g.fill(x + 2, y - 6, x + 6, y - 2, color);
            g.fill(x - 5, y - 1, x + 3, y + 2, BLACK);
            g.fill(x - 3, y + 2, x + 3, y + 4, BLACK);
            g.fill(x - 8, y - 5, x - 5, y - 3, PALE_GREEN);
            g.fill(x + 5, y - 2, x + 7, y, PALE_GREEN);
        } else if (index == 5) {
            g.fill(x + 5, y - 4, x + 9, y + 2, color);
            g.fill(x + 3, y - 6, x + 7, y - 2, color);
            g.fill(x + 3, y, x + 7, y + 4, color);
            g.fill(x - 3, y + 3, x + 5, y + 7, color);
            g.fill(x - 6, y + 1, x - 1, y + 5, color);
            g.fill(x - 8, y - 3, x - 4, y + 3, color);
            g.fill(x - 6, y - 6, x - 2, y - 2, color);
            g.fill(x - 3, y - 1, x + 5, y + 2, BLACK);
            g.fill(x - 3, y + 2, x + 3, y + 4, BLACK);
            g.fill(x + 5, y - 5, x + 8, y - 3, PALE_GREEN);
            g.fill(x - 7, y - 2, x - 5, y, PALE_GREEN);
        } else {
            g.fill(x - 8, y - 8, x + 8, y + 8, BLACK);
            g.fill(x - 5, y - 6, x + 5, y + 6, color);
            g.fill(x - 5, y - 6, x + 2, y - 2, GREEN);
            g.fill(x + 2, y - 6, x + 5, y - 1, BLACK);
            g.fill(x + 4, y - 6, x + 5, y - 3, PALE_GREEN);
            g.fill(x - 5, y, x + 5, y + 6, BLACK);
            g.fill(x - 3, y + 1, x + 3, y + 5, color);
            g.fill(x - 2, y + 2, x + 2, y + 4, PALE_GREEN);
            g.fill(x - 7, y - 7, x - 5, y + 5, PALE_GREEN);
            g.fill(x - 7, y + 6, x + 7, y + 8, BLACK);
            g.fill(x - 4, y + 6, x + 4, y + 7, GREEN);
        }
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        int canvasX = contentX;
        int canvasY = contentY + 30;
        int canvasHeight = Math.max(1, contentH - 32);
        if (screen.inside(canvasX, canvasY, contentW, canvasHeight, mouseX, mouseY)) {
            if (button == 0 || button == 1) {
                paintStrokeActive = state.tool == AntPaintTool.PENCIL || state.tool == AntPaintTool.ERASER;
                paintStrokeButton = button;
                lastPaintPixelX = -1;
                lastPaintPixelY = -1;
                if (paintStrokeActive) state.history.record(state.canvas);
            }
            if (button == 0 || button == 1) paintAt(mouseX, mouseY, canvasX, canvasY, contentW, canvasHeight);
        }
        else if (mouseY >= contentY + 11 && mouseY < contentY + 30 && mouseX >= contentX && mouseX < contentX + Math.min(196, contentW)) {
            paintStrokeActive = false;
            toolbarAction(((int) mouseX - contentX) / 28);
        }
        return true;
    }

    private void save() {
        String path = "/pictures/" + state.name;
        if (!path.endsWith(".antpaint")) path += ".antpaint";
        state.name = path.substring(path.lastIndexOf('/') + 1);
        AntPaintFile file = new AntPaintFile(UUID.randomUUID().toString(), state.name, state.canvas.copy(), 0L, 0L);
        String encoded = Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(file));
        state.loadedContents = encoded;
        String savedPath = path;
        boolean exists = ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + savedPath + "\t"));
        if (exists) ComputerNetworking.saveFile(savedPath, encoded); else ComputerNetworking.createFile(savedPath, encoded);
        screen.session.antmail.state.paintPath = savedPath;
        state.loadedPath = savedPath;
        screen.session.antmail.state.paintAttachment = null;
        screen.session.antmail.state.paintLoadPendingPath = "";
        state.dirty = false;
        ComputerNetworking.listFiles();
    }

    private void cycleTool(int offset) {
        state.tool = offset < 52 ? AntPaintTool.PENCIL : offset < 108 ? AntPaintTool.ERASER : AntPaintTool.FILL;
    }

    private void toolbarAction(int index) {
        if (index == 0) state.tool = AntPaintTool.PENCIL;
        else if (index == 1) state.tool = AntPaintTool.ERASER;
        else if (index == 2) state.tool = AntPaintTool.FILL;
        else if (index == 3) {
            state.history.record(state.canvas);
            state.canvas.clear();
            state.dirty = true;
        } else if (index == 4 && state.history.canUndo()) {
            state.canvas = state.history.undo(state.canvas);
            state.dirty = true;
        } else if (index == 5 && state.history.canRedo()) {
            state.canvas = state.history.redo(state.canvas);
            state.dirty = true;
        } else if (index == 6) {
            save();
        }
    }

    void paintAt(double mouseX, double mouseY, int x, int y, int w, int h) {
        int size = Math.min(w / AntPaintCanvas.WIDTH, h / AntPaintCanvas.HEIGHT);
        if (size <= 0) return;
        int canvasX = x + (w - size * AntPaintCanvas.WIDTH) / 2;
        int px = (int) ((mouseX - canvasX) / size);
        int canvasY = y + Math.max(0, (h - size * AntPaintCanvas.HEIGHT) / 2);
        int py = (int) ((mouseY - canvasY) / size);
        if (px < 0 || py < 0 || px >= AntPaintCanvas.WIDTH || py >= AntPaintCanvas.HEIGHT) {
            lastPaintPixelX = -1;
            lastPaintPixelY = -1;
            return;
        }
        if (state.tool == AntPaintTool.FILL) {
            state.history.record(state.canvas);
            if (state.canvas.floodFill(px, py, true) > 0) state.dirty = true;
            return;
        }
        if (lastPaintPixelX < 0 || lastPaintPixelY < 0) applyPixel(px, py);
        else {
            int dx = Math.abs(px - lastPaintPixelX);
            int dy = Math.abs(py - lastPaintPixelY);
            int stepX = lastPaintPixelX < px ? 1 : -1;
            int stepY = lastPaintPixelY < py ? 1 : -1;
            int error = dx - dy;
            int currentX = lastPaintPixelX;
            int currentY = lastPaintPixelY;
            while (currentX != px || currentY != py) {
                int twiceError = error * 2;
                if (twiceError > -dy) { error -= dy; currentX += stepX; }
                if (twiceError < dx) { error += dx; currentY += stepY; }
                applyPixel(currentX, currentY);
            }
        }
        lastPaintPixelX = px;
        lastPaintPixelY = py;
    }

    private void applyPixel(int x, int y) {
        boolean changed = state.tool == AntPaintTool.PENCIL
                ? state.canvas.draw(x, y)
                : state.canvas.erase(x, y);
        if (changed) state.dirty = true;
    }

    static final class State {
        AntPaintCanvas canvas = new AntPaintCanvas();
        final AntPaintHistory history = new AntPaintHistory();
        AntPaintTool tool = AntPaintTool.PENCIL;
        String name = "UNTITLED.ANTPAINT";
        String loadedPath = "";
        String loadedContents = "";
        boolean dirty;
    }
}
