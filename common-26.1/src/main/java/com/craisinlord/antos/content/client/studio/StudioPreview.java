package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.craisinlord.antos.content.guide.TaskMarkup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;

final class StudioPreview {
    private final AntStudioScreen screen;
    private final StudioStore store;
    private final StudioKit kit;
    private final Map<StudioKey, Integer> heights = new HashMap<>();
    private final Map<String, Integer> windowHeights = new HashMap<>();
    private int state;
    private int x;
    private int w;
    private int y;

    StudioPreview(AntStudioScreen screen) {
        this.screen = screen;
        store = screen.store;
        kit = screen.kit;
    }

    private String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    void render(StudioKey key, int px, int py, int pw, int ph) {
        kit.fill(px, py, pw, ph, StudioTheme.desk);
        kit.fill(px, py, pw, 22, StudioTheme.window);
        kit.fill(px, py + 21, pw, 1, StudioTheme.line);
        JsonObject body = key == null ? null : store.view(key);
        if (key == null || body == null || store.loaded(key) == null) {
            kit.text(t("preview"), px + 6, py + 7, pw / 2, StudioTheme.strong);
            if (key != null) kit.wrap(t("loading"), px + 8, py + 32, pw - 16, StudioTheme.soft);
            return;
        }
        List<String> states = switch (key.kind()) {
            case TASK -> List.of(t("preview.todo"), t("preview.ready"), t("preview.done"));
            case MESSAGE -> List.of(t("preview.open"), t("preview.inbox"));
            case TERMINAL_COMMAND -> List.of(t("preview.works"), t("preview.locked"));
            default -> List.of();
        };
        int segments = 2;
        for (String value : states) segments += kit.width(value) + 10;
        boolean labelled = states.isEmpty() || segments > pw - 10 || segments < pw - kit.width(t("preview")) - 20;
        int labelWidth = labelled ? kit.text(t("preview"), px + 6, py + 7, pw / 2, StudioTheme.strong) : 0;
        if (!states.isEmpty()) {
            state = Math.min(state, states.size() - 1);
            int size = segments;
            if (size <= pw - labelWidth - 10) kit.segments(labelled ? px + pw - size - 5 : px + 5, py + 3, states, state, index -> state = index);
            else kit.drop(px + labelWidth + 12, py + 3, pw - labelWidth - 17, states.get(state), null, click -> {
                StudioMenu menu = new StudioMenu(screen, click.x(), py + 3, 16);
                for (int index = 0; index < states.size(); index++) {
                    int choice = index;
                    menu.check(states.get(index), index == state, () -> state = choice);
                }
                screen.popup(menu);
            });
        }
        String id = "preview:" + key;
        int offset = kit.scroll(id, px, py + 22, pw, ph - 22, heights.getOrDefault(key, 0));
        kit.clip(px, py + 22, pw, ph - 22);
        int top = py + 22 - offset;
        x = px + 7;
        w = pw - 16;
        y = top + 7;
        int depth = kit.depth();
        try {
            switch (key.kind()) {
                case TASK -> window("TASKS", "task", () -> task(key, body));
                case ARCHIVE_ENTRY -> window("ANTARCHIVE", "article", () -> article(key, body));
                case MESSAGE -> window("ANTMAIL", "mail", () -> mail(body));
                case PRODUCT -> window("ANTAZON", "product", () -> product(body));
                case SELL_RULE -> window("ANTAZON", "sell", () -> sell(body));
                case DISK -> disk(key, body);
                case PROGRAM -> window(StudioText.label(key, body), "program", () -> program(body));
                case TERMINAL_COMMAND -> window("TERMINAL", "terminal", () -> terminal(body));
                case REWARD_POOL -> window(StudioText.label(key, body), "pool", () -> pool(body));
                case DEAL_CAMPAIGN -> window("ANTAZON", "deal", () -> {
                    kit.text(StudioText.read(StudioJson.string(body, "label")), x, y, w, StudioTheme.accent);
                    y += 13;
                });
                default -> window(StudioText.label(key, body), "plain", () -> {
                    StudioIcons.of(store, key).draw(kit, x, y, 24);
                    y += 28;
                    y = kit.wrap(StudioText.label(key, body), x, y, w, StudioTheme.strong);
                });
            }
        } catch (RuntimeException exception) {
            kit.unwind(depth);
            x = px + 7;
            w = pw - 16;
            y = kit.wrap(t("preview.unavailable"), x, y, w, StudioTheme.soft) + 8;
        }
        connections(key);
        heights.put(key, y - top + 8);
        kit.unclip();
    }

