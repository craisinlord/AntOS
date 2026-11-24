package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

final class StudioTree {
    private record Row(String id, int depth, String label, StudioIcon icon, StudioKey key, boolean group, int count, boolean open, Runnable action) { }

    private final AntStudioScreen screen;
    private final StudioStore store;
    private final StudioKit kit;
    private final Map<StudioCategory, String> queries = new HashMap<>();
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> collapsed = new HashSet<>();
    private List<Row> rows = List.of();
    private String built = "";
    private boolean loose;
    private String revealing = "";

    StudioTree(AntStudioScreen screen) {
        this.screen = screen;
        store = screen.store;
        kit = screen.kit;
    }

    private String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    void expand(String id) {
        expanded.add(id);
        collapsed.remove(id);
    }

    void reveal(StudioKey key, StudioKey disk) {
        if (screen.category() == StudioCategory.FLOPPIES) {
            StudioKey parent = disk;
            if (parent == null && key.kind() != AuthoringKind.DISK) parent = store.disksOf(key).stream().findFirst().orElse(null);
            if (parent != null && key.kind() != AuthoringKind.DISK) {
                expand("disk:" + parent.id());
                expand("disk:" + parent.id() + ":" + key.kind());
            } else if (parent == null && store.onDefault(key)) {
                expand("default");
                expand("default:" + key.kind());
            }
        }
        revealing = key.toString();
    }

    String query() { return queries.getOrDefault(screen.category(), ""); }
    String field() { return "tree:filter:" + screen.category(); }

    void render(int px, int py, int pw, int ph) {
        kit.fill(px, py, pw, ph, StudioTheme.window);
        StudioCategory category = screen.category();
        boolean looseOffered = Set.of(StudioCategory.TASKS, StudioCategory.ARCHIVE, StudioCategory.PROGRAMS).contains(category);
        int fieldWidth = pw - 8 - (looseOffered ? 18 : 0);
        kit.field(field(), px + 4, py + 4, fieldWidth, query(), new StudioKit.Field().hint(t("filter", category.title().toLowerCase(Locale.ROOT))).max(128), value -> queries.put(category, value));
        if (looseOffered) kit.button(px + pw - 20, py + 4, 16, 16, "!", loose ? StudioKit.ON : StudioKit.NORMAL, true, t("loose.tip"), () -> loose = !loose);
        kit.fill(px, py + 23, pw, 1, StudioTheme.line);
        String state = category + "|" + store.revision() + "|" + query() + "|" + expanded.hashCode() + "|" + collapsed.hashCode() + "|" + loose + "|" + screen.prefs.groupDisks;
        if (!state.equals(built)) {
            rows = build(category);
            built = state;
        }
        int top = py + 25;
        int height = ph - 25;
        String scroll = "tree:" + category;
        if (!revealing.isEmpty()) {
            for (int index = 0; index < rows.size(); index++) if (rows.get(index).key() != null && rows.get(index).key().toString().equals(revealing)) {
                int at = index * 14;
                int offset = kit.scrollOf(scroll);
                if (at < offset || at + 14 > offset + height) kit.scrollTo(scroll, at - height / 2);
                break;
            }
            revealing = "";
        }
        int offset = kit.scroll(scroll, px, top, pw, height, rows.size() * 14 + 6);
        kit.clip(px, top, pw, height);
        int y = top - offset;
        for (Row row : rows) {
            if (y + 14 > top && y < top + height) renderRow(row, px, y, pw);
            y += 14;
        }
        if (rows.isEmpty()) kit.wrap(t(store.loading() ? "loading" : "nothing_here"), px + 8, top + 8, pw - 16, StudioTheme.soft);
        if (y < top + height) kit.hit(px, Math.max(top, y), pw, top + height - Math.max(top, y), click -> {
            if (click.button() == 1) screen.newMenu(click.x(), click.y());
        });
        kit.unclip();
    }

