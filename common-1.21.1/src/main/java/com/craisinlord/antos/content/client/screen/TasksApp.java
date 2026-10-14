package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.guide.TaskAppearance;
import com.craisinlord.antos.content.guide.TaskMarkup;
import com.craisinlord.antos.content.client.renderer.TaskShapeRenderer;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.WIDTH;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HEIGHT;

final class TasksApp extends ComputerApp {
    private static final int NODE_SIZE = 32;
    private static final int NODE_GRID = 56;
    private static final int ICON_REFERENCE_SIZE = NODE_SIZE - 6;
    private static final int ICON_ITEM_SIZE = Math.min(32, Math.max(16, ICON_REFERENCE_SIZE - 4));
    private static final float DEFAULT_MAP_ZOOM = 0.75F;
    private static final int BLEED_SIDE = 7;
    private static final int BLEED_TOP = 8;
    private static final int BLEED_BOTTOM = 5;
    private static final int EDGE_FADE = 14;
    private static final int EDGE_FADE_STEPS = 7;
    private static final int CHIP_FILL = 0xCC071007;
    private static final float CHROME_FADED_ALPHA = 0.3F;
    private static final float CHROME_SCALE = 0.8F;
    private static final int CHROME_BUTTON_HEIGHT = 12;
    private static final int TEAM_BUTTON_WIDTH = 48;

    int refreshTicks;
    int mapScrollX;
    int mapScrollY;
    private float mapZoom = defaultMapZoom();
    Window panningWindow;
    double panStartX;
    double panStartY;
    int panStartScrollX;
    int panStartScrollY;
    int panLimitX;
    int panLimitY;
    int panMinimumX;
    int panMinimumY;
    private boolean panMoved;
    private long lastMapMotion;
    private float chromeAlpha = 1.0F;
    private long chromeFadeTime;
    private final List<int[]> chromeAreas = new ArrayList<>();
    private boolean mapPointerFree;
    int categoryScroll;
    int detailScroll;
    private int detailMaximumScroll;
    String selectedCategory = "";
    String selectedTaskId = "";
    private String recipeItem = "";
    private boolean sidebarPinned;
    private boolean sidebarClickOpen;
    private boolean sidebarHoverOpen;
    private boolean sidebarHoverDismissed;
    private String archiveCacheId = "";
    private Set<ResourceLocation> archiveCacheDisks = Set.of();
    private Set<ResourceLocation> archiveCacheUnlocks = Set.of();
    private Set<ResourceLocation> archiveCacheVisible = Set.of();
    private Object archiveCacheDiskSource;
    private Object archiveCacheUnlockSource;
    private int detailViewTop;
    private int detailViewBottom;
    private Object nodeCacheTasks;
    private int nodeCacheMapX;
    private int nodeCacheMapY;
    private int nodeCacheOffsetX;
    private int nodeCacheOffsetY;
    private float nodeCacheZoom;
    private List<Node> nodeCache;
    private Map<String, Node> nodeCacheById;
    private Object limitCacheTasks;
    private int limitCacheMapX;
    private int limitCacheMapY;
    private int limitCacheMapW;
    private int limitCacheMapH;
    private float limitCacheZoom;
    private int[] limitCache;
    private final List<ArchiveButton> archiveButtons = new ArrayList<>();
    private final List<ClaimButton> claimButtons = new ArrayList<>();
    private final List<ItemButton> itemButtons = new ArrayList<>();
    private final List<PoolChoiceButton> poolChoiceButtons = new ArrayList<>();
    private final List<PrerequisiteButton> prerequisiteButtons = new ArrayList<>();
    private final List<Occupied> occupied = new ArrayList<>();
    private String pendingJumpTaskId = "";
    private int[] jumpConfirmButton = new int[4];
    private int[] jumpCancelButton = new int[4];
    private TaskRichText richText;
    private final Map<String, Map<Integer, String>> poolSelections = new HashMap<>();
    private boolean teamInviteFocused;
    private String teamInviteUsername = "";
    private boolean teamDisbandConfirm;
    private boolean teamMenuOpen;
    private boolean searchFocused;
    private String searchText = "";
    private int searchResultScroll;
    private boolean controlsOpen;
    private final Map<String, String> observedStates = new HashMap<>();
    private String notice = "";
    private long noticeUntil;
    private boolean windowWasOpen;
    private int viewRevision = Integer.MIN_VALUE;
    private String viewKey = "";
    private String viewLanguage = "";
    private boolean viewDirty = true;
    private List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> viewAllRows = List.of();
    private List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> viewVisibleRows = List.of();
    private Map<String, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> viewRowsById = Map.of();
    private Map<String, List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow>> viewRowsByCategory = Map.of();
    private List<String> viewCategories = List.of();
    private List<SidebarRow> viewSidebarRows = List.of();
    private final Map<String, SidebarCount> viewSidebarCounts = new HashMap<>();
    private final Map<List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow>, List<Cell>> layoutCache = new IdentityHashMap<>();
    private final Map<String, String> translations = new HashMap<>();
    private String searchCacheQuery;
    private List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> searchCacheResults = List.of();

    void tick(boolean windowOpen) {
        if (windowOpen && (!windowWasOpen || --refreshTicks <= 0)) {
            ComputerNetworking.requestTasks(com.craisinlord.antos.content.client.ComputerTasksClientState.requestToken());
            refreshTicks = 40;
        }
        windowWasOpen = windowOpen;
    }

    void opened() {
        refreshTicks = 0;
        selectedTaskId = "";
        selectedCategory = "";
        mapScrollX = 0;
        mapScrollY = 0;
        categoryScroll = 0;
        windowWasOpen = true;
        refreshTicks = 40;
        ComputerNetworking.requestTasks(com.craisinlord.antos.content.client.ComputerTasksClientState.requestToken());
    }

    void select(String taskId) {
        selectedTaskId = taskId;
        detailScroll = 0;
    }

    void drag(double mouseX, double mouseY, int button) {
        if (panningWindow == null || button != 0) return;
        int deltaX = (int) Math.round(mouseX - panStartX);
        int deltaY = (int) Math.round(mouseY - panStartY);
        if (deltaX != 0 || deltaY != 0) {
            panMoved = true;
            lastMapMotion = System.currentTimeMillis();
        }
        mapScrollX = Math.max(panMinimumX, Math.min(panLimitX, panStartScrollX - deltaX));
        mapScrollY = Math.max(panMinimumY, Math.min(panLimitY, panStartScrollY - deltaY));
    }

    void release() {
        panningWindow = null;
        panMoved = false;
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        archiveButtons.clear();
        claimButtons.clear();
        itemButtons.clear();
        poolChoiceButtons.clear();
        prerequisiteButtons.clear();
        ensureView();
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> allRows = viewAllRows;
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> visibleRows = viewVisibleRows;
        if (!com.craisinlord.antos.content.client.ComputerTasksClientState.hasSnapshot()) {
            g.drawString(font, Component.literal("TASKS"), x + 5, y + 3, PALE_GREEN, false);
            g.fill(x + 2, y + 17, x + 80, y + 18, GREEN);
            g.drawString(font, Component.literal("TASKS ARE LOADING..."), x + 96, y + 20, GREEN, false);
            g.drawString(font, Component.literal("WAITING FOR THE COMPUTER"), x + 96, y + 38, PALE_GREEN, false);
            return;
        }
        if (com.craisinlord.antos.content.client.ComputerTasksClientState.hasError()) {
            g.drawString(font, Component.literal("TASK LIST TOO LARGE TO LOAD"), x + 96, y + 20, GREEN, false);
            g.drawString(font, Component.literal("ASK THE PACK AUTHOR FOR HELP"), x + 96, y + 38, PALE_GREEN, false);
            return;
        }
        int bodyY = y;
        int bodyHeight = Math.max(1, h);
        List<String> categories = viewCategories;
        List<SidebarRow> sidebarRows = viewSidebarRows;
        if (selectedCategory.isBlank() || !categories.contains(selectedCategory)) {
            selectedCategory = categories.isEmpty() ? "" : categories.get(0);
        }
        boolean pointerOnSidebarTab = screen.hovered(x, bodyY + 1, 20, 19);
        boolean pointerOnSidebar = screen.hovered(x, bodyY, 134, bodyHeight);
        if (sidebarHoverDismissed && !pointerOnSidebarTab) sidebarHoverDismissed = false;
        sidebarHoverOpen = !sidebarHoverDismissed
                && (pointerOnSidebarTab || sidebarHoverOpen && pointerOnSidebar);
        boolean revealSidebar = sidebarPinned || sidebarClickOpen || sidebarHoverOpen;
        int sidebarWidth = revealSidebar ? 124 : 0;
        int sidebarRight = x + sidebarWidth;
        int contentOffset = sidebarWidth > 0 ? sidebarWidth + 9 : 30;
        var selectedTask = visibleRows.isEmpty() ? null : viewRowsById.get(selectedTaskId);
        if (selectedTask != null && (!selectedTask.visible() || selectedTask.isLink())) selectedTask = null;
        boolean mapView = !visibleRows.isEmpty() && selectedTask == null;
        chromeAreas.clear();
        if (mapView) {
            renderMap(g, x, y, w, h, contentOffset, sidebarWidth);
            screen.beginLayer(g);
        } else {
            chromeAlpha = 1.0F;
        }
        if (sidebarWidth > 0) {
            g.fill(x, bodyY, sidebarRight, bodyY + bodyHeight, 0xFF071007);
            g.fill(sidebarRight, bodyY, sidebarRight + 1, bodyY + bodyHeight, GREEN);
            int toggleColor = screen.hovered(x + 2, bodyY + 1, 18, 16) ? PALE_GREEN : GREEN;
            g.fill(x + 2, bodyY + 1, x + 20, bodyY + 17, HOVER_FILL);
            screen.box(g, x + 2, bodyY + 1, x + 20, bodyY + 17, toggleColor);
            g.drawCenteredString(font, Component.literal("<"), x + 11, bodyY + 4, toggleColor);
            g.drawString(font, Component.literal("CATEGORIES"), x + 27, bodyY + 3, GREEN, false);
            g.fill(x + 5, bodyY + 16, sidebarRight - 5, bodyY + 17, DARK_GREEN);
            if (screen.hovered(x + 4, bodyY + 18, sidebarWidth - 8, 16)) screen.drawHover(g, x + 4, bodyY + 18, sidebarWidth - 8, 16);
            screen.box(g, x + 4, bodyY + 18, sidebarRight - 4, bodyY + 34, sidebarPinned ? GREEN : 0xFF476047);
            g.drawString(font, Component.literal("KEEP OPEN"), x + 9, bodyY + 22, PALE_GREEN, false);
            String keepOpenState = sidebarPinned ? "ON" : "OFF";
            g.drawString(font, Component.literal(keepOpenState), sidebarRight - font.width(keepOpenState) - 10, bodyY + 22,
                    sidebarPinned ? GREEN : 0xFF87B787, false);
        } else {
            int toggleColor = fade(pointerOnSidebarTab ? PALE_GREEN : GREEN);
            g.fill(x, bodyY + 1, x + 20, bodyY + 19, fade(0xFF071007));
            screen.box(g, x, bodyY + 1, x + 20, bodyY + 19, toggleColor);
            g.drawCenteredString(font, Component.literal(">"), x + 10, bodyY + 5, toggleColor);
        }
        int categoriesVisible = Math.max(1, (bodyHeight - 52 + 18) / 19);
        categoryScroll = Math.max(0, Math.min(categoryScroll, Math.max(0, sidebarRows.size() - categoriesVisible)));
        int categoryY = bodyY + 39;
        for (int rowIndex = categoryScroll; sidebarWidth > 0 && rowIndex < sidebarRows.size() && rowIndex < categoryScroll + categoriesVisible; rowIndex++) {
            SidebarRow sidebarRow = sidebarRows.get(rowIndex);
            boolean header = sidebarRow.category().isBlank();
            boolean selected = !header && sidebarRow.category().equals(selectedCategory);
            boolean rowHovered = screen.hovered(x + 2, categoryY - 2, sidebarWidth - 4, 16);
            if (selected || rowHovered) g.fill(x + 2, categoryY - 2, sidebarRight - 2, categoryY + 14, HOVER_FILL);
            int iconLeft = x + 4 + sidebarRow.indent();
            boolean hasIcon = !sidebarRow.iconItem().isBlank() || !sidebarRow.iconEntity().isBlank();
            if (hasIcon) {
        boolean previousTint = screen.archiveGreenTint;
                screen.archiveGreenTint = sidebarRow.greenTint();
                try {
                    screen.renderArchiveAsset(g, sidebarRow.iconItem(), sidebarRow.iconEntity(), "", "", iconLeft + 8, categoryY + 6, 16, 0.0F, 0.9F, sidebarRow.renderMobFromSpawnEgg());
                } finally {
                    screen.archiveGreenTint = previousTint;
                }
            }
            int labelX = hasIcon ? iconLeft + 19 : iconLeft + 2;
            SidebarCount sidebarCount = viewSidebarCounts.getOrDefault(sidebarKey(sidebarRow), new SidebarCount("0/0", false));
            String count = sidebarCount.label();
            boolean rowHasClaimable = sidebarCount.claimable();
            int categoryLabelWidth = Math.max(1, sidebarRight - font.width(count) - 10 - labelX);
            int labelColor = header ? PALE_GREEN : selected ? GREEN : 0xFF87B787;
            drawSidebarLabel(g, sidebarRow.label().toUpperCase(Locale.ROOT), labelX, categoryY + 2, categoryLabelWidth, labelColor, rowHovered);
            g.drawString(font, Component.literal(count), sidebarRight - font.width(count) - 6, categoryY + 2,
                    rowHasClaimable || header || selected ? PALE_GREEN : 0xFF638063, false);
            categoryY += 19;
        }
        if (sidebarWidth > 0 && categoryScroll + categoriesVisible < sidebarRows.size()) {
            int arrowX = sidebarRight - 14;
            int arrowY = bodyY + bodyHeight - 13;
            g.fill(arrowX - 2, arrowY - 1, arrowX + 12, arrowY + 11, 0xFF071007);
            g.drawCenteredString(font, Component.literal("v"), arrowX + 5, arrowY, 0xFF87B787);
        }
        if (sidebarWidth > 0 && screen.hovered(x + 4, bodyY + 18, sidebarWidth - 8, 16)) {
            int tipX = sidebarRight + 5;
            int tipY = bodyY + 19;
            g.fill(tipX, tipY, tipX + 170, tipY + 15, 0xFF071007);
            screen.box(g, tipX, tipY, tipX + 170, tipY + 15, GREEN);
            g.drawString(font, Component.literal(screen.trimToWidth("STAYS OPEN WHEN POINTER LEAVES", 160)), tipX + 5, tipY + 4, PALE_GREEN, false);
        }
        if (visibleRows.isEmpty()) {
            g.drawString(font, Component.literal(allRows.isEmpty() ? "NO TASK RECORDS FOUND" : "NO RECORDS VISIBLE YET"), x + contentOffset, y + 12, GREEN, false);
            g.drawString(font, Component.literal(allRows.isEmpty() ? "INSERT A FIELD DISK TO LOAD ITS RECORDS" : "COMPLETE A PREREQUISITE TO REVEAL MORE"), x + contentOffset, y + 28, PALE_GREEN, false);
            renderTeamOverlay(g, x, y, w, h);
            return;
        }
        if (selectedTask != null) {
            renderDetail(g, selectedTask, x + contentOffset, bodyY, w - contentOffset, bodyHeight, unlockedArchiveIds(selectedTask));
            renderTeamOverlay(g, x, y, w, h);
            renderJumpConfirm(g, x + contentOffset, bodyY, w - contentOffset, bodyHeight);
            return;
        }
        renderTeamOverlay(g, x, y, w, h);
    }