    private void window(String title, String id, Runnable content) {
        int top = y;
        int height = windowHeights.getOrDefault(id, 60);
        kit.box(x, top, w, height, StudioTheme.window, StudioTheme.strongLine);
        kit.fill(x + 1, top + 1, w - 2, 14, StudioTheme.panel);
        kit.fill(x + 1, top + 15, w - 2, 1, StudioTheme.line);
        kit.text(title, x + 6, top + 4, w - 12, StudioTheme.strong);
        x += 7;
        w -= 14;
        y = top + 21;
        content.run();
        x -= 7;
        w += 14;
        windowHeights.put(id, y - top + 5);
        y += 12;
    }

    private void region(String section, Runnable content) {
        int top = y;
        content.run();
        int height = Math.max(8, y - top);
        boolean hover = kit.hover(x - 3, top - 2, w + 6, height + 2);
        if (hover || screen.editors.hovered.equals(section)) kit.dashed(x - 3, top - 3, w + 6, height + 3, StudioTheme.accent);
        kit.hit(x - 3, top - 2, w + 6, height + 2, click -> screen.editors.jump(section));
    }

    private void markup(String text, int color) {
        StringBuilder line = new StringBuilder();
        for (TaskMarkup.Token token : TaskMarkup.parse(AntOSPlayerText.apply(text))) {
            if (token instanceof TaskMarkup.Text value) line.append(value.text());
            else if (token instanceof TaskMarkup.Icon icon) line.append('[').append(StudioText.prettyId(icon.id())).append(']');
            else line.append('\n');
        }
        y = kit.wrap(line.toString(), x, y, w, color);
    }

    private void item(StudioIcon icon, String name, String amount, int color) {
        icon.draw(kit, x, y, 14);
        int size = amount.isEmpty() ? 0 : kit.width(amount) + 4;
        kit.text(name, x + 18, y + 3, w - 18 - size, color);
        if (!amount.isEmpty()) kit.right(amount, x + w, y + 3, StudioTheme.muted);
        y += 17;
    }

    private void title(StudioIcon icon, String text) {
        kit.box(x, y, 22, 22, StudioTheme.desk, StudioTheme.strongLine);
        icon.draw(kit, x + 3, y + 3, 16);
        int end = kit.wrap(text, x + 28, y + 7, w - 28, StudioTheme.strong);
        y = Math.max(y + 27, end + 4);
    }