    private void renderRow(Row row, int px, int y, int pw) {
        boolean selected = row.key() != null && row.key().equals(screen.current()) && !row.id().startsWith("more");
        boolean hover = kit.hover(px, y, pw - 4, 14);
        if (selected) {
            kit.fill(px, y, pw, 14, StudioTheme.selected);
            kit.fill(px, y, 2, 14, StudioTheme.accent);
        } else if (hover) kit.fill(px, y, pw, 14, StudioTheme.hover);
        int left = px + 5 + row.depth() * 9;
        if (row.group()) kit.arrow(left, y + 6, row.open(), StudioTheme.soft);
        if (row.group() || row.depth() == 0) left += 8;
        if (row.icon() != null) {
            row.icon().draw(kit, left, y + 2, 10);
            left += 13;
        }
        int right = px + pw - 6;
        boolean problem = false;
        boolean dirty = false;
        if (row.key() != null) {
            problem = store.broken(row.key());
            dirty = store.dirty(row.key());
            StudioStore.Row source = store.row(row.key());
            if (source != null && !source.error.isEmpty()) problem = true;
        }
        if (problem || dirty) {
            kit.dot(right - 5, y + 5, problem, false);
            right -= 8;
        }
        if (row.count() >= 0) right -= kit.right(Integer.toString(row.count()), right, y + 3, StudioTheme.soft) + 4;
        int color = row.action() != null ? hover ? StudioTheme.accent : StudioTheme.muted : selected ? StudioTheme.strong : row.key() == null ? StudioTheme.muted : hover ? StudioTheme.strong : StudioTheme.text;
        StudioStore.Doc doc = row.key() == null ? null : store.loaded(row.key());
        if (doc != null && doc.removed) color = StudioTheme.strongLine;
        kit.text(row.label(), left, y + 3, right - left - 2, color);
        if (row.group() && row.key() != null) kit.hit(px, y, left - px, 14, click -> toggle(row));
        int hitX = row.group() && row.key() != null ? left : px;
        if (row.key() != null) {
            kit.grab(hitX, y, px + pw - 5 - hitX, 14, new StudioKit.Payload(row.key(), row.label()), click -> {
                if (click.button() == 1) { screen.contextMenu(row.key(), diskOf(row), click.x(), click.y()); return; }
                if (row.group() && row.key().equals(screen.current())) toggle(row);
                else if (row.group()) {
                    expand(row.id());
                }
                screen.open(row.key(), diskOf(row));
            });
            if (row.key().kind() == AuthoringKind.DISK) kit.dropZone(px, y, pw - 4, 14, value -> value instanceof StudioKey dropped && !StudioStore.diskField(dropped.kind()).isEmpty()
                    && !store.direct(dropped, row.key()), value -> store.putOnDisk((StudioKey) value, row.key()));
            if (row.key().kind() == AuthoringKind.ARCHIVE_FOLDER) kit.dropZone(px, y, pw - 4, 14, value -> value instanceof StudioKey dropped && dropped.kind() == AuthoringKind.ARCHIVE_ENTRY,
                    value -> store.with(List.of((StudioKey) value), () -> store.edit((StudioKey) value, "", body -> body.addProperty("folder", row.key().id()))));
            if (row.key().kind() == AuthoringKind.CATEGORY) kit.dropZone(px, y, pw - 4, 14, value -> value instanceof StudioKey dropped && dropped.kind() == AuthoringKind.TASK,
                    value -> store.with(List.of((StudioKey) value), () -> store.edit((StudioKey) value, "", body -> body.addProperty("category", row.key().id()))));
        } else if (row.action() != null) kit.press(px, y, pw - 4, 14, row.action());
        else kit.press(px, y, pw - 4, 14, () -> toggle(row));
    }

    private void toggle(Row row) {
        if (row.open()) {
            expanded.remove(row.id());
            collapsed.add(row.id());
        } else expand(row.id());
    }

    private StudioKey diskOf(Row row) {
        String id = row.id();
        if (!id.startsWith("disk:")) return null;
        String rest = id.substring(5);
        int marker = rest.indexOf('|');
        return marker < 0 ? null : new StudioKey(AuthoringKind.DISK, rest.substring(0, marker));
    }

    private boolean open(String id, boolean fallback) { return !query().isBlank() || expanded.contains(id) || fallback && !collapsed.contains(id); }

    private boolean matches(StudioKey key) {
        String query = query().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return true;
        String text = (store.label(key) + " " + key.id()).toLowerCase(Locale.ROOT);
        for (String word : query.split("\\s+")) if (!text.contains(word)) return false;
        return true;
    }

    private boolean shown(StudioKey key) {
        if (!matches(key)) return false;
        if (!loose || screen.category() == StudioCategory.FLOPPIES) return true;
        return Set.of(AuthoringKind.TASK, AuthoringKind.ARCHIVE_ENTRY, AuthoringKind.PROGRAM).contains(key.kind()) && store.disksOf(key).isEmpty() && !store.onDefault(key);
    }