    private void renderMap(GuiGraphics g, int x, int y, int w, int h, int contentOffset, int sidebarWidth) {
        int bodyY = y;
        int bodyHeight = Math.max(1, h);
        pendingJumpTaskId = "";
        selectedTaskId = "";
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows = rowsFor(selectedCategory);
        int mapX = x + contentOffset;
        int mapY = bodyY + 18;
        int mapW = w - contentOffset;
        int mapH = Math.max(1, bodyHeight - 36);
        int[] clip = mapClip(x, bodyY, w, bodyHeight, sidebarWidth);
        long completeCount = categoryRows.stream().filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::complete).count();
        long claimableCount = categoryRows.stream().filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::claimable).count();
        long taskCount = categoryRows.stream().filter(row -> !row.isLink()).count();
        String progressLabel = completeCount + "/" + taskCount + (searchFocused ? " DONE" : " COMPLETE")
                + (claimableCount > 0 ? (searchFocused ? " !" : "  //  READY ") + claimableCount : "");
        String categoryLabel = categoryLabel(selectedCategory).toUpperCase(Locale.ROOT);
        int searchControlWidth = searchControlWidth();
        String visibleCategoryLabel = screen.trimToWidth(categoryLabel, Math.max(1, mapW - chromeTextWidth(progressLabel) - searchControlWidth - 12));
        int searchLeft = mapX + mapW - searchControlWidth;
        String visibleProgressLabel = screen.trimToWidth(progressLabel,
                Math.max(1, Math.round((mapW - font.width(visibleCategoryLabel) - searchControlWidth - 12) / CHROME_SCALE)));
        int progressLeft = searchLeft - chromeTextWidth(visibleProgressLabel) - 5;
        int[] categoryChip = {mapX - 4, bodyY, mapX + font.width(visibleCategoryLabel) + 4, bodyY + 19};
        int[] progressChip = {progressLeft - 4, bodyY + 1, searchLeft - 1, bodyY + 1 + CHROME_BUTTON_HEIGHT};
        chromeAreas.add(categoryChip);
        chromeAreas.add(progressChip);
        chromeAreas.add(new int[] {searchLeft, bodyY + 1, mapX + mapW, bodyY + 1 + CHROME_BUTTON_HEIGHT});
        chromeAreas.addAll(List.of(controlChips(mapX, bodyY, mapW, bodyHeight)));
        chromeAreas.add(teamChip(x, y, w, h));
        if (sidebarWidth == 0) chromeAreas.add(new int[] {x, bodyY + 1, x + 20, bodyY + 19});
        if (controlsOpen) chromeAreas.add(new int[] {controlsPanelX(mapX, mapW), bodyY + 22, mapX + mapW, bodyY + 114});
        if (teamMenuOpen) chromeAreas.add(new int[] {x + w - 232, y + h - 17 - teamMenuHeight(), x + w - 4, y + h - 17});
        double pointerX = screen.session.mouseX;
        double pointerY = screen.session.mouseY;
        boolean pointerOnChrome = insideChrome(pointerX, pointerY);
        mapPointerFree = !pointerOnChrome && pointerX >= clip[0] && pointerX < clip[2] && pointerY >= clip[1] && pointerY < clip[3];
        updateChromeAlpha(pointerOnChrome);
        screen.enableComputerScissor(g, clip[0], clip[1], clip[2], clip[3]);
        int[] mapScrollLimits = mapScrollLimits(categoryRows, mapX, mapY, mapW, mapH);
        mapScrollX = Math.max(mapScrollLimits[0], Math.min(mapScrollX, mapScrollLimits[1]));
        mapScrollY = Math.max(mapScrollLimits[2], Math.min(mapScrollY, mapScrollLimits[3]));
        List<Node> nodes = nodes(categoryRows, mapX, mapY, mapScrollX, mapScrollY, mapZoom);
        Map<String, Node> nodesById = nodeCacheById;
        int clipLeft = clip[0];
        int clipTop = clip[1];
        int clipRight = clip[2];
        int clipBottom = clip[3];
        occupied.clear();
        for (Node node : nodes) {
            for (String requiredId : node.task().requires()) {
                Node parent = nodesById.get(requiredId);
                if (parent == null || !connectionOnScreen(parent, node, clipLeft, clipTop, clipRight, clipBottom)) continue;
                renderConnection(g, parent, node, parent.task().complete(), node.task().appearance());
            }
            for (String requiredId : node.task().requiresAny()) {
                Node parent = nodesById.get(requiredId);
                if (parent == null || !connectionOnScreen(parent, node, clipLeft, clipTop, clipRight, clipBottom)) continue;
                renderConnection(g, parent, node, parent.task().complete(), node.task().appearance());
            }
            for (String connectedId : node.task().connects()) {
                Node other = nodesById.get(connectedId);
                if (other == null || other == node || !connectionOnScreen(node, other, clipLeft, clipTop, clipRight, clipBottom)) continue;
                renderConnection(g, node, other, node.task().complete() || other.task().complete(), node.task().appearance());
            }
        }
        List<Node> onScreen = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (node.visualRight() < clipLeft || node.visualLeft() > clipRight || node.visualBottom() < clipTop || node.visualTop() > clipBottom) continue;
            onScreen.add(node);
        }
        for (Node node : onScreen) {
            occupied.add(new Occupied(Math.round(node.shapeLeft()), Math.round(node.shapeTop()),
                    Math.round(node.shapeLeft() + node.shapeSize()), Math.round(node.shapeTop() + node.shapeSize()), node));
            if (node.task().claimable()) {
                int[] badge = badgeBounds(node);
                occupied.add(new Occupied(badge[0], badge[1], badge[2], badge[3], node));
            }
        }
        for (Node node : onScreen) renderNode(g, node);
        screen.beginLayer(g);
        renderEdgeFade(g, clipLeft, clipTop, clipRight, clipBottom);
        for (Node node : onScreen) renderHoverTitle(g, node, clipLeft, clipTop, clipRight);
        g.disableScissor();
        screen.beginLayer(g);
        g.fill(categoryChip[0], categoryChip[1], categoryChip[2], categoryChip[3], fade(CHIP_FILL));
        g.drawString(font, Component.literal(visibleCategoryLabel), mapX, bodyY + 3, fade(PALE_GREEN), false);
        g.fill(mapX, bodyY + 16, mapX + font.width(visibleCategoryLabel), bodyY + 17, fade(GREEN));
        g.fill(progressChip[0], progressChip[1], progressChip[2], progressChip[3], fade(CHIP_FILL));
        float chromeTextY = bodyY + 1 + CHROME_BUTTON_HEIGHT / 2.0F;
        drawChromeText(g, visibleProgressLabel, progressLeft, chromeTextY, fade(claimableCount > 0 ? PALE_GREEN : GREEN), false);
        if (searchFocused) {
            int searchBottom = bodyY + 1 + CHROME_BUTTON_HEIGHT;
            g.fill(searchLeft, bodyY + 1, mapX + mapW, searchBottom, fade(0xFF071007));
            screen.box(g, searchLeft, bodyY + 1, mapX + mapW, searchBottom, fade(GREEN));
            String searchValue = searchText.isBlank() ? "SEARCH TASKS" : searchText;
            String visibleSearch = screen.trimToWidth(searchValue + (screen.caretVisible() ? "|" : ""), Math.round((searchControlWidth - 20) / CHROME_SCALE));
            drawChromeText(g, visibleSearch, searchLeft + 4, chromeTextY, fade(searchText.isBlank() ? 0xFF87B787 : PALE_GREEN), false);
            drawChromeText(g, "X", mapX + mapW - 7, chromeTextY, fade(GREEN), true);
        } else {
            drawChromeButton(g, searchLeft, bodyY + 1, searchControlWidth - 2, "FIND");
        }
        renderMapControls(g, mapX, bodyY, mapW, bodyHeight);
        renderSearchResults(g, mapX, mapY, mapW);
        renderNotice(g, mapX, mapY, mapW);
    }

    private static int[] mapClip(int x, int bodyY, int w, int bodyHeight, int sidebarWidth) {
        return new int[] {sidebarWidth > 0 ? x + sidebarWidth + 1 : x - BLEED_SIDE, bodyY - BLEED_TOP,
                x + w + BLEED_SIDE, bodyY + bodyHeight + BLEED_BOTTOM};
    }

    private boolean insideChrome(double pointerX, double pointerY) {
        for (int[] area : chromeAreas) {
            if (pointerX >= area[0] && pointerX < area[2] && pointerY >= area[1] && pointerY < area[3]) return true;
        }
        return false;
    }

    private void updateChromeAlpha(boolean pointerOnChrome) {
        long now = System.currentTimeMillis();
        boolean moving = panningWindow != null && panMoved || now - lastMapMotion < 500L;
        float target = moving && !pointerOnChrome ? CHROME_FADED_ALPHA : 1.0F;
        float timeConstant = target < chromeAlpha ? 90.0F : 180.0F;
        float step = 1.0F - (float) Math.exp(-(now - chromeFadeTime) / timeConstant);
        chromeAlpha += (target - chromeAlpha) * step;
        chromeFadeTime = now;
    }

    private int fade(int color) {
        int alpha = Math.max(4, Math.round((color >>> 24) * chromeAlpha));
        return alpha << 24 | color & 0xFFFFFF;
    }

    private void renderEdgeFade(GuiGraphics g, int left, int top, int right, int bottom) {
        g.fill(left - 2, top - 2, right + 2, top, BLACK);
        g.fill(left - 2, bottom, right + 2, bottom + 2, BLACK);
        g.fill(left - 2, top, left, bottom, BLACK);
        g.fill(right, top, right + 2, bottom, BLACK);
        for (int segment = 0; segment < EDGE_FADE_STEPS; segment++) {
            int from = segment * EDGE_FADE / EDGE_FADE_STEPS;
            int to = (segment + 1) * EDGE_FADE / EDGE_FADE_STEPS;
            int outer = edgeFadeColor(from);
            int inner = edgeFadeColor(to);
            g.fillGradient(left, top + from, right, top + to, outer, inner);
            g.fillGradient(left, bottom - to, right, bottom - from, inner, outer);
            horizontalGradient(g, left + from, top, left + to, bottom, outer, inner);
            horizontalGradient(g, right - to, top, right - from, bottom, inner, outer);
        }
    }

    private static int edgeFadeColor(int distance) {
        float strength = 1.0F - distance / (float) EDGE_FADE;
        return Math.round(strength * strength * 255) << 24 | BLACK & 0xFFFFFF;
    }

    private static void horizontalGradient(GuiGraphics g, int left, int top, int right, int bottom, int leftColor, int rightColor) {
        Matrix4f pose = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
        buffer.addVertex(pose, left, top, 0.0F).setColor(leftColor);
        buffer.addVertex(pose, left, bottom, 0.0F).setColor(leftColor);
        buffer.addVertex(pose, right, bottom, 0.0F).setColor(rightColor);
        buffer.addVertex(pose, right, top, 0.0F).setColor(rightColor);
    }

    private void drawChromeButton(GuiGraphics g, int x, int y, int w, String text) {
        g.fill(x, y, x + w, y + CHROME_BUTTON_HEIGHT, fade(HOVER_FILL));
        drawChromeText(g, text, x + w / 2.0F, y + CHROME_BUTTON_HEIGHT / 2.0F, fade(PALE_GREEN), true);
    }

    private void drawChromeText(GuiGraphics g, String text, float x, float centerY, int color, boolean centered) {
        g.pose().pushPose();
        g.pose().translate(x, centerY, 0.0F);
        g.pose().scale(CHROME_SCALE, CHROME_SCALE, 1.0F);
        g.drawString(font, text, centered ? -font.width(text) / 2 : 0, -4, color, centered);
        g.pose().popPose();
    }

    private int chromeTextWidth(String text) {
        return (int) Math.ceil(font.width(text) * CHROME_SCALE);
    }

    private int searchControlWidth() {
        return searchFocused ? 106 : 35;
    }

    private static int[] teamChip(int x, int y, int w, int h) {
        return new int[] {x + w - TEAM_BUTTON_WIDTH - 6, y + h - CHROME_BUTTON_HEIGHT - 3, x + w - 2, y + h + 1};
    }

    private record MapControls(int y, boolean compact, int zoomOutX, int zoomLabelX, int zoomInX, int centerX, int centerWidth, int helpX, int buttonWidth) { }

    private static MapControls mapControls(int mapX, int bodyY, int mapW, int bodyHeight) {
        boolean compact = mapW < 220;
        int zoomOutX = mapX + 3;
        return new MapControls(bodyY + bodyHeight - CHROME_BUTTON_HEIGHT - 1, compact, zoomOutX, zoomOutX + 14,
                zoomOutX + (compact ? 14 : 40), zoomOutX + (compact ? 29 : 54), compact ? 34 : 38,
                compact ? mapX + mapW - 14 : mapX + mapW - 70, 11);
    }

    private static boolean connectionOnScreen(Node from, Node to, int mapX, int mapY, int mapRight, int mapBottom) {
        return Math.max(from.right(), to.right()) >= mapX && Math.min(from.left(), to.left()) <= mapRight
                && Math.max(from.bottom(), to.bottom()) >= mapY && Math.min(from.top(), to.top()) <= mapBottom;
    }

    private void renderJumpConfirm(GuiGraphics g, int x, int y, int w, int h) {
        var target = viewRowsById.get(pendingJumpTaskId);
        if (target == null || !target.visible() || target.isLink()) {
            pendingJumpTaskId = "";
            return;
        }
        screen.beginLayer(g);
        g.pose().pushPose();
        g.pose().translate(0.0D, 0.0D, 300.0D);
        int panelW = Math.max(60, Math.min(w - 8, 210));
        int panelH = 66;
        int panelX = x + (w - panelW) / 2;
        int panelY = y + Math.max(20, (h - panelH) / 2);
        g.fill(x, y, x + w, y + h, 0x99000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF071007);
        screen.box(g, panelX, panelY, panelX + panelW, panelY + panelH, GREEN);
        g.drawString(font, Component.literal(screen.trimToWidth("OPEN PREREQUISITE?", panelW - 10)), panelX + 6, panelY + 6, PALE_GREEN, false);
        g.drawString(font, Component.literal(screen.trimToWidth(plain(target.title()), panelW - 12)), panelX + 6, panelY + 19, GREEN, false);
        g.drawString(font, Component.literal(screen.trimToWidth("TAB // " + categoryLabel(target.category()).toUpperCase(Locale.ROOT), panelW - 12)),
                panelX + 6, panelY + 30, 0xFF87B787, false);
        int buttonW = Math.max(20, (panelW - 18) / 2);
        int buttonY = panelY + panelH - 20;
        jumpConfirmButton = new int[]{panelX + 6, buttonY, buttonW, 15};
        jumpCancelButton = new int[]{panelX + panelW - 6 - buttonW, buttonY, buttonW, 15};
        drawTeamButton(g, jumpConfirmButton[0], buttonY, buttonW, "GO", false);
        drawTeamButton(g, jumpCancelButton[0], buttonY, buttonW, "CANCEL", false);
        if (screen.hovered(jumpConfirmButton[0], buttonY, buttonW, 15)) screen.box(g, jumpConfirmButton[0], buttonY, jumpConfirmButton[0] + buttonW, buttonY + 15, GREEN);
        if (screen.hovered(jumpCancelButton[0], buttonY, buttonW, 15)) screen.box(g, jumpCancelButton[0], buttonY, jumpCancelButton[0] + buttonW, buttonY + 15, GREEN);
        g.flush();
        g.pose().popPose();
    }

    private void confirmJump() {
        var target = viewRowsById.get(pendingJumpTaskId);
        pendingJumpTaskId = "";
        if (target != null && target.visible() && !target.isLink()) openSearchResult(target);
    }

    private void openLink(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow link) {
        String target = link.linkCategory();
        if (target.isBlank()) {
            if (groupCollapsed(link.linkGroup())) {
                AntOSSettings.setTaskGroupCollapsed(link.linkGroup(), false);
                viewDirty = true;
                ensureView();
            }
            target = viewCategories.stream().filter(category -> link.linkGroup().equals(categoryGroup(category))).findFirst().orElse("");
        }
        if (target.isBlank() || !viewCategories.contains(target)) {
            notice = "TAB NOT AVAILABLE YET";
            noticeUntil = System.currentTimeMillis() + 2500L;
            return;
        }
        selectedCategory = target;
        selectedTaskId = "";
        recipeItem = "";
        sidebarClickOpen = false;
        searchFocused = false;
        controlsOpen = false;
        detailScroll = 0;
        detailMaximumScroll = 0;
        mapScrollX = 0;
        mapScrollY = 0;
        centerCurrentMap();
    }

    private void renderTeamOverlay(GuiGraphics g, int x, int y, int w, int h) {
        screen.beginLayer(g);
        screen.enableComputerScissor(g, x, y, x + w, y + h);
        try {
            renderTeamPanel(g, x, y, w, h);
            renderTeamMenu(g, x, y, w, h);
        } finally {
            g.disableScissor();
        }
    }

    private void observeStatusChanges(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> rows) {
        if (observedStates.isEmpty()) {
            for (var task : rows) observedStates.put(task.id(), task.complete() + ":" + task.claimable());
            return;
        }
        for (var task : rows) {
            String nextState = task.complete() + ":" + task.claimable();
            String previousState = observedStates.put(task.id(), nextState);
            if (previousState == null) continue;
            String title = plain(task.title());
            if (task.claimable() && !previousState.endsWith(":true")) {
                notice = !previousState.startsWith("true:") && task.complete()
                        ? "TASK COMPLETE // REWARD READY // " + title : "REWARD READY // " + title;
                noticeUntil = System.currentTimeMillis() + 4000L;
            } else if (task.complete() && !previousState.startsWith("true:")) {
                notice = "TASK COMPLETE // " + title;
                noticeUntil = System.currentTimeMillis() + 4000L;
            }
        }
    }

    private void renderMapControls(GuiGraphics g, int mapX, int bodyY, int mapW, int bodyHeight) {
        MapControls controls = mapControls(mapX, bodyY, mapW, bodyHeight);
        for (int[] chip : controlChips(mapX, bodyY, mapW, bodyHeight)) g.fill(chip[0], chip[1], chip[2], chip[3], fade(CHIP_FILL));
        drawChromeButton(g, controls.zoomOutX(), controls.y(), controls.buttonWidth(), "-");
        if (!controls.compact()) drawChromeText(g, Math.round(mapZoom / DEFAULT_MAP_ZOOM * 100) + "%",
                (controls.zoomLabelX() + controls.zoomInX()) / 2.0F, controls.y() + CHROME_BUTTON_HEIGHT / 2.0F, fade(PALE_GREEN), true);
        drawChromeButton(g, controls.zoomInX(), controls.y(), controls.buttonWidth(), "+");
        drawChromeButton(g, controls.centerX(), controls.y(), controls.centerWidth(), "CENTER");
        drawChromeButton(g, controls.helpX(), controls.y(), controls.buttonWidth(), "?");
        if (controlsOpen) {
            int panelW = controlsPanelWidth(mapW);
            int panelX = controlsPanelX(mapX, mapW);
            int panelY = bodyY + 22;
            g.fill(panelX, panelY, panelX + panelW, panelY + 92, 0xFF071007);
            screen.box(g, panelX, panelY, panelX + panelW, panelY + 92, GREEN);
            String[] hints = {"DRAG // MOVE MAP", "WHEEL // SCROLL", "SHIFT + WHEEL // SIDEWAYS", "CTRL + WHEEL // ZOOM", "- / + // ZOOM", "CENTER // FRAME CATEGORY", "! // REWARD READY", "CTRL + F // FIND TASK"};
            for (int i = 0; i < hints.length; i++) {
                g.drawString(font, Component.literal(screen.trimToWidth(hints[i], panelW - 10)), panelX + 5, panelY + 5 + i * 11, i == 0 ? PALE_GREEN : GREEN, false);
            }
        }
    }

    private static int[][] controlChips(int mapX, int bodyY, int mapW, int bodyHeight) {
        MapControls controls = mapControls(mapX, bodyY, mapW, bodyHeight);
        int top = controls.y() - 2;
        int bottom = controls.y() + CHROME_BUTTON_HEIGHT + 2;
        return new int[][] {{controls.zoomOutX() - 2, top, controls.centerX() + controls.centerWidth() + 2, bottom},
                {controls.helpX() - 2, top, controls.helpX() + controls.buttonWidth() + 2, bottom}};
    }

    private int controlsPanelWidth(int mapW) { return Math.min(152, Math.max(1, mapW - 4)); }

    private int controlsPanelX(int mapX, int mapW) { return mapX + mapW - controlsPanelWidth(mapW); }

    private void renderSearchResults(GuiGraphics g, int mapX, int mapY, int mapW) {
        if (!searchFocused || searchText.isBlank()) return;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0D, 0.0D, 200.0D);
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> results = searchResults();
        int panelX = mapX + 2;
        int panelY = mapY + 2;
        int panelW = Math.min(mapW - 4, 192);
        searchResultScroll = Math.max(0, Math.min(Math.max(0, results.size() - 5), searchResultScroll));
        int visible = Math.min(5, Math.max(0, results.size() - searchResultScroll));
        int panelH = Math.max(25, 21 + visible * 18);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF071007);
        screen.box(g, panelX, panelY, panelX + panelW, panelY + panelH, GREEN);
        String summary = results.isEmpty() ? "NO TASKS FOUND" : results.size() + " MATCHES";
        g.drawString(font, Component.literal(summary), panelX + 5, panelY + 4, PALE_GREEN, false);
        for (int i = 0; i < visible; i++) {
            var task = results.get(i + searchResultScroll);
            int rowY = panelY + 17 + i * 18;
            if (screen.hovered(panelX + 2, rowY - 1, panelW - 4, 17)) g.fill(panelX + 2, rowY - 1, panelX + panelW - 2, rowY + 16, HOVER_FILL);
            String title = plain(task.title());
            String category = categoryLabel(task.category());
            String resultLabel = title + "  //  " + category;
            g.drawString(font, Component.literal(screen.trimToWidth(resultLabel, panelW - 10)), panelX + 5, rowY + 3,
                    task.complete() ? PALE_GREEN : GREEN, false);
        }
        g.flush();
        g.pose().popPose();
    }

    private void renderNotice(GuiGraphics g, int mapX, int mapY, int mapW) {
        if (notice.isBlank() || System.currentTimeMillis() >= noticeUntil
                || searchFocused && !searchText.isBlank()) return;
        String label = screen.trimToWidth(notice, Math.max(1, mapW - 12));
        int width = Math.min(mapW - 4, font.width(label) + 10);
        int left = mapX + (mapW - width) / 2;
        int top = mapY + 2;
        g.fill(left, top, left + width, top + 15, 0xFF071007);
        screen.box(g, left, top, left + width, top + 15, PALE_GREEN);
        g.drawCenteredString(font, Component.literal(label), left + width / 2, top + 4, PALE_GREEN);
    }

    private void renderTeamPanel(GuiGraphics g, int x, int y, int w, int h) {
        var state = com.craisinlord.antos.content.client.ComputerTasksClientState.team();
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TeamInvite> invites =
                com.craisinlord.antos.content.client.ComputerTasksClientState.invites();
        String label = state != null ? "TEAM " + state.members().size()
                : invites.isEmpty() ? "TEAM +" : "TEAM !";
        int[] chip = teamChip(x, y, w, h);
        g.fill(chip[0], chip[1], chip[2], chip[3], fade(CHIP_FILL));
        drawChromeButton(g, chip[0] + 2, chip[1] + 2, TEAM_BUTTON_WIDTH, label);
        if (!teamMenuOpen && screen.hovered(chip[0] + 2, chip[1] + 2, TEAM_BUTTON_WIDTH, CHROME_BUTTON_HEIGHT)) {
            int tipX = x + w - 203;
            int tipY = y + h - 33;
            g.fill(tipX, tipY, tipX + 136, tipY + 14, 0xFF071007);
            screen.box(g, tipX, tipY, tipX + 136, tipY + 14, GREEN);
            g.drawCenteredString(font, Component.literal(screen.trimToWidth("TEAM // MEMBERS AND INVITES", 128)), tipX + 68, tipY + 4, PALE_GREEN);
        }
    }

    private void renderTeamMenu(GuiGraphics g, int x, int y, int w, int h) {
        if (!teamMenuOpen) return;
        var state = com.craisinlord.antos.content.client.ComputerTasksClientState.team();
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TeamInvite> invites =
                com.craisinlord.antos.content.client.ComputerTasksClientState.invites();
        int menuX = x + w - 232;
        int menuY = y + h - 17 - teamMenuHeight();
        int menuW = 228;
        int menuH = teamMenuHeight();
        g.fill(menuX, menuY, menuX + menuW, menuY + menuH, 0xFF071007);
        screen.box(g, menuX, menuY, menuX + menuW, menuY + menuH, GREEN);
        String status = com.craisinlord.antos.content.client.ComputerTasksClientState.teamStatus();
        String summary = status.isBlank() ? state == null ? "TEAM // NONE" : "TEAM // " + state.ownerName().toUpperCase(Locale.ROOT)
                + " // " + state.members().size() + " MEMBERS" : status;
        g.drawString(font, Component.literal(screen.trimToWidth(summary, menuW - 10)), menuX + 5, menuY + 4,
                status.isBlank() ? GREEN : PALE_GREEN, false);
        if (state == null) {
            drawTeamButton(g, menuX + 4, menuY + 20, 84, "CREATE TEAM", false);
        } else if (state.owner()) {
            int fieldX = menuX + 4;
            screen.box(g, fieldX, menuY + 20, fieldX + 92, menuY + 35, teamInviteFocused ? GREEN : PALE_GREEN);
            String inviteValue = teamInviteUsername.isBlank() && !teamInviteFocused ? "ANTOS USERNAME" : teamInviteUsername;
            g.drawString(font, Component.literal(screen.trimToWidth(inviteValue + (teamInviteFocused && screen.caretVisible() ? "|" : ""), 86)),
                    fieldX + 3, menuY + 24, teamInviteFocused ? PALE_GREEN : GREEN, false);
            drawTeamButton(g, menuX + 100, menuY + 20, 48, "INVITE", false);
            drawTeamButton(g, menuX + 152, menuY + 20, 72, teamDisbandConfirm ? "CONFIRM?" : "DISBAND", false);
        } else {
            drawTeamButton(g, menuX + 4, menuY + 20, 82, "LEAVE TEAM", false);
        }
        if (state == null && !invites.isEmpty()) {
            var invite = invites.get(0);
            String inviteLabel = "FROM " + invite.inviterName().toUpperCase(Locale.ROOT) + " // "
                    + invite.ownerName().toUpperCase(Locale.ROOT) + "'S TEAM" + (invites.size() > 1 ? " +" + (invites.size() - 1) : "");
            g.drawString(font, Component.literal(screen.trimToWidth(inviteLabel, menuW - 112)), menuX + 4, menuY + 42, PALE_GREEN, false);
            drawTeamButton(g, menuX + menuW - 106, menuY + 38, 48, "JOIN", false);
            drawTeamButton(g, menuX + menuW - 54, menuY + 38, 48, "DECLINE", false);
        }
    }

    private void drawTeamButton(GuiGraphics g, int x, int y, int w, String text, boolean disabled) {
        g.fill(x, y, x + w, y + 15, disabled ? DARK_GREEN : HOVER_FILL);
        g.drawCenteredString(font, Component.literal(text), x + w / 2, y + 4, disabled ? 0xFF638063 : PALE_GREEN);
    }

    private int teamMenuHeight() {
        return com.craisinlord.antos.content.client.ComputerTasksClientState.team() == null
                && !com.craisinlord.antos.content.client.ComputerTasksClientState.invites().isEmpty() ? 58 : 38;
    }

    private Set<ResourceLocation> unlockedArchiveIds(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task) {
        List<ResourceLocation> diskSource = com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.activatedDisks();
        List<ResourceLocation> unlockSource = com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get();
        if (task.id().equals(archiveCacheId) && diskSource == archiveCacheDiskSource && unlockSource == archiveCacheUnlockSource) return archiveCacheVisible;
        Set<ResourceLocation> visible = computeUnlockedArchiveIds(task);
        archiveCacheDiskSource = diskSource;
        archiveCacheUnlockSource = unlockSource;
        return visible;
    }

    private Set<ResourceLocation> computeUnlockedArchiveIds(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task) {
        Set<ResourceLocation> diskIds = Set.copyOf(screen.workspaceDiskIds());
        Set<ResourceLocation> unlocked = Set.copyOf(com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
        if (task.id().equals(archiveCacheId) && diskIds.equals(archiveCacheDisks)
                && unlocked.equals(archiveCacheUnlocks)) return archiveCacheVisible;
        Set<ResourceLocation> available = new HashSet<>();
        ComputerGuideData.entriesFor(List.copyOf(diskIds), unlocked).forEach(entry -> available.add(entry.id()));
        archiveCacheId = task.id();
        archiveCacheDisks = diskIds;
        archiveCacheUnlocks = unlocked;
        archiveCacheVisible = Set.copyOf(available);
        return archiveCacheVisible;
    }

    private void renderDetail(GuiGraphics g, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task,
                                  int x, int y, int w, int h, Set<ResourceLocation> unlockedArchiveIds) {
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = task.greenTint();
        if (screen.hovered(x - 2, y, 105, 16)) screen.drawHover(g, x - 2, y, 105, 16);
        g.drawString(font, Component.literal("< BACK TO MAP"), x, y + 3, GREEN, false);
        String taskState = task.complete() ? "COMPLETE" : task.available() ? "AVAILABLE" : "LOCKED";
        g.drawString(font, Component.literal(taskState), x + w - font.width(taskState), y + 3,
                task.complete() ? PALE_GREEN : task.available() ? GREEN : 0xFF638063, false);
        g.fill(x, y + 17, x + w, y + 19, GREEN);
        screen.enableComputerScissor(g, x, y + 19, x + w, y + h - 17);
        detailViewTop = y + 19;
        detailViewBottom = y + h - 17;
        int line = y + 25 - detailScroll;
        line = detailRichText(g, text(task.title()), x, line, w, task.complete() ? PALE_GREEN : GREEN, task.renderMobFromSpawnEgg()) + 2;
        line = detailRichText(g, text(task.description()), x, line, w, PALE_GREEN, task.renderMobFromSpawnEgg()) + 3;
        for (String part : task.showcase()) line = renderShowcasePart(g, part, x, line, w);
        g.drawString(font, Component.literal(task.total() == 0 ? "PROGRESS // COMPLETE" : "PROGRESS " + task.done() + "/" + task.total()), x, line, GREEN, false);
        line += 11;
        if (task.total() > 0) {
            int barY = line;
            int barWidth = Math.max(1, w - 2);
            g.fill(x, barY, x + barWidth, barY + 4, DARK_GREEN);
            int filled = (int) ((long) barWidth * task.done() / task.total());
            if (filled > 0) g.fill(x, barY, x + filled, barY + 4, task.complete() ? PALE_GREEN : GREEN);
            line += 8;
        }
        if (!task.objectives().isEmpty()) {
            line = screen.renderTaskSectionLabel(g, "OBJECTIVES", x, line, w) + 3;
        }
        for (var objective : task.objectives()) {
            boolean done = objective.complete();
            String label = text(objective.description());
            String text = (done ? "[X] " : "[ ] ")
                    + objective.progress() + "/" + objective.displayGoal() + "  " + label;
            int objectiveColor = done ? PALE_GREEN : GREEN;
            if (!objective.item().isBlank()) {
                int rowY = line;
                int textBottom = richText().measure(text, line, w - 22);
                if (detailRowVisible(rowY, Math.max(textBottom, rowY + 17))) {
                    if (screen.hovered(x + 2, line, 16, 17)) screen.drawHover(g, x + 2, line, 16, 17);
                    screen.renderArchiveAsset(g, objective.item(), "", "", "", x + 9, line + 4, 14, 0, 0.8F, task.renderMobFromSpawnEgg());
                    line = richText().render(g, text, x + 20, line, w - 22, objectiveColor, task.renderMobFromSpawnEgg()) + 3;
                } else line = textBottom + 3;
                if (rowY >= y + 19 && rowY + 17 <= y + h - 17)
                    itemButtons.add(new ItemButton(objective.item(), x + 2, rowY, x + 18, rowY + 17));
                if (recipeItem.equals(objective.item())) line = screen.renderTaskItemRecipe(g, objective.item(), x + 20, line - 1, w - 22) + 3;
            } else line = detailRichText(g, text, x + 2, line, w - 4, objectiveColor, task.renderMobFromSpawnEgg()) + 3;
        }
        for (int section = 0; section < 2; section++) {
            List<String> requiredIds = section == 0 ? task.requires() : task.requiresAny();
            if (requiredIds.isEmpty()) continue;
            line += 3;
            line = screen.renderTaskSectionLabel(g, section == 0 ? "PREREQUISITES" : "ANY ONE OF", x, line, w) + 3;
            for (String requiredId : requiredIds) {
                var required = viewRowsById.get(requiredId);
                if (required != null && (!required.visible() || required.isLink())) required = null;
                String requiredTitle = required == null ? "SEALED FILE" : plain(required.title());
                boolean otherTab = required != null && !required.category().equals(task.category());
                if (otherTab) requiredTitle += "  // " + categoryLabel(required.category()).toUpperCase(Locale.ROOT);
                if (required != null) requiredTitle += "  >";
                String label = (required != null && required.complete() ? "[X] " : "[ ] ") + requiredTitle;
                int rowTop = line;
                List<net.minecraft.util.FormattedCharSequence> labelLines = screen.wrapLines(label, Math.max(1, w - 4));
                int rowHeight = labelLines.size() * 11;
                if (detailRowVisible(rowTop - 1, rowTop + rowHeight)) {
                    boolean rowHovered = required != null && screen.hovered(x + 1, rowTop - 1, w - 2, rowHeight + 1);
                    if (rowHovered) screen.drawHover(g, x + 1, rowTop - 1, w - 2, rowHeight + 1);
                    line = screen.wrap(g, label, x + 2, line, w - 4, rowHovered || required != null && required.complete() ? PALE_GREEN : GREEN);
                } else line = rowTop + screen.wrapLines(label, w - 4).size() * 11;
                if (required != null && rowTop - 1 >= y + 19 && line <= y + h - 17)
                    prerequisiteButtons.add(new PrerequisiteButton(required.id(), x + 1, rowTop - 1, x + w - 1, line));
                line += 2;
            }
            line += 3;
        }
        line += 4;
        if (task.hasRewards()) {
            line = screen.renderTaskSectionLabel(g, "REWARD", x, line, w) + 3;
            for (int rewardIndex = 0; rewardIndex < task.rewards().size(); rewardIndex++) {
                var reward = task.rewards().get(rewardIndex);
                String rewardText = rewardText(reward);
                if (reward.type().equals("choice")) {
                    line = screen.wrap(g, rewardText, x + 2, line, w - 4, GREEN) + 2;
                    String selectedPath = poolSelections.getOrDefault(task.id(), Map.of()).get(rewardIndex);
                    for (int optionIndex = 0; optionIndex < reward.options().size(); optionIndex++) {
                        var option = reward.options().get(optionIndex);
                        String path = "option:" + optionIndex;
                        boolean selected = path.equals(selectedPath);
                        int rowTop = line;
                        if (detailRowVisible(rowTop, rowTop + 30)) {
                            if (screen.hovered(x + 1, rowTop, w - 2, 30)) screen.drawHover(g, x + 1, rowTop, w - 2, 30);
                            if (selected) screen.box(g, x + 1, rowTop, x + w - 1, rowTop + 30, 0x5533AA55);
                            String itemIcon = choiceOptionIcon(option);
                            if (!itemIcon.isBlank()) screen.renderArchiveAsset(g, itemIcon, "", "", "", x + 9, rowTop + 8, 14, 0, 0.8F, task.renderMobFromSpawnEgg());
                            else {
                                g.fill(x + 2, rowTop + 7, x + 17, rowTop + 22, 0xFF183318);
                                g.drawCenteredString(font, Component.literal(AntOSPlayerText.currencySymbol()), x + 9, rowTop + 11, PALE_GREEN);
                            }
                            String optionTitle = Component.translatable(option.title()).getString();
                            String optionSummary = choiceOptionSummary(option);
                            g.drawString(font, Component.literal(truncateText(optionTitle, w - 25)), x + 20, rowTop + 3,
                                    selected ? PALE_GREEN : GREEN, false);
                            g.drawString(font, Component.literal(truncateText(optionSummary, w - 25)), x + 20, rowTop + 15,
                                    selected ? PALE_GREEN : GREEN, false);
                        }
                        if (rowTop >= y + 19 && rowTop + 30 <= y + h - 17)
                            poolChoiceButtons.add(new PoolChoiceButton(task.id(), rewardIndex, path, x + 1, rowTop, x + w - 1, rowTop + 30));
                        line += 32;
                    }
                } else if (reward.type().equals("item_pool")) {
                    List<PoolOutcome> outcomes = poolOutcomes(reward.pool(), "", 1, 0);
                    String selectedPath = poolSelections.getOrDefault(task.id(), Map.of()).get(rewardIndex);
                    if (reward.mode().equals("random")) {
                        if (!outcomes.isEmpty()) {
                            PoolOutcome preview = outcomes.get((int) ((System.currentTimeMillis() / 350L) % outcomes.size()));
                            screen.renderArchiveAsset(g, preview.item(), "", "", "", x + 9, line + 4, 14, 0, 0.8F, task.renderMobFromSpawnEgg());
                        }
                        line = screen.wrap(g, rewardText, x + 20, line, w - 22, GREEN) + 2;
                    } else {
                        line = screen.wrap(g, rewardText, x + 2, line, w - 4, GREEN) + 2;
                        for (PoolOutcome outcome : outcomes) {
                            boolean selected = outcome.path().equals(selectedPath);
                            int rowTop = line;
                            String label = outcome.count() + "x " + itemName(outcome.item()) + (selected ? "  [SELECTED]" : "");
                            int labelBottom = rowTop + 2 + screen.wrapLines(label, w - 22).size() * 11;
                            if (detailRowVisible(rowTop, Math.max(labelBottom, rowTop + 17))) {
                                if (screen.hovered(x + 1, rowTop, w - 2, 17)) screen.drawHover(g, x + 1, rowTop, w - 2, 17);
                                if (selected) screen.box(g, x + 1, rowTop, x + w - 1, rowTop + 17, 0x5533AA55);
                                screen.renderArchiveAsset(g, outcome.item(), "", "", "", x + 9, rowTop + 2, 14, 0, 0.8F, task.renderMobFromSpawnEgg());
                                line = screen.wrap(g, label, x + 20, rowTop + 2, w - 22, selected ? PALE_GREEN : GREEN) + 2;
                            } else line = labelBottom + 2;
                            if (rowTop >= y + 19 && rowTop + 17 <= y + h - 17)
                                poolChoiceButtons.add(new PoolChoiceButton(task.id(), rewardIndex, outcome.path(), x + 1, rowTop, x + w - 1, rowTop + 17));
                        }
                    }
                } else if (!reward.item().isBlank()) {
                    int rowY = line;
                    int textBottom = rowY + screen.wrapLines(rewardText, w - 22).size() * 11;
                    if (detailRowVisible(rowY, Math.max(textBottom, rowY + 17))) {
                        if (screen.hovered(x + 2, line, 16, 17)) screen.drawHover(g, x + 2, line, 16, 17);
                        screen.renderArchiveAsset(g, reward.item(), "", "", "", x + 9, line + 4, 14, 0, 0.8F, task.renderMobFromSpawnEgg());
                        line = screen.wrap(g, rewardText, x + 20, line, w - 22, GREEN) + 2;
                    } else line = textBottom + 2;
                    if (rowY >= y + 19 && rowY + 17 <= y + h - 17)
                        itemButtons.add(new ItemButton(reward.item(), x + 2, rowY, x + 18, rowY + 17));
                    if (recipeItem.equals(reward.item())) line = screen.renderTaskItemRecipe(g, reward.item(), x + 20, line - 1, w - 22) + 2;
                } else line = detailWrap(g, rewardText, x + 2, line, w - 4, GREEN) + 2;
            }
            boolean missingChoice = false;
            for (int rewardIndex = 0; rewardIndex < task.rewards().size(); rewardIndex++) {
                var reward = task.rewards().get(rewardIndex);
                if ((reward.type().equals("choice") || reward.type().equals("item_pool") && reward.mode().equals("choice"))
                        && !poolSelections.getOrDefault(task.id(), Map.of()).containsKey(rewardIndex)) missingChoice = true;
            }
            String rewardStatus = task.claimed() ? "REWARD CLAIMED" : task.claimable() ? missingChoice ? "CHOOSE A REWARD" : "READY TO CLAIM" : "";
            if (!rewardStatus.isBlank()) line = screen.wrap(g, rewardStatus, x, line, w, task.claimed() ? PALE_GREEN : GREEN) + 3;
            if (task.claimable()) {
                int buttonY = line - 1;
                if (buttonY >= y + 19 && buttonY + 16 <= y + h - 17) {
                    if (!missingChoice) claimButtons.add(new ClaimButton(task.id(), x + 1, buttonY, Math.min(x + w - 1, x + 101), buttonY + 15));
                    drawTeamButton(g, x + 1, buttonY, Math.min(w - 2, 100), missingChoice ? "SELECT REWARD" : "CLAIM REWARD", false);
                }
                line += 18;
            }
        }
        Set<String> archiveRewardIds = task.rewards().stream().filter(reward -> reward.type().equals("archive"))
                .map(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskReward::target)
                .collect(java.util.stream.Collectors.toSet());
        List<String> relatedArchives = task.archiveEntries().stream().filter(value -> !archiveRewardIds.contains(value)).toList();
        if (!relatedArchives.isEmpty()) line = screen.renderTaskSectionLabel(g, "RELATED ARCHIVE FILES", x, line, w) + 3;
        for (String archiveValue : relatedArchives) {
            ResourceLocation archiveId;
            try { archiveId = ResourceLocation.parse(archiveValue); } catch (RuntimeException ignored) { continue; }
            ComputerGuideData.Entry entry = ComputerGuideData.entry(archiveId);
            String label = entry == null ? archiveId.toString() : AntOSPlayerText.apply(ComputerGuideData.archiveTitle(entry).getString());
            int buttonY = line - 2;
            boolean unlocked = unlockedArchiveIds.contains(archiveId);
            String archiveLabel = (unlocked ? "ARCHIVE // " : "ARCHIVE LOCKED // ") + label;
            int labelBottom = line + screen.wrapLines(archiveLabel, w - 4).size() * 11;
            boolean rowVisible = detailRowVisible(buttonY, Math.max(labelBottom, buttonY + 13));
            if (unlocked) {
                if (buttonY >= y + 19 && buttonY + 13 <= y + h - 17) {
                    archiveButtons.add(new ArchiveButton(archiveId, x + 1, buttonY, x + w - 1, buttonY + 13));
                }
                if (rowVisible && screen.hovered(x + 1, buttonY, w - 2, 13)) screen.drawHover(g, x + 1, buttonY, w - 2, 13);
            }
            line = rowVisible ? screen.wrap(g, archiveLabel, x + 2, line, w - 4, unlocked ? PALE_GREEN : 0xFF638063) + 2 : labelBottom + 2;
        }
        g.disableScissor();
        int contentBottom = line + detailScroll;
        int maximumScroll = Math.max(0, contentBottom - (y + h - 22));
        detailMaximumScroll = maximumScroll;
        detailScroll = Math.max(0, Math.min(detailScroll, maximumScroll));
        if (maximumScroll > 0) g.drawString(font, Component.literal("SCROLL FOR DETAILS"), x + 2, y + h - 12, PALE_GREEN, false);
        screen.archiveGreenTint = previousTint;
    }

    private boolean detailRowVisible(int top, int bottom) {
        return bottom + 24 >= detailViewTop && top - 24 <= detailViewBottom;
    }

    private int detailRichText(GuiGraphics g, String text, int x, int line, int width, int color, boolean renderMobFromSpawnEgg) {
        int bottom = richText().measure(text, line, width);
        if (!detailRowVisible(line, bottom)) return bottom;
        return richText().render(g, text, x, line, width, color, renderMobFromSpawnEgg);
    }

    private int detailWrap(GuiGraphics g, String text, int x, int line, int width, int color) {
        int bottom = line + screen.wrapLines(text, width).size() * 11;
        if (!detailRowVisible(line, bottom)) return bottom;
        return screen.wrap(g, text, x, line, width, color);
    }

    private int renderShowcasePart(GuiGraphics g, String part, int x, int y, int w) {
        if (part.startsWith("@item:")) return screen.renderArchivePreview(g, part.substring(6), "", "", x, y, w) + 4;
        if (part.startsWith("@entity:")) return screen.renderArchivePreview(g, "", part.substring(8), "", x, y, w) + 4;
        if (part.startsWith("@recipe:")) return screen.renderArchiveRecipe(g, part.substring(8), x, y, Math.min(w, 88), 16) ? y + 52 : y;
        return y;
    }

    private String rewardText(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskReward reward) {
        return switch (reward.type()) {
            case "item" -> {
                String name = reward.item();
                try {
                    var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(reward.item())).orElse(null);
                    if (item != null) name = Component.translatable(item.getDescriptionId()).getString();
                } catch (RuntimeException ignored) { }
                yield reward.count() + "x " + name;
            }
            case "experience" -> reward.experience() + " experience points";
            case "experience_levels" -> reward.count() + " experience levels";
            case "antcoins" -> reward.antcoins() + " " + AntOSPlayerText.currencyName();
            case "item_pool" -> reward.mode().equals("choice") ? "Choose one reward:" : "Random reward pool";
            case "choice" -> "Choose one reward package:";
            case "archive" -> {
                try {
                    var entry = ComputerGuideData.entry(ResourceLocation.parse(reward.target()));
                    yield "Archive: " + (entry == null ? reward.target() : AntOSPlayerText.apply(ComputerGuideData.archiveTitle(entry).getString()));
                } catch (RuntimeException ignored) { yield "Archive: " + reward.target(); }
            }
            case "mail" -> "Antmail: " + reward.subject();
            case "effect" -> "Effect: " + reward.target();
            case "advancement" -> "Advancement: " + reward.target();
            default -> reward.type() + (reward.target().isBlank() ? "" : ": " + reward.target());
        };
    }

    private List<PoolOutcome> poolOutcomes(String poolId, String prefix, int multiplier, int depth) {
        if (poolId == null || poolId.isBlank() || depth > 8) return List.of();
        var pool = com.craisinlord.antos.content.client.ComputerTasksClientState.rewardPool(poolId);
        if (pool == null) return List.of();
        List<PoolOutcome> result = new ArrayList<>();
        for (int index = 0; index < pool.entries().size() && result.size() < 64; index++) {
            var entry = pool.entries().get(index);
            String path = prefix.isBlank() ? Integer.toString(index) : prefix + "." + index;
            int count = (int) Math.min(108L, (long) multiplier * entry.count());
            if (!entry.item().isBlank()) result.add(new PoolOutcome(path, entry.item(), count));
            else result.addAll(poolOutcomes(entry.pool(), path, count, depth + 1).stream().limit(64 - result.size()).toList());
        }
        return List.copyOf(result);
    }

    private String itemName(String itemId) {
        try {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
            if (item != null) return Component.translatable(item.getDescriptionId()).getString();
        } catch (RuntimeException ignored) { }
        return itemId;
    }

    private String choiceOptionSummary(com.craisinlord.antos.content.client.ComputerTasksClientState.RewardOption option) {
        return option.rewards().stream().map(reward -> switch (reward.type()) {
            case "item" -> reward.count() + "x " + itemName(reward.item());
            case "antcoins" -> reward.antcoins() + " " + AntOSPlayerText.currencyName();
            case "item_pool" -> "Random item";
            case "experience" -> reward.experience() + " XP";
            case "experience_levels" -> reward.count() + " levels";
            case "effect" -> "Effect";
            case "advancement" -> "Advancement";
            case "mail" -> "Antmail";
            default -> reward.type();
        }).collect(java.util.stream.Collectors.joining(" + "));
    }

    private String truncateText(String value, int width) {
        if (Minecraft.getInstance().font.width(value) <= width) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && Minecraft.getInstance().font.width(value.substring(0, end) + suffix) > width) end--;
        return value.substring(0, end) + suffix;
    }

    private String choiceOptionIcon(com.craisinlord.antos.content.client.ComputerTasksClientState.RewardOption option) {
        for (var reward : option.rewards()) {
            if (reward.type().equals("item") && !reward.item().isBlank()) return reward.item();
            if (reward.type().equals("item_pool")) {
                List<PoolOutcome> outcomes = poolOutcomes(reward.pool(), "", 1, 0);
                if (!outcomes.isEmpty()) return outcomes.get((int) ((System.currentTimeMillis() / 350L) % outcomes.size())).item();
            }
        }
        return "";
    }

    private List<SidebarRow> visibleSidebarRows() {
        ensureView();
        return viewSidebarRows;
    }

    private List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> rowsFor(String category) {
        ensureView();
        return viewRowsByCategory.getOrDefault(category, List.of());
    }

    private TaskRichText richText() {
        if (richText == null) richText = new TaskRichText(screen, font);
        return richText;
    }

    private String plain(String key) {
        return TaskMarkup.strip(text(key));
    }

    private String text(String key) {
        String cached = translations.get(key);
        if (cached == null) {
            cached = AntOSPlayerText.apply(Component.translatable(key).getString());
            translations.put(key, cached);
        }
        return cached;
    }

    private static String sidebarKey(SidebarRow row) {
        return row.category().isBlank() ? "group:" + row.group() : "category:" + row.category();
    }

    private void ensureView() {
        int revision = com.craisinlord.antos.content.client.ComputerTasksClientState.revision();
        String key = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!viewDirty && revision == viewRevision && key.equals(viewKey) && language.equals(viewLanguage)) return;
        viewDirty = false;
        viewRevision = revision;
        viewKey = key;
        viewLanguage = language;
        translations.clear();
        richText().clearCache();
        layoutCache.clear();
        nodeCache = null;
        nodeCacheById = null;
        nodeCacheTasks = null;
        limitCache = null;
        limitCacheTasks = null;
        searchCacheQuery = null;
        viewAllRows = com.craisinlord.antos.content.client.ComputerTasksClientState.get();
        viewVisibleRows = viewAllRows.stream().filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::visible).toList();
        Map<String, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> byId = new HashMap<>();
        for (var row : viewAllRows) byId.put(row.id(), row);
        Map<String, List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow>> byCategory = new LinkedHashMap<>();
        Map<String, int[]> counts = new HashMap<>();
        for (var row : viewVisibleRows) {
            byCategory.computeIfAbsent(row.category(), ignored -> new ArrayList<>()).add(row);
            int[] count = counts.computeIfAbsent(row.category(), ignored -> new int[3]);
            if (row.isLink()) continue;
            count[0]++;
            if (row.complete()) count[1]++;
            if (row.claimable()) count[2]++;
        }
        byCategory.replaceAll((category, rows) -> List.copyOf(rows));
        viewRowsById = byId;
        viewRowsByCategory = byCategory;
        viewCategories = orderedCategories(viewVisibleRows);
        viewSidebarRows = sidebarRows(viewVisibleRows);
        viewSidebarCounts.clear();
        for (SidebarRow row : viewSidebarRows) {
            int[] count = new int[3];
            if (row.category().isBlank()) {
                for (String category : viewCategories) {
                    if (!row.group().equals(categoryGroup(category))) continue;
                    int[] categoryCount = counts.getOrDefault(category, new int[3]);
                    for (int i = 0; i < 3; i++) count[i] += categoryCount[i];
                }
            } else count = counts.getOrDefault(row.category(), count);
            String label = count[1] + "/" + count[0] + (count[2] > 0 ? " !" + count[2] : "");
            if (row.category().isBlank()) label = (groupCollapsed(row.group()) ? "+ " : "- ") + label;
            viewSidebarCounts.put(sidebarKey(row), new SidebarCount(label, count[2] > 0));
        }
        observeStatusChanges(viewVisibleRows);
    }

    private record SidebarCount(String label, boolean claimable) { }

    private record Cell(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task, int column, int row) { }

    private record SidebarRow(String group, String category, String label, String iconItem, String iconEntity, int indent, boolean greenTint, boolean renderMobFromSpawnEgg) { }

    private void drawSidebarLabel(GuiGraphics g, String label, int x, int y, int width, int color, boolean hovered) {
        int labelWidth = font.width(label);
        if (labelWidth <= width) {
            g.drawString(font, Component.literal(label), x, y, color, false);
            return;
        }
        if (!hovered) {
            g.drawString(font, Component.literal(screen.trimToWidth(label, width)), x, y, color, false);
            return;
        }
        int overflow = labelWidth - width;
        long travelTime = overflow * 45L;
        long pauseTime = 900L;
        long cycleTime = pauseTime * 2 + travelTime * 2;
        long phase = System.currentTimeMillis() % cycleTime;
        int offset;
        if (phase < pauseTime) offset = 0;
        else if (phase < pauseTime + travelTime) offset = (int) ((phase - pauseTime) / 45L);
        else if (phase < pauseTime * 2 + travelTime) offset = overflow;
        else offset = overflow - (int) ((phase - pauseTime * 2 - travelTime) / 45L);
        int start = font.plainSubstrByWidth(label, offset).length();
        String visibleLabel = font.plainSubstrByWidth(label.substring(start), width);
        g.drawString(font, Component.literal(visibleLabel), x, y, color, false);
    }

    private record SidebarEntry(String group, String category, int sortOrder, boolean defined) { }

    private List<String> orderedCategories(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> rows) {
        List<String> result = new ArrayList<>();
        for (SidebarEntry entry : sidebarEntries(rows)) {
            if (entry.group().isBlank()) result.add(entry.category());
            else result.addAll(groupCategories(entry.group(), rows));
        }
        return result;
    }

    private List<SidebarRow> sidebarRows(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> rows) {
        var groups = com.craisinlord.antos.content.client.ComputerTasksClientState.groups();
        List<SidebarRow> result = new ArrayList<>();
        for (SidebarEntry entry : sidebarEntries(rows)) {
            if (entry.group().isBlank()) {
                result.add(categorySidebarRow(entry.category(), 0));
                continue;
            }
            var group = groups.get(entry.group());
            result.add(new SidebarRow(group.id(), "", text(group.title()),
                    group.iconItem(), group.iconEntity(), 0, group.greenTint(), group.renderMobFromSpawnEgg()));
            if (!groupCollapsed(group.id())) for (String category : groupCategories(group.id(), rows)) result.add(categorySidebarRow(category, 8));
        }
        return result;
    }

    private List<SidebarEntry> sidebarEntries(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> rows) {
        Map<String, SidebarEntry> entries = new LinkedHashMap<>();
        for (String category : rows.stream().map(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::category).distinct().toList()) {
            String group = categoryGroup(category);
            if (!group.isBlank()) {
                var groupInfo = com.craisinlord.antos.content.client.ComputerTasksClientState.groups().get(group);
                entries.putIfAbsent("group:" + group, new SidebarEntry(group, "", groupInfo.sortOrder(), true));
                continue;
            }
            var info = com.craisinlord.antos.content.client.ComputerTasksClientState.category(category);
            entries.put("category:" + category, new SidebarEntry("", category, info == null ? 0 : info.sortOrder(), info != null));
        }
        return entries.values().stream().sorted(Comparator.comparing((SidebarEntry entry) -> !entry.defined())
                .thenComparingInt(entry -> entry.defined() ? entry.sortOrder() : 0)
                .thenComparing(entry -> entry.group().isBlank() ? entry.category() : entry.group())).toList();
    }

    private List<String> groupCategories(String group, List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> rows) {
        return rows.stream().map(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::category).distinct()
                .filter(category -> group.equals(categoryGroup(category)))
                .sorted(Comparator.comparingInt((String category) -> com.craisinlord.antos.content.client.ComputerTasksClientState.category(category).sortOrder())
                        .thenComparing(category -> category))
                .toList();
    }

    private String categoryGroup(String category) {
        var info = com.craisinlord.antos.content.client.ComputerTasksClientState.category(category);
        if (info == null || info.group().isBlank()) return "";
        return com.craisinlord.antos.content.client.ComputerTasksClientState.groups().containsKey(info.group()) ? info.group() : "";
    }

    private SidebarRow categorySidebarRow(String category, int indent) {
        var info = com.craisinlord.antos.content.client.ComputerTasksClientState.category(category);
        return new SidebarRow("", category, categoryLabel(category),
                info == null ? "" : info.iconItem(), info == null ? "" : info.iconEntity(), indent,
                info == null || info.greenTint(), info == null || info.renderMobFromSpawnEgg());
    }

    private String categoryLabel(String category) {
        var info = com.craisinlord.antos.content.client.ComputerTasksClientState.category(category);
        return text(info == null ? categoryTitleKey(category) : info.title());
    }

    private boolean groupCollapsed(String group) {
        Boolean stored = AntOSSettings.taskGroupCollapsed(group);
        if (stored != null) return stored;
        var info = com.craisinlord.antos.content.client.ComputerTasksClientState.groups().get(group);
        return info != null && info.collapsed();
    }

    private String categoryTitleKey(String category) {
        try {
            ResourceLocation id = ResourceLocation.parse(category);
            return "task.category." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        } catch (RuntimeException ignored) { return category; }
    }

    private static float defaultMapZoom() {
        return Integer.parseInt(AntOSSettings.preferences().taskMapZoom) / 100.0F;
    }

    void resetMapZoom() {
        mapZoom = defaultMapZoom();
    }

    private List<Node> nodes(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks,
                                     int mapX, int mapY, int offsetX, int offsetY, float zoom) {
        if (nodeCache != null && nodeCacheTasks == tasks && nodeCacheMapX == mapX && nodeCacheMapY == mapY
                && nodeCacheOffsetX == offsetX && nodeCacheOffsetY == offsetY && Float.compare(nodeCacheZoom, zoom) == 0) return nodeCache;
        List<Node> result = buildNodes(tasks, mapX, mapY, offsetX, offsetY, zoom);
        Map<String, Node> byId = new HashMap<>();
        for (Node node : result) byId.put(node.task().id(), node);
        nodeCacheTasks = tasks;
        nodeCacheMapX = mapX;
        nodeCacheMapY = mapY;
        nodeCacheOffsetX = offsetX;
        nodeCacheOffsetY = offsetY;
        nodeCacheZoom = zoom;
        nodeCache = result;
        nodeCacheById = byId;
        return result;
    }

    private List<Node> buildNodes(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks,
                                     int mapX, int mapY, int offsetX, int offsetY, float zoom) {
        List<Cell> cells = cells(tasks);
        List<Node> result = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            float nodeSize = Math.max(8, NODE_SIZE * zoom * cell.task().appearance().size());
            float left = mapX + 7 + cell.column() * NODE_GRID * zoom - offsetX + (NODE_SIZE * zoom - nodeSize) / 2;
            float top = mapY + 8 + cell.row() * NODE_GRID * zoom - offsetY + (NODE_SIZE * zoom - nodeSize) / 2;
            result.add(new Node(cell.task(), (int) Math.floor(left), (int) Math.floor(top),
                    (int) Math.ceil(left + nodeSize), (int) Math.ceil(top + nodeSize), left, top, nodeSize));
        }
        return List.copyOf(result);
    }

    private List<Cell> cells(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks) {
        List<Cell> cached = layoutCache.get(tasks);
        if (cached != null) return cached;
        Map<String, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> byId = new HashMap<>();
        tasks.forEach(task -> byId.put(task.id(), task));
        Map<String, Integer> depths = new HashMap<>();
        for (var task : tasks) depth(task, byId, depths, new HashSet<>());
        Map<Integer, Integer> rowsByColumn = new HashMap<>();
        Set<Long> occupiedCells = new HashSet<>();
        for (var task : tasks) if (task.hasPosition()) occupiedCells.add(cellKey(task.x(), task.y()));
        List<Cell> placed = new ArrayList<>();
        for (var task : tasks) if (task.hasPosition()) placed.add(new Cell(task, task.x(), task.y()));
        List<Cell> result = new ArrayList<>();
        for (var task : tasks.stream().sorted(java.util.Comparator.comparingInt((com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow value) -> depths.getOrDefault(value.id(), 0)).thenComparing(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::id)).toList()) {
            int column = depths.getOrDefault(task.id(), 0);
            int row;
            if (task.hasPosition()) {
                column = task.x();
                row = task.y();
            } else {
                row = rowsByColumn.getOrDefault(column, 0);
                while (occupiedCells.contains(cellKey(column, row)) || overlapsPlaced(new Cell(task, column, row), placed)) row++;
                rowsByColumn.put(column, row + 1);
                occupiedCells.add(cellKey(column, row));
                placed.add(new Cell(task, column, row));
            }
            result.add(new Cell(task, column, row));
        }
        List<Cell> cells = List.copyOf(result);
        if (tasks.isEmpty() || viewRowsByCategory.get(tasks.get(0).category()) == tasks) layoutCache.put(tasks, cells);
        return cells;
    }

    private static long cellKey(int column, int row) {
        return ((long) column << 32) | (row & 0xFFFFFFFFL);
    }

    private static boolean overlapsPlaced(Cell candidate, List<Cell> placed) {
        for (Cell other : placed) {
            double extent = (layoutSize(candidate) + layoutSize(other)) / 2.0;
            if (Math.abs((long) candidate.column() - other.column()) * NODE_GRID < extent + 8
                    && Math.abs((long) candidate.row() - other.row()) * NODE_GRID < extent + 18) return true;
        }
        return false;
    }

    private static double layoutSize(Cell cell) {
        double size = NODE_SIZE * cell.task().appearance().size();
        double icon = (NODE_SIZE - 6) * cell.task().appearance().size() * cell.task().appearance().shape().iconFit() * cell.task().appearance().iconScale();
        return Math.max(size, icon);
    }

    private int[] mapScrollLimits(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks,
                                      int mapX, int mapY, int mapW, int mapH) {
        if (limitCache != null && limitCacheTasks == tasks && limitCacheMapX == mapX && limitCacheMapY == mapY
                && limitCacheMapW == mapW && limitCacheMapH == mapH && Float.compare(limitCacheZoom, mapZoom) == 0) return limitCache.clone();
        List<Node> nodes = buildNodes(tasks, mapX, mapY, 0, 0, mapZoom);
        int left = nodes.stream().mapToInt(Node::visualLeft).min().orElse(mapX + mapW / 2);
        int right = nodes.stream().mapToInt(Node::visualRight).max().orElse(mapX + mapW);
        int top = nodes.stream().mapToInt(Node::visualTop).min().orElse(mapY + mapH / 2);
        int bottom = nodes.stream().mapToInt(Node::visualBottom).max().orElse(mapY + mapH);
        int minimumX = Math.min(0, left - (mapX + mapW - 8));
        int maximumX = Math.max(0, right - (mapX + 8));
        int minimumY = Math.min(0, top - (mapY + mapH - 8));
        int maximumY = Math.max(0, bottom - (mapY + 8));
        limitCacheTasks = tasks;
        limitCacheMapX = mapX;
        limitCacheMapY = mapY;
        limitCacheMapW = mapW;
        limitCacheMapH = mapH;
        limitCacheZoom = mapZoom;
        limitCache = new int[]{minimumX, maximumX, minimumY, maximumY};
        return limitCache.clone();
    }

    private List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> searchResults() {
        ensureView();
        String query = searchText.strip().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return List.of();
        if (query.equals(searchCacheQuery)) return searchCacheResults;
        searchCacheResults = viewVisibleRows.stream()
                .filter(task -> !task.isLink())
                .filter(task -> plain(task.title()).toLowerCase(Locale.ROOT).contains(query)
                        || plain(task.description()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing((com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task) -> task.complete())
                        .thenComparing(task -> plain(task.title()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        searchCacheQuery = query;
        return searchCacheResults;
    }

    private void centerMapOn(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks,
                                 int mapX, int mapY, int mapW, int mapH, String taskId) {
        List<Node> nodes = nodes(tasks, mapX, mapY, 0, 0, mapZoom);
        if (nodes.isEmpty()) {
            mapScrollX = 0;
            mapScrollY = 0;
            return;
        }
        int left;
        int right;
        int top;
        int bottom;
        if (taskId != null && !taskId.isBlank()) {
            Node target = nodes.stream().filter(node -> node.task().id().equals(taskId)).findFirst().orElse(null);
            if (target == null) return;
            left = target.left();
            right = target.right();
            top = target.top();
            bottom = target.bottom();
        } else {
            left = nodes.stream().mapToInt(Node::visualLeft).min().orElse(mapX);
            right = nodes.stream().mapToInt(Node::visualRight).max().orElse(mapX + mapW);
            top = nodes.stream().mapToInt(Node::visualTop).min().orElse(mapY);
            bottom = nodes.stream().mapToInt(Node::visualBottom).max().orElse(mapY + mapH);
        }
        int centerX = (left + right) / 2;
        int centerY = (top + bottom) / 2;
        int[] limits = mapScrollLimits(tasks, mapX, mapY, mapW, mapH);
        mapScrollX = Math.max(limits[0], Math.min(limits[1], centerX - (mapX + mapW / 2)));
        mapScrollY = Math.max(limits[2], Math.min(limits[3], centerY - (mapY + mapH / 2)));
    }

    private void openSearchResult(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task) {
        selectedCategory = task.category();
        selectedTaskId = task.id();
        recipeItem = "";
        detailScroll = 0;
        detailMaximumScroll = 0;
        sidebarClickOpen = false;
        searchFocused = false;
        controlsOpen = false;
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows = rowsFor(task.category());
        if (screen.activeWindow == null || !screen.activeWindow.type.equals("TASKS")) return;
        int sidebarX = -WIDTH / 2 + screen.windowLocalX(screen.activeWindow) + 8;
        int sidebarY = -HEIGHT / 2 + screen.windowLocalY(screen.activeWindow) + 28;
        int contentWidth = screen.windowWidth(screen.activeWindow) - 16;
        int contentHeight = screen.windowHeight(screen.activeWindow) - 34;
        boolean sidebarShown = sidebarPinned || sidebarHoverOpen;
        int contentOffset = sidebarShown ? 133 : 30;
        centerMapOn(categoryRows, sidebarX + contentOffset, sidebarY + 18,
                contentWidth - contentOffset, Math.max(1, contentHeight - 36), task.id());
    }

    private void centerCurrentMap() {
        if (screen.activeWindow == null || !screen.activeWindow.type.equals("TASKS")) return;
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows = rowsFor(selectedCategory);
        int sidebarX = -WIDTH / 2 + screen.windowLocalX(screen.activeWindow) + 8;
        int sidebarY = -HEIGHT / 2 + screen.windowLocalY(screen.activeWindow) + 28;
        int contentWidth = screen.windowWidth(screen.activeWindow) - 16;
        int contentHeight = screen.windowHeight(screen.activeWindow) - 34;
        boolean sidebarShown = sidebarPinned || sidebarClickOpen || sidebarHoverOpen;
        int contentOffset = sidebarShown ? 133 : 30;
        centerMapOn(categoryRows, sidebarX + contentOffset, sidebarY + 18,
                contentWidth - contentOffset, Math.max(1, contentHeight - 36), "");
    }

    private List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRowsForSelected() {
        return rowsFor(selectedCategory);
    }

    private int depth(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task,
                          Map<String, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> byId,
                          Map<String, Integer> cache, Set<String> visiting) {
        if (cache.containsKey(task.id())) return cache.get(task.id());
        if (!visiting.add(task.id())) return 0;
        int depth = 0;
        for (String requiredId : java.util.stream.Stream.concat(task.requires().stream(), task.requiresAny().stream()).toList()) {
            var required = byId.get(requiredId);
            if (required != null && required.category().equals(task.category())) depth = Math.max(depth, depth(required, byId, cache, visiting) + 1);
        }
        visiting.remove(task.id()); cache.put(task.id(), depth); return depth;
    }

    private void renderConnection(GuiGraphics g, Node from, Node to, boolean complete, TaskAppearance appearance) {
        TaskAppearance.LineAxis axis = appearance.lineAxis();
        float bend = appearance.lineBend();
        int color = complete ? GREEN : 0xFF356035;
        int dx = Math.abs(to.centerX() - from.centerX()), dy = Math.abs(to.centerY() - from.centerY());
        boolean vertical = dx <= 2 || dy > 2 && switch (axis) {
            case VERTICAL -> true;
            case HORIZONTAL -> false;
            case AUTO -> dy > dx + 2;
        };
        if (vertical) {
            boolean down = to.centerY() >= from.centerY();
            int x1 = from.centerX(), y1 = from.edgeY(down);
            int x2 = to.centerX(), y2 = to.edgeY(!down);
            int mid = Math.round(y1 + (y2 - y1) * bend);
            segment(g, x1 - 1, Math.min(y1, mid), x1 + 1, Math.max(y1, mid) + 1, color);
            segment(g, Math.min(x1, x2), mid - 1, Math.max(x1, x2) + 1, mid + 1, color);
            segment(g, x2 - 1, Math.min(mid, y2), x2 + 1, Math.max(mid, y2) + 1, color);
            return;
        }
        boolean forward = to.centerX() >= from.centerX();
        int x1 = from.edgeX(forward), y1 = from.centerY();
        int x2 = to.edgeX(!forward), y2 = to.centerY();
        int mid = Math.round(x1 + (x2 - x1) * bend);
        segment(g, Math.min(x1, mid), y1 - 1, Math.max(x1, mid) + 1, y1 + 1, color);
        segment(g, mid - 1, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, color);
        segment(g, Math.min(mid, x2), y2 - 1, Math.max(mid, x2) + 1, y2 + 1, color);
    }

    private void segment(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, bottom, color);
        occupied.add(new Occupied(left, top, right, bottom, null));
    }

    private boolean labelBlocked(Node node, int left, int top, int right, int bottom) {
        for (Occupied area : occupied) {
            if (area.owner() == node) continue;
            if (area.left() <= right && area.right() >= left && area.top() <= bottom && area.bottom() >= top) return true;
        }
        return false;
    }

    private int[] badgeBounds(Node node) {
        int nodeSize = node.right() - node.left();
        int badgeX = node.left() + (int) Math.round(nodeSize * node.task().appearance().shape().edge(0.25, true));
        int badgeY = node.task().appearance().shape() == TaskAppearance.Shape.SQUARE ? node.top() - 3 : node.top() + nodeSize / 4 - 8;
        return new int[] {badgeX - 9, badgeY, badgeX + 2, badgeY + 11};
    }

    private record Occupied(int left, int top, int right, int bottom, Node owner) { }

    private void renderNode(GuiGraphics g, Node node) {
        var task = node.task();
        int nodeSize = node.right() - node.left();
        int border = task.complete() ? PALE_GREEN : task.available() ? GREEN : 0xFF476047;
        boolean isHovered = mapPointerFree && node.contains(screen.session.mouseX, screen.session.mouseY);
        TaskShapeRenderer.draw(g, task.appearance().shape(), node.shapeLeft(), node.shapeTop(), node.shapeSize(),
                task.available() ? 0xFF0B180B : 0xFF080D08, border, task.available() ? GREEN : 0xFF638063, isHovered);
        renderIcon(g, task, node.centerX(), node.centerY(), node.iconSize());
        String state = task.isLink() ? "OPEN >" : task.complete() ? "DONE" : task.available() ? task.done() + "/" + task.total() : "";
        int stateColor = task.available() || task.complete() ? PALE_GREEN : 0xFF638063;
        if (task.claimable()) {
            int[] badge = badgeBounds(node);
            g.fill(badge[0], badge[1], badge[2], badge[3], 0xFFB8FFB8);
            g.drawCenteredString(font, Component.literal("!"), badge[0] + 5, badge[1] + 1, 0xFF071007);
        }
        int labelTop = node.bottom() + 3;
        if (task.available() || task.complete()) {
            int labelWidth = font.width(state);
            int labelLeft = node.left() + (nodeSize - labelWidth) / 2;
            if (!labelBlocked(node, labelLeft, labelTop, labelLeft + labelWidth, labelTop + font.lineHeight))
                g.drawString(font, Component.literal(state), labelLeft, labelTop, stateColor, false);
        } else {
            int lockX = node.left() + nodeSize / 2;
            if (!labelBlocked(node, lockX - 4, labelTop, lockX + 4, labelTop + 9))
                renderLockIcon(g, lockX, labelTop, 0xFF87B787);
        }
    }

    private void renderHoverTitle(GuiGraphics g, Node node, int mapLeft, int mapTop, int mapRight) {
        if (!mapPointerFree || !node.contains(screen.session.mouseX, screen.session.mouseY)) return;
        var task = node.task();
        int nodeSize = node.right() - node.left();
        int border = task.complete() ? PALE_GREEN : task.available() ? GREEN : 0xFF638063;
        String title = screen.trimToWidth(plain(task.title()) + (task.isLink() ? "  >" : ""), 120);
        int titleWidth = font.width(title) + 8;
        int titleX = Math.max(mapLeft + 2, Math.min(node.left() + (nodeSize - titleWidth) / 2, mapRight - titleWidth - 2));
        int titleY = Math.max(mapTop + 2, node.top() - 15);
        g.fill(titleX - 2, titleY, titleX + titleWidth, titleY + 13, 0xFF071007);
        screen.box(g, titleX - 2, titleY, titleX + titleWidth, titleY + 13, GREEN);
        g.drawCenteredString(font, Component.literal(title), titleX + titleWidth / 2, titleY + 2, border);
    }

    private void renderLockIcon(GuiGraphics g, int centerX, int top, int color) {
        g.fill(centerX - 2, top, centerX + 2, top + 1, color);
        g.fill(centerX - 3, top + 1, centerX - 2, top + 4, color);
        g.fill(centerX + 2, top + 1, centerX + 3, top + 4, color);
        g.fill(centerX - 4, top + 4, centerX + 4, top + 9, color);
        g.fill(centerX - 2, top + 5, centerX + 2, top + 8, 0xFF071007);
        g.fill(centerX, top + 6, centerX + 1, top + 7, PALE_GREEN);
    }

    private void renderIcon(GuiGraphics g, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task, int centerX, int centerY, float size) {
        if (!task.iconTexture().isBlank()) {
            var texture = ResourceLocation.tryParse(task.iconTexture());
            if (texture != null) {
                g.pose().pushPose();
                g.pose().translate(centerX, centerY, 0.0F);
                g.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
                g.blit(texture, -8, -8, 16, 16, 0, 0, 16, 16, 16, 16);
                g.pose().popPose();
                return;
            }
        }
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = task.greenTint();
        float scale = size / ICON_REFERENCE_SIZE;
        try {
            screen.renderArchiveAsset(g, task.iconItem(), task.iconEntity(), "", "", centerX, centerY, ICON_REFERENCE_SIZE, 0.0F, 0.9F, task.renderMobFromSpawnEgg(), scale);
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }

    private record Node(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task, int left, int top, int right, int bottom,
                        float shapeLeft, float shapeTop, float shapeSize) {
        int centerX() { return Math.round(shapeLeft + shapeSize / 2); }
        int centerY() { return Math.round(shapeTop + shapeSize / 2); }
        float iconSize() {
            return (float) (shapeSize * (NODE_SIZE - 6) / (double) NODE_SIZE * task.appearance().shape().iconFit() * task.appearance().iconScale());
        }
        int overflow() {
            float itemSize = ICON_ITEM_SIZE * iconSize() / ICON_REFERENCE_SIZE;
            return Math.max(0, (int) Math.ceil((Math.max(iconSize(), itemSize) - (right - left)) / 2));
        }
        int visualLeft() { return left - overflow() - 4; }
        int visualRight() { return right + overflow() + 4; }
        int visualTop() { return top - overflow() - 8; }
        int visualBottom() { return bottom + Math.max(14, overflow()); }
        int edgeX(boolean rightSide) {
            return Math.round(shapeLeft + (float) (shapeSize * task.appearance().shape().edge(0.5, rightSide)));
        }
        int edgeY(boolean bottomSide) {
            return Math.round(shapeTop + (float) (shapeSize * task.appearance().shape().edgeVertical(0.5, bottomSide)));
        }
        boolean contains(double x, double y) {
            return task.appearance().shape().contains((x - shapeLeft) / shapeSize, (y - shapeTop) / shapeSize);
        }
    }

    private record ArchiveButton(ResourceLocation entryId, int left, int top, int right, int bottom) { }

    private record ClaimButton(String taskId, int left, int top, int right, int bottom) { }

    private record ItemButton(String itemId, int left, int top, int right, int bottom) { }
    private record PrerequisiteButton(String taskId, int left, int top, int right, int bottom) { }
    private record PoolChoiceButton(String taskId, int rewardIndex, String path, int left, int top, int right, int bottom) { }
    private record PoolOutcome(String path, String item, int count) { }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        if (button != 0) return true;
        if (!pendingJumpTaskId.isBlank()) {
            if (screen.inside(jumpConfirmButton[0], jumpConfirmButton[1], jumpConfirmButton[2], jumpConfirmButton[3], mouseX, mouseY)) confirmJump();
            else pendingJumpTaskId = "";
            return true;
        }
        int[] teamChip = teamChip(contentX, contentY, contentW, contentH);
        if (screen.inside(teamChip[0] + 2, teamChip[1] + 2, TEAM_BUTTON_WIDTH, CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
            teamMenuOpen = !teamMenuOpen;
            searchFocused = false;
            controlsOpen = false;
            teamInviteFocused = false;
            return true;
        }
        int teamMenuX = contentX + contentW - 232;
        int teamMenuY = contentY + contentH - 17 - teamMenuHeight();
        if (teamMenuOpen && screen.inside(teamMenuX, teamMenuY, 228, teamMenuHeight(), mouseX, mouseY)) {
            var team = com.craisinlord.antos.content.client.ComputerTasksClientState.team();
            List<com.craisinlord.antos.content.client.ComputerTasksClientState.TeamInvite> invites =
                    com.craisinlord.antos.content.client.ComputerTasksClientState.invites();
            if (team == null && screen.inside(teamMenuX + 4, teamMenuY + 20, 84, 15, mouseX, mouseY)) {
                ComputerNetworking.createTeam();
            } else if (team == null && !invites.isEmpty()
                    && screen.inside(teamMenuX + 122, teamMenuY + 38, 48, 15, mouseX, mouseY)) {
                ComputerNetworking.acceptTeamInvite(invites.get(0).id());
            } else if (team == null && !invites.isEmpty()
                    && screen.inside(teamMenuX + 174, teamMenuY + 38, 48, 15, mouseX, mouseY)) {
                ComputerNetworking.declineTeamInvite(invites.get(0).id());
            } else if (team != null && team.owner()
                    && screen.inside(teamMenuX + 4, teamMenuY + 20, 92, 15, mouseX, mouseY)) {
                teamInviteFocused = true;
            } else if (team != null && team.owner()
                    && screen.inside(teamMenuX + 100, teamMenuY + 20, 48, 15, mouseX, mouseY)) {
                if (!teamInviteUsername.isBlank()) {
                    ComputerNetworking.inviteToTeam(teamInviteUsername);
                    teamInviteUsername = "";
                }
            } else if (team != null && team.owner()
                    && screen.inside(teamMenuX + 152, teamMenuY + 20, 72, 15, mouseX, mouseY)) {
                if (teamDisbandConfirm) {
                    ComputerNetworking.disbandTeam();
                    teamDisbandConfirm = false;
                } else teamDisbandConfirm = true;
            } else if (team != null && !team.owner()
                    && screen.inside(teamMenuX + 4, teamMenuY + 20, 82, 15, mouseX, mouseY)) {
                ComputerNetworking.leaveTeam();
            }
            return true;
        }
        if (teamMenuOpen) {
            teamMenuOpen = false;
            teamInviteFocused = false;
        }
        if (searchFocused && button == 0) {
            boolean sidebarOpenForMap = sidebarPinned || sidebarClickOpen || sidebarHoverOpen;
            int searchOffset = sidebarOpenForMap ? 133 : 30;
            int searchMapX = contentX + searchOffset;
            int searchMapW = contentW - searchOffset;
            int searchControlWidth = searchControlWidth();
            int searchLeft = searchMapX + searchMapW - searchControlWidth;
            if (screen.inside(searchLeft, contentY + 1, searchControlWidth, CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                if (mouseX >= searchMapX + searchMapW - 14) {
                    searchText = "";
                    searchResultScroll = 0;
                } else searchFocused = true;
                return true;
            }
        }
        int bodyY = contentY;
        int bodyHeight = Math.max(1, contentH);
        boolean pointerOnTab = screen.inside(contentX, bodyY + 1, 20, 19, mouseX, mouseY);
        boolean pointerOnSidebar = screen.inside(contentX, bodyY, 134, bodyHeight, mouseX, mouseY);
        if (pointerOnTab) {
            searchFocused = false;
            controlsOpen = false;
            if (sidebarClickOpen || sidebarPinned) {
                sidebarClickOpen = false;
                sidebarPinned = false;
                sidebarHoverOpen = false;
                sidebarHoverDismissed = true;
            } else {
                sidebarClickOpen = true;
                sidebarHoverDismissed = false;
                sidebarHoverOpen = false;
            }
            return true;
        }
        if (sidebarHoverDismissed && !pointerOnTab) sidebarHoverDismissed = false;
        sidebarHoverOpen = !sidebarHoverDismissed
                && (pointerOnTab || sidebarHoverOpen && pointerOnSidebar);
        boolean sidebarShown = sidebarPinned || sidebarClickOpen || sidebarHoverOpen;
        int sidebarWidth = sidebarShown ? 124 : 0;
        int contentOffset = sidebarWidth > 0 ? sidebarWidth + 9 : 30;
        if (screen.inside(contentX, bodyY, sidebarWidth, bodyHeight, mouseX, mouseY)) {
            if (screen.inside(contentX + 4, bodyY + 18, sidebarWidth - 8, 16, mouseX, mouseY)) {
                searchFocused = false;
                controlsOpen = false;
                sidebarPinned = !sidebarPinned;
                return true;
            }
            int categoryOffset = (int) mouseY - (bodyY + 37);
            int visibleCategories = Math.max(1, (bodyHeight - 52 + 18) / 19);
            int visibleIndex = categoryOffset / 19;
            int categoryIndex = categoryScroll + visibleIndex;
            List<SidebarRow> sidebarRows = visibleSidebarRows();
            if (categoryOffset >= 0 && categoryOffset % 19 < 16 && visibleIndex < visibleCategories
                    && categoryIndex >= 0 && categoryIndex < sidebarRows.size()) {
                SidebarRow sidebarRow = sidebarRows.get(categoryIndex);
                if (sidebarRow.category().isBlank()) {
                    AntOSSettings.setTaskGroupCollapsed(sidebarRow.group(), !groupCollapsed(sidebarRow.group()));
                    viewDirty = true;
                    return true;
                }
                selectedCategory = sidebarRow.category();
                selectedTaskId = "";
                searchFocused = false;
                controlsOpen = false;
                recipeItem = "";
                sidebarClickOpen = false;
                detailScroll = 0;
                detailMaximumScroll = 0;
                mapScrollX = 0; mapScrollY = 0;
                int newContentOffset = sidebarPinned || sidebarHoverOpen ? 133 : 30;
                List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> newCategoryRows = rowsFor(selectedCategory);
                centerMapOn(newCategoryRows, contentX + newContentOffset, bodyY + 18,
                        contentW - newContentOffset, Math.max(1, bodyHeight - 36), "");
            }
            return true;
        }
        if (!selectedTaskId.isBlank()) {
            if (screen.inside(contentX + contentOffset - 2, bodyY, 105, 16, mouseX, mouseY)) {
                selectedTaskId = ""; recipeItem = ""; detailScroll = 0; detailMaximumScroll = 0; archiveButtons.clear();
                return true;
            }
            for (ItemButton itemButton : itemButtons) {
                if (!screen.inside(itemButton.left(), itemButton.top(), itemButton.right() - itemButton.left(),
                        itemButton.bottom() - itemButton.top(), mouseX, mouseY)) continue;
                recipeItem = recipeItem.equals(itemButton.itemId()) ? "" : itemButton.itemId();
                detailScroll = 0;
                return true;
            }
            for (PrerequisiteButton prerequisiteButton : prerequisiteButtons) {
                if (!screen.inside(prerequisiteButton.left(), prerequisiteButton.top(), prerequisiteButton.right() - prerequisiteButton.left(),
                        prerequisiteButton.bottom() - prerequisiteButton.top(), mouseX, mouseY)) continue;
                var required = viewRowsById.get(prerequisiteButton.taskId());
                var current = viewRowsById.get(selectedTaskId);
                if (required == null) return true;
                if (current != null && required.category().equals(current.category())) openSearchResult(required);
                else pendingJumpTaskId = required.id();
                return true;
            }
            for (PoolChoiceButton choiceButton : poolChoiceButtons) {
                if (!screen.inside(choiceButton.left(), choiceButton.top(), choiceButton.right() - choiceButton.left(),
                        choiceButton.bottom() - choiceButton.top(), mouseX, mouseY)) continue;
                poolSelections.computeIfAbsent(choiceButton.taskId(), ignored -> new HashMap<>())
                        .put(choiceButton.rewardIndex(), choiceButton.path());
                return true;
            }
            for (ClaimButton claimButton : claimButtons) {
                if (!screen.inside(claimButton.left(), claimButton.top(), claimButton.right() - claimButton.left(),
                        claimButton.bottom() - claimButton.top(), mouseX, mouseY)) continue;
                ResourceLocation taskId = ResourceLocation.tryParse(claimButton.taskId());
                if (taskId != null) {
                    ComputerNetworking.claimTaskReward(taskId, poolSelections.getOrDefault(claimButton.taskId(), Map.of()));
                    ComputerNetworking.requestAntazonWallet();
                }
                return true;
            }
            for (ArchiveButton archiveButton : archiveButtons) {
                if (!screen.inside(archiveButton.left(), archiveButton.top(), archiveButton.right() - archiveButton.left(),
                        archiveButton.bottom() - archiveButton.top(), mouseX, mouseY)) continue;
                boolean unlocked = ComputerGuideData.entriesFor(screen.workspaceDiskIds(),
                        com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get()).stream()
                        .anyMatch(entry -> entry.id().equals(archiveButton.entryId()));
                if (unlocked) {
                    screen.session.archive.state.entryId = archiveButton.entryId(); screen.session.archive.state.detailScroll = 0;
                    ComputerNetworking.recordArchiveViewed(archiveButton.entryId());
                    screen.open("ARCHIVE");
                }
                return true;
            }
        } else {
            int mapX = contentX + contentOffset;
            int mapY = bodyY + 18;
            int mapW = contentW - contentOffset;
            int mapH = Math.max(1, bodyHeight - 36);
            MapControls controls = mapControls(mapX, bodyY, mapW, bodyHeight);
            int controlY = controls.y();
            int buttonWidth = controls.buttonWidth();
            int searchControlWidth = searchControlWidth();
            int searchLeft = mapX + mapW - searchControlWidth;
            if (screen.inside(searchLeft, bodyY + 1, searchControlWidth, CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                if (searchFocused && mouseX >= mapX + mapW - 14) {
                    searchText = "";
                    searchResultScroll = 0;
                } else {
                    searchFocused = true;
                    controlsOpen = false;
                    teamMenuOpen = false;
                }
                return true;
            }
            if (searchFocused && !searchText.isBlank()) {
                List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> results = searchResults();
                int panelX = mapX + 2;
                int panelY = mapY + 2;
                int panelW = Math.min(mapW - 4, 192);
                int visibleResults = Math.min(5, Math.max(0, results.size() - searchResultScroll));
                int panelH = Math.max(25, 21 + visibleResults * 18);
                if (screen.inside(panelX, panelY, panelW, panelH, mouseX, mouseY)) {
                    int resultIndex = mouseY < panelY + 17 ? -1 : ((int) mouseY - (panelY + 17)) / 18 + searchResultScroll;
                    if (resultIndex >= searchResultScroll && resultIndex < results.size()
                            && resultIndex < searchResultScroll + visibleResults) openSearchResult(results.get(resultIndex));
                    return true;
                }
            }
            if (screen.inside(controls.helpX(), controlY, buttonWidth, CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                controlsOpen = !controlsOpen;
                searchFocused = false;
                return true;
            }
            if (controlsOpen && screen.inside(controlsPanelX(mapX, mapW), bodyY + 22,
                    controlsPanelWidth(mapW), 92, mouseX, mouseY)) return true;
            if (screen.inside(controls.zoomOutX(), controlY, buttonWidth, CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                mapZoom = Math.max(0.55F, mapZoom - 0.1F);
                centerMapOn(categoryRowsForSelected(), mapX, mapY, mapW, mapH, "");
                return true;
            }
            if (screen.inside(controls.zoomInX(), controlY, buttonWidth, CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                mapZoom = Math.min(1.6F, mapZoom + 0.1F);
                centerMapOn(categoryRowsForSelected(), mapX, mapY, mapW, mapH, "");
                return true;
            }
            if (!controls.compact() && screen.inside(controls.zoomLabelX(), controlY, controls.zoomInX() - controls.zoomLabelX(), CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                mapZoom = defaultMapZoom();
                centerMapOn(categoryRowsForSelected(), mapX, mapY, mapW, mapH, "");
                return true;
            }
            if (screen.inside(controls.centerX(), controlY, controls.centerWidth(), CHROME_BUTTON_HEIGHT, mouseX, mouseY)) {
                centerMapOn(categoryRowsForSelected(), mapX, mapY, mapW, mapH, "");
                return true;
            }
            if (controlsOpen) controlsOpen = false;
            if (searchFocused) searchFocused = false;
            int[] clip = mapClip(contentX, bodyY, contentW, bodyHeight, sidebarWidth);
            if (screen.inside(clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1], mouseX, mouseY)) {
                if (insideChrome(mouseX, mouseY)) return true;
                List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows = rowsFor(selectedCategory);
                List<Node> nodes = nodes(categoryRows, mapX, mapY, mapScrollX, mapScrollY, mapZoom);
                for (int index = nodes.size() - 1; index >= 0; index--) {
                    Node node = nodes.get(index);
                    if (node.contains(mouseX, mouseY)) {
                        if (node.task().isLink()) {
                            openLink(node.task());
                            return true;
                        }
                        selectedTaskId = node.task().id(); recipeItem = ""; sidebarClickOpen = false;
                        detailScroll = 0; detailMaximumScroll = 0; return true;
                    }
                }
                if (button == 0) {
                    int[] limits = mapScrollLimits(categoryRows, mapX, mapY, mapW, mapH);
                    panningWindow = window;
                    panMoved = false;
                    panStartX = mouseX;
                    panStartY = mouseY;
                    panStartScrollX = mapScrollX;
                    panStartScrollY = mapScrollY;
                    panMinimumX = limits[0];
                    panLimitX = limits[1];
                    panMinimumY = limits[2];
                    panLimitY = limits[3];
                }
            }
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        float scale = screen.uiScale();
        double localX = (mouseX - screen.width / 2.0F) / scale, localY = (mouseY - screen.height / 2.0F) / scale;
        int windowX = -WIDTH / 2 + screen.windowLocalX(window), windowY = -HEIGHT / 2 + screen.windowLocalY(window);
        int sidebarX = windowX + 8, sidebarY = windowY + 28, contentHeight = screen.windowHeight(window) - 34;
        int contentWidth = screen.windowWidth(window) - 16;
        int menuY = sidebarY + contentHeight - 17 - teamMenuHeight();
        if (teamMenuOpen && screen.inside(sidebarX + contentWidth - 232, menuY, 228, teamMenuHeight(), localX, localY)) return true;
        int bodyY = sidebarY;
        int bodyHeight = Math.max(1, contentHeight);
        boolean sidebarShown = sidebarPinned || sidebarClickOpen || sidebarHoverOpen;
        int sidebarWidth = sidebarShown ? 124 : 0;
        int contentOffset = sidebarWidth > 0 ? sidebarWidth + 9 : 30;
        if (sidebarShown && screen.inside(sidebarX, bodyY + 37, sidebarWidth, Math.max(1, bodyHeight - 37), localX, localY)) {
            int visible = Math.max(1, (bodyHeight - 52 + 18) / 19);
            categoryScroll = Math.max(0, Math.min(Math.max(0, visibleSidebarRows().size() - visible),
                    categoryScroll - (int) Math.signum(scrollY)));
        } else if (!selectedTaskId.isBlank()
                && screen.inside(sidebarX + contentOffset, bodyY + 19, Math.max(1, contentWidth - contentOffset), Math.max(1, bodyHeight - 36), localX, localY)) {
            detailScroll = Math.max(0, Math.min(detailMaximumScroll,
                    detailScroll - (int) Math.signum(scrollY) * 22));
        } else if (selectedTaskId.isBlank() && insideMapClip(sidebarX, bodyY, contentWidth, bodyHeight, sidebarWidth, localX, localY)) {
            int mapX = sidebarX + contentOffset, mapY = bodyY + 18;
            int mapW = contentWidth - contentOffset, mapH = Math.max(1, bodyHeight - 36);
            if (controlsOpen && screen.inside(controlsPanelX(mapX, mapW), bodyY + 22,
                    controlsPanelWidth(mapW), 92, localX, localY)) return true;
            if (searchFocused && !searchText.isBlank()
                    && screen.inside(mapX + 2, mapY + 2, Math.min(mapW - 4, 192), Math.max(25, 21 + Math.min(5,
                    searchResults().size()) * 18), localX, localY)) {
                int count = searchResults().size();
                searchResultScroll = Math.max(0, Math.min(Math.max(0, count - 5),
                        searchResultScroll - (int) Math.signum(scrollY)));
                return true;
            }
            List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows = rowsFor(selectedCategory);
            int[] limits = mapScrollLimits(categoryRows, mapX, mapY, mapW, mapH);
            lastMapMotion = System.currentTimeMillis();
            if (Screen.hasControlDown()) {
                mapZoom = Math.max(0.55F, Math.min(1.6F, mapZoom + (scrollY > 0 ? 0.1F : -0.1F)));
                centerMapOn(categoryRows, mapX, mapY, mapW, mapH, "");
            } else if (Screen.hasShiftDown()) mapScrollX = Math.max(limits[0], Math.min(limits[1], mapScrollX - (int) Math.signum(scrollY) * 36));
            else mapScrollY = Math.max(limits[2], Math.min(limits[3], mapScrollY - (int) Math.signum(scrollY) * 36));
        }
        return true;
    }

    private boolean insideMapClip(int x, int bodyY, int w, int bodyHeight, int sidebarWidth, double pointerX, double pointerY) {
        int[] clip = mapClip(x, bodyY, w, bodyHeight, sidebarWidth);
        return screen.inside(clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1], pointerX, pointerY);
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (searchFocused && !teamInviteFocused
                && codePoint >= 32 && searchText.length() < 64) {
            searchText += codePoint;
            searchResultScroll = 0;
            return true;
        }
        if (teamInviteFocused) {
            if ((Character.isLetterOrDigit(codePoint) || codePoint == '_') && teamInviteUsername.length() < 16) teamInviteUsername += codePoint;
            return true;
        }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (!pendingJumpTaskId.isBlank()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) confirmJump();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) pendingJumpTaskId = "";
            return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE;
        }
        if (!teamInviteFocused) {
            if (searchFocused) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                    searchResultScroll = 0;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> results = searchResults();
                    if (!results.isEmpty()) openSearchResult(results.get(Math.min(searchResultScroll, results.size() - 1)));
                    return true;
                }
            } else if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_F) {
                searchFocused = true;
                controlsOpen = false;
                teamMenuOpen = false;
                selectedTaskId = "";
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                mapZoom = Math.max(0.55F, mapZoom - 0.1F);
                centerCurrentMap();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                mapZoom = Math.min(1.6F, mapZoom + 0.1F);
                centerCurrentMap();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_0) {
                mapZoom = defaultMapZoom();
                centerCurrentMap();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                centerCurrentMap();
                return true;
            }
        }
        if (teamInviteFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!teamInviteUsername.isEmpty()) teamInviteUsername = teamInviteUsername.substring(0, teamInviteUsername.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!teamInviteUsername.isBlank()) {
                    ComputerNetworking.inviteToTeam(teamInviteUsername);
                    teamInviteUsername = "";
                }
                teamInviteFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { teamInviteFocused = false; return true; }
        }
        return false;
    }
}