    private void task(StudioKey key, JsonObject body) {
        title(StudioIcons.task(store, body), StudioText.label(key, body));
        String description = StudioText.read(StudioJson.string(body, "description"));
        region("text", () -> {
            if (description.isBlank()) kit.text(t("preview.no_text"), x, y, w, StudioTheme.soft);
            else markup(description, StudioTheme.text);
            if (description.isBlank()) y += 11;
        });
        y += 4;
        for (JsonElement block : StudioJson.list(body, "content")) block(StudioEditors.block(block));
        JsonArray objectives = StudioJson.list(body, "objectives");
        region("needs", () -> {
            kit.text(t("preview.objectives"), x, y, w, StudioTheme.muted);
            y += 12;
            for (JsonElement element : objectives) {
                JsonObject row = element.getAsJsonObject();
                StudioIcon icon = StudioIcons.objective(store, row);
                int count = StudioJson.integer(row, "count", 1);
                StudioEditors.GoalType type = StudioEditors.goal(row);
                String name = StudioText.read(StudioJson.string(row, "description"));
                if (name.isBlank()) name = screen.editors.goalName(row);
                if (name.isBlank()) name = t("something");
                boolean counted = type == null || type.counted() && !type.id().equals("game") && !type.id().equals("floppy");
                item(icon == null ? StudioIcon.item("minecraft:paper") : icon, name, counted ? (state == 0 ? 0 : count) + "/" + count : state == 0 ? "" : t("preview.check"), state == 0 ? StudioTheme.text : StudioTheme.accent);
            }
            if (objectives.isEmpty()) {
                kit.text(t("preview.no_goals"), x, y, w, StudioTheme.soft);
                y += 12;
            }
        });
        y += 5;
        JsonArray rewards = StudioJson.list(body, "rewards");
        region("gets", () -> {
            kit.text(t("preview.rewards"), x, y, w, StudioTheme.muted);
            y += 12;
            rewardRows(rewards);
        });
        if (state > 0 && !rewards.isEmpty()) {
            y += 3;
            kit.box(x, y, w, 16, state == 1 ? StudioTheme.hover : StudioTheme.section, state == 1 ? StudioTheme.accent : StudioTheme.line);
            String label = t(state == 1 ? "preview.claim" : "preview.claimed");
            kit.text(label, x + (w - kit.width(label)) / 2, y + 4, w, state == 1 ? StudioTheme.accent : StudioTheme.soft);
            y += 18;
        }
    }

    private void rewardRows(JsonArray rewards) {
        for (JsonElement element : rewards) {
            JsonObject reward = element.getAsJsonObject();
            String type = StudioJson.string(reward, "type", "item");
            if (type.equals("item")) item(StudioIcons.reward(store, reward), StudioText.itemName(StudioJson.string(reward, "item")), "x" + StudioJson.integer(reward, "count", 1), StudioTheme.text);
            else if (type.equals("antcoins")) item(StudioIcon.coin(), AntOSPlayerText.currencyName(), Long.toString((long) StudioJson.number(reward, "amount", 0)), StudioTheme.text);
            else item(StudioIcons.reward(store, reward), StudioSentence.rewardName(store, reward), "", StudioTheme.text);
        }
        if (rewards.isEmpty()) {
            kit.text(t("preview.no_rewards"), x, y, w, StudioTheme.soft);
            y += 12;
        }
    }

    private void article(StudioKey key, JsonObject body) {
        title(StudioIcons.of(store, key), StudioText.label(key, body));
        String subtitle = StudioText.read(StudioJson.string(body, "subtitle"));
        if (!subtitle.isBlank()) y = kit.wrap(subtitle, x, y - 2, w, StudioTheme.muted) + 3;
        String cover = StudioJson.string(body, "cover_entity");
        if (!cover.isBlank() && StudioJson.string(body, "cover_item").isBlank()) {
            if (kit.visible(y, 56)) kit.assets.asset(kit.g(), "", cover, "", "", x + w / 2, y + 28, 54, (float) StudioJson.number(body, "rotation", 0), (float) StudioJson.number(body, "render_scale", 1), false, 1.0F);
            y += 60;
        }
        JsonArray blocks = body.has("content") ? StudioJson.list(body, "content") : StudioJson.list(body, "description");
        region("page", () -> {
            for (JsonElement element : blocks) block(StudioEditors.block(element));
            if (blocks.isEmpty()) {
                kit.text(t("preview.no_text"), x, y, w, StudioTheme.soft);
                y += 12;
            }
        });
        JsonArray gallery = StudioJson.list(body, "gallery");
        if (!gallery.isEmpty()) {
            String heading = StudioText.read(StudioJson.string(body, "gallery_title"));
            if (!heading.isBlank()) {
                kit.text(heading, x, y + 2, w, StudioTheme.muted);
                y += 14;
            }
            int cx = x;
            for (JsonElement element : gallery) {
                if (cx + 20 > x + w) {
                    cx = x;
                    y += 22;
                }
                JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
                String entity = StudioJson.string(object, "entity");
                String item = element.isJsonPrimitive() ? element.getAsString() : StudioJson.string(object, "item", StudioJson.string(object, "id"));
                kit.slot(cx, y, 20, entity.isEmpty() ? StudioIcon.item(item) : StudioIcon.entity(entity), false, StudioJson.string(object, "name"), null);
                cx += 22;
            }
            y += 24;
        }
    }

