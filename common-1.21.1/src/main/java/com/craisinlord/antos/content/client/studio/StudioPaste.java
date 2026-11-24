package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class StudioPaste extends StudioPopup {
    private static final class Row {
        String title = "";
        final List<JsonObject> goals = new ArrayList<>();
        final List<JsonObject> rewards = new ArrayList<>();
        final List<String> unknown = new ArrayList<>();
    }

    private final AntStudioScreen screen;
    private String text;
    private List<Row> rows = List.of();
    private boolean link;

    StudioPaste(AntStudioScreen screen, String text) {
        this.screen = screen;
        this.text = text;
        parse();
    }

    private static String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    private void parse() {
        List<Row> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank() || result.size() >= 128) continue;
            String[] cells = line.split("\t", -1);
            Row row = new Row();
            row.title = cells[0].trim();
            if (row.title.isEmpty()) continue;
            if (cells.length > 1) for (String part : cells[1].split("[,;+]")) {
                if (part.isBlank()) continue;
                StudioParse.Amount amount = StudioParse.amount(part);
                if (amount == null) row.unknown.add(part.trim());
                else row.goals.add(amount.goal());
            }
            if (cells.length > 2) for (String part : cells[2].split("[,;+]")) {
                if (part.isBlank()) continue;
                JsonObject reward = StudioParse.reward(part);
                if (reward == null) row.unknown.add(part.trim());
                else row.rewards.add(reward);
            }
            if (cells.length == 1) {
                StudioParse.Amount amount = StudioParse.amount(row.title);
                if (amount != null) row.goals.add(amount.goal());
            }
            result.add(row);
        }
        rows = result;
    }

    @Override
    boolean shade() { return true; }

    @Override
    void layout(StudioKit kit) { center(kit, 420, 280); }

    @Override
    void render(StudioKit kit) {
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        kit.text(t("paste.title"), x + 8, y + 8, w - 16, StudioTheme.strong);
        kit.area("paste:text", x + 8, y + 22, w - 16, 70, text, 65536, t("paste.hint"), false, value -> {
            text = value;
            parse();
        });
        int top = y + 98;
        int bottom = y + h - 26;
        int offset = kit.scroll("paste", x + 6, top, w - 12, bottom - top, rows.size() * 18);
        kit.clip(x + 6, top, w - 12, bottom - top);
        int rowY = top - offset;
        for (Row row : rows) {
            kit.text(row.title, x + 10, rowY + 5, w / 3 - 14, StudioTheme.text);
            int cx = x + w / 3;
            for (JsonObject goal : row.goals) {
                StudioIcon icon = StudioIcons.objective(screen.store, goal);
                if (icon != null) icon.draw(kit, cx, rowY + 2, 13);
                cx += 15 + kit.text("x" + StudioJson.integer(goal, "count", 1), cx + 15, rowY + 5, 40, StudioTheme.muted) + 5;
            }
            kit.text(">", cx, rowY + 5, 8, StudioTheme.soft);
            cx += 9;
            for (JsonObject reward : row.rewards) {
                StudioIcons.reward(screen.store, reward).draw(kit, cx, rowY + 2, 13);
                int amount = StudioJson.integer(reward, "count", StudioJson.integer(reward, "amount", StudioJson.integer(reward, "points", 1)));
                cx += 15 + kit.text("x" + amount, cx + 15, rowY + 5, 50, StudioTheme.muted) + 5;
            }
            if (!row.unknown.isEmpty()) {
                kit.dot(cx, rowY + 6, true, true);
                kit.text(t("paste.unknown", String.join(", ", row.unknown)), cx + 8, rowY + 5, x + w - 14 - cx - 8, StudioTheme.warning);
            }
            rowY += 18;
        }
        kit.unclip();
        if (rows.isEmpty()) kit.wrap(t("paste.empty"), x + 10, top + 6, w - 20, StudioTheme.soft);
        kit.check(x + 8, bottom + 7, t("paste.link"), link, value -> link = value);
        int size = kit.buttonWidth(t("paste.add", rows.size()));
        kit.button(x + w - size - 8, bottom + 5, size, 16, t("paste.add", rows.size()), StudioKit.PRIMARY, !rows.isEmpty(), "", this::add);
        int cancel = kit.buttonWidth(t("cancel"));
        kit.button(x + w - size - cancel - 12, bottom + 5, cancel, 16, t("cancel"), StudioKit.NORMAL, true, "", () -> screen.closePopup(this));
    }

    void add() {
        StudioStore store = screen.store;
        List<StudioKey> made = new ArrayList<>();
        store.transaction(() -> {
            int lowest = 0;
            for (StudioKey task : screen.map.tasks()) lowest = Math.max(lowest, StudioJson.integer(StudioJson.object(store.view(task), "position"), "y", -1) + 1);
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                StudioKey key = screen.create(AuthoringKind.TASK, row.title);
                int column = index % 8;
                int line = lowest + 1 + index / 8;
                StudioKey previous = made.isEmpty() ? null : made.get(made.size() - 1);
                store.edit(key, "", body -> {
                    JsonArray goals = new JsonArray();
                    row.goals.forEach(goals::add);
                    body.add("objectives", goals);
                    JsonArray rewards = new JsonArray();
                    row.rewards.forEach(rewards::add);
                    body.add("rewards", rewards);
                    JsonObject position = new JsonObject();
                    position.addProperty("x", column);
                    position.addProperty("y", Math.min(128, line));
                    body.add("position", position);
                    if (!screen.map.category.isEmpty()) body.addProperty("category", screen.map.category);
                    if (link && previous != null) StudioJson.add(body, "requires", previous.id());
                });
                if (!screen.map.disk.isEmpty() && (!link || previous == null)) store.putOnDisk(key, new StudioKey(AuthoringKind.DISK, screen.map.disk));
                made.add(key);
            }
        });
        screen.closePopup(this);
        store.say(t("paste.added", made.size()), true);
        screen.table.selected.clear();
        screen.table.selected.addAll(made);
    }
}
