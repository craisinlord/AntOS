package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class StudioEditors extends StudioForm {
    record GoalType(String id, String type, String stat, String reference, boolean counted) { }

    static final List<GoalType> GOALS = List.of(
            new GoalType("collect", "item", "", "item", true),
            new GoalType("craft", "stat", "item_crafted", "item", true),
            new GoalType("mine", "stat", "block_mined", "item", true),
            new GoalType("kill", "stat", "entity_killed", "entity", true),
            new GoalType("use", "stat", "item_used", "item", true),
            new GoalType("pick_up", "stat", "item_picked_up", "item", true),
            new GoalType("drop", "stat", "item_dropped", "item", true),
            new GoalType("break", "stat", "item_broken", "item", true),
            new GoalType("die_to", "stat", "entity_killed_by", "entity", true),
            new GoalType("statistic", "stat", "custom", "stat", true),
            new GoalType("advancement", "advancement", "", "advancement", false),
            new GoalType("dimension", "dimension_entered", "", "dimension", false),
            new GoalType("biome", "biome_visited", "", "biome", false),
            new GoalType("structure", "structure_entered", "", "structure", false),
            new GoalType("floppy", "disk_installed", "", "kind:disk", true),
            new GoalType("read_mail", "mail_read", "", "kind:message", false),
            new GoalType("read_article", "archive_viewed", "", "kind:archive_entry", false),
            new GoalType("command", "terminal_command", "", "kind:terminal_command", false),
            new GoalType("finish_program", "program_ending", "", "kind:program", false),
            new GoalType("game", "game_record", "", "game", true));
    static final List<String> REWARDS = List.of("item", "antcoins", "experience", "experience_levels", "item_pool", "choice", "archive", "advancement", "effect", "mail");
    private static final Set<String> QUIET_REWARDS = Set.of("archive", "choice");
    private static final Set<String> OPTION_REWARDS = Set.of("item", "antcoins", "item_pool", "experience", "experience_levels", "advancement", "effect", "mail");

    StudioEditors(AntStudioScreen screen) { super(screen); }

    void render(StudioKey shown, int fx, int fy, int fw, int fh) {
        render(shown, fx, fy, fw, fh, () -> {
            switch (shown.kind()) {
                case TASK -> task();
                case CATEGORY -> category(true);
                case GROUP -> category(false);
                case REWARD_POOL -> pool();
                case DISK -> disk();
                case DISK_CATEGORY -> diskCategory();
                case PROGRAM -> program();
                case MESSAGE -> message();
                case MAIL_POOL -> mailPool();
                case PRODUCT -> product();
                case SELL_RULE -> sellRule();
                case DEAL_CAMPAIGN -> deal();
                case ARCHIVE_ENTRY -> article();
                case ARCHIVE_FOLDER -> folder();
                case TERMINAL_COMMAND -> command();
            }
        });
    }

    private String t(String key, Object... arguments) { return StudioText.t(key, arguments); }
    private StudioKey key(AuthoringKind kind, String id) { return new StudioKey(kind, id); }

    static GoalType goal(JsonObject row) {
        String type = StudioJson.string(row, "type", "item");
        if (type.equals("item_tag")) type = "item";
        String stat = StudioJson.string(row, "stat_type");
        for (GoalType goal : GOALS) if (goal.type().equals(type) && (!type.equals("stat") || goal.stat().equals(stat))) return goal;
        return null;
    }

    private void whoTask() {
        section("who", t("section.who_task"), "", List.of("who", "requires", "requires_any", "availability"), () -> {
            List<String> all = root.strings("requires");
            List<String> any = root.strings("requires_any");
            boolean chained = !all.isEmpty() || !any.isEmpty();
            boolean everyone = !chained && root.string("availability", "disk").equals("automatic");
            List<StudioKey> disks = store.keys(AuthoringKind.DISK).stream().filter(disk -> store.direct(key, disk)).toList();
            Flow flow = flow();
            flow.chip(t("who.everyone"), null, everyone, t("who.everyone.tip"), click -> store.with(disks, () -> store.transaction(() -> {
                root.change("", target -> {
                    JsonObject body = target.getAsJsonObject();
                    body.addProperty("availability", "automatic");
                    body.remove("requires");
                    body.remove("requires_any");
                });
                disks.forEach(disk -> store.takeOffDisk(key, disk));
            })));
            for (StudioKey disk : disks) flow.chip(store.label(disk), StudioIcons.of(store, disk), true, t("who.floppy.tip"), click -> linkMenu(click, disk, () -> store.takeOffDisk(key, disk)));
            for (String id : all) flow.chip(t("who.after", store.label(AuthoringKind.TASK, id)), StudioIcons.of(store, key(AuthoringKind.TASK, id)), true, id,
                    click -> linkMenu(click, key(AuthoringKind.TASK, id), () -> root.change("", target -> StudioJson.remove(target.getAsJsonObject(), "requires", id))));
            for (String id : any) flow.chip(t("who.after_any", store.label(AuthoringKind.TASK, id)), StudioIcons.of(store, key(AuthoringKind.TASK, id)), true, id,
                    click -> linkMenu(click, key(AuthoringKind.TASK, id), () -> root.change("", target -> StudioJson.remove(target.getAsJsonObject(), "requires_any", id))));
            flow.chip("+ " + t("who.floppy"), null, false, t("who.floppy.tip"), click -> pickRef("kind:disk", click.x(), click.y(), false, value -> store.putOnDisk(key, key(AuthoringKind.DISK, value))));
            flow.chip("+ " + t("who.after_task"), null, false, t("who.after_task.tip"), click -> pickRef("kind:task", click.x(), click.y(), false, value -> {
                if (!value.equals(key.id())) root.change("", target -> StudioJson.add(target.getAsJsonObject(), "requires", value));
            }));
            flow.chip(t("more"), null, false, "", click -> screen.popup(new StudioMenu(screen, click.x(), click.y(), 4)
                    .item(t("who.any_task"), () -> pickRef("kind:task", click.x(), click.y(), false, value -> {
                        if (!value.equals(key.id())) root.change("", target -> StudioJson.add(target.getAsJsonObject(), "requires_any", value));
                    }))
                    .separator()
                    .check(t("field.hide_until_dependencies_complete"), root.bool("hide_until_dependencies_complete", false),
                            () -> root.put("hide_until_dependencies_complete", !root.bool("hide_until_dependencies_complete", false)))
                    .check(t("field.invisible_until_completed"), root.bool("invisible_until_completed", false),
                            () -> root.put("invisible_until_completed", !root.bool("invisible_until_completed", false)))));
            flow.end();
            if (chained && any.size() > 1 && all.isEmpty()) y = kit.wrap(t("who.any_note"), x, y, w, StudioTheme.soft) + 2;
            if (advanced) {
                ref(root, "category", "kind:category", true);
                pair(() -> number(root, "sort_order", 0, -100000, 100000, true), () -> {
                    if (!root.string("program").isEmpty() || advanced) optionalText(root, "program", t("picker.none"), 128);
                });
                checks().add(root, "hide_until_dependencies_complete", false).add(root, "invisible_until_completed", false).end();
            }
        });
    }

    private void linkMenu(StudioKit.Click click, StudioKey target, Runnable remove) {
        StudioMenu menu = new StudioMenu(screen, click.x(), click.y(), 4);
        if (store.exists(target)) menu.item(t("open_named", store.label(target)), () -> screen.open(target));
        menu.danger(t("remove"), true, remove);
        screen.popup(menu);
    }

    private void task() {
        if (root.string("type").equals("link") || root.has("link_category") || root.has("link_group")) {
            taskLink();
            return;
        }
        whoTask();
        section("needs", t("section.needs"), "", List.of("objectives"), this::objectives);
        section("gets", t("section.gets"), "", List.of("rewards"), () -> rewards(root.at("rewards"), REWARDS, 0));
        section("text", t("section.text"), t("optional"), List.of("description", "content"), () -> {
            area(root, "description", t("hint.task_text"), 32768, 32);
            if (advanced || root.has("content")) blocks("content", false);
        });
        if (advanced) {
            section("look", t("section.look"), "", List.of("size", "icon_scale", "line_bend", "shape"), () -> {
                choice(root, "shape", "square", List.of("square", "diamond", "hexagon", "circle", "pentagon", "octagon", "triangle"));
                pair(() -> number(root, "size", 1, .5, 3, false), () -> number(root, "icon_scale", 1, .25, 2, false));
                pair(() -> choice(root, "line_axis", "auto", List.of("auto", "vertical", "horizontal")), () -> number(root, "line_bend", .5, 0, 1, false));
                StudioNode position = root.at("position");
                pair(() -> number(position, "x", 0, -128, 128, true), () -> number(position, "y", 0, -128, 128, true));
                checks().add(root, "green_tint", true).add(root, "render_mob_from_spawn_egg", true).end();
            });
            section("links", t("section.links"), "", List.of("connects", "archive_entries"), () -> {
                refList(root, "connects", "kind:task");
                refList(root, "archive_entries", "kind:archive_entry");
            });
            leftovers(Set.of("title", "description", "category", "availability", "position", "objectives", "rewards", "program", "sort_order", "requires", "requires_any",
                    "connects", "archive_entries", "hide_until_dependencies_complete", "invisible_until_completed", "green_tint", "icon", "icon_entity",
                    "render_mob_from_spawn_egg", "shape", "size", "icon_scale", "line_axis", "line_bend", "content"));
        }
    }

    private void taskLink() {
        section("text", t("section.link"), "", List.of("link_category", "link_group"), () -> {
            ref(root, "link_category", "kind:category", true);
            ref(root, "link_group", "kind:group", true);
            area(root, "description", t("optional"), 4096, 28);
        });
        section("who", t("section.where"), "", List.of("requires", "connects", "category"), () -> {
            ref(root, "category", "kind:category", true);
            refList(root, "requires", "kind:task");
            refList(root, "connects", "kind:task");
            StudioNode position = root.at("position");
            pair(() -> number(position, "x", 0, -128, 128, true), () -> number(position, "y", 0, -128, 128, true));
            number(root, "sort_order", 0, -100000, 100000, true);
        });
        if (advanced) leftovers(Set.of("title", "description", "type", "link_category", "link_group", "requires", "connects", "category", "position", "sort_order", "icon", "icon_entity"));
    }

    static JsonObject newGoal(GoalType type) {
        JsonObject row = new JsonObject();
        row.addProperty("id", StudioProblems.goalId());
        row.addProperty("type", type.type());
        if (!type.stat().isEmpty()) row.addProperty("stat_type", type.stat());
        if (type.id().equals("game")) row.addProperty("stat_type", "wins");
        row.addProperty("target", "");
        row.addProperty("count", 1);
        return row;
    }

    private void objectives() {
        StudioNode list = root.at("objectives");
        JsonArray values = list.array();
        for (int index = 0; index < values.size(); index++) {
            if (!values.get(index).isJsonObject()) continue;
            objective(list, index, values.size());
        }
        add(t("goal"), at -> screen.popup(goalMenu(at[0], at[1], 14, type -> list.add(newGoal(type)))));
    }

    private StudioMenu goalMenu(int mx, int my, int mh, Consumer<GoalType> action) {
        StudioMenu menu = new StudioMenu(screen, mx, my, mh);
        for (GoalType type : GOALS) {
            if (type.id().equals("advancement") || type.id().equals("floppy") || type.id().equals("game")) menu.separator();
            if (!advanced && Set.of("pick_up", "drop", "break", "die_to", "statistic", "use").contains(type.id())) continue;
            menu.item(t("goal." + type.id()), () -> action.accept(type));
        }
        for (String custom : store.refs.list("objective_type")) menu.item(StudioText.prettyId(custom), () -> action.accept(new GoalType(custom, custom, "", "", true)));
        return menu;
    }

    private void objective(StudioNode list, int index, int size) {
        StudioNode row = list.at(index);
        JsonObject body = row.object();
        GoalType type = goal(body);
        int top = y;
        int height = 20 + (advanced ? 34 : 0);
        String title = type == null ? StudioText.prettyId(row.string("type")) : t("goal." + type.id());
        grip(list, index, top, height, title);
        int typeWidth = Math.min(78, Math.max(52, kit.width(title) + 20));
        kit.drop(x + 2, y, typeWidth, title, null, click -> {
            if (click.button() == 1) { rowMenu(list, index, size, click); return; }
            screen.popup(goalMenu(x + 2, top, 16, next -> row.change("", target -> {
                JsonObject object = target.getAsJsonObject();
                GoalType previous = goal(object);
                object.addProperty("type", next.type());
                object.remove("stat_type");
                if (!next.stat().isEmpty()) object.addProperty("stat_type", next.stat());
                if (next.id().equals("game")) object.addProperty("stat_type", "wins");
                if (previous == null || !previous.reference().equals(next.reference())) object.addProperty("target", "");
                if (!next.type().equals("item")) object.remove("consume");
                object.remove("tag_mode");
                object.remove("target_category");
                object.remove("ending");
            })));
        });
        int cx = x + typeWidth + 6;
        int right = x + w - 14;
        removeButton(right, y, () -> list.removeAt(index));
        boolean counted = type == null || type.counted() && (!type.id().equals("floppy") || !row.string("target_category").isEmpty());
        if (counted) {
            right -= 36;
            numberField(row, "count", right, y, 34, 1, 1, 1_000_000, true);
            if (type != null && type.id().equals("game")) {
                String metric = row.string("stat_type", "wins");
                int metricWidth = Math.min(62, kit.width(choiceLabel("stat_type", metric)) + 18);
                right -= metricWidth + 3;
                int dropX = right;
                kit.drop(dropX, y, metricWidth, choiceLabel("stat_type", metric), null, click -> {
                    StudioMenu menu = new StudioMenu(screen, dropX, top, 16);
                    for (String value : List.of("wins", "time", "moves", "score")) menu.check(choiceLabel("stat_type", value), value.equals(metric), () -> row.put("stat_type", value));
                    screen.popup(menu);
                });
            } else {
                right -= 8;
                kit.text("x", right, y + 5, 8, StudioTheme.soft);
            }
        }
        target(row, type, cx, right - 4 - cx);
        y = top + 20;
        if (advanced) {
            int kept = x;
            int keptW = w;
            x += 8;
            w -= 8;
            Checks checks = checks();
            if (type != null && type.id().equals("collect")) {
                checks.add(row, "consume", false);
                if (row.string("type").equals("item_tag")) checks.add(t("field.tag_all"), t("hint.tag_all"), row.string("tag_mode", "any").equals("all"), value -> row.put("tag_mode", value ? "all" : "any"));
            }
            checks.add(row, "optional", false).add(row, "sticky", true).add(row, "notify_player", true).end();
            row(t("field.goal_text"), t("hint.goal_text"), (fx, fw) -> textField(row, "description", fx, y, fw, t("automatic", goalName(body)), 512));
            if (type != null && type.id().equals("floppy")) row(t("field.target_category"), t("hint.target_category"), (fx, fw) -> {
                String current = row.string("target_category");
                kit.drop(fx, y, fw, current.isEmpty() ? t("picker.none") : refLabel("kind:disk_category", current), refIcon("kind:disk_category", current), click ->
                        pickRef("kind:disk_category", fx, click.y(), true, value -> row.change("", target -> {
                            JsonObject object = target.getAsJsonObject();
                            if (value.isEmpty()) object.remove("target_category");
                            else {
                                object.addProperty("target_category", value);
                                object.remove("target");
                            }
                        })));
            });
            if (type != null && type.id().equals("finish_program")) optionalText(row, "ending", t("any_ending"), 64);
            row(t("field.goal_id"), t("hint.goal_id"), (fx, fw) -> kit.field(row.id("id"), fx, y, fw, row.string("id"), new StudioKit.Field().max(128).bad(bad(row.field("id"))), value -> row.put("id", value)));
            x = kept;
            w = keptW;
            y += 2;
        }
    }

    private void target(StudioNode row, GoalType type, int cx, int cw) {
        String target = row.string("target");
        boolean wrong = bad(row.field("target"));
        if (type == null) {
            kit.field(row.id("target"), cx, y, cw, target, new StudioKit.Field().hint(t("target")).bad(wrong), value -> row.put("target", value));
            return;
        }
        if (type.reference().equals("item") || type.reference().equals("entity")) {
            boolean tag = row.string("type").equals("item_tag");
            boolean entity = type.reference().equals("entity");
            StudioIcon icon = target.isEmpty() ? null : entity ? StudioIcon.entity(target) : tag ? StudioIcon.item("minecraft:bundle") : StudioIcon.item(target);
            int slotY = y - 1;
            StudioKit.Action pick = click -> {
                StudioPicker picker = new StudioPicker(screen, cx, slotY, 18, picked -> row.change("", element -> {
                    JsonObject object = element.getAsJsonObject();
                    object.addProperty("target", picked.id());
                    if (type.id().equals("collect")) {
                        object.addProperty("type", picked.type() == StudioPicker.Type.TAG ? "item_tag" : "item");
                        if (picked.type() != StudioPicker.Type.TAG) object.remove("tag_mode");
                    }
                }));
                if (entity) picker.entities();
                else picker.items(type.id().equals("collect"), false);
                screen.popup(picker);
            };
            kit.slot(cx, slotY, 18, icon, wrong, target, pick);
            if (wrong) kit.outline(cx, slotY, 18, 18, StudioTheme.error);
            String name = target.isEmpty() ? t(entity ? "pick_mob" : "pick_item") : entity ? StudioText.entityName(target) : tag ? StudioText.tag(target) : StudioText.itemName(target);
            kit.text(name, cx + 23, y + 4, cw - 24, target.isEmpty() ? StudioTheme.soft : StudioTheme.strong);
            return;
        }
        if (type.id().equals("floppy") && !row.string("target_category").isEmpty()) {
            kit.text(t("any_floppy_of", refLabel("kind:disk_category", row.string("target_category"))), cx, y + 4, cw, StudioTheme.text);
            return;
        }
        refField(row, "target", type.reference(), false, cx, y, cw);
        if (Set.of("biome", "structure").contains(type.reference()) && target.startsWith("#") && kit.hover(cx, y, cw, 16)) kit.tooltip(target);
    }

    String goalName(JsonObject row) {
        GoalType type = goal(row);
        String target = StudioJson.string(row, "target");
        if (type == null) return StudioText.prettyId(target);
        return switch (type.reference()) {
            case "item" -> StudioJson.string(row, "type").equals("item_tag") ? StudioText.tag(target) : StudioText.itemName(target);
            case "entity" -> StudioText.entityName(target);
            default -> target.isEmpty() ? "" : refLabel(type.reference(), target);
        };
    }

    static JsonObject newReward(String type) {
        JsonObject reward = new JsonObject();
        reward.addProperty("type", type);
        com.craisinlord.antos.content.editor.AuthoringTemplates.selectType(reward, "rewards");
        switch (type) {
            case "item" -> reward.addProperty("item", "");
            case "antcoins" -> reward.addProperty("amount", 10);
            case "experience" -> reward.addProperty("points", 50);
            case "mail" -> {
                reward.addProperty("sender", "fieldoffice");
                reward.addProperty("subject", "");
                reward.addProperty("body", "");
            }
            case "choice" -> {
                JsonArray options = new JsonArray();
                for (int number = 1; number <= 2; number++) {
                    JsonObject option = new JsonObject();
                    option.addProperty("id", "option_" + java.util.UUID.randomUUID().toString().substring(0, 6));
                    option.addProperty("title", StudioText.t("option_number", number));
                    JsonArray inner = new JsonArray();
                    inner.add(newReward("item"));
                    option.add("rewards", inner);
                    options.add(option);
                }
                reward.add("options", options);
            }
            default -> { }
        }
        return reward;
    }

    private void rewards(StudioNode list, List<String> types, int depth) {
        JsonArray values = list.array();
        for (int index = 0; index < values.size(); index++) {
            if (!values.get(index).isJsonObject()) continue;
            reward(list, index, values.size(), types, depth);
        }
        add(t("reward"), at -> {
            StudioMenu menu = new StudioMenu(screen, at[0], at[1], 14);
            for (String type : types) {
                if (type.equals("item")) menu.item(t("reward.item"), () -> screen.popup(new StudioPicker(screen, at[0], at[1], 14, picked -> {
                    JsonObject reward = newReward("item");
                    reward.addProperty("item", picked.id());
                    list.add(reward);
                }).items(false, false)));
                else menu.item(rewardTitle(type), StudioIcons.reward(store, typed(type)), () -> list.add(newReward(type)));
            }
            screen.popup(menu);
        });
    }

    static String rewardTitle(String type) {
        return type.equals("antcoins") ? com.craisinlord.antos.content.client.AntOSPlayerText.currencyName() : StudioText.t("reward." + type);
    }

    private static JsonObject typed(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }

    private void reward(StudioNode list, int index, int size, List<String> types, int depth) {
        StudioNode row = list.at(index);
        String type = row.string("type", "item");
        int top = y;
        String title = rewardTitle(type);
        grip(list, index, top, 19, title);
        int typeWidth = Math.min(78, Math.max(48, kit.width(title) + 20));
        kit.drop(x + 2, y, typeWidth, title, null, click -> {
            if (click.button() == 1) { rowMenu(list, index, size, click); return; }
            StudioMenu menu = new StudioMenu(screen, x + 2, top, 16);
            for (String next : types) menu.check(rewardTitle(next), next.equals(type), () -> {
                if (!next.equals(type)) list.set(index, newReward(next));
            });
            screen.popup(menu);
        });
        int cx = x + typeWidth + 6;
        int right = x + w - 14;
        removeButton(right, y, () -> list.removeAt(index));
        switch (type) {
            case "item" -> {
                right -= 36;
                numberField(row, "count", right, y, 34, 1, 1, 108, true);
                right -= 8;
                kit.text("x", right, y + 5, 8, StudioTheme.soft);
                String item = row.string("item");
                boolean wrong = bad(row.field("item"));
                kit.slot(cx, y - 1, 18, item.isEmpty() ? null : StudioIcon.item(item), wrong, item, click ->
                        screen.popup(new StudioPicker(screen, cx, top - 1, 18, picked -> row.put("item", picked.id())).items(false, false)));
                if (wrong) kit.outline(cx, y - 1, 18, 18, StudioTheme.error);
                kit.text(item.isEmpty() ? t("pick_item") : StudioText.itemName(item), cx + 23, y + 4, right - 4 - cx - 23, item.isEmpty() ? StudioTheme.soft : StudioTheme.strong);
            }
            case "antcoins", "experience", "experience_levels" -> {
                StudioIcons.reward(store, row.object()).draw(kit, cx, y, 16);
                String field = type.equals("antcoins") ? "amount" : type.equals("experience") ? "points" : "levels";
                numberField(row, field, right - 62, y, 60, 1, 1, type.equals("antcoins") ? 1_000_000_000 : type.equals("experience") ? 1_000_000 : 1000, true);
            }
            case "item_pool" -> refField(row, "pool", "kind:reward_pool", false, cx, y, right - 4 - cx);
            case "archive" -> refField(row, "entry", "kind:archive_entry", false, cx, y, right - 4 - cx);
            case "advancement" -> refField(row, "advancement", "advancement", false, cx, y, right - 4 - cx);
            case "effect" -> {
                kit.text(t("seconds_short"), right - 12, y + 5, 10, StudioTheme.soft);
                right -= 50;
                kit.number(row.id("duration_ticks") + "!number", right, y, 36, Math.round(row.integer("duration_ticks", 1200) / 20.0), 1, 3600, true, value -> row.put("duration_ticks", (int) (value * 20)));
                refField(row, "effect", "effect", false, cx, y, right - 4 - cx);
            }
            case "mail" -> {
                boolean saved = row.has("message");
                int available = right - 4 - cx;
                if (saved) refField(row, "message", "kind:message", false, cx, y, available);
                else kit.field(row.id("subject"), cx, y, available, row.string("subject"), new StudioKit.Field().hint(t("field.subject")).max(64).bad(bad(row.field("subject"))), value -> row.put("subject", value));
            }
            case "choice" -> kit.text(t("choice_note"), cx, y + 4, right - cx, StudioTheme.soft);
            default -> kit.text(t("edit_in_json"), cx, y + 4, right - cx, StudioTheme.soft);
        }
        y = top + 20;
        int kept = x;
        int keptW = w;
        x += 10;
        w -= 10;
        if (type.equals("choice")) choiceOptions(row.at("options"));
        else if (type.equals("mail") && !row.has("message")) {
            row(t("field.sender"), "", (fx, fw) -> kit.field(row.id("sender"), fx, y, fw, row.string("sender"), new StudioKit.Field().max(64), value -> row.put("sender", value)));
            area(row, "body", t("hint.mail_body"), 4096, 28);
            if (advanced) {
                kit.button(x, y, kit.buttonWidth(t("use_saved_mail")), 14, t("use_saved_mail"), StudioKit.GHOST, true, "", () -> row.change("", target -> {
                    JsonObject object = target.getAsJsonObject();
                    object.remove("sender");
                    object.remove("subject");
                    object.remove("body");
                    object.addProperty("message", "");
                }));
                y += 16;
            }
        } else if (advanced) switch (type) {
            case "effect" -> number(row, "amplifier", 0, 0, 255, true);
            case "item_pool" -> {
                if (depth == 0) choice(row, "mode", "random", List.of("random", "choice"));
            }
            default -> { }
        }
        x = kept;
        w = keptW;
    }

    private void choiceOptions(StudioNode options) {
        for (int index = 0; index < options.array().size(); index++) {
            int position = index;
            StudioNode option = options.at(index);
            kit.field(option.id("title"), x, y, w - 18, option.string("title"), new StudioKit.Field().hint(t("option_title")).max(128), value -> option.put("title", value));
            removeButton(x + w - 14, y, () -> options.removeAt(position));
            y += 19;
            x += 8;
            w -= 8;
            rewards(option.at("rewards"), REWARDS.stream().filter(OPTION_REWARDS::contains).toList(), 1);
            x -= 8;
            w += 8;
            y += 2;
        }
        if (options.array().size() < 6) add(t("option"), at -> {
            JsonObject option = new JsonObject();
            option.addProperty("id", "option_" + java.util.UUID.randomUUID().toString().substring(0, 6));
            option.addProperty("title", t("option_number", options.array().size() + 1));
            JsonArray inner = new JsonArray();
            inner.add(newReward("item"));
            option.add("rewards", inner);
            options.add(option);
        });
    }

    private List<String> quietRewards() { return REWARDS.stream().filter(type -> !QUIET_REWARDS.contains(type)).toList(); }

    private void category(boolean task) {
        section("text", t(task ? "section.category" : "section.group"), "", List.of("title", "icon", "sort_order"), () -> {
            if (task) ref(root, "group", "kind:group", true);
            item(root, "icon", "icon_entity", t("field.icon"));
            number(root, "sort_order", 0, -100000, 100000, true);
            Checks checks = checks();
            if (!task) checks.add(root, "collapsed", false);
            checks.add(root, "green_tint", true);
            if (advanced) checks.add(root, "render_mob_from_spawn_egg", true);
            checks.end();
        });
        if (task) section("contains", t("section.tasks_inside"), "", List.of(), () -> cards(store.keys(AuthoringKind.TASK).stream()
                .filter(entry -> StudioJson.string(store.view(entry), "category", "antos:general").equals(key.id())).toList()));
        if (advanced) leftovers(Set.of("title", "group", "icon", "icon_entity", "sort_order", "collapsed", "green_tint", "render_mob_from_spawn_egg"));
    }

    private void cards(List<StudioKey> keys) {
        if (keys.isEmpty()) {
            kit.text(t("nothing_here"), x, y + 2, w, StudioTheme.soft);
            y += 14;
        }
        for (StudioKey entry : keys) {
            card(entry, x, y, w, null);
            y += 18;
        }
    }

    private void card(StudioKey entry, int cx, int cy, int cw, Runnable remove) {
        if (!kit.visible(cy, 17)) return;
        boolean hover = kit.hover(cx, cy, cw, 17);
        kit.box(cx, cy, cw, 17, hover ? StudioTheme.hover : StudioTheme.button, hover ? StudioTheme.accent : StudioTheme.line);
        boolean exists = store.exists(entry);
        if (exists) StudioIcons.of(store, entry).draw(kit, cx + 4, cy + 3, 11);
        kit.text(exists ? store.label(entry) : entry.id(), cx + 19, cy + 5, cw - 32, exists ? StudioTheme.text : StudioTheme.error);
        if (store.broken(entry)) kit.dot(cx + cw - 10, cy + 6, true, false);
        else if (store.dirty(entry)) kit.dot(cx + cw - 9, cy + 6, false, false);
        kit.grab(cx, cy, cw, 17, new StudioKit.Payload(entry, store.label(entry)), click -> {
            if (click.button() == 1) {
                StudioMenu menu = new StudioMenu(screen, click.x(), click.y(), 0).item(t("open"), () -> screen.open(entry));
                if (remove != null) menu.danger(t("take_off"), true, remove);
                screen.popup(menu);
            } else if (exists) screen.open(entry);
        });
    }

    private void pool() {
        section("gets", t("section.pool"), t("pool_note"), List.of("entries"), () -> {
            StudioNode list = root.at("entries");
            JsonArray values = list.array();
            double total = 0;
            for (JsonElement value : values) if (value.isJsonObject()) total += Math.max(1, StudioJson.number(value.getAsJsonObject(), "weight", 1));
            for (int index = 0; index < values.size(); index++) {
                int position = index;
                StudioNode row = list.at(index);
                grip(list, index, y, 19, t("item"));
                int slotY = y;
                boolean nested = row.has("pool");
                if (nested) refField(row, "pool", "kind:reward_pool", false, x + 2, y, w - 130);
                else {
                    String item = row.string("item");
                    kit.slot(x + 2, y, 18, item.isEmpty() ? null : StudioIcon.item(item), bad(list.field(Integer.toString(index))), item, click ->
                            screen.popup(new StudioPicker(screen, x + 2, slotY, 18, picked -> row.put("item", picked.id())).items(false, false)));
                    kit.text(item.isEmpty() ? t("pick_item") : StudioText.itemName(item), x + 25, y + 5, w - 155, StudioTheme.strong);
                }
                kit.text("x", x + w - 124, y + 5, 8, StudioTheme.soft);
                numberField(row, "count", x + w - 116, y, 28, 1, 1, 108, true);
                numberField(row, "weight", x + w - 84, y, 34, 1, 1, 1_000_000, true);
                if (kit.hover(x + w - 84, y, 34, 16)) kit.tooltip(t("hint.weight"));
                kit.right(Math.round(1000 * Math.max(1, row.number("weight", 1)) / Math.max(1, total)) / 10.0 + "%", x + w - 16, y + 5, StudioTheme.soft);
                removeButton(x + w - 14, y, () -> list.removeAt(position));
                y += 20;
            }
            add(t("item"), at -> screen.popup(new StudioPicker(screen, at[0], at[1], 14, picked -> list.add(poolEntry("item", picked.id()))).items(false, false)));
            if (advanced) add(t("another_pool"), at -> pickRef("kind:reward_pool", at[0], at[1], false, value -> list.add(poolEntry("pool", value))));
        });
        used();
    }

    private static JsonObject poolEntry(String name, String id) {
        JsonObject entry = new JsonObject();
        entry.addProperty(name, id);
        entry.addProperty("weight", 1);
        entry.addProperty("count", 1);
        return entry;
    }

    private void used() {
        List<StudioKey> users = screen.users(key);
        if (users.isEmpty()) return;
        section("used", t("section.used"), "", List.of(), () -> cards(users));
    }

    private void disk() {
        int startX = x;
        int startW = w;
        boolean columns = w >= 250;
        List<StudioKey> tasks = store.onDisk(key, AuthoringKind.TASK);
        List<StudioKey> articles = store.onDisk(key, AuthoringKind.ARCHIVE_ENTRY);
        List<StudioKey> mail = new ArrayList<>(store.onDisk(key, AuthoringKind.MESSAGE));
        mail.addAll(store.onDisk(key, AuthoringKind.MAIL_POOL));
        if (columns) {
            int top = y;
            int half = (startW - 6) / 2;
            int bottom = Math.max(diskColumn(AuthoringKind.TASK, tasks, startX, top, half, "tasks"),
                    diskColumn(AuthoringKind.MESSAGE, mail, startX + half + 6, diskColumn(AuthoringKind.ARCHIVE_ENTRY, articles, startX + half + 6, top, half, "articles"), half, "mail"));
            y = bottom;
        } else {
            y = diskColumn(AuthoringKind.TASK, tasks, startX, y, startW, "tasks");
            y = diskColumn(AuthoringKind.ARCHIVE_ENTRY, articles, startX, y, startW, "articles");
            y = diskColumn(AuthoringKind.MESSAGE, mail, startX, y, startW, "mail");
        }
        x = startX;
        w = startW;
        List<StudioKey> programs = store.onDisk(key, AuthoringKind.PROGRAM);
        if (!programs.isEmpty()) section("programs", t("section.programs"), Integer.toString(programs.size()), List.of("programs"), () -> {
            for (StudioKey entry : programs) {
                card(entry, x, y, w, () -> store.takeOffDisk(entry, key));
                y += 18;
            }
        });
        if (!root.at("files").array().isEmpty()) section("files", t("section.files"), "", List.of("files"), () -> files(root.at("files")));
        if (!root.at("rewards").array().isEmpty()) section("rewards", t("section.disk_rewards"), "", List.of("rewards"), () -> rewards(root.at("rewards"), quietRewards(), 0));
        if (!root.string("wallpaper").isEmpty()) section("wallpaper", t("section.wallpaper"), "", List.of("wallpaper"), () -> ref(root, "wallpaper", "wallpaper", true));
        section("also", t("section.also"), "", List.of(), () -> {
            Flow flow = flow();
            flow.chip("+ " + t("file"), null, false, t("file.tip"), click -> {
                JsonObject file = new JsonObject();
                file.addProperty("path", "readme.txt");
                file.addProperty("source", "");
                root.at("files").add(file);
            });
            flow.chip("+ " + StudioText.kind(AuthoringKind.PROGRAM), null, false, "", click ->
                    pickRef("kind:program", click.x(), click.y(), false, value -> root.change("", target -> StudioJson.add(target.getAsJsonObject(), "programs", value))));
            if (root.string("wallpaper").isEmpty()) flow.chip("+ " + t("wallpaper"), null, false, "", click ->
                    pickRef("wallpaper", click.x(), click.y(), false, value -> root.put("wallpaper", value)));
            flow.chip("+ " + t("reward_chip"), null, false, t("disk_reward.tip"), click -> root.at("rewards").add(newReward("item")));
            flow.end();
        });
        section("password", t("section.password"), "", List.of("encryption"), () -> {
            if (!root.has("encryption")) {
                kit.text(t("password.none"), x, y + 4, w - 60, StudioTheme.soft);
                kit.button(x + w - 50, y, 50, 16, t("set"), StudioKit.NORMAL, true, "", () -> {
                    JsonObject lock = new JsonObject();
                    lock.addProperty("locked", true);
                    lock.addProperty("password", "");
                    root.put("encryption", lock);
                });
                y += 18;
                return;
            }
            StudioNode lock = root.at("encryption");
            text(lock, "password", t("password.hint"), 128);
            optionalText(lock, "hint", t("optional"), 512);
            if (advanced) {
                strings(lock, "password_any", t("field.password"));
                checks().add(lock, "case_sensitive", false).end();
            }
            kit.button(x, y, kit.buttonWidth(t("password.remove")), 15, t("password.remove"), StudioKit.DANGER, true, "", () -> root.drop("encryption"));
            y += 18;
        });
        if (advanced) {
            section("more", t("section.more"), "", List.of("category"), () -> {
                ref(root, "category", "kind:disk_category", true);
                checks().add(root, "hidden", false).add(root, "consumable", false).end();
            });
            leftovers(Set.of("title", "category", "entries", "tasks", "wallpaper", "description", "hidden", "rewards", "files", "programs", "subscribe", "consumable", "encryption"));
        }
    }

    private int diskColumn(AuthoringKind kind, List<StudioKey> keys, int cx, int cy, int cw, String name) {
        int rows = keys.size() + 1;
        int height = 18 + rows * 18 + 2;
        if (!kit.visible(cy, height)) return cy + height + 5;
        if (kit.over(cx, cy, cw, height)) hovered = name;
        kit.box(cx, cy, cw, height, StudioTheme.section, hovered.equals(name) ? StudioTheme.strongLine : StudioTheme.line);
        kit.text(t("section." + name), cx + 6, cy + 5, cw - 40, StudioTheme.muted);
        kit.right(Integer.toString(keys.size()), cx + cw - 6, cy + 5, StudioTheme.soft);
        int top = cy + 17;
        for (StudioKey entry : keys) {
            boolean direct = store.direct(entry, key);
            card(entry, cx + 5, top, cw - 10, direct ? () -> store.takeOffDisk(entry, key) : null);
            if (kit.hover(cx + 5, top, cw - 10, 17)) kit.tooltip(direct ? store.label(entry) : t("through_chain", store.label(entry)));
            top += 18;
        }
        boolean hover = kit.hover(cx + 5, top, cw - 10, 16);
        kit.dashed(cx + 5, top, cw - 10, 16, hover ? StudioTheme.accent : StudioTheme.strongLine);
        kit.text("+ " + t("add_" + name), cx + 11, top + 4, cw - 22, hover ? StudioTheme.accent : StudioTheme.muted);
        int addY = top;
        kit.press(cx + 5, top, cw - 10, 16, () -> {
            StudioMenu menu = new StudioMenu(screen, cx + 5, addY, 16);
            if (kind == AuthoringKind.MESSAGE) {
                menu.item(t("mail_when_inserted"), () -> screen.makeMail(key, "disk_installed"));
                menu.item(t("mail_after_task"), () -> screen.makeMail(key, "task_complete"));
                menu.item(t("mail_random"), () -> screen.makeMail(key, "random"));
            } else menu.item(t("new_kind", StudioText.kind(kind)), () -> screen.make(kind, t("untitled", StudioText.kind(kind)), true));
            menu.item(t("add_existing"), () -> pickRef("kind:" + kind.key(), cx + 5, addY, false, value -> store.putOnDisk(new StudioKey(kind, value), key)));
            screen.popup(menu);
        });
        kit.dropZone(cx, cy, cw, height, value -> value instanceof StudioKey dropped && (dropped.kind() == kind || kind == AuthoringKind.MESSAGE && dropped.kind() == AuthoringKind.MAIL_POOL)
                && !store.direct(dropped, key), value -> store.putOnDisk((StudioKey) value, key));
        return cy + height + 5;
    }

    private void files(StudioNode list) {
        for (int index = 0; index < list.array().size(); index++) {
            int position = index;
            StudioNode row = list.at(index);
            int half = (w - 24) / 2;
            kit.field(row.id("path"), x, y, half, row.string("path"), new StudioKit.Field().hint(t("file.name")).max(64).bad(bad(row.field("path"))), value -> row.put("path", value));
            refField(row, "source", "file", false, x + half + 4, y, half);
            removeButton(x + w - 14, y, () -> list.removeAt(position));
            y += 19;
        }
        add(t("file"), at -> {
            JsonObject file = new JsonObject();
            file.addProperty("path", "readme.txt");
            file.addProperty("source", "");
            list.add(file);
        });
        if (store.refs.list("file").isEmpty()) y = kit.wrap(t("file.where"), x, y, w, StudioTheme.soft) + 2;
    }

    private void diskCategory() {
        section("look", t("section.look"), "", List.of("texture", "color"), () -> {
            row(t("field.texture"), t("hint.texture"), (cx, cw) -> refField(root, "texture", "texture", false, cx, y, cw));
            row(t("field.color"), t("hint.color"), (cx, cw) -> {
                String color = root.string("color", "AAAAAA").replaceFirst("^#", "");
                int parsed = StudioTheme.hex(color, StudioTheme.line);
                kit.box(cx, y, 16, 16, parsed, StudioTheme.strongLine);
                kit.field(root.id("color"), cx + 20, y, Math.min(70, cw - 20), color, new StudioKit.Field().max(7).bad(bad("color")), value -> root.put("color", value));
            });
        });
        section("contains", t("section.floppies_inside"), "", List.of(), () -> cards(store.keys(AuthoringKind.DISK).stream().filter(entry -> {
            String category = StudioJson.string(store.view(entry), "category", "general");
            return (category.contains(":") ? category : entry.namespace() + ":" + category).equals(key.id());
        }).toList()));
        if (advanced) leftovers(Set.of("title", "texture", "color"));
    }

    private void whoDisks(String title) {
        section("who", title, "", List.of("who"), () -> {
            Flow flow = flow();
            List<StudioKey> disks = store.keys(AuthoringKind.DISK).stream().filter(disk -> store.direct(key, disk)).toList();
            for (StudioKey disk : disks) flow.chip(store.label(disk), StudioIcons.of(store, disk), true, "", click -> linkMenu(click, disk, () -> store.takeOffDisk(key, disk)));
            flow.chip("+ " + t("who.floppy"), null, false, t("who.floppy.tip"), click -> pickRef("kind:disk", click.x(), click.y(), false, value -> store.putOnDisk(key, key(AuthoringKind.DISK, value))));
            flow.end();
            if (disks.isEmpty()) y = kit.wrap(t("who.nobody"), x, y, w, StudioTheme.warning) + 2;
        });
    }

    private void program() {
        whoDisks(t("section.who_program"));
        boolean adventure = root.string("type").equals("adventure");
        section("type", t("section.program"), "", List.of("type", "start"), () -> {
            row(t("field.type"), "", (cx, cw) -> kit.segments(cx, y, List.of(t("quiz"), t("adventure")), adventure ? 1 : 0, index -> {
                if (index == (adventure ? 1 : 0)) return;
                root.change("", target -> {
                    JsonObject body = target.getAsJsonObject();
                    body.addProperty("type", index == 1 ? "adventure" : "quiz");
                    if (index == 1 && !body.has("nodes")) {
                        JsonObject nodes = new JsonObject();
                        nodes.add("start", page(t("adventure.first"), "end"));
                        JsonObject end = new JsonObject();
                        end.addProperty("text", t("adventure.last"));
                        end.addProperty("ending", "finished");
                        nodes.add("end", end);
                        body.add("nodes", nodes);
                        body.addProperty("start", "start");
                    }
                    if (index == 0 && !body.has("questions")) body.add("questions", new JsonArray());
                });
            }));
            if (!adventure) {
                int count = root.at("questions").array().size();
                number(root, "questions_per_run", Math.max(1, count), 1, 128, true);
            } else row(t("field.start"), t("hint.start"), (cx, cw) -> pageDrop(root.string("start"), cx, y, cw, value -> root.put("start", value)));
            if (advanced) checks().add(root, "green_tint", true).end();
        });
        if (adventure) adventure();
        else {
            section("questions", t("section.questions"), Integer.toString(root.at("questions").array().size()), List.of("questions"), this::questions);
            section("gets", t("section.gets"), "", List.of("rewards", "daily_reward"), () -> {
                rewards(root.at("rewards"), quietRewards(), 0);
                choice(root, "reward_on", "perfect", List.of("perfect", "complete"));
                choice(root, "reward_limit", "daily", List.of("daily", "once"));
            });
        }
        if (advanced) leftovers(Set.of("title", "type", "questions", "questions_per_run", "reward_on", "reward_limit", "rewards", "green_tint", "start", "nodes"));
    }

    private void questions() {
        StudioNode list = root.at("questions");
        for (int index = 0; index < list.array().size(); index++) {
            int position = index;
            StudioNode question = list.at(index);
            JsonArray answers = question.at("answers").array();
            int height = 20 + answers.size() * 18 + 17 + (advanced ? 58 : 0);
            if (!kit.visible(y, height)) {
                y += height;
                continue;
            }
            grip(list, index, y, 18, t("question_number", index + 1));
            kit.field(question.id("question"), x + 2, y, w - 18, question.string("question"), new StudioKit.Field().hint(t("question_hint")).max(2048).bad(bad(question.field("question"))), value -> question.put("question", value));
            removeButton(x + w - 14, y, () -> list.removeAt(position));
            y += 19;
            int correct = question.integer("correct", 0);
            for (int answer = 0; answer < answers.size(); answer++) {
                int choice = answer;
                StudioNode node = question.at("answers");
                boolean hover = kit.hover(x + 8, y, 14, 16);
                kit.box(x + 10, y + 4, 9, 9, choice == correct ? StudioTheme.accent : StudioTheme.desk, hover ? StudioTheme.accent : StudioTheme.strongLine);
                if (hover) kit.tooltip(t("correct_answer"));
                kit.press(x + 8, y, 14, 16, () -> question.put("correct", choice));
                kit.field(node.id(Integer.toString(answer)), x + 24, y, w - 42, answers.get(answer).getAsString(), new StudioKit.Field().max(512), value -> node.set(choice, new JsonPrimitive(value)));
                if (answers.size() > 2) removeButton(x + w - 14, y, () -> question.change("", target -> {
                    JsonObject object = target.getAsJsonObject();
                    object.getAsJsonArray("answers").remove(choice);
                    int was = StudioJson.integer(object, "correct", 0);
                    if (was > choice || was >= object.getAsJsonArray("answers").size()) object.addProperty("correct", Math.max(0, was - 1));
                }));
                y += 18;
            }
            x += 24;
            w -= 24;
            if (answers.size() < 8) add(t("answer"), at -> question.at("answers").add(new JsonPrimitive("")));
            if (advanced) {
                optionalText(question, "explanation", t("optional"), 2048);
                item(question, "item", "entity", t("field.picture"));
            }
            x -= 24;
            w += 24;
            y += 2;
        }
        add(t("question"), at -> {
            JsonObject question = new JsonObject();
            question.addProperty("question", "");
            JsonArray answers = new JsonArray();
            answers.add("");
            answers.add("");
            question.add("answers", answers);
            question.addProperty("correct", 0);
            list.add(question);
        });
    }

    private static JsonObject page(String text, String destination) {
        JsonObject page = new JsonObject();
        page.addProperty("text", text);
        JsonArray choices = new JsonArray();
        JsonObject choice = new JsonObject();
        choice.addProperty("text", StudioText.t("adventure.continue"));
        choice.addProperty("goto", destination);
        choices.add(choice);
        page.add("choices", choices);
        return page;
    }

    private void pageDrop(String current, int cx, int cy, int cw, Consumer<String> action) {
        JsonObject nodes = root.at("nodes").object();
        String shown = current.isEmpty() ? t("picker.choose") : current;
        kit.drop(cx, cy, cw, shown, null, click -> {
            StudioMenu menu = new StudioMenu(screen, cx, cy, 16);
            for (String name : nodes.keySet()) menu.check(name, name.equals(current), () -> action.accept(name));
            menu.separator().item(t("adventure.new_page"), () -> {
                String name = newPage(nodes);
                store.transaction(() -> {
                    JsonObject end = new JsonObject();
                    end.addProperty("text", "");
                    end.addProperty("ending", name);
                    root.at("nodes").put(name, end);
                    action.accept(name);
                });
            });
            screen.popup(menu);
        });
        if (!current.isEmpty() && !nodes.has(current)) kit.outline(cx, cy, cw, 16, StudioTheme.error);
    }

    private static String newPage(JsonObject nodes) {
        int number = nodes.size() + 1;
        while (nodes.has("page_" + number)) number++;
        return "page_" + number;
    }

    private void adventure() {
        JsonObject nodes = root.at("nodes").object();
        for (String name : nodes.keySet()) {
            StudioNode page = root.at("nodes").at(name);
            section("page:" + name, name.equals(root.string("start")) ? t("adventure.page_start", name) : t("adventure.page", name), "", List.of("nodes/" + name), () -> {
                area(page, "text", t("adventure.text"), 8192, 30);
                boolean ending = page.has("ending") && page.at("choices").array().isEmpty();
                row(t("field.page_type"), "", (cx, cw) -> kit.segments(cx, y, List.of(t("adventure.choices"), t("adventure.ending")), ending ? 1 : 0, index -> page.change("", target -> {
                    JsonObject object = target.getAsJsonObject();
                    if (index == 1) {
                        object.remove("choices");
                        object.addProperty("ending", name);
                    } else {
                        object.remove("ending");
                        object.remove("rewards");
                        object.remove("password");
                        if (!object.has("choices")) object.add("choices", page("", nodes.keySet().iterator().next()).getAsJsonArray("choices"));
                    }
                })));
                if (ending) {
                    optionalText(page, "ending", "", 64);
                    rewards(page.at("rewards"), quietRewards(), 0);
                    if (advanced) optionalText(page, "password", t("optional"), 128);
                } else {
                    StudioNode choices = page.at("choices");
                    for (int index = 0; index < choices.array().size(); index++) {
                        int position = index;
                        StudioNode choice = choices.at(index);
                        int dropWidth = Math.min(90, w / 3);
                        kit.field(choice.id("text"), x, y, w - dropWidth - 20, choice.string("text"), new StudioKit.Field().hint(t("adventure.choice")).max(512), value -> choice.put("text", value));
                        pageDrop(choice.string("goto"), x + w - dropWidth - 17, y, dropWidth, value -> choice.put("goto", value));
                        removeButton(x + w - 14, y, () -> choices.removeAt(position));
                        y += 19;
                        if (advanced) pair(() -> optionalText(choice, "requires_flag", "", 64), () -> optionalText(choice, "requires_no_flag", "", 64));
                    }
                    if (choices.array().size() < 8) add(t("adventure.choice"), at -> {
                        JsonObject choice = new JsonObject();
                        choice.addProperty("text", "");
                        choice.addProperty("goto", "");
                        choices.add(choice);
                    });
                }
                if (advanced) {
                    strings(page, "set_flags", "");
                    strings(page, "clear_flags", "");
                }
                if (!name.equals(root.string("start"))) {
                    kit.button(x, y, kit.buttonWidth(t("adventure.remove")), 14, t("adventure.remove"), StudioKit.DANGER, true, "", () -> root.at("nodes").drop(name));
                    y += 16;
                }
            });
        }
        kit.button(x, y, kit.buttonWidth("+ " + t("adventure.new_page")), 15, "+ " + t("adventure.new_page"), StudioKit.ADD, true, "", () -> {
            String name = newPage(nodes);
            JsonObject end = new JsonObject();
            end.addProperty("text", "");
            end.addProperty("ending", name);
            root.at("nodes").put(name, end);
            jump("page:" + name);
        });
        y += 20;
    }

    private void message() {
        StudioNode trigger = root.at("trigger");
        String type = trigger.string("type");
        section("when", t("section.when"), "", List.of("trigger", "once"), () -> {
            Flow flow = flow();
            for (String value : List.of("disk_installed", "task_complete", "random"))
                flow.chip(t("trigger." + value), null, type.equals(value), t("trigger." + value + ".tip"), click -> setTrigger(value));
            boolean other = !Set.of("disk_installed", "task_complete", "random").contains(type);
            flow.chip(other && !type.isEmpty() ? StudioText.has("trigger." + type) ? t("trigger." + type) : StudioText.prettyId(type) : t("more"), null, other && !type.isEmpty(), "", click ->
                    screen.popup(new StudioMenu(screen, click.x(), click.y(), 4)
                            .check(t("trigger.mail_read"), type.equals("mail_read"), () -> setTrigger("mail_read"))
                            .check(t("trigger.advancement"), type.equals("advancement"), () -> setTrigger("advancement"))
                            .check(t("trigger.game_record"), type.equals("game_record"), () -> setTrigger("game_record"))
                            .check(t("trigger.custom"), type.contains(":"), () -> setTrigger("pack:custom"))));
            flow.end();
            switch (type) {
                case "disk_installed" -> row(t("which"), "", (cx, cw) -> refField(trigger, "value", "kind:disk", false, cx, y, cw));
                case "task_complete" -> row(t("which"), "", (cx, cw) -> refField(trigger, "value", "kind:task", false, cx, y, cw));
                case "mail_read" -> row(t("which"), "", (cx, cw) -> refField(trigger, "value", "kind:message", false, cx, y, cw));
                case "advancement" -> row(t("which"), "", (cx, cw) -> refField(trigger, "value", "advancement", false, cx, y, cw));
                case "game_record" -> {
                    row(t("which"), "", (cx, cw) -> refField(trigger, "value", "game", false, cx, y, cw));
                    row(t("field.record"), t("hint.record"), (cx, cw) -> {
                        choiceField(trigger, "metric", "wins", List.of("wins", "time", "moves", "score"), cx, y, cw - 50);
                        numberField(trigger, "count", cx + cw - 46, y, 46, 1, 1, 100_000_000, true);
                    });
                }
                case "random" -> {
                    if (trigger.string("pool").isEmpty()) row(t("field.chance"), t("hint.chance"), (cx, cw) -> {
                        double chance = 0;
                        try { chance = Double.parseDouble(trigger.string("value", "0")); } catch (NumberFormatException ignored) { }
                        kit.number(trigger.id("value") + "!number", cx, y, 46, Math.round(chance * 10000) / 100.0, 0, 100, false,
                                value -> trigger.put("value", StudioKit.format(Math.round(value * 100) / 10000.0, false)));
                        kit.text(t("percent_each_day"), cx + 52, y + 5, cw - 54, StudioTheme.soft);
                    });
                    ref(trigger, "pool", "kind:mail_pool", true);
                    Flow owners = flow();
                    List<StudioKey> disks = store.keys(AuthoringKind.DISK).stream().filter(disk -> StudioJson.contains(store.view(disk), "subscribe", key.id())).toList();
                    owners.chip(t("who.everyone"), null, !trigger.bool("subscription_only", false), "", click -> trigger.put("subscription_only", false));
                    for (StudioKey disk : disks) owners.chip(store.label(disk), StudioIcons.of(store, disk), trigger.bool("subscription_only", false), "", click -> linkMenu(click, disk, () -> store.takeOffDisk(key, disk)));
                    owners.chip("+ " + t("who.floppy"), null, false, t("mail_random.tip"), click -> pickRef("kind:disk", click.x(), click.y(), false, value -> store.putOnDisk(key, key(AuthoringKind.DISK, value))));
                    owners.end();
                    if (advanced) number(trigger, "weight", 1, 1, 1_000_000, true);
                }
                default -> {
                    if (!type.isEmpty()) {
                        row(t("field.type"), t("hint.custom_trigger"), (cx, cw) -> kit.field(trigger.id("type"), cx, y, cw, type, new StudioKit.Field().max(64), value -> trigger.put("type", value)));
                        optionalText(trigger, "value", "", 256);
                    }
                }
            }
            checks().add(root, "once", !type.equals("random")).end();
            if (advanced) {
                pair(() -> number(trigger, "delay_minutes", 0, 0, 1_000_000, true), () -> number(trigger, "delay_seconds", 0, 0, 1_000_000, true));
                number(trigger, "delay", 0, 0, 1_000_000, true);
            }
        });
        section("from", t("section.from"), "", List.of("sender", "sender_display"), () -> {
            row(t("field.sender_display"), t("hint.sender_display"), (cx, cw) -> {
                String shown = root.string("sender_display", root.string("sender"));
                kit.field(root.id("sender_display"), cx, y, cw - 20, shown, new StudioKit.Field().hint(t("hint.who_from")).max(64).bad(bad("sender")), value -> root.change("sender_display", target -> {
                    JsonObject body = target.getAsJsonObject();
                    String before = StudioText.slug(StudioJson.string(body, "sender_display"));
                    String address = StudioJson.string(body, "sender");
                    body.addProperty("sender_display", value);
                    String slug = StudioText.slug(value).replace("_", "");
                    if (address.isBlank() || address.equals(before.replace("_", ""))) body.addProperty("sender", slug.length() > 24 ? slug.substring(0, 24) : slug);
                }));
                kit.button(cx + cw - 17, y, 17, 16, "...", StudioKit.NORMAL, true, t("senders.tip"), () -> pickRef("sender", cx, y, false, value -> root.change("", target -> {
                    target.getAsJsonObject().addProperty("sender", value);
                    target.getAsJsonObject().addProperty("sender_display", StudioProblems.senderName(store, value));
                })));
            });
            if (advanced) {
                optionalText(root, "sender", "", 128);
                item(root, "sender_avatar_item", "sender_avatar_entity", t("field.avatar"));
                optionalText(root, "style", t("choice.default"), 64);
            }
        });
        section("message", t("section.message"), "", List.of("body", "subject"), () -> {
            int height = kit.areaHeight(root.id("body"), 44, 140);
            kit.area(root.id("body"), x, y, w, height, root.string("body"), 4096, t("hint.mail_body"), bad("body"), value -> root.put("body", value));
            y += height + 3;
            kit.button(x, y, kit.buttonWidth("+ " + t("player_name")), 14, "+ " + t("player_name"), StudioKit.ADD, true, t("player_name.tip"), () -> kit.insert(root.id("body"), "{player}"));
            y += 15;
        });
        section("attached", t("section.attached"), "", List.of("delivery", "antcoins"), () -> {
            items(root.at("delivery"), "items", true, false);
            if (root.has("antcoins") && root.integer("antcoins", 0) > 0) row(com.craisinlord.antos.content.client.AntOSPlayerText.currencyName(), "", (cx, cw) -> {
                numberField(root, "antcoins", cx, y, 60, 0, 0, 1_000_000_000, true);
                removeButton(cx + cw - 14, y, () -> root.drop("antcoins"));
            });
            else add(com.craisinlord.antos.content.client.AntOSPlayerText.currencyName(), at -> root.put("antcoins", 10));
            if (advanced) {
                choice(root.at("delivery"), "on", "read", List.of("read", "deliver"));
                add(t("explorer_map_add"), at -> pickRef("structure", at[0], at[1], false, value -> {
                    JsonObject map = new JsonObject();
                    map.addProperty("explorer_map", value);
                    map.addProperty("decoration", "minecraft:red_x");
                    root.at("delivery").at("items").add(map);
                }));
            }
        });
        if (advanced) {
            section("conditions", t("section.conditions"), "", List.of("conditions", "deliver_window"), () -> {
                StudioNode conditions = root.at("conditions");
                refList(conditions, "tasks_complete", "kind:task");
                refList(conditions, "mail_read", "kind:message");
                refList(conditions, "advancements", "advancement");
                refList(conditions, "not_advancements", "advancement");
                ref(conditions, "dimension", "dimension", true);
                StudioNode window = root.at("deliver_window");
                pair(() -> number(window, "from", 0, 0, 24000, true), () -> number(window, "to", 24000, 0, 24000, true));
            });
            section("effects", t("section.effects"), "", List.of("notification"), () -> {
                optionalText(root, "notification", t("choice.default"), 256);
                checks().add(root, "delete_after_read", false).add(root, "blockable", true).end();
            });
            leftovers(Set.of("sender", "sender_display", "subject", "body", "trigger", "once", "style", "sender_avatar_item", "sender_avatar_entity", "delete_after_read", "blockable",
                    "notification", "antcoins", "conditions", "deliver_window", "delivery"));
        }
    }

    private void setTrigger(String value) {
        StudioNode trigger = root.at("trigger");
        if (trigger.string("type").equals(value)) return;
        root.change("", target -> {
            JsonObject body = target.getAsJsonObject();
            JsonObject previous = StudioJson.object(body, "trigger");
            JsonObject next = new JsonObject();
            next.addProperty("type", value);
            next.addProperty("value", value.equals("random") ? "0.05" : value.equals("disk_installed") && screen.contextDisk() != null ? screen.contextDisk().id() : "");
            if (value.equals("game_record")) {
                next.addProperty("metric", "wins");
                next.addProperty("count", 1);
            }
            for (String name : List.of("delay", "delay_seconds", "delay_minutes")) if (previous.has(name)) next.add(name, previous.get(name));
            body.add("trigger", next);
        });
    }

    private void mailPool() {
        section("text", t("section.mail_pool"), "", List.of("chance_per_day", "max_per_day"), () -> {
            row(t("field.chance"), t("hint.pool_chance"), (cx, cw) -> {
                kit.number(root.id("chance_per_day") + "!number", cx, y, 46, Math.round(root.number("chance_per_day", 1) * 10000) / 100.0, 0, 100, false, value -> root.put("chance_per_day", Math.round(value * 100) / 10000.0));
                kit.text(t("percent_each_day"), cx + 52, y + 5, cw - 54, StudioTheme.soft);
            });
            number(root, "max_per_day", 1, 0, 1_000_000, true);
            checks().add(root, "subscription_only", false).add(root, "blockable", true).end();
        });
        section("contains", t("section.pool_mail"), "", List.of(), () -> cards(store.keys(AuthoringKind.MESSAGE).stream().filter(entry -> {
            String pool = StudioJson.string(StudioJson.object(store.view(entry), "trigger"), "pool");
            return !pool.isEmpty() && (pool.contains(":") ? pool : entry.namespace() + ":" + pool).equals(key.id());
        }).toList()));
        if (advanced) leftovers(Set.of("chance_per_day", "max_per_day", "subscription_only", "blockable"));
    }

    private void whoUnlock(String title) {
        section("who", title, "", List.of("unlock_tasks", "unlock_disks", "unlock_commands"), () -> {
            List<String> tasks = root.strings("unlock_tasks");
            List<String> disks = root.strings("unlock_disks");
            List<String> commands = root.strings("unlock_commands");
            boolean everyone = tasks.isEmpty() && disks.isEmpty() && commands.isEmpty();
            Flow flow = flow();
            flow.chip(t("who.everyone"), null, everyone, "", click -> root.change("", target -> {
                JsonObject body = target.getAsJsonObject();
                body.remove("unlock_disks");
                body.remove("unlock_commands");
                body.add("unlock_tasks", new JsonArray());
            }));
            unlocks(flow, "unlock_disks", AuthoringKind.DISK, disks);
            unlocks(flow, "unlock_tasks", AuthoringKind.TASK, tasks);
            unlocks(flow, "unlock_commands", AuthoringKind.TERMINAL_COMMAND, commands);
            flow.chip("+ " + t("who.floppy"), null, false, "", click -> pickRef("kind:disk", click.x(), click.y(), false, value -> root.change("", target -> StudioJson.add(target.getAsJsonObject(), "unlock_disks", value))));
            flow.chip("+ " + t("who.after_task"), null, false, "", click -> pickRef("kind:task", click.x(), click.y(), false, value -> root.change("", target -> StudioJson.add(target.getAsJsonObject(), "unlock_tasks", value))));
            flow.chip("+ " + t("who.command"), null, false, "", click -> pickRef("kind:terminal_command", click.x(), click.y(), false, value -> root.change("", target -> StudioJson.add(target.getAsJsonObject(), "unlock_commands", value))));
            flow.end();
            if (tasks.size() + disks.size() + commands.size() > 1) choice(root, "unlock_mode", "all", List.of("all", "any"));
            if (advanced) checks().add(root, "hidden_until_unlocked", true).add(root, "notify_on_unlock", true).end();
        });
    }

    private void unlocks(Flow flow, String name, AuthoringKind kind, List<String> ids) {
        for (String id : ids) flow.chip(kind == AuthoringKind.TASK ? t("who.after", store.label(kind, id)) : store.label(kind, id), store.known(kind, id) ? StudioIcons.of(store, key(kind, id)) : null, true, id,
                click -> linkMenu(click, key(kind, id), () -> root.change("", target -> StudioJson.remove(target.getAsJsonObject(), name, id))));
    }

    private void product() {
        section("price", t("section.price"), "", List.of("payments"), () -> {
            StudioNode list = root.at("payments");
            for (int index = 0; index < list.array().size(); index++) {
                int position = index;
                StudioNode row = list.at(index);
                boolean item = row.string("type", "antcoins").equals("item");
                grip(list, index, y, 18, t("price"));
                int top = y;
                kit.drop(x + 2, y, 70, item ? t("reward.item") : com.craisinlord.antos.content.client.AntOSPlayerText.currencyName(), null, click -> screen.popup(new StudioMenu(screen, x + 2, top, 16)
                        .check(com.craisinlord.antos.content.client.AntOSPlayerText.currencyName(), !item, () -> row.change("", target -> {
                            target.getAsJsonObject().addProperty("type", "antcoins");
                            target.getAsJsonObject().addProperty("resource", "antos:antcoins");
                        }))
                        .check(t("reward.item"), item, () -> row.change("", target -> {
                            target.getAsJsonObject().addProperty("type", "item");
                            target.getAsJsonObject().addProperty("resource", "");
                        }))));
                int cx = x + 78;
                if (item) {
                    String resource = row.string("resource");
                    boolean tag = resource.startsWith("#");
                    kit.slot(cx, y - 1, 18, resource.isEmpty() ? null : tag ? StudioIcon.item("minecraft:bundle") : StudioIcon.item(resource), bad(row.field("resource")), resource, click ->
                            screen.popup(new StudioPicker(screen, cx, top, 18, picked -> row.put("resource", (picked.type() == StudioPicker.Type.TAG ? "#" : "") + picked.id())).items(true, false)));
                    kit.text(resource.isEmpty() ? t("pick_item") : tag ? StudioText.tag(resource) : StudioText.itemName(resource), cx + 23, y + 4, w - 160, StudioTheme.strong);
                } else StudioIcon.coin().draw(kit, cx, y, 16);
                numberField(row, "amount", x + w - 66, y, 50, 1, 1, 1_000_000_000, true);
                removeButton(x + w - 14, y, () -> list.removeAt(position));
                y += 20;
            }
            add(t("price"), at -> {
                JsonObject payment = new JsonObject();
                payment.addProperty("type", "antcoins");
                payment.addProperty("resource", "antos:antcoins");
                payment.addProperty("amount", 10);
                list.add(payment);
            });
        });
        section("gets", t("section.buyer_gets"), "", List.of("rewards", "item_pool", "variants"), () -> {
            StudioNode list = root.at("rewards");
            for (int index = 0; index < list.array().size(); index++) {
                int position = index;
                StudioNode row = list.at(index);
                String item = row.string("item");
                int top = y;
                grip(list, index, y, 19, StudioText.itemName(item));
                kit.slot(x + 2, y, 18, item.isEmpty() ? null : StudioIcon.item(item), bad(row.field("item")), item, click ->
                        screen.popup(new StudioPicker(screen, x + 2, top, 18, picked -> row.put("item", picked.id())).items(false, false)));
                kit.text(item.isEmpty() ? t("pick_item") : StudioText.itemName(item), x + 25, y + 5, w - 85, StudioTheme.strong);
                kit.text("x", x + w - 57, y + 5, 8, StudioTheme.soft);
                numberField(row, "count", x + w - 48, y, 32, 1, 1, 108, true);
                removeButton(x + w - 14, y, () -> list.removeAt(position));
                y += 20;
            }
            add(t("item"), at -> screen.popup(new StudioPicker(screen, at[0], at[1], 14, picked -> {
                JsonObject reward = new JsonObject();
                reward.addProperty("item", picked.id());
                reward.addProperty("count", 1);
                list.add(reward);
            }).items(false, false)));
            if (advanced || root.has("item_pool")) itemPool();
            if (advanced || root.has("variants")) variants();
        });
        whoUnlock(t("section.who_buy"));
        section("text", t("section.listing"), "", List.of("description", "category", "tags", "quantity"), () -> {
            area(root, "description", t("hint.listing"), 32768, 28);
            row(t("field.shop_category"), t("hint.shop_category"), (cx, cw) -> {
                kit.field(root.id("category"), cx, y, cw - 20, root.string("category", "general"), new StudioKit.Field().max(64), value -> root.put("category", value));
                kit.button(cx + cw - 17, y, 17, 16, "...", StudioKit.NORMAL, true, t("existing"), () -> pickRef("shop_category", cx, y, false, value -> root.put("category", value)));
            });
            if (advanced) {
                strings(root, "tags", "");
                number(root, "quantity", 1, 1, 1_000_000, true);
                checks().add(root, "enabled", true).add(root, "green_tint", true).add(root, "render_mob_from_spawn_egg", true).end();
            }
        });
        if (advanced) {
            section("stock", t("section.stock"), "", List.of("availability"), () -> {
                StudioNode availability = root.at("availability");
                pair(() -> number(availability, "server_stock", 0, 0, 1_000_000, true), () -> number(availability, "player_limit", 0, 0, 1_000_000, true));
                pair(() -> number(availability, "restock_after_minecraft_days", 0, 0, 1_000_000, true), () -> number(availability, "restock_after_real_hours", 0, 0, 1_000_000, true));
                number(availability, "cooldown_minecraft_days", 0, 0, 1_000_000, true);
                choice(availability, "player_limit_reset", "none", List.of("none", "minecraft_day", "minecraft_week"));
            });
            section("delivery", t("section.delivery"), "", List.of("delivery", "delivery_location"), () -> {
                choice(root, "delivery", "falling_chest", List.of("falling_chest", "direct", "falling_chest_location"));
                if (root.string("delivery").equals("falling_chest_location")) {
                    StudioNode location = root.at("delivery_location");
                    number(location, "x", 0, -30_000_000, 30_000_000, true);
                    number(location, "y", 64, -2048, 2048, true);
                    number(location, "z", 0, -30_000_000, 30_000_000, true);
                    var player = net.minecraft.client.Minecraft.getInstance().player;
                    if (player != null) {
                        kit.button(x, y, kit.buttonWidth(t("use_here")), 15, t("use_here"), StudioKit.NORMAL, true, "", () -> root.change("", target -> {
                            JsonObject here = new JsonObject();
                            here.addProperty("x", player.blockPosition().getX());
                            here.addProperty("y", player.blockPosition().getY());
                            here.addProperty("z", player.blockPosition().getZ());
                            target.getAsJsonObject().add("delivery_location", here);
                        }));
                        y += 18;
                    }
                }
            });
            section("gallery", t("section.gallery"), "", List.of("gallery"), () -> gallery(root.at("gallery"), false));
            leftovers(Set.of("name", "description", "category", "tags", "thumbnail", "gallery", "quantity", "payments", "unlock_mode", "unlock_tasks", "availability", "rewards", "delivery",
                    "enabled", "hidden_until_unlocked", "notify_on_unlock", "green_tint", "render_mob_from_spawn_egg", "unlock_disks", "unlock_commands", "variants", "item_pool", "delivery_location"));
        }
    }

    private void itemPool() {
        if (!root.has("item_pool")) {
            add(t("item_pool_add"), at -> {
                JsonObject pool = new JsonObject();
                pool.add("items", new JsonArray());
                pool.addProperty("count", 1);
                pool.addProperty("mode", "all");
                root.put("item_pool", pool);
            });
            return;
        }
        StudioNode pool = root.at("item_pool");
        kit.text(t("item_pool_title"), x, y + 2, w - 20, StudioTheme.muted);
        removeButton(x + w - 14, y - 2, () -> root.drop("item_pool"));
        y += 15;
        items(pool, "items", false, false);
        refList(pool, "tags", "item_tag");
        number(pool, "count", 1, 1, 108, true);
        choice(pool, "mode", "all", List.of("all", "rotate"));
    }

    private void variants() {
        if (!root.has("variants")) {
            add(t("variants_add"), at -> {
                JsonObject variants = new JsonObject();
                variants.addProperty("tag", "minecraft:planks");
                variants.addProperty("mode", "choose");
                root.put("variants", variants);
            });
            return;
        }
        StudioNode variants = root.at("variants");
        kit.text(t("variants_title"), x, y + 2, w - 20, StudioTheme.muted);
        removeButton(x + w - 14, y - 2, () -> root.drop("variants"));
        y += 15;
        ref(variants, "tag", "item_tag", false);
        choice(variants, "mode", "choose", List.of("all", "random", "choose"));
    }

    private void gallery(StudioNode list, boolean named) {
        for (int index = 0; index < list.array().size(); index++) {
            int position = index;
            JsonElement value = list.array().get(index);
            if (!value.isJsonObject()) {
                String id = value.isJsonPrimitive() ? value.getAsString() : "";
                kit.slot(x + 2, y, 18, StudioIcon.item(id), false, id, null);
                kit.text(StudioText.itemName(id), x + 25, y + 5, w - 45, StudioTheme.text);
                removeButton(x + w - 14, y, () -> list.removeAt(position));
                y += 20;
                continue;
            }
            StudioNode row = list.at(index);
            String entity = row.string("entity");
            String item = row.string("item", row.string("id"));
            int top = y;
            grip(list, index, y, 19, t("picture"));
            kit.slot(x + 2, y, 18, !entity.isEmpty() ? StudioIcon.entity(entity) : StudioIcon.item(item), false, entity.isEmpty() ? item : entity, click ->
                    screen.popup(new StudioPicker(screen, x + 2, top, 18, picked -> list.set(position, asset(picked, row.string("name")))).items(false, true)));
            String shown = entity.isEmpty() ? StudioText.itemName(item) : StudioText.entityName(entity);
            if (named) kit.field(row.id("name"), x + 25, y + 1, w - 43, row.string("name"), new StudioKit.Field().hint(shown).max(256), text -> row.putOrDrop("name", text));
            else kit.text(shown, x + 25, y + 5, w - 45, StudioTheme.text);
            removeButton(x + w - 14, y, () -> list.removeAt(position));
            y += 20;
        }
        add(t("picture"), at -> screen.popup(new StudioPicker(screen, at[0], at[1], 14, picked -> list.add(asset(picked, ""))).items(false, true)));
    }

    private static JsonObject asset(StudioPicker.Picked picked, String name) {
        JsonObject object = new JsonObject();
        object.addProperty(picked.type() == StudioPicker.Type.ENTITY ? "entity" : "item", picked.id());
        if (!name.isEmpty()) object.addProperty("name", name);
        return object;
    }

    private void sellRule() {
        section("items", t("section.sell_items"), "", List.of("items", "tags"), () -> {
            if (root.has("item") && !root.has("items")) {
                item(root, "item", "", t("item"));
            } else items(root, "items", false, false);
            refList(root, "tags", "item_tag");
        });
        section("price", t("section.sell_price"), "", List.of("value", "limit"), () -> {
            row(t("field.sell_value"), "", (cx, cw) -> {
                StudioIcon.coin().draw(kit, cx, y, 16);
                numberField(root, "value", cx + 20, y, 60, 1, 1, 1_000_000_000, true);
            });
            row(t("field.limit"), t("hint.limit"), (cx, cw) -> {
                numberField(root, "limit", cx, y, 50, 0, 0, 1_000_000, true);
                if (root.integer("limit", 0) > 0) choiceField(root, "limit_reset", "none", List.of("none", "minecraft_day", "minecraft_week"), cx + 56, y, cw - 56);
                else kit.text(t("no_limit"), cx + 56, y + 5, cw - 56, StudioTheme.soft);
            });
        });
        whoUnlock(t("section.who_sell"));
        if (advanced) {
            section("look", t("section.look"), "", List.of(), () -> checks().add(root, "green_tint", true).add(root, "render_mob_from_spawn_egg", true).end());
            leftovers(Set.of("items", "item", "tags", "tag", "value", "limit", "limit_reset", "unlock_tasks", "unlock_mode", "unlock_disks", "unlock_commands", "hidden_until_unlocked",
                    "notify_on_unlock", "green_tint", "render_mob_from_spawn_egg"));
        }
    }

    private void deal() {
        section("price", t("section.discount"), "", List.of("discount_percent", "picks"), () -> {
            JsonElement discount = root.object().get("discount_percent");
            if (discount != null && discount.isJsonPrimitive()) number(root, "discount_percent", 10, 1, 90, true);
            else {
                StudioNode range = root.at("discount_percent");
                pair(() -> number(range, "min", 10, 1, 90, true), () -> number(range, "max", 20, 1, 90, true));
            }
            number(root, "picks", 1, 1, 1000, true);
            checks().add(root, "enabled", true).end();
        });
        section("when", t("section.deal_when"), "", List.of("every_minecraft_days", "every_real_hours"), () -> {
            boolean real = root.has("every_real_hours") && !root.has("every_minecraft_days");
            row(t("field.changes_every"), "", (cx, cw) -> {
                numberField(root, real ? "every_real_hours" : "every_minecraft_days", cx, y, 46, 1, 1, 1_000_000, true);
                int unitX = cx + 52;
                int unitY = y;
                boolean wide = cw - 52 >= kit.width(t("minecraft_days")) + kit.width(t("real_hours")) + 22;
                java.util.function.IntConsumer choose = index -> root.change("", target -> {
                    JsonObject body = target.getAsJsonObject();
                    int amount = StudioJson.integer(body, real ? "every_real_hours" : "every_minecraft_days", 1);
                    body.remove("every_real_hours");
                    body.remove("every_minecraft_days");
                    body.addProperty(index == 1 ? "every_real_hours" : "every_minecraft_days", amount);
                });
                if (wide) kit.segments(unitX, unitY, List.of(t("minecraft_days"), t("real_hours")), real ? 1 : 0, choose);
                else kit.drop(unitX, unitY, cw - 52, t(real ? "real_hours" : "minecraft_days"), null, click -> screen.popup(new StudioMenu(screen, unitX, unitY, 16)
                        .check(t("minecraft_days"), !real, () -> choose.accept(0)).check(t("real_hours"), real, () -> choose.accept(1))));
            });
            choice(root, "order", "shuffled", List.of("shuffled", "sequential"));
        });
        section("candidates", t("section.candidates"), "", List.of("candidates"), () -> {
            StudioNode rules = root.at("candidates");
            refList(rules, "products", "kind:product");
            refList(rules, "categories", "shop_category");
            refList(rules, "tags", "shop_tag");
            if (advanced) {
                refList(rules, "exclude_products", "kind:product");
                refList(rules, "exclude_categories", "shop_category");
                refList(rules, "exclude_tags", "shop_tag");
                pair(() -> number(rules, "min_price", 0, 0, 1_000_000_000, true), () -> number(rules, "max_price", 0, 0, 1_000_000_000, true));
            }
            if (rules.strings("products").isEmpty() && rules.strings("categories").isEmpty() && rules.strings("tags").isEmpty()) y = kit.wrap(t("deal_everything"), x, y, w, StudioTheme.soft) + 2;
        });
        if (advanced) {
            section("more", t("section.more"), "", List.of(), () -> {
                pair(() -> number(root, "max_per_category", 0, 0, 1000, true), () -> number(root, "deal_limit", 0, 0, 1_000_000, true));
                number(root, "priority", 0, -1_000_000, 1_000_000, true);
            });
            leftovers(Set.of("label", "enabled", "picks", "every_minecraft_days", "every_real_hours", "order", "discount_percent", "candidates", "max_per_category", "deal_limit", "priority"));
        }
    }

    private void article() {
        section("who", t("section.who_read"), "", List.of("who", "unlocked_by_default"), () -> {
            List<StudioKey> disks = store.keys(AuthoringKind.DISK).stream().filter(disk -> store.direct(key, disk)).toList();
            List<StudioKey> tasks = rewardingTasks();
            boolean everyone = root.bool("unlocked_by_default", false);
            Flow flow = flow();
            flow.chip(t("who.everyone"), null, everyone, "", click -> root.put("unlocked_by_default", !everyone));
            for (StudioKey disk : disks) flow.chip(store.label(disk), StudioIcons.of(store, disk), true, "", click -> linkMenu(click, disk, () -> store.takeOffDisk(key, disk)));
            for (StudioKey task : tasks) flow.chip(t("who.after", store.label(task)), StudioIcons.of(store, task), true, "", click -> linkMenu(click, task, () -> store.with(List.of(task), () -> store.edit(task, "", body -> {
                JsonArray rewards = StudioJson.list(body, "rewards");
                for (int index = rewards.size() - 1; index >= 0; index--) if (rewards.get(index).isJsonObject() && StudioJson.string(rewards.get(index).getAsJsonObject(), "entry").equals(key.id())) rewards.remove(index);
            }))));
            flow.chip("+ " + t("who.floppy"), null, false, t("who.floppy.tip"), click -> pickRef("kind:disk", click.x(), click.y(), false, value -> store.with(List.of(key(AuthoringKind.DISK, value)), () -> store.transaction(() -> {
                store.putOnDisk(key, key(AuthoringKind.DISK, value));
                root.put("unlocked_by_default", false);
            }))));
            flow.chip("+ " + t("who.after_task"), null, false, t("who.article_task.tip"), click -> pickRef("kind:task", click.x(), click.y(), false, value -> {
                StudioKey task = key(AuthoringKind.TASK, value);
                store.with(List.of(task), () -> store.transaction(() -> {
                    store.edit(task, "", body -> {
                        JsonObject reward = new JsonObject();
                        reward.addProperty("type", "archive");
                        reward.addProperty("entry", key.id());
                        JsonArray rewards = StudioJson.list(body, "rewards");
                        rewards.add(reward);
                        body.add("rewards", rewards);
                    });
                    root.put("unlocked_by_default", false);
                }));
            }));
            flow.end();
        });
        section("page", t("section.page"), t("drag_to_reorder"), List.of("content", "description"), () -> blocks(root.has("content") || !root.has("description") ? "content" : "description", true));
        section("place", t("section.where"), "", List.of("folder", "category"), () -> {
            ref(root, "folder", "kind:archive_folder", true);
            if (advanced) {
                row(t("field.article_category"), t("hint.article_category"), (cx, cw) -> {
                    kit.field(root.id("category"), cx, y, cw - 20, root.string("category", "general"), new StudioKit.Field().max(128), value -> root.put("category", value));
                    kit.button(cx + cw - 17, y, 17, 16, "...", StudioKit.NORMAL, true, t("existing"), () -> pickRef("article_category", cx, y, false, value -> root.put("category", value)));
                });
                row(t("field.subtitle"), "", (cx, cw) -> textField(root, "subtitle", cx, y, cw, t("optional"), 512));
                number(root, "sort_order", 0, -100000, 100000, true);
                optionalText(root, "type", "article", 64);
            }
        });
        if (advanced) {
            section("look", t("section.look"), "", List.of("render_scale", "rotation"), () -> {
                item(root, "cover_item", "cover_entity", t("field.cover"));
                ref(root, "cover_potion", "potion", true);
                item(root, "item", "entity", t("field.subject_item"));
                optionalText(root, "enchantment", "", 128);
                ref(root, "recipe", "recipe", true);
                pair(() -> number(root, "rotation", 0, -360, 360, false), () -> number(root, "render_scale", 1, .1, 3, false));
                checks().add(root, "green_tint", true).end();
            });
            section("gallery", t("section.gallery"), "", List.of("gallery"), () -> {
                optionalText(root, "gallery_title", t("optional"), 256);
                gallery(root.at("gallery"), true);
            });
            section("locate", t("section.locate"), "", List.of("structure", "search_radius"), () -> {
                ref(root, "structure", "structure", true);
                ref(root, "structure_tag", "structure_tag", true);
                ref(root, "dimension", "dimension", true);
                number(root, "search_radius", 0, 0, 1000, true);
                optionalText(root, "locator", "", 128);
            });
            section("lookup", t("section.lookup"), t("lookup_note"), List.of("lookup", "lookup_recipes"), () -> {
                items(root, "lookup", false, true);
                strings(root, "lookup_recipes", "minecraft:smelting");
            });
            leftovers(Set.of("type", "category", "title", "subtitle", "description", "cover_item", "cover_entity", "cover_potion", "green_tint", "unlocked_by_default", "folder", "sort_order", "item",
                    "entity", "enchantment", "recipe", "structure", "structure_tag", "dimension", "search_radius", "locator", "rotation", "render_scale", "gallery", "gallery_title", "lookup",
                    "lookup_recipes", "content"));
        }
    }

    private List<StudioKey> rewardingTasks() {
        List<StudioKey> result = new ArrayList<>();
        for (StudioKey task : store.keys(AuthoringKind.TASK)) for (JsonElement reward : StudioJson.list(store.view(task), "rewards"))
            if (reward.isJsonObject() && StudioJson.string(reward.getAsJsonObject(), "entry").equals(key.id()) && !result.contains(task)) result.add(task);
        return result;
    }

    record Block(String type, String value, boolean beside) { }

    static Block block(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("display")) return new Block("display", StudioJson.string(object, "display"), StudioJson.bool(object, "beside", false));
            if (object.has("recipe")) return new Block("recipe", StudioJson.string(object, "recipe"), false);
            return new Block("text", StudioJson.string(object, "text"), false);
        }
        String value = element.isJsonPrimitive() ? element.getAsString() : "";
        for (String type : List.of("item", "entity", "recipe", "display", "enchantment")) if (value.startsWith("@" + type + ":")) return new Block(type, value.substring(type.length() + 2), false);
        return new Block(value.equals("@beside") ? "beside" : "text", value, false);
    }

    private static JsonElement encodeBlock(boolean objects, String type, String value, boolean beside) {
        if (!objects || Set.of("item", "entity", "enchantment", "beside").contains(type)) return new JsonPrimitive(type.equals("text") || type.equals("beside") ? value : "@" + type + ":" + value);
        JsonObject object = new JsonObject();
        object.addProperty(type, value);
        if (type.equals("display") && beside) object.addProperty("beside", true);
        return object;
    }

    private void blocks(String name, boolean pictures) {
        StudioNode list = root.at(name);
        boolean objects = name.equals("content");
        JsonArray values = list.array();
        for (int index = 0; index < values.size(); index++) {
            int position = index;
            Block block = block(values.get(index));
            boolean wrong = bad(list.field(Integer.toString(index)));
            int top = y;
            if (block.type().equals("text")) {
                String id = list.id(Integer.toString(index));
                boolean wrapped = values.get(index).isJsonObject();
                int height = kit.areaHeight(id, 30, 150);
                if (kit.visible(y, height)) {
                    grip(list, index, top, height, t("block.text"));
                    String raw = block.value();
                    kit.area(id, x + 2, y, w - 18, height, StudioText.read(raw), 8192, t("hint.paragraph"), wrong, value -> {
                        String written = raw.startsWith("literal:") ? "literal:" + value : value;
                        list.set(position, wrapped ? encodeBlock(true, "text", written, false) : new JsonPrimitive(written));
                    });
                    removeButton(x + w - 14, y, () -> list.removeAt(position));
                }
                y += height + 3;
                continue;
            }
            if (kit.visible(y, 24)) {
                grip(list, index, top, 23, t("block." + block.type()));
                kit.box(x + 2, y, w - 18, 23, StudioTheme.button, wrong ? StudioTheme.error : StudioTheme.line);
                kit.text(t("block." + block.type()), x + 8, y + 8, 50, StudioTheme.soft);
                int cx = x + 58;
                int cw = w - 18 - 62;
                switch (block.type()) {
                    case "item", "entity" -> {
                        boolean entity = block.type().equals("entity");
                        StudioIcon icon = block.value().isEmpty() ? null : entity ? StudioIcon.entity(block.value()) : StudioIcon.item(block.value());
                        kit.slot(cx, y + 2, 19, icon, block.value().isEmpty(), block.value(), click -> screen.popup(new StudioPicker(screen, cx, top + 2, 19, picked ->
                                list.set(position, encodeBlock(objects, picked.type() == StudioPicker.Type.ENTITY ? "entity" : "item", picked.id(), false))).items(false, true)));
                        kit.text(block.value().isEmpty() ? t("pick_item_or_mob") : entity ? StudioText.entityName(block.value()) : StudioText.itemName(block.value()), cx + 25, y + 8, cw - 26,
                                block.value().isEmpty() ? StudioTheme.soft : StudioTheme.strong);
                    }
                    case "recipe", "display" -> {
                        int dropY = y + 3;
                        String shown = block.value().isEmpty() ? t("picker.choose") : StudioText.prettyId(block.value());
                        int size = block.type().equals("display") && advanced ? cw - 62 : cw;
                        kit.drop(cx, dropY, size, shown, null, click -> pickRef(block.type(), cx, dropY, false, value -> list.set(position, encodeBlock(objects, block.type(), value, block.beside()))));
                        if (block.type().equals("display") && advanced && objects)
                            kit.check(cx + size + 5, y + 5, t("beside"), block.beside(), value -> list.set(position, encodeBlock(true, "display", block.value(), value)));
                    }
                    default -> kit.field(list.id(Integer.toString(index)), cx, y + 3, cw, block.value(), new StudioKit.Field().max(256), value -> list.set(position, encodeBlock(objects, block.type(), value, false)));
                }
                removeButton(x + w - 14, y + 3, () -> list.removeAt(position));
            }
            y += 26;
        }
        Flow flow = flow();
        flow.chip("+ " + t("block.text"), null, false, "", click -> list.add(encodeBlock(objects, "text", "", false)));
        if (pictures) flow.chip("+ " + t("block.item"), null, false, "", click -> screen.popup(new StudioPicker(screen, click.x(), click.y(), 4, picked ->
                list.add(encodeBlock(objects, picked.type() == StudioPicker.Type.ENTITY ? "entity" : "item", picked.id(), false))).items(false, true)));
        flow.chip("+ " + t("block.recipe"), null, false, "", click -> pickRef("recipe", click.x(), click.y(), false, value -> list.add(encodeBlock(objects, "recipe", value, false))));
        flow.chip("+ " + t("block.display"), null, false, t("block.display.tip"), click -> pickRef("display", click.x(), click.y(), false, value -> list.add(encodeBlock(objects, "display", value, false))));
        flow.end();
    }

    private void folder() {
        section("text", t("section.folder"), "", List.of("icon_item", "parent", "sort_order"), () -> {
            item(root, "icon_item", "", t("field.icon"));
            ref(root, "parent", "kind:archive_folder", true);
            number(root, "sort_order", 0, -100000, 100000, true);
            if (advanced) checks().add(root, "green_tint", true).end();
        });
        section("contains", t("section.articles_inside"), "", List.of(), () -> cards(store.keys(AuthoringKind.ARCHIVE_ENTRY).stream()
                .filter(entry -> StudioJson.string(store.view(entry), "folder").equals(key.id())).toList()));
        if (advanced) leftovers(Set.of("title", "icon_item", "parent", "sort_order", "green_tint"));
    }

    private void command() {
        StudioNode requires = root.at("requires");
        section("who", t("section.who_command"), "", List.of("requires"), () -> {
            List<String> disks = requires.strings("disks");
            List<String> tasks = requires.strings("tasks");
            List<String> commands = requires.strings("commands");
            Flow flow = flow();
            flow.chip(t("who.everyone"), null, disks.isEmpty() && tasks.isEmpty() && commands.isEmpty(), "", click -> requires.change("", target -> {
                for (String name : List.of("disks", "tasks", "commands")) target.getAsJsonObject().add(name, new JsonArray());
            }));
            for (Map.Entry<String, AuthoringKind> entry : List.of(Map.entry("disks", AuthoringKind.DISK), Map.entry("tasks", AuthoringKind.TASK), Map.entry("commands", AuthoringKind.TERMINAL_COMMAND)))
                for (String id : requires.strings(entry.getKey())) flow.chip(store.label(entry.getValue(), id), store.known(entry.getValue(), id) ? StudioIcons.of(store, key(entry.getValue(), id)) : null, true, id,
                        click -> linkMenu(click, key(entry.getValue(), id), () -> requires.change("", target -> StudioJson.remove(target.getAsJsonObject(), entry.getKey(), id))));
            flow.chip("+ " + t("who.floppy"), null, false, "", click -> pickRef("kind:disk", click.x(), click.y(), false, value -> requires.change("", target -> StudioJson.add(target.getAsJsonObject(), "disks", value))));
            flow.chip("+ " + t("who.after_task"), null, false, "", click -> pickRef("kind:task", click.x(), click.y(), false, value -> requires.change("", target -> StudioJson.add(target.getAsJsonObject(), "tasks", value))));
            flow.end();
            if (disks.size() + tasks.size() + commands.size() > 1) choice(requires, "mode", "all", List.of("all", "any"));
            if (advanced) {
                refList(requires, "commands", "kind:terminal_command");
                optionalText(requires, "directory", "/", 256);
            }
        });
        section("output", t("section.output"), "", List.of("output", "locked_output"), () -> {
            outputLines("output", t("output.hint"));
            if (advanced || !root.at("locked_output").array().isEmpty()) {
                kit.text(t("field.locked_output"), x, y + 2, w, StudioTheme.muted);
                y += 13;
                outputLines("locked_output", t("output.locked"));
            }
            if (advanced) {
                choice(root, "style", "output", List.of("output", "highlight", "system", "error"));
                row(t("field.typing_speed"), "", (cx, cw) -> {
                    numberField(root, "typing_speed", cx, y, 46, 40, 5, 400, true);
                    kit.check(cx + 54, y + 1, t("field.typing"), root.bool("typing", false), value -> root.put("typing", value));
                });
                checks().add(root, "clear", false).end();
            }
        });
        section("gets", t("section.command_gives"), "", List.of("rewards", "tasks", "entries", "programs", "files"), () -> {
            rewards(root.at("rewards"), quietRewards(), 0);
            refList(root, "tasks", "kind:task");
            refList(root, "entries", "kind:archive_entry");
            if (advanced) {
                refList(root, "programs", "kind:program");
                refList(root, "subscribe", "kind:message");
                kit.text(t("section.files"), x, y + 2, w, StudioTheme.muted);
                y += 13;
                files(root.at("files"));
                JsonElement wallpaper = root.object().get("wallpaper");
                if (wallpaper == null || wallpaper.isJsonPrimitive()) ref(root, "wallpaper", "wallpaper", true);
                optionalText(root, "open", "", 64);
                optionalText(root, "action", "", 128);
            }
            checks().add(root, "once", true).end();
        });
        section("text", t("section.command_help"), "", List.of("command", "aliases", "arguments"), () -> {
            optionalText(root, "description", t("optional"), 512);
            checks().add(root, "hidden", true).end();
            if (advanced) {
                strings(root, "aliases", "");
                strings(root, "arguments", "");
                optionalText(root, "usage", t("choice.default"), 256);
                optionalText(root, "hint", t("optional"), 512);
                checks().add(root, "case_sensitive", false).add(root, "variadic", false).end();
            }
        });
        if (advanced) leftovers(Set.of("command", "aliases", "arguments", "usage", "description", "hint", "requires", "output", "locked_output", "hidden", "once", "clear", "entries", "tasks", "wallpaper", "files",
                "programs", "subscribe", "rewards", "open", "action", "case_sensitive", "style", "typing", "typing_speed", "variadic"));
    }

    private void outputLines(String name, String placeholder) {
        JsonArray values = root.at(name).array();
        boolean plain = true;
        for (JsonElement value : values) plain &= value.isJsonPrimitive();
        if (plain && !root.object().has(name) || plain && values.size() == root.strings(name).size()) {
            lines(root, name, placeholder);
            return;
        }
        StudioNode list = root.at(name);
        for (int index = 0; index < values.size(); index++) {
            int position = index;
            JsonElement value = values.get(index);
            boolean object = value.isJsonObject();
            String text = object ? StudioJson.string(value.getAsJsonObject(), "text") : value.getAsString();
            grip(list, index, y, 18, text);
            kit.field(list.id(Integer.toString(index)), x + 2, y, w - (advanced && object ? 90 : 18), text, new StudioKit.Field().max(512), next -> {
                if (object) list.at(position).put("text", next);
                else list.set(position, new JsonPrimitive(next));
            });
            if (advanced && object) choiceField(list.at(index), "style", root.string("style", "output"), List.of("output", "highlight", "system", "error"), x + w - 84, y, 66);
            removeButton(x + w - 14, y, () -> list.removeAt(position));
            y += 19;
        }
        add(t("line"), at -> {
            JsonObject line = new JsonObject();
            line.addProperty("text", "");
            list.add(line);
        });
    }
}
