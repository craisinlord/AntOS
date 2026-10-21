package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.google.gson.JsonObject;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.AntOSUiSounds.Cue;
import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.computer.paint.AntPaintFile;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.computer.paint.AntPaintFilename;
import com.craisinlord.antos.content.computer.paint.AntPaintHistory;
import com.craisinlord.antos.content.computer.paint.AntPaintTool;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;


final class PaintApp extends ComputerApp {
    private static final int TOOLBAR = 18;
    private static final int BUTTON = 18;
    private static final int MENU_ROW = 13;
    private static final AntPaintTool[] TOOLS = {
            AntPaintTool.PENCIL, AntPaintTool.ERASER, AntPaintTool.FILL,
            AntPaintTool.LINE, AntPaintTool.RECTANGLE, AntPaintTool.ELLIPSE
    };

    final State state = new State();
    final AppKit kit = new AppKit(this);
    private final FileDialog dialog = new FileDialog(this, kit);
    private int canvasX;
    private int canvasY;
    private int pixelSize = 1;
    private boolean strokeActive;
    private int strokeButton = -1;
    private boolean strokeValue;
    private AntPaintCanvas strokeBefore;
    private AntPaintCanvas shapeBase;
    private int startX;
    private int startY;
    private int endX;
    private int endY;
    private int lastX = -1;
    private int lastY = -1;
    private String confirmAction = "";
    private long confirmUntil;
    private String notice = "";
    private int noticeColor = Ui.ACCENT;
    private long noticeUntil;
    private boolean listed;

    void sync() {
        var fileState = ComputerFileSystemClientState.get();
        if (fileState.openSerial() != state.openSerial) {
            state.openSerial = fileState.openSerial();
            String opened = fileState.openedPath();
            boolean requested = !state.pendingOpenPath.isEmpty() && opened.equals(state.pendingOpenPath);
            if (opened.toLowerCase(Locale.ROOT).endsWith(AntPaintFilename.EXTENSION) && !fileState.openedContents().isEmpty() && (requested || !state.dirty)) {
                try {
                    AntPaintFile file = AntPaintFileCodec.decode(Base64.getDecoder().decode(fileState.openedContents()));
                    load(opened, file);
                    screen.session.antmail.state.paintPath = opened;
                } catch (RuntimeException exception) {
                    if (requested) notice(tr("notice.unreadable"), Ui.ERROR);
                }
            }
            if (requested) state.pendingOpenPath = "";
        }
        if (!state.pendingPath.isEmpty() && fileState.mutationSerial() != state.pendingSerial
                && fileState.lastMutationPath().equals(state.pendingPath)) {
            resolvePending(fileState.lastMutationSuccess(), fileState.lastMutationError());
        }
    }

    private void load(String path, AntPaintFile file) {
        state.path = path;
        state.name = safeName(path.substring(path.lastIndexOf('/') + 1), file.filename());
        state.fileId = file.fileId();
        state.canvas = file.canvas().copy();
        state.savedPixels = state.canvas.copyPixels();
        state.history.clear();
        state.dirty = false;
        cancelStroke();
    }