    private void block(StudioEditors.Block block) {
        switch (block.type()) {
            case "item" -> {
                if (block.value().isEmpty()) placeholder();
                else {
                    if (kit.visible(y, 36)) {
                        if (StudioIcon.stack(block.value()).isEmpty()) StudioIcon.unknown(kit, x + w / 2 - 16, y, 32);
                        else StudioIcon.item(block.value()).draw(kit, x + w / 2 - 16, y, 32);
                    }
                    y += 36;
                }
            }
            case "entity" -> {
                if (block.value().isEmpty()) placeholder();
                else {
                    if (kit.visible(y, 60)) kit.assets.asset(kit.g(), "", block.value(), "", "", x + w / 2, y + 30, 58, 0, .9F, false, 1.0F);
                    y += 64;
                }
            }
            case "recipe" -> {
                if (block.value().isEmpty()) placeholder();
                else {
                    if (kit.visible(y, 50) && !kit.assets.recipe(kit.g(), block.value(), x + Math.max(0, (w - 88) / 2), y, Math.min(w, 88)))
                        kit.wrap(t("preview.no_recipe", block.value()), x, y + 18, w, StudioTheme.soft);
                    y += 52;
                }
            }
            case "display" -> {
                if (block.value().isEmpty()) placeholder();
                else {
                    int end = kit.assets.display(kit.g(), block.value(), x, y, w, 24, kit.height - 15);
                    y = Math.max(y + 14, end) + 3;
                }
            }
            case "beside" -> { }
            case "enchantment" -> {
                kit.assets.asset(kit.g(), "", "", block.value(), "", x + w / 2, y + 16, 32, 0, 1, false, 1.0F);
                y += 36;
            }
            default -> {
                if (!block.value().isBlank()) {
                    markup(StudioText.read(block.value()), StudioTheme.text);
                    y += 4;
                }
            }
        }
    }

    private void placeholder() {
        kit.dashed(x, y, w, 30, StudioTheme.strongLine);
        String text = t("preview.placeholder");
        kit.text(text, x + Math.max(2, (w - kit.width(text)) / 2), y + 11, w - 4, StudioTheme.soft);
        y += 34;
    }

    private void mail(JsonObject body) {
        String sender = StudioJson.string(body, "sender_display", StudioJson.string(body, "sender"));
        region("from", () -> {
            if (sender.isBlank()) {
                kit.dashed(x, y, w, 15, StudioTheme.error);
                kit.text(t("preview.no_sender"), x + 4, y + 4, w - 8, StudioTheme.error);
                y += 18;
            } else {
                StudioIcons.of(store, AuthoringKind.MESSAGE, body).draw(kit, x, y, 12);
                kit.text(sender, x + 16, y + 2, w - 16, StudioTheme.accent);
                y += 16;
            }
        });
        if (state == 1) {
            kit.box(x - 3, y - 2, w + 6, 28, StudioTheme.selected, StudioTheme.line);
            kit.text(StudioJson.string(body, "subject"), x, y + 2, w, StudioTheme.strong);
            kit.text(AntOSPlayerText.apply(StudioJson.string(body, "body")).replace('\n', ' '), x, y + 13, w, StudioTheme.muted);
            y += 30;
            return;
        }
        y = kit.wrap(StudioJson.string(body, "subject"), x, y, w, StudioTheme.strong) + 4;
        region("message", () -> y = kit.wrap(AntOSPlayerText.apply(StudioJson.string(body, "body")), x, y, w, StudioTheme.text));
        JsonArray items = StudioJson.list(StudioJson.object(body, "delivery"), "items");
        long coins = (long) StudioJson.number(body, "antcoins", 0);
        if (items.isEmpty() && coins <= 0) return;
        y += 4;
        region("attached", () -> {
            kit.fill(x, y, w, 1, StudioTheme.line);
            y += 5;
            for (JsonElement element : items) {
                JsonObject object = element.getAsJsonObject();
                var decoded = StudioItems.decode(object);
                item(object.has("explorer_map") ? StudioIcon.item("minecraft:filled_map") : decoded.isEmpty() ? StudioIcon.item(StudioJson.string(object, "id")) : StudioIcon.stack(decoded),
                        object.has("explorer_map") ? t("a_map") : decoded.isEmpty() ? StudioText.itemName(StudioJson.string(object, "id")) : decoded.getHoverName().getString(),
                        "x" + StudioJson.integer(object, "count", 1), StudioTheme.text);
            }
            if (coins > 0) item(StudioIcon.coin(), AntOSPlayerText.currencyName(), Long.toString(coins), StudioTheme.text);
        });
    }

