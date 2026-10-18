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

final class ArchiveApp extends ComputerApp {
    final State state = new State();
    final AppKit kit = new AppKit(this);
    private final List<FocusTarget> focusTargets = new java.util.ArrayList<>();
    private final List<BreadcrumbButton> breadcrumbButtons = new java.util.ArrayList<>();
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
    private ArchiveLayout layout;
    private List<BrowseItem> visibleItems = List.of();
    private boolean recovering;
    private boolean breadcrumbMenu;
    private boolean revealFocus;
    private boolean layoutGallery;
    private int layoutFolders = -1;
    private ResourceLocation openedEntry;
    private int menuX;
    private int menuY;
    private int menuW;
    private int menuH;

    private static String tr(String key, Object... args) {
        return Component.translatable("computer.antos.archive." + key, args).getString();
    }

    private static String translated(String key) {
        return key.startsWith("literal:") ? key.substring(8) : Component.translatable(key).getString();
    }

    void select(ComputerGuideData.Entry entry) {
        if (entry.id().equals(state.entryId)) return;
        rememberView();
        state.entryId = entry.id();
        openedEntry = entry.id();
        state.searchFocused = false;
        state.detailScroll = 0;
        state.controlFocus = "content";
        ComputerNetworking.recordArchiveViewed(entry.id());
        if (!entry.structureId().isBlank() || !entry.locatorId().isBlank()) {
            com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.searching(entry.dimensionId(), entry.structureId().isBlank());
            ComputerNetworking.locateStructure(entry.id());
        }
    }

    void drag(double mouseY) {
        kit.drag(mouseY);
    }

    void release() {
        kit.release();
    }

    private void rememberView() {
        if (recovering) return;
        if (state.backHistory.size() >= 64) state.backHistory.remove(0);
        state.backHistory.add(new ViewState(state.folderId, state.entryId, state.search, state.scroll,
                state.detailScroll, state.focusKey, state.gallery, List.copyOf(state.history)));
    }

    private void restoreView(ViewState view) {
        state.folderId = view.folder();
        state.entryId = view.entry();
        openedEntry = view.entry();
        state.search = view.search();
        state.scroll = view.scroll();
        state.detailScroll = view.detailScroll();
        state.focusKey = view.focus();
        state.gallery = view.gallery();
        state.history.clear();
        state.history.addAll(view.history());
        state.searchFocused = false;
        state.controlFocus = "content";
        breadcrumbMenu = false;
        kit.release();
    }

