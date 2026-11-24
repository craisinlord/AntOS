package com.craisinlord.antos.content.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AuthoringChecks {
    private AuthoringChecks() { }

    public static List<AuthoringIssue> check(AuthoringKind kind, JsonObject body) {
        List<AuthoringIssue> issues = new ArrayList<>();
        try {
            switch (kind) {
                case TASK -> task(body, issues);
                case CATEGORY, GROUP, ARCHIVE_FOLDER -> text(body, "title", issues);
                case REWARD_POOL -> pool(body, issues);
                case DISK -> disk(body, issues);
                case DISK_CATEGORY -> {
                    text(body, "title", issues);
                    String color = string(body, "color").replaceFirst("^#", "");
                    if (!color.isBlank() && !color.matches("[0-9a-fA-F]{6}")) issues.add(AuthoringIssue.error("color", "color", color));
                }
                case PROGRAM -> program(body, issues);
                case MESSAGE -> message(body, issues);
                case MAIL_POOL -> {
                    range(body, "chance_per_day", 0, 1, issues);
                    range(body, "max_per_day", 0, 1_000_000, issues);
                }
                case TAKEOVER -> {
                    if (string(body, "title").isBlank() && list(body, "lines").isEmpty()) issues.add(AuthoringIssue.error("takeover_empty", "title"));
                }
                case PRODUCT -> product(body, issues);
                case SELL_RULE -> {
                    range(body, "value", 1, 1_000_000_000, issues);
                    if (!body.has("value")) issues.add(AuthoringIssue.error("value_missing", "value"));
                    if (list(body, "items").isEmpty() && list(body, "tags").isEmpty() && string(body, "item").isBlank() && string(body, "tag").isBlank())
                        issues.add(AuthoringIssue.error("sell_items", "items"));
                }
                case DEAL_CAMPAIGN -> deal(body, issues);
                case ARCHIVE_ENTRY -> article(body, issues);
                case TERMINAL_COMMAND -> command(body, issues);
            }
            AuthoringValidation.validate(kind, body);
        } catch (RuntimeException exception) {
            if (issues.stream().allMatch(AuthoringIssue::warning)) issues.add(AuthoringIssue.error("invalid", "", clean(exception)));
        }
        return issues;
    }

    public static String clean(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "This setting could not be read";
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private static void task(JsonObject body, List<AuthoringIssue> issues) {
        if (string(body, "type").equals("link") || body.has("link_category") || body.has("link_group")) {
            if (string(body, "link_category").isBlank() && string(body, "link_group").isBlank()) issues.add(AuthoringIssue.error("link_target", "link_category"));
            return;
        }
        text(body, "title", issues);
        Set<String> ids = new HashSet<>();
        JsonArray objectives = list(body, "objectives");
        for (int index = 0; index < objectives.size(); index++) {
            JsonObject row = objectives.get(index).getAsJsonObject();
            String path = "objectives/" + index;
            String id = string(row, "id");
            if (id.isBlank()) issues.add(AuthoringIssue.error("goal_id", path + "/id"));
            else if (!ids.add(id)) issues.add(AuthoringIssue.error("goal_duplicate", path + "/id", id));
            if (string(row, "target").isBlank() && string(row, "target_category").isBlank()) issues.add(AuthoringIssue.error("goal_target", path + "/target"));
            range(row, "count", 1, 1_000_000, path + "/", issues);
        }
        if (objectives.isEmpty()) issues.add(AuthoringIssue.warning("task_no_goals", "objectives"));
        rewards(list(body, "rewards"), "rewards", issues, 0);
        range(body, "size", .5, 3, issues);
        range(body, "icon_scale", .25, 2, issues);
        range(body, "line_bend", 0, 1, issues);
        range(body, "sort_order", -100_000, 100_000, issues);
    }

    private static void rewards(JsonArray rewards, String path, List<AuthoringIssue> issues, int depth) {
        for (int index = 0; index < rewards.size(); index++) {
            JsonObject reward = rewards.get(index).getAsJsonObject();
            String at = path + "/" + index;
            switch (string(reward, "type")) {
                case "item" -> need(reward, "item", at, issues);
                case "item_pool" -> need(reward, "pool", at, issues);
                case "archive" -> need(reward, "entry", at, issues);
                case "advancement" -> need(reward, "advancement", at, issues);
                case "effect" -> need(reward, "effect", at, issues);
                case "wallpaper" -> need(reward, "id", at, issues);
                case "mail" -> {
                    if (string(reward, "message").isBlank() && string(reward, "subject").isBlank()) issues.add(AuthoringIssue.error("reward_target", at + "/subject"));
                }
                case "choice" -> {
                    JsonArray options = list(reward, "options");
                    if (options.size() < 2) issues.add(AuthoringIssue.error("choice_options", at + "/options"));
                    for (int option = 0; option < options.size(); option++) {
                        JsonArray nested = list(options.get(option).getAsJsonObject(), "rewards");
                        if (nested.isEmpty()) issues.add(AuthoringIssue.error("choice_reward", at + "/options/" + option));
                        if (depth == 0) rewards(nested, at + "/options/" + option + "/rewards", issues, 1);
                    }
                }
                case "" -> issues.add(AuthoringIssue.error("reward_type", at + "/type"));
                default -> { }
            }
            range(reward, "count", 1, 108, at + "/", issues);
            range(reward, "points", 1, 1_000_000, at + "/", issues);
            range(reward, "levels", 1, 1000, at + "/", issues);
            range(reward, "amount", 1, 1_000_000_000, at + "/", issues);
            range(reward, "duration_ticks", 1, 72_000, at + "/", issues);
            range(reward, "amplifier", 0, 255, at + "/", issues);
        }
    }

    private static void need(JsonObject reward, String key, String path, List<AuthoringIssue> issues) {
        if (string(reward, key).isBlank()) issues.add(AuthoringIssue.error("reward_target", path + "/" + key));
    }

    private static void pool(JsonObject body, List<AuthoringIssue> issues) {
        JsonArray entries = list(body, "entries");
        if (entries.isEmpty()) issues.add(AuthoringIssue.error("pool_empty", "entries"));
        for (int index = 0; index < entries.size(); index++) {
            JsonObject row = entries.get(index).getAsJsonObject();
            if (string(row, "item").isBlank() == string(row, "pool").isBlank()) issues.add(AuthoringIssue.error("pool_entry", "entries/" + index));
            range(row, "weight", 1, 1_000_000, "entries/" + index + "/", issues);
            range(row, "count", 1, 108, "entries/" + index + "/", issues);
        }
    }

    private static void disk(JsonObject body, List<AuthoringIssue> issues) {
        text(body, "title", issues);
        JsonArray files = list(body, "files");
        for (int index = 0; index < files.size(); index++) {
            JsonObject file = files.get(index).getAsJsonObject();
            if (string(file, "path").isBlank()) issues.add(AuthoringIssue.error("file_name", "files/" + index + "/path"));
            if (string(file, "source").isBlank()) issues.add(AuthoringIssue.error("file_source", "files/" + index + "/source"));
        }
        rewards(list(body, "rewards"), "rewards", issues, 0);
        if (body.has("encryption") && body.get("encryption").isJsonObject()) {
            JsonObject lock = body.getAsJsonObject("encryption");
            if (string(lock, "password").isBlank() && list(lock, "password_any").isEmpty()) issues.add(AuthoringIssue.error("password_empty", "encryption/password"));
        }
    }

    private static void program(JsonObject body, List<AuthoringIssue> issues) {
        text(body, "title", issues);
        if (string(body, "type").equals("adventure")) {
            JsonObject nodes = object(body, "nodes");
            if (nodes.size() == 0) issues.add(AuthoringIssue.error("adventure_empty", "nodes"));
            else if (!nodes.has(string(body, "start"))) issues.add(AuthoringIssue.error("adventure_start", "start"));
            for (var entry : nodes.entrySet()) {
                JsonObject node = entry.getValue().getAsJsonObject();
                String at = "nodes/" + entry.getKey();
                JsonArray choices = list(node, "choices");
                if (choices.isEmpty() && string(node, "ending").isBlank()) issues.add(AuthoringIssue.error("page_stuck", at, entry.getKey()));
                if (!choices.isEmpty() && !string(node, "ending").isBlank()) issues.add(AuthoringIssue.error("page_both", at, entry.getKey()));
                for (int index = 0; index < choices.size(); index++) {
                    String destination = string(choices.get(index).getAsJsonObject(), "goto");
                    if (!nodes.has(destination)) issues.add(AuthoringIssue.error("choice_destination", at + "/choices/" + index, destination));
                }
            }
            return;
        }
        JsonArray questions = list(body, "questions");
        if (questions.isEmpty()) issues.add(AuthoringIssue.error("quiz_empty", "questions"));
        for (int index = 0; index < questions.size(); index++) {
            JsonObject question = questions.get(index).getAsJsonObject();
            String at = "questions/" + index;
            if (string(question, "question").isBlank()) issues.add(AuthoringIssue.error("question_text", at + "/question"));
            JsonArray answers = list(question, "answers");
            if (answers.size() < 2) issues.add(AuthoringIssue.error("question_answers", at + "/answers"));
            int correct = question.has("correct") ? question.get("correct").getAsInt() : -1;
            if (correct < 0 || correct >= answers.size()) issues.add(AuthoringIssue.error("question_correct", at + "/correct"));
        }
        rewards(list(body, "rewards"), "rewards", issues, 0);
    }

    private static void message(JsonObject body, List<AuthoringIssue> issues) {
        text(body, "subject", issues);
        if (string(body, "subject").length() > 64) issues.add(AuthoringIssue.error("too_long", "subject", "64"));
        if (string(body, "body").length() > 4096) issues.add(AuthoringIssue.error("too_long", "body", "4096"));
        if (string(body, "sender").isBlank()) issues.add(AuthoringIssue.error("mail_sender", "sender"));
        JsonObject trigger = object(body, "trigger");
        String type = string(trigger, "type");
        String value = string(trigger, "value");
        if (type.isBlank()) issues.add(AuthoringIssue.error("mail_trigger", "trigger"));
        else if (type.equals("random")) {
            if (string(trigger, "pool").isBlank()) {
                double chance = -1;
                try { chance = Double.parseDouble(value); } catch (NumberFormatException ignored) { }
                if (!Double.isFinite(chance) || chance < 0 || chance > 1) issues.add(AuthoringIssue.error("mail_chance", "trigger/value"));
            }
        } else if (value.isBlank() && !type.contains(":") && !trigger.has("day")) issues.add(AuthoringIssue.error("mail_target", "trigger/value"));
        JsonArray items = list(object(body, "delivery"), "items");
        for (int index = 0; index < items.size(); index++) {
            JsonObject item = items.get(index).getAsJsonObject();
            if (string(item, "id").isBlank() && !item.has("explorer_map")) issues.add(AuthoringIssue.error("mail_item", "delivery/items/" + index));
        }
        range(body, "antcoins", 0, 1_000_000_000, issues);
    }

    private static void product(JsonObject body, List<AuthoringIssue> issues) {
        text(body, "name", issues);
        JsonArray payments = list(body, "payments");
        if (payments.isEmpty()) issues.add(AuthoringIssue.error("product_price", "payments"));
        for (int index = 0; index < payments.size(); index++) {
            JsonObject payment = payments.get(index).getAsJsonObject();
            String at = "payments/" + index + "/";
            if (string(payment, "type").equals("item") && string(payment, "resource").isBlank()) issues.add(AuthoringIssue.error("price_item", at + "resource"));
            range(payment, "amount", 1, 1_000_000_000, at, issues);
        }
        JsonArray rewards = list(body, "rewards");
        if (rewards.isEmpty() && !body.has("item_pool") && !body.has("variants")) issues.add(AuthoringIssue.error("product_contents", "rewards"));
        if (body.has("item_pool") && body.has("variants")) issues.add(AuthoringIssue.error("product_both", "variants"));
        long total = 0;
        for (int index = 0; index < rewards.size(); index++) {
            JsonObject reward = rewards.get(index).getAsJsonObject();
            if (string(reward, "item").isBlank()) issues.add(AuthoringIssue.error("reward_target", "rewards/" + index + "/item"));
            range(reward, "count", 1, 108, "rewards/" + index + "/", issues);
            total += reward.has("count") ? reward.get("count").getAsLong() : 1;
        }
        if (total > 108) issues.add(AuthoringIssue.error("product_total", "rewards", Long.toString(total)));
        range(body, "quantity", 1, 1_000_000, issues);
        if (string(body, "delivery").equals("falling_chest_location")) {
            JsonObject location = object(body, "delivery_location");
            if (!location.has("x") || !location.has("y") || !location.has("z")) issues.add(AuthoringIssue.error("product_location", "delivery_location"));
        }
    }

    private static void deal(JsonObject body, List<AuthoringIssue> issues) {
        text(body, "label", issues);
        range(body, "picks", 1, 1000, issues);
        if (body.has("every_minecraft_days") && body.has("every_real_hours")) issues.add(AuthoringIssue.error("deal_cadence", "every_real_hours"));
        JsonElement discount = body.get("discount_percent");
        if (discount == null || !discount.isJsonObject()) return;
        JsonObject object = discount.getAsJsonObject();
        range(object, "min", 1, 90, "discount_percent/", issues);
        range(object, "max", 1, 90, "discount_percent/", issues);
        if (object.has("min") && object.has("max") && object.get("min").getAsDouble() > object.get("max").getAsDouble())
            issues.add(AuthoringIssue.error("deal_order", "discount_percent/max"));
    }

    private static void article(JsonObject body, List<AuthoringIssue> issues) {
        text(body, "title", issues);
        boolean written = false;
        JsonArray content = list(body, "content");
        for (int index = 0; index < content.size(); index++) {
            JsonElement block = content.get(index);
            if (!block.isJsonObject()) {
                String value = block.getAsString();
                boolean marker = value.startsWith("@") && value.endsWith(":");
                if (marker) issues.add(AuthoringIssue.error("block_empty", "content/" + index));
                written |= !value.isBlank() && !marker;
                continue;
            }
            JsonObject object = block.getAsJsonObject();
            boolean filled = !string(object, "text").isBlank() || !string(object, "display").isBlank() || !string(object, "recipe").isBlank();
            written |= filled;
            if (!filled && !object.has("text")) issues.add(AuthoringIssue.error("block_empty", "content/" + index));
        }
        for (JsonElement line : list(body, "description")) written |= line.isJsonPrimitive() && !line.getAsString().isBlank();
        if (!written && list(body, "gallery").isEmpty() && string(body, "item").isBlank() && string(body, "entity").isBlank())
            issues.add(AuthoringIssue.warning("article_empty", "content"));
        range(body, "render_scale", .1, 3, issues);
        range(body, "rotation", -360, 360, issues);
        range(body, "search_radius", 0, 1000, issues);
        range(body, "sort_order", -100_000, 100_000, issues);
    }

    private static void command(JsonObject body, List<AuthoringIssue> issues) {
        String name = string(body, "command");
        if (name.isBlank()) issues.add(AuthoringIssue.error("command_name", "command"));
        else if (!name.matches("[a-z0-9._-]{1,32}")) issues.add(AuthoringIssue.error("command_letters", "command", name));
        rewards(list(body, "rewards"), "rewards", issues, 0);
    }

    private static void text(JsonObject body, String key, List<AuthoringIssue> issues) {
        String value = string(body, key);
        if (value.isBlank() || value.equals("literal:")) issues.add(AuthoringIssue.error("title_missing", key));
        else if (value.length() > 512) issues.add(AuthoringIssue.error("too_long", key, "512"));
    }

    private static void range(JsonObject body, String key, double minimum, double maximum, List<AuthoringIssue> issues) {
        range(body, key, minimum, maximum, "", issues);
    }

    private static void range(JsonObject body, String key, double minimum, double maximum, String path, List<AuthoringIssue> issues) {
        JsonElement value = body.get(key);
        if (value == null) return;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            issues.add(AuthoringIssue.error("number", path + key));
            return;
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < minimum || number > maximum)
            issues.add(AuthoringIssue.error("range", path + key, trim(minimum), trim(maximum)));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    public static JsonArray list(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    public static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
}