    private void product(JsonObject body) {
        kit.box(x, y, w, 46, StudioTheme.desk, StudioTheme.line);
        StudioIcons.product(body).draw(kit, x + w / 2 - 16, y + 7, 32);
        y += 51;
        y = kit.wrap(StudioText.read(StudioJson.string(body, "name")), x, y, w, StudioTheme.strong) + 2;
        region("price", () -> {
            for (JsonElement element : StudioJson.list(body, "payments")) {
                kit.text(StudioSentence.price(element.getAsJsonObject()), x, y, w, StudioTheme.accent);
                y += 12;
            }
        });
        region("text", () -> {
            String description = StudioText.read(StudioJson.string(body, "description"));
            if (!description.isBlank()) y = kit.wrap(description, x, y + 2, w, StudioTheme.text) + 2;
        });
        region("gets", () -> {
            for (JsonElement element : StudioJson.list(body, "rewards")) {
                JsonObject reward = element.getAsJsonObject();
                item(StudioIcon.item(StudioJson.string(reward, "item")), StudioText.itemName(StudioJson.string(reward, "item")), "x" + StudioJson.integer(reward, "count", 1), StudioTheme.text);
            }
        });
    }

    private void sell(JsonObject body) {
        List<String> items = new ArrayList<>(StudioJson.strings(body, "items"));
        if (!StudioJson.string(body, "item").isEmpty()) items.add(StudioJson.string(body, "item"));
        String value = Long.toString((long) StudioJson.number(body, "value", 1));
        for (String id : items) item(id.startsWith("#") ? StudioIcon.item("minecraft:bundle") : StudioIcon.item(id), id.startsWith("#") ? StudioText.tag(id) : StudioText.itemName(id), value, StudioTheme.text);
        for (String id : StudioJson.strings(body, "tags")) item(StudioIcon.item("minecraft:bundle"), StudioText.tag(id), value, StudioTheme.text);
    }

    private void pool(JsonObject body) {
        JsonArray entries = StudioJson.list(body, "entries");
        double total = 0;
        for (JsonElement element : entries) total += Math.max(1, StudioJson.number(element.getAsJsonObject(), "weight", 1));
        for (JsonElement element : entries) {
            JsonObject row = element.getAsJsonObject();
            String pool = StudioJson.string(row, "pool");
            item(pool.isEmpty() ? StudioIcon.item(StudioJson.string(row, "item")) : StudioIcon.item("minecraft:bundle"),
                    pool.isEmpty() ? StudioText.itemName(StudioJson.string(row, "item")) : store.label(AuthoringKind.REWARD_POOL, pool),
                    Math.round(100 * Math.max(1, StudioJson.number(row, "weight", 1)) / Math.max(1, total)) + "%", StudioTheme.text);
        }
    }