    private static String safeName(String candidate, String fallback) {
        try {
            return AntPaintFilename.normalize(candidate);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private void resolvePending(boolean success, String error) {
        String kind = state.pendingKind;
        String path = state.pendingPath;
        state.pendingPath = "";
        if (!success) {
            AntOSUiSounds.play(Cue.ERROR);
            notice(tr("notice.failed", AppKit.fileError(error)), Ui.ERROR);
            return;
        }
        if (kind.equals("save_as")) {
            state.path = path;
            state.name = state.pendingName;
        }
        state.savedPixels = state.pendingPixels;
        markChanged();
        AntOSUiSounds.play(Cue.SAVE);
        notice(tr("notice.saved"), Ui.TEXT);
        ComputerNetworking.listFiles();
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        if (!resumePath.isEmpty() && screen.session.text.state.pendingOpenPath.isEmpty()) {
            state.pendingOpenPath = resumePath;
            ComputerNetworking.openFile(resumePath);
            resumePath = "";
        }
        if (!listed) {
            listed = true;
            ComputerNetworking.listFiles();
        }
        sync();
        kit.begin();
        kit.suppressHover(dialog.active() || state.menuOpen);
        if (!confirmAction.isEmpty() && System.currentTimeMillis() > confirmUntil) confirmAction = "";
        renderToolbar(g, x, y);
        renderCanvas(g, x, y + TOOLBAR + 2, w, y + h - 11);
        renderFooter(g, x, y + h - 10, w);
        if (state.menuOpen) renderMenu(g, x, y, w, h);
        dialog.render(g, x, y, w, h);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderToolbar(GuiGraphics g, int x, int y) {
        int fileWidth = font.width(tr("file")) + 10;
        kit.button(g, x, y, fileWidth, TOOLBAR, tr("file"), state.menuOpen, true, () -> state.menuOpen = !state.menuOpen);
        int buttonX = x + fileWidth + 3;
        for (AntPaintTool tool : TOOLS) {
            String key = tool.name().toLowerCase(Locale.ROOT);
            kit.iconButton(g, buttonX, y, BUTTON, icon(key), state.tool == tool, true, tr("tip." + key), () -> selectTool(tool));
            buttonX += BUTTON + 1;
        }
        kit.divider(g, buttonX + 1, y, TOOLBAR);
        buttonX += 4;
        kit.iconButton(g, buttonX, y, BUTTON, icon("undo"), false, state.history.canUndo() && !strokeActive, tr("tip.undo"), this::undo);
        buttonX += BUTTON + 1;
        kit.iconButton(g, buttonX, y, BUTTON, icon("redo"), false, state.history.canRedo() && !strokeActive, tr("tip.redo"), this::redo);
        buttonX += BUTTON + 1;
        kit.divider(g, buttonX + 1, y, TOOLBAR);
        buttonX += 4;
        kit.button(g, buttonX, y, BUTTON, TOOLBAR, "", false, true, tr("tip.size", state.brushSize), this::cycleSize);
        int dot = state.brushSize * 2;
        int dotX = buttonX + (BUTTON - dot) / 2;
        int dotY = y + (TOOLBAR - dot) / 2;
        g.fill(dotX, dotY, dotX + dot, dotY + dot, Ui.TEXT);
    }

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("antos", "textures/gui/antpaint/" + (name.equals("fill") ? "bucket" : name) + ".png");
    }

    private void renderCanvas(GuiGraphics g, int x, int top, int w, int bottom) {
        g.fill(x, top, x + w, bottom, Ui.PANEL);
        int height = Math.max(1, bottom - top);
        pixelSize = Math.max(1, Math.min(w / AntPaintCanvas.WIDTH, height / AntPaintCanvas.HEIGHT));
        int canvasWidth = pixelSize * AntPaintCanvas.WIDTH;
        int canvasHeight = pixelSize * AntPaintCanvas.HEIGHT;
        canvasX = x + (w - canvasWidth) / 2;
        canvasY = top + Math.max(0, (height - canvasHeight) / 2);
        kit.outline(g, canvasX - 1, canvasY - 1, canvasWidth + 2, canvasHeight + 2, Ui.LINE);
        g.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, Ui.BACKGROUND);
        for (int py = 0; py < AntPaintCanvas.HEIGHT; py++) {
            int px = 0;
            while (px < AntPaintCanvas.WIDTH) {
                if (!state.canvas.get(px, py)) {
                    px++;
                    continue;
                }
                int start = px;
                while (px < AntPaintCanvas.WIDTH && state.canvas.get(px, py)) px++;
                g.fill(canvasX + start * pixelSize, canvasY + py * pixelSize, canvasX + px * pixelSize, canvasY + (py + 1) * pixelSize, Ui.ACCENT);
            }
        }
        if (state.grid && pixelSize >= 3) {
            for (int column = 1; column < AntPaintCanvas.WIDTH; column++) g.fill(canvasX + column * pixelSize, canvasY, canvasX + column * pixelSize + 1, canvasY + canvasHeight, Ui.alpha(Ui.HOVER, 0x80));
            for (int row = 1; row < AntPaintCanvas.HEIGHT; row++) g.fill(canvasX, canvasY + row * pixelSize, canvasX + canvasWidth, canvasY + row * pixelSize + 1, Ui.alpha(Ui.HOVER, 0x80));
        }
        int[] hover = hoverPixel();
        if (hover != null && !strokeActive && !dialog.active() && !state.menuOpen) {
            int low = state.tool == AntPaintTool.FILL ? 0 : -(state.brushSize - 1) / 2;
            int high = state.tool == AntPaintTool.FILL ? 0 : state.brushSize / 2;
            int left = Math.max(0, hover[0] + low);
            int right = Math.min(AntPaintCanvas.WIDTH - 1, hover[0] + high);
            int upper = Math.max(0, hover[1] + low);
            int lower = Math.min(AntPaintCanvas.HEIGHT - 1, hover[1] + high);
            kit.outline(g, canvasX + left * pixelSize, canvasY + upper * pixelSize, (right - left + 1) * pixelSize, (lower - upper + 1) * pixelSize, Ui.FOCUS);
        }
        kit.hit(canvasX, canvasY, canvasWidth, canvasHeight, this::pressCanvas);
    }

    private void renderFooter(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, Ui.SURFACE);
        int textY = y + 2;
        int[] hover = hoverPixel();
        String toolName = tr("tool." + state.tool.name().toLowerCase(Locale.ROOT));
        String position;
        if (strokeActive && shapeBase != null) position = tr("shape_size", Math.abs(endX - startX) + 1, Math.abs(endY - startY) + 1);
        else if (hover != null) position = tr("position", hover[0], hover[1]);
        else position = tr("position_none");
        String detail = state.tool == AntPaintTool.FILL ? toolName : toolName + " " + tr("size", state.brushSize);
        String left = position + "  " + detail;
        String right;
        int rightColor;
        if (System.currentTimeMillis() < noticeUntil) {
            right = notice;
            rightColor = noticeColor;
        } else {
            right = state.name + (state.dirty ? " *" : "");
            rightColor = state.dirty ? Ui.TEXT : Ui.ACCENT;
        }
        int leftWidth = Math.min(font.width(left), w / 2 + 10);
        g.drawString(font, Component.literal(screen.trimToWidth(left, leftWidth)), x, textY, Ui.TEXT_MUTED, false);
        String shown = screen.trimToWidth(right, Math.max(10, w - leftWidth - 8));
        g.drawString(font, Component.literal(shown), x + w - font.width(shown), textY, rightColor, false);
    }

