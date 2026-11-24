package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringChecks;
import com.craisinlord.antos.content.editor.AuthoringIssue;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class StudioProblems {
    record Problem(StudioKey key, AuthoringIssue issue, String fixLabel, Runnable fix) {
        String text() { return StudioProblems.text(issue); }
    }

    private StudioProblems() { }

    static String text(AuthoringIssue issue) {
        Object[] arguments = issue.arguments().stream().map(StudioProblems::argument).toArray();
        if (StudioText.has("issue." + issue.code())) return StudioText.t("issue." + issue.code(), arguments);
        return issue.arguments().isEmpty() ? issue.code() : issue.arguments().get(0);
    }

    private static Object argument(String value) { return value; }

    static List<Problem> check(StudioStore store, StudioStore.Doc doc) {
        List<AuthoringIssue> issues = new ArrayList<>();
        if (doc.conflict) issues.add(AuthoringIssue.error("changed_elsewhere", "", doc.key.id()));
        if (!doc.removed) {
            if (doc.key.id().isBlank() || net.minecraft.resources.ResourceLocation.tryParse(doc.key.id()) == null) issues.add(AuthoringIssue.error("bad_id", "", doc.key.id()));
            issues.addAll(AuthoringChecks.check(doc.key.kind(), doc.body));
            try { references(store, doc, issues); }
            catch (RuntimeException exception) {
                if (issues.stream().allMatch(AuthoringIssue::warning)) issues.add(AuthoringIssue.error("invalid", "", AuthoringChecks.clean(exception)));
            }
        }
        for (AuthoringIssue issue : doc.serverIssues)
            if (issues.stream().noneMatch(existing -> existing.code().equals(issue.code()) && existing.field().equals(issue.field()))) issues.add(issue);
        List<Problem> result = new ArrayList<>();
        for (AuthoringIssue issue : issues) result.add(fixable(store, doc, issue));
        result.sort(java.util.Comparator.comparing(problem -> problem.issue().warning()));
        return List.copyOf(result);
    }

    private static void references(StudioStore store, StudioStore.Doc doc, List<AuthoringIssue> issues) {
        JsonObject body = doc.body;
        switch (doc.key.kind()) {
            case TASK -> {
                for (String field : List.of("requires", "requires_any")) for (String id : StudioJson.strings(body, field)) {
                    if (id.equals(doc.key.id())) issues.add(AuthoringIssue.error("needs_itself", field));
                    else need(store, AuthoringKind.TASK, id, field, issues);
                }
                if (loops(store, doc.key)) issues.add(AuthoringIssue.error("needs_loop", "requires"));
                for (String id : StudioJson.strings(body, "connects")) need(store, AuthoringKind.TASK, id, "connects", issues);
                for (String id : StudioJson.strings(body, "archive_entries")) need(store, AuthoringKind.ARCHIVE_ENTRY, id, "archive_entries", issues);
                JsonArray objectives = StudioJson.list(body, "objectives");
                for (int index = 0; index < objectives.size(); index++) {
                    JsonObject row = objectives.get(index).getAsJsonObject();
                    String target = StudioJson.string(row, "target");
                    String path = "objectives/" + index + "/target";
                    if (target.isBlank()) continue;
                    switch (StudioJson.string(row, "type")) {
                        case "item" -> item(target, path, issues);
                        case "disk_installed" -> need(store, AuthoringKind.DISK, target, path, issues);
                        case "program_ending" -> need(store, AuthoringKind.PROGRAM, target, path, issues);
                        case "terminal_command" -> need(store, AuthoringKind.TERMINAL_COMMAND, target, path, issues);
                        case "mail_read" -> need(store, AuthoringKind.MESSAGE, target, path, issues);
                        case "archive_viewed" -> need(store, AuthoringKind.ARCHIVE_ENTRY, target, path, issues);
                        default -> { }
                    }
                }
                rewards(store, StudioJson.list(body, "rewards"), "rewards", issues);
                if (!StudioJson.string(body, "type").equals("link") && StudioJson.string(body, "availability", "disk").equals("disk")
                        && StudioJson.list(body, "requires").isEmpty() && StudioJson.list(body, "requires_any").isEmpty()
                        && store.disksOf(doc.key).isEmpty() && !granted(store, doc.key)) issues.add(AuthoringIssue.warning("task_hidden", "who"));
            }
            case ARCHIVE_ENTRY -> {
                String folder = StudioJson.string(body, "folder");
                if (!folder.isBlank()) need(store, AuthoringKind.ARCHIVE_FOLDER, folder, "folder", issues);
                if (!StudioJson.bool(body, "unlocked_by_default", false) && store.disksOf(doc.key).isEmpty() && !granted(store, doc.key)
                        && !rewarded(store, doc.key.id())) issues.add(AuthoringIssue.warning("article_hidden", "who"));
            }
            case ARCHIVE_FOLDER -> {
                String parent = StudioJson.string(body, "parent");
                if (!parent.isBlank()) need(store, AuthoringKind.ARCHIVE_FOLDER, parent, "parent", issues);
            }
            case DISK -> {
                for (String id : StudioJson.strings(body, "tasks")) warn(store, AuthoringKind.TASK, id, "tasks", issues);
                for (String id : StudioJson.strings(body, "entries")) warn(store, AuthoringKind.ARCHIVE_ENTRY, id, "entries", issues);
                for (String id : StudioJson.strings(body, "programs")) warn(store, AuthoringKind.PROGRAM, id, "programs", issues);
                for (String id : StudioJson.strings(body, "subscribe"))
                    if (!store.known(AuthoringKind.MESSAGE, id) && !store.known(AuthoringKind.MAIL_POOL, id)) issues.add(AuthoringIssue.warning("reference_gone", "subscribe", id));
                String category = StudioJson.string(body, "category");
                if (!category.isBlank() && !store.known(AuthoringKind.DISK_CATEGORY, category.contains(":") ? category : doc.key.namespace() + ":" + category))
                    issues.add(AuthoringIssue.warning("reference_gone", "category", category));
                rewards(store, StudioJson.list(body, "rewards"), "rewards", issues);
            }
            case MESSAGE -> {
                JsonObject trigger = StudioJson.object(body, "trigger");
                String value = StudioJson.string(trigger, "value");
                if (!value.isBlank()) switch (StudioJson.string(trigger, "type")) {
                    case "task_complete" -> need(store, AuthoringKind.TASK, value, "trigger/value", issues);
                    case "mail_read" -> need(store, AuthoringKind.MESSAGE, value, "trigger/value", issues);
                    case "disk_installed" -> need(store, AuthoringKind.DISK, value, "trigger/value", issues);
                    default -> { }
                }
                String pool = StudioJson.string(trigger, "pool");
                if (!pool.isBlank()) need(store, AuthoringKind.MAIL_POOL, pool.contains(":") ? pool : doc.key.namespace() + ":" + pool, "trigger/pool", issues);
                String takeover = StudioJson.string(body, "takeover");
                if (!takeover.isBlank()) need(store, AuthoringKind.TAKEOVER, takeover, "takeover", issues);
                JsonObject conditions = StudioJson.object(body, "conditions");
                for (String id : StudioJson.strings(conditions, "mail_read")) need(store, AuthoringKind.MESSAGE, id, "conditions/mail_read", issues);
                for (String id : StudioJson.strings(conditions, "tasks_complete")) need(store, AuthoringKind.TASK, id, "conditions/tasks_complete", issues);
                for (JsonElement element : StudioJson.list(StudioJson.object(body, "delivery"), "items"))
                    if (element.isJsonObject() && !StudioJson.string(element.getAsJsonObject(), "id").isBlank()) item(StudioJson.string(element.getAsJsonObject(), "id"), "delivery/items", issues);
                if (StudioJson.string(trigger, "type").equals("random") && StudioJson.bool(trigger, "subscription_only", false)
                        && store.keys(AuthoringKind.DISK).stream().noneMatch(disk -> StudioJson.contains(store.view(disk), "subscribe", doc.key.id()))
                        && pool.isBlank()) issues.add(AuthoringIssue.warning("mail_unsubscribed", "trigger"));
            }
            case PRODUCT, SELL_RULE -> {
                for (String id : StudioJson.strings(body, "unlock_tasks")) warn(store, AuthoringKind.TASK, id, "unlock_tasks", issues);
                for (String id : StudioJson.strings(body, "unlock_disks")) warn(store, AuthoringKind.DISK, id, "unlock_disks", issues);
                for (String id : StudioJson.strings(body, "unlock_commands")) warn(store, AuthoringKind.TERMINAL_COMMAND, id, "unlock_commands", issues);
                if (doc.key.kind() == AuthoringKind.PRODUCT) {
                    JsonArray rewards = StudioJson.list(body, "rewards");
                    for (int index = 0; index < rewards.size(); index++) {
                        String id = StudioJson.string(rewards.get(index).getAsJsonObject(), "item");
                        if (!id.isBlank()) item(id, "rewards/" + index + "/item", issues);
                    }
                } else for (String id : StudioJson.strings(body, "items")) if (!id.startsWith("#")) item(id, "items", issues);
            }
            case REWARD_POOL -> {
                JsonArray entries = StudioJson.list(body, "entries");
                for (int index = 0; index < entries.size(); index++) {
                    JsonObject row = entries.get(index).getAsJsonObject();
                    if (!StudioJson.string(row, "item").isBlank()) item(StudioJson.string(row, "item"), "entries/" + index, issues);
                    if (!StudioJson.string(row, "pool").isBlank()) need(store, AuthoringKind.REWARD_POOL, StudioJson.string(row, "pool"), "entries/" + index, issues);
                }
            }
            case TERMINAL_COMMAND -> {
                for (String id : StudioJson.strings(body, "tasks")) warn(store, AuthoringKind.TASK, id, "tasks", issues);
                for (String id : StudioJson.strings(body, "entries")) warn(store, AuthoringKind.ARCHIVE_ENTRY, id, "entries", issues);
                for (String id : StudioJson.strings(body, "programs")) warn(store, AuthoringKind.PROGRAM, id, "programs", issues);
                rewards(store, StudioJson.list(body, "rewards"), "rewards", issues);
            }
            case PROGRAM -> rewards(store, StudioJson.list(body, "rewards"), "rewards", issues);
            case DEAL_CAMPAIGN -> {
                for (String id : StudioJson.strings(StudioJson.object(body, "candidates"), "products")) warn(store, AuthoringKind.PRODUCT, id, "candidates/products", issues);
            }
            default -> { }
        }
    }

    private static void rewards(StudioStore store, JsonArray rewards, String path, List<AuthoringIssue> issues) {
        for (int index = 0; index < rewards.size(); index++) {
            JsonObject reward = rewards.get(index).getAsJsonObject();
            String at = path + "/" + index + "/";
            switch (StudioJson.string(reward, "type")) {
                case "item" -> {
                    if (!StudioJson.string(reward, "item").isBlank()) item(StudioJson.string(reward, "item"), at + "item", issues);
                }
                case "item_pool" -> {
                    if (!StudioJson.string(reward, "pool").isBlank()) need(store, AuthoringKind.REWARD_POOL, StudioJson.string(reward, "pool"), at + "pool", issues);
                }
                case "archive" -> {
                    if (!StudioJson.string(reward, "entry").isBlank()) need(store, AuthoringKind.ARCHIVE_ENTRY, StudioJson.string(reward, "entry"), at + "entry", issues);
                }
                case "mail" -> {
                    if (!StudioJson.string(reward, "message").isBlank()) need(store, AuthoringKind.MESSAGE, StudioJson.string(reward, "message"), at + "message", issues);
                }
                default -> { }
            }
        }
    }

    private static boolean granted(StudioStore store, StudioKey key) {
        String field = StudioStore.diskField(key.kind());
        for (StudioKey command : store.keys(AuthoringKind.TERMINAL_COMMAND)) if (StudioJson.contains(store.view(command), field, key.id())) return true;
        return false;
    }

    private static boolean rewarded(StudioStore store, String entry) {
        for (StudioKey task : store.keys(AuthoringKind.TASK)) for (JsonElement reward : StudioJson.list(store.view(task), "rewards"))
            if (reward.isJsonObject() && StudioJson.string(reward.getAsJsonObject(), "entry").equals(entry)) return true;
        return false;
    }

    private static boolean loops(StudioStore store, StudioKey start) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> open = new ArrayList<>(StudioJson.strings(store.view(start), "requires"));
        open.addAll(StudioJson.strings(store.view(start), "requires_any"));
        while (!open.isEmpty() && seen.size() < 2048) {
            String id = open.remove(open.size() - 1);
            if (id.equals(start.id())) return !StudioJson.contains(store.view(start), "requires", id) && !StudioJson.contains(store.view(start), "requires_any", id);
            if (!seen.add(id)) continue;
            JsonObject body = store.view(new StudioKey(AuthoringKind.TASK, id));
            open.addAll(StudioJson.strings(body, "requires"));
            open.addAll(StudioJson.strings(body, "requires_any"));
        }
        return false;
    }

    private static void need(StudioStore store, AuthoringKind kind, String id, String field, List<AuthoringIssue> issues) {
        if (!store.known(kind, id)) issues.add(AuthoringIssue.error("reference_missing", field, id, StudioText.kind(kind)));
    }

    private static void warn(StudioStore store, AuthoringKind kind, String id, String field, List<AuthoringIssue> issues) {
        if (!store.known(kind, id)) issues.add(AuthoringIssue.warning("reference_gone", field, id));
    }

    private static void item(String id, String field, List<AuthoringIssue> issues) {
        if (StudioIcon.stack(id).isEmpty()) issues.add(AuthoringIssue.error("unknown_item", field, id));
    }

    static String commonSender(StudioStore store) {
        Map<String, Integer> counts = new HashMap<>();
        for (StudioKey key : store.keys(AuthoringKind.MESSAGE)) {
            String sender = StudioJson.string(store.view(key), "sender");
            if (!sender.isBlank()) counts.merge(sender, 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).orElse("fieldoffice");
    }

    static String senderName(StudioStore store, String sender) {
        for (StudioKey key : store.keys(AuthoringKind.MESSAGE)) if (StudioJson.string(store.view(key), "sender").equals(sender)) {
            String shown = StudioJson.string(store.view(key), "sender_display");
            if (!shown.isBlank()) return shown;
        }
        return sender.equals("fieldoffice") ? "Field Office" : sender;
    }

    private static Problem fixable(StudioStore store, StudioStore.Doc doc, AuthoringIssue issue) {
        StudioKey key = doc.key;
        String label = "";
        Runnable fix = null;
        switch (issue.code()) {
            case "mail_sender" -> {
                String sender = commonSender(store);
                label = StudioText.t("fix.use", senderName(store, sender));
                fix = () -> store.edit(key, "", body -> {
                    body.addProperty("sender", sender);
                    if (StudioJson.string(body, "sender_display").isBlank()) body.addProperty("sender_display", senderName(store, sender));
                });
            }
            case "goal_duplicate", "goal_id" -> {
                label = StudioText.t("fix.new_id");
                fix = () -> store.edit(key, "", body -> row(body, issue.field()).addProperty("id", goalId()));
            }
            case "changed_elsewhere" -> {
                label = StudioText.t("fix.keep_mine");
                fix = () -> store.keepMine(key);
            }
            case "id_taken" -> {
                label = StudioText.t("fix.new_id");
                fix = () -> store.rename(key, store.freeId(key.kind(), key.namespace(), StudioText.label(key, doc.body) + " " + UUID.randomUUID().toString().substring(0, 4)));
            }
            case "product_price" -> {
                label = StudioText.t("fix.price");
                fix = () -> store.edit(key, "", body -> {
                    JsonObject payment = new JsonObject();
                    payment.addProperty("type", "antcoins");
                    payment.addProperty("resource", "antos:antcoins");
                    payment.addProperty("amount", 10);
                    JsonArray payments = new JsonArray();
                    payments.add(payment);
                    body.add("payments", payments);
                });
            }
            case "deal_cadence" -> {
                label = StudioText.t("fix.deal_days");
                fix = () -> store.edit(key, "", body -> body.remove("every_real_hours"));
            }
            case "deal_order" -> {
                label = StudioText.t("fix.swap");
                fix = () -> store.edit(key, "", body -> {
                    JsonObject discount = body.getAsJsonObject("discount_percent");
                    JsonElement low = discount.get("max");
                    discount.add("max", discount.get("min"));
                    discount.add("min", low);
                });
            }
            case "product_both" -> {
                label = StudioText.t("fix.remove_variants");
                fix = () -> store.edit(key, "", body -> body.remove("variants"));
            }
            case "password_empty" -> {
                label = StudioText.t("fix.unlock");
                fix = () -> store.edit(key, "", body -> body.remove("encryption"));
            }
            case "mail_chance" -> {
                label = StudioText.t("fix.chance");
                fix = () -> store.edit(key, "", body -> body.getAsJsonObject("trigger").addProperty("value", "0.05"));
            }
            case "task_hidden" -> {
                label = StudioText.t("fix.everyone");
                fix = () -> store.edit(key, "", body -> body.addProperty("availability", "automatic"));
            }
            case "article_hidden" -> {
                label = StudioText.t("fix.everyone");
                fix = () -> store.edit(key, "", body -> body.addProperty("unlocked_by_default", true));
            }
            case "needs_itself" -> {
                label = StudioText.t("fix.remove_link");
                fix = () -> store.edit(key, "", body -> StudioJson.remove(body, issue.field(), key.id()));
            }
            case "reference_missing", "reference_gone", "missing_reference" -> {
                if (Set.of("requires", "requires_any", "connects", "archive_entries", "tasks", "entries", "programs", "subscribe", "unlock_tasks", "unlock_disks", "unlock_commands").contains(issue.field())) {
                    label = StudioText.t("fix.remove_link");
                    fix = () -> store.edit(key, "", body -> StudioJson.remove(body, issue.field(), issue.arguments().get(0)));
                } else if (Set.of("folder", "parent", "takeover").contains(issue.field())) {
                    label = StudioText.t("fix.clear");
                    fix = () -> store.edit(key, "", body -> body.remove(issue.field()));
                }
            }
            case "command_letters" -> {
                String clean = StudioText.slug(issue.arguments().get(0));
                if (!clean.isEmpty()) {
                    String value = clean.length() > 32 ? clean.substring(0, 32) : clean;
                    label = StudioText.t("fix.use", value);
                    fix = () -> store.edit(key, "", body -> body.addProperty("command", value));
                }
            }
            case "question_correct" -> {
                label = StudioText.t("fix.first_answer");
                fix = () -> store.edit(key, "", body -> row(body, issue.field()).addProperty("correct", 0));
            }
            case "block_empty" -> {
                label = StudioText.t("fix.remove_block");
                fix = () -> store.edit(key, "", body -> {
                    JsonArray blocks = StudioJson.list(body, "content");
                    int index = Integer.parseInt(issue.field().substring(issue.field().indexOf('/') + 1));
                    if (index < blocks.size()) blocks.remove(index);
                });
            }
            case "adventure_start" -> {
                JsonObject nodes = StudioJson.object(doc.body, "nodes");
                if (nodes.size() > 0) {
                    String first = nodes.keySet().iterator().next();
                    label = StudioText.t("fix.use", first);
                    fix = () -> store.edit(key, "", body -> body.addProperty("start", first));
                }
            }
            default -> { }
        }
        return new Problem(key, issue, label, fix);
    }

    private static JsonObject row(JsonObject body, String field) {
        JsonElement current = body;
        String[] parts = field.split("/");
        for (int index = 0; index < parts.length - 1; index++)
            current = current.isJsonArray() ? current.getAsJsonArray().get(Integer.parseInt(parts[index])) : current.getAsJsonObject().get(parts[index]);
        return current.getAsJsonObject();
    }

    static String goalId() { return "goal_" + UUID.randomUUID().toString().substring(0, 8); }
}