    private void disk(StudioKey key, JsonObject body) {
        StudioIcons.disk(store, body).draw(kit, x + w / 2 - 28, y + 4, 56);
        y += 66;
        int top = y;
        int height = windowHeights.getOrDefault("disk", 70);
        kit.box(x, top, w, height, StudioTheme.window, StudioTheme.strongLine);
        x += 7;
        w -= 14;
        y = top + 7;
        kit.text(StudioText.label(key, body), x, y, w, StudioTheme.strong);
        y += 12;
        String category = StudioJson.string(body, "category", "antos:general");
        StudioKey categoryKey = new StudioKey(AuthoringKind.DISK_CATEGORY, category.contains(":") ? category : key.namespace() + ":" + category);
        JsonObject found = store.view(categoryKey);
        String color = StudioJson.string(found, "color", "AAAAAA").replaceFirst("^#", "");
        kit.text(store.exists(categoryKey) ? store.label(categoryKey) : StudioText.prettyId(category), x, y, w, StudioTheme.hex(color, StudioTheme.muted));
        y += 15;
        kit.text(t("field.description"), x, y, w, StudioTheme.muted);
        y += 11;
        StudioNode node = new StudioNode(store, key);
        String raw = StudioJson.string(body, "description");
        kit.area("preview" + node.id("description"), x, y, w, 44, StudioText.read(raw), 4096, t("optional"), false, value -> node.put("description", StudioText.write(AuthoringKind.DISK, "description", raw, value)));
        y += 47;
        x -= 7;
        w += 14;
        windowHeights.put("disk", y - top + 4);
        y += 10;
        kit.text(t("try_it"), x, y, w, StudioTheme.muted);
        y += 12;
        boolean saved = store.row(key) != null;
        kit.button(x, y, w, 16, t("give_floppy"), StudioKit.NORMAL, saved && !store.busy(), t(saved ? "give_floppy.tip" : "apply_first"), () -> screen.tool("give_disk", key, null, t("gave_floppy", StudioText.label(key, body))));
        y += 18;
        kit.button(x, y, w, 16, t("reset_progress"), StudioKit.NORMAL, saved && !store.busy(), t(saved ? "reset_progress.tip" : "apply_first"), () -> screen.confirm(t("reset_progress.ask", StudioText.label(key, body)), t("reset_progress.yes"),
                () -> screen.tool("reset_disk", key, null, t("reset_done", StudioText.label(key, body)))));
        y += 22;
        kit.text(t("look"), x, y, w, StudioTheme.muted);
        y += 12;
        int cx = x;
        List<StudioKey> looks = store.keys(AuthoringKind.DISK_CATEGORY);
        for (int index = 0; index < looks.size(); index++) {
            StudioKey look = looks.get(index);
            if (cx + 22 > x + w) {
                cx = x;
                y += 24;
            }
            kit.slot(cx, y, 22, StudioIcons.of(store, look), look.equals(categoryKey), store.label(look), click -> node.put("category", look.id()));
            cx += 24;
        }
        if (cx + 22 > x + w) {
            cx = x;
            y += 24;
        }
        kit.button(cx, y, 22, 22, "+", StudioKit.NORMAL, true, t("new_look"), () -> screen.make(AuthoringKind.DISK_CATEGORY, t("untitled", StudioText.kind(AuthoringKind.DISK_CATEGORY)), false));
        y += 28;
    }

    private void program(JsonObject body) {
        if (StudioJson.string(body, "type").equals("adventure")) {
            JsonObject nodes = StudioJson.object(body, "nodes");
            JsonObject node = StudioJson.object(nodes, StudioJson.string(body, "start"));
            y = kit.wrap(StudioJson.string(node, "text"), x, y, w, StudioTheme.text) + 4;
            for (JsonElement choice : StudioJson.list(node, "choices")) {
                kit.box(x, y, w, 15, StudioTheme.button, StudioTheme.line);
                kit.text("> " + StudioJson.string(choice.getAsJsonObject(), "text"), x + 4, y + 4, w - 8, StudioTheme.text);
                y += 17;
            }
            return;
        }
        JsonArray questions = StudioJson.list(body, "questions");
        if (questions.isEmpty()) {
            kit.text(t("preview.no_questions"), x, y, w, StudioTheme.soft);
            y += 12;
            return;
        }
        region("questions", () -> {
            JsonObject question = questions.get(0).getAsJsonObject();
            kit.text(t("question_number", 1), x, y, w, StudioTheme.muted);
            y += 12;
            y = kit.wrap(StudioJson.string(question, "question"), x, y, w, StudioTheme.strong) + 4;
            int correct = StudioJson.integer(question, "correct", 0);
            JsonArray answers = StudioJson.list(question, "answers");
            for (int index = 0; index < answers.size(); index++) {
                kit.box(x, y, w, 15, StudioTheme.button, index == correct ? StudioTheme.accent : StudioTheme.line);
                kit.text(answers.get(index).getAsString(), x + 4, y + 4, w - 8, index == correct ? StudioTheme.accent : StudioTheme.text);
                y += 17;
            }
        });
    }