    private void renderMenu(GuiGraphics g, int x, int y, int w, int h) {
        kit.suppressHover(false);
        kit.hit(x, y, w, h, () -> state.menuOpen = false);
        List<MenuItem> items = menuItems();
        int width = 120;
        for (MenuItem item : items) if (item != null) width = Math.max(width, font.width(item.label()) + font.width(item.shortcut()) + 24);
        width = Math.min(width, w);
        int height = 6;
        for (MenuItem item : items) height += item == null ? 5 : MENU_ROW;
        int menuX = x;
        int menuY = y + TOOLBAR + 1;
        g.pose().pushPose();
        try {
            g.pose().translate(0.0F, 0.0F, 100.0F);
            g.fill(menuX, menuY, menuX + width, menuY + height, Ui.BACKGROUND);
            screen.box(g, menuX, menuY, menuX + width, menuY + height, Ui.BORDER_STRONG);
            int rowY = menuY + 3;
            for (MenuItem item : items) {
                if (item == null) {
                    g.fill(menuX + 6, rowY + 2, menuX + width - 6, rowY + 3, Ui.SURFACE);
                    rowY += 5;
                    continue;
                }
                boolean hover = kit.hovered(menuX + 2, rowY, width - 4, MENU_ROW);
                if (hover) g.fill(menuX + 2, rowY, menuX + width - 2, rowY + MENU_ROW, Ui.HOVER);
                g.drawString(font, Component.literal(item.label()), menuX + 7, rowY + 3, item.warning() ? Ui.ERROR : hover ? Ui.ACCENT : Ui.TEXT, false);
                if (!item.shortcut().isEmpty()) {
                    g.drawString(font, Component.literal(item.shortcut()), menuX + width - 7 - font.width(item.shortcut()), rowY + 3, Ui.TEXT_MUTED, false);
                }
                Runnable action = item.action();
                kit.hit(menuX + 2, rowY, width - 4, MENU_ROW, action);
                rowY += MENU_ROW;
            }
        } finally {
            g.pose().popPose();
        }
    }

