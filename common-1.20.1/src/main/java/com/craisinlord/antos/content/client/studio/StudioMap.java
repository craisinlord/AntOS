package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.client.renderer.TaskShapeRenderer;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.craisinlord.antos.content.guide.TaskAppearance;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StudioMap {
    private record Node(StudioKey key, int x, int y, int size, int column, int row, TaskAppearance appearance) { }
    private record Link(StudioKey from, StudioKey to, String field, int[] points) { }

    private final AntStudioScreen screen;
    private final StudioStore store;
    private final StudioKit kit;
    final Set<StudioKey> selected = new LinkedHashSet<>();
    String category = "";
    String disk = "";
    private float zoom = 1.0F;
    private double panX = 20;
    private double panY = 20;
    private boolean fitted;
    private List<Node> nodes = List.of();
    private List<Link> links = List.of();
    private int moveX;
    private int moveY;
    private boolean moving;
    private StudioKey linking;
    private int[] box;
    private int x;
    private int y;
    private int w;
    private int h;
    boolean space;

    StudioMap(AntStudioScreen screen) {
        this.screen = screen;
        store = screen.store;
        kit = screen.kit;
    }

    private String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    List<StudioKey> tasks() {
        List<StudioKey> result = new ArrayList<>();
        for (StudioKey task : store.keys(AuthoringKind.TASK)) {
            if (!category.isEmpty() && !StudioJson.string(store.view(task), "category", "antos:general").equals(category)) continue;
            if (!disk.isEmpty() && !store.disksOf(task).contains(new StudioKey(AuthoringKind.DISK, disk))) continue;
            result.add(task);
        }
        return result;
    }

    int filters(int fx, int fy, int available) {
        int start = fx;
        StudioKey chosen = new StudioKey(AuthoringKind.CATEGORY, category);
        String label = category.isEmpty() ? t("map.all_categories") : t("map.category", store.exists(chosen) ? store.label(chosen) : StudioText.prettyId(category));
        int size = Math.min(available / 2 - 4, kit.width(label) + 20);
        kit.drop(fx, fy, size, label, null, click -> {
            StudioMenu menu = new StudioMenu(screen, click.x(), fy, 16);
            menu.check(t("map.all_categories"), category.isEmpty(), () -> setCategory(""));
            Set<String> ids = new LinkedHashSet<>();
            for (StudioKey key : store.keys(AuthoringKind.CATEGORY)) ids.add(key.id());
            for (StudioKey task : store.keys(AuthoringKind.TASK)) ids.add(StudioJson.string(store.view(task), "category", "antos:general"));
            for (String id : ids) menu.check(store.known(AuthoringKind.CATEGORY, id) ? store.label(AuthoringKind.CATEGORY, id) : StudioText.prettyId(id), id.equals(category), () -> setCategory(id));
            menu.separator().item(t("new_kind", StudioText.kind(AuthoringKind.CATEGORY)), () -> {
                StudioKey made = screen.make(AuthoringKind.CATEGORY, t("untitled", StudioText.kind(AuthoringKind.CATEGORY)), false);
                setCategory(made.id());
            });
            screen.popup(menu);
        });
        fx += size + 5;
        StudioKey floppy = new StudioKey(AuthoringKind.DISK, disk);
        String diskLabel = disk.isEmpty() ? t("map.all_floppies") : t("map.floppy", store.label(floppy));
        int diskSize = Math.min(available / 2 - 4, kit.width(diskLabel) + 20);
        kit.drop(fx, fy, diskSize, diskLabel, null, click -> {
            StudioMenu menu = new StudioMenu(screen, click.x(), fy, 16);
            menu.check(t("map.all_floppies"), disk.isEmpty(), () -> setDisk(""));
            for (StudioKey key : store.keys(AuthoringKind.DISK)) menu.item(store.label(key), StudioIcons.of(store, key), "", true, key.id().equals(disk), () -> setDisk(key.id()));
            screen.popup(menu);
        });
        return fx + diskSize - start;
    }

    private void setCategory(String value) {
        category = value;
        fitted = false;
        selected.clear();
    }

    private void setDisk(String value) {
        disk = value;
        fitted = false;
        selected.clear();
    }

    void show(StudioKey task) {
        if (task == null || task.kind() != AuthoringKind.TASK) return;
        String wanted = StudioJson.string(store.view(task), "category", "antos:general");
        if (!category.isEmpty() && !category.equals(wanted)) setCategory(wanted);
        if (!disk.isEmpty() && !store.disksOf(task).contains(new StudioKey(AuthoringKind.DISK, disk))) setDisk("");
        if (!selected.contains(task)) {
            selected.clear();
            selected.add(task);
        }
    }

    private float cell() { return 56 * zoom; }
    private int column(double px) { return (int) Math.round((px - x - panX) / cell()); }
    private int row(double py) { return (int) Math.round((py - y - panY) / cell()); }

    private void build() {
        List<StudioKey> tasks = tasks();
        List<Node> result = new ArrayList<>();
        int lowest = 0;
        List<StudioKey> loose = new ArrayList<>();
        for (StudioKey task : tasks) {
            JsonObject body = store.view(task);
            JsonObject position = StudioJson.object(body, "position");
            if (!position.has("x") || !position.has("y")) {
                loose.add(task);
                continue;
            }
            int column = StudioJson.integer(position, "x", 0);
            int row = StudioJson.integer(position, "y", 0);
            lowest = Math.max(lowest, row + 1);
            result.add(node(task, body, column, row));
        }
        for (int index = 0; index < loose.size(); index++) {
            StudioKey task = loose.get(index);
            result.add(node(task, store.view(task), index % 8, lowest + (result.isEmpty() ? 0 : 1) + index / 8));
        }
        nodes = result;
        Map<StudioKey, Node> byKey = new HashMap<>();
        for (Node node : nodes) byKey.put(node.key(), node);
        List<Link> found = new ArrayList<>();
        for (Node node : nodes) for (String field : List.of("requires", "requires_any", "connects")) for (String id : StudioJson.strings(store.view(node.key()), field)) {
            Node source = byKey.get(new StudioKey(AuthoringKind.TASK, id));
            if (source != null) found.add(new Link(source.key(), node.key(), field, route(source, node)));
        }
        links = found;
    }

    private Node node(StudioKey task, JsonObject body, int column, int row) {
        TaskAppearance appearance;
        try { appearance = TaskAppearance.parse(body); }
        catch (RuntimeException exception) { appearance = TaskAppearance.parse(new JsonObject()); }
        int size = Math.max(10, Math.round(32 * zoom * appearance.size()));
        int offsetX = moving && selected.contains(task) ? moveX : 0;
        int offsetY = moving && selected.contains(task) ? moveY : 0;
        int centerX = (int) Math.round(x + panX + column * cell()) + offsetX;
        int centerY = (int) Math.round(y + panY + row * cell()) + offsetY;
        return new Node(task, centerX - size / 2, centerY - size / 2, size, column, row, appearance);
    }

    private static int[] route(Node from, Node to) {
        int fx = from.x() + from.size() / 2;
        int fy = from.y() + from.size() / 2;
        int tx = to.x() + to.size() / 2;
        int ty = to.y() + to.size() / 2;
        TaskAppearance appearance = to.appearance();
        boolean vertical = appearance.lineAxis() == TaskAppearance.LineAxis.VERTICAL || appearance.lineAxis() == TaskAppearance.LineAxis.AUTO && Math.abs(ty - fy) > Math.abs(tx - fx);
        if (vertical) {
            int bend = Math.round(ty + (fy - ty) * appearance.lineBend());
            return new int[]{fx, fy, fx, bend, tx, bend, tx, ty};
        }
        int bend = Math.round(tx + (fx - tx) * appearance.lineBend());
        return new int[]{fx, fy, bend, fy, bend, ty, tx, ty};
    }

    private void segment(int x1, int y1, int x2, int y2, int color, boolean dashed) {
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        int width = Math.max(2, Math.abs(x2 - x1) + 2);
        int height = Math.max(2, Math.abs(y2 - y1) + 2);
        if (!dashed) {
            kit.fill(left - 1, top - 1, width, height, color);
            return;
        }
        if (width > height) for (int at = 0; at < width; at += 6) kit.fill(left - 1 + at, top - 1, Math.min(3, width - at), 2, color);
        else for (int at = 0; at < height; at += 6) kit.fill(left - 1, top - 1 + at, 2, Math.min(3, height - at), color);
    }

    void render(int px, int py, int pw, int ph) {
        x = px;
        y = py;
        w = pw;
        h = ph;
        if (!fitted && store.ready() && w > 0) fit();
        build();
        kit.fill(x, y, w, h, StudioTheme.desk);
        kit.clip(x, y, w, h);
        float step = cell() / 2;
        if (step >= 8) for (float gx = (float) (panX % step); gx < w; gx += step) for (float gy = (float) (panY % step); gy < h; gy += step)
            kit.fill(x + Math.round(gx), y + Math.round(gy), 1, 1, StudioTheme.line);
        kit.hit(x, y, w, h, this::press);
        kit.wheel(x, y, w, h, this::wheel);
        kit.dropZone(x, y, w, h, value -> value instanceof StudioKey key && key.kind() == AuthoringKind.TASK, value -> place((StudioKey) value, column(kit.mx), row(kit.my)));
        for (Link link : links) {
            boolean lit = selected.contains(link.to()) || selected.contains(link.from());
            int color = link.field().equals("connects") ? StudioTheme.line : lit ? StudioTheme.accent : StudioTheme.strongLine;
            int[] points = link.points();
            for (int at = 0; at + 3 < points.length; at += 2) segment(points[at], points[at + 1], points[at + 2], points[at + 3], color, link.field().equals("requires_any"));
        }
        for (Node node : nodes) if (node.x() + node.size() > x - 40 && node.x() < x + w + 40 && node.y() + node.size() > y - 20 && node.y() < y + h + 20) renderNode(node);
        if (linking != null) {
            Node source = find(linking);
            if (source != null) {
                Node over = at(kit.mx, kit.my);
                int tx = over == null ? Math.round((float) (x + panX + column(kit.mx) * cell())) : over.x() + over.size() / 2;
                int ty = over == null ? Math.round((float) (y + panY + row(kit.my) * cell())) : over.y() + over.size() / 2;
                int sx = source.x() + source.size() / 2;
                int sy = source.y() + source.size() / 2;
                segment(sx, sy, tx, sy, StudioTheme.accent, true);
                segment(tx, sy, tx, ty, StudioTheme.accent, true);
                if (over == null) {
                    int size = Math.round(32 * zoom);
                    kit.fill(tx - size / 2, ty - size / 2, size, size, StudioTheme.button);
                    kit.dashed(tx - size / 2, ty - size / 2, size, size, StudioTheme.accent);
                    kit.text("+", tx - 2, ty - 4, 10, StudioTheme.accent);
                    label(t("map.drop_new"), tx, ty + size / 2 + 4, 80, StudioTheme.text);
                }
            }
        }
        if (box != null) {
            int left = Math.min(box[0], box[2]);
            int top = Math.min(box[1], box[3]);
            kit.outline(left, top, Math.abs(box[2] - box[0]), Math.abs(box[3] - box[1]), StudioTheme.accent);
        }
        if (nodes.isEmpty() && store.ready()) {
            String message = t("map.empty");
            kit.text(message, x + Math.max(6, (w - kit.width(message)) / 2), y + h / 2 - 12, w - 12, StudioTheme.soft);
            kit.button(x + (w - 90) / 2, y + h / 2 + 4, 90, 16, t("new_kind", StudioText.kind(AuthoringKind.TASK)), StudioKit.PRIMARY, true, "", () -> create(0, 0, null));
        } else if (w > 250 && h > 120) help();
        kit.unclip();
    }

    private void help() {
        List<String> lines = List.of(t("map.help1"), t("map.help2"), t("map.help3"));
        int width = lines.stream().mapToInt(kit::width).max().orElse(0) + 12;
        if (width > w - 16) return;
        int top = y + h - 44;
        kit.box(x + 7, top, width, 38, StudioTheme.window, StudioTheme.line);
        for (int index = 0; index < lines.size(); index++) kit.text(lines.get(index), x + 13, top + 5 + index * 11, width - 10, StudioTheme.soft);
    }

    private void label(String text, int centerX, int top, int width, int color) {
        List<String> lines = new ArrayList<>();
        String rest = text;
        while (!rest.isEmpty() && lines.size() < 2) {
            String part = kit.font().plainSubstrByWidth(rest, width);
            if (part.length() < rest.length()) {
                int space = part.lastIndexOf(' ');
                if (space > 0) part = part.substring(0, space);
            }
            if (part.isEmpty()) break;
            rest = rest.substring(part.length()).trim();
            lines.add(part.trim());
        }
        if (!rest.isEmpty() && !lines.isEmpty()) lines.set(lines.size() - 1, kit.trim(lines.get(lines.size() - 1) + " " + rest, width));
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int size = kit.width(line);
            kit.fill(centerX - size / 2 - 1, top + index * 9 - 1, size + 2, 9, StudioTheme.desk);
            kit.g().drawString(kit.font(), line, centerX - size / 2, top + index * 9, color, false);
        }
    }

    private void renderNode(Node node) {
        JsonObject body = store.view(node.key());
        boolean chosen = selected.contains(node.key());
        boolean hover = kit.over(node.x(), node.y(), node.size(), node.size());
        boolean problem = store.broken(node.key());
        int border = problem ? StudioTheme.error : store.dirty(node.key()) ? StudioTheme.accent : hover ? StudioTheme.accent : StudioTheme.strongLine;
        if (chosen) kit.outline(node.x() - 3, node.y() - 3, node.size() + 6, node.size() + 6, StudioTheme.strong);
        if (node.appearance().shape() == TaskAppearance.Shape.SQUARE) kit.box(node.x(), node.y(), node.size(), node.size(), StudioTheme.button, border);
        else TaskShapeRenderer.draw(kit.g(), node.appearance().shape(), node.x(), node.y(), node.size(), StudioTheme.button, border, StudioTheme.accent, false);
        int icon = Math.max(6, Math.round(node.size() * 0.6F * (float) node.appearance().shape().iconFit() * node.appearance().iconScale()));
        StudioIcons.task(store, body).draw(kit, node.x() + (node.size() - icon) / 2, node.y() + (node.size() - icon) / 2, icon);
        if (zoom >= .55F) label(StudioText.label(node.key(), body), node.x() + node.size() / 2, node.y() + node.size() + 9, Math.max(40, Math.round(64 * zoom)), chosen ? StudioTheme.strong : StudioTheme.text);
        kit.hit(node.x(), node.y(), node.size(), node.size(), click -> pressNode(node, click));
        if (hover && zoom < .55F) kit.tooltip(StudioText.label(node.key(), body));
        boolean near = kit.over(node.x() - 8, node.y() - 8, node.size() + 18, node.size() + 18);
        if ((chosen && selected.size() == 1 || near) && linking == null && !moving && box == null) {
            handle(node, node.x() + node.size() + 3, node.y() + node.size() / 2 - 3);
            handle(node, node.x() + node.size() / 2 - 3, node.y() + node.size() + (zoom >= .55F ? 1 : 3));
        }
    }

    private void handle(Node node, int hx, int hy) {
        boolean hover = kit.hover(hx - 2, hy - 2, 10, 10);
        kit.fill(hx, hy, 6, 6, hover ? StudioTheme.strong : StudioTheme.accent);
        if (hover) kit.tooltip(t("map.handle"));
        kit.hit(hx - 2, hy - 2, 10, 10, click -> {
            if (click.button() != 0) return;
            linking = node.key();
            kit.capture(new StudioKit.Capture() {
                @Override public void move(int px, int py) { }
                @Override public void release(int px, int py) { finishLink(px, py); }
            });
        });
    }

    private Node find(StudioKey key) {
        for (Node node : nodes) if (node.key().equals(key)) return node;
        return null;
    }

    private Node at(int px, int py) {
        for (int index = nodes.size() - 1; index >= 0; index--) if (StudioKit.inside(nodes.get(index).x(), nodes.get(index).y(), nodes.get(index).size(), nodes.get(index).size(), px, py)) return nodes.get(index);
        return null;
    }

    private void finishLink(int px, int py) {
        StudioKey source = linking;
        linking = null;
        if (!StudioKit.inside(x, y, w, h, px, py)) return;
        Node over = at(px, py);
        if (over == null) {
            create(column(px), row(py), source);
            return;
        }
        if (over.key().equals(source)) return;
        link(source, over.key());
    }

    void link(StudioKey source, StudioKey target) {
        store.with(List.of(target), () -> {
            if (StudioJson.contains(store.view(target), "requires", source.id())) return;
            store.edit(target, "", body -> StudioJson.add(body, "requires", source.id()));
            screen.store.say(t("map.linked", store.label(target), store.label(source)), true);
        });
    }

    void create(int column, int row, StudioKey after) {
        store.transaction(() -> {
            StudioKey made = screen.make(AuthoringKind.TASK, t("untitled", StudioText.kind(AuthoringKind.TASK)), false);
            store.edit(made, "", body -> {
                JsonObject position = new JsonObject();
                position.addProperty("x", Math.max(-128, Math.min(128, column)));
                position.addProperty("y", Math.max(-128, Math.min(128, row)));
                body.add("position", position);
                if (!category.isEmpty()) body.addProperty("category", category);
                if (after != null) {
                    StudioJson.add(body, "requires", after.id());
                    body.addProperty("category", StudioJson.string(store.view(after), "category", "antos:general"));
                }
            });
            if (after == null && !disk.isEmpty()) store.putOnDisk(made, new StudioKey(AuthoringKind.DISK, disk));
            selected.clear();
            selected.add(made);
        });
    }

    private void place(StudioKey task, int column, int row) {
        store.with(List.of(task), () -> store.edit(task, "", body -> {
            JsonObject position = new JsonObject();
            position.addProperty("x", Math.max(-128, Math.min(128, column)));
            position.addProperty("y", Math.max(-128, Math.min(128, row)));
            body.add("position", position);
        }));
    }

    private void pressNode(Node node, StudioKit.Click click) {
        if (click.button() == 1) {
            if (!selected.contains(node.key())) {
                selected.clear();
                selected.add(node.key());
            }
            if (selected.size() > 1) manyMenu(click.x(), click.y());
            else screen.contextMenu(node.key(), null, click.x(), click.y());
            return;
        }
        if (click.button() != 0) { pan(click); return; }
        if (click.shift() || click.control()) {
            if (!selected.remove(node.key())) selected.add(node.key());
            return;
        }
        if (!selected.contains(node.key())) {
            selected.clear();
            selected.add(node.key());
        }
        if (selected.size() == 1) screen.open(node.key());
        int startX = click.x();
        int startY = click.y();
        kit.capture(new StudioKit.Capture() {
            @Override public void move(int px, int py) {
                if (!moving && Math.abs(px - startX) + Math.abs(py - startY) < 5) return;
                moving = true;
                moveX = px - startX;
                moveY = py - startY;
            }
            @Override public void release(int px, int py) {
                if (!moving) {
                    selected.clear();
                    selected.add(node.key());
                    screen.open(node.key());
                    return;
                }
                moving = false;
                int columns = Math.round(moveX / cell());
                int rows = Math.round(moveY / cell());
                moveX = 0;
                moveY = 0;
                if (columns != 0 || rows != 0) moveSelected(columns, rows);
            }
        });
    }

    private void moveSelected(int columns, int rows) {
        Map<StudioKey, int[]> targets = new HashMap<>();
        for (Node node : nodes) if (selected.contains(node.key())) targets.put(node.key(), new int[]{node.column() + columns, node.row() + rows});
        store.with(targets.keySet(), () -> store.transaction(() -> targets.forEach((key, position) -> place(key, position[0], position[1]))));
    }

    void nudge(int columns, int rows) {
        if (!selected.isEmpty()) moveSelected(columns, rows);
    }

    private void pan(StudioKit.Click click) {
        double startX = panX;
        double startY = panY;
        kit.capture(new StudioKit.Capture() {
            private boolean moved;
            @Override public void move(int px, int py) {
                if (Math.abs(px - click.x()) + Math.abs(py - click.y()) > 3) moved = true;
                if (!moved) return;
                panX = startX + px - click.x();
                panY = startY + py - click.y();
            }
            @Override public void release(int px, int py) {
                if (!moved && click.button() == 1) emptyMenu(px, py);
            }
        });
    }

    private void press(StudioKit.Click click) {
        if (click.button() != 0 || space) { pan(click); return; }
        if (click.twice()) {
            create(column(click.x()), row(click.y()), null);
            return;
        }
        Link link = linkAt(click.x(), click.y());
        if (link != null) {
            linkMenu(link, click);
            return;
        }
        int startX = click.x();
        int startY = click.y();
        boolean add = click.shift() || click.control();
        kit.capture(new StudioKit.Capture() {
            @Override public void move(int px, int py) {
                if (box == null && Math.abs(px - startX) + Math.abs(py - startY) < 5) return;
                box = new int[]{startX, startY, px, py};
            }
            @Override public void release(int px, int py) {
                if (!add) selected.clear();
                if (box == null) return;
                int left = Math.min(box[0], box[2]);
                int right = Math.max(box[0], box[2]);
                int top = Math.min(box[1], box[3]);
                int bottom = Math.max(box[1], box[3]);
                for (Node node : nodes) if (node.x() + node.size() > left && node.x() < right && node.y() + node.size() > top && node.y() < bottom) selected.add(node.key());
                box = null;
            }
        });
    }

    private Link linkAt(int px, int py) {
        for (Link link : links) {
            int[] points = link.points();
            for (int at = 0; at + 3 < points.length; at += 2) {
                int left = Math.min(points[at], points[at + 2]) - 3;
                int right = Math.max(points[at], points[at + 2]) + 3;
                int top = Math.min(points[at + 1], points[at + 3]) - 3;
                int bottom = Math.max(points[at + 1], points[at + 3]) + 3;
                if (px >= left && px <= right && py >= top && py <= bottom) return link;
            }
        }
        return null;
    }

    private void linkMenu(Link link, StudioKit.Click click) {
        StudioMenu menu = new StudioMenu(screen, click.x(), click.y(), 2).heading(store.label(link.from()) + " > " + store.label(link.to()));
        for (String field : List.of("requires", "requires_any", "connects")) menu.check(t("map.link_" + field), link.field().equals(field), () -> relink(link, field));
        menu.separator().danger(t("map.remove_link"), true, () -> relink(link, ""));
        screen.popup(menu);
    }

    private void relink(Link link, String field) {
        store.with(List.of(link.to()), () -> store.edit(link.to(), "", body -> {
            StudioJson.remove(body, link.field(), link.from().id());
            if (!field.isEmpty()) StudioJson.add(body, field, link.from().id());
        }));
    }

    private void emptyMenu(int px, int py) {
        screen.popup(new StudioMenu(screen, px, py, 0)
                .item(t("map.new_here"), () -> create(column(px), row(py), null))
                .item(t("map.fit"), () -> fitted = false)
                .item(t("map.select_all"), () -> { for (Node node : nodes) selected.add(node.key()); }));
    }

    void manyMenu(int px, int py) { manyMenuFor(List.copyOf(selected), px, py); }

    void manyMenuFor(List<StudioKey> keys, int px, int py) {
        screen.popup(new StudioMenu(screen, px, py, 0).heading(t("selected", keys.size()))
                .item(t("bulk.floppy"), () -> screen.bulkFloppy(keys, px, py))
                .item(t("bulk.category"), () -> screen.bulkCategory(keys, px, py))
                .item(t("bulk.reward"), () -> screen.bulkReward(keys, px, py))
                .item(t("bulk.link"), () -> screen.bulkLink(keys))
                .separator().danger(t("bulk.remove"), true, () -> screen.bulkRemove(keys)));
    }

    private void wheel(double amount) {
        zoomBy(amount > 0 ? 1.12F : 1 / 1.12F, kit.mx, kit.my);
    }

    void zoomBy(float factor, int px, int py) {
        float before = zoom;
        zoom = Math.max(.3F, Math.min(2.0F, zoom * factor));
        double ratio = zoom / before;
        panX = px - x - (px - x - panX) * ratio;
        panY = py - y - (py - y - panY) * ratio;
    }

    void zoomCenter(float factor) { zoomBy(factor, x + w / 2, y + h / 2); }
    int zoomPercent() { return Math.round(zoom * 100); }

    private void fit() {
        fitted = true;
        zoom = 1.0F;
        panX = 0;
        panY = 0;
        build();
        if (nodes.isEmpty()) {
            panX = w / 2.0;
            panY = h / 2.0 - 10;
            return;
        }
        int minColumn = nodes.stream().mapToInt(Node::column).min().orElse(0);
        int maxColumn = nodes.stream().mapToInt(Node::column).max().orElse(0);
        int minRow = nodes.stream().mapToInt(Node::row).min().orElse(0);
        int maxRow = nodes.stream().mapToInt(Node::row).max().orElse(0);
        float wide = (w - 60) / Math.max(1F, (maxColumn - minColumn + 1) * 56F);
        float tall = (h - 90) / Math.max(1F, (maxRow - minRow + 1) * 56F);
        zoom = Math.max(.35F, Math.min(1.0F, Math.min(wide, tall)));
        panX = w / 2.0 - (minColumn + maxColumn) / 2.0 * cell();
        panY = (h - 30) / 2.0 - (minRow + maxRow) / 2.0 * cell();
    }

    boolean removeSelected() {
        if (selected.isEmpty()) return false;
        screen.bulkRemove(List.copyOf(selected));
        return true;
    }

    void selectAll() { for (Node node : nodes) selected.add(node.key()); }

    int[] center(StudioKey key) {
        Node node = find(key);
        return node == null ? null : new int[]{node.x() + node.size() / 2, node.y() + node.size() / 2, node.size()};
    }
}