    private Row leaf(String prefix, int depth, StudioKey key) {
        return new Row(prefix + "|" + key, depth, store.exists(key) ? store.label(key) : key.id(), store.exists(key) ? StudioIcons.of(store, key) : null, key, false, -1, false, null);
    }

    private List<Row> build(StudioCategory category) {
        List<Row> result = new ArrayList<>();
        switch (category) {
            case FLOPPIES -> floppies(result);
            case TASKS -> {
                Map<String, List<StudioKey>> byCategory = new LinkedHashMap<>();
                for (StudioKey group : store.keys(AuthoringKind.CATEGORY)) byCategory.put(group.id(), new ArrayList<>());
                for (StudioKey task : store.keys(AuthoringKind.TASK)) if (shown(task)) byCategory.computeIfAbsent(StudioJson.string(store.view(task), "category", "antos:general"), ignored -> new ArrayList<>()).add(task);
                byCategory.forEach((id, tasks) -> {
                    StudioKey group = new StudioKey(AuthoringKind.CATEGORY, id);
                    boolean known = store.exists(group);
                    if (tasks.isEmpty() && (!known || !matches(group) || loose)) return;
                    String rowId = "tasks:" + id;
                    boolean open = open(rowId, byCategory.size() <= 3);
                    result.add(new Row(rowId, 0, known ? store.label(group) : StudioText.prettyId(id), known ? StudioIcons.of(store, group) : StudioIcon.pixel("files/folder"), known ? group : null, true, tasks.size(), open, null));
                    if (open) tasks.forEach(task -> result.add(leaf(rowId, 1, task)));
                });
                section(result, "groups", AuthoringKind.GROUP);
                section(result, "pools", AuthoringKind.REWARD_POOL);
            }
            case ARCHIVE -> archive(result);
            case MAIL -> {
                Map<String, List<StudioKey>> senders = new LinkedHashMap<>();
                List<StudioKey> messages = store.keys(AuthoringKind.MESSAGE).stream().filter(this::shown).toList();
                List<StudioKey> nobody = messages.stream().filter(key -> StudioJson.string(store.view(key), "sender").isBlank()).toList();
                for (StudioKey key : messages) if (!nobody.contains(key)) senders.computeIfAbsent(StudioJson.string(store.view(key), "sender"), ignored -> new ArrayList<>()).add(key);
                if (!nobody.isEmpty()) group(result, "mail:", t("no_sender"), null, nobody, true);
                senders.entrySet().stream().sorted(Comparator.comparing(entry -> StudioProblems.senderName(store, entry.getKey()).toLowerCase(Locale.ROOT)))
                        .forEach(entry -> group(result, "mail:" + entry.getKey(), StudioProblems.senderName(store, entry.getKey()), null, entry.getValue(), senders.size() <= 4));
                section(result, "mail_pools", AuthoringKind.MAIL_POOL);
                section(result, "takeovers", AuthoringKind.TAKEOVER);
            }
            case ANTAZON -> {
                Map<String, List<StudioKey>> categories = new java.util.TreeMap<>();
                for (StudioKey key : store.keys(AuthoringKind.PRODUCT)) if (shown(key)) categories.computeIfAbsent(StudioJson.string(store.view(key), "category", "general"), ignored -> new ArrayList<>()).add(key);
                categories.forEach((name, keys) -> group(result, "shop:" + name, StudioText.pretty(name), StudioIcon.pixel("files/folder"), keys, categories.size() <= 3));
                section(result, "sell_rules", AuthoringKind.SELL_RULE);
                section(result, "deals", AuthoringKind.DEAL_CAMPAIGN);
            }
            case PROGRAMS -> {
                List<StudioKey> quizzes = store.keys(AuthoringKind.PROGRAM).stream().filter(this::shown).filter(key -> !StudioJson.string(store.view(key), "type").equals("adventure")).toList();
                List<StudioKey> adventures = store.keys(AuthoringKind.PROGRAM).stream().filter(this::shown).filter(key -> StudioJson.string(store.view(key), "type").equals("adventure")).toList();
                group(result, "quizzes", t("quizzes"), null, quizzes, true);
                group(result, "adventures", t("adventures"), null, adventures, true);
            }
            case TERMINAL -> store.keys(AuthoringKind.TERMINAL_COMMAND).stream().filter(this::shown).forEach(key -> result.add(leaf("terminal", 0, key)));
        }
        if (query().isBlank()) for (AuthoringKind kind : category.kinds) {
            if (category == StudioCategory.FLOPPIES && kind == AuthoringKind.DISK_CATEGORY) continue;
            result.add(new Row("new:" + kind, 0, "+ " + t(kind == AuthoringKind.DISK ? "add_disk" : "new_kind", StudioText.kind(kind)), null, null, false, -1, false, () -> {
                if (kind == AuthoringKind.TASK || kind == AuthoringKind.ARCHIVE_ENTRY || kind == AuthoringKind.MESSAGE || kind == AuthoringKind.PRODUCT) screen.newDialog(kind);
                else screen.make(kind, t("untitled", StudioText.kind(kind)), false);
            }));
            if (kind == category.kinds.get(0) && category.kinds.size() > 2) break;
        }
        return List.copyOf(result);
    }