    private List<MenuItem> menuItems() {
        List<MenuItem> items = new ArrayList<>();
        boolean confirmNew = confirmAction.equals("new");
        boolean confirmClear = confirmAction.equals("clear");
        items.add(new MenuItem(confirmNew ? tr("menu.discard") : tr("menu.new"), confirmNew ? "" : tr("key.new"), confirmNew, () -> closeMenuAfter(this::newFile)));
        items.add(new MenuItem(tr("menu.open"), tr("key.open"), false, () -> closeMenuAfter(this::beginOpen)));
        items.add(new MenuItem(tr("menu.save"), tr("key.save"), false, () -> closeMenuAfter(this::save)));
        items.add(new MenuItem(tr("menu.save_as"), "", false, () -> closeMenuAfter(this::beginSaveAs)));
        items.add(null);
        items.add(new MenuItem(tr("menu.invert"), tr("key.invert"), false, () -> closeMenuAfter(this::invert)));
        items.add(new MenuItem(confirmClear ? tr("menu.clear_confirm") : tr("menu.clear"), "", confirmClear, () -> closeMenuAfter(this::clearCanvas)));
        items.add(null);
        items.add(new MenuItem(state.grid ? tr("menu.grid_hide") : tr("menu.grid_show"), tr("key.grid"), false, () -> closeMenuAfter(this::toggleGrid)));
        return items;
    }

    private void closeMenuAfter(Runnable action) {
        String before = confirmAction;
        action.run();
        if (confirmAction.isEmpty() || confirmAction.equals(before)) state.menuOpen = false;
    }

    private int[] hoverPixel() {
        int mouseX = screen.session.mouseX;
        int mouseY = screen.session.mouseY;
        if (mouseX < canvasX || mouseY < canvasY) return null;
        int px = (mouseX - canvasX) / pixelSize;
        int py = (mouseY - canvasY) / pixelSize;
        if (px >= AntPaintCanvas.WIDTH || py >= AntPaintCanvas.HEIGHT) return null;
        return new int[]{px, py};
    }

    private int[] pixelAt(double mouseX, double mouseY, boolean clamp) {
        int px = (int) Math.floor((mouseX - canvasX) / pixelSize);
        int py = (int) Math.floor((mouseY - canvasY) / pixelSize);
        if (clamp) return new int[]{Math.max(0, Math.min(AntPaintCanvas.WIDTH - 1, px)), Math.max(0, Math.min(AntPaintCanvas.HEIGHT - 1, py))};
        if (px < 0 || py < 0 || px >= AntPaintCanvas.WIDTH || py >= AntPaintCanvas.HEIGHT) return null;
        return new int[]{px, py};
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        kit.click(mouseX, mouseY, button);
        return true;
    }

    private void pressCanvas() {
        int button = kit.clickButton();
        if (strokeActive || (button != 0 && button != 1)) return;
        int[] pixel = pixelAt(kit.clickX(), kit.clickY(), false);
        if (pixel == null) return;
        boolean primary = button == 0;
        strokeValue = state.tool == AntPaintTool.ERASER ? !primary : primary;
        if (state.tool == AntPaintTool.FILL) {
            if (state.canvas.get(pixel[0], pixel[1]) != strokeValue) {
                state.history.record(state.canvas);
                state.canvas.floodFill(pixel[0], pixel[1], strokeValue);
                markChanged();
            }
            return;
        }
        strokeActive = true;
        strokeButton = button;
        strokeBefore = state.canvas.copy();
        startX = pixel[0];
        startY = pixel[1];
        endX = startX;
        endY = startY;
        if (shapeTool()) {
            shapeBase = state.canvas.copy();
            updateShape(pixel[0], pixel[1]);
        } else {
            lastX = -1;
            lastY = -1;
            paintTo(pixel[0], pixel[1]);
        }
    }

    private boolean shapeTool() {
        return state.tool == AntPaintTool.LINE || state.tool == AntPaintTool.RECTANGLE || state.tool == AntPaintTool.ELLIPSE;
    }

