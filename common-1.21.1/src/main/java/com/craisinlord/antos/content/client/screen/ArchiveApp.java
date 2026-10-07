package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HEIGHT;

final class ArchiveApp extends ComputerApp {
    final State state = new State();

    Window scrollbarDragging;

    void drag(double mouseY) {
        if (scrollbarDragging != null) updateScrollbar(scrollbarDragging, mouseY);
    }

    void release() {
        scrollbarDragging = null;
    }

    void render(GuiGraphics g, int x, int y, int w, int h, boolean maximized) {
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());

        ComputerGuideData.Entry selected = null;
        if (state.entryId != null) {
            selected = entries.stream().filter(entry -> entry.id().equals(state.entryId)).findFirst().orElse(null);
            if (selected == null) state.entryId = null;
        }

        if (selected != null) {
            screen.archiveGreenTint = selected.greenTint();
            g.drawString(font, Component.literal("< BACK // RECOVERED FILES"), x, y, GREEN, false);
            g.fill(x, y + 16, x + w, y + 18, GREEN);
            g.drawString(font, Component.literal(screen.trimToWidth(AntOSPlayerText.apply(ComputerGuideData.archiveTitle(selected).getString()), w)), x, y + 27, GREEN, false);
            int contentBottom = y + h - 20;
            screen.enableComputerScissor(g, x, y + 36, x + w, contentBottom);
            int line = y + 43 - state.detailScroll * 11;
            line = screen.wrap(g, selected.type().toUpperCase() + " // " + selected.category().toUpperCase(), x, line, w, PALE_GREEN) + 5;
            if (!selected.entityId().isBlank()) line = screen.renderArchivePreview(g, "", selected.entityId(), "", x, line, w, selected.rotation(), selected.renderScale()) + 2;
            for (String descriptionPart : selected.descriptionKeys()) {
                line = renderDescriptionPart(g, descriptionPart, x, line, w);
            }
            if (!selected.itemId().isBlank()) line = screen.renderArchivePreview(g, selected.itemId(), "", "", x, line, w) + 2;
            if (!selected.enchantmentId().isBlank()) line = screen.renderArchivePreview(g, "", "", selected.enchantmentId(), x, line, w) + 2;
            if (!selected.recipeId().isBlank()) line = screen.renderArchiveRecipe(g, selected.recipeId(), x, line, w) + 2;
            if (!selected.structureId().isBlank()) {
                line = screen.wrap(g, "STRUCTURE // " + selected.structureId(), x, line, w, PALE_GREEN) + 2;
                line = screen.wrap(g, com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.get(), x, line, w, GREEN) + 2;
            } else if (!selected.locatorId().isBlank()) {
                line = screen.wrap(g, com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.get(), x, line, w, GREEN) + 2;
            }
            if (!selected.dimensionId().isBlank()) line = screen.wrap(g, "DIMENSION // " + selected.dimensionId(), x, line, w, PALE_GREEN);
            g.disableScissor();
            state.detailMaxScroll = Math.max(0, (line + state.detailScroll * 11 - contentBottom + 10) / 11);
            state.detailScroll = Math.max(0, Math.min(state.detailScroll, state.detailMaxScroll));
            return;
        }

