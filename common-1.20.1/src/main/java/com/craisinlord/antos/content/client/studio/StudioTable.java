package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;

final class StudioTable {
    private final AntStudioScreen screen;
    private final StudioStore store;
    private final StudioKit kit;
    final Set<StudioKey> selected = new LinkedHashSet<>();
    private StudioKey anchor;
    private String filter = "";
    private String adding = "";
    private List<StudioKey> rows = List.of();

    StudioTable(AntStudioScreen screen) {
        this.screen = screen;
        store = screen.store;
        kit = screen.kit;
    }

    private String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    void filterField(int x, int y, int w) {
        kit.field("table:filter", x, y, w, filter, new StudioKit.Field().hint(t("filter", t("category.tasks").toLowerCase(Locale.ROOT))).max(128), value -> filter = value);
    }

    void render(int x, int y, int w, int h) {
        String query = filter.toLowerCase(Locale.ROOT).trim();
        rows = screen.map.tasks().stream().filter(key -> query.isEmpty() || (store.label(key) + " " + key.id()).toLowerCase(Locale.ROOT).contains(query)).toList();
        selected.removeIf(key -> !store.exists(key));
        kit.fill(x, y, w, h, StudioTheme.window);
        int top = y;
        if (!selected.isEmpty()) {
            kit.fill(x, top, w, 22, StudioTheme.selected);
            List<StudioKey> keys = rows.stream().filter(selected::contains).toList();
            int cx = x + 6;
            cx += kit.text(t("selected", keys.size()), cx, top + 7, 90, StudioTheme.strong) + 8;
            cx += bulk(cx, top, t("bulk.floppy"), (bx, by) -> screen.bulkFloppy(keys, bx, by));
            cx += bulk(cx, top, t("bulk.category"), (bx, by) -> screen.bulkCategory(keys, bx, by));
            cx += bulk(cx, top, t("bulk.reward"), (bx, by) -> screen.bulkReward(keys, bx, by));
            cx += bulk(cx, top, t("bulk.link"), (bx, by) -> screen.bulkLink(keys));
            cx += bulk(cx, top, t("duplicate"), (bx, by) -> store.with(keys, () -> store.transaction(() -> keys.forEach(store::duplicate))));
            cx += bulk(cx, top, t("bulk.remove"), (bx, by) -> screen.bulkRemove(keys));
            int size = Math.min(kit.buttonWidth(t("clear")), x + w - 4 - cx - 4);
            if (size > 20) kit.button(x + w - size - 4, top + 3, size, 16, t("clear"), StudioKit.GHOST, true, "", selected::clear);
            top += 22;
        }
        int titleX = x + 22;
        int rest = w - 22;
        int titleW = rest * 28 / 100;
        int whoW = rest * 22 / 100;
        int needsW = rest * 27 / 100;
        int whoX = titleX + titleW;
        int needsX = whoX + whoW;
        int getsX = needsX + needsW;
        int getsW = x + w - getsX;
        boolean all = !rows.isEmpty() && selected.containsAll(rows);
        box(x + 6, top + 4, all, () -> {
            if (all) selected.clear();
            else selected.addAll(rows);
        });
        kit.text(t("column.title"), titleX + 4, top + 6, titleW - 6, StudioTheme.muted);
        kit.text(t("section.who_task"), whoX + 4, top + 6, whoW - 6, StudioTheme.muted);
        kit.text(t("section.needs"), needsX + 4, top + 6, needsW - 6, StudioTheme.muted);
        kit.text(t("section.gets"), getsX + 4, top + 6, getsW - 6, StudioTheme.muted);
        kit.fill(x, top + 18, w, 1, StudioTheme.strongLine);
        top += 19;
        int bottom = y + h - 22;
        int offset = kit.scroll("table", x, top, w, bottom - top, rows.size() * 20 + 24);
        kit.clip(x, top, w, bottom - top);
        int rowY = top - offset;
        List<StudioKey> needed = new ArrayList<>();
        for (StudioKey key : rows) {
            if (rowY + 20 > top && rowY < bottom) {
                if (store.loaded(key) == null) needed.add(key);
                row(key, x, rowY, w, titleX, titleW, whoX, whoW, needsX, needsW, getsX, getsW);
            }
            rowY += 20;
        }
        if (!needed.isEmpty()) store.load(needed);
        kit.text("+", x + 9, rowY + 8, 8, StudioTheme.accent);
        kit.field("table:add", titleX, rowY + 3, Math.min(w - 40, 260), adding, new StudioKit.Field().hint(t("table.add")).max(128).enter(this::addRow), value -> adding = value);
        kit.unclip();
        kit.fill(x, bottom, w, 1, StudioTheme.line);
        int size = kit.buttonWidth(t("table.paste"));
        kit.button(x + 6, bottom + 3, size, 16, t("table.paste"), StudioKit.NORMAL, true, "", this::paste);
        kit.text(t("table.paste_note"), x + size + 14, bottom + 7, w - size - 20, StudioTheme.soft);
    }