    private void group(List<Row> result, String id, String label, StudioIcon icon, List<StudioKey> keys, boolean fallback) {
        if (keys.isEmpty() && (!query().isBlank() || loose)) return;
        boolean open = open(id, fallback);
        result.add(new Row(id, 0, label, icon, null, true, keys.size(), open, null));
        if (open) keys.forEach(key -> result.add(leaf(id, 1, key)));
    }

    private void section(List<Row> result, String id, AuthoringKind kind) {
        if (loose) return;
        List<StudioKey> keys = store.keys(kind).stream().filter(this::shown).toList();
        if (keys.isEmpty() && !query().isBlank()) return;
        boolean open = open(id, false);
        result.add(new Row(id, 0, t("list." + id), null, null, true, keys.size(), open, null));
        if (!open) return;
        keys.forEach(key -> result.add(leaf(id, 1, key)));
        if (query().isBlank()) result.add(new Row("new:" + kind + ":in", 1, "+ " + t("new_kind", StudioText.kind(kind)), null, null, false, -1, false, () -> screen.make(kind, t("untitled", StudioText.kind(kind)), false)));
    }

    private void floppies(List<Row> result) {
        List<StudioKey> defaults = new ArrayList<>();
        for (AuthoringKind kind : List.of(AuthoringKind.TASK, AuthoringKind.ARCHIVE_ENTRY)) for (StudioKey key : store.keys(kind)) if (store.onDefault(key) && matches(key)) defaults.add(key);
        boolean openDefault = open("default", false);
        if (query().isBlank() || !defaults.isEmpty()) {
            result.add(new Row("default", 0, t("default"), null, null, true, defaults.size(), openDefault, null));
            if (openDefault) for (AuthoringKind kind : List.of(AuthoringKind.TASK, AuthoringKind.ARCHIVE_ENTRY)) {
                List<StudioKey> keys = defaults.stream().filter(key -> key.kind() == kind).toList();
                if (keys.isEmpty()) continue;
                String id = "default:" + kind;
                boolean open = open(id, defaults.size() <= 24);
                result.add(new Row(id, 1, t("list." + kind.key()), null, null, true, keys.size(), open, null));
                if (open) keys.forEach(key -> result.add(leaf(id, 2, key)));
            }
        }
        List<StudioKey> disks = store.keys(AuthoringKind.DISK);
        if (!screen.prefs.groupDisks) {
            disks.forEach(disk -> disk(result, disk, 0));
            return;
        }
        Map<String, List<StudioKey>> groups = new LinkedHashMap<>();
        for (StudioKey disk : disks) {
            String category = StudioJson.string(store.view(disk), "category", "general");
            groups.computeIfAbsent(category.contains(":") ? category : disk.namespace() + ":" + category, ignored -> new ArrayList<>()).add(disk);
        }
        groups.forEach((id, members) -> {
            StudioKey category = new StudioKey(AuthoringKind.DISK_CATEGORY, id);
            boolean open = open("diskgroup:" + id, true);
            result.add(new Row("diskgroup:" + id, 0, store.exists(category) ? store.label(category) : StudioText.prettyId(id), StudioIcon.pixel("files/folder"), store.exists(category) ? category : null, true, members.size(), open, null));
            if (open) members.forEach(disk -> disk(result, disk, 1));
        });
    }