    private void terminal(JsonObject body) {
        kit.text("> " + StudioJson.string(body, "command"), x, y, w, StudioTheme.strong);
        y += 13;
        region("output", () -> {
            JsonArray lines = StudioJson.list(body, state == 1 ? "locked_output" : "output");
            for (JsonElement element : lines) {
                String text = element.isJsonPrimitive() ? element.getAsString() : StudioJson.string(element.getAsJsonObject(), "text");
                String style = element.isJsonObject() ? StudioJson.string(element.getAsJsonObject(), "style", StudioJson.string(body, "style", "output")) : StudioJson.string(body, "style", "output");
                y = kit.wrap(AntOSPlayerText.apply(text), x, y, w, switch (style) {
                    case "highlight" -> StudioTheme.accent;
                    case "system" -> StudioTheme.muted;
                    case "error" -> StudioTheme.error;
                    default -> StudioTheme.text;
                });
            }
            if (lines.isEmpty()) {
                kit.text(t("preview.no_text"), x, y, w, StudioTheme.soft);
                y += 12;
            }
        });
    }

    private void connections(StudioKey key) {
        List<StudioKey> before = new ArrayList<>();
        JsonObject body = store.view(key);
        if (key.kind() != AuthoringKind.DISK) for (StudioKey disk : store.disksOf(key)) if (store.direct(key, disk)) before.add(disk);
        if (key.kind() == AuthoringKind.TASK) for (String field : List.of("requires", "requires_any")) for (String id : StudioJson.strings(body, field)) before.add(new StudioKey(AuthoringKind.TASK, id));
        if (key.kind() == AuthoringKind.MESSAGE) {
            JsonObject trigger = StudioJson.object(body, "trigger");
            if (StudioJson.string(trigger, "type").equals("task_complete") && !StudioJson.string(trigger, "value").isEmpty()) before.add(new StudioKey(AuthoringKind.TASK, StudioJson.string(trigger, "value")));
        }
        List<StudioKey> after = key.kind() == AuthoringKind.DISK || Set.of(AuthoringKind.CATEGORY, AuthoringKind.GROUP, AuthoringKind.DISK_CATEGORY, AuthoringKind.ARCHIVE_FOLDER).contains(key.kind()) ? List.of() : screen.users(key);
        after = after.stream().filter(other -> !before.contains(other)).toList();
        if (before.isEmpty() && after.isEmpty()) return;
        kit.text(t("connected"), x, y, w, StudioTheme.muted);
        y += 12;
        for (StudioKey other : before) {
            int size = Math.min(w - 34, kit.chipWidth(store.label(other), true, false));
            kit.chip(x, y, size, store.label(other), StudioIcons.of(store, other), false, other.id(), () -> screen.open(other));
            kit.text("> " + t("this"), x + size + 4, y + 4, 34, StudioTheme.soft);
            y += 18;
        }
        for (StudioKey other : after) {
            kit.text(">", x, y + 4, 8, StudioTheme.soft);
            kit.chip(x + 9, y, w - 9, store.label(other), StudioIcons.of(store, other), false, other.id(), () -> screen.open(other));
            y += 18;
        }
    }
}