    private interface Place { void run(int x, int y); }

    private int bulk(int x, int top, String label, Place action) {
        int size = kit.width(label) + 9;
        kit.button(x, top + 3, size, 16, label, StudioKit.NORMAL, !store.saving(), "", () -> action.run(x, top + 19));
        return size + 3;
    }

    private void box(int x, int y, boolean on, Runnable action) {
        boolean hover = kit.hover(x, y, 10, 10);
        kit.box(x, y, 9, 9, on ? StudioTheme.accent : StudioTheme.desk, hover ? StudioTheme.accent : StudioTheme.strongLine);
        kit.press(x - 3, y - 3, 15, 15, action);
    }

    private void row(StudioKey key, int x, int y, int w, int titleX, int titleW, int whoX, int whoW, int needsX, int needsW, int getsX, int getsW) {
        boolean on = selected.contains(key);
        if (on) kit.fill(x, y, w, 19, StudioTheme.selected);
        else if (kit.hover(x, y, w, 19)) kit.fill(x, y, w, 19, StudioTheme.section);
        kit.fill(x, y + 19, w, 1, StudioTheme.line);
        kit.hit(x, y, w, 19, click -> {
            if (click.button() == 1) {
                if (on && selected.size() > 1) screen.map.manyMenuFor(List.copyOf(selected), click.x(), click.y());
                else screen.contextMenu(key, null, click.x(), click.y());
                return;
            }
            if (click.twice()) { screen.open(key); return; }
            toggle(key, click.shift(), click.control());
        });
        box(x + 6, y + 5, on, () -> {
            if (!selected.remove(key)) selected.add(key);
            anchor = key;
        });
        JsonObject body = store.view(key);
        StudioIcons.task(store, body).draw(kit, titleX + 2, y + 4, 12);
        StudioNode node = new StudioNode(store, key);
        boolean ready = store.loaded(key) != null;
        int mark = store.broken(key) || store.dirty(key) ? 8 : 0;
        if (ready) {
            String raw = StudioJson.string(body, "title");
            kit.field("table:" + node.id("title"), titleX + 16, y + 2, titleW - 20 - mark, StudioText.read(raw), new StudioKit.Field().plain().max(512),
                    value -> node.put("title", StudioText.write(AuthoringKind.TASK, "title", raw, value)));
        } else kit.text(store.label(key), titleX + 20, y + 6, titleW - 24 - mark, StudioTheme.text);
        if (mark > 0) kit.dot(titleX + titleW - 8, y + 8, store.broken(key), false);
        who(key, node, body, whoX, y, whoW);
        if (ready) {
            needs(node, body, needsX, y, needsW);
            gets(node, body, getsX, y, getsW);
        } else kit.text("...", needsX + 4, y + 6, needsW, StudioTheme.soft);
    }

    private void toggle(StudioKey key, boolean range, boolean add) {
        if (range && anchor != null && rows.contains(anchor)) {
            int from = rows.indexOf(anchor);
            int to = rows.indexOf(key);
            if (!add) selected.clear();
            for (int index = Math.min(from, to); index <= Math.max(from, to); index++) selected.add(rows.get(index));
            return;
        }
        if (add) {
            if (!selected.remove(key)) selected.add(key);
        } else if (selected.size() == 1 && selected.contains(key)) selected.clear();
        else {
            selected.clear();
            selected.add(key);
        }
        anchor = key;
    }