    private void disk(List<Row> result, StudioKey disk, int depth) {
        List<AuthoringKind> kinds = List.of(AuthoringKind.TASK, AuthoringKind.ARCHIVE_ENTRY, AuthoringKind.MESSAGE, AuthoringKind.MAIL_POOL, AuthoringKind.PROGRAM);
        Map<AuthoringKind, List<StudioKey>> contents = new LinkedHashMap<>();
        boolean self = matches(disk);
        int total = 0;
        for (AuthoringKind kind : kinds) {
            List<StudioKey> keys = store.onDisk(disk, kind);
            total += keys.size();
            List<StudioKey> visible = self ? keys : keys.stream().filter(this::matches).toList();
            if (!visible.isEmpty()) contents.put(kind, visible);
        }
        if (!self && contents.isEmpty()) return;
        String id = "disk:" + disk.id();
        boolean open = expanded.contains(id) || !query().isBlank() && !self;
        result.add(new Row(id, depth, store.label(disk), StudioIcons.of(store, disk), disk, true, total, open, null));
        if (!open) return;
        contents.forEach((kind, keys) -> {
            String section = id + ":" + kind;
            boolean shown = expanded.contains(section) || !query().isBlank() || !collapsed.contains(section) && keys.size() <= 12;
            result.add(new Row(section, depth + 1, t("list." + kind.key()), null, null, true, keys.size(), shown, null));
            if (shown) keys.forEach(key -> result.add(leaf("disk:" + disk.id(), depth + 2, key)));
        });
        result.add(new Row("more:" + disk.id(), depth + 1, "+ " + t("add_to_floppy"), null, null, false, -1, false, () -> screen.newDialogFor(disk)));
    }

    private void archive(List<Row> result) {
        List<StudioKey> entries = store.keys(AuthoringKind.ARCHIVE_ENTRY).stream().filter(this::shown).toList();
        List<StudioKey> folders = store.keys(AuthoringKind.ARCHIVE_FOLDER);
        Map<String, List<StudioKey>> inside = new HashMap<>();
        for (StudioKey entry : entries) {
            String folder = StudioJson.string(store.view(entry), "folder");
            if (!folder.isBlank() && !store.known(AuthoringKind.ARCHIVE_FOLDER, folder)) folder = "";
            inside.computeIfAbsent(folder, ignored -> new ArrayList<>()).add(entry);
        }
        for (StudioKey folder : folders) if (StudioJson.string(store.view(folder), "parent").isBlank()) folder(result, folder, folders, inside, 0);
        List<StudioKey> rootEntries = inside.getOrDefault("", List.of());
        if (folders.isEmpty()) rootEntries.forEach(entry -> result.add(leaf("archive", 0, entry)));
        else if (!rootEntries.isEmpty()) group(result, "archive:root", t("no_folder"), StudioIcon.pixel("files/folder"), rootEntries, true);
    }

    private void folder(List<Row> result, StudioKey folder, List<StudioKey> folders, Map<String, List<StudioKey>> inside, int depth) {
        List<StudioKey> entries = inside.getOrDefault(folder.id(), List.of());
        List<StudioKey> children = folders.stream().filter(child -> StudioJson.string(store.view(child), "parent").equals(folder.id())).toList();
        int count = entries.size() + children.stream().mapToInt(child -> inside.getOrDefault(child.id(), List.of()).size()).sum();
        if (count == 0 && (!query().isBlank() && !matches(folder) || loose)) return;
        String id = "folder:" + folder.id();
        boolean open = open(id, folders.size() <= 3);
        result.add(new Row(id, depth, store.label(folder), StudioIcons.of(store, folder), folder, true, count, open, null));
        if (!open) return;
        if (depth < 4) children.forEach(child -> folder(result, child, folders, inside, depth + 1));
        entries.forEach(entry -> result.add(leaf(id, depth + 1, entry)));
    }

    boolean key(int key) {
        if (key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN && key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT) return false;
        int selected = -1;
        for (int index = 0; index < rows.size(); index++) if (rows.get(index).key() != null && rows.get(index).key().equals(screen.current())) { selected = index; break; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (selected < 0 || !rows.get(selected).group()) return false;
            if (rows.get(selected).open() != (key == GLFW.GLFW_KEY_RIGHT)) toggle(rows.get(selected));
            return true;
        }
        int step = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
        for (int index = selected < 0 ? step > 0 ? 0 : rows.size() - 1 : selected + step; index >= 0 && index < rows.size(); index += step) {
            Row row = rows.get(index);
            if (row.key() == null) continue;
            screen.open(row.key(), diskOf(row));
            return true;
        }
        return true;
    }

    List<String> labels() { return rows.stream().map(Row::label).toList(); }
}
