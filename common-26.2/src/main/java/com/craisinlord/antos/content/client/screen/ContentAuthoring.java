package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AuthoringClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.craisinlord.antos.content.editor.AuthoringTemplates;
import com.craisinlord.antos.content.editor.AuthoringValidation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class ContentAuthoring extends ComputerApp {
    final AppKit kit = new AppKit(this);
    private final AuthoringForm form = new AuthoringForm(this);
    private final LocalDraftRecovery recovery;
    private final String appIdentity;
    private final Map<String, Draft> drafts = new HashMap<>();
    private final Map<AuthoringKind, JsonArray> catalogs = new HashMap<>();
    private final Map<AuthoringKind, Integer> offsets = new HashMap<>();
    private final Map<AuthoringKind, Integer> totals = new HashMap<>();
    private final Map<AuthoringKind, List<JsonObject>> filteredCatalogs = new HashMap<>();
    private final Map<AuthoringKind, Map<String, Integer>> categoryCounts = new HashMap<>();
    private final Map<String, JsonObject> pendingPositions = new HashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private AuthoringKind kind = AuthoringKind.TASK;
    private String app = "";
    private String tab = "TASK";
    private String notice = "";
    private long noticeUntil;
    private Draft draft;
    private EditBox idField;
    private EditBox search;
    private String query = "";
    private String cachedQuery;
    private AuthoringKind cachedFilterKind;
    private String category = "";
    private int listScroll;
    private int left;
    private int top;
    private int width;
    private int height;
    private int mapX;
    private int mapY;
    private int mapWidth;
    private int mapHeight;
    private int detailX;
    private float zoom = .75F;
    private int panX;
    private int panY;
    private String dragging = "";
    private double dragX;
    private double dragY;
    private boolean moved;
    private boolean panning;
    private boolean linking;
    private String linkMode = "requires";
    private String linkSource = "";
    private boolean hidePrompt;
    private boolean removePrompt;
    private boolean refreshPrompt;
    private int titleX;
    private int titleY;
    private int titleWidth;
    private String titleApp = "";
    private boolean preview;
    private boolean loading;
    private boolean checkpointNeeded;
    private long changedAt;
    private long savedAtReloadRevision = -1;
    private Draft cachedArchivePreviewDraft;
    private long cachedArchivePreviewRevision = -1;
    private int cachedArchivePreviewWidth = -1;
    private ArchivePreview cachedArchivePreview;

    private record Node(String id, JsonObject body, int x, int y, int size,
                        com.craisinlord.antos.content.guide.TaskAppearance appearance, String title) { }
    private record ArchivePreviewLine(FormattedCharSequence text, int color, int advance) { }
    private record ArchivePreview(List<FormattedCharSequence> title, List<FormattedCharSequence> subtitle,
                                  List<ArchivePreviewLine> content) { }
    private static final class Draft {
        String id;
        final AuthoringKind kind;
        JsonObject body;
        String hash;
        String source;
        boolean authored;
        boolean dirty;
        long revision;
        final java.util.Set<String> invalid = new java.util.HashSet<>();
        long snapshotAt;
        final ArrayDeque<JsonObject> undo = new ArrayDeque<>();
        final ArrayDeque<JsonObject> redo = new ArrayDeque<>();
        Draft(AuthoringKind kind, String id, JsonObject body, String hash, String source, boolean authored) {
            this.kind = kind; this.id = id; this.body = body; this.hash = hash; this.source = source; this.authored = authored;
        }
    }

    ContentAuthoring(String appIdentity) { this.appIdentity = appIdentity; recovery = new LocalDraftRecovery(this, kit, "content-authoring-" + appIdentity); }

    boolean handles(ComputerScreen.Window window) {
        return window != null && window.type.equals(appIdentity) && AuthoringClientState.editing()
                && (window.type.equals("TASKS") || window.type.equals("ANTMAIL") || window.type.equals("ANTAZON") || window.type.equals("ARCHIVE")
                || window.type.equals("DISKS") || window.type.equals("TERMINAL"));
    }

    int renderTitle(ThemedGraphics graphics, ComputerScreen.Window window, int controlsLeft, int y) {
        if (!window.type.equals(appIdentity) || !AuthoringClientState.available()
                || !(window.type.equals("TASKS") || window.type.equals("ANTMAIL") || window.type.equals("ANTAZON") || window.type.equals("ARCHIVE")
                || window.type.equals("DISKS") || window.type.equals("TERMINAL"))) return controlsLeft;
        titleWidth = hidePrompt ? 82 : AuthoringClientState.editing() ? 55 : 37;
        titleX = controlsLeft - titleWidth - 3;
        titleY = y + 3;
        titleApp = window.type;
        graphics.fill(titleX, titleY, titleX + titleWidth, titleY + 14, Ui.PANEL);
        screen.box(graphics, titleX, titleY, titleX + titleWidth, titleY + 14, AuthoringClientState.editing() ? Ui.ACCENT : Ui.LINE);
        graphics.drawCenteredString(font, Component.literal(hidePrompt ? "HIDE CONTROLS?" : AuthoringClientState.editing() ? "EDITING ON" : "EDIT"),
                titleX + titleWidth / 2, titleY + 3, AuthoringClientState.editing() ? Ui.FOCUS : Ui.TEXT_MUTED);
        return titleX - 3;
    }

    boolean titleClick(ComputerScreen.Window window, double x, double y, int button) {
        if (!AuthoringClientState.available() || !window.type.equals(titleApp)
                || !ComputerScreen.inside(titleX, titleY, titleWidth, 14, x, y)) return false;
        if (AuthoringClientState.saving()) { notice("Wait for the current save before leaving editing mode"); return true; }
        if (button == 1) { hidePrompt = !hidePrompt; return true; }
        if (button != 0) return true;
        if (hidePrompt) {
            checkpoint();
            hidePrompt = false;
            if (!AuthoringClientState.setEnabled(false)) notice("Could not save antos.json");
            return true;
        }
        String operation = AuthoringClientState.editing() ? "leave" : "enter";
        if (operation.equals("leave")) checkpoint();
        AuthoringClientState.request(operation, new JsonObject(), response -> {
            if (success(response) && AuthoringClientState.editing()) { app = ""; switchApp(window.type); }
        });
        return true;
    }

    boolean render(ThemedGraphics graphics, ComputerScreen.Window window, int x, int y, int w, int h) {
        if (!handles(window)) return false;
        switchApp(window.type);
        if (loading && !AuthoringClientState.busy()) loading = false;
        left = x; top = y; width = w; height = h;
        kit.begin();
        if (recovery.active()) { recovery.render(graphics, x, y, w, h, this::restore); return true; }
        int buttonWidth = Math.max(30, Math.min(49, (w - 20) / 7));
        kit.button(graphics, x, y, buttonWidth, 17, "+ NEW", false, !loading, this::create);
        kit.button(graphics, x + buttonWidth + 3, y, buttonWidth, 17, "SAVE", draft != null && draft.dirty, draft != null && !loading, this::save);
        kit.button(graphics, x + (buttonWidth + 3) * 2, y, buttonWidth, 17, "UNDO", false, draft != null && !draft.undo.isEmpty(), this::undo);
        kit.button(graphics, x + (buttonWidth + 3) * 3, y, buttonWidth, 17, refreshPrompt ? "CONFIRM" : "REFRESH", refreshPrompt, !loading, this::refreshCurrent);
        kit.button(graphics, x + (buttonWidth + 3) * 4, y, buttonWidth, 17, "RELOAD", false, !loading, this::reload);
        if (kind == AuthoringKind.TASK) kit.button(graphics, x + (buttonWidth + 3) * 5, y, buttonWidth + 10, 17, linking ? "LINK " + linkLabel() : "CONNECT", linking, true, "Click to connect; right-click to choose ALL, ANY or VISUAL", () -> {
            if (kit.clickButton() == 1) linkMode = linkMode.equals("requires") ? "requires_any" : linkMode.equals("requires_any") ? "connects" : "requires";
            linking = kit.clickButton() == 1 || !linking;
            linkSource = draft == null ? "" : draft.id;
            notice(linkMode.equals("connects") ? "Select source, then visual connection" : "Select task, then its prerequisite // " + linkLabel());
        });
        int side = Math.max(79, Math.min(112, w / 4));
        int bodyY = y + 23;
        int bodyHeight = Math.max(20, h - 43);
        graphics.fill(x + side, bodyY, x + side + 1, bodyY + bodyHeight, Ui.LINE);
        renderCatalog(graphics, x, bodyY, side - 5, bodyHeight);
        mapX = x + side + 7;
        mapY = bodyY;
        mapWidth = w - side - 7;
        mapHeight = bodyHeight;
        detailX = mapX;
        if (kind == AuthoringKind.TASK) {
            boolean split = draft != null && w >= 390;
            if (split) {
                int inspector = Math.max(152, Math.min(205, mapWidth / 2));
                mapWidth -= inspector + 7;
                detailX = mapX + mapWidth + 7;
            }
            if (draft == null || split) renderMap(graphics);
            if (draft != null) renderDraft(graphics, detailX, bodyY, x + w - detailX, bodyHeight);
        } else if (draft != null) renderDraft(graphics, mapX, bodyY, mapWidth, bodyHeight);
        else label(graphics, "Select content or create a new " + kind.title().toLowerCase(java.util.Locale.ROOT), mapX + 3, bodyY + 12, Ui.TEXT_MUTED, mapWidth - 6);
        String message = !AuthoringClientState.error().isBlank() ? AuthoringClientState.error()
                : System.currentTimeMillis() < noticeUntil ? notice : draft != null && draft.dirty ? "UNSAVED CHANGES // DRAFT RETAINED WHILE BROWSING"
                : savedAtReloadRevision >= 0 && AuthoringClientState.reloadRevision() <= savedAtReloadRevision ? "SAVED // /reload TO APPLY" : "JSON AUTHORING // SAVE, THEN /reload TO APPLY";
        label(graphics, message, x, y + h - 12, !AuthoringClientState.error().isBlank() ? Ui.ERROR : Ui.TEXT_MUTED, w);
        if (screen.hovered(x, y + h - 12, w, 12)) kit.tooltip(message);
        form.renderPicker(graphics, x + Math.max(0, (w - 260) / 2), y + 4, Math.min(w, 260), Math.max(80, h - 9));
        kit.drawTooltip(graphics, x, y, x + w, y + h);
        return true;
    }

    private void switchApp(String value) {
        if (app.equals(value)) return;
        app = value;
        if (!kind.app().equals(value)) kind = java.util.Arrays.stream(AuthoringKind.values()).filter(entry -> entry.app().equals(value)).findFirst().orElse(AuthoringKind.TASK);
        draft = null;
        query = "";
        category = "";
        search = null;
        listScroll = 0;
        tab = value.equals("TASKS") ? "TASK" : "MESSAGE";
        refresh(kind);
    }

    private void renderCatalog(ThemedGraphics graphics, int x, int y, int w, int h) {
        kit.button(graphics, x, y, w, 17, kind.title().toUpperCase(java.util.Locale.ROOT) + " >", false, !loading, () -> {
            List<AuthoringKind> kinds = java.util.Arrays.stream(AuthoringKind.values()).filter(value -> value.app().equals(app)).toList();
            int direction = kit.clickButton() == 1 ? -1 : 1;
            kind = kinds.get(Math.floorMod(kinds.indexOf(kind) + direction, kinds.size()));
            draft = null;
            listScroll = 0;
            refresh(kind);
        });
        if (search == null) {
            search = new EditBox(font, x + 3, y + 24, Math.max(20, w - 6), 14, Component.literal("Search content"));
            search.setBordered(false);
            search.setTextColor(Ui.TEXT);
            search.setMaxLength(128);
            search.setHint(Component.literal("SEARCH"));
            search.setValue(query);
            search.setResponder(value -> { query = value; listScroll = 0; cachedQuery = null; });
        }
        search.setX(x + 3); search.setY(y + 24); search.setWidth(Math.max(20, w - 6));
        kit.outline(graphics, x, y + 20, w, 19, search.isFocused() ? Ui.FOCUS : Ui.LINE);
        search.extractRenderState(graphics.source(), screen.session.mouseX, screen.session.mouseY, 0);
        JsonArray entries = catalogs.getOrDefault(kind, new JsonArray());
        List<JsonObject> filtered = filteredCatalogs.get(kind);
        if (filtered == null || cachedFilterKind != kind || !query.equals(cachedQuery)) {
            filtered = new ArrayList<>();
            String normalizedQuery = query.toLowerCase(java.util.Locale.ROOT);
            for (JsonElement entry : entries) {
                JsonObject item = entry.getAsJsonObject();
                if ((item.get("id").getAsString() + " " + text(item, "category", "") + " " + text(item, "title", "") + " " + text(item, "name", "") + " " + text(item, "label", "") + " " + text(item, "subject", ""))
                        .toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)) filtered.add(item);
            }
            filtered = List.copyOf(filtered);
            filteredCatalogs.put(kind, filtered);
            if (kind == AuthoringKind.TASK) {
                Map<String, Integer> counts = new HashMap<>();
                counts.put("", filtered.size());
                for (JsonObject item : filtered) counts.merge(text(item, "category", "antos:general"), 1, Integer::sum);
                categoryCounts.put(kind, Map.copyOf(counts));
            }
            cachedFilterKind = kind;
            cachedQuery = query;
        }
        int count = Math.max(1, (h - 68) / 28);
        if (kind == AuthoringKind.TASK) {
            java.util.LinkedHashSet<String> categories = new java.util.LinkedHashSet<>();
            categories.add("");
            for (JsonObject entry : filtered) categories.add(text(entry, "category", "antos:general"));
            List<String> values = new ArrayList<>(categories);
            listScroll = Math.max(0, Math.min(Math.max(0, values.size() - count), listScroll));
            for (int index = listScroll; index < Math.min(values.size(), listScroll + count); index++) {
                String value = values.get(index);
                int row = y + 44 + (index - listScroll) * 28;
                if (category.equals(value)) graphics.fill(x, row, x + w, row + 26, Ui.SELECTED_FILL);
                label(graphics, value.isEmpty() ? "ALL CATEGORIES" : value.substring(value.indexOf(':') + 1).replace('_', ' ').toUpperCase(java.util.Locale.ROOT), x + 3, row + 4, Ui.ACCENT, w - 6);
                int tasks = categoryCounts.getOrDefault(kind, Map.of()).getOrDefault(value, 0);
                label(graphics, tasks + " TASKS", x + 3, row + 16, Ui.TEXT_MUTED, w - 6);
                kit.hit(x, row, w, 26, () -> { category = value; draft = null; panX = 0; panY = 0; });
            }
            renderPages(graphics, x, y + h - 19, w);
            return;
        }
        listScroll = Math.max(0, Math.min(Math.max(0, filtered.size() - count), listScroll));
        for (int index = listScroll; index < Math.min(filtered.size(), listScroll + count); index++) {
            JsonObject entry = filtered.get(index);
            String id = entry.get("id").getAsString();
            int row = y + 44 + (index - listScroll) * 28;
            if (draft != null && draft.id.equals(id)) graphics.fill(x, row, x + w, row + 26, Ui.SELECTED_FILL);
            String title = entry.has("title") ? translated(entry.get("title").getAsString())
                    : entry.has("name") ? translated(entry.get("name").getAsString())
                    : entry.has("label") ? translated(entry.get("label").getAsString())
                    : text(entry, "subject", id.substring(id.indexOf(':') + 1));
            label(graphics, title, x + 3, row + 3, Ui.TEXT_STRONG, w - 6);
            label(graphics, id, x + 3, row + 15, Ui.TEXT_MUTED, w - 6);
            kit.hit(x, row, w, 26, () -> open(id));
        }
        renderPages(graphics, x, y + h - 19, w);
    }

    private void renderPages(ThemedGraphics graphics, int x, int y, int w) {
        int offset = offsets.getOrDefault(kind, 0);
        int total = totals.getOrDefault(kind, 0);
        if (total <= 128) return;
        kit.button(graphics, x, y, 18, 16, "<", false, offset > 0 && !loading, () -> { offsets.put(kind, Math.max(0, offset - 128)); refresh(kind); });
        label(graphics, (offset / 128 + 1) + "/" + ((total + 127) / 128), x + 24, y + 4, Ui.TEXT_MUTED, Math.max(1, w - 48));
        kit.button(graphics, x + w - 18, y, 18, 16, ">", false, offset + 128 < total && !loading, () -> { offsets.put(kind, offset + 128); refresh(kind); });
    }

    private void renderMap(ThemedGraphics graphics) {
        nodes.clear();
        graphics.flush();
        screen.enableComputerScissor(graphics, mapX, mapY, mapX + mapWidth, mapY + mapHeight);
        try {
            int originX = mapX + 25 + panX;
            int originY = mapY + 42 + panY;
            label(graphics, linking ? linkMode.toUpperCase(java.util.Locale.ROOT) : "TASK MAP", mapX + 2, mapY + 2, Ui.ACCENT, mapWidth - 4);
            List<JsonObject> entries = new ArrayList<>();
            for (JsonElement entry : catalogs.getOrDefault(kind, new JsonArray())) entries.add(entry.getAsJsonObject());
            if (draft != null && entries.stream().noneMatch(entry -> text(entry, "id", "").equals(draft.id))) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", draft.id);
                entries.add(entry);
            }
            int index = 0;
            for (JsonElement value : entries) {
                JsonObject entry = value.getAsJsonObject();
                String id = entry.get("id").getAsString();
                JsonObject body = draft != null && draft.id.equals(id) ? draft.body : entry;
                if (!category.isEmpty() && !category.equals(text(body, "category", "antos:general"))) continue;
                if (!(id + " " + text(body, "title", "")).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) continue;
                JsonObject position = body.has("position") && body.get("position").isJsonObject() ? body.getAsJsonObject("position") : null;
                int column = position == null ? index % 4 : integer(position, "x", index % 4);
                int row = position == null ? index / 4 : integer(position, "y", index / 4);
                var appearance = com.craisinlord.antos.content.guide.TaskAppearance.parse(body);
                int size = Math.max(12, Math.round(32 * zoom * appearance.size()));
                int nx = originX + Math.round(column * 56 * zoom);
                int ny = originY + Math.round(row * 56 * zoom);
                nodes.add(new Node(id, body, nx, ny, size, appearance, translated(text(body, "title", id))));
                index++;
            }
            Map<String, Node> byId = new HashMap<>();
            for (Node node : nodes) byId.put(node.id(), node);
            for (Node node : nodes) for (String field : List.of("requires", "requires_any", "connects")) {
                if (!node.body().has(field) || !node.body().get(field).isJsonArray()) continue;
                for (JsonElement reference : node.body().getAsJsonArray(field)) {
                    Node target = byId.get(reference.getAsString());
                    if (target == null) continue;
                    int centerX = node.x() + node.size() / 2;
                    int centerY = node.y() + node.size() / 2;
                    int targetX = target.x() + target.size() / 2;
                    int targetY = target.y() + target.size() / 2;
                    var appearance = node.appearance();
                    boolean vertical = appearance.lineAxis() == com.craisinlord.antos.content.guide.TaskAppearance.LineAxis.VERTICAL
                            || appearance.lineAxis() == com.craisinlord.antos.content.guide.TaskAppearance.LineAxis.AUTO && Math.abs(targetY - centerY) > Math.abs(targetX - centerX);
                    if (vertical) {
                        int bend = Math.round(centerY + (targetY - centerY) * appearance.lineBend());
                        graphics.fill(centerX, Math.min(centerY, bend), centerX + 1, Math.max(centerY, bend) + 1, Ui.LINE);
                        graphics.fill(Math.min(centerX, targetX), bend, Math.max(centerX, targetX) + 1, bend + 1, Ui.LINE);
                        graphics.fill(targetX, Math.min(bend, targetY), targetX + 1, Math.max(bend, targetY) + 1, Ui.LINE);
                    } else {
                        int bend = Math.round(centerX + (targetX - centerX) * appearance.lineBend());
                        graphics.fill(Math.min(centerX, bend), centerY, Math.max(centerX, bend) + 1, centerY + 1, Ui.LINE);
                        graphics.fill(bend, Math.min(centerY, targetY), bend + 1, Math.max(centerY, targetY) + 1, Ui.LINE);
                        graphics.fill(Math.min(bend, targetX), targetY, Math.max(bend, targetX) + 1, targetY + 1, Ui.LINE);
                    }
                }
            }
            for (Node node : nodes) {
                if (node.x() + node.size() < mapX || node.x() > mapX + mapWidth || node.y() + node.size() < mapY || node.y() > mapY + mapHeight) continue;
                var appearance = node.appearance();
                int border = draft != null && draft.id.equals(node.id()) ? Ui.FOCUS : Ui.ACCENT;
                if (appearance.shape() == com.craisinlord.antos.content.guide.TaskAppearance.Shape.SQUARE) {
                    graphics.fill(node.x(), node.y(), node.x() + node.size(), node.y() + node.size(), Ui.PANEL);
                    kit.outline(graphics, node.x(), node.y(), node.size(), node.size(), border);
                } else com.craisinlord.antos.content.client.renderer.TaskShapeRenderer.draw(graphics, appearance.shape(), node.x(), node.y(), node.size(), Ui.PANEL, border, Ui.FOCUS, false);
                String icon = text(node.body(), "icon", "minecraft:paper");
                boolean previousTint = screen.archiveGreenTint;
                screen.archiveGreenTint = !node.body().has("green_tint") || node.body().get("green_tint").getAsBoolean();
                try {
                    float iconScale = node.size() * 26 / 32.0F * (float) appearance.shape().iconFit() * appearance.iconScale() / 26;
                    screen.renderArchiveAsset(graphics, icon, text(node.body(), "icon_entity", ""), "", "", node.x() + node.size() / 2,
                            node.y() + node.size() / 2, 26, 0, .9F, !node.body().has("render_mob_from_spawn_egg") || node.body().get("render_mob_from_spawn_egg").getAsBoolean(), iconScale);
                } finally { screen.archiveGreenTint = previousTint; }
                String title = node.title();
                if (screen.hovered(node.x(), node.y(), node.size(), node.size())) kit.tooltip(title + "\n" + node.id());
                label(graphics, title, node.x() - 6, node.y() + node.size() + 3, Ui.TEXT, Math.max(28, Math.round(52 * zoom)));
            }
        } finally { graphics.flush(); graphics.disableScissor(); }
    }

    private void renderDraft(ThemedGraphics graphics, int x, int y, int w, int h) {
        if (w < 60) return;
        graphics.fill(x - 3, y, x - 2, y + h, Ui.LINE);
        kit.button(graphics, x + w - 48, y, 22, 15, "DUP", false, true, "Duplicate as new content", this::duplicate);
        kit.button(graphics, x + w - 23, y, 22, 15, "X", false, true, "Return to map or list; draft is retained", () -> { draft = null; removePrompt = false; });
        if (idField == null || !idField.getValue().equals(draft.id) && !idField.isFocused()) {
            idField = new EditBox(font, x + 3, y + 5, w - 58, 14, Component.literal("Content ID"));
            idField.setBordered(false);
            idField.setTextColor(Ui.TEXT);
            idField.setMaxLength(240);
            idField.setValue(draft.id);
            idField.setEditable(draft.hash.isEmpty());
            idField.setResponder(value -> { if (draft != null && draft.hash.isEmpty()) { draft.id = value; draft.dirty = true; } });
        }
        idField.setX(x + 3); idField.setY(y + 5); idField.setWidth(Math.max(20, w - 58));
        idField.setEditable(draft.hash.isEmpty() && !loading);
        graphics.fill(x, y, x + w - 54, y + 19, Ui.BACKGROUND);
        kit.outline(graphics, x, y, w - 54, 19, idField.isFocused() ? Ui.FOCUS : Ui.LINE);
        idField.extractRenderState(graphics.source(), screen.session.mouseX, screen.session.mouseY, 0);
        List<String> tabs = kind == AuthoringKind.TASK ? List.of("TASK", "GOALS", "REWARDS", "LOOK", "LINKS", "ALL")
                : kind == AuthoringKind.MESSAGE ? List.of("MESSAGE", "SENDER", "SCHEDULE", "DELIVERY", "ALL")
                : kind == AuthoringKind.PRODUCT ? List.of("LISTING", "PRICE", "CONTENTS", "UNLOCKS", "ALL")
                : kind == AuthoringKind.SELL_RULE ? List.of("RULE", "UNLOCKS", "ALL")
                : kind == AuthoringKind.DEAL_CAMPAIGN ? List.of("CAMPAIGN", "CANDIDATES", "ALL")
                : kind == AuthoringKind.ARCHIVE_ENTRY ? List.of("ENTRY", "ARTICLE", "ART", "LINKS", "ALL")
                : kind == AuthoringKind.DISK ? List.of("DISK", "CONTENTS", "FILES", "PROGRAMS", "UNLOCKS", "ALL")
                : kind == AuthoringKind.DISK_CATEGORY ? List.of("CATEGORY", "ALL")
                : kind == AuthoringKind.PROGRAM ? List.of("PROGRAM", "QUESTIONS", "REWARDS", "ALL")
                : kind == AuthoringKind.TERMINAL_COMMAND ? List.of("COMMAND", "OUTPUT", "REQUIREMENTS", "EFFECTS", "ALL") : List.of("ALL");
        if (!tabs.contains(tab)) tab = tabs.getFirst();
        int tabWidth = Math.max(24, (w - (tabs.size() - 1) * 2) / tabs.size());
        for (int index = 0; index < tabs.size(); index++) {
            String choice = tabs.get(index);
            kit.button(graphics, x + index * (tabWidth + 2), y + 23, tabWidth, 15, choice, tab.equals(choice), true, () -> { tab = choice; preview = false; });
        }
        int bottom = y + h - 23;
        int bottomSpace = kind == AuthoringKind.MESSAGE || kind == AuthoringKind.ARCHIVE_ENTRY || draft.authored ? 23 : 0;
        if (preview && kind == AuthoringKind.MESSAGE) renderMailPreview(graphics, x, y + 43, w, h - 43 - bottomSpace);
        else if (preview && kind == AuthoringKind.ARCHIVE_ENTRY) renderArchivePreview(graphics, x, y + 43, w, h - 43 - bottomSpace);
        else {
            form.document(kind, draft.body, fields());
            form.render(graphics, x, y + 43, w, Math.max(16, h - 43 - bottomSpace));
        }
        if (kind == AuthoringKind.MESSAGE || kind == AuthoringKind.ARCHIVE_ENTRY) kit.button(graphics, x, bottom, Math.min(58, w / 2 - 2), 17, preview ? "EDIT" : "PREVIEW", preview, true, () -> preview = !preview);
        if (draft.authored) kit.button(graphics, x + w - 84, bottom, 84, 17, removePrompt ? "CONFIRM REMOVE" : "REMOVE OVERRIDE", removePrompt, !loading, () -> {
            if (!removePrompt) { removePrompt = true; notice("Removing the override reveals any inherited definition after /reload"); }
            else remove();
        });
    }

    private Set<String> fields() {
        return switch (tab) {
            case "TASK" -> Set.of("title", "description", "category", "availability", "program", "sort_order", "content");
            case "GOALS" -> Set.of("objectives");
            case "REWARDS" -> Set.of("rewards");
            case "LOOK" -> Set.of("icon", "icon_entity", "shape", "size", "icon_scale", "line_axis", "line_bend", "green_tint", "render_mob_from_spawn_egg");
            case "LINKS" -> kind == AuthoringKind.ARCHIVE_ENTRY
                    ? Set.of("structure", "structure_tag", "dimension", "search_radius", "locator", "lookup", "lookup_recipes")
                    : Set.of("position", "requires", "requires_any", "connects", "archive_entries", "hide_until_dependencies_complete", "invisible_until_completed", "type", "link_category", "link_group");
            case "MESSAGE" -> Set.of("subject", "body");
            case "SENDER" -> Set.of("sender", "sender_display", "sender_avatar_item", "sender_avatar_entity", "style", "notification");
            case "SCHEDULE" -> Set.of("trigger", "once", "conditions", "deliver_window", "delete_after_read", "blockable");
            case "DELIVERY" -> Set.of("delivery", "antcoins", "wallpaper");
            case "LISTING" -> Set.of("name", "description", "category", "tags", "quantity", "enabled", "delivery");
            case "PRICE" -> Set.of("payments", "availability");
            case "CONTENTS" -> kind == AuthoringKind.PRODUCT
                    ? Set.of("thumbnail", "gallery", "rewards", "item_pool", "variants", "delivery_location")
                    : Set.of("entries", "tasks", "wallpaper");
            case "UNLOCKS" -> kind == AuthoringKind.PRODUCT
                    ? Set.of("unlock_mode", "unlock_tasks", "unlock_disks", "unlock_commands", "hidden_until_unlocked", "notify_on_unlock")
                    : kind == AuthoringKind.DISK ? Set.of("rewards", "consumable", "encryption")
                    : Set.of("unlock_tasks", "unlock_mode", "unlock_disks", "unlock_commands", "hidden_until_unlocked", "notify_on_unlock");
            case "RULE" -> Set.of("items", "tags", "value", "limit", "limit_reset");
            case "CAMPAIGN" -> Set.of("label", "enabled", "picks", "every_minecraft_days", "every_real_hours", "order", "discount_percent", "max_per_category", "deal_limit", "priority");
            case "CANDIDATES" -> Set.of("candidates");
            case "ENTRY" -> Set.of("type", "category", "title", "subtitle", "folder", "sort_order", "unlocked_by_default");
            case "ARTICLE" -> Set.of("description", "content");
            case "ART" -> Set.of("item", "entity", "enchantment", "recipe", "cover_item", "cover_entity", "cover_potion", "gallery", "gallery_title", "rotation", "render_scale", "green_tint");
            case "DISK" -> Set.of("title", "category", "description", "hidden");
            case "FILES" -> Set.of("files");
            case "PROGRAMS" -> Set.of("programs", "subscribe");
            case "CATEGORY" -> Set.of("title", "texture", "color");
            case "PROGRAM" -> Set.of("type", "title", "questions_per_run", "reward_on", "reward_limit", "green_tint", "start", "nodes");
            case "QUESTIONS" -> Set.of("questions");
            case "COMMAND" -> Set.of("command", "aliases", "arguments", "usage", "description", "hint", "case_sensitive", "hidden", "once", "variadic");
            case "OUTPUT" -> Set.of("style", "typing", "typing_speed", "output", "locked_output", "clear");
            case "REQUIREMENTS" -> Set.of("requires");
            case "EFFECTS" -> Set.of("entries", "tasks", "wallpaper", "files", "programs", "subscribe", "rewards", "open", "action");
            default -> Set.of();
        };
    }

    private void renderMailPreview(ThemedGraphics graphics, int x, int y, int w, int h) {
        graphics.flush();
        screen.enableComputerScissor(graphics, x, y, x + w, y + h);
        try {
            label(graphics, text(draft.body, "sender_display", text(draft.body, "sender", "")), x + 3, y + 3, Ui.ACCENT, w - 6);
            label(graphics, text(draft.body, "subject", ""), x + 3, y + 18, Ui.TEXT_STRONG, w - 6);
            String body = com.craisinlord.antos.content.client.AntOSPlayerText.apply(text(draft.body, "body", ""));
            int line = y + 35;
            for (var part : font.split(Component.literal(body), Math.max(1, w - 6))) {
                graphics.drawString(font, part, x + 3, line, Ui.TEXT, false);
                line += 11;
                if (line > y + h) break;
            }
        } finally { graphics.flush(); graphics.disableScissor(); }
    }

    private void renderArchivePreview(ThemedGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, Ui.SURFACE);
        kit.outline(graphics, x, y, x + w, y + h, Ui.LINE);
        screen.enableComputerScissor(graphics, x + 3, y + 3, x + w - 3, y + h - 3);
        try {
            ArchivePreview cached = archivePreview(w);
            int line = y + 8;
            for (var part : cached.title()) {
                graphics.drawString(font, part, x + 8, line, Ui.TEXT_STRONG, false);
                line += 14;
                if (line > y + h) return;
            }
            if (!cached.subtitle().isEmpty()) {
                for (var part : cached.subtitle()) {
                    graphics.drawString(font, part, x + 8, line, Ui.TEXT_MUTED, false);
                    line += 11;
                    if (line > y + h) return;
                }
                line += 5;
            }
            String item = text(draft.body, "cover_item", text(draft.body, "item", ""));
            String entity = text(draft.body, "cover_entity", text(draft.body, "entity", ""));
            String enchantment = text(draft.body, "enchantment", "");
            String potion = text(draft.body, "cover_potion", "");
            if (!item.isBlank() || !entity.isBlank() || !enchantment.isBlank() || !potion.isBlank()) {
                screen.renderArchiveAsset(graphics, item, entity, enchantment, potion, x + 26, line + 23, 42,
                        (float) number(draft.body, "rotation", 0), (float) number(draft.body, "render_scale", 1));
                line += 50;
            }
            for (ArchivePreviewLine part : cached.content()) {
                if (part.text() != null) graphics.drawString(font, part.text(), x + 8, line, part.color(), false);
                line += part.advance();
                if (line > y + h) return;
            }
        } finally { graphics.flush(); graphics.disableScissor(); }
    }

    private ArchivePreview archivePreview(int width) {
        if (cachedArchivePreviewDraft != draft || cachedArchivePreviewRevision != draft.revision || cachedArchivePreviewWidth != width) {
            int textWidth = Math.max(1, width - 16);
            String title = translated(text(draft.body, "title", "literal:New Archive entry"));
            String subtitle = translated(text(draft.body, "subtitle", ""));
            List<FormattedCharSequence> titleLines = font.split(Component.literal(title), textWidth);
            List<FormattedCharSequence> subtitleLines = subtitle.isBlank() ? List.of() : font.split(Component.literal(subtitle), textWidth);
            List<ArchivePreviewLine> content = new ArrayList<>();
            JsonArray blocks = draft.body.has("content") && draft.body.get("content").isJsonArray()
                    ? draft.body.getAsJsonArray("content") : draft.body.has("description") && draft.body.get("description").isJsonArray()
                    ? draft.body.getAsJsonArray("description") : new JsonArray();
            for (JsonElement block : blocks) {
                String value = block.isJsonPrimitive() ? translated(block.getAsString())
                        : block.isJsonObject() && block.getAsJsonObject().has("text")
                        ? block.getAsJsonObject().get("text").getAsString()
                        : block.isJsonObject() && block.getAsJsonObject().has("display")
                        ? "DISPLAY // " + block.getAsJsonObject().get("display").getAsString()
                        : block.isJsonObject() && block.getAsJsonObject().has("recipe")
                        ? "RECIPE // " + block.getAsJsonObject().get("recipe").getAsString() : "";
                if (value.isBlank()) continue;
                for (FormattedCharSequence part : font.split(Component.literal(com.craisinlord.antos.content.client.AntOSPlayerText.apply(value)), textWidth))
                    content.add(new ArchivePreviewLine(part, Ui.TEXT, 11));
                content.add(new ArchivePreviewLine(null, Ui.TEXT, 3));
            }
            cachedArchivePreview = new ArchivePreview(titleLines, subtitleLines, List.copyOf(content));
            cachedArchivePreviewDraft = draft;
            cachedArchivePreviewRevision = draft.revision;
            cachedArchivePreviewWidth = width;
        }
        return cachedArchivePreview;
    }

    private static double number(JsonObject object, String key, double fallback) {
        try { return object.has(key) ? object.get(key).getAsDouble() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private void refresh(AuthoringKind requested) {
        refresh(requested, false);
    }

    private void refresh(AuthoringKind requested, boolean force) {
        loading = true;
        JsonObject request = new JsonObject();
        request.addProperty("refresh", force);
        request.addProperty("kind", requested.key());
        request.addProperty("offset", offsets.getOrDefault(requested, 0));
        AuthoringClientState.request("list", request, response -> {
            loading = false;
            if (success(response) && response.has("entries")) {
                catalogs.put(requested, response.getAsJsonArray("entries"));
                filteredCatalogs.remove(requested);
                categoryCounts.remove(requested);
                offsets.put(requested, response.get("offset").getAsInt());
                totals.put(requested, response.get("total").getAsInt());
            }
        });
    }

    private void refreshCurrent() {
        if (draft == null || draft.hash.isEmpty()) { refresh(kind, true); return; }
        if (draft.dirty && !refreshPrompt) { refreshPrompt = true; notice("Refresh will discard this draft. Click CONFIRM to load the saved source."); return; }
        String id = draft.id;
        Draft previous = draft;
        drafts.entrySet().removeIf(entry -> entry.getValue() == previous);
        draft = null;
        refreshPrompt = false;
        checkpoint();
        open(id);
    }

    private void open(String id) {
        if (loading) return;
        String key = kind.key() + "/" + id;
        Draft remembered = drafts.get(key);
        if ((remembered == null || !remembered.dirty) && drafts.values().stream().distinct().filter(value -> value.dirty).count() >= 16) { notice("Save a draft before opening another definition"); return; }
        if (remembered != null) { select(remembered); return; }
        if (drafts.size() >= 48) drafts.entrySet().removeIf(entry -> !entry.getValue().dirty);
        loading = true;
        AuthoringKind requested = kind;
        JsonObject request = new JsonObject();
        request.addProperty("kind", requested.key()); request.addProperty("id", id);
        AuthoringClientState.request("get", request, response -> {
            loading = false;
            if (!success(response)) return;
            Draft loaded = new Draft(requested, id, response.getAsJsonObject("body").deepCopy(), response.get("hash").getAsString(), response.get("source").getAsString(), response.get("authored").getAsBoolean());
            drafts.put(key, loaded);
            if (kind == requested) select(loaded);
            JsonObject position = pendingPositions.remove(key);
            if (position != null) {
                loaded.undo.addLast(loaded.body.deepCopy());
                loaded.body.add("position", position);
                loaded.dirty = true;
                checkpointNeeded = true;
                changedAt = System.currentTimeMillis();
                form.invalidate();
            }
        });
    }

    private void select(Draft value) { draft = value; idField = null; removePrompt = false; refreshPrompt = false; preview = false; form.invalidate(); }

    private boolean create() {
        if (drafts.values().stream().distinct().filter(value -> value.dirty).count() >= 16) { notice("Save an existing draft before creating another"); return false; }
        JsonObject body = AuthoringTemplates.create(kind);
        if (kind == AuthoringKind.TASK && !category.isEmpty()) body.addProperty("category", category);
        String namespace = draft != null && draft.id.contains(":") ? draft.id.substring(0, draft.id.indexOf(':')) : "pack";
        String id = namespace + ":" + kind.key() + "_" + Long.toString(System.currentTimeMillis(), 36);
        Draft value = new Draft(kind, id, body, "", "New content", false);
        value.dirty = true;
        drafts.put(kind.key() + "/" + id, value);
        select(value);
        checkpointNeeded = true;
        changedAt = System.currentTimeMillis();
        return true;
    }

    private void duplicate() {
        if (draft == null) return;
        if (drafts.values().stream().distinct().filter(value -> value.dirty).count() >= 16) { notice("Save an existing draft before creating another"); return; }
        Draft copy = new Draft(kind, draft.id + "_copy_" + Long.toString(System.currentTimeMillis(), 36), draft.body.deepCopy(), "", "New copy", false);
        copy.dirty = true;
        drafts.put(kind.key() + "/" + copy.id, copy);
        select(copy);
        checkpointNeeded = true;
        changedAt = System.currentTimeMillis();
    }

    void changed() {
        if (draft == null) return;
        draft.revision++;
        long now = System.currentTimeMillis();
        if (now - draft.snapshotAt >= 500) {
            draft.undo.addLast(draft.body.deepCopy());
            while (draft.undo.size() > 24) draft.undo.removeFirst();
            draft.snapshotAt = now;
        }
        draft.redo.clear();
        draft.dirty = true;
        checkpointNeeded = true;
        changedAt = now;
    }

    private void undo() {
        if (draft == null || draft.undo.isEmpty()) return;
        draft.redo.addLast(draft.body.deepCopy());
        draft.body = draft.undo.removeLast();
        draft.revision++;
        draft.dirty = true;
        draft.invalid.clear();
        checkpointNeeded = true;
        form.invalidate();
    }

    private void redo() {
        if (draft == null || draft.redo.isEmpty()) return;
        draft.undo.addLast(draft.body.deepCopy());
        draft.body = draft.redo.removeLast();
        draft.revision++;
        draft.dirty = true;
        draft.invalid.clear();
        checkpointNeeded = true;
        form.invalidate();
    }

    private void save() {
        if (draft == null || loading) return;
        if (!draft.invalid.isEmpty()) { notice("Fix the invalid number in " + draft.invalid.iterator().next() + " before saving"); return; }
        try { AuthoringValidation.validate(kind, draft.body); }
        catch (RuntimeException exception) { notice(exception.getMessage()); return; }
        Draft saving = draft;
        JsonObject sent = saving.body.deepCopy();
        JsonObject request = identity(saving);
        request.add("body", sent);
        loading = true;
        if (idField != null) idField.setEditable(false);
        AuthoringClientState.request("save", request, response -> {
            loading = false;
            if (!success(response)) return;
            saving.hash = response.get("hash").getAsString();
            saving.source = response.get("source").getAsString();
            saving.authored = true;
            savedAtReloadRevision = AuthoringClientState.reloadRevision();
            saving.dirty = !saving.body.equals(sent);
            drafts.entrySet().removeIf(entry -> entry.getValue() == saving);
            drafts.put(saving.kind.key() + "/" + saving.id, saving);
            checkpoint();
            notice("SAVED // /reload TO APPLY");
            refresh(saving.kind);
        });
    }

    private JsonObject identity(Draft value) {
        JsonObject request = new JsonObject();
        request.addProperty("kind", value.kind.key()); request.addProperty("id", value.id); request.addProperty("hash", value.hash);
        return request;
    }

    private void remove() {
        if (draft == null || loading) return;
        loading = true;
        Draft removing = draft;
        AuthoringClientState.request("remove", identity(removing), response -> {
            loading = false;
            if (!success(response)) return;
            drafts.entrySet().removeIf(entry -> entry.getValue() == removing);
            if (draft == removing) draft = null;
            savedAtReloadRevision = AuthoringClientState.reloadRevision();
            checkpoint();
            notice("OVERRIDE REMOVED // /reload TO APPLY");
            refresh(removing.kind);
        });
    }

    private void reload() {
        if (AuthoringClientState.saving()) { notice("Wait for the current save before reloading"); return; }
        if (drafts.values().stream().anyMatch(value -> value.dirty)) { notice("Save your drafts before reloading"); return; }
        var player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand("reload");
        catalogs.clear();
        filteredCatalogs.clear();
        categoryCounts.clear();
        drafts.clear();
        draft = null;
        app = "";
        notice("Reload requested");
    }

    boolean click(ComputerScreen.Window window, net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        if (!handles(window)) return false;
        if (recovery.active()) { kit.click(x, y, button); return true; }
        if (form.pickerClick(event)) return true;
        if (search != null && search.isMouseOver(x, y)) { search.setFocused(true); if (idField != null) idField.setFocused(false); search.mouseClicked(event, false); return true; }
        if (search != null) search.setFocused(false);
        if (draft != null && idField != null && idField.isMouseOver(x, y)) { idField.setFocused(true); idField.mouseClicked(event, false); return true; }
        if (idField != null) idField.setFocused(false);
        if (kit.click(x, y, button)) return true;
        if (kind == AuthoringKind.TASK && ComputerScreen.inside(mapX, mapY, mapWidth, mapHeight, x, y)
                && (draft == null || width >= 390)) {
            for (Node node : nodes) if (ComputerScreen.inside(node.x(), node.y(), node.size(), node.size(), x, y)) {
                if (linking) { connect(node.id()); return true; }
                open(node.id());
                dragging = node.id(); dragX = x; dragY = y; moved = false;
                return true;
            }
            if (button == 1) {
                if (!create()) return true;
                JsonObject position = new JsonObject();
                position.addProperty("x", coordinate((float) (x - mapX - 25 - panX) / (56 * zoom)));
                position.addProperty("y", coordinate((float) (y - mapY - 42 - panY) / (56 * zoom)));
                draft.body.add("position", position);
            } else { panning = true; dragX = x; dragY = y; }
            return true;
        }
        form.click(event);
        return true;
    }

    private void connect(String id) {
        if (linkSource.isEmpty()) { linkSource = id; notice("Now select the prerequisite or connected task"); return; }
        if (linkSource.equals(id)) { notice("A task cannot connect to itself"); return; }
        if (draft == null || !draft.id.equals(linkSource)) { open(linkSource); notice("Source selected; click the target again"); return; }
        structuralChange();
        JsonArray references = draft.body.has(linkMode) ? draft.body.getAsJsonArray(linkMode) : new JsonArray();
        boolean present = false;
        for (int index = references.size() - 1; index >= 0; index--) if (references.get(index).getAsString().equals(id)) { references.remove(index); present = true; }
        if (!present) references.add(id);
        draft.body.add(linkMode, references);
        linking = false;
        form.invalidate();
        notice(present ? "Connection removed" : "Connection added // save to keep it");
    }

    private String linkLabel() { return linkMode.equals("connects") ? "VISUAL" : linkMode.equals("requires_any") ? "ANY" : "ALL"; }

    boolean drag(ComputerScreen.Window window, net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x();
        double y = event.y();
        if (!handles(window)) return false;
        if (form.pickerOpen()) return true;
        if (kit.draggingScrollbar()) { kit.drag(y); return true; }
        if (dragging.isEmpty() && !panning) { form.drag(event, x - dragX, y - dragY); dragX = x; dragY = y; return true; }
        if (panning) { panX += (int) (x - dragX); panY += (int) (y - dragY); dragX = x; dragY = y; return true; }
        if (!dragging.isEmpty() && Math.hypot(x - dragX, y - dragY) > 3) {
            JsonObject position = new JsonObject();
            position.addProperty("x", coordinate((float) (x - mapX - 25 - panX - 12) / (56 * zoom)));
            position.addProperty("y", coordinate((float) (y - mapY - 42 - panY - 12) / (56 * zoom)));
            if (draft != null && draft.id.equals(dragging)) {
                if (!moved) { structuralChange(); moved = true; }
                draft.body.add("position", position);
                form.invalidate();
            } else pendingPositions.put(kind.key() + "/" + dragging, position);
        }
        return true;
    }

    boolean release(ComputerScreen.Window window) {
        if (!handles(window)) return false;
        kit.release(); dragging = ""; panning = false; return true;
    }

    boolean scroll(ComputerScreen.Window window, double x, double y, double amount) {
        if (!handles(window)) return false;
        if (recovery.active() || form.pickerOpen()) return true;
        if (x < mapX) listScroll = Math.max(0, listScroll - (int) amount);
        else if (kind == AuthoringKind.TASK && (draft == null || width >= 390) && x < mapX + mapWidth)
            zoom = Math.max(.35F, Math.min(1.5F, zoom + (float) amount * .05F));
        else form.scroll(x, y, amount);
        return true;
    }

    boolean type(ComputerScreen.Window window, net.minecraft.client.input.CharacterEvent event) {
        if (!handles(window)) return false;
        if (recovery.active()) return true;
        if (search != null && search.isFocused()) search.charTyped(event);
        else if (idField != null && idField.isFocused()) idField.charTyped(event);
        else form.type(event);
        return true;
    }

    boolean key(ComputerScreen.Window window, net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int modifiers = event.modifiers();
        if (!handles(window)) return false;
        if (recovery.active()) {
            if (key == GLFW.GLFW_KEY_TAB) kit.focusNext((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) kit.activateFocused();
            else recovery.key(key);
            return true;
        }
        if (form.pickerOpen()) { form.key(event); return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_S) { save(); return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (key == GLFW.GLFW_KEY_Y || key == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0)) { redo(); return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (draft != null) draft = null; else if (!AuthoringClientState.saving()) { checkpoint(); AuthoringClientState.request("leave", new JsonObject(), response -> { }); } return true; }
        if (search != null && search.isFocused()) search.keyPressed(event);
        else if (idField != null && idField.isFocused()) idField.keyPressed(event);
        else form.key(event);
        return true;
    }

    void notice(String message) { notice = message == null ? "Editor request failed" : message; noticeUntil = System.currentTimeMillis() + 8000; }
    void structuralChange() { if (draft != null) draft.snapshotAt = 0; changed(); if (draft != null) draft.snapshotAt = 0; }
    void fieldInvalid(String path) { if (draft != null) draft.invalid.add(path); notice("Enter a valid number in " + path); }
    boolean invalidNumber(String path) { return draft != null && draft.invalid.contains(path); }
    void fieldValid(String path) { if (draft != null) draft.invalid.remove(path); }
    void tick() { if (checkpointNeeded && System.currentTimeMillis() - changedAt > 5000) checkpoint(); }

    void checkpoint() {
        JsonArray entries = new JsonArray();
        for (Draft value : drafts.values().stream().distinct().toList()) if (value.dirty) {
            JsonObject entry = identity(value);
            entry.add("body", value.body.deepCopy());
            entry.addProperty("source", value.source);
            entry.addProperty("authored", value.authored);
            entries.add(entry);
        }
        JsonObject snapshot = new JsonObject();
        snapshot.add("drafts", entries);
        recovery.checkpoint(entries.isEmpty() ? null : snapshot);
        checkpointNeeded = false;
    }

    private void restore(JsonObject snapshot) {
        JsonArray entries = AuthoringValidation.array(snapshot, "drafts", 16);
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            AuthoringKind restoredKind = AuthoringKind.parse(entry.get("kind").getAsString());
            JsonObject body = entry.getAsJsonObject("body").deepCopy();
            AuthoringValidation.parse(body.toString());
            Draft value = new Draft(restoredKind, entry.get("id").getAsString(), body, entry.get("hash").getAsString(), entry.get("source").getAsString(), entry.get("authored").getAsBoolean());
            value.dirty = true;
            drafts.put(restoredKind.key() + "/" + value.id, value);
            if (restoredKind == kind) select(value);
        }
        notice("Drafts restored. Refresh source content if a saved revision has changed.");
    }
    private static boolean success(JsonObject response) { return response.has("ok") && response.get("ok").getAsBoolean(); }
    private void label(ThemedGraphics graphics, String label, int x, int y, int color, int width) { graphics.drawString(font, screen.trimToWidth(label, Math.max(1, width)), x, y, color, false); }
    private static String text(JsonObject object, String key, String fallback) { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback; }
    private static int coordinate(float value) { return Math.max(-128, Math.min(128, Math.round(value))); }
    private static int integer(JsonObject object, String key, int fallback) { try { return object.has(key) ? object.get(key).getAsInt() : fallback; } catch (RuntimeException exception) { return fallback; } }
    private static String translated(String text) { return text.startsWith("literal:") ? text.substring(8) : Component.translatable(text).getString(); }
}