    private void who(StudioKey key, StudioNode node, JsonObject body, int x, int y, int w) {
        List<String> after = new ArrayList<>(StudioJson.strings(body, "requires"));
        after.addAll(StudioJson.strings(body, "requires_any"));
        StudioKey disk = store.keys(AuthoringKind.DISK).stream().filter(value -> store.direct(key, value)).findFirst().orElse(null);
        String label;
        int left = x + 4;
        int color = StudioTheme.text;
        if (!after.isEmpty()) label = t("who.after", store.label(AuthoringKind.TASK, after.get(0))) + (after.size() > 1 ? " +" + (after.size() - 1) : "");
        else if (StudioJson.string(body, "availability", "disk").equals("automatic")) label = t("who.everyone");
        else if (disk != null) {
            StudioIcons.of(store, disk).draw(kit, left, y + 4, 11);
            left += 14;
            label = store.label(disk);
        } else {
            label = t("who.nobody_short");
            color = StudioTheme.warning;
        }
        boolean hover = kit.hover(x, y, w - 4, 19);
        kit.text(label, left, y + 6, x + w - 8 - left, hover ? StudioTheme.accent : color);
        kit.hit(x, y, w - 4, 19, click -> {
            StudioMenu menu = new StudioMenu(screen, x, y, 19);
            menu.item(t("who.everyone"), () -> store.with(List.of(key), () -> node.change("", target -> {
                JsonObject object = target.getAsJsonObject();
                object.addProperty("availability", "automatic");
                object.remove("requires");
                object.remove("requires_any");
            })));
            menu.item(t("who.floppy") + "...", () -> screen.editors.pickRef("kind:disk", x, y, false, value -> store.putOnDisk(key, new StudioKey(AuthoringKind.DISK, value))));
            menu.item(t("who.after_task") + "...", () -> screen.editors.pickRef("kind:task", x, y, false, value -> {
                if (!value.equals(key.id())) store.with(List.of(key), () -> node.change("", target -> {
                    JsonArray list = new JsonArray();
                    list.add(value);
                    target.getAsJsonObject().add("requires", list);
                }));
            }));
            screen.popup(menu);
        });
    }

    private void needs(StudioNode node, JsonObject body, int x, int y, int w) {
        JsonArray goals = StudioJson.list(body, "objectives");
        if (goals.isEmpty()) {
            kit.button(x + 2, y + 2, kit.buttonWidth("+ " + t("goal")), 15, "+ " + t("goal"), StudioKit.ADD, true, "", () -> screen.popup(new StudioPicker(screen, x, y, 18, picked -> {
                JsonObject goal = StudioEditors.newGoal(StudioEditors.GOALS.get(0));
                goal.addProperty("target", picked.id());
                node.at("objectives").add(goal);
            }).items(false, false)));
            return;
        }
        StudioNode row = node.at("objectives").at(0);
        JsonObject goal = goals.get(0).getAsJsonObject();
        StudioEditors.GoalType type = StudioEditors.goal(goal);
        String verb = type == null ? StudioText.prettyId(StudioJson.string(goal, "type")) : t("goal." + type.id());
        int left = x + 4;
        left += kit.text(verb, left, y + 6, 56, StudioTheme.muted) + 5;
        StudioIcon icon = StudioIcons.objective(store, goal);
        if (icon != null) {
            int slotX = left;
            if (type != null && (type.reference().equals("item") || type.reference().equals("entity"))) {
                kit.slot(slotX, y + 1, 17, icon, false, screen.editors.goalName(goal), click -> {
                    StudioPicker picker = new StudioPicker(screen, slotX, y, 18, picked -> row.put("target", picked.id()));
                    if (type.reference().equals("entity")) picker.entities();
                    else picker.items(false, false);
                    screen.popup(picker);
                });
            } else icon.draw(kit, slotX + 2, y + 3, 13);
            left += 20;
        }
        int right = x + w - 4;
        if (goals.size() > 1) right -= kit.right("+" + (goals.size() - 1), right, y + 6, StudioTheme.soft) + 4;
        if (type == null || type.counted()) kit.number("table:" + row.id("count") + "!number", left, y + 2, Math.max(24, Math.min(40, right - left)), StudioJson.number(goal, "count", 1), 1, 1_000_000, true, value -> row.put("count", (long) value));
        else kit.text(screen.editors.goalName(goal), left, y + 6, right - left, StudioTheme.text);
    }

