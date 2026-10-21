package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.google.gson.JsonObject;
import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.computer.paint.AntPaintFile;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.computer.paint.AntPaintFilename;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;


final class FileExplorerApp extends ComputerApp {
    private static final int TOOLBAR = 18;
    private static final int BUTTON = 18;
    private static final int ROW = 16;
    private static final int SIDEBAR = 82;
    private static final int MENU_ROW = 13;
    private static final String[] PLACES = {"/", ComputerFileSystem.DOCUMENTS, ComputerFileSystem.PICTURES, ComputerFileSystem.SYSTEM};

    final State state = new State();
    final AppKit kit = new AppKit(this);
    private final FileDialog dialog = new FileDialog(this, kit);
    private final List<Drop> drops = new ArrayList<>();
    private List<Entry> visibleEntries = List.of();
    private int listTop;
    private int listBottom;
    private int visibleRows = 1;
    private String notice = "";
    private int noticeColor = Ui.ACCENT;
    private long noticeUntil;
    private String typeBuffer = "";
    private long typeTime;
    private String pressPath = "";
    private double pressX;
    private double pressY;
    private boolean dragging;
    private String dropTarget = "";
    private long lastAutoScroll;
    private String lastClickPath = "";
    private long lastClickTime;
    private boolean menuOpen;
    private String menuPath = "";
    private int menuX;
    private int menuY;

    static ResourceLocation icon(String name) {
        String folder = name.equals("pencil") || name.equals("trash") ? "antpaint" : "files";
        return new ResourceLocation("antos", "textures/gui/" + folder + "/" + name + ".png");
    }

    void reveal(String path) {
        state.directory = screen.parentDirectory(path);
        state.selected = path;
        state.scroll = 0;
        state.revealSelected = true;
        menuOpen = false;
        ComputerNetworking.listFiles();
    }

    void sync() {
        var fileState = ComputerFileSystemClientState.get();
        if (!state.pendingPath.isEmpty() && fileState.mutationSerial() != state.pendingSerial
                && fileState.lastMutationPath().equals(state.pendingPath)) {
            resolvePending(fileState.lastMutationSuccess(), fileState.lastMutationError());
        }
        if (fileState.success() && !state.directory.equals("/") && !fileState.files().isEmpty() && find(state.directory) == null) {
            state.directory = "/";
            state.selected = "";
        }
    }