    void render(GuiGraphics g, int x, int y, int w, int h, boolean maximized) {
        List<ResourceLocation> diskIds = screen.workspaceDiskIds();
        List<ResourceLocation> unlocked = com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get();
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(diskIds, unlocked);
        visibleItems = browseItems(entries);
        if (!state.focusKey.isEmpty() && visibleItems.stream().noneMatch(item -> item.key().equals(state.focusKey))) state.focusKey = "";
        ComputerGuideData.Entry selected = ComputerGuideData.availableEntry(diskIds, unlocked, state.entryId);
        if (state.entryId != null && selected == null) {
            state.entryId = null;
            state.detailScroll = 0;
        }
        int folders = (int) visibleItems.stream().filter(item -> item.folder() != null).count();
        if (layout == null || layout.width() != w || layout.height() != h || layoutGallery != state.gallery
                || layoutFolders != folders || layout.cells().size() != visibleItems.size()) {
            layout = ArchiveLayout.create(w, h, state.gallery, folders, visibleItems.size() - folders);
            layoutGallery = state.gallery;
            layoutFolders = folders;
        }
        state.scroll = Math.max(0, Math.min(layout.maximumScroll(), state.scroll));
        if (revealFocus) {
            state.scroll = layout.reveal(focusedIndex(), state.scroll);
            revealFocus = false;
        }
        kit.begin();
        focusTargets.clear();
        breadcrumbButtons.clear();
        kit.suppressHover(breadcrumbMenu);
        renderToolbar(g, x, y, w, selected);
        renderBreadcrumbs(g, x, y + 21, w);
        if (selected != null) renderArticle(g, selected, entries, x, y + ArchiveLayout.BODY_TOP, w - 8, layout.viewportHeight());
        else renderBrowser(g, x, y, w);
        renderFooter(g, x, y + h - 11, w, selected, folders);
        if (breadcrumbMenu) {
            focusTargets.clear();
            kit.suppressHover(false);
            renderBreadcrumbMenu(g, x, y + 38, w);
        }
        FocusTarget focus = focusTargets.stream().filter(target -> target.key().equals(state.controlFocus)).findFirst().orElse(null);
        if (focus != null && !state.controlFocus.equals("search") && !state.controlFocus.equals("content")) kit.outline(g, focus.x(), focus.y(), focus.w(), focus.h(), PALE_GREEN);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderBrowser(GuiGraphics g, int x, int y, int w) {
        int top = y + ArchiveLayout.BODY_TOP;
        int bottom = top + layout.viewportHeight();
        focusTargets.add(new FocusTarget("content", x, top, w - 8, layout.viewportHeight(), () -> state.controlFocus = "content"));
        kit.hit(x, top, w - 8, layout.viewportHeight(), () -> {
            state.controlFocus = "content";
            state.searchFocused = false;
        });
        if (visibleItems.isEmpty()) {
            g.drawString(font, Component.literal(tr(state.search.isBlank() ? "empty" : "no_results")), x + 6, top + 12, PALE_GREEN, false);
            screen.wrap(g, tr(state.search.isBlank() ? "empty_hint" : "no_results_hint"), x + 6, top + 27, w - 22, AppKit.MUTED);
            if (!state.search.isBlank()) kit.button(g, x + 6, top + 52, kit.labelWidth(tr("clear")), 18, tr("clear"), false, true, this::clearSearch);
        }
        screen.enableComputerScissor(g, x, top, x + w - 8, bottom);
        kit.clip(x, top, w - 8, layout.viewportHeight());
        for (ArchiveLayout.Section section : layout.sections()) {
            int sectionY = top + section.y() - state.scroll;
            if (!visible(sectionY, ArchiveLayout.HEADING, top, bottom)) continue;
            String label = tr(section.folders() ? "folders" : state.search.isBlank() ? "entries" : "results");
            g.drawString(font, Component.literal(label), x + 3, sectionY + 3, AppKit.MUTED, false);
            g.fill(x + 8 + font.width(label), sectionY + 7, x + w - 12, sectionY + 8, AppKit.SOFT);
        }
        for (ArchiveLayout.Cell cell : layout.cells()) {
            int cellY = top + cell.y() - state.scroll;
            if (!visible(cellY, cell.height(), top, bottom)) continue;
            BrowseItem item = visibleItems.get(cell.index());
            renderCell(g, item, x + cell.x(), cellY, cell.width() - 3, cell.height() - 2);
        }
        kit.unclip();
        g.disableScissor();
        kit.scrollbar(g, x + w - 4, top, layout.viewportHeight(), layout.viewportHeight(), layout.contentHeight(),
                state.scroll, layout.maximumScroll(), value -> state.scroll = value);
    }

    private void renderCell(GuiGraphics g, BrowseItem item, int x, int y, int w, int h) {
        boolean hover = kit.hovered(x, y, w, h);
        boolean focused = item.key().equals(state.focusKey) && state.controlFocus.equals("content");
        if (state.gallery || hover || focused) g.fill(x, y, x + w, y + h, hover ? ComputerScreen.HOVER_FILL : AppKit.PANEL);
        if (focused) kit.outline(g, x, y, w, h, PALE_GREEN);
        int artSize = state.gallery ? 36 : 26;
        int artX = state.gallery ? x + (w - artSize) / 2 : x + 3;
        int artY = state.gallery ? y + 4 : y + 2;
        if (item.folder() != null) renderFolderThumbnail(g, item.folder(), artX, artY, artSize);
        else renderThumbnail(g, item.entry(), artX + artSize / 2, artY + artSize / 2, hover, artSize);
        int textX = state.gallery ? x + 5 : x + 36;
        int textY = state.gallery ? y + 45 : y + 5;
        String count = item.folder() == null || state.gallery ? "" : tr("count", item.count());
        int countWidth = count.isEmpty() ? 0 : font.width(count) + 14;
        int textWidth = Math.max(8, (state.gallery ? w - 10 : w - 42) - countWidth);
        String title = screen.trimToWidth(item.title(), textWidth);
        g.drawString(font, Component.literal(title), textX, textY, item.folder() == null ? PALE_GREEN : GREEN, false);
        String metadata = !state.search.isBlank() ? item.subtitle() : item.folder() != null
                ? state.gallery ? tr("count", item.count()) : ""
                : item.entry().subtitleKey().isBlank() ? item.entry().category() : AntOSPlayerText.apply(translated(item.entry().subtitleKey()));
        String shownMetadata = screen.trimToWidth(metadata, state.gallery ? w - 10 : w - 42);
        if (!metadata.isBlank()) g.drawString(font, Component.literal(shownMetadata), textX, textY + 11, AppKit.MUTED, false);
        if (!count.isEmpty()) {
            g.drawString(font, Component.literal(count), x + w - 13 - font.width(count), y + 11, AppKit.MUTED, false);
            g.drawString(font, Component.literal(">"), x + w - 8, y + 11, PALE_GREEN, false);
        }
        if (hover && (!title.equals(item.title()) || !shownMetadata.equals(metadata))) kit.tooltip(item.title() + (metadata.isBlank() ? "" : " — " + metadata));
        kit.hit(x, y, w, h, () -> {
            state.focusKey = item.key();
            state.controlFocus = "content";
            if (item.entry() != null) select(item.entry());
            else openFolder(item.folder().id());
        });
    }

    private void renderFooter(GuiGraphics g, int x, int y, int w, ComputerGuideData.Entry selected, int folders) {
        g.fill(x, y, x + w, y + 1, AppKit.SOFT);
        String label = selected != null ? tr("reading", AntOSPlayerText.apply(ComputerGuideData.archiveTitle(selected).getString()))
                : !state.search.isBlank() ? tr("result_count", visibleItems.size()) : tr("summary", folders, visibleItems.size() - folders);
        g.drawString(font, Component.literal(screen.trimToWidth(label, w - 8)), x + 3, y + 3, AppKit.MUTED, false);
    }

    private void renderArticle(GuiGraphics g, ComputerGuideData.Entry entry, List<ComputerGuideData.Entry> entries, int x, int top, int w, int h) {
        state.detailScroll = Math.max(0, Math.min(state.detailScroll, state.detailMaxScroll));
        int bottom = top + h;
        int contentWidth = w - 12;
        int line = top + 5 - state.detailScroll * 11;
        screen.archiveGreenTint = entry.greenTint();
        screen.enableComputerScissor(g, x, top, x + w, bottom);
        kit.clip(x, top, w, h);
        line = screen.wrap(g, entry.category().toUpperCase(Locale.ROOT), x + 5, line, contentWidth, AppKit.MUTED) + 7;
        line = renderArticleTitle(g, AntOSPlayerText.apply(ComputerGuideData.archiveTitle(entry).getString()), x + 5, line, contentWidth) + 5;
        if (!entry.subtitleKey().isBlank()) line = screen.wrap(g, AntOSPlayerText.apply(translated(entry.subtitleKey())), x + 5, line, contentWidth, PALE_GREEN) + 7;
        g.fill(x + 5, line, x + w - 5, line + 1, AppKit.SOFT);
        line += 8;
        String heroEntity = !entry.entityId().isBlank() ? entry.entityId()
                : entry.itemId().isBlank() && entry.recipeId().isBlank() ? entry.coverEntityId() : "";
        if (!heroEntity.isBlank()) line = visible(line, 96, top, bottom)
                ? screen.renderArchivePreview(g, "", heroEntity, "", x + 5, line, contentWidth, entry.rotation(), entry.renderScale()) + 8 : line + 104;
        else if (entry.itemId().isBlank() && entry.recipeId().isBlank() && entry.enchantmentId().isBlank()
                && (!entry.coverItemId().isBlank() || !entry.coverPotionId().isBlank())) {
            if (visible(line, 48, top, bottom)) screen.renderArchiveAsset(g, entry.coverItemId(), "", "", entry.coverPotionId(),
                    x + contentWidth / 2, line + 24, 44, entry.rotation(), entry.renderScale());
            line += 56;
        }
        for (String part : entry.descriptionKeys()) line = renderDescriptionPart(g, part, x + 5, line, contentWidth, top, bottom) + 4;
        line = renderGallery(g, entry, entries, x + 5, line + 3, contentWidth, top, bottom);
        if (!entry.itemId().isBlank()) line = visible(line, 48, top, bottom)
                ? screen.renderArchivePreview(g, entry.itemId(), "", "", x + 5, line, contentWidth) + 8 : line + 56;
        if (!entry.enchantmentId().isBlank()) line = visible(line, 48, top, bottom)
                ? screen.renderArchivePreview(g, "", "", entry.enchantmentId(), x + 5, line, contentWidth) + 8 : line + 56;
        if (!entry.recipeId().isBlank()) line = screen.renderArchiveRecipe(g, entry.recipeId(), x + 5, line, contentWidth) + 8;
        if (!entry.structureId().isBlank() || !entry.locatorId().isBlank()) {
            g.fill(x + 5, line, x + w - 5, line + 1, AppKit.SOFT);
            line = screen.wrap(g, com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.get(), x + 5, line + 8, contentWidth, PALE_GREEN) + 6;
        }
        if (!entry.dimensionId().isBlank()) line = screen.wrap(g, tr("dimension", entry.dimensionId()), x + 5, line, contentWidth, AppKit.MUTED);
        kit.unclip();
        g.disableScissor();
        state.detailMaxScroll = Math.max(0, (line + state.detailScroll * 11 - bottom + 10) / 11);
        state.detailScroll = Math.min(state.detailScroll, state.detailMaxScroll);
        kit.scrollbar(g, x + w + 4, top, h, Math.max(1, h / 11), Math.max(1, h / 11) + state.detailMaxScroll,
                state.detailScroll, state.detailMaxScroll, value -> state.detailScroll = value);
        focusTargets.add(new FocusTarget("content", x, top, w, h, () -> state.controlFocus = "content"));
    }

    private int renderArticleTitle(GuiGraphics g, String title, int x, int y, int w) {
        var lines = font.split(Component.literal(title), Math.max(1, (int) (w / 1.25F)));
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(1.25F, 1.25F, 1.0F);
            for (int index = 0; index < lines.size(); index++) g.drawString(font, lines.get(index), 0, index * 11, GREEN, false);
        } finally {
            g.pose().popPose();
        }
        return y + lines.size() * 14;
    }