    private void gets(StudioNode node, JsonObject body, int x, int y, int w) {
        JsonArray rewards = StudioJson.list(body, "rewards");
        int left = x + 4;
        for (int index = 0; index < rewards.size(); index++) {
            if (left + 60 > x + w - 18) {
                kit.text("+" + (rewards.size() - index), left, y + 6, 20, StudioTheme.soft);
                left += 18;
                break;
            }
            JsonObject reward = rewards.get(index).getAsJsonObject();
            StudioNode row = node.at("rewards").at(index);
            String type = StudioJson.string(reward, "type");
            StudioIcons.reward(store, reward).draw(kit, left, y + 3, 13);
            if (kit.hover(left, y + 2, 15, 15)) kit.tooltip(StudioSentence.rewardName(store, reward));
            left += 16;
            String field = switch (type) {
                case "item" -> "count";
                case "antcoins" -> "amount";
                case "experience" -> "points";
                case "experience_levels" -> "levels";
                default -> "";
            };
            if (!field.isEmpty()) {
                kit.number("table:" + row.id(field) + "!number", left, y + 2, 34, StudioJson.number(reward, field, 1), 1, field.equals("count") ? 108 : field.equals("levels") ? 1000 : 1_000_000, true, value -> row.put(field, (long) value));
                left += 38;
            }
        }
        int addX = Math.min(left, x + w - 18);
        kit.button(addX, y + 2, 15, 15, "+", StudioKit.ADD, true, t("bulk.reward"), () -> screen.popup(new StudioPicker(screen, addX, y, 18, picked -> {
            JsonObject reward = StudioEditors.newReward("item");
            reward.addProperty("item", picked.id());
            node.at("rewards").add(reward);
        }).items(false, false)));
    }

    private void addRow() {
        String title = adding.trim();
        if (title.isEmpty()) return;
        adding = "";
        store.transaction(() -> {
            StudioKey made = screen.create(AuthoringKind.TASK, title);
            if (!screen.map.category.isEmpty()) store.edit(made, "", body -> body.addProperty("category", screen.map.category));
            if (!screen.map.disk.isEmpty()) store.putOnDisk(made, new StudioKey(AuthoringKind.DISK, screen.map.disk));
            StudioParse.Amount amount = StudioParse.amount(title);
            if (amount != null) store.edit(made, "", body -> {
                JsonArray goals = new JsonArray();
                goals.add(amount.goal());
                body.add("objectives", goals);
            });
        });
        kit.focus("table:add");
    }

    void paste() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        screen.popup(new StudioPaste(screen, text == null ? "" : text));
    }

    boolean copy() {
        if (selected.isEmpty()) return false;
        StringBuilder result = new StringBuilder();
        for (StudioKey key : rows) if (selected.contains(key)) {
            JsonObject body = store.view(key);
            List<String> goals = new ArrayList<>();
            StudioJson.list(body, "objectives").forEach(element -> {
                JsonObject goal = element.getAsJsonObject();
                StudioEditors.GoalType type = StudioEditors.goal(goal);
                String verb = type == null || type.id().equals("collect") ? "" : type.id() + " ";
                goals.add(verb + StudioJson.integer(goal, "count", 1) + " " + StudioJson.string(goal, "target"));
            });
            List<String> rewards = new ArrayList<>();
            StudioJson.list(body, "rewards").forEach(element -> {
                JsonObject reward = element.getAsJsonObject();
                rewards.add(switch (StudioJson.string(reward, "type")) {
                    case "item" -> StudioJson.integer(reward, "count", 1) + " " + StudioJson.string(reward, "item");
                    case "antcoins" -> StudioJson.integer(reward, "amount", 1) + " antcoins";
                    case "experience" -> StudioJson.integer(reward, "points", 1) + " xp";
                    case "experience_levels" -> StudioJson.integer(reward, "levels", 1) + " levels";
                    default -> StudioSentence.rewardName(store, reward);
                });
            });
            result.append(store.label(key)).append('\t').append(String.join(", ", goals)).append('\t').append(String.join(", ", rewards)).append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(result.toString());
        store.say(t("table.copied", selected.size()), true);
        return true;
    }
}