    private void resolvePending(boolean success, String error) {
        String kind = state.pendingKind;
        String path = state.pendingPath;
        state.pendingPath = "";
        ComputerNetworking.listFiles();
        if (!success) {
            notice(tr("notice.failed", AppKit.fileError(error)), Ui.ERROR);
            return;
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        notice(tr("notice." + kind, name), Ui.TEXT);
        if (kind.equals("delete")) {
            state.selected = "";
            return;
        }
        if (screen.parentDirectory(path).equals(state.directory)) {
            state.selected = path;
            state.revealSelected = true;
        }
        if (kind.equals("create_text")) {
            ComputerNetworking.openFile(path);
            screen.open("TEXT");
        } else if (kind.equals("create_image")) {
            ComputerNetworking.openFile(path);
            screen.open("PAINT");
        }
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        sync();
        kit.begin();
        drops.clear();
        kit.suppressHover(dialog.active() || menuOpen || dragging);
        List<Entry> all = entries();
        List<Entry> rows = rows(all);
        visibleEntries = rows;
        Entry selected = find(state.selected);
        renderToolbar(g, x, y, w, selected);
        renderAddress(g, x, y + TOOLBAR + 3, w);
        int bodyTop = y + TOOLBAR + 24;
        int bodyBottom = y + h - 12;
        int listX = x;
        if (state.sidebar) {
            renderSidebar(g, x, bodyTop, bodyBottom, all);
            listX = x + SIDEBAR + 4;
        }
        renderList(g, listX, bodyTop, x + w - listX, bodyBottom, rows, all);
        renderFooter(g, x, y + h - 10, w, rows, selected, all);
        if (menuOpen) renderMenu(g, x, y, w, h);
        if (dragging) renderDragGhost(g);
        dialog.render(g, x, y, w, h);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderToolbar(GuiGraphics g, int x, int y, int w, Entry selected) {
        boolean writable = !ComputerFileSystem.isProtected(state.directory);
        boolean mutable = selected != null && mutable(selected.path());
        int buttonX = x;
        kit.iconButton(g, buttonX, y, BUTTON, icon("up"), false, !state.directory.equals("/"), tr("tip.up"), this::goUp);
        buttonX += BUTTON + 1;
        kit.divider(g, buttonX + 1, y, TOOLBAR);
        buttonX += 4;
        kit.iconButton(g, buttonX, y, BUTTON, icon("new_folder"), false, writable, tr("tip.new_folder"), this::newFolder);
        buttonX += BUTTON + 1;
        kit.iconButton(g, buttonX, y, BUTTON, icon("new_text"), false, writable, tr("tip.new_text"), this::newTextFile);
        buttonX += BUTTON + 1;
        kit.iconButton(g, buttonX, y, BUTTON, icon("new_image"), false, writable, tr("tip.new_image"), this::newPainting);
        buttonX += BUTTON + 1;
        kit.divider(g, buttonX + 1, y, TOOLBAR);
        buttonX += 4;
        kit.iconButton(g, buttonX, y, BUTTON, icon("open"), false, selected != null, tr("tip.open"), () -> activate(find(state.selected)));
        buttonX += BUTTON + 1;
        kit.iconButton(g, buttonX, y, BUTTON, icon("pencil"), false, mutable, tr("tip.rename"), () -> rename(find(state.selected)));
        buttonX += BUTTON + 1;
        kit.iconButton(g, buttonX, y, BUTTON, icon("move"), false, mutable, tr("tip.move"), () -> move(find(state.selected)));
        buttonX += BUTTON + 1;
        kit.iconButton(g, buttonX, y, BUTTON, icon("trash"), false, mutable, tr("tip.delete"), () -> delete(find(state.selected)));
        buttonX += BUTTON + 1;
        kit.divider(g, buttonX + 1, y, TOOLBAR);
        buttonX += 4;
        kit.iconButton(g, buttonX, y, BUTTON, icon("refresh"), false, true, tr("tip.refresh"), this::refresh);
        buttonX += BUTTON + 1;
        int placesWidth = kit.labelWidth(tr("places"));
        if (buttonX + 4 + placesWidth <= x + w) {
            kit.button(g, x + w - placesWidth, y, placesWidth, TOOLBAR, tr("places"), state.sidebar, true, tr("tip.places"), () -> state.sidebar = !state.sidebar);
        }
    }

    private void renderAddress(GuiGraphics g, int x, int y, int w) {
        int height = 16;
        g.fill(x, y, x + w, y + height, Ui.PANEL);
        kit.outline(g, x, y, w, height, Ui.LINE);
        List<String> paths = breadcrumbPaths();
        String separator = " > ";
        int available = w - 8;
        int total = 0;
        for (String path : paths) total += font.width(crumbLabel(path)) + font.width(separator);
        if (total > available && paths.size() > 2) {
            List<String> collapsed = new ArrayList<>();
            collapsed.add("/");
            collapsed.add("");
            collapsed.add(paths.get(paths.size() - 1));
            if (font.width(crumbLabel(paths.get(paths.size() - 2))) + font.width(crumbLabel(paths.get(paths.size() - 1))) + 60 < available) {
                collapsed.add(2, paths.get(paths.size() - 2));
            }
            paths = collapsed;
        }
        int cursor = x + 4;
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            boolean last = index == paths.size() - 1;
            String label = path.isEmpty() ? "..." : crumbLabel(path);
            int maxWidth = Math.max(16, x + w - 4 - cursor);
            label = screen.trimToWidth(label, last ? maxWidth : Math.min(maxWidth, Math.max(30, w / 3)));
            int labelWidth = font.width(label);
            if (!path.isEmpty()) {
                boolean target = dragging && dropTarget.equals(path);
                boolean hover = kit.hovered(cursor - 2, y + 2, labelWidth + 4, height - 4);
                if (hover || target) g.fill(cursor - 2, y + 2, cursor + labelWidth + 2, y + height - 2, Ui.HOVER);
                if (target) kit.outline(g, cursor - 2, y + 2, labelWidth + 4, height - 4, Ui.FOCUS);
                kit.hit(cursor - 2, y + 2, labelWidth + 4, height - 4, () -> navigate(path));
                drops.add(new Drop(cursor - 2, y + 2, labelWidth + 4, height - 4, path));
            }
            g.drawString(font, Component.literal(label), cursor, y + 4, last ? Ui.TEXT : path.isEmpty() ? Ui.TEXT_MUTED : Ui.ACCENT, false);
            cursor += labelWidth;
            if (!last) {
                g.drawString(font, Component.literal(separator), cursor, y + 4, Ui.TEXT_MUTED, false);
                cursor += font.width(separator);
            }
        }
    }

    private String crumbLabel(String path) {
        return path.equals("/") ? "/" : path.substring(path.lastIndexOf('/') + 1);
    }

    private List<String> breadcrumbPaths() {
        List<String> paths = new ArrayList<>(List.of("/"));
        StringBuilder current = new StringBuilder();
        for (String part : state.directory.split("/")) {
            if (part.isBlank()) continue;
            current.append('/').append(part);
            paths.add(current.toString());
        }
        return paths;
    }

    private void renderSidebar(GuiGraphics g, int x, int top, int bottom, List<Entry> all) {
        g.fill(x, top, x + SIDEBAR, bottom, Ui.PANEL);
        g.fill(x + SIDEBAR, top, x + SIDEBAR + 1, bottom, Ui.SURFACE);
        g.drawString(font, Component.literal(tr("places")), x + 5, top + 4, Ui.TEXT_STRONG, false);
        g.fill(x + 3, top + 14, x + SIDEBAR - 3, top + 15, Ui.SURFACE);
        int rowY = top + 18;
        for (String place : PLACES) {
            if (!place.equals("/") && find(place) == null) continue;
            if (rowY + 15 > bottom - 36) break;
            boolean current = state.directory.equals(place);
            boolean locked = ComputerFileSystem.isProtected(place);
            boolean target = dragging && dropTarget.equals(place);
            boolean hover = kit.hovered(x + 2, rowY, SIDEBAR - 4, 15);
            if (current || hover || target) g.fill(x + 2, rowY, x + SIDEBAR - 2, rowY + 15, current ? Ui.SELECTED_FILL : Ui.HOVER);
            if (target) kit.outline(g, x + 2, rowY, SIDEBAR - 4, 15, Ui.FOCUS);
            kit.icon(g, icon(locked ? "lock" : "folder"), x + 5, rowY + 2, 12, true);
            String label = tr("place." + (place.equals("/") ? "root" : place.substring(1)));
            g.drawString(font, Component.literal(screen.trimToWidth(label, SIDEBAR - 26)), x + 20, rowY + 4, current ? Ui.TEXT : Ui.ACCENT, false);
            kit.hit(x + 2, rowY, SIDEBAR - 4, 15, () -> navigate(place));
            if (!locked) drops.add(new Drop(x + 2, rowY, SIDEBAR - 4, 15, place));
            rowY += 16;
        }
        int used = 0;
        for (Entry entry : all) if (!entry.directory()) used += entry.size();
        int limit = ComputerFileSystem.MAX_TOTAL_SIZE;
        int base = bottom - 34;
        g.drawString(font, Component.literal(tr("storage")), x + 5, base, Ui.TEXT_MUTED, false);
        int barX = x + 5;
        int barWidth = SIDEBAR - 10;
        g.fill(barX, base + 10, barX + barWidth, base + 13, Ui.SURFACE);
        int filled = (int) Math.round(barWidth * Math.min(1.0, used / (double) limit));
        g.fill(barX, base + 10, barX + filled, base + 13, used > limit * 0.9 ? Ui.ERROR : Ui.ACCENT);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("storage.bytes", kilobytes(used), kilobytes(limit)), barWidth)), x + 5, base + 15, Ui.TEXT, false);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("storage.items", all.size(), ComputerFileSystem.MAX_NODES), barWidth)), x + 5, base + 25, Ui.TEXT_MUTED, false);
    }

    private void renderList(GuiGraphics g, int x, int top, int w, int bottom, List<Entry> rows, List<Entry> all) {
        String nameHeader = tr("column.name") + (!state.sortBySize ? state.descending ? " v" : " ^" : "");
        String sizeHeader = (state.sortBySize ? state.descending ? "v " : "^ " : "") + tr("column.size");
        int headerColor = Ui.TEXT_MUTED;
        boolean nameHover = kit.hovered(x, top, w / 2, 12);
        boolean sizeHover = kit.hovered(x + w - 70, top, 70, 12);
        g.drawString(font, Component.literal(nameHeader), x + 19, top + 2, nameHover || !state.sortBySize ? Ui.ACCENT : headerColor, false);
        g.drawString(font, Component.literal(sizeHeader), x + w - 8 - font.width(sizeHeader), top + 2, sizeHover || state.sortBySize ? Ui.ACCENT : headerColor, false);
        g.fill(x, top + 12, x + w, top + 13, Ui.SURFACE);
        listTop = top + 14;
        listBottom = bottom;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW);
        state.maximumScroll = Math.max(0, rows.size() - visibleRows);
        if (state.revealSelected) {
            int index = indexOf(rows, state.selected);
            if (index >= 0) {
                if (index < state.scroll) state.scroll = index;
                if (index >= state.scroll + visibleRows) state.scroll = index - visibleRows + 1;
            }
            state.revealSelected = false;
        }
        state.scroll = Math.max(0, Math.min(state.maximumScroll, state.scroll));
        kit.hit(x, listTop, w, listBottom - listTop, this::pressBackground);
        kit.hit(x, top, w - 72, 12, () -> sortBy(false));
        kit.hit(x + w - 70, top, 70, 12, () -> sortBy(true));
        Map<String, Integer> children = childCounts(all);
        int rowWidth = w - 6;
        for (int row = 0; row < visibleRows && state.scroll + row < rows.size(); row++) {
            Entry entry = rows.get(state.scroll + row);
            int rowY = listTop + row * ROW;
            boolean selected = entry.path().equals(state.selected);
            boolean hover = kit.hovered(x, rowY, rowWidth, ROW - 1);
            boolean target = dragging && dropTarget.equals(entry.path());
            boolean dragged = dragging && entry.path().equals(pressPath);
            if (selected || hover || target) g.fill(x, rowY, x + rowWidth, rowY + ROW - 1, selected ? Ui.SELECTED_FILL : Ui.HOVER);
            if (target) kit.outline(g, x, rowY, rowWidth, ROW - 1, Ui.FOCUS);
            boolean locked = ComputerFileSystem.isProtected(entry.path());
            kit.icon(g, icon(locked ? "lock" : entry.directory() ? "folder" : entry.image() ? "image" : "text"), x + 3, rowY + 2, 12, !dragged);
            String size = entry.directory() ? tr("size.items", children.getOrDefault(entry.path(), 0))
                    : entry.image() ? tr("size.painting") : bytes(entry.size());
            int sizeWidth = font.width(size);
            int nameColor = dragged ? Ui.TEXT_DISABLED : selected ? Ui.TEXT : entry.directory() ? Ui.ACCENT : Ui.TEXT;
            g.drawString(font, Component.literal(screen.trimToWidth(entry.name(), Math.max(12, rowWidth - sizeWidth - 30))), x + 19, rowY + 4, nameColor, false);
            g.drawString(font, Component.literal(size), x + rowWidth - 4 - sizeWidth, rowY + 4, Ui.TEXT_MUTED, false);
            kit.hit(x, rowY, rowWidth, ROW - 1, () -> pressRow(entry));
            if (entry.directory() && !locked) drops.add(new Drop(x, rowY, rowWidth, ROW - 1, entry.path()));
        }
        if (rows.isEmpty()) {
            var fileState = ComputerFileSystemClientState.get();
            String title;
            String hint;
            if (!fileState.error().isBlank()) {
                title = tr("empty.error");
                hint = AppKit.fileError(fileState.error());
            } else if (!fileState.success()) {
                title = tr("empty.loading");
                hint = tr("empty.loading_hint");
            } else {
                title = tr("empty.title");
                hint = ComputerFileSystem.isProtected(state.directory) ? tr("empty.locked_hint") : tr("empty.hint");
            }
            g.drawString(font, Component.literal(screen.trimToWidth(title, w - 12)), x + 6, listTop + 6, Ui.TEXT_STRONG, false);
            screen.wrapLimited(g, hint, x + 6, listTop + 19, w - 12, listBottom, Ui.TEXT_MUTED);
        }
        kit.scrollbar(g, x + w - 3, listTop, listBottom - listTop, visibleRows, rows.size(), state.scroll, state.maximumScroll, value -> state.scroll = value);
    }

    private void renderFooter(GuiGraphics g, int x, int y, int w, List<Entry> rows, Entry selected, List<Entry> all) {
        g.fill(x, y, x + w, y + 1, Ui.SURFACE);
        int textY = y + 2;
        String left;
        if (selected != null) {
            String detail = selected.directory() ? tr("details.folder", childCounts(all).getOrDefault(selected.path(), 0))
                    : selected.image() ? tr("details.painting") : tr("details.text", bytes(selected.size()));
            left = selected.name() + " " + detail;
        } else {
            int folders = 0;
            for (Entry entry : rows) if (entry.directory()) folders++;
            left = tr("summary", rows.size() - folders, folders);
        }
        String right = "";
        int rightColor = Ui.TEXT_MUTED;
        if (dragging) {
            right = dropTarget.isEmpty() ? tr("drag.hint") : tr("drag.target", crumbLabel(dropTarget));
            rightColor = dropTarget.isEmpty() ? Ui.TEXT_MUTED : Ui.TEXT;
        } else if (System.currentTimeMillis() < noticeUntil) {
            right = notice;
            rightColor = noticeColor;
        }
        String shownRight = screen.trimToWidth(right, Math.max(10, w / 2));
        int rightWidth = right.isEmpty() ? 0 : font.width(shownRight);
        g.drawString(font, Component.literal(screen.trimToWidth(left, Math.max(10, w - rightWidth - 8))), x, textY, selected != null ? Ui.TEXT : Ui.TEXT_MUTED, false);
        if (!right.isEmpty()) g.drawString(font, Component.literal(shownRight), x + w - rightWidth, textY, rightColor, false);
    }

    private void renderDragGhost(GuiGraphics g) {
        Entry entry = find(pressPath);
        if (entry == null) return;
        String label = screen.trimToWidth(entry.name(), 90);
        int width = font.width(label) + 22;
        int gx = screen.session.mouseX + 8;
        int gy = screen.session.mouseY + 6;
        g.pose().pushPose();
        try {
            g.pose().translate(0.0F, 0.0F, 250.0F);
            g.fill(gx, gy, gx + width, gy + 15, Ui.BACKGROUND);
            kit.outline(g, gx, gy, width, 15, dropTarget.isEmpty() ? Ui.LINE : Ui.FOCUS);
            kit.icon(g, icon(entry.directory() ? "folder" : entry.image() ? "image" : "text"), gx + 3, gy + 2, 12, true);
            g.drawString(font, Component.literal(label), gx + 17, gy + 4, Ui.TEXT, false);
        } finally {
            g.pose().popPose();
        }
    }

    private void renderMenu(GuiGraphics g, int x, int y, int w, int h) {
        kit.suppressHover(false);
        kit.hit(x, y, w, h, () -> menuOpen = false);
        List<MenuItem> items = menuItems();
        int width = 110;
        for (MenuItem item : items) if (item != null) width = Math.max(width, font.width(item.label()) + font.width(item.shortcut()) + 24);
        width = Math.min(width, w);
        int height = 6;
        for (MenuItem item : items) height += item == null ? 5 : MENU_ROW;
        int left = Math.max(x, Math.min(x + w - width, menuX));
        int top = Math.max(y, Math.min(y + h - height, menuY));
        g.pose().pushPose();
        try {
            g.pose().translate(0.0F, 0.0F, 100.0F);
            g.fill(left, top, left + width, top + height, Ui.BACKGROUND);
            screen.box(g, left, top, left + width, top + height, Ui.BORDER_STRONG);
            int rowY = top + 3;
            for (MenuItem item : items) {
                if (item == null) {
                    g.fill(left + 6, rowY + 2, left + width - 6, rowY + 3, Ui.SURFACE);
                    rowY += 5;
                    continue;
                }
                boolean hover = item.enabled() && kit.hovered(left + 2, rowY, width - 4, MENU_ROW);
                if (hover) g.fill(left + 2, rowY, left + width - 2, rowY + MENU_ROW, Ui.HOVER);
                int color = !item.enabled() ? Ui.TEXT_DISABLED : item.danger() ? Ui.ERROR : hover ? Ui.ACCENT : Ui.TEXT;
                g.drawString(font, Component.literal(item.label()), left + 7, rowY + 3, color, false);
                if (!item.shortcut().isEmpty()) {
                    g.drawString(font, Component.literal(item.shortcut()), left + width - 7 - font.width(item.shortcut()), rowY + 3, Ui.TEXT_MUTED, false);
                }
                if (item.enabled()) {
                    Runnable action = item.action();
                    kit.hit(left + 2, rowY, width - 4, MENU_ROW, () -> {
                        menuOpen = false;
                        action.run();
                    });
                }
                rowY += MENU_ROW;
            }
        } finally {
            g.pose().popPose();
        }
    }

    private List<MenuItem> menuItems() {
        List<MenuItem> items = new ArrayList<>();
        Entry entry = menuPath.isEmpty() ? null : find(menuPath);
        if (entry != null) {
            boolean mutable = mutable(entry.path());
            items.add(new MenuItem(tr("menu.open"), tr("key.open"), true, false, () -> activate(entry)));
            items.add(null);
            items.add(new MenuItem(tr("menu.rename"), tr("key.rename"), mutable, false, () -> rename(entry)));
            items.add(new MenuItem(tr("menu.move"), tr("key.move"), mutable, false, () -> move(entry)));
            items.add(new MenuItem(tr("menu.copy_path"), tr("key.copy_path"), true, false, () -> copyPath(entry.path())));
            items.add(null);
            items.add(new MenuItem(tr("menu.delete"), tr("key.delete"), mutable, true, () -> delete(entry)));
        } else {
            boolean writable = !ComputerFileSystem.isProtected(state.directory);
            items.add(new MenuItem(tr("menu.new_folder"), tr("key.new_folder"), writable, false, this::newFolder));
            items.add(new MenuItem(tr("menu.new_text"), "", writable, false, this::newTextFile));
            items.add(new MenuItem(tr("menu.new_image"), "", writable, false, this::newPainting));
            items.add(null);
            items.add(new MenuItem(tr("menu.copy_path"), "", true, false, () -> copyPath(state.directory)));
            items.add(new MenuItem(tr("menu.refresh"), tr("key.refresh"), true, false, this::refresh));
        }
        return items;
    }

    private void openMenu(String path) {
        menuOpen = true;
        menuPath = path;
        menuX = (int) kit.clickX();
        menuY = (int) kit.clickY();
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        kit.click(mouseX, mouseY, button);
        return true;
    }

    private void pressBackground() {
        state.selected = "";
        if (kit.clickButton() == 1) openMenu("");
    }

    private void pressRow(Entry entry) {
        int button = kit.clickButton();
        state.selected = entry.path();
        if (button == 1) {
            openMenu(entry.path());
            return;
        }
        if (button != 0) return;
        long now = System.currentTimeMillis();
        boolean activate = entry.path().equals(lastClickPath) && now - lastClickTime <= 350L;
        lastClickPath = entry.path();
        lastClickTime = now;
        if (activate) {
            lastClickPath = "";
            activate(entry);
            return;
        }
        if (mutable(entry.path())) {
            pressPath = entry.path();
            pressX = kit.clickX();
            pressY = kit.clickY();
        }
    }

    void drag(double mouseX, double mouseY) {
        if (kit.draggingScrollbar()) {
            kit.drag(mouseY);
            return;
        }
        if (pressPath.isEmpty() || dialog.active()) return;
        if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) > 4) dragging = true;
        if (!dragging) return;
        dropTarget = "";
        for (Drop drop : drops) {
            if (ComputerScreen.inside(drop.x(), drop.y(), drop.w(), drop.h(), mouseX, mouseY) && dropAllowed(pressPath, drop.path())) {
                dropTarget = drop.path();
                break;
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoScroll > 90L) {
            if (mouseY < listTop + 4 && mouseY > listTop - 14) {
                state.scroll = Math.max(0, state.scroll - 1);
                lastAutoScroll = now;
            } else if (mouseY > listBottom - 4 && mouseY < listBottom + 12) {
                state.scroll = Math.min(state.maximumScroll, state.scroll + 1);
                lastAutoScroll = now;
            }
        }
    }

    void release(double mouseX, double mouseY) {
        kit.release();
        if (dragging && !dropTarget.isEmpty()) moveInto(pressPath, dropTarget);
        cancelDrag();
    }

    private void cancelDrag() {
        dragging = false;
        pressPath = "";
        dropTarget = "";
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (dialog.active()) return dialog.scroll(scrollY);
        state.scroll = Math.max(0, Math.min(state.maximumScroll, state.scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (dialog.active()) return dialog.type(codePoint);
        if (codePoint <= 32 || codePoint == 127) return false;
        long now = System.currentTimeMillis();
        typeBuffer = now - typeTime > 800L ? String.valueOf(codePoint) : typeBuffer + codePoint;
        typeTime = now;
        String prefix = typeBuffer.toLowerCase(Locale.ROOT);
        for (Entry entry : visibleEntries) {
            if (entry.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                state.selected = entry.path();
                state.revealSelected = true;
                break;
            }
        }
        return true;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (dialog.active()) return dialog.key(keyCode, modifiers);
        boolean control = screen.hasControl(modifiers);
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (dragging) {
                cancelDrag();
                return true;
            }
            if (menuOpen) {
                menuOpen = false;
                return true;
            }
            if (!state.selected.isEmpty()) {
                state.selected = "";
                return true;
            }
            return false;
        }
        menuOpen = false;
        Entry selected = find(state.selected);
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                if (alt) goUp();
                else step(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                step(1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (!visibleEntries.isEmpty()) select(visibleEntries.get(0));
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (!visibleEntries.isEmpty()) select(visibleEntries.get(visibleEntries.size() - 1));
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (selected != null) activate(selected);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                goUp();
                return true;
            }
            case GLFW.GLFW_KEY_F2 -> {
                if (selected != null) rename(selected);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selected != null) delete(selected);
                return true;
            }
            case GLFW.GLFW_KEY_F5 -> {
                refresh();
                return true;
            }
            default -> { }
        }
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_N -> newFolder();
                case GLFW.GLFW_KEY_M -> {
                    if (selected != null) move(selected);
                }
                case GLFW.GLFW_KEY_C -> copyPath(selected != null ? selected.path() : state.directory);
                case GLFW.GLFW_KEY_R -> {
                    if (selected != null) rename(selected);
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void step(int amount) {
        if (visibleEntries.isEmpty()) return;
        int index = indexOf(visibleEntries, state.selected);
        int next = index < 0 ? amount > 0 ? 0 : visibleEntries.size() - 1 : Math.max(0, Math.min(visibleEntries.size() - 1, index + amount));
        select(visibleEntries.get(next));
    }

    private void select(Entry entry) {
        state.selected = entry.path();
        state.revealSelected = true;
    }

    private void activate(Entry entry) {
        if (entry == null) return;
        if (entry.directory()) {
            navigate(entry.path());
            return;
        }
        ComputerNetworking.openFile(entry.path());
        screen.open(entry.image() ? "PAINT" : "TEXT");
    }

    private void navigate(String path) {
        if (dragging) return;
        state.directory = path;
        state.selected = "";
        state.scroll = 0;
        menuOpen = false;
    }

    private void goUp() {
        if (state.directory.equals("/")) return;
        String previous = state.directory;
        navigate(screen.parentDirectory(state.directory));
        state.selected = previous;
        state.revealSelected = true;
    }

    private void refresh() {
        ComputerNetworking.listFiles();
        notice(tr("notice.refreshed"), Ui.TEXT_MUTED);
    }

    private void sortBy(boolean size) {
        if (state.sortBySize == size) state.descending = !state.descending;
        else {
            state.sortBySize = size;
            state.descending = false;
        }
    }

    private void copyPath(String path) {
        Minecraft.getInstance().keyboardHandler.setClipboard(path);
        notice(tr("notice.copied"), Ui.TEXT);
    }

    private void newFolder() {
        if (ComputerFileSystem.isProtected(state.directory)) {
            notice(tr("notice.locked"), Ui.ERROR);
            return;
        }
        dialog.openName(tr("new_folder.title"), uniqueName(tr("new_folder.default")), input -> {
            String error = validateName(input);
            if (!error.isEmpty()) return error;
            String target = join(state.directory, input);
            if (find(target) != null) return tr("error.exists");
            pending("create_folder", target);
            ComputerNetworking.createDirectory(target);
            return "";
        });
    }

    private void newTextFile() {
        if (ComputerFileSystem.isProtected(state.directory)) {
            notice(tr("notice.locked"), Ui.ERROR);
            return;
        }
        dialog.openName(tr("new_text.title"), uniqueName(tr("new_text.default")), input -> {
            String name = input.contains(".") ? input : input + ".txt";
            String error = validateName(name);
            if (!error.isEmpty()) return error;
            if (name.toLowerCase(Locale.ROOT).endsWith(AntPaintFilename.EXTENSION)) return tr("error.text_extension");
            String target = join(state.directory, name);
            if (find(target) != null) return tr("error.exists");
            pending("create_text", target);
            ComputerNetworking.createFile(target, "");
            return "";
        });
    }

    private void newPainting() {
        if (ComputerFileSystem.isProtected(state.directory)) {
            notice(tr("notice.locked"), Ui.ERROR);
            return;
        }
        String initial = uniqueName(tr("new_image.default") + AntPaintFilename.EXTENSION);
        initial = initial.substring(0, initial.length() - AntPaintFilename.EXTENSION.length());
        dialog.openName(tr("new_image.title"), initial, input -> {
            String base = input.toLowerCase(Locale.ROOT).endsWith(AntPaintFilename.EXTENSION)
                    ? input.substring(0, input.length() - AntPaintFilename.EXTENSION.length()).trim() : input.trim();
            if (base.isEmpty()) return tr("error.blank");
            if (!AntPaintFilename.isValid(base)) return tr("error.painting_name");
            String name = AntPaintFilename.normalize(base);
            String target = join(state.directory, name);
            if (target.length() > ComputerFileSystem.MAX_PATH_LENGTH) return tr("error.long");
            if (find(target) != null) return tr("error.exists");
            AntPaintFile file = new AntPaintFile(UUID.randomUUID().toString(), name, new AntPaintCanvas(), 0L, 0L);
            pending("create_image", target);
            ComputerNetworking.createFile(target, Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(file)));
            return "";
        });
    }

    private void rename(Entry entry) {
        if (entry == null || !mutable(entry.path())) return;
        dialog.openName(tr("rename.title"), entry.name(), input -> {
            String error = validateName(input);
            if (!error.isEmpty()) return error;
            if (!entry.directory()) {
                boolean painting = input.toLowerCase(Locale.ROOT).endsWith(AntPaintFilename.EXTENSION);
                if (painting != entry.image()) return tr("error.keep_type");
                if (painting && !AntPaintFilename.isValid(input.substring(0, input.length() - AntPaintFilename.EXTENSION.length()))) return tr("error.painting_name");
            }
            String target = join(screen.parentDirectory(entry.path()), input);
            if (target.equals(entry.path())) return "";
            if (find(target) != null && !target.equalsIgnoreCase(entry.path())) return tr("error.exists");
            pending("rename", target);
            ComputerNetworking.moveFile(entry.path(), target);
            return "";
        });
    }

    private void move(Entry entry) {
        if (entry == null || !mutable(entry.path())) return;
        dialog.openFolderPicker(tr("move.title", entry.name()), screen.parentDirectory(entry.path()),
                folder -> dropAllowed(entry.path(), folder), folder -> moveInto(entry.path(), folder));
    }

    private void moveInto(String source, String folder) {
        if (!dropAllowed(source, folder)) return;
        String name = source.substring(source.lastIndexOf('/') + 1);
        String target = join(folder, name);
        if (target.length() > ComputerFileSystem.MAX_PATH_LENGTH) {
            notice(tr("error.long"), Ui.ERROR);
            return;
        }
        if (find(target) != null) {
            notice(tr("notice.move_exists", name), Ui.ERROR);
            return;
        }
        pending("move", target);
        ComputerNetworking.moveFile(source, target);
    }

    private void delete(Entry entry) {
        if (entry == null || !mutable(entry.path())) return;
        int inside = 0;
        if (entry.directory()) {
            for (Entry other : entries()) if (other.path().startsWith(entry.path() + "/")) inside++;
        }
        if (inside > 0) {
            dialog.openConfirm(tr("delete.title"), tr("delete.folder_message", entry.name(), inside), tr("delete.confirm"), () -> {
                pending("delete", entry.path());
                ComputerNetworking.deleteTree(entry.path());
            });
        } else {
            dialog.openConfirm(tr("delete.title"), tr("delete.message", entry.name()), tr("delete.confirm"), () -> {
                pending("delete", entry.path());
                ComputerNetworking.deleteFile(entry.path());
            });
        }
    }

    private boolean dropAllowed(String source, String folder) {
        if (source.isEmpty() || folder.isEmpty()) return false;
        if (ComputerFileSystem.isProtected(folder) || !mutable(source)) return false;
        if (!folder.equals("/")) {
            Entry target = find(folder);
            if (target == null || !target.directory()) return false;
        }
        if (folder.equals(source) || folder.startsWith(source + "/")) return false;
        return !screen.parentDirectory(source).equals(folder);
    }

    private boolean mutable(String path) {
        return !path.isBlank() && !path.equals("/") && !ComputerFileSystem.isProtected(path) && find(path) != null;
    }

    private String validateName(String name) {
        if (name.isBlank()) return tr("error.blank");
        if (name.contains("/") || name.contains("\\") || name.startsWith(".") || name.contains("..")) return tr("error.invalid");
        if (join(state.directory, name).length() > ComputerFileSystem.MAX_PATH_LENGTH) return tr("error.long");
        return "";
    }

    private String uniqueName(String name) {
        if (find(join(state.directory, name)) == null) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int number = 2; number < 100; number++) {
            String candidate = base + " " + number + extension;
            if (find(join(state.directory, candidate)) == null) return candidate;
        }
        return name;
    }

    private static String join(String folder, String name) {
        return folder.equals("/") ? "/" + name : folder + "/" + name;
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

    private List<Entry> entries() {
        List<FileListing.Line> lines = FileListing.lines();
        if (lines == entrySource) return entryCache;
        List<Entry> entries = new ArrayList<>();
        Map<String, Entry> paths = new HashMap<>();
        for (FileListing.Line line : lines) {
            int size = 0;
            if (line.size() != null) {
                try {
                    size = Integer.parseInt(line.size().trim());
                } catch (NumberFormatException ignored) {
                }
            }
            Entry entry = new Entry(line.type(), line.path(), line.path().substring(line.path().lastIndexOf('/') + 1), size);
            entries.add(entry);
            paths.putIfAbsent(entry.path(), entry);
        }
        entrySource = lines;
        entryCache = List.copyOf(entries);
        entriesByPath = paths;
        return entryCache;
    }

    private List<FileListing.Line> entrySource;
    private List<Entry> entryCache = List.of();
    private Map<String, Entry> entriesByPath = Map.of();

    private List<Entry> rows(List<Entry> all) {
        Comparator<Entry> order = state.sortBySize
                ? Comparator.comparingInt(Entry::size).thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT))
                : Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT));
        if (state.descending) order = order.reversed();
        Comparator<Entry> folders = Comparator.comparing(entry -> !entry.directory());
        return all.stream().filter(entry -> isDirectChild(entry.path(), state.directory)).sorted(folders.thenComparing(order)).toList();
    }

    private Map<String, Integer> childCounts(List<Entry> all) {
        Map<String, Integer> counts = new HashMap<>();
        for (Entry entry : all) counts.merge(screen.parentDirectory(entry.path()), 1, Integer::sum);
        return counts;
    }

    private Entry find(String path) {
        if (path == null || path.isEmpty()) return null;
        entries();
        return entriesByPath.get(path);
    }

    private static int indexOf(List<Entry> entries, String path) {
        for (int index = 0; index < entries.size(); index++) if (entries.get(index).path().equals(path)) return index;
        return -1;
    }

    private static boolean isDirectChild(String path, String directory) {
        if (path.equals(directory)) return false;
        String prefix = directory.equals("/") ? "/" : directory + "/";
        return path.startsWith(prefix) && path.indexOf('/', prefix.length()) < 0;
    }

    private String bytes(int size) {
        if (size < 1024) return tr("size.bytes", size);
        return tr("size.kilobytes", String.format(Locale.ROOT, "%.1f", size / 1024.0));
    }

    private static String kilobytes(int size) {
        double value = size / 1024.0;
        return value >= 10 || value == Math.floor(value) ? String.valueOf((int) Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private String tr(String key, Object... arguments) {
        return Component.translatable("computer.antos.files." + key, arguments).getString();
    }

    private record Entry(String type, String path, String name, int size) {
        boolean directory() {
            return type.equals("DIRECTORY");
        }

        boolean image() {
            return type.equals("IMAGE");
        }
    }

    private record Drop(int x, int y, int w, int h, String path) { }

    private record MenuItem(String label, String shortcut, boolean enabled, boolean danger, Runnable action) { }

    static final class State {
        int scroll;
        int maximumScroll;
        boolean revealSelected;
        boolean sidebar = com.craisinlord.antos.config.AntOSSettings.preferences().explorerPlaces;
        boolean sortBySize = com.craisinlord.antos.config.AntOSSettings.preferences().explorerSortBySize;
        boolean descending;
        String directory = "/";
        String selected = "";
        String pendingKind = "";
        String pendingPath = "";
        int pendingSerial;
    }

    JsonObject saveResume() {
        JsonObject json = new JsonObject();
        json.addProperty("directory", state.directory);
        json.addProperty("selected", state.selected);
        return json;
    }

    void loadResume(JsonObject json) {
        String directory = ComputerResume.string(json, "directory");
        if (!directory.startsWith("/")) return;
        state.directory = directory;
        state.selected = ComputerResume.string(json, "selected");
        state.revealSelected = !state.selected.isEmpty();
    }

}