    private static String folderPathTitle(ResourceLocation folderId) {
        return ComputerGuideData.archiveFolderPath(folderId).stream().map(ComputerGuideData::archiveFolder)
                .map(folder -> AntOSPlayerText.apply(ComputerGuideData.archiveFolderTitle(folder).getString()))
                .collect(java.util.stream.Collectors.joining(" > "));
    }

    private void control(GuiGraphics g, String key, int x, int y, int w, String label, boolean active, boolean enabled, String tip, Runnable action) {
        Runnable focused = () -> {
            state.controlFocus = key;
            state.searchFocused = false;
            action.run();
        };
        String icon = switch (key) {
            case "back" -> "archive_back";
            case "up" -> "up";
            case "list" -> "archive_list";
            case "grid" -> "archive_grid";
            default -> "";
        };
        if (icon.isBlank()) kit.button(g, x, y, w, 18, label, active, enabled, tip, focused);
        else kit.iconButton(g, x, y, 18, FileExplorerApp.icon(icon), active, enabled, tip, focused);
        if (enabled) focusTargets.add(new FocusTarget(key, x, y, w, 18, focused));
    }

    private void renderToolbar(GuiGraphics g, int x, int y, int w, ComputerGuideData.Entry selected) {
        control(g, "back", x, y, 18, "<", false, selected != null || state.folderId != null || !state.backHistory.isEmpty(), tr("back"), this::goBack);
        control(g, "up", x + 21, y, 18, "^", false, selected != null || state.folderId != null, tr("up"), this::goUp);
        int fieldX = x + 42;
        int fieldW = Math.max(30, w - 84);
        String scope = state.folderId == null ? Component.translatable("computer.antos.app.archive.name").getString() : folderPathTitle(state.folderId);
        kit.field(g, fieldX, y, fieldW - 21, 18, state.search, state.search.length(), state.searchFocused, tr("search", scope));
        if (kit.hovered(fieldX, y, fieldW - 21, 18)) kit.tooltip(tr("search", scope));
        if (selected == null) {
            kit.hit(fieldX, y, fieldW, 18, this::focusSearch);
            focusTargets.add(new FocusTarget("search", fieldX, y, fieldW, 18, this::focusSearch));
        }
        control(g, "clear", fieldX + fieldW - 18, y, 18, "X", false, selected == null && !state.search.isEmpty(), tr("clear"), this::clearSearch);
        control(g, "list", x + w - 39, y, 18, "=", !state.gallery, selected == null, tr("list_view"), () -> setGallery(false));
        control(g, "grid", x + w - 18, y, 18, "+", state.gallery, selected == null, tr("gallery_view"), () -> setGallery(true));
    }

