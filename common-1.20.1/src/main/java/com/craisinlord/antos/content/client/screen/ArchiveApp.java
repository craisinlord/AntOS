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
    private final java.util.IdentityHashMap<ComputerGuideData.Entry, String> searchText = new java.util.IdentityHashMap<>();
    private final java.util.Map<String, List<net.minecraft.util.FormattedCharSequence>> detailLines = new java.util.HashMap<>();
    private Object textLanguage;
    private String textPlayer;
    private long textRevision = -1;
    private int detailWidth = -1;
    private List<ComputerGuideData.Entry> filterSource;
    private String filterQuery;
    private ResourceLocation filterFolder;
    private List<ComputerGuideData.Entry> browseSource;
    private List<BrowseItem> browseResult;
    private List<ComputerGuideData.Entry> filterResult;
    private final List<GalleryButton> galleryButtons = new java.util.ArrayList<>();
    private final List<BreadcrumbButton> breadcrumbButtons = new java.util.ArrayList<>();

    void select(ComputerGuideData.Entry entry) {
        state.entryId = entry.id();
        state.searchFocused = false;
        state.detailScroll = 0;
        ComputerNetworking.recordArchiveViewed(entry.id());
        if (!entry.structureId().isBlank() || !entry.locatorId().isBlank()) {
            com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.searching(entry.dimensionId(), entry.structureId().isBlank());
            ComputerNetworking.locateStructure(entry.id());
        }
    }

    void drag(double mouseY) {
        if (scrollbarDragging != null) updateScrollbar(scrollbarDragging, mouseY);
    }

    void release() {
        scrollbarDragging = null;
    }

    void render(GuiGraphics g, int x, int y, int w, int h, boolean maximized) {
        List<ResourceLocation> diskIds = screen.workspaceDiskIds();
        List<ResourceLocation> unlocked = com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get();
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(diskIds, unlocked);

        List<BrowseItem> items = browseItems(entries);
        galleryButtons.clear();
        breadcrumbButtons.clear();
        ComputerGuideData.Entry selected = null;
        if (state.entryId != null) {
            selected = ComputerGuideData.availableEntry(diskIds, unlocked, state.entryId);
            if (selected == null) state.entryId = null;
        }

        if (selected != null) {
            screen.archiveGreenTint = selected.greenTint();
            renderBreadcrumbs(g, x, y, w);
            g.fill(x, y + 16, x + w, y + 18, GREEN);
            g.drawString(font, Component.literal(screen.trimToWidth(AntOSPlayerText.apply(ComputerGuideData.archiveTitle(selected).getString()), w)), x, y + 27, GREEN, false);
            int contentBottom = y + h - 20;
            int contentTop = y + 36;
            screen.enableComputerScissor(g, x, contentTop, x + w, contentBottom);
            int line = y + 43 - state.detailScroll * 11;
            line = screen.wrap(g, selected.type().toUpperCase() + " // " + selected.category().toUpperCase(), x, line, w, PALE_GREEN) + 5;
            String heroEntity = !selected.entityId().isBlank() ? selected.entityId()
                    : selected.itemId().isBlank() && selected.recipeId().isBlank() ? selected.coverEntityId() : "";
            if (!heroEntity.isBlank()) line = visible(line, 96, contentTop, contentBottom)
                    ? screen.renderArchivePreview(g, "", heroEntity, "", x, line, w, selected.rotation(), selected.renderScale()) + 2 : line + 98;
            for (String descriptionPart : selected.descriptionKeys()) {
                line = renderDescriptionPart(g, descriptionPart, x, line, w, contentTop, contentBottom);
            }
            line = renderGallery(g, selected, entries, x, line, w, contentTop, contentBottom);
            if (!selected.itemId().isBlank()) line = visible(line, 48, contentTop, contentBottom)
                    ? screen.renderArchivePreview(g, selected.itemId(), "", "", x, line, w) + 2 : line + 50;
            if (!selected.enchantmentId().isBlank()) line = visible(line, 48, contentTop, contentBottom)
                    ? screen.renderArchivePreview(g, "", "", selected.enchantmentId(), x, line, w) + 2 : line + 50;
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

        renderBreadcrumbs(g, x, y, w - 8);
        g.fill(x, y + 14, x + w - 8, y + 35, BLACK);
        screen.box(g, x, y + 14, x + w - 8, y + 35, state.searchFocused ? GREEN : PALE_GREEN);
        String searchText = state.search.isBlank() && !state.searchFocused ? "SEARCH TITLE OR DESCRIPTION" : state.search;
        g.drawString(font, Component.literal(screen.trimToWidth(searchText + (state.searchFocused && screen.caretVisible() ? "|" : ""), w - 16)), x + 6, y + 20,
                state.search.isBlank() ? PALE_GREEN : GREEN, false);
        if (entries.isEmpty()) {
            g.drawString(font, Component.literal("NO ARCHIVE DATA"), x, y + 52, PALE_GREEN, false);
            return;
        }
        if (items.isEmpty()) {
            g.drawString(font, Component.literal("NO MATCHING ENTRIES"), x, y + 52, PALE_GREEN, false);
            return;
        }
        int columns = maximized ? 3 : 1;
        int cellHeight = maximized ? 72 : 38;
        int thumbnailSize = maximized ? 44 : 28;
        int listTop = y + 40;
        int visibleRows = Math.max(1, (h - 58) / cellHeight);
        int totalRows = (items.size() + columns - 1) / columns;
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
                if (index >= items.size()) break;
                BrowseItem item = items.get(index);
                ComputerGuideData.Entry entry = item.entry();
                int cellWidth = (w - 8) / columns;
                int cellX = x + column * cellWidth;
                int thumbX = maximized ? cellX + (cellWidth - thumbnailSize) / 2 : cellX + 3;
                int thumbY = maximized ? rowTop + 2 : rowTop + 4;
                int textX = maximized ? cellX + 3 : cellX + thumbnailSize + 10;
                int textY = maximized ? rowTop + thumbnailSize + 5 : rowTop + 7;
                boolean hover = screen.inside(cellX, rowTop, cellWidth - 2, cellHeight - 2, screen.session.archiveMouseX, screen.session.archiveMouseY);
                if (hover) screen.drawHover(g, cellX, rowTop, cellWidth - 2, cellHeight - 2);
                g.fill(thumbX, thumbY, thumbX + thumbnailSize, thumbY + thumbnailSize, BLACK);
                if (entry != null) renderThumbnail(g, entry, thumbX + thumbnailSize / 2, thumbY + thumbnailSize / 2, hover, thumbnailSize);
                else renderFolderThumbnail(g, item.folder(), thumbX, thumbY, thumbnailSize);
                int textWidth = maximized ? cellWidth - 8 : cellWidth - thumbnailSize - 12;
                g.drawString(font, Component.literal(screen.trimToWidth(item.title(), textWidth)), textX, textY, GREEN, false);
                g.drawString(font, Component.literal(screen.trimToWidth(state.search.isBlank() && entry != null ? entry.category().toUpperCase(Locale.ROOT) : item.subtitle(), textWidth)), textX, textY + 12, PALE_GREEN, false);
            }
        }
        int shownStart = startRow * columns;
        int shownEnd = Math.min(items.size(), (startRow + visibleRows) * columns);
        if (totalRows > visibleRows) g.drawString(font, Component.literal("SCROLL " + (shownStart + 1) + "-" + shownEnd + " / " + items.size()), x, y + h - 12, PALE_GREEN, false);
        else g.drawString(font, Component.translatable("computer.antos.archive.open"), x, y + h - 12, PALE_GREEN, false);
    }

    private static String folderPathTitle(ResourceLocation folderId) {
        return ComputerGuideData.archiveFolderPath(folderId).stream()
                .map(ComputerGuideData::archiveFolder).map(folder -> AntOSPlayerText.apply(ComputerGuideData.archiveFolderTitle(folder).getString()))
                .collect(java.util.stream.Collectors.joining(" > "));
    }

    private void renderBreadcrumbs(GuiGraphics g, int x, int y, int w) {
        int cursor = x;
        int separatorWidth = font.width(" > ");
        if (state.entryId != null || state.folderId != null) {
            g.drawString(font, Component.literal("<"), cursor, y, GREEN, false);
            breadcrumbButtons.add(new BreadcrumbButton(cursor, y, cursor + 10, y + 13, null, true));
            cursor += 14;
        }
        List<ResourceLocation> path = new java.util.ArrayList<>();
        path.add(null);
        path.addAll(ComputerGuideData.archiveFolderPath(state.folderId));
        for (int index = 0; index < path.size(); index++) {
            ResourceLocation folderId = path.get(index);
            String title = folderId == null ? Component.translatable("computer.antos.app.archive.name").getString()
                    : AntOSPlayerText.apply(ComputerGuideData.archiveFolderTitle(ComputerGuideData.archiveFolder(folderId)).getString());
            int remaining = path.size() - index;
            int available = Math.max(1, x + w - cursor - (remaining - 1) * separatorWidth);
            String label = screen.trimToWidth(title, Math.max(1, available / remaining));
            int width = font.width(label);
            boolean hover = screen.inside(cursor, y, width, 13, screen.session.archiveMouseX, screen.session.archiveMouseY);
            g.drawString(font, Component.literal(label), cursor, y, hover ? GREEN : PALE_GREEN, false);
            breadcrumbButtons.add(new BreadcrumbButton(cursor, y, cursor + width, y + 13, folderId, false));
            cursor += width;
            if (index + 1 < path.size()) {
                g.drawString(font, Component.literal(" > "), cursor, y, PALE_GREEN, false);
                cursor += separatorWidth;
            }
        }
    }

    private boolean clickBreadcrumb(double mouseX, double mouseY) {
        for (BreadcrumbButton button : List.copyOf(breadcrumbButtons)) {
            if (mouseX < button.left() || mouseX >= button.right() || mouseY < button.top() || mouseY >= button.bottom()) continue;
            if (button.back()) goBack();
            else navigateTo(button.folder());
            return true;
        }
        return false;
    }

    private void openFolder(ResourceLocation folder) {
        if (ComputerGuideData.archiveFolder(folder) == null || java.util.Objects.equals(state.folderId, folder)) return;
        state.history.add(new BrowseLocation(state.folderId, state.search, state.scroll));
        state.folderId = folder;
        state.entryId = null;
        state.search = "";
        state.scroll = 0;
        state.searchFocused = false;
        scrollbarDragging = null;
    }

    private void navigateTo(ResourceLocation folder) {
        if (ComputerGuideData.archiveFolder(folder) == null) folder = null;
        state.entryId = null;
        state.detailScroll = 0;
        state.searchFocused = false;
        scrollbarDragging = null;
        if (java.util.Objects.equals(state.folderId, folder)) return;
        state.folderId = folder;
        for (int index = state.history.size() - 1; index >= 0; index--) {
            BrowseLocation location = state.history.get(index);
            if (java.util.Objects.equals(location.folder(), folder)) {
                state.history.subList(index, state.history.size()).clear();
                state.search = location.search();
                state.scroll = location.scroll();
                return;
            }
        }
        List<ResourceLocation> path = ComputerGuideData.archiveFolderPath(folder);
        state.history.removeIf(location -> location.folder() != null && !path.contains(location.folder()));
        state.search = "";
        state.scroll = 0;
    }

    private void openParent() {
        ComputerGuideData.ArchiveFolder folder = ComputerGuideData.archiveFolder(state.folderId);
        navigateTo(folder == null ? null : folder.parent());
    }

    private void goBack() {
        if (state.entryId != null) {
            state.entryId = null;
            state.detailScroll = 0;
            state.searchFocused = false;
        } else {
            openParent();
        }
    }

    private List<ComputerGuideData.Entry> filterEntries(List<ComputerGuideData.Entry> entries) {
        validateTextCaches();
        if (filterSource != entries) {
            while (state.folderId != null && entries.stream().noneMatch(entry -> ComputerGuideData.archiveFolderContains(state.folderId, ComputerGuideData.archiveFolderId(entry)))) openParent();
        }
        String query = state.search.trim().toLowerCase(Locale.ROOT);
        if (filterSource == entries && query.equals(filterQuery) && java.util.Objects.equals(filterFolder, state.folderId)) return filterResult;
        if (searchText.size() > entries.size() * 2 + 64) searchText.clear();
        List<ComputerGuideData.Entry> result = entries.stream()
                .filter(entry -> ComputerGuideData.archiveFolderContains(state.folderId, ComputerGuideData.archiveFolderId(entry)))
                .filter(entry -> query.isEmpty() || searchText.computeIfAbsent(entry, this::entrySearchText).contains(query))
                .sorted(java.util.Comparator.comparingInt(ComputerGuideData.Entry::sortOrder)
                        .thenComparing(entry -> AntOSPlayerText.apply(ComputerGuideData.archiveTitle(entry).getString()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.id().toString())).toList();
        filterSource = entries;
        filterQuery = query;
        filterFolder = state.folderId;
        filterResult = result;
        return result;
    }

    private List<BrowseItem> browseItems(List<ComputerGuideData.Entry> entries) {
        List<ComputerGuideData.Entry> filtered = filterEntries(entries);
        if (browseSource == filtered && browseResult != null) return browseResult;
        List<BrowseItem> result = new java.util.ArrayList<>();
        if (state.search.isBlank()) {
            java.util.Map<ResourceLocation, Integer> counts = new java.util.HashMap<>();
            for (ComputerGuideData.Entry entry : filtered) {
                ResourceLocation folder = ComputerGuideData.archiveFolderId(entry);
                for (ResourceLocation ancestor : ComputerGuideData.archiveFolderPath(folder)) counts.merge(ancestor, 1, Integer::sum);
            }
            counts.keySet().stream().map(ComputerGuideData::archiveFolder)
                    .filter(folder -> java.util.Objects.equals(folder.parent(), state.folderId))
                    .sorted(java.util.Comparator.comparingInt(ComputerGuideData.ArchiveFolder::sortOrder)
                            .thenComparing(folder -> AntOSPlayerText.apply(ComputerGuideData.archiveFolderTitle(folder).getString()), String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(folder -> folder.id().toString()))
                    .forEach(folder -> result.add(new BrowseItem(null, folder, counts.get(folder.id()))));
            filtered.stream().filter(entry -> java.util.Objects.equals(ComputerGuideData.archiveFolderId(entry), state.folderId))
                    .forEach(entry -> result.add(new BrowseItem(entry, null, 0)));
        } else {
            filtered.forEach(entry -> result.add(new BrowseItem(entry, null, 0)));
        }
        browseSource = filtered;
        browseResult = List.copyOf(result);
        return browseResult;
    }

    private void renderFolderThumbnail(GuiGraphics g, ComputerGuideData.ArchiveFolder folder, int x, int y, int size) {
        screen.archiveGreenTint = folder.greenTint();
        g.fill(x, y + 2, x + size / 2, y + 6, PALE_GREEN);
        screen.box(g, x, y + 5, x + size, y + size, GREEN);
        screen.renderArchiveAsset(g, folder.iconItem(), "", "", "", x + size / 2, y + size / 2 + 2, size - 8, 0.0F, 1.0F);
    }

    private record BrowseItem(ComputerGuideData.Entry entry, ComputerGuideData.ArchiveFolder folder, int count) {
        String title() {
            return AntOSPlayerText.apply(entry == null ? ComputerGuideData.archiveFolderTitle(folder).getString() : ComputerGuideData.archiveTitle(entry).getString());
        }

        String subtitle() {
            if (entry == null) return Component.translatable("computer.antos.archive.folder_entries", count).getString();
            ComputerGuideData.ArchiveFolder parent = ComputerGuideData.archiveFolder(ComputerGuideData.archiveFolderId(entry));
            return parent == null ? entry.category().toUpperCase(Locale.ROOT)
                    : folderPathTitle(parent.id());
        }
    }

    private void validateTextCaches() {
        Object language = net.minecraft.locale.Language.getInstance();
        String player = AntOSPlayerText.playerName();
        long revision = ComputerGuideData.definitionRevision();
        if (language != textLanguage || !player.equals(textPlayer) || revision != textRevision) {
            textLanguage = language;
            textPlayer = player;
            textRevision = revision;
            searchText.clear();
            detailLines.clear();
            filterSource = null;
            browseSource = null;
            browseResult = null;
            filterQuery = null;
            filterResult = null;
        }
    }

    private static boolean visible(int top, int height, int clipTop, int clipBottom) {
        return top + height > clipTop && top < clipBottom;
    }

    private List<net.minecraft.util.FormattedCharSequence> detailLines(String part, int w) {
        validateTextCaches();
        if (w != detailWidth || detailLines.size() > 512) {
            detailWidth = w;
            detailLines.clear();
        }
        return detailLines.computeIfAbsent(part, key -> font.split(Component.literal(AntOSPlayerText.apply(Component.translatable(key).getString())), w));
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

    private int renderGallery(GuiGraphics g, ComputerGuideData.Entry selected, List<ComputerGuideData.Entry> entries, int x, int y, int w,
                              int clipTop, int clipBottom) {
        List<ComputerGuideData.GalleryItem> gallery = selected.gallery();
        if (gallery.isEmpty()) return y;
        String title = selected.galleryTitleKey().isBlank() ? "GALLERY" : Component.translatable(selected.galleryTitleKey()).getString().toUpperCase(Locale.ROOT);
        y = screen.wrap(g, title + " // " + gallery.size(), x, y + 2, w, PALE_GREEN) + 3;
        int cell = 48;
        int cellWidth = 64;
        int cellHeight = cell + 26;
        int columns = Math.max(1, (w + cellWidth - cell) / cellWidth);
        for (int index = 0; index < gallery.size(); index++) {
            ComputerGuideData.GalleryItem galleryItem = gallery.get(index);
            int cellX = x + (index % columns) * cellWidth;
            int cellY = y + (index / columns) * cellHeight;
            if (!visible(cellY, cellHeight, clipTop, clipBottom)) continue;
            String entityId = galleryEntity(galleryItem);
            String itemId = entityId.isEmpty() ? galleryItem.id() : "";
            ResourceLocation target = galleryTarget(galleryItem.id(), selected, entries);
            boolean hover = target != null && screen.inside(cellX, cellY, cell, cell, screen.session.archiveMouseX, screen.session.archiveMouseY);
            g.fill(cellX, cellY, cellX + cell, cellY + cell, hover ? 0xFF173817 : 0xFF102010);
            if (target != null) screen.box(g, cellX, cellY, cellX + cell, cellY + cell, hover ? GREEN : 0xFF356035);
            screen.renderArchiveAsset(g, itemId, entityId, "", "", cellX + cell / 2, cellY + cell / 2, cell - 6, 0.0F, 1.0F);
            List<net.minecraft.util.FormattedCharSequence> nameLines = font.split(Component.literal(galleryName(galleryItem, entityId)), cellWidth - 4);
            for (int nameLine = 0; nameLine < Math.min(2, nameLines.size()); nameLine++)
                g.drawString(font, nameLines.get(nameLine), cellX, cellY + cell + 3 + nameLine * 10, target != null ? GREEN : PALE_GREEN, false);
            if (target != null && cellY >= clipTop && cellY + cell <= clipBottom)
                galleryButtons.add(new GalleryButton(cellX, cellY, cellX + cell, cellY + cell, target));
        }
        return y + (gallery.size() + columns - 1) / columns * cellHeight + 2;
    }

    private static String galleryEntity(ComputerGuideData.GalleryItem galleryItem) {
        if (galleryItem.item()) return "";
        if (galleryItem.entity()) return galleryItem.id();
        ResourceLocation id = ResourceLocation.tryParse(galleryItem.id());
        return id != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? galleryItem.id() : "";
    }

    private static String galleryName(ComputerGuideData.GalleryItem galleryItem, String entityId) {
        if (!galleryItem.nameKey().isBlank()) return Component.translatable(galleryItem.nameKey()).getString();
        ResourceLocation id = ResourceLocation.tryParse(galleryItem.id());
        if (id == null) return galleryItem.id();
        if (!entityId.isEmpty()) return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? galleryItem.id() : item.getDescription().getString();
    }

    private static ResourceLocation galleryTarget(String id, ComputerGuideData.Entry selected, List<ComputerGuideData.Entry> entries) {
        ComputerGuideData.Entry coverMatch = null;
        for (ComputerGuideData.Entry entry : entries) {
            if (entry == selected || entry.id().equals(selected.id())) continue;
            if (id.equals(entry.entityId()) || id.equals(entry.itemId())) return entry.id();
            if (coverMatch == null && (id.equals(entry.coverEntityId()) || id.equals(entry.coverItemId()))) coverMatch = entry;
        }
        return coverMatch == null ? null : coverMatch.id();
    }

    private int renderDescriptionPart(GuiGraphics g, String part, int x, int y, int w, int clipTop, int clipBottom) {
        if (part.startsWith("@item:")) return visible(y, 48, clipTop, clipBottom) ? screen.renderArchivePreview(g, part.substring(6), "", "", x, y, w) + 2 : y + 50;
        if (part.startsWith("@entity:")) return visible(y, 96, clipTop, clipBottom) ? screen.renderArchivePreview(g, "", part.substring(8), "", x, y, w) + 2 : y + 98;
        if (part.startsWith("@enchantment:")) return visible(y, 48, clipTop, clipBottom) ? screen.renderArchivePreview(g, "", "", part.substring(13), x, y, w) + 2 : y + 50;
        if (part.startsWith("@recipe:")) return screen.renderArchiveRecipe(g, part.substring(8), x, y, w) + 2;
        for (var line : detailLines(part, w)) {
            if (visible(y, 11, clipTop, clipBottom)) g.drawString(font, line, x, y, GREEN, false);
            y += 11;
        }
        return y + 3;
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        List<BrowseItem> entries = browseItems(ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get()));
        int archiveX = x + 8;
        int archiveY = y + 28;
        if (clickBreadcrumb(mouseX, mouseY)) return true;
        if (state.entryId != null) {
            for (GalleryButton galleryButton : List.copyOf(galleryButtons)) {
                if (mouseX < galleryButton.left() || mouseX >= galleryButton.right() || mouseY < galleryButton.top() || mouseY >= galleryButton.bottom()) continue;
                ComputerGuideData.Entry target = ComputerGuideData.availableEntry(screen.workspaceDiskIds(),
                        com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get(), galleryButton.target());
                if (target != null) select(target);
                return true;
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
                BrowseItem opened = entries.get(index);
                if (opened.entry() != null) select(opened.entry());
                else openFolder(opened.folder().id());
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
            int totalRows = (browseItems(entries).size() + columns - 1) / columns;
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
        if (state.entryId == null && state.searchFocused && codePoint >= 32 && state.search.length() < 48) {
            state.search += codePoint;
            state.scroll = 0;
            return true;
        }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (state.entryId != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            goBack();
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
        if (state.folderId != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            openParent();
            return true;
        }
        return false;
    }

    void updateScrollbar(Window window, double mouseY) {
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
        int columns = window.maximized ? 3 : 1;
        int cellHeight = window.maximized ? 72 : 38;
        int visibleRows = Math.max(1, (screen.windowHeight(window) - 34 - 58) / cellHeight);
        int totalRows = (browseItems(entries).size() + columns - 1) / columns;
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

    private record GalleryButton(int left, int top, int right, int bottom, ResourceLocation target) { }

    private record BrowseLocation(ResourceLocation folder, String search, int scroll) { }

    private record BreadcrumbButton(int left, int top, int right, int bottom, ResourceLocation folder, boolean back) { }

    static final class State {
        ResourceLocation entryId;
        ResourceLocation folderId;
        final List<BrowseLocation> history = new java.util.ArrayList<>();
        int scroll;
        String search = "";
        boolean searchFocused;
        int detailScroll;
        int detailMaxScroll;
    }
}
