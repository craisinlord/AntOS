package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

final class StudioSentence {
    private StudioSentence() { }

    private static Component hi(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(StudioTheme.accent & 0xFFFFFF)));
    }

    private static MutableComponent t(String key, Object... arguments) { return Component.translatable("antos.studio.say." + key, arguments); }

    private static Component join(List<Component> parts) {
        if (parts.isEmpty()) return Component.empty();
        MutableComponent result = parts.get(0).copy();
        for (int index = 1; index < parts.size(); index++)
            result = index == parts.size() - 1 ? t("and", result, parts.get(index)) : t("comma", result, parts.get(index));
        return result;
    }

    static Component of(StudioStore store, StudioEditors editors, StudioKey key) {
        JsonObject body = store.view(key);
        if (body == null) return Component.empty();
        try {
            return switch (key.kind()) {
                case TASK -> task(store, editors, key, body);
                case DISK -> disk(store, key, body);
                case ARCHIVE_ENTRY -> article(store, key, body);
                case MESSAGE -> message(store, key, body);
                case PRODUCT -> product(store, body);
                case SELL_RULE -> sell(store, body);
                case DEAL_CAMPAIGN -> deal(body);
                case PROGRAM -> program(store, key, body);
                case REWARD_POOL -> t("pool", hi(Integer.toString(StudioJson.list(body, "entries").size())));
                case MAIL_POOL -> t(StudioJson.bool(body, "subscription_only", false) ? "mail_pool_owners" : "mail_pool", hi(StudioJson.integer(body, "max_per_day", 1) + ""),
                        hi(Math.round(StudioJson.number(body, "chance_per_day", 1) * 100) + "%"));
                case CATEGORY, GROUP, DISK_CATEGORY, ARCHIVE_FOLDER -> t(key.kind().key(), hi(Integer.toString(children(store, key))));
                case TERMINAL_COMMAND -> t(StudioJson.list(body, "rewards").isEmpty() ? "command" : "command_reward", hi(StudioJson.string(body, "command")));
            };
        } catch (RuntimeException exception) { return Component.empty(); }
    }

    private static int children(StudioStore store, StudioKey key) {
        int count = 0;
        switch (key.kind()) {
            case CATEGORY -> { for (StudioKey task : store.keys(AuthoringKind.TASK)) if (StudioJson.string(store.view(task), "category", "antos:general").equals(key.id())) count++; }
            case GROUP -> { for (StudioKey category : store.keys(AuthoringKind.CATEGORY)) if (StudioJson.string(store.view(category), "group").equals(key.id())) count++; }
            case ARCHIVE_FOLDER -> { for (StudioKey entry : store.keys(AuthoringKind.ARCHIVE_ENTRY)) if (StudioJson.string(store.view(entry), "folder").equals(key.id())) count++; }
            default -> {
                for (StudioKey disk : store.keys(AuthoringKind.DISK)) {
                    String category = StudioJson.string(store.view(disk), "category", "general");
                    if ((category.contains(":") ? category : disk.namespace() + ":" + category).equals(key.id())) count++;
                }
            }
        }
        return count;
    }

    private static Component task(StudioStore store, StudioEditors editors, StudioKey key, JsonObject body) {
        if (StudioJson.string(body, "type").equals("link") || body.has("link_category") || body.has("link_group")) {
            String category = StudioJson.string(body, "link_category");
            String group = StudioJson.string(body, "link_group");
            return t("link", hi(category.isEmpty() ? store.label(AuthoringKind.GROUP, group) : store.label(AuthoringKind.CATEGORY, category)));
        }
        List<Component> goals = new ArrayList<>();
        JsonArray objectives = StudioJson.list(body, "objectives");
        for (int index = 0; index < Math.min(4, objectives.size()); index++) {
            JsonObject row = objectives.get(index).getAsJsonObject();
            StudioEditors.GoalType type = StudioEditors.goal(row);
            String name = editors.goalName(row);
            if (name.isBlank()) name = StudioText.t("something");
            int count = StudioJson.integer(row, "count", 1);
            if (type == null) goals.add(t("goal.other", hi(name)));
            else if (type.id().equals("game")) goals.add(t("goal.game_" + StudioJson.string(row, "stat_type", "wins"), hi(Integer.toString(count)), hi(name)));
            else goals.add(t("goal." + type.id(), hi(type.counted() && !type.id().equals("floppy") ? count + "x " + name : name)));
        }
        if (objectives.size() > 4) goals.add(t("more", hi(Integer.toString(objectives.size() - 4))));
        Component who = who(store, key, body);
        if (goals.isEmpty()) return t("task_empty", who);
        List<Component> rewards = rewards(store, StudioJson.list(body, "rewards"));
        return rewards.isEmpty() ? t("task_plain", who, join(goals)) : t("task", who, join(goals), join(rewards));
    }

    private static Component who(StudioStore store, StudioKey key, JsonObject body) {
        List<Component> after = new ArrayList<>();
        for (String id : StudioJson.strings(body, "requires")) after.add(hi(store.label(AuthoringKind.TASK, id)));
        List<Component> any = new ArrayList<>();
        for (String id : StudioJson.strings(body, "requires_any")) any.add(hi(store.label(AuthoringKind.TASK, id)));
        if (!after.isEmpty()) return t("who_after", join(after));
        if (!any.isEmpty()) return t(any.size() > 1 ? "who_any" : "who_after", join(any));
        List<Component> disks = new ArrayList<>();
        for (StudioKey disk : store.keys(AuthoringKind.DISK)) if (store.direct(key, disk)) disks.add(hi(store.label(disk)));
        if (StudioJson.string(body, "availability", "disk").equals("automatic")) return t("who_everyone");
        return disks.isEmpty() ? t("who_nobody") : t("who_floppy", join(disks));
    }

    static List<Component> rewards(StudioStore store, JsonArray rewards) {
        List<Component> result = new ArrayList<>();
        for (int index = 0; index < Math.min(4, rewards.size()); index++) result.add(hi(rewardName(store, rewards.get(index).getAsJsonObject())));
        if (rewards.size() > 4) result.add(t("more", hi(Integer.toString(rewards.size() - 4))));
        return result;
    }

    static String rewardName(StudioStore store, JsonObject reward) {
        return switch (StudioJson.string(reward, "type", "item")) {
            case "item" -> StudioJson.integer(reward, "count", 1) + "x " + StudioText.itemName(StudioJson.string(reward, "item"));
            case "antcoins" -> StudioIcon.coins((long) StudioJson.number(reward, "amount", 0));
            case "experience" -> StudioText.t("xp_points", StudioJson.integer(reward, "points", 0));
            case "experience_levels" -> StudioText.t("xp_levels", StudioJson.integer(reward, "levels", 0));
            case "item_pool" -> StudioText.t("from_pool", StudioJson.string(reward, "pool").isEmpty() ? StudioText.t("something") : store.label(AuthoringKind.REWARD_POOL, StudioJson.string(reward, "pool")));
            case "choice" -> StudioText.t("choice_of", StudioJson.list(reward, "options").size());
            case "archive" -> StudioText.t("the_article", StudioJson.string(reward, "entry").isEmpty() ? StudioText.t("something") : store.label(AuthoringKind.ARCHIVE_ENTRY, StudioJson.string(reward, "entry")));
            case "advancement" -> StudioText.prettyId(StudioJson.string(reward, "advancement"));
            case "effect" -> StudioText.effectName(StudioJson.string(reward, "effect"));
            case "mail" -> StudioText.t("a_mail");
            default -> StudioJson.string(reward, "type");
        };
    }

    private static Component disk(StudioStore store, StudioKey key, JsonObject body) {
        int tasks = store.onDisk(key, AuthoringKind.TASK).size();
        int articles = store.onDisk(key, AuthoringKind.ARCHIVE_ENTRY).size();
        int now = 0;
        int later = 0;
        for (StudioKey mail : store.onDisk(key, AuthoringKind.MESSAGE)) if (store.diskTrigger(mail, key)) now++; else later++;
        later += store.onDisk(key, AuthoringKind.MAIL_POOL).size();
        List<Component> unlocks = new ArrayList<>();
        if (tasks > 0) unlocks.add(hi(StudioText.t(tasks == 1 ? "count.task" : "count.tasks", tasks)));
        if (articles > 0) unlocks.add(hi(StudioText.t(articles == 1 ? "count.article" : "count.articles", articles)));
        int programs = StudioJson.list(body, "programs").size();
        if (programs > 0) unlocks.add(hi(StudioText.t(programs == 1 ? "count.program" : "count.programs", programs)));
        int files = StudioJson.list(body, "files").size();
        if (files > 0) unlocks.add(hi(StudioText.t(files == 1 ? "count.file" : "count.files", files)));
        Component first = unlocks.isEmpty() ? now + later == 0 ? t("disk_empty") : t("disk_mail_only") : t("disk", join(unlocks));
        List<Component> mails = new ArrayList<>();
        if (now > 0) mails.add(t("mail_now", hi(Integer.toString(now))));
        if (later > 0) mails.add(t("mail_later", hi(Integer.toString(later))));
        return mails.isEmpty() ? t("stop", first) : t("disk_mail", first, join(mails));
    }

    private static Component article(StudioStore store, StudioKey key, JsonObject body) {
        if (StudioJson.bool(body, "unlocked_by_default", false)) return t("article_everyone");
        List<Component> disks = new ArrayList<>();
        for (StudioKey disk : store.keys(AuthoringKind.DISK)) if (store.direct(key, disk)) disks.add(hi(store.label(disk)));
        for (StudioKey task : store.keys(AuthoringKind.TASK)) for (JsonElement reward : StudioJson.list(store.view(task), "rewards"))
            if (reward.isJsonObject() && StudioJson.string(reward.getAsJsonObject(), "entry").equals(key.id())) disks.add(hi(store.label(task)));
        return disks.isEmpty() ? t("article_nobody") : t("article", join(disks));
    }

    private static Component message(StudioStore store, StudioKey key, JsonObject body) {
        JsonObject trigger = StudioJson.object(body, "trigger");
        String value = StudioJson.string(trigger, "value");
        String some = StudioText.t("something");
        Component when = switch (StudioJson.string(trigger, "type")) {
            case "disk_installed" -> t("when_floppy", hi(value.isEmpty() ? some : store.label(AuthoringKind.DISK, value)));
            case "task_complete" -> t("when_task", hi(value.isEmpty() ? some : store.label(AuthoringKind.TASK, value)));
            case "mail_read" -> t("when_mail", hi(value.isEmpty() ? some : store.label(AuthoringKind.MESSAGE, value)));
            case "advancement" -> t("when_advancement", hi(value.isEmpty() ? some : StudioText.prettyId(value)));
            case "game_record" -> t("when_game_" + StudioJson.string(trigger, "metric", "wins"), hi(StudioText.gameName(value)), hi(Integer.toString(StudioJson.integer(trigger, "count", 1))));
            case "random" -> StudioJson.string(trigger, "pool").isEmpty() ? t("when_random", hi(percent(value))) : t("when_pool", hi(store.label(AuthoringKind.MAIL_POOL, StudioJson.string(trigger, "pool"))));
            default -> t("when_other", hi(StudioJson.string(trigger, "type")));
        };
        List<Component> attached = new ArrayList<>();
        JsonArray items = StudioJson.list(StudioJson.object(body, "delivery"), "items");
        for (int index = 0; index < Math.min(3, items.size()); index++) {
            JsonObject item = items.get(index).getAsJsonObject();
            attached.add(hi(item.has("explorer_map") ? StudioText.t("a_map") : StudioJson.integer(item, "Count", 1) + "x " + StudioText.itemName(StudioJson.string(item, "id"))));
        }
        if (StudioJson.number(body, "antcoins", 0) > 0) attached.add(hi(StudioIcon.coins((long) StudioJson.number(body, "antcoins", 0))));
        boolean once = StudioJson.bool(body, "once", !StudioJson.string(trigger, "type").equals("random"));
        Component sent = t(once ? "sent_once" : "sent", when);
        return attached.isEmpty() ? t("stop", sent) : t("sent_with", sent, join(attached));
    }

    private static String percent(String value) {
        try { return Math.round(Double.parseDouble(value) * 10000) / 100.0 + "%"; }
        catch (NumberFormatException exception) { return "0%"; }
    }

    private static Component unlock(StudioStore store, JsonObject body) {
        List<Component> parts = new ArrayList<>();
        for (String id : StudioJson.strings(body, "unlock_disks")) parts.add(hi(store.label(AuthoringKind.DISK, id)));
        for (String id : StudioJson.strings(body, "unlock_tasks")) parts.add(hi(store.label(AuthoringKind.TASK, id)));
        for (String id : StudioJson.strings(body, "unlock_commands")) parts.add(hi(store.label(AuthoringKind.TERMINAL_COMMAND, id)));
        return parts.isEmpty() ? t("who_everyone") : t(StudioJson.string(body, "unlock_mode", "all").equals("any") ? "who_unlock_any" : "who_unlock", join(parts));
    }

    private static Component product(StudioStore store, JsonObject body) {
        List<Component> gets = new ArrayList<>();
        for (JsonElement element : StudioJson.list(body, "rewards")) if (gets.size() < 3) {
            JsonObject reward = element.getAsJsonObject();
            gets.add(hi(StudioJson.integer(reward, "count", 1) + "x " + StudioText.itemName(StudioJson.string(reward, "item"))));
        }
        if (body.has("variants")) gets.add(hi(StudioText.tag(StudioJson.string(StudioJson.object(body, "variants"), "tag"))));
        if (body.has("item_pool")) gets.add(hi(StudioText.t("a_rotating_item")));
        List<Component> prices = new ArrayList<>();
        for (JsonElement element : StudioJson.list(body, "payments")) if (prices.size() < 3) prices.add(hi(price(element.getAsJsonObject())));
        return t("product", unlock(store, body), gets.isEmpty() ? hi(StudioText.t("nothing")) : join(gets), prices.isEmpty() ? hi(StudioText.t("no_price")) : join(prices));
    }

    static String price(JsonObject payment) {
        long amount = (long) StudioJson.number(payment, "amount", 1);
        if (!StudioJson.string(payment, "type", "antcoins").equals("item")) return StudioIcon.coins(amount);
        String resource = StudioJson.string(payment, "resource");
        return amount + "x " + (resource.startsWith("#") ? StudioText.tag(resource) : StudioText.itemName(resource));
    }

    private static Component sell(StudioStore store, JsonObject body) {
        List<Component> items = new ArrayList<>();
        for (String id : StudioJson.strings(body, "items")) if (items.size() < 4) items.add(hi(id.startsWith("#") ? StudioText.tag(id) : StudioText.itemName(id)));
        for (String id : StudioJson.strings(body, "tags")) if (items.size() < 4) items.add(hi(StudioText.tag(id)));
        if (!StudioJson.string(body, "item").isEmpty()) items.add(hi(StudioText.itemName(StudioJson.string(body, "item"))));
        return t("sell", unlock(store, body), items.isEmpty() ? hi(StudioText.t("nothing")) : join(items), hi(StudioIcon.coins((long) StudioJson.number(body, "value", 1))));
    }

    private static Component deal(JsonObject body) {
        JsonElement discount = body.get("discount_percent");
        String range = discount != null && discount.isJsonObject()
                ? StudioJson.integer(discount.getAsJsonObject(), "min", 10) + "-" + StudioJson.integer(discount.getAsJsonObject(), "max", 20) + "%"
                : StudioJson.integer(body, "discount_percent", 10) + "%";
        boolean real = body.has("every_real_hours") && !body.has("every_minecraft_days");
        int every = StudioJson.integer(body, real ? "every_real_hours" : "every_minecraft_days", 1);
        int picks = StudioJson.integer(body, "picks", 1);
        return t("deal", plural(real ? "every_hours" : "every_days", every), plural("picks", picks), hi(range));
    }

    private static Component program(StudioStore store, StudioKey key, JsonObject body) {
        List<Component> disks = new ArrayList<>();
        for (StudioKey disk : store.keys(AuthoringKind.DISK)) if (store.direct(key, disk)) disks.add(hi(store.label(disk)));
        Component who = disks.isEmpty() ? t("who_nobody") : t("who_floppy", join(disks));
        if (StudioJson.string(body, "type").equals("adventure")) return t("adventure", who, hi(Integer.toString(StudioJson.object(body, "nodes").size())));
        int count = StudioJson.list(body, "questions").size();
        return t("quiz", who, hi(Integer.toString(Math.min(count, StudioJson.integer(body, "questions_per_run", count)))), plural("questions", count));
    }

    private static MutableComponent plural(String key, int count) { return count == 1 ? t(key + ".one", hi("1")) : t(key, hi(Integer.toString(count))); }
}