    private void focusSearch() {
        state.searchFocused = true;
        state.controlFocus = "search";
    }

    private void clearSearch() {
        state.search = "";
        state.scroll = 0;
        focusSearch();
    }

    private void setGallery(boolean gallery) {
        if (state.gallery == gallery) return;
        if (state.focusKey.isEmpty() && layout != null) {
            layout.cells().stream().filter(cell -> cell.y() + cell.height() > state.scroll).findFirst()
                    .ifPresent(cell -> state.focusKey = visibleItems.get(cell.index()).key());
        }
        state.gallery = gallery;
        revealFocus = true;
    }

    private void goUp() {
        if (state.entryId != null) {
            ComputerGuideData.Entry entry = ComputerGuideData.entry(state.entryId);
            navigateTo(entry == null ? null : ComputerGuideData.archiveFolderId(entry));
        } else openParent();
    }

    private void renderBreadcrumbs(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 16, AppKit.PANEL);
        kit.outline(g, x, y, w, 16, AppKit.SOFT);
        List<ResourceLocation> path = new java.util.ArrayList<>();
        path.add(null);
        path.addAll(ComputerGuideData.archiveFolderPath(state.folderId));
        int total = path.stream().mapToInt(id -> font.width(crumbTitle(id)) + 14).sum();
        boolean collapsed = total > w - 8 && path.size() > 2;
        int cursor = x + 4;
        if (collapsed) {
            crumb(g, "crumb-root", null, cursor, y, 20, tr("root_short"));
            cursor += 28;
            int menuX = cursor;
            Runnable open = () -> breadcrumbMenu = !breadcrumbMenu;
            kit.hit(menuX, y + 1, 20, 14, open);
            focusTargets.add(new FocusTarget("ancestors", menuX, y + 1, 20, 14, open));
            g.drawString(font, Component.literal("..."), menuX + 1, y + 4, GREEN, false);
            cursor += 28;
            crumb(g, "crumb-current", state.folderId, cursor, y, Math.max(12, x + w - cursor - 4), crumbTitle(state.folderId));
        } else {
            for (int index = 0; index < path.size(); index++) {
                ResourceLocation id = path.get(index);
                String title = crumbTitle(id);
                int remaining = Math.max(12, x + w - cursor - 4);
                int reserve = index + 1 < path.size() ? font.width(crumbTitle(path.get(path.size() - 1))) + 14 : 0;
                int width = Math.min(font.width(title) + 4, Math.max(12, remaining - reserve));
                if (index == path.size() - 1) width = remaining;
                crumb(g, "crumb-" + index, id, cursor, y, width, title);
                cursor += width;
                if (index + 1 < path.size()) {
                    g.drawString(font, Component.literal(">"), cursor + 3, y + 4, AppKit.MUTED, false);
                    cursor += 12;
                }
            }
        }
    }

    private String crumbTitle(ResourceLocation id) {
        return id == null ? Component.translatable("computer.antos.app.archive.name").getString()
                : AntOSPlayerText.apply(ComputerGuideData.archiveFolderTitle(ComputerGuideData.archiveFolder(id)).getString());
    }

    private void crumb(GuiGraphics g, String key, ResourceLocation folder, int x, int y, int w, String title) {
        boolean current = java.util.Objects.equals(folder, state.folderId);
        boolean hover = kit.hovered(x, y + 1, w, 14);
        if (hover) g.fill(x, y + 1, x + w, y + 15, ComputerScreen.HOVER_FILL);
        String label = screen.trimToWidth(title, Math.max(1, w - 4));
        g.drawString(font, Component.literal(label), x + 2, y + 4, current ? PALE_GREEN : GREEN, false);
        if (hover) kit.tooltip(folderPathTitle(folder).isBlank() ? title : folderPathTitle(folder));
        Runnable navigate = () -> {
            navigateTo(folder);
            state.controlFocus = "content";
            breadcrumbMenu = false;
        };
        kit.hit(x, y + 1, w, 14, navigate);
        focusTargets.add(new FocusTarget(key, x, y + 1, w, 14, navigate));
        breadcrumbButtons.add(new BreadcrumbButton(x, y + 1, x + w, y + 15, folder, false));
    }

    private void renderBreadcrumbMenu(GuiGraphics g, int x, int y, int w) {
        List<ResourceLocation> path = ComputerGuideData.archiveFolderPath(state.folderId);
        int menuWidth = Math.min(w, Math.max(100, path.stream().mapToInt(id -> font.width(crumbTitle(id)) + 16).max().orElse(100)));
        int menuHeight = path.size() * 19 + 4;
        menuX = x;
        menuY = y;
        menuW = menuWidth;
        menuH = menuHeight;
        g.pose().pushPose();
        try {
            g.pose().translate(0, 0, 150);
            g.fill(x, y, x + menuWidth, y + menuHeight, AppKit.PANEL);
            kit.outline(g, x, y, menuWidth, menuHeight, PALE_GREEN);
            kit.block(x, y, menuWidth, menuHeight);
            for (int index = 0; index < path.size(); index++) {
                ResourceLocation id = path.get(index);
                control(g, "ancestor-" + index, x + 2, y + 2 + index * 19, menuWidth - 4, crumbTitle(id), false, true, folderPathTitle(id), () -> {
                    navigateTo(id);
                    state.controlFocus = "content";
                    breadcrumbMenu = false;
                });
            }
        } finally {
            g.pose().popPose();
        }
    }

    private boolean clickBreadcrumb(double mouseX, double mouseY) {
        for (BreadcrumbButton button : List.copyOf(breadcrumbButtons)) {
            if (mouseX < button.left() || mouseX >= button.right() || mouseY < button.top() || mouseY >= button.bottom()) continue;
            if (button.back()) goBack();
            else navigateTo(button.folder());
            state.controlFocus = "content";
            breadcrumbMenu = false;
            return true;
        }
        return false;
    }

    private void openFolder(ResourceLocation folder) {
        if (ComputerGuideData.archiveFolder(folder) == null || java.util.Objects.equals(state.folderId, folder)) return;
        rememberView();
        state.history.add(new BrowseLocation(state.folderId, state.search, state.scroll));
        state.folderId = folder;
        state.entryId = null;
        state.search = "";
        state.focusKey = "";
        state.scroll = 0;
        state.searchFocused = false;
        kit.release();
    }

    private void navigateTo(ResourceLocation folder) {
        if (ComputerGuideData.archiveFolder(folder) == null) folder = null;
        if (state.entryId != null || !java.util.Objects.equals(state.folderId, folder)) rememberView();
        state.entryId = null;
        state.detailScroll = 0;
        state.searchFocused = false;
        kit.release();
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
        if (state.entryId != null && !state.entryId.equals(openedEntry)) {
            state.entryId = null;
            state.detailScroll = 0;
            state.searchFocused = false;
            return;
        }
        if (!state.backHistory.isEmpty()) {
            restoreView(state.backHistory.remove(state.backHistory.size() - 1));
            return;
        }
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
            while (state.folderId != null && entries.stream().noneMatch(entry -> ComputerGuideData.archiveFolderContains(state.folderId, ComputerGuideData.archiveFolderId(entry)))) {
                boolean previous = recovering;
                recovering = true;
                openParent();
                recovering = previous;
            }
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

    private record BrowseItem(ComputerGuideData.Entry entry, ComputerGuideData.ArchiveFolder folder, int count) {
        String key() {
            return entry == null ? "folder:" + folder.id() : "entry:" + entry.id();
        }
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
        return detailLines.computeIfAbsent(part, key -> font.split(Component.literal(AntOSPlayerText.apply(translated(key))), w));
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

    private void renderFolderThumbnail(GuiGraphics g, ComputerGuideData.ArchiveFolder folder, int x, int y, int size) {
        PixelIcons.draw(g, FileExplorerApp.icon("folder"), x, y, size);
        int iconSize = Math.max(12, size * 3 / 5);
        int centerX = x + size / 2;
        int centerY = y + size / 2 + size / 12;
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = folder.greenTint();
        try {
            if (!screen.renderArchiveAsset(g, folder.iconItem(), "", "", "", centerX, centerY, iconSize, 0.0F, 1.0F))
                PixelIcons.draw(g, FileExplorerApp.icon("text"), centerX - iconSize / 2, centerY - iconSize / 2, iconSize);
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }

    private void renderThumbnail(GuiGraphics g, ComputerGuideData.Entry entry, int centerX, int centerY, boolean hover, int size) {
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = entry.greenTint();
        try {
            boolean rendered = !entry.recipeId().isBlank() && screen.renderArchiveRecipe(g, entry.recipeId(), centerX - size / 2,
                    centerY - size / 2 + size / 5, size, Math.max(6, size / 4));
            if (!rendered) rendered = screen.renderArchiveAsset(g, entry.coverItemId().isBlank() ? entry.itemId() : entry.coverItemId(),
                    entry.coverEntityId().isBlank() ? entry.entityId() : entry.coverEntityId(), entry.enchantmentId(), entry.coverPotionId(),
                    centerX, centerY, size, entry.rotation(), entry.renderScale());
            if (!rendered) PixelIcons.draw(g, FileExplorerApp.icon("text"), centerX - size / 2, centerY - size / 2, size);
            else {
                int marker = Math.max(8, size / 4);
                g.fill(centerX + size / 2 - marker - 1, centerY + size / 2 - marker - 1, centerX + size / 2 + 1, centerY + size / 2 + 1, BLACK);
                PixelIcons.draw(g, FileExplorerApp.icon("text"), centerX + size / 2 - marker, centerY + size / 2 - marker, marker);
            }
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }

    private int renderGallery(GuiGraphics g, ComputerGuideData.Entry selected, List<ComputerGuideData.Entry> entries, int x, int y, int w,
                              int clipTop, int clipBottom) {
        List<ComputerGuideData.GalleryItem> gallery = selected.gallery();
        if (gallery.isEmpty()) return y;
        String title = selected.galleryTitleKey().isBlank() ? tr("gallery") : Component.translatable(selected.galleryTitleKey()).getString().toUpperCase(Locale.ROOT);
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
                kit.hit(cellX, cellY, cell, cell, () -> {
                    ComputerGuideData.Entry entry = ComputerGuideData.availableEntry(screen.workspaceDiskIds(),
                            com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get(), target);
                    if (entry != null) select(entry);
                });
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
        if (button != 0) return true;
        if (breadcrumbMenu && !screen.inside(menuX, menuY, menuW, menuH, mouseX, mouseY)) {
            breadcrumbMenu = false;
            return true;
        }
        if (clickBreadcrumb(mouseX, mouseY)) return true;
        boolean handled = kit.click(mouseX, mouseY, button);
        if (!handled) {
            breadcrumbMenu = false;
            state.searchFocused = false;
            state.controlFocus = "content";
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (breadcrumbMenu) return true;
        if (state.entryId != null) state.detailScroll = Math.max(0, Math.min(state.detailMaxScroll, state.detailScroll - (int) Math.signum(scrollY) * 3));
        else if (layout != null) state.scroll = Math.max(0, Math.min(layout.maximumScroll(), state.scroll - (int) Math.signum(scrollY) * 16));
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (state.entryId == null && state.searchFocused && codePoint >= 32 && state.search.length() < 96) {
            state.search += codePoint;
            state.scroll = 0;
            return true;
        }
        return false;
    }

    private int focusedIndex() {
        for (int index = 0; index < visibleItems.size(); index++) if (visibleItems.get(index).key().equals(state.focusKey)) return index;
        return -1;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && state.entryId == null) {
            focusSearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (focusTargets.isEmpty()) return true;
            int current = -1;
            for (int index = 0; index < focusTargets.size(); index++) if (focusTargets.get(index).key().equals(state.controlFocus)) current = index;
            int next = Math.floorMod(current + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), focusTargets.size());
            state.controlFocus = focusTargets.get(next).key();
            state.searchFocused = state.controlFocus.equals("search");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (breadcrumbMenu) breadcrumbMenu = false;
            else if (state.entryId != null) goBack();
            else if (state.searchFocused) {
                state.searchFocused = false;
                state.controlFocus = "content";
            } else if (state.folderId != null) openParent();
            else return false;
            return true;
        }
        if (state.searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!state.search.isEmpty()) state.search = state.search.substring(0, state.search.offsetByCodePoints(state.search.length(), -1));
                state.scroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_DOWN) {
                state.searchFocused = false;
                state.controlFocus = "content";
                if (!visibleItems.isEmpty()) state.focusKey = visibleItems.get(0).key();
                return true;
            }
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            FocusTarget control = focusTargets.stream().filter(target -> target.key().equals(state.controlFocus)).findFirst().orElse(null);
            if (!state.controlFocus.equals("content") && control != null) {
                control.action().run();
                return true;
            }
            int selected = focusedIndex();
            if (state.entryId == null && selected >= 0) {
                BrowseItem item = visibleItems.get(selected);
                if (item.entry() != null) select(item.entry());
                else openFolder(item.folder().id());
                return true;
            }
        }
        if (state.entryId != null) {
            int movement = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : keyCode == GLFW.GLFW_KEY_UP ? -1
                    : keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 8 : keyCode == GLFW.GLFW_KEY_PAGE_UP ? -8 : 0;
            if (keyCode == GLFW.GLFW_KEY_HOME) state.detailScroll = 0;
            else if (keyCode == GLFW.GLFW_KEY_END) state.detailScroll = state.detailMaxScroll;
            else if (movement != 0) state.detailScroll = Math.max(0, Math.min(state.detailMaxScroll, state.detailScroll + movement));
            else return false;
            return true;
        }
        if (layout == null || visibleItems.isEmpty()) return false;
        int index = focusedIndex();
        int next = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> layout.move(index, 0, -1);
            case GLFW.GLFW_KEY_DOWN -> layout.move(index, 0, 1);
            case GLFW.GLFW_KEY_LEFT -> state.gallery ? layout.move(index, -1, 0) : index;
            case GLFW.GLFW_KEY_RIGHT -> state.gallery ? layout.move(index, 1, 0) : index;
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> visibleItems.size() - 1;
            default -> -1;
        };
        if (next < 0) return false;
        state.focusKey = visibleItems.get(next).key();
        state.controlFocus = "content";
        state.scroll = layout.reveal(next, state.scroll);
        return true;
    }

    private record BrowseLocation(ResourceLocation folder, String search, int scroll) { }
    private record BreadcrumbButton(int left, int top, int right, int bottom, ResourceLocation folder, boolean back) { }
    private record FocusTarget(String key, int x, int y, int w, int h, Runnable action) { }
    private record ViewState(ResourceLocation folder, ResourceLocation entry, String search, int scroll, int detailScroll,
                             String focus, boolean gallery, List<BrowseLocation> history) { }

    static final class State {
        ResourceLocation entryId;
        ResourceLocation folderId;
        final List<BrowseLocation> history = new java.util.ArrayList<>();
        final List<ViewState> backHistory = new java.util.ArrayList<>();
        int scroll;
        String search = "";
        boolean searchFocused;
        boolean gallery;
        String focusKey = "";
        String controlFocus = "content";
        int detailScroll;
        int detailMaxScroll;
    }
}