    void drag(double mouseX, double mouseY, int button) {
        if (kit.draggingScrollbar()) {
            kit.drag(mouseY);
            return;
        }
        if (!strokeActive || button != strokeButton) return;
        if (shapeBase != null) {
            int[] pixel = pixelAt(mouseX, mouseY, true);
            updateShape(pixel[0], pixel[1]);
            return;
        }
        int[] pixel = pixelAt(mouseX, mouseY, false);
        if (pixel == null) {
            lastX = -1;
            lastY = -1;
            return;
        }
        paintTo(pixel[0], pixel[1]);
    }

    void release(int button) {
        kit.release();
        if (!strokeActive || button != strokeButton) return;
        if (strokeBefore != null && !Arrays.equals(strokeBefore.copyPixels(), state.canvas.copyPixels())) state.history.record(strokeBefore);
        cancelStroke();
        markChanged();
    }

    private void cancelStroke() {
        strokeActive = false;
        strokeButton = -1;
        strokeBefore = null;
        shapeBase = null;
        lastX = -1;
        lastY = -1;
    }

    private void paintTo(int px, int py) {
        if (lastX < 0 || lastY < 0) stamp(px, py);
        else line(lastX, lastY, px, py);
        lastX = px;
        lastY = py;
        markChanged();
    }

    private void updateShape(int px, int py) {
        int dx = px - startX;
        int dy = py - startY;
        if (Screen.hasShiftDown()) {
            int stepX = dx < 0 ? -1 : 1;
            int stepY = dy < 0 ? -1 : 1;
            int distance = Math.max(Math.abs(dx), Math.abs(dy));
            if (state.tool == AntPaintTool.LINE && Math.abs(dx) > Math.abs(dy) * 2) dy = 0;
            else if (state.tool == AntPaintTool.LINE && Math.abs(dy) > Math.abs(dx) * 2) dx = 0;
            else {
                dx = stepX * distance;
                dy = stepY * distance;
            }
        }
        endX = startX + dx;
        endY = startY + dy;
        state.canvas = shapeBase.copy();
        switch (state.tool) {
            case LINE -> line(startX, startY, endX, endY);
            case RECTANGLE -> {
                line(startX, startY, endX, startY);
                line(endX, startY, endX, endY);
                line(endX, endY, startX, endY);
                line(startX, endY, startX, startY);
            }
            case ELLIPSE -> ellipse(startX, startY, endX, endY);
            default -> { }
        }
        markChanged();
    }

    private void line(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1;
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;
        stamp(x, y);
        while (x != x1 || y != y1) {
            int twice = error * 2;
            if (twice > -dy) { error -= dy; x += stepX; }
            if (twice < dx) { error += dx; y += stepY; }
            stamp(x, y);
        }
    }

    private void ellipse(int x0, int y0, int x1, int y1) {
        long a = Math.abs(x1 - x0);
        long b = Math.abs(y1 - y0);
        if (a == 0 || b == 0) {
            line(x0, y0, x1, y1);
            return;
        }
        long b1 = b & 1;
        double dx = 4.0 * (1 - a) * b * b;
        double dy = 4.0 * (b1 + 1) * a * a;
        double error = dx + dy + b1 * a * a;
        if (x0 > x1) { x0 = x1; x1 += (int) a; }
        if (y0 > y1) y0 = y1;
        y0 += (int) ((b + 1) / 2);
        y1 = y0 - (int) b1;
        long aa = 8 * a * a;
        long bb = 8 * b * b;
        do {
            stamp(x1, y0);
            stamp(x0, y0);
            stamp(x0, y1);
            stamp(x1, y1);
            double twice = 2 * error;
            if (twice <= dy) { y0++; y1--; error += dy += aa; }
            if (twice >= dx || 2 * error > dy) { x0++; x1--; error += dx += bb; }
        } while (x0 <= x1);
        while (y0 - y1 <= b) {
            stamp(x0 - 1, y0);
            stamp(x1 + 1, y0++);
            stamp(x0 - 1, y1);
            stamp(x1 + 1, y1--);
        }
    }