        List<ComputerGuideData.Entry> filteredEntries = filterEntries(entries);
        g.drawString(font, Component.literal("RECOVERED FILES // " + filteredEntries.size() + "/" + entries.size()), x, y, GREEN, false);
        g.fill(x, y + 14, x + w - 8, y + 35, BLACK);
        screen.box(g, x, y + 14, x + w - 8, y + 35, state.searchFocused ? GREEN : PALE_GREEN);
        String searchText = state.search.isBlank() && !state.searchFocused ? "SEARCH TITLE OR DESCRIPTION" : state.search;
        g.drawString(font, Component.literal(screen.trimToWidth(searchText + (state.searchFocused && screen.caretVisible() ? "|" : ""), w - 16)), x + 6, y + 20,
                state.search.isBlank() ? PALE_GREEN : GREEN, false);
        if (entries.isEmpty()) {
            g.drawString(font, Component.literal("NO ARCHIVE DATA"), x, y + 28, PALE_GREEN, false);
            return;
        }
        if (filteredEntries.isEmpty()) {
            g.drawString(font, Component.literal("NO MATCHING ENTRIES"), x, y + 52, PALE_GREEN, false);
            return;
        }
        int columns = maximized ? 3 : 1;
        int cellHeight = maximized ? 72 : 38;
        int thumbnailSize = maximized ? 44 : 28;
        int listTop = y + 40;
        int visibleRows = Math.max(1, (h - 58) / cellHeight);
        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        int maximumScroll = Math.max(0, totalRows - visibleRows);
        state.scroll = Math.max(0, Math.min(state.scroll, maximumScroll));
        int startRow = state.scroll;
        if (maximumScroll > 0) {
            int trackTop = listTop;
            int trackHeight = Math.max(1, h - 58);
            int thumbHeight = screen.scrollbarThumbHeight(trackHeight, visibleRows, totalRows);
            int thumbTop = trackTop + (trackHeight - thumbHeight) * startRow / maximumScroll;
            int trackX = x + w - 5;
            g.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, 0xFF173817);
            g.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, GREEN);
        }
        for (int rowIndex = startRow; rowIndex < totalRows && rowIndex < startRow + visibleRows; rowIndex++) {
            int rowTop = listTop + (rowIndex - startRow) * cellHeight;
            for (int column = 0; column < columns; column++) {
                int index = rowIndex * columns + column;
                if (index >= filteredEntries.size()) break;
                ComputerGuideData.Entry entry = filteredEntries.get(index);
                int cellWidth = (w - 8) / columns;
                int cellX = x + column * cellWidth;
                int thumbX = maximized ? cellX + (cellWidth - thumbnailSize) / 2 : cellX + 3;
                int thumbY = maximized ? rowTop + 2 : rowTop + 4;
                int textX = maximized ? cellX + 3 : cellX + thumbnailSize + 10;
                int textY = maximized ? rowTop + thumbnailSize + 5 : rowTop + 7;
                boolean hover = screen.inside(cellX, rowTop, cellWidth - 2, cellHeight - 2, screen.session.archiveMouseX, screen.session.archiveMouseY);
            if (hover) screen.drawHover(g, cellX, rowTop, cellWidth - 2, cellHeight - 2);
                g.fill(thumbX, thumbY, thumbX + thumbnailSize, thumbY + thumbnailSize, BLACK);
                renderThumbnail(g, entry, thumbX + thumbnailSize / 2, thumbY + thumbnailSize / 2, hover, thumbnailSize);
                int textWidth = maximized ? cellWidth - 8 : cellWidth - thumbnailSize - 12;
                g.drawString(font, Component.literal(screen.trimToWidth(AntOSPlayerText.apply(ComputerGuideData.archiveTitle(entry).getString()), textWidth)), textX, textY, GREEN, false);
                g.drawString(font, Component.literal(screen.trimToWidth(entry.category().toUpperCase(), textWidth)), textX, textY + 12, PALE_GREEN, false);
            }
        }
        int shownStart = startRow * columns;
        int shownEnd = Math.min(filteredEntries.size(), (startRow + visibleRows) * columns);
        if (totalRows > visibleRows) g.drawString(font, Component.literal("SCROLL " + (shownStart + 1) + "-" + shownEnd + " / " + filteredEntries.size()), x, y + h - 12, PALE_GREEN, false);
        else g.drawString(font, Component.literal("[ CLICK ENTRY TO OPEN ]"), x, y + h - 12, PALE_GREEN, false);
    }

    private List<ComputerGuideData.Entry> filterEntries(List<ComputerGuideData.Entry> entries) {
        String query = state.search.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return entries;
        return entries.stream().filter(entry -> entrySearchText(entry).contains(query)).toList();
    }

    private String entrySearchText(ComputerGuideData.Entry entry) {
        StringBuilder text = new StringBuilder();
        text.append(ComputerGuideData.archiveTitle(entry).getString()).append(' ');
        if (!entry.subtitleKey().isBlank()) text.append(Component.translatable(entry.subtitleKey()).getString()).append(' ');
        for (String part : entry.descriptionKeys()) {
            if (part.startsWith("@item:") || part.startsWith("@entity:") || part.startsWith("@enchantment:") || part.startsWith("@recipe:")) {
                text.append(part.substring(part.indexOf(':') + 1).replace('_', ' ').replace(':', ' ')).append(' ');
            } else if (part.startsWith("literal:")) {
                text.append(part.substring("literal:".length())).append(' ');
            } else {
                text.append(Component.translatable(part).getString()).append(' ');
            }
        }
        return AntOSPlayerText.apply(text.toString()).toLowerCase(Locale.ROOT);
    }

    private void renderThumbnail(GuiGraphics g, ComputerGuideData.Entry entry, int centerX, int centerY, boolean hover, int size) {
        screen.archiveGreenTint = entry.greenTint();
        int background = hover ? 0xFF173817 : 0xFF102010;
        g.fill(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2, background);
        if (!entry.recipeId().isBlank() && screen.renderArchiveRecipe(g, entry.recipeId(), centerX - size / 2, centerY - size / 2 + size / 5, size, Math.max(6, size / 4))) return;
        String itemId = entry.coverItemId().isBlank() ? entry.itemId() : entry.coverItemId();
        String entityId = entry.coverEntityId().isBlank() ? entry.entityId() : entry.coverEntityId();
        if (!screen.renderArchiveAsset(g, itemId, entityId, entry.enchantmentId(), entry.coverPotionId(), centerX, centerY, size, entry.rotation(), entry.renderScale())) {
            String fallback = !itemId.isBlank() ? itemId : !entry.enchantmentId().isBlank() ? entry.enchantmentId() : entityId;
            g.drawString(font, Component.literal(screen.trimToWidth(fallback.isBlank() ? "NO RENDER" : fallback, size - 4)), centerX - size / 2 + 2, centerY - 4, hover ? GREEN : PALE_GREEN, false);
        }
    }

    private int renderDescriptionPart(GuiGraphics g, String part, int x, int y, int w) {
        if (part.startsWith("@item:")) return screen.renderArchivePreview(g, part.substring(6), "", "", x, y, w) + 2;
        if (part.startsWith("@entity:")) return screen.renderArchivePreview(g, "", part.substring(8), "", x, y, w) + 2;
        if (part.startsWith("@enchantment:")) return screen.renderArchivePreview(g, "", "", part.substring(13), x, y, w) + 2;
        if (part.startsWith("@recipe:")) return screen.renderArchiveRecipe(g, part.substring(8), x, y, w) + 2;
        return screen.wrap(g, Component.literal(AntOSPlayerText.apply(Component.translatable(part).getString())), x, y, w, GREEN) + 3;
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        List<ComputerGuideData.Entry> entries = filterEntries(ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get()));
        int archiveX = x + 8;
        int archiveY = y + 28;
        if (state.entryId != null) {
            if (screen.inside(archiveX, archiveY, w - 16, 20, mouseX, mouseY)) {
                state.entryId = null;
                state.detailScroll = 0;
            }
        } else {
            if (screen.inside(archiveX, archiveY + 14, w - 16, 21, mouseX, mouseY)) {
                state.searchFocused = true;
                return true;
            }
            state.searchFocused = false;
            int archiveContentWidth = w - 16;
            int columns = window.maximized ? 3 : 1;
            int cellHeight = window.maximized ? 72 : 38;
            int visibleRows = Math.max(1, (h - 34 - 58) / cellHeight);
            int totalRows = (entries.size() + columns - 1) / columns;
            if (totalRows > visibleRows && screen.inside(archiveX + archiveContentWidth - 5, archiveY + 40, 5,
                    Math.max(1, h - 34 - 58), mouseX, mouseY)) {
                scrollbarDragging = window;
                updateScrollbar(window, mouseY);
                return true;
            }
            int startRow = Math.max(0, Math.min(state.scroll, Math.max(0, totalRows - visibleRows)));
            int gridTop = archiveY + 40;
            int relativeRow = ((int) mouseY - gridTop) / cellHeight;
            int relativeColumn = Math.min(columns - 1, Math.max(0, ((int) mouseX - archiveX) / Math.max(1, (contentW - 8) / columns)));
            int index = (startRow + relativeRow) * columns + relativeColumn;
            int cellWidth = (contentW - 8) / columns;
            int cellX = archiveX + relativeColumn * cellWidth;
            int rowTop = gridTop + relativeRow * cellHeight;
            if (relativeRow >= 0 && relativeRow < visibleRows && index < entries.size()
                    && screen.inside(cellX, rowTop, cellWidth - 2, cellHeight - 2, mouseX, mouseY)) {
                ComputerGuideData.Entry openedEntry = entries.get(index);
                state.entryId = openedEntry.id();
                state.detailScroll = 0;
                ComputerNetworking.recordArchiveViewed(openedEntry.id());
                if (!openedEntry.structureId().isBlank() || !openedEntry.locatorId().isBlank()) {
                    com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.searching(openedEntry.dimensionId(),
                            openedEntry.structureId().isBlank());
                    ComputerNetworking.locateStructure(openedEntry.id());
                }
            }
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (state.entryId == null) {
            List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                    com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
            int columns = window.maximized ? 3 : 1;
            int cellHeight = window.maximized ? 72 : 38;
            int visibleRows = Math.max(1, (screen.windowHeight(window) - 34 - 58) / cellHeight);
            int totalRows = (filterEntries(entries).size() + columns - 1) / columns;
            int maximum = Math.max(0, totalRows - visibleRows);
            state.scroll = Math.max(0, Math.min(maximum,
                    state.scroll - (int) Math.signum(scrollY)));
        } else {
            state.detailScroll = Math.max(0, Math.min(state.detailMaxScroll,
                    state.detailScroll - (int) Math.signum(scrollY)));
        }
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (state.searchFocused && codePoint >= 32 && state.search.length() < 48) {
            state.search += codePoint;
            state.scroll = 0;
            return true;
        }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (state.entryId != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            state.entryId = null;
            state.detailScroll = 0;
            return true;
        }
        if (state.searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!state.search.isEmpty()) state.search = state.search.substring(0, state.search.length() - 1);
                state.scroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.searchFocused = false;
                return true;
            }
        }
        return false;
    }

    void updateScrollbar(Window window, double mouseY) {
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
        int columns = window.maximized ? 3 : 1;
        int cellHeight = window.maximized ? 72 : 38;
        int visibleRows = Math.max(1, (screen.windowHeight(window) - 34 - 58) / cellHeight);
        int totalRows = (filterEntries(entries).size() + columns - 1) / columns;
        int maximum = Math.max(0, totalRows - visibleRows);
        if (maximum == 0) {
            state.scroll = 0;
            return;
        }
        int trackHeight = Math.max(1, screen.windowHeight(window) - 34 - 58);
        int thumbHeight = screen.scrollbarThumbHeight(trackHeight, visibleRows, totalRows);
        int trackTop = -HEIGHT / 2 + screen.windowLocalY(window) + 68;
        int travel = Math.max(1, trackHeight - thumbHeight);
        double progress = Math.max(0.0, Math.min(1.0, (mouseY - trackTop - thumbHeight / 2.0) / travel));
        state.scroll = (int) Math.round(progress * maximum);
    }

    static final class State {
        ResourceLocation entryId;
        int scroll;
        String search = "";
        boolean searchFocused;
        int detailScroll;
        int detailMaxScroll;
    }
}