    private void stamp(int px, int py) {
        int low = -(state.brushSize - 1) / 2;
        int high = state.brushSize / 2;
        for (int offsetY = low; offsetY <= high; offsetY++) {
            for (int offsetX = low; offsetX <= high; offsetX++) {
                int x = px + offsetX;
                int y = py + offsetY;
                if (x >= 0 && y >= 0 && x < AntPaintCanvas.WIDTH && y < AntPaintCanvas.HEIGHT) state.canvas.set(x, y, strokeValue);
            }
        }
    }

    private void markChanged() {
        state.dirty = !Arrays.equals(state.canvas.copyPixels(), state.savedPixels);
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (dialog.active()) return dialog.scroll(scrollY);
        return false;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        return dialog.active() && dialog.type(codePoint);
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (dialog.active()) return dialog.key(keyCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && state.menuOpen) {
            state.menuOpen = false;
            return true;
        }
        boolean control = screen.hasControl(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> { if (shift) redo(); else undo(); }
                case GLFW.GLFW_KEY_Y -> redo();
                case GLFW.GLFW_KEY_S -> { if (shift) beginSaveAs(); else save(); }
                case GLFW.GLFW_KEY_O -> beginOpen();
                case GLFW.GLFW_KEY_N -> newFile();
                case GLFW.GLFW_KEY_I -> invert();
                default -> { return false; }
            }
            state.menuOpen = false;
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_B -> selectTool(AntPaintTool.PENCIL);
            case GLFW.GLFW_KEY_E -> selectTool(AntPaintTool.ERASER);
            case GLFW.GLFW_KEY_F -> selectTool(AntPaintTool.FILL);
            case GLFW.GLFW_KEY_L -> selectTool(AntPaintTool.LINE);
            case GLFW.GLFW_KEY_R -> selectTool(AntPaintTool.RECTANGLE);
            case GLFW.GLFW_KEY_O -> selectTool(AntPaintTool.ELLIPSE);
            case GLFW.GLFW_KEY_G -> toggleGrid();
            case GLFW.GLFW_KEY_LEFT_BRACKET -> state.brushSize = Math.max(1, state.brushSize - 1);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> state.brushSize = Math.min(3, state.brushSize + 1);
            default -> { return false; }
        }
        return true;
    }

    private void selectTool(AntPaintTool tool) {
        if (strokeActive) return;
        state.tool = tool;
    }

    private void cycleSize() {
        state.brushSize = state.brushSize % 3 + 1;
    }

    private void toggleGrid() {
        state.grid = !state.grid;
    }

    private void undo() {
        if (strokeActive || !state.history.canUndo()) return;
        state.canvas = state.history.undo(state.canvas);
        markChanged();
    }

    private void redo() {
        if (strokeActive || !state.history.canRedo()) return;
        state.canvas = state.history.redo(state.canvas);
        markChanged();
    }

    private void invert() {
        if (strokeActive) return;
        state.history.record(state.canvas);
        for (int py = 0; py < AntPaintCanvas.HEIGHT; py++) for (int px = 0; px < AntPaintCanvas.WIDTH; px++) state.canvas.set(px, py, !state.canvas.get(px, py));
        markChanged();
    }

    private void clearCanvas() {
        if (strokeActive) return;
        if (!confirmAction.equals("clear")) {
            confirm("clear");
            return;
        }
        confirmAction = "";
        state.history.record(state.canvas);
        state.canvas.clear();
        markChanged();
    }

    private void newFile() {
        if (strokeActive) return;
        if (state.dirty && !confirmAction.equals("new")) {
            confirm("new");
            return;
        }
        confirmAction = "";
        state.path = "";
        state.name = State.UNTITLED;
        state.fileId = "";
        state.canvas = new AntPaintCanvas();
        state.savedPixels = state.canvas.copyPixels();
        state.history.clear();
        state.dirty = false;
    }

    private void confirm(String action) {
        confirmAction = action;
        confirmUntil = System.currentTimeMillis() + 3000L;
    }

    private void beginOpen() {
        dialog.openPicker("IMAGE", AntPaintFilename.EXTENSION, tr("open.title"), state.dirty ? tr("open.unsaved") : "", state.path, path -> {
            state.pendingOpenPath = path;
            ComputerNetworking.openFile(path);
        });
    }

    private boolean fileExists(String path) {
        if (path.isEmpty()) return false;
        return FileListing.exists(path);
    }

    private String folder() {
        if (state.path.isEmpty()) return ComputerFileSystem.PICTURES;
        String parent = screen.parentDirectory(state.path);
        return parent.isBlank() || ComputerFileSystem.isProtected(parent) ? ComputerFileSystem.PICTURES : parent;
    }

    private String encode(String name) {
        String id = state.fileId.isBlank() ? UUID.randomUUID().toString() : state.fileId;
        state.fileId = id;
        AntPaintFile file = new AntPaintFile(id, name, state.canvas.copy(), 0L, 0L);
        return Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(file));
    }

    private void save() {
        if (strokeActive) return;
        if (!fileExists(state.path) || ComputerFileSystem.isProtected(state.path)) {
            beginSaveAs();
            return;
        }
        state.pendingPixels = state.canvas.copyPixels();
        pending("save", state.path);
        ComputerNetworking.saveFile(state.path, encode(state.name));
        linkAntmail(state.path);
    }

    private void beginSaveAs() {
        if (strokeActive) return;
        String initial = state.name.toLowerCase(Locale.ROOT).endsWith(AntPaintFilename.EXTENSION)
                ? state.name.substring(0, state.name.length() - AntPaintFilename.EXTENSION.length()) : state.name;
        dialog.openName(tr("save_as.title"), initial, input -> {
            String base = input.toLowerCase(Locale.ROOT).endsWith(AntPaintFilename.EXTENSION)
                    ? input.substring(0, input.length() - AntPaintFilename.EXTENSION.length()).trim() : input.trim();
            if (base.isEmpty()) return tr("error.blank");
            if (!AntPaintFilename.isValid(base)) return tr("error.name");
            String name = AntPaintFilename.normalize(base);
            String parent = folder();
            String target = parent.equals("/") ? "/" + name : parent + "/" + name;
            if (target.length() > ComputerFileSystem.MAX_PATH_LENGTH) return tr("error.long");
            if (fileExists(target)) {
                if (!target.equals(state.path)) return tr("error.exists");
                save();
                return "";
            }
            state.pendingName = name;
            state.pendingPixels = state.canvas.copyPixels();
            pending("save_as", target);
            ComputerNetworking.createFile(target, encode(name));
            linkAntmail(target);
            return "";
        });
    }

    private void linkAntmail(String path) {
        screen.session.antmail.state.paintPath = path;
        screen.session.antmail.state.paintAttachment = null;
        screen.session.antmail.state.paintLoadPendingPath = "";
    }

    private void pending(String kind, String path) {
        state.pendingKind = kind;
        state.pendingPath = path;
        state.pendingSerial = ComputerFileSystemClientState.get().mutationSerial();
    }

    private void notice(String text, int color) {
        notice = text;
        noticeColor = color;
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    private String tr(String key, Object... arguments) {
        return Component.translatable("computer.antos.paint." + key, arguments).getString();
    }

    private record MenuItem(String label, String shortcut, boolean warning, Runnable action) { }

    private String resumePath = "";

    static final class State {
        static final String UNTITLED = "UNTITLED.ANTPAINT";
        AntPaintCanvas canvas = new AntPaintCanvas();
        final AntPaintHistory history = new AntPaintHistory();
        AntPaintTool tool = AntPaintTool.PENCIL;
        String name = UNTITLED;
        String path = "";
        String fileId = "";
        boolean[] savedPixels = new boolean[AntPaintCanvas.PIXEL_COUNT];
        boolean dirty;
        boolean grid = com.craisinlord.antos.config.AntOSSettings.preferences().paintGrid;
        boolean menuOpen;
        int brushSize = com.craisinlord.antos.config.AntOSSettings.preferences().paintBrush;
        int openSerial = -1;
        String pendingOpenPath = "";
        String pendingKind = "";
        String pendingPath = "";
        String pendingName = "";
        boolean[] pendingPixels = new boolean[AntPaintCanvas.PIXEL_COUNT];
        int pendingSerial;
    }

    JsonObject saveResume() {
        JsonObject json = new JsonObject();
        if (!state.path.isEmpty()) json.addProperty("path", state.path);
        return json;
    }

    void loadResume(JsonObject json) {
        String path = ComputerResume.string(json, "path");
        if (path.startsWith("/")) resumePath = path;
    }

}
